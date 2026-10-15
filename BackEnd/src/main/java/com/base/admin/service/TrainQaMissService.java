package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.TrainQaMissQueryDTO;
import com.base.admin.domain.vo.TrainQaMissVO;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import com.base.admin.util.TrainQaIntentMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainQaMissService {

    private final JdbcTemplate jdbc;

    public void record(String question, String category, String status, Long docId, Long versionId) {
        if (!StringUtils.hasText(question)) {
            return;
        }
        LoginUser user = SecurityUtils.getCurrentUser();
        Long userId = user == null ? null : user.getUserId();
        String username = user == null ? "" : nvl(user.getUsername());
        String nickname = lookupNickname(userId, username);
        String tokens = String.join(" ", TrainQaIntentMatcher.tokenList(question));
        try {
            jdbc.update("""
                    INSERT INTO train_qa_miss
                      (category, question, intent_tokens, user_id, username, nickname, status, doc_id, version_id, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,1)
                    """,
                    StringUtils.hasText(category) ? category.trim() : "cashier",
                    question.trim(),
                    tokens.length() > 512 ? tokens.substring(0, 512) : tokens,
                    userId,
                    username,
                    nickname,
                    StringUtils.hasText(status) ? status : "NOT_FOUND",
                    docId,
                    versionId);
        } catch (Exception e) {
            log.warn("未命中提问落库失败: {}", e.getMessage());
        }
    }

    public PageResult<TrainQaMissVO> page(TrainQaMissQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        StringBuilder where = new StringBuilder(" WHERE is_active = 1 ");
        List<Object> args = new ArrayList<>();
        String cat = StringUtils.hasText(query.getCategory()) ? query.getCategory().trim() : "cashier";
        where.append(" AND category = ? ");
        args.add(cat);
        if (StringUtils.hasText(query.getKeyword())) {
            String like = "%" + query.getKeyword().trim() + "%";
            where.append(" AND (question LIKE ? OR nickname LIKE ? OR username LIKE ? OR intent_tokens LIKE ?) ");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (query.getCreateTimeStart() != null) {
            where.append(" AND create_time >= ? ");
            args.add(query.getCreateTimeStart().atStartOfDay());
        }
        if (query.getCreateTimeEnd() != null) {
            where.append(" AND create_time < ? ");
            args.add(query.getCreateTimeEnd().plusDays(1).atStartOfDay());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(1) FROM train_qa_miss " + where, Integer.class, args.toArray());
        int t = total == null ? 0 : total;
        int offset = (pageNum - 1) * pageSize;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<TrainQaMissVO> rows = jdbc.query(
                "SELECT id, category, question, intent_tokens, user_id, username, nickname, status, version_id, create_time "
                        + "FROM train_qa_miss " + where + " ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> {
                    TrainQaMissVO vo = new TrainQaMissVO();
                    vo.setId(rs.getLong("id"));
                    vo.setCategory(rs.getString("category"));
                    vo.setQuestion(rs.getString("question"));
                    vo.setIntentTokens(rs.getString("intent_tokens"));
                    long uid = rs.getLong("user_id");
                    vo.setUserId(rs.wasNull() ? null : uid);
                    vo.setUsername(rs.getString("username"));
                    vo.setNickname(rs.getString("nickname"));
                    vo.setStatus(rs.getString("status"));
                    long vid = rs.getLong("version_id");
                    vo.setVersionId(rs.wasNull() ? null : vid);
                    vo.setCreateTime(rs.getString("create_time"));
                    return vo;
                },
                listArgs.toArray());
        return new PageResult<>(t, rows);
    }

    private String lookupNickname(Long userId, String username) {
        try {
            if (userId != null) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT nickname, username FROM sys_user WHERE user_id = ? AND is_active = 1 LIMIT 1", userId);
                if (!rows.isEmpty()) {
                    String nick = nvl(rows.get(0).get("nickname"));
                    String un = nvl(rows.get(0).get("username"));
                    return StringUtils.hasText(nick) ? nick : (StringUtils.hasText(un) ? un : username);
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return StringUtils.hasText(username) ? username : "匿名";
    }

    private static String nvl(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
