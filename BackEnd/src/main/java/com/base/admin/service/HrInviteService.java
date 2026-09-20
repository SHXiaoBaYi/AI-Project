package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrInviteCreateDTO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HrInviteService {

    private static final String UNBOUND = "面试官未绑定钉钉，不能自动创建日程！请先去联系行政帮忙帮忙钉钉事宜";
    private static final Map<Integer, String> ROUND_NAME = Map.of(1, "一面", 2, "二面", 3, "三面", 4, "四面", 5, "五面");

    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalk;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long create(HrInviteCreateDTO dto) {
        java.util.List<Long> ids = interviewerIds(dto);
        Long last = null;
        for (Long interviewerUserId : ids) {
            HrInviteCreateDTO one = new HrInviteCreateDTO();
            one.setApplicationId(dto.getApplicationId());
            one.setRoundNo(dto.getRoundNo());
            one.setInterviewerUserId(interviewerUserId);
            one.setInterviewAt(dto.getInterviewAt());
            one.setDurationMin(dto.getDurationMin());
            one.setLocation(dto.getLocation());
            last = createOne(one);
        }
        return last;
    }

    @Transactional
    public Long createOne(HrInviteCreateDTO dto) {
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND interviewer_user_id = ? AND status = 'SUCCESS' AND is_active = 1
                """, Integer.class, dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewerUserId());
        if (open != null && open > 0) {
            throw new BusinessException("该面试官这一轮已有成功邀约，请先取消再重新发起");
        }
        Map<String, Object> person = jdbc.query("""
                SELECT c.display_name candidate_name, COALESCE(r.job_name, '') job_name, c.phone, c.email, u.nickname, u.phone interviewer_phone
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                JOIN sys_user u ON u.user_id = ?
                WHERE a.id = ? AND a.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("candidate_name", rs.getString("candidate_name"));
            row.put("job_name", rs.getString("job_name"));
            row.put("phone", rs.getString("phone"));
            row.put("email", rs.getString("email"));
            row.put("nickname", rs.getString("nickname"));
            row.put("interviewer_phone", rs.getString("interviewer_phone"));
            return row;
        }, dto.getInterviewerUserId(), dto.getApplicationId());
        if (person == null) {
            throw new BusinessException("投递或面试官不存在");
        }
        String unionId = ensureUnionId(dto.getInterviewerUserId());
        long inviteId = insertInvite(dto, duration);
        String title = "【" + ROUND_NAME.get(dto.getRoundNo()) + "】" + person.get("candidate_name") + " - " + person.get("job_name");
        String description = "候选人：" + person.get("candidate_name")
                + "\n岗位：" + person.get("job_name")
                + "\n电话：" + blank(person.get("phone"))
                + "\n邮箱：" + blank(person.get("email"))
                + "\n地点：" + blank(dto.getLocation());
        DingTalkCalendarClient.CalendarCall call = dingTalk.createEvent(unionId, title, description, dto.getInterviewAt(), duration);
        writeLog(inviteId, "CREATE_CALENDAR", call, SecurityUtils.getCurrentUserId(), call.eventId(),
                Map.of("title", title, "start", String.valueOf(dto.getInterviewAt()), "unionId", blank(unionId)));
        if (!call.success()) {
            jdbc.update("UPDATE hr_interview_invite SET status = 'FAILED', fail_reason = ? WHERE id = ?",
                    cut(call.message()), inviteId);
            return inviteId;
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'SUCCESS', dingtalk_event_id = ?, dingtalk_calendar_id = 'primary', fail_reason = NULL
                WHERE id = ?
                """, call.eventId(), inviteId);
        jdbc.update("""
                INSERT INTO hr_interview_round (application_id, round_no, interviewer_name, interviewer_user_id, interview_at, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE interviewer_name = VALUES(interviewer_name), interviewer_user_id = VALUES(interviewer_user_id),
                  interview_at = VALUES(interview_at), is_active = 1
                """, dto.getApplicationId(), dto.getRoundNo(), person.get("nickname"), dto.getInterviewerUserId(),
                dto.getInterviewAt(), SecurityUtils.getCurrentUsername());
        if (dto.getRoundNo() == 1) {
            jdbc.update("""
                    INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                    VALUES (?, 'INVITED', ?, 'INVITE', ?, 1)
                    ON DUPLICATE KEY UPDATE event_at = VALUES(event_at), is_active = 1
                    """, dto.getApplicationId(), dto.getInterviewAt(), SecurityUtils.getCurrentUsername());
        }
        return inviteId;
    }

    @Transactional
    public void cancel(Long inviteId) {
        Map<String, Object> invite = jdbc.query("""
                SELECT id, application_id, round_no, interviewer_user_id, status, dingtalk_event_id
                FROM hr_interview_invite WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? Map.<String, Object>of(
                "id", rs.getLong("id"),
                "applicationId", rs.getLong("application_id"),
                "roundNo", rs.getInt("round_no"),
                "interviewerUserId", rs.getLong("interviewer_user_id"),
                "status", rs.getString("status"),
                "eventId", rs.getString("dingtalk_event_id")) : null, inviteId);
        if (invite == null) {
            throw new BusinessException("邀约不存在");
        }
        String status = (String) invite.get("status");
        if (!"SUCCESS".equals(status) && !"CANCEL_FAILED".equals(status)) {
            throw new BusinessException("只有已建成日程的邀约可以取消");
        }
        String unionId = jdbc.query("SELECT dingtalk_union_id FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, invite.get("interviewerUserId"));
        DingTalkCalendarClient.CalendarCall call = dingTalk.deleteEvent(unionId, (String) invite.get("eventId"));
        writeLog(inviteId, "CANCEL_CALENDAR", call, SecurityUtils.getCurrentUserId(), (String) invite.get("eventId"),
                Map.of("eventId", blank(invite.get("eventId"))));
        if (!call.success()) {
            jdbc.update("UPDATE hr_interview_invite SET status = 'CANCEL_FAILED', fail_reason = ? WHERE id = ?",
                    cut(call.message()), inviteId);
            throw new BusinessException(call.message());
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, fail_reason = NULL
                WHERE id = ?
                """, SecurityUtils.getCurrentUserId(), LocalDateTime.now(), inviteId);
        jdbc.update("""
                UPDATE hr_interview_round SET interview_at = NULL, is_active = 1
                WHERE application_id = ? AND round_no = ?
                """, invite.get("applicationId"), invite.get("roundNo"));
        if (((Number) invite.get("roundNo")).intValue() == 1) {
            jdbc.update("""
                    UPDATE hr_stage_event SET is_active = 0
                    WHERE application_id = ? AND stage_code = 'INVITED' AND source_sheet = 'INVITE'
                    """, invite.get("applicationId"));
        }
    }

    public List<Map<String, Object>> list(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.application_id, i.round_no, i.interviewer_user_id, u.nickname interviewer_name,
                       i.interview_at, i.duration_min, i.location, i.status, i.fail_reason, i.dingtalk_event_id,
                       c.display_name, a.requisition_id, r.job_name, f.file_name
                FROM hr_interview_invite i
                JOIN hr_application a ON a.id = i.application_id
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN sys_user u ON u.user_id = i.interviewer_user_id
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE i.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(q.getMine())) {
            sql.append("""
                     AND i.interviewer_user_id = ? AND i.status = 'SUCCESS'
                     AND NOT EXISTS (
                       SELECT 1 FROM hr_interview_record rec
                       WHERE rec.invite_id = i.id AND rec.is_active = 1 AND rec.conclusion IN ('PASS', 'FAIL')
                     )
                    """);
            args.add(SecurityUtils.getCurrentUserId());
        }
        if (q.getCandidateName() != null && !q.getCandidateName().isBlank()) {
            sql.append(" AND c.display_name LIKE ? ");
            args.add("%" + q.getCandidateName().trim() + "%");
        }
        if (q.getRequisitionId() != null) {
            sql.append(" AND a.requisition_id = ? ");
            args.add(q.getRequisitionId());
        }
        if (q.getRoundNo() != null) {
            sql.append(" AND i.round_no = ? ");
            args.add(q.getRoundNo());
        }
        if (q.getInterviewerUserId() != null) {
            sql.append(" AND i.interviewer_user_id = ? ");
            args.add(q.getInterviewerUserId());
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            sql.append(" AND i.status = ? ");
            args.add(q.getStatus());
        }
        sql.append(" ORDER BY i.interview_at DESC, i.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public void update(Long id, HrInviteCreateDTO dto) {
        java.util.List<Long> ids = interviewerIds(dto);
        for (Long interviewerUserId : ids) {
            ensureUnionId(interviewerUserId);
        }
        dto.setInterviewerUserId(ids.get(0));
        Map<String, Object> invite = loadInvite(id);
        releaseCalendar(id, (Long) invite.get("interviewerUserId"), (String) invite.get("eventId"));
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        jdbc.update("""
                UPDATE hr_interview_invite
                SET application_id = ?, round_no = ?, interviewer_user_id = ?, interview_at = ?, duration_min = ?, location = ?, status = 'FAILED', fail_reason = NULL
                WHERE id = ? AND is_active = 1
                """, dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewerUserId(), dto.getInterviewAt(),
                duration, dto.getLocation(), id);
        attachCalendar(id, dto, duration);
        for (int i = 1; i < ids.size(); i++) {
            HrInviteCreateDTO one = new HrInviteCreateDTO();
            one.setApplicationId(dto.getApplicationId());
            one.setRoundNo(dto.getRoundNo());
            one.setInterviewerUserId(ids.get(i));
            one.setInterviewAt(dto.getInterviewAt());
            one.setDurationMin(dto.getDurationMin());
            one.setLocation(dto.getLocation());
            createOne(one);
        }
    }

    @Transactional
    public void remove(Long id) {
        Map<String, Object> invite = loadInvite(id);
        releaseCalendar(id, (Long) invite.get("interviewerUserId"), (String) invite.get("eventId"));
        jdbc.update("UPDATE hr_interview_invite SET is_active = 0, status = 'CANCELLED' WHERE id = ?", id);
    }

    @Transactional
    public void removeBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的面试邀约");
        }
        for (Long id : ids) {
            if (id != null) {
                remove(id);
            }
        }
    }

    @Transactional
    public Long forward(Long id, Long interviewerUserId) {
        Map<String, Object> invite = loadInvite(id);
        HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
        Long created = create(dto);
        releaseCalendar(id, (Long) invite.get("interviewerUserId"), (String) invite.get("eventId"));
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?
                WHERE id = ?
                """, SecurityUtils.getCurrentUserId(), LocalDateTime.now(), id);
        return created;
    }

    @Transactional
    public Long addInterviewer(Long id, Long interviewerUserId) {
        Map<String, Object> invite = loadInvite(id);
        HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
        Long created = create(dto);
        Object requisitionId = invite.get("requisitionId");
        if (requisitionId != null) {
            jdbc.update("""
                    INSERT INTO hr_requisition_round_interviewer (requisition_id, round_no, interviewer_user_id, create_by, is_active)
                    VALUES (?, ?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """, requisitionId, invite.get("roundNo"), interviewerUserId, SecurityUtils.getCurrentUsername());
        }
        return created;
    }

    private HrInviteCreateDTO copyInvite(Map<String, Object> invite, Long interviewerUserId) {
        HrInviteCreateDTO dto = new HrInviteCreateDTO();
        dto.setApplicationId((Long) invite.get("applicationId"));
        dto.setRoundNo(((Number) invite.get("roundNo")).intValue());
        dto.setInterviewerUserId(interviewerUserId);
        dto.setInterviewAt((LocalDateTime) invite.get("interviewAt"));
        dto.setDurationMin(invite.get("durationMin") == null ? 60 : ((Number) invite.get("durationMin")).intValue());
        dto.setLocation((String) invite.get("location"));
        return dto;
    }

    private Map<String, Object> loadInvite(Long inviteId) {
        Map<String, Object> invite = jdbc.query("""
                SELECT i.id, i.application_id, i.round_no, i.interviewer_user_id, i.status, i.dingtalk_event_id,
                       i.interview_at, i.duration_min, i.location, a.requisition_id
                FROM hr_interview_invite i
                JOIN hr_application a ON a.id = i.application_id
                WHERE i.id = ? AND i.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("applicationId", rs.getLong("application_id"));
            row.put("roundNo", rs.getInt("round_no"));
            row.put("interviewerUserId", rs.getLong("interviewer_user_id"));
            row.put("status", rs.getString("status"));
            row.put("eventId", rs.getString("dingtalk_event_id"));
            row.put("interviewAt", rs.getTimestamp("interview_at") == null ? null : rs.getTimestamp("interview_at").toLocalDateTime());
            row.put("durationMin", rs.getObject("duration_min") == null ? null : rs.getInt("duration_min"));
            row.put("location", rs.getString("location"));
            row.put("requisitionId", rs.getObject("requisition_id") == null ? null : rs.getLong("requisition_id"));
            return row;
        }, inviteId);
        if (invite == null) {
            throw new BusinessException("邀约不存在");
        }
        return invite;
    }

    private void releaseCalendar(Long inviteId, Long interviewerUserId, String eventId) {
        if (eventId == null || eventId.isBlank() || interviewerUserId == null) {
            return;
        }
        String unionId = jdbc.query("SELECT dingtalk_union_id FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, interviewerUserId);
        DingTalkCalendarClient.CalendarCall call = dingTalk.deleteEvent(unionId, eventId);
        writeLog(inviteId, "CANCEL_CALENDAR", call, SecurityUtils.getCurrentUserId(), eventId, Map.of("eventId", eventId));
    }

    private void attachCalendar(Long inviteId, HrInviteCreateDTO dto, int duration) {
        Map<String, Object> person = jdbc.query("""
                SELECT c.display_name candidate_name, COALESCE(r.job_name, '') job_name, c.phone, c.email, u.nickname, u.phone interviewer_phone
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                JOIN sys_user u ON u.user_id = ?
                WHERE a.id = ? AND a.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("candidate_name", rs.getString("candidate_name"));
            row.put("job_name", rs.getString("job_name"));
            row.put("phone", rs.getString("phone"));
            row.put("email", rs.getString("email"));
            row.put("nickname", rs.getString("nickname"));
            row.put("interviewer_phone", rs.getString("interviewer_phone"));
            return row;
        }, dto.getInterviewerUserId(), dto.getApplicationId());
        if (person == null) {
            throw new BusinessException("投递或面试官不存在");
        }
        String unionId = ensureUnionId(dto.getInterviewerUserId());
        String title = "【" + ROUND_NAME.getOrDefault(dto.getRoundNo(), dto.getRoundNo() + "面") + "】"
                + person.get("candidate_name") + " - " + person.get("job_name");
        String description = "候选人：" + person.get("candidate_name")
                + "\n岗位：" + person.get("job_name")
                + "\n电话：" + blank(person.get("phone"))
                + "\n邮箱：" + blank(person.get("email"))
                + "\n地点：" + blank(dto.getLocation());
        DingTalkCalendarClient.CalendarCall call = dingTalk.createEvent(unionId, title, description, dto.getInterviewAt(), duration);
        writeLog(inviteId, "CREATE_CALENDAR", call, SecurityUtils.getCurrentUserId(), call.eventId(),
                Map.of("title", title, "start", String.valueOf(dto.getInterviewAt()), "unionId", blank(unionId)));
        if (!call.success()) {
            jdbc.update("UPDATE hr_interview_invite SET status = 'FAILED', fail_reason = ? WHERE id = ?", cut(call.message()), inviteId);
            return;
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'SUCCESS', dingtalk_event_id = ?, dingtalk_calendar_id = 'primary', fail_reason = NULL
                WHERE id = ?
                """, call.eventId(), inviteId);
    }

    private long insertInvite(HrInviteCreateDTO dto, int duration) {
        jdbc.update("""
                INSERT INTO hr_interview_invite (application_id, round_no, interviewer_user_id, interview_at, duration_min, location, status, invited_by, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'FAILED', ?, ?, 1)
                """, dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewerUserId(), dto.getInterviewAt(),
                duration, dto.getLocation(), SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUsername());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private java.util.List<Long> interviewerIds(HrInviteCreateDTO dto) {
        java.util.List<Long> ids = dto.getInterviewerUserIds() == null ? java.util.List.of()
                : dto.getInterviewerUserIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty() && dto.getInterviewerUserId() != null) {
            return java.util.List.of(dto.getInterviewerUserId());
        }
        if (ids.isEmpty()) {
            throw new BusinessException("请选择面试官");
        }
        return ids;
    }

    private String ensureUnionId(Long userId) {
        String unionId = jdbc.query("SELECT dingtalk_union_id FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        if (StringUtils.hasText(unionId)) {
            return unionId;
        }
        String name = jdbc.query("SELECT nickname FROM sys_user WHERE user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        throw new BusinessException(UNBOUND + (name == null || name.isBlank() ? "" : "（" + name + "）"));
    }

    private void writeLog(long inviteId, String action, DingTalkCalendarClient.CalendarCall call, Long operatorId,
                          String eventId, Map<String, String> request) {
        jdbc.update("""
                INSERT INTO hr_interview_invite_log (invite_id, action, result, operator_id, dingtalk_event_id, request_body, response_body, operated_at, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, 1)
                """, inviteId, action, call.success() ? "SUCCESS" : "FAILED", operatorId, eventId,
                json(request), json(call.response() == null ? Map.of("message", blank(call.message())) : Map.of("raw", call.response())),
                LocalDateTime.now(), SecurityUtils.getCurrentUsername());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"message\":\"无法序列化\"}";
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blank(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String cut(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 250 ? text : text.substring(0, 250);
    }
}
