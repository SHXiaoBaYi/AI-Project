package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrInviteCreateDTO;
import com.base.admin.domain.vo.HrInviteSaveVO;
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

    private static final String NO_CALENDAR_REASON = "面试官未绑定钉钉，未创建钉钉日程";
    private static final Map<Integer, String> ROUND_NAME = Map.of(1, "一面", 2, "二面", 3, "三面", 4, "四面", 5, "五面");

    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalk;
    private final ObjectMapper objectMapper;

    @Transactional
    public HrInviteSaveVO create(HrInviteCreateDTO dto) {
        java.util.List<Long> ids = interviewerIds(dto);
        java.util.List<String> unbound = new ArrayList<>();
        Long last = null;
        for (Long interviewerUserId : ids) {
            HrInviteCreateDTO one = new HrInviteCreateDTO();
            one.setApplicationId(dto.getApplicationId());
            one.setRoundNo(dto.getRoundNo());
            one.setInterviewerUserId(interviewerUserId);
            one.setInterviewAt(dto.getInterviewAt());
            one.setDurationMin(dto.getDurationMin());
            one.setLocation(dto.getLocation());
            InviteWrite written = createOne(one);
            last = written.id();
            if (written.unboundName() != null) {
                unbound.add(written.unboundName());
            }
        }
        return saveResult(last, unboundMessage(unbound));
    }

    @Transactional
    private InviteWrite createOne(HrInviteCreateDTO dto) {
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND interviewer_user_id = ? AND status IN ('SUCCESS', 'NO_CALENDAR') AND is_active = 1
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
        String unionId = findUnionId(dto.getInterviewerUserId());
        long inviteId = insertInvite(dto, duration);
        if (!StringUtils.hasText(unionId)) {
            markNoCalendar(inviteId);
            writeRoundAndStage(dto, person);
            return new InviteWrite(inviteId, interviewerName(dto.getInterviewerUserId()));
        }
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
            return new InviteWrite(inviteId, null);
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'SUCCESS', dingtalk_event_id = ?, dingtalk_calendar_id = 'primary', fail_reason = NULL
                WHERE id = ?
                """, call.eventId(), inviteId);
        writeRoundAndStage(dto, person);
        return new InviteWrite(inviteId, null);
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
                       c.display_name, a.requisition_id, a.current_stage, r.job_name, f.file_name,
                       mine_rec.id record_id, mine_rec.conclusion my_conclusion, mine_rec.comment my_comment,
                       CASE WHEN mine_rec.id IS NOT NULL AND mine_rec.conclusion IS NOT NULL AND mine_rec.conclusion <> '' THEN 1 ELSE 0 END reviewed
                FROM hr_interview_invite i
                JOIN hr_application a ON a.id = i.application_id
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN sys_user u ON u.user_id = i.interviewer_user_id
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                LEFT JOIN hr_interview_record mine_rec ON mine_rec.application_id = i.application_id
                  AND mine_rec.round_no = i.round_no AND mine_rec.interviewer_user_id = i.interviewer_user_id
                  AND mine_rec.is_active = 1
                WHERE i.is_active = 1
                """);
        List<Object> args = new ArrayList<>();
        if (Boolean.TRUE.equals(q.getMine())) {
            sql.append("""
                     AND i.interviewer_user_id = ? AND i.status IN ('SUCCESS', 'NO_CALENDAR')
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
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        if (Boolean.TRUE.equals(q.getMine())) {
            rows.removeIf(row -> HrInterviewRecordService.roundConcluded(
                    row.get("current_stage") == null ? null : String.valueOf(row.get("current_stage")),
                    row.get("round_no") == null ? 0 : ((Number) row.get("round_no")).intValue()));
        }
        return rows;
    }

    @Transactional
    public HrInviteSaveVO update(Long id, HrInviteCreateDTO dto) {
        java.util.List<Long> desired = interviewerIds(dto);
        Map<String, Object> anchor = loadInvite(id);
        java.util.List<Map<String, Object>> peers = loadSession(anchor);
        java.util.Map<Long, Map<String, Object>> byInterviewer = new java.util.HashMap<>();
        for (Map<String, Object> peer : peers) {
            byInterviewer.putIfAbsent((Long) peer.get("interviewerUserId"), peer);
        }
        java.util.List<String> unbound = new ArrayList<>();
        java.util.List<String> cancelMisses = new ArrayList<>();
        java.util.Set<Long> kept = new java.util.HashSet<>();
        for (Long interviewerId : desired) {
            Map<String, Object> existing = byInterviewer.get(interviewerId);
            if (existing == null) {
                HrInviteCreateDTO one = new HrInviteCreateDTO();
                one.setApplicationId(dto.getApplicationId());
                one.setRoundNo(dto.getRoundNo());
                one.setInterviewerUserId(interviewerId);
                one.setInterviewAt(dto.getInterviewAt());
                one.setDurationMin(dto.getDurationMin());
                one.setLocation(dto.getLocation());
                InviteWrite written = createOne(one);
                if (written.unboundName() != null) {
                    unbound.add(written.unboundName());
                }
                continue;
            }
            kept.add((Long) existing.get("id"));
            String[] rewritten = rewriteInvite((Long) existing.get("id"), (Long) existing.get("interviewerUserId"),
                    (String) existing.get("eventId"), dto, interviewerId);
            if (StringUtils.hasText(rewritten[0])) {
                cancelMisses.add("保存面试邀约记录成功，但是由于面试官" + rewritten[0] + "没有绑定钉钉，原钉钉日程未能取消");
            }
            if (StringUtils.hasText(rewritten[1])) {
                unbound.add(rewritten[1]);
            }
        }
        for (Map<String, Object> peer : peers) {
            Long peerId = (Long) peer.get("id");
            if (!kept.contains(peerId)) {
                HrInviteSaveVO removed = remove(peerId);
                if (StringUtils.hasText(removed.getWarning())) {
                    cancelMisses.add(removed.getWarning());
                }
            }
        }
        String warning = unboundMessage(unbound);
        if (!cancelMisses.isEmpty()) {
            warning = joinWarning(String.join("；", cancelMisses), warning);
        }
        return saveResult(id, warning);
    }

    private java.util.List<Map<String, Object>> loadSession(Map<String, Object> anchor) {
        return jdbc.query("""
                SELECT id, interviewer_user_id, dingtalk_event_id
                FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                """, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("interviewerUserId", rs.getLong("interviewer_user_id"));
            row.put("eventId", rs.getString("dingtalk_event_id"));
            return row;
        }, anchor.get("applicationId"), anchor.get("roundNo"), anchor.get("interviewAt"));
    }

    private String[] rewriteInvite(Long inviteId, Long oldInterviewerId, String eventId, HrInviteCreateDTO dto, Long interviewerId) {
        String released = releaseCalendar(inviteId, oldInterviewerId, eventId);
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        jdbc.update("""
                UPDATE hr_interview_invite
                SET application_id = ?, round_no = ?, interviewer_user_id = ?, interview_at = ?, duration_min = ?, location = ?, status = 'FAILED', fail_reason = NULL, dingtalk_event_id = NULL
                WHERE id = ? AND is_active = 1
                """, dto.getApplicationId(), dto.getRoundNo(), interviewerId, dto.getInterviewAt(),
                duration, dto.getLocation(), inviteId);
        HrInviteCreateDTO one = new HrInviteCreateDTO();
        one.setApplicationId(dto.getApplicationId());
        one.setRoundNo(dto.getRoundNo());
        one.setInterviewerUserId(interviewerId);
        one.setInterviewAt(dto.getInterviewAt());
        one.setDurationMin(dto.getDurationMin());
        one.setLocation(dto.getLocation());
        return new String[]{released, attachCalendar(inviteId, one, duration)};
    }

    @Transactional
    public HrInviteSaveVO remove(Long id) {
        Map<String, Object> invite = loadInvite(id);
        String unboundName = releaseCalendar(id, (Long) invite.get("interviewerUserId"), (String) invite.get("eventId"));
        String warning = StringUtils.hasText(unboundName)
                ? "删除面试邀约成功，但是由于面试官" + unboundName + "没有绑定钉钉，未能取消钉钉日程"
                : null;
        jdbc.update("UPDATE hr_interview_invite SET is_active = 0, status = 'CANCELLED' WHERE id = ?", id);
        return saveResult(id, warning);
    }

    @Transactional
    public HrInviteSaveVO removeBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的面试邀约");
        }
        java.util.List<String> warnings = new ArrayList<>();
        Long last = null;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            HrInviteSaveVO one = remove(id);
            last = one.getId();
            if (StringUtils.hasText(one.getWarning())) {
                warnings.add(one.getWarning());
            }
        }
        return saveResult(last, warnings.isEmpty() ? null : String.join("；", warnings));
    }

    @Transactional
    public void createCalendar(Long id) {
        Map<String, Object> invite = loadInvite(id);
        assertCalendarCreatable(invite);
        Long interviewerUserId = (Long) invite.get("interviewerUserId");
        if (!StringUtils.hasText(findUnionId(interviewerUserId))) {
            throw new BusinessException("面试官" + interviewerName(interviewerUserId) + "没有绑定钉钉，不能创建钉钉日程，请联系行政绑定");
        }
        HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        String skipped = attachCalendar(id, dto, duration);
        if (skipped != null) {
            throw new BusinessException("面试官" + skipped + "没有绑定钉钉，不能创建钉钉日程，请联系行政绑定");
        }
        String status = jdbc.query("SELECT status FROM hr_interview_invite WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (!"SUCCESS".equals(status)) {
            String reason = jdbc.query("SELECT fail_reason FROM hr_interview_invite WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, id);
            throw new BusinessException(StringUtils.hasText(reason) ? reason : "钉钉日程创建失败");
        }
    }

    @Transactional
    public HrInviteSaveVO createCalendarBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要创建钉钉日程的面试邀约");
        }
        int created = 0;
        int skipped = 0;
        java.util.List<String> problems = new ArrayList<>();
        java.util.List<String> unbound = new ArrayList<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            Map<String, Object> invite = loadInvite(id);
            String status = (String) invite.get("status");
            String eventId = (String) invite.get("eventId");
            if ("CANCELLED".equals(status)) {
                problems.add("已取消的邀约不能创建钉钉日程");
                continue;
            }
            if ("SUCCESS".equals(status) && StringUtils.hasText(eventId)) {
                skipped++;
                continue;
            }
            Long interviewerUserId = (Long) invite.get("interviewerUserId");
            if (!StringUtils.hasText(findUnionId(interviewerUserId))) {
                unbound.add(interviewerName(interviewerUserId));
                continue;
            }
            try {
                createCalendar(id);
                created++;
            } catch (BusinessException ex) {
                String name = unboundInterviewer(ex.getMessage());
                if (name != null) {
                    unbound.add(name);
                } else {
                    problems.add(ex.getMessage());
                }
            }
        }
        int requested = (int) ids.stream().filter(java.util.Objects::nonNull).count();
        java.util.List<String> unboundNames = unbound.stream().distinct().toList();
        if (created == 0 && skipped == requested && problems.isEmpty() && unboundNames.isEmpty()) {
            throw new BusinessException("所选邀约都已经有钉钉日程");
        }
        if (created == 0 && unboundNames.isEmpty() && !problems.isEmpty()) {
            throw new BusinessException(String.join("；", problems));
        }
        HrInviteSaveVO vo = saveResult(null, problems.isEmpty() ? null : "已创建 " + created + " 条钉钉日程。" + String.join("；", problems));
        vo.setCreated(created);
        vo.setUnboundInterviewers(unboundNames);
        return vo;
    }

    private static String unboundInterviewer(String message) {
        if (message == null || !message.contains("没有绑定钉钉")) {
            return null;
        }
        int start = message.indexOf("面试官");
        int end = message.indexOf("没有绑定钉钉");
        if (start >= 0 && end > start + 3) {
            return message.substring(start + 3, end);
        }
        return message;
    }

    private void assertCalendarCreatable(Map<String, Object> invite) {
        String status = (String) invite.get("status");
        String eventId = (String) invite.get("eventId");
        if ("CANCELLED".equals(status)) {
            throw new BusinessException("已取消的邀约不能创建钉钉日程");
        }
        if ("SUCCESS".equals(status) && StringUtils.hasText(eventId)) {
            throw new BusinessException("这条邀约已经有钉钉日程");
        }
    }

    @Transactional
    public HrInviteSaveVO forward(Long id, Long interviewerUserId) {
        assertNoReview(id);
        Map<String, Object> invite = loadInvite(id);
        HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
        HrInviteSaveVO created = create(dto);
        String stuck = releaseCalendar(id, (Long) invite.get("interviewerUserId"), (String) invite.get("eventId"));
        if (StringUtils.hasText(stuck)) {
            created.setWarning(joinWarning(created.getWarning(),
                    "原钉钉日程未能取消，因为面试官" + stuck + "没有绑定钉钉"));
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?
                WHERE id = ?
                """, SecurityUtils.getCurrentUserId(), LocalDateTime.now(), id);
        return created;
    }

    @Transactional
    public HrInviteSaveVO addInterviewer(Long id, Long interviewerUserId) {
        assertNoReview(id);
        Map<String, Object> invite = loadInvite(id);
        HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
        HrInviteSaveVO created = create(dto);
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

    private void assertNoReview(Long inviteId) {
        Integer reviewed = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_record
                WHERE invite_id = ? AND is_active = 1 AND conclusion IS NOT NULL AND conclusion <> ''
                """, Integer.class, inviteId);
        if (reviewed != null && reviewed > 0) {
            throw new BusinessException("这条面试已经有评价，不能转发或添加面试官");
        }
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

    private String releaseCalendar(Long inviteId, Long interviewerUserId, String eventId) {
        if (eventId == null || eventId.isBlank() || interviewerUserId == null) {
            return null;
        }
        String unionId = findUnionId(interviewerUserId);
        if (!StringUtils.hasText(unionId)) {
            writeLog(inviteId, "CANCEL_CALENDAR", DingTalkCalendarClient.CalendarCall.fail("面试官未绑定钉钉，未取消日程"),
                    SecurityUtils.getCurrentUserId(), eventId, Map.of("eventId", eventId));
            return interviewerName(interviewerUserId);
        }
        DingTalkCalendarClient.CalendarCall call = dingTalk.deleteEvent(unionId, eventId);
        writeLog(inviteId, "CANCEL_CALENDAR", call, SecurityUtils.getCurrentUserId(), eventId, Map.of("eventId", eventId));
        return null;
    }

    private String attachCalendar(Long inviteId, HrInviteCreateDTO dto, int duration) {
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
        String unionId = findUnionId(dto.getInterviewerUserId());
        if (!StringUtils.hasText(unionId)) {
            markNoCalendar(inviteId);
            writeRoundAndStage(dto, person);
            return interviewerName(dto.getInterviewerUserId());
        }
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
            return null;
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'SUCCESS', dingtalk_event_id = ?, dingtalk_calendar_id = 'primary', fail_reason = NULL
                WHERE id = ?
                """, call.eventId(), inviteId);
        writeRoundAndStage(dto, person);
        return null;
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

    private String findUnionId(Long userId) {
        if (userId == null) {
            return null;
        }
        return jdbc.query("SELECT dingtalk_union_id FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
    }

    private String interviewerName(Long userId) {
        String name = jdbc.query("SELECT COALESCE(NULLIF(nickname, ''), username) FROM sys_user WHERE user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        return name == null || name.isBlank() ? "未知面试官" : name;
    }

    private void markNoCalendar(long inviteId) {
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'NO_CALENDAR', dingtalk_event_id = NULL, dingtalk_calendar_id = NULL, fail_reason = ?
                WHERE id = ?
                """, NO_CALENDAR_REASON, inviteId);
    }

    private void writeRoundAndStage(HrInviteCreateDTO dto, Map<String, Object> person) {
        jdbc.update("""
                INSERT INTO hr_interview_round (application_id, round_no, interviewer_name, interviewer_user_id, interview_at, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE interviewer_name = VALUES(interviewer_name), interviewer_user_id = VALUES(interviewer_user_id),
                  interview_at = VALUES(interview_at), is_active = 1
                """, dto.getApplicationId(), dto.getRoundNo(), person.get("nickname"), dto.getInterviewerUserId(),
                dto.getInterviewAt(), SecurityUtils.getCurrentUsername());
        if (dto.getRoundNo() != null && dto.getRoundNo() == 1) {
            jdbc.update("""
                    INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                    VALUES (?, 'INVITED', ?, 'INVITE', ?, 1)
                    ON DUPLICATE KEY UPDATE event_at = VALUES(event_at), is_active = 1
                    """, dto.getApplicationId(), dto.getInterviewAt(), SecurityUtils.getCurrentUsername());
        }
    }

    private static String unboundMessage(java.util.List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        String joined = String.join("、", names.stream().filter(StringUtils::hasText).distinct().toList());
        if (!StringUtils.hasText(joined)) {
            return null;
        }
        return "保存面试邀约记录成功，但是由于面试官" + joined + "没有绑定钉钉，导致没有创建钉钉日程；若需要系统自动创建钉钉日程，请联系行政绑定";
    }

    private static String joinWarning(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return right;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        return left + "；" + right;
    }

    private static HrInviteSaveVO saveResult(Long id, String warning) {
        HrInviteSaveVO vo = new HrInviteSaveVO();
        vo.setId(id);
        vo.setWarning(warning);
        return vo;
    }

    private record InviteWrite(long id, String unboundName) {
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
