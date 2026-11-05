package com.base.admin.service;

import com.base.admin.common.PageResult;
import com.base.admin.domain.dto.HrStageAdjustDTO;
import com.base.admin.domain.dto.HrStageAdjustLogQueryDTO;
import com.base.admin.domain.vo.HrStageAdjustLogVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.security.LoginUser;
import com.base.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HrStageAdjustService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final HrDataScope dataScope;
    private final HrKpiTaskService kpiTaskService;

    @Transactional
    public void adjust(HrStageAdjustDTO dto) {
        String toStage = dto.getStageCode().trim();
        String reason = dto.getReason().trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请填写调整原因");
        }
        Integer stageOk = jdbc.queryForObject(
                "SELECT COUNT(1) FROM hr_stage_def WHERE stage_code = ? AND is_active = 1", Integer.class, toStage);
        if (stageOk == null || stageOk == 0) {
            throw new BusinessException("阶段不存在");
        }
        assertVisible(dto.getApplicationId());
        App app = jdbc.query("""
                SELECT a.current_stage, c.display_name, COALESCE(r.job_name, '') job_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ? AND a.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new App(rs.getString("current_stage"), rs.getString("display_name"), rs.getString("job_name"));
        }, dto.getApplicationId());
        if (app == null) {
            throw new BusinessException("候选人不存在");
        }
        String fromStage = app.stage() == null ? "" : app.stage();
        String user = SecurityUtils.getCurrentUsername();
        String resumeStatus = "ENTERED".equals(toStage) ? "DRAFT" : "PASS";
        jdbc.update("""
                UPDATE hr_application
                SET current_stage = ?, resume_status = ?, update_by = ?, update_time = NOW()
                WHERE id = ? AND is_active = 1
                """, toStage, resumeStatus, user, dto.getApplicationId());
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, ?, ?, 'ADJUST', ?, 1)
                ON DUPLICATE KEY UPDATE event_at = VALUES(event_at), source_sheet = 'ADJUST',
                    is_active = 1, update_by = VALUES(create_by)
                """, dto.getApplicationId(), toStage, dto.getBusinessAt(), user);
        if ("ONBOARDED".equals(toStage)) {
            jdbc.update("""
                    INSERT INTO hr_onboard (application_id, onboard_date, source, create_by, update_by, is_active)
                    VALUES (?, ?, 'ADJUST', ?, ?, 1)
                    ON DUPLICATE KEY UPDATE onboard_date = VALUES(onboard_date), source = 'ADJUST',
                        is_active = 1, update_by = VALUES(update_by)
                    """, dto.getApplicationId(), dto.getBusinessAt().toLocalDate(), user, user);
            kpiTaskService.onApplicationOnboarded(dto.getApplicationId());
        } else if ("ONBOARDED".equals(fromStage)) {
            jdbc.update("""
                    UPDATE hr_onboard SET is_active = 0, update_by = ? WHERE application_id = ? AND is_active = 1
                    """, user, dto.getApplicationId());
        }
        Operator op = operator();
        jdbc.update("""
                INSERT INTO hr_stage_adjust_log
                    (application_id, candidate_name, job_name, from_stage, to_stage, business_at, reason,
                     operator_user_id, operator_username, operator_nickname, operated_at, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, 1)
                """, dto.getApplicationId(), app.name(), app.job(), fromStage, toStage, dto.getBusinessAt(), reason,
                op.userId(), op.username(), op.nickname(), user);
    }

    public PageResult<HrStageAdjustLogVO> logs(HrStageAdjustLogQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        StringBuilder where = new StringBuilder("""
                 FROM hr_stage_adjust_log l
                 JOIN hr_application a ON a.id = l.application_id
                 LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                 LEFT JOIN hr_stage_def fs ON fs.stage_code = l.from_stage
                 LEFT JOIN hr_stage_def ts ON ts.stage_code = l.to_stage
                 WHERE l.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        dataScope.apply(where, args, "r", "a");
        if (query.getApplicationId() != null) {
            where.append(" AND l.application_id = ? ");
            args.add(query.getApplicationId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String like = "%" + query.getKeyword().trim() + "%";
            where.append("""
                     AND (l.candidate_name LIKE ? OR l.job_name LIKE ? OR l.operator_nickname LIKE ?
                          OR l.operator_username LIKE ? OR l.reason LIKE ?)
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (query.getCreateTimeStart() != null) {
            where.append(" AND l.operated_at >= ? ");
            args.add(query.getCreateTimeStart().atStartOfDay());
        }
        if (query.getCreateTimeEnd() != null) {
            where.append(" AND l.operated_at < ? ");
            args.add(query.getCreateTimeEnd().plusDays(1).atStartOfDay());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(1)" + where, Integer.class, args.toArray());
        int t = total == null ? 0 : total;
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((pageNum - 1) * pageSize);
        List<HrStageAdjustLogVO> rows = jdbc.query("""
                SELECT l.id, l.application_id, l.candidate_name, l.job_name, l.from_stage, l.to_stage,
                       COALESCE(fs.stage_name, l.from_stage) from_stage_name,
                       COALESCE(ts.stage_name, l.to_stage) to_stage_name,
                       l.business_at, l.reason, l.operator_user_id, l.operator_username, l.operator_nickname, l.operated_at
                """ + where + " ORDER BY l.operated_at DESC, l.id DESC LIMIT ? OFFSET ?", (rs, i) -> {
            HrStageAdjustLogVO vo = new HrStageAdjustLogVO();
            vo.setId(rs.getLong("id"));
            vo.setApplicationId(rs.getLong("application_id"));
            vo.setCandidateName(rs.getString("candidate_name"));
            vo.setJobName(rs.getString("job_name"));
            vo.setFromStage(rs.getString("from_stage"));
            vo.setFromStageName(rs.getString("from_stage_name"));
            vo.setToStage(rs.getString("to_stage"));
            vo.setToStageName(rs.getString("to_stage_name"));
            vo.setBusinessAt(rs.getTimestamp("business_at") == null ? null
                    : rs.getTimestamp("business_at").toLocalDateTime().format(TS));
            vo.setReason(rs.getString("reason"));
            long opId = rs.getLong("operator_user_id");
            vo.setOperatorUserId(rs.wasNull() ? null : opId);
            vo.setOperatorUsername(rs.getString("operator_username"));
            vo.setOperatorNickname(rs.getString("operator_nickname"));
            vo.setOperatedAt(rs.getTimestamp("operated_at") == null ? null
                    : rs.getTimestamp("operated_at").toLocalDateTime().format(TS));
            return vo;
        }, listArgs.toArray());
        return new PageResult<>(t, rows);
    }

    private void assertVisible(Long applicationId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1) FROM hr_application a
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ? AND a.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(applicationId);
        dataScope.apply(sql, args, "r", "a");
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        if (n == null || n == 0) {
            throw new BusinessException("候选人不存在");
        }
    }

    private Operator operator() {
        LoginUser user = SecurityUtils.getCurrentUser();
        Long userId = user == null ? null : user.getUserId();
        String username = SecurityUtils.getCurrentUsername();
        String nickname = username;
        if (userId != null) {
            String nick = jdbc.query("""
                    SELECT COALESCE(NULLIF(TRIM(nickname), ''), username) FROM sys_user WHERE user_id = ?
                    """, rs -> rs.next() ? rs.getString(1) : null, userId);
            if (StringUtils.hasText(nick)) {
                nickname = nick;
            }
        }
        return new Operator(userId, username, nickname);
    }

    private record App(String stage, String name, String job) {}

    private record Operator(Long userId, String username, String nickname) {}
}
