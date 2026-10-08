package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.TrainQaCalibrateQueryDTO;
import com.base.admin.domain.dto.TrainQaCalibrateSaveDTO;
import com.base.admin.domain.dto.TrainQaFeedbackDTO;
import com.base.admin.domain.vo.TrainAssistantAskVO;
import com.base.admin.domain.vo.TrainQaCalibrateVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 培训答疑人工校准库：准的答案绑定文档版本，同意图优先命中；文档换版后旧校准不再生效。
 */
@Slf4j
@Service
public class TrainQaCalibrateService {

    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_STALE = "STALE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TrainDocService trainDocService;

    public TrainQaCalibrateService(JdbcTemplate jdbc, ObjectMapper objectMapper, @Lazy TrainDocService trainDocService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.trainDocService = trainDocService;
    }

    /** 答疑优先命中：当前文档最新版 + 已标记准确 + 同意图。 */
    public TrainAssistantAskVO.QaItem findApprovedHit(String userQuestion, long docId, long versionId, String versionLabel) {
        String norm = normalizeIntent(userQuestion);
        if (!StringUtils.hasText(norm)) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, user_question, topic_title, answer_text, answer_html, images_json, aliases_json
                FROM train_qa_calibrate
                WHERE is_active = 1 AND status = ? AND doc_id = ? AND version_id = ?
                ORDER BY update_time DESC
                LIMIT 200
                """, STATUS_APPROVED, docId, versionId);
        Map<String, Object> best = null;
        for (Map<String, Object> row : rows) {
            if (matchesIntent(norm, row)) {
                best = row;
                break;
            }
        }
        if (best == null) {
            return null;
        }
        Long id = ((Number) best.get("id")).longValue();
        jdbc.update("UPDATE train_qa_calibrate SET hit_count = IFNULL(hit_count,0) + 1 WHERE id = ?", id);
        TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
        item.setCalibrateId(id);
        item.setFromCalibrate(true);
        String topic = str(best.get("topic_title"));
        item.setQuestion(StringUtils.hasText(topic) ? topic : str(best.get("user_question")));
        item.setAnswer(str(best.get("answer_text")));
        item.setAnswerHtml(str(best.get("answer_html")));
        item.setImages(parseImages(str(best.get("images_json"))));
        item.setVersionLabel(versionLabel);
        return item;
    }

    @Transactional
    public Long feedback(TrainQaFeedbackDTO dto) {
        if (dto.getAccurate() == null) {
            throw new BusinessException("请标记准或不准");
        }
        String category = normalizeCategory(dto.getCategory());
        DocRef doc = resolveDoc(dto.getDocId(), dto.getVersionId(), category);
        String status = Boolean.TRUE.equals(dto.getAccurate()) ? STATUS_APPROVED : STATUS_REJECTED;
        String userQ = nz(dto.getUserQuestion());
        String norm = normalizeIntent(userQ);
        if (!StringUtils.hasText(norm)) {
            throw new BusinessException("问题不能为空");
        }
        Long id = dto.getCalibrateId();
        if (id == null) {
            id = findIdByNorm(doc.docId, doc.versionId, norm);
        }
        String operator = SecurityUtils.getCurrentUsername();
        String imagesJson = toImagesJson(dto.getImages());
        if (id != null) {
            jdbc.update("""
                    UPDATE train_qa_calibrate SET
                      user_question = ?, intent_norm = ?, topic_title = ?, answer_text = ?, answer_html = ?,
                      images_json = ?, status = ?, source = 'ASSISTANT_MARK', update_by = ?, is_active = 1
                    WHERE id = ? AND is_active = 1
                    """,
                    userQ, norm, nz(dto.getTopicTitle()), nz(dto.getAnswer()), nz(dto.getAnswerHtml()),
                    imagesJson, status, operator, id);
            return id;
        }
        KeyHolder kh = new GeneratedKeyHolder();
        String finalStatus = status;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO train_qa_calibrate
                      (doc_id, version_id, category, user_question, intent_norm, aliases_json, topic_title,
                       answer_text, answer_html, images_json, status, hit_count, source, remark, create_by, update_by, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,0,'ASSISTANT_MARK',NULL,?,?,1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, doc.docId);
            ps.setLong(2, doc.versionId);
            ps.setString(3, category);
            ps.setString(4, userQ);
            ps.setString(5, norm);
            ps.setString(6, "[]");
            ps.setString(7, nz(dto.getTopicTitle()));
            ps.setString(8, nz(dto.getAnswer()));
            ps.setString(9, nz(dto.getAnswerHtml()));
            ps.setString(10, imagesJson);
            ps.setString(11, finalStatus);
            ps.setString(12, operator);
            ps.setString(13, operator);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? null : key.longValue();
    }

    public PageResult<TrainQaCalibrateVO> page(TrainQaCalibrateQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String category = normalizeCategory(query.getCategory());
        StringBuilder where = new StringBuilder(" WHERE c.is_active = 1 ");
        List<Object> args = new ArrayList<>();
        where.append(" AND c.category = ? ");
        args.add(category);
        if (query.getDocId() != null) {
            where.append(" AND c.doc_id = ? ");
            args.add(query.getDocId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            where.append(" AND c.status = ? ");
            args.add(query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            where.append(" AND (c.user_question LIKE ? OR c.topic_title LIKE ? OR c.answer_text LIKE ?) ");
            String like = "%" + query.getKeyword().trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (Boolean.TRUE.equals(query.getOnlyLatest())) {
            where.append(" AND c.version_id = d.latest_version_id ");
        }
        String countSql = "SELECT COUNT(1) FROM train_qa_calibrate c JOIN train_doc d ON d.id = c.doc_id " + where;
        Integer total = jdbc.queryForObject(countSql, Integer.class, args.toArray());
        int t = total == null ? 0 : total;
        int offset = (pageNum - 1) * pageSize;
        String listSql = """
                SELECT c.id, c.doc_id, d.title doc_title, c.version_id, v.version_label,
                       d.latest_version_id, c.category, c.user_question, c.aliases_json, c.topic_title,
                       c.answer_text, c.answer_html, c.images_json, c.status, c.hit_count, c.source,
                       c.remark, c.update_by, c.update_time
                FROM train_qa_calibrate c
                JOIN train_doc d ON d.id = c.doc_id
                LEFT JOIN train_doc_version v ON v.id = c.version_id
                """ + where + " ORDER BY c.update_time DESC LIMIT ? OFFSET ?";
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add(offset);
        List<TrainQaCalibrateVO> rows = jdbc.query(listSql, (rs, i) -> {
            TrainQaCalibrateVO vo = new TrainQaCalibrateVO();
            vo.setId(rs.getLong("id"));
            vo.setDocId(rs.getLong("doc_id"));
            vo.setDocTitle(rs.getString("doc_title"));
            vo.setVersionId(rs.getLong("version_id"));
            vo.setVersionLabel(rs.getString("version_label"));
            long latest = rs.getLong("latest_version_id");
            vo.setLatestVersion(latest > 0 && latest == vo.getVersionId());
            vo.setCategory(rs.getString("category"));
            vo.setUserQuestion(rs.getString("user_question"));
            vo.setAliases(aliasesToText(rs.getString("aliases_json")));
            vo.setTopicTitle(rs.getString("topic_title"));
            vo.setAnswer(rs.getString("answer_text"));
            vo.setAnswerHtml(rs.getString("answer_html"));
            vo.setImages(parseImages(rs.getString("images_json")));
            vo.setStatus(rs.getString("status"));
            vo.setHitCount(rs.getInt("hit_count"));
            vo.setSource(rs.getString("source"));
            vo.setRemark(rs.getString("remark"));
            vo.setUpdateBy(rs.getString("update_by"));
            vo.setUpdateTime(rs.getString("update_time"));
            return vo;
        }, listArgs.toArray());
        return new PageResult<>(t, rows);
    }

    @Transactional
    public Long save(TrainQaCalibrateSaveDTO dto) {
        String category = normalizeCategory(dto.getCategory());
        DocRef doc = resolveDoc(dto.getDocId(), dto.getVersionId(), category);
        String userQ = nz(dto.getUserQuestion());
        String norm = normalizeIntent(userQ);
        if (!StringUtils.hasText(norm)) {
            throw new BusinessException("用户问法不能为空");
        }
        String status = StringUtils.hasText(dto.getStatus())
                ? dto.getStatus().trim().toUpperCase(Locale.ROOT)
                : STATUS_APPROVED;
        if (!List.of(STATUS_APPROVED, STATUS_REJECTED, STATUS_PENDING, STATUS_STALE).contains(status)) {
            throw new BusinessException("状态不合法");
        }
        String operator = SecurityUtils.getCurrentUsername();
        String aliasesJson = aliasesToJson(dto.getAliases());
        String imagesJson = toImagesJson(dto.getImages());
        if (dto.getId() != null) {
            int n = jdbc.update("""
                    UPDATE train_qa_calibrate SET
                      doc_id=?, version_id=?, category=?, user_question=?, intent_norm=?, aliases_json=?,
                      topic_title=?, answer_text=?, answer_html=?, images_json=?, status=?, remark=?,
                      source='ADMIN_EDIT', update_by=?, is_active=1
                    WHERE id=? AND is_active=1
                    """,
                    doc.docId, doc.versionId, category, userQ, norm, aliasesJson,
                    nz(dto.getTopicTitle()), nz(dto.getAnswer()), nz(dto.getAnswerHtml()), imagesJson,
                    status, nz(dto.getRemark()), operator, dto.getId());
            if (n <= 0) {
                throw new BusinessException("记录不存在");
            }
            return dto.getId();
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO train_qa_calibrate
                      (doc_id, version_id, category, user_question, intent_norm, aliases_json, topic_title,
                       answer_text, answer_html, images_json, status, hit_count, source, remark, create_by, update_by, is_active)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,0,'ADMIN_EDIT',?,?,?,1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, doc.docId);
            ps.setLong(2, doc.versionId);
            ps.setString(3, category);
            ps.setString(4, userQ);
            ps.setString(5, norm);
            ps.setString(6, aliasesJson);
            ps.setString(7, nz(dto.getTopicTitle()));
            ps.setString(8, nz(dto.getAnswer()));
            ps.setString(9, nz(dto.getAnswerHtml()));
            ps.setString(10, imagesJson);
            ps.setString(11, status);
            ps.setString(12, nz(dto.getRemark()));
            ps.setString(13, operator);
            ps.setString(14, operator);
            return ps;
        }, kh);
        Number key = kh.getKey();
        return key == null ? null : key.longValue();
    }

    @Transactional
    public void delete(Long id) {
        int n = jdbc.update("UPDATE train_qa_calibrate SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (n <= 0) {
            throw new BusinessException("记录不存在");
        }
    }

    @Transactional
    public TrainQaCalibrateVO rerecognize(Long id) {
        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT id, doc_id, version_id, category, user_question, topic_title, status
                FROM train_qa_calibrate WHERE id = ? AND is_active = 1
                """, id);
        if (found.isEmpty()) {
            throw new BusinessException("记录不存在");
        }
        Map<String, Object> row = found.get(0);
        String userQ = str(row.get("user_question"));
        long docId = ((Number) row.get("doc_id")).longValue();
        long versionId = ((Number) row.get("version_id")).longValue();
        List<TrainAssistantAskVO.QaItem> items = trainDocService.extractQaForVersion(userQ, docId, versionId);
        if (items.isEmpty()) {
            throw new BusinessException("重新识别未找到答案，请手动校准");
        }
        TrainAssistantAskVO.QaItem hit = items.get(0);
        String operator = SecurityUtils.getCurrentUsername();
        // 重新识别后进入待审，避免不准内容直接上线
        jdbc.update("""
                UPDATE train_qa_calibrate SET
                  topic_title = ?, answer_text = ?, answer_html = ?, images_json = ?,
                  status = ?, source = 'RE_RECOGNIZE', update_by = ?
                WHERE id = ?
                """,
                nz(hit.getQuestion()), nz(hit.getAnswer()), nz(hit.getAnswerHtml()),
                toImagesJson(hit.getImages()), STATUS_PENDING, operator, id);
        TrainQaCalibrateVO vo = new TrainQaCalibrateVO();
        vo.setId(id);
        vo.setDocId(docId);
        vo.setVersionId(versionId);
        vo.setUserQuestion(userQ);
        vo.setTopicTitle(hit.getQuestion());
        vo.setAnswer(hit.getAnswer());
        vo.setAnswerHtml(hit.getAnswerHtml());
        vo.setImages(hit.getImages() == null ? List.of() : hit.getImages());
        vo.setStatus(STATUS_PENDING);
        vo.setSource("RE_RECOGNIZE");
        return vo;
    }

    /** 文档上传新版本后：旧版校准标记为过期（不再参与答疑优先命中）。 */
    public void markStaleForDocExceptVersion(long docId, long keepVersionId) {
        int n = jdbc.update("""
                UPDATE train_qa_calibrate SET status = ?
                WHERE doc_id = ? AND version_id <> ? AND is_active = 1 AND status = ?
                """, STATUS_STALE, docId, keepVersionId, STATUS_APPROVED);
        if (n > 0) {
            log.info("文档换版，已将旧校准标为 STALE docId={} keepVersionId={} count={}", docId, keepVersionId, n);
        }
    }

    private boolean matchesIntent(String norm, Map<String, Object> row) {
        String intentNorm = normalizeIntent(str(row.get("user_question")));
        if (norm.equals(intentNorm)) {
            return true;
        }
        // aliases_json: ["怎么寄存","寄存操作"]
        for (String alias : parseAliases(str(row.get("aliases_json")))) {
            if (norm.equals(normalizeIntent(alias))) {
                return true;
            }
        }
        String topic = normalizeIntent(str(row.get("topic_title")));
        return StringUtils.hasText(topic) && (norm.equals(topic) || topic.contains(norm) || norm.contains(topic));
    }

    private Long findIdByNorm(long docId, long versionId, String norm) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM train_qa_calibrate
                WHERE is_active = 1 AND doc_id = ? AND version_id = ? AND intent_norm = ?
                ORDER BY update_time DESC LIMIT 1
                """, (rs, i) -> rs.getLong(1), docId, versionId, norm);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private DocRef resolveDoc(Long docId, Long versionId, String category) {
        if (docId != null && versionId != null) {
            DocRef r = new DocRef();
            r.docId = docId;
            r.versionId = versionId;
            return r;
        }
        Map<String, Object> latest = jdbc.query("""
                SELECT d.id doc_id, d.latest_version_id version_id
                FROM train_doc d
                WHERE d.is_active = 1 AND d.category = ? AND d.status = 0
                  AND d.latest_version_id IS NOT NULL
                ORDER BY d.update_time DESC LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return Map.<String, Object>of(
                    "doc_id", rs.getLong("doc_id"),
                    "version_id", rs.getLong("version_id"));
        }, category);
        if (latest == null) {
            throw new BusinessException("暂无培训文档");
        }
        DocRef r = new DocRef();
        r.docId = docId != null ? docId : ((Number) latest.get("doc_id")).longValue();
        r.versionId = versionId != null ? versionId : ((Number) latest.get("version_id")).longValue();
        return r;
    }

    public static String normalizeIntent(String q) {
        if (q == null) {
            return "";
        }
        return q.replaceAll("[\\s\\p{Punct}？?！!，,。、；;：:\\\"'（）()【】\\[\\]《》<>·…—\\-_/\\\\|]+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : "cashier";
    }

    private String toImagesJson(List<String> images) {
        try {
            return objectMapper.writeValueAsString(images == null ? List.of() : images);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String aliasesToJson(String aliasesText) {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(aliasesText)) {
            for (String line : aliasesText.split("\\R")) {
                String t = line.trim();
                if (StringUtils.hasText(t)) {
                    list.add(t);
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String aliasesToText(String json) {
        List<String> list = parseAliases(json);
        return String.join("\n", list);
    }

    private List<String> parseAliases(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static class DocRef {
        long docId;
        long versionId;
    }
}
