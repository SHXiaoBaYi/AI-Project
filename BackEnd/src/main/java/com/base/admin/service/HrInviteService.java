package com.base.admin.service;

import com.base.admin.domain.dto.HrBoardQueryDTO;
import com.base.admin.domain.dto.HrInviteCreateDTO;
import com.base.admin.domain.vo.HrInviteSaveVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.ChinaHoliday;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HrInviteService {

    private static final String NO_CALENDAR_REASON = "面试官未绑定钉钉，未创建钉钉日程";
    /** 临时关闭自动建钉钉日程时的状态说明（列表「创建钉钉日程」入口也已关闭） */
    private static final String CALENDAR_SKIPPED_REASON = "暂未自动创建钉钉日程";
    /**
     * 临时开关：创建/修改面试邀约时是否自动创建钉钉日程。
     * false = 只落邀约记录（状态 NO_CALENDAR），不调钉钉；恢复时改回 true。
     */
    private static final boolean AUTO_CREATE_DINGTALK_CALENDAR = false;
    private static final Map<Integer, String> ROUND_NAME = Map.of(1, "一面", 2, "二面", 3, "三面", 4, "四面", 5, "五面");
    /** 面试开始时间：每天 09:30～17:30，半小时整点 */
    private static final LocalTime INTERVIEW_START = LocalTime.of(9, 30);
    private static final LocalTime INTERVIEW_END = LocalTime.of(17, 30);

    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalk;
    private final HrMasterService hrMasterService;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;
    private final DingTalkScheduleRuleService dingTalkScheduleRuleService;

    @Transactional
    public HrInviteSaveVO create(HrInviteCreateDTO dto) {
        validateInterviewAt(dto.getInterviewAt());
        java.util.List<Long> ids = interviewerIds(dto);
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        java.util.List<Long> inviteIds = new ArrayList<>();
        for (Long interviewerUserId : ids) {
            HrInviteCreateDTO one = copyDto(dto, interviewerUserId);
            inviteIds.add(createOneRecord(one, duration));
        }
        Long last = inviteIds.isEmpty() ? null : inviteIds.get(inviteIds.size() - 1);
        if (!AUTO_CREATE_DINGTALK_CALENDAR) {
            finalizeInviteWithoutCalendar(inviteIds, dto);
            return saveResult(last, null);
        }
        java.util.List<String> unbound = attachSessionCalendars(inviteIds, dto, duration);
        boolean calendarOk = unbound.size() < ids.size();
        if (calendarOk && last != null) {
            notifyRoundCc(last, dto);
        }
        return saveResult(last, unboundMessage(unbound));
    }

    /** 只写邀约行，不建钉钉日程（同场多面试官统一由 attachSessionCalendars 建一条）。 */
    private long createOneRecord(HrInviteCreateDTO dto, int duration) {
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND interviewer_user_id = ? AND status IN ('SUCCESS', 'NO_CALENDAR') AND is_active = 1
                """, Integer.class, dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewerUserId());
        if (open != null && open > 0) {
            throw new BusinessException("该面试官这一轮已有成功邀约，请先取消再重新发起");
        }
        Map<String, Object> person = loadInvitePerson(dto.getInterviewerUserId(), dto.getApplicationId());
        if (person == null) {
            throw new BusinessException("投递或面试官不存在");
        }
        long inviteId = insertInvite(dto, duration);
        replaceInviteCc(inviteId, resolveCcUserIds(dto));
        return inviteId;
    }

    @Transactional
    private InviteWrite createOne(HrInviteCreateDTO dto) {
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        long inviteId = createOneRecord(dto, duration);
        if (!AUTO_CREATE_DINGTALK_CALENDAR) {
            finalizeInviteWithoutCalendar(List.of(inviteId), dto);
            return new InviteWrite(inviteId, null);
        }
        java.util.List<String> unbound = attachSessionCalendars(List.of(inviteId), dto, duration);
        return new InviteWrite(inviteId, unbound.isEmpty() ? null : unbound.getFirst());
    }

    /** 不调钉钉：标记未建日程，并写入轮次/阶段。 */
    private void finalizeInviteWithoutCalendar(java.util.List<Long> inviteIds, HrInviteCreateDTO dto) {
        if (inviteIds == null || inviteIds.isEmpty()) {
            return;
        }
        for (Long inviteId : inviteIds) {
            if (inviteId == null) {
                continue;
            }
            Long interviewerUserId = jdbc.query("SELECT interviewer_user_id FROM hr_interview_invite WHERE id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, inviteId);
            Map<String, Object> person = loadInvitePerson(interviewerUserId, dto.getApplicationId());
            jdbc.update("""
                    UPDATE hr_interview_invite
                    SET status = 'NO_CALENDAR', dingtalk_event_id = NULL, dingtalk_calendar_id = NULL, fail_reason = ?
                    WHERE id = ?
                    """, CALENDAR_SKIPPED_REASON, inviteId);
            if (person != null) {
                writeRoundAndStage(copyDto(dto, interviewerUserId), person);
            }
        }
    }

    @Transactional
    public void cancel(Long inviteId) {
        Map<String, Object> invite = jdbc.query("""
                SELECT id, application_id, round_no, interviewer_user_id, interview_at, status, dingtalk_event_id,
                       invited_by, dingtalk_calendar_id
                FROM hr_interview_invite WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("applicationId", rs.getLong("application_id"));
            row.put("roundNo", rs.getInt("round_no"));
            row.put("interviewerUserId", rs.getLong("interviewer_user_id"));
            row.put("interviewAt", rs.getObject("interview_at", LocalDateTime.class));
            row.put("status", rs.getString("status"));
            row.put("eventId", rs.getString("dingtalk_event_id"));
            row.put("invitedBy", rs.getObject("invited_by") == null ? null : rs.getLong("invited_by"));
            row.put("calendarId", rs.getString("dingtalk_calendar_id"));
            return row;
        }, inviteId);
        if (invite == null) {
            throw new BusinessException("邀约不存在");
        }
        String status = (String) invite.get("status");
        if (!"SUCCESS".equals(status) && !"CANCEL_FAILED".equals(status)) {
            throw new BusinessException("只有已建成日程的邀约可以取消");
        }
        String eventId = (String) invite.get("eventId");
        DingTalkCalendarClient.CalendarCall call = releaseSharedCalendarEvent(invite, true);
        writeLog(inviteId, "CANCEL_CALENDAR", call, SecurityUtils.getCurrentUserId(), eventId,
                Map.of("eventId", blank(eventId)));
        if (call != null && !call.success()) {
            jdbc.update("UPDATE hr_interview_invite SET status = 'CANCEL_FAILED', fail_reason = ? WHERE id = ?",
                    cut(call.message()), inviteId);
            throw new BusinessException(call.message());
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET status = 'CANCELLED', cancelled_by = ?, cancelled_at = ?, fail_reason = NULL,
                    dingtalk_event_id = NULL, dingtalk_calendar_id = NULL
                WHERE id = ?
                """, SecurityUtils.getCurrentUserId(), LocalDateTime.now(), inviteId);
        Long applicationId = (Long) invite.get("applicationId");
        int roundNo = ((Number) invite.get("roundNo")).intValue();
        LocalDateTime interviewAt = (LocalDateTime) invite.get("interviewAt");
        Integer successLeft = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                  AND status = 'SUCCESS' AND id <> ?
                """, Integer.class, applicationId, roundNo, interviewAt, inviteId);
        if (successLeft == null || successLeft == 0) {
            releaseCcCalendarsForSession(applicationId, roundNo, interviewAt);
        }
        jdbc.update("""
                UPDATE hr_interview_round SET interview_at = NULL, is_active = 1
                WHERE application_id = ? AND round_no = ?
                """, applicationId, roundNo);
        if (roundNo == 1) {
            jdbc.update("""
                    UPDATE hr_stage_event SET is_active = 0
                    WHERE application_id = ? AND stage_code = 'INVITED' AND source_sheet = 'INVITE'
                    """, applicationId);
        }
    }

    public List<Map<String, Object>> list(HrBoardQueryDTO query) {
        HrBoardQueryDTO q = query == null ? new HrBoardQueryDTO() : query;
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.application_id, i.round_no, i.interviewer_user_id, u.nickname interviewer_name,
                       i.interview_at, i.duration_min, i.location, i.status, i.fail_reason, i.dingtalk_event_id,
                       c.display_name, a.requisition_id, a.current_stage, r.job_name, f.file_name,
                       (SELECT GROUP_CONCAT(cc.cc_user_id ORDER BY cc.id SEPARATOR '|')
                        FROM hr_interview_invite_cc cc
                        WHERE cc.invite_id = i.id AND cc.is_active = 1) cc_user_ids,
                       (SELECT GROUP_CONCAT(cu.nickname ORDER BY cc.id SEPARATOR '、')
                        FROM hr_interview_invite_cc cc
                        LEFT JOIN sys_user cu ON cu.user_id = cc.cc_user_id
                        WHERE cc.invite_id = i.id AND cc.is_active = 1) cc_names,
                       mine_rec.id record_id, mine_rec.conclusion my_conclusion, mine_rec.fail_reason my_fail_reason, mine_rec.comment my_comment,
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
        validateInterviewAt(dto.getInterviewAt());
        java.util.List<Long> desired = interviewerIds(dto);
        Map<String, Object> anchor = loadInvite(id);
        releaseCcCalendarsForSession((Long) anchor.get("applicationId"),
                ((Number) anchor.get("roundNo")).intValue(),
                (LocalDateTime) anchor.get("interviewAt"));
        releaseSessionCalendars((Long) anchor.get("applicationId"),
                ((Number) anchor.get("roundNo")).intValue(),
                (LocalDateTime) anchor.get("interviewAt"));
        java.util.List<Map<String, Object>> peers = loadSession(anchor);
        java.util.Map<Long, Map<String, Object>> byInterviewer = new java.util.HashMap<>();
        for (Map<String, Object> peer : peers) {
            byInterviewer.putIfAbsent((Long) peer.get("interviewerUserId"), peer);
        }
        java.util.List<String> cancelMisses = new ArrayList<>();
        java.util.List<Long> keepInviteIds = new ArrayList<>();
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        for (Long interviewerId : desired) {
            Map<String, Object> existing = byInterviewer.get(interviewerId);
            if (existing == null) {
                keepInviteIds.add(createOneRecord(copyDto(dto, interviewerId), duration));
                continue;
            }
            Long inviteId = (Long) existing.get("id");
            assertNoInterviewRecord(inviteId, "修改");
            jdbc.update("""
                    UPDATE hr_interview_invite
                    SET application_id = ?, round_no = ?, interviewer_user_id = ?, interview_at = ?, duration_min = ?, location = ?,
                        status = 'FAILED', fail_reason = NULL, dingtalk_event_id = NULL, dingtalk_calendar_id = NULL
                    WHERE id = ? AND is_active = 1
                    """, dto.getApplicationId(), dto.getRoundNo(), interviewerId, dto.getInterviewAt(),
                    duration, dto.getLocation(), inviteId);
            replaceInviteCc(inviteId, resolveCcUserIds(dto));
            keepInviteIds.add(inviteId);
        }
        java.util.Set<Long> kept = new java.util.HashSet<>(keepInviteIds);
        for (Map<String, Object> peer : peers) {
            Long peerId = (Long) peer.get("id");
            if (!kept.contains(peerId)) {
                HrInviteSaveVO removed = removeWithoutCalendarRelease(peerId);
                if (StringUtils.hasText(removed.getWarning())) {
                    cancelMisses.add(removed.getWarning());
                }
            }
        }
        String warning;
        if (!AUTO_CREATE_DINGTALK_CALENDAR) {
            finalizeInviteWithoutCalendar(keepInviteIds, dto);
            warning = null;
        } else {
            java.util.List<String> unbound = attachSessionCalendars(keepInviteIds, dto, duration);
            boolean calendarOk = unbound.size() < desired.size();
            if (calendarOk && !keepInviteIds.isEmpty()) {
                notifyRoundCc(keepInviteIds.getFirst(), dto);
            }
            warning = unboundMessage(unbound);
        }
        if (!cancelMisses.isEmpty()) {
            warning = joinWarning(String.join("；", cancelMisses), warning);
        }
        return saveResult(id, warning);
    }

    /** 删除邀约行（日历已在外层按场次释放）。 */
    private HrInviteSaveVO removeWithoutCalendarRelease(Long id) {
        assertNoInterviewRecord(id, "删除");
        Map<String, Object> invite = loadInvite(id);
        Long applicationId = (Long) invite.get("applicationId");
        int roundNo = ((Number) invite.get("roundNo")).intValue();
        LocalDateTime interviewAt = (LocalDateTime) invite.get("interviewAt");
        jdbc.update("UPDATE hr_interview_invite SET is_active = 0, status = 'CANCELLED' WHERE id = ?", id);
        jdbc.update("UPDATE hr_interview_invite_cc SET is_active = 0 WHERE invite_id = ?", id);
        Integer peersLeft = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                """, Integer.class, applicationId, roundNo, interviewAt);
        if (peersLeft == null || peersLeft == 0) {
            releaseCcCalendarsForSession(applicationId, roundNo, interviewAt);
        }
        return saveResult(id, null);
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

    @Transactional
    public HrInviteSaveVO remove(Long id) {
        assertNoInterviewRecord(id, "删除");
        Map<String, Object> invite = loadInvite(id);
        Long applicationId = (Long) invite.get("applicationId");
        int roundNo = ((Number) invite.get("roundNo")).intValue();
        LocalDateTime interviewAt = (LocalDateTime) invite.get("interviewAt");
        String unboundName = releaseCalendar(id, (Long) invite.get("interviewerUserId"), (String) invite.get("eventId"));
        String warning = StringUtils.hasText(unboundName)
                ? "删除面试邀约成功，但是由于面试官" + unboundName + "没有绑定钉钉，未能取消钉钉日程"
                : null;
        jdbc.update("UPDATE hr_interview_invite SET is_active = 0, status = 'CANCELLED' WHERE id = ?", id);
        jdbc.update("UPDATE hr_interview_invite_cc SET is_active = 0 WHERE invite_id = ?", id);
        Integer peersLeft = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                """, Integer.class, applicationId, roundNo, interviewAt);
        if (peersLeft == null || peersLeft == 0) {
            releaseCcCalendarsForSession(applicationId, roundNo, interviewAt);
        }
        return saveResult(id, warning);
    }

    @Transactional
    public HrInviteSaveVO removeBatch(java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的面试邀约");
        }
        java.util.List<String> warnings = new ArrayList<>();
        int removed = 0;
        Long last = null;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (hasInterviewRecord(id)) {
                warnings.add("已有面试记录的邀约不能删除");
                continue;
            }
            HrInviteSaveVO one = remove(id);
            last = one.getId();
            removed++;
            if (StringUtils.hasText(one.getWarning())) {
                warnings.add(one.getWarning());
            }
        }
        if (removed == 0) {
            throw new BusinessException(warnings.isEmpty() ? "没有可删除的面试邀约" : String.join("；", warnings));
        }
        return saveResult(last, warnings.isEmpty() ? null : String.join("；", warnings));
    }

    @Transactional
    public void createCalendar(Long id) {
        createCalendar(id, true);
    }

    private void createCalendar(Long id, boolean notifyCc) {
        Map<String, Object> invite = loadInvite(id);
        assertCalendarCreatable(invite);
        Long interviewerUserId = (Long) invite.get("interviewerUserId");
        if (!StringUtils.hasText(findUnionId(interviewerUserId))) {
            throw new BusinessException("面试官" + interviewerName(interviewerUserId) + "没有绑定钉钉，不能创建钉钉日程，请联系行政绑定");
        }
        // 同场未建日程的邀约一并挂到同一条创建人日程上，避免创建人收到多份
        java.util.List<Long> sessionIds = jdbc.query("""
                SELECT id FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                  AND status IN ('FAILED', 'NO_CALENDAR') AND (dingtalk_event_id IS NULL OR dingtalk_event_id = '')
                ORDER BY id
                """, (rs, rowNum) -> rs.getLong(1),
                invite.get("applicationId"), invite.get("roundNo"), invite.get("interviewAt"));
        if (sessionIds.isEmpty()) {
            sessionIds = List.of(id);
        } else if (!sessionIds.contains(id)) {
            sessionIds = new ArrayList<>(sessionIds);
            sessionIds.add(0, id);
        }
        HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
        int duration = dto.getDurationMin() == null ? 60 : dto.getDurationMin();
        java.util.List<String> unbound = attachSessionCalendars(sessionIds, dto, duration);
        String status = jdbc.query("SELECT status FROM hr_interview_invite WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (!"SUCCESS".equals(status)) {
            if (!unbound.isEmpty()) {
                throw new BusinessException("面试官" + unbound.getFirst() + "没有绑定钉钉，不能创建钉钉日程，请联系行政绑定");
            }
            String reason = jdbc.query("SELECT fail_reason FROM hr_interview_invite WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, id);
            throw new BusinessException(StringUtils.hasText(reason) ? reason : "钉钉日程创建失败");
        }
        if (notifyCc) {
            notifyRoundCc(id, dto);
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
        java.util.Set<String> handledSessions = new java.util.HashSet<>();
        java.util.Set<String> ccSessions = new java.util.HashSet<>();
        java.util.Set<Long> countedCreated = new java.util.HashSet<>();
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
            if (hasInterviewRecord(id)) {
                problems.add("已有面试记录的邀约不能创建钉钉日程");
                continue;
            }
            if ("SUCCESS".equals(status) && StringUtils.hasText(eventId)) {
                skipped++;
                continue;
            }
            String sessionKey = invite.get("applicationId") + "|" + invite.get("roundNo") + "|" + invite.get("interviewAt");
            if (!handledSessions.add(sessionKey)) {
                String after = jdbc.query("SELECT status FROM hr_interview_invite WHERE id = ?",
                        rs -> rs.next() ? rs.getString(1) : null, id);
                if ("SUCCESS".equals(after) && countedCreated.add(id)) {
                    created++;
                } else if ("NO_CALENDAR".equals(after)) {
                    unbound.add(interviewerName((Long) invite.get("interviewerUserId")));
                }
                continue;
            }
            Long interviewerUserId = (Long) invite.get("interviewerUserId");
            try {
                createCalendar(id, false);
                List<Long> successIds = jdbc.query("""
                        SELECT id FROM hr_interview_invite
                        WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                          AND status = 'SUCCESS' AND dingtalk_event_id IS NOT NULL AND dingtalk_event_id <> ''
                        """, (rs, rowNum) -> rs.getLong(1),
                        invite.get("applicationId"), invite.get("roundNo"), invite.get("interviewAt"));
                for (Long sid : successIds) {
                    if (countedCreated.add(sid)) {
                        created++;
                    }
                }
                List<String> sessionUnbound = jdbc.query("""
                        SELECT COALESCE(NULLIF(u.nickname, ''), u.username)
                        FROM hr_interview_invite i
                        JOIN sys_user u ON u.user_id = i.interviewer_user_id
                        WHERE i.application_id = ? AND i.round_no = ? AND i.is_active = 1 AND i.interview_at <=> ?
                          AND i.status = 'NO_CALENDAR'
                        """, (rs, rowNum) -> rs.getString(1),
                        invite.get("applicationId"), invite.get("roundNo"), invite.get("interviewAt"));
                unbound.addAll(sessionUnbound);
                HrInviteCreateDTO dto = copyInvite(invite, interviewerUserId);
                if (ccSessions.add(sessionKey)) {
                    notifyRoundCc(id, dto);
                }
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
        java.util.List<String> unboundNames = unbound.stream().filter(StringUtils::hasText).distinct().toList();
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
        assertNoInterviewRecord(((Number) invite.get("id")).longValue(), "创建钉钉日程");
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

    private void assertNoInterviewRecord(Long inviteId, String action) {
        if (hasInterviewRecord(inviteId)) {
            throw new BusinessException("这条邀约已有面试记录，不能" + action);
        }
    }

    private boolean hasInterviewRecord(Long inviteId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1)
                FROM hr_interview_invite i
                JOIN hr_interview_record rec ON rec.is_active = 1
                  AND (
                    rec.invite_id = i.id
                    OR (rec.application_id = i.application_id AND rec.round_no = i.round_no
                        AND rec.interviewer_user_id = i.interviewer_user_id)
                  )
                WHERE i.id = ? AND i.is_active = 1
                """, Integer.class, inviteId);
        return count != null && count > 0;
    }

    private HrInviteCreateDTO copyInvite(Map<String, Object> invite, Long interviewerUserId) {
        HrInviteCreateDTO dto = new HrInviteCreateDTO();
        dto.setApplicationId((Long) invite.get("applicationId"));
        dto.setRoundNo(((Number) invite.get("roundNo")).intValue());
        dto.setInterviewerUserId(interviewerUserId);
        dto.setInterviewAt((LocalDateTime) invite.get("interviewAt"));
        dto.setDurationMin(invite.get("durationMin") == null ? 60 : ((Number) invite.get("durationMin")).intValue());
        dto.setLocation((String) invite.get("location"));
        Long inviteId = invite.get("id") == null ? null : ((Number) invite.get("id")).longValue();
        dto.setCcUserIds(inviteId == null ? List.of() : loadInviteCcIds(inviteId));
        return dto;
    }

    private Map<String, Object> loadInvite(Long inviteId) {
        Map<String, Object> invite = jdbc.query("""
                SELECT i.id, i.application_id, i.round_no, i.interviewer_user_id, i.status, i.dingtalk_event_id,
                       i.dingtalk_calendar_id, i.invited_by, i.interview_at, i.duration_min, i.location, a.requisition_id
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
            row.put("calendarId", rs.getString("dingtalk_calendar_id"));
            row.put("invitedBy", rs.getObject("invited_by") == null ? null : rs.getLong("invited_by"));
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
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        Map<String, Object> invite = jdbc.query("""
                SELECT id, interviewer_user_id, invited_by, dingtalk_event_id, dingtalk_calendar_id,
                       application_id, round_no, interview_at, duration_min, location, status
                FROM hr_interview_invite WHERE id = ? AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("interviewerUserId", rs.getLong("interviewer_user_id"));
            row.put("invitedBy", rs.getObject("invited_by") == null ? null : rs.getLong("invited_by"));
            row.put("eventId", rs.getString("dingtalk_event_id"));
            row.put("calendarId", rs.getString("dingtalk_calendar_id"));
            row.put("applicationId", rs.getLong("application_id"));
            row.put("roundNo", rs.getInt("round_no"));
            row.put("interviewAt", rs.getTimestamp("interview_at") == null ? null : rs.getTimestamp("interview_at").toLocalDateTime());
            row.put("durationMin", rs.getObject("duration_min") == null ? 60 : rs.getInt("duration_min"));
            row.put("location", rs.getString("location"));
            row.put("status", rs.getString("status"));
            return row;
        }, inviteId);
        if (invite == null) {
            invite = new java.util.HashMap<>();
            invite.put("id", inviteId);
            invite.put("interviewerUserId", interviewerUserId);
            invite.put("eventId", eventId);
            invite.put("calendarId", "primary");
        }
        DingTalkCalendarClient.CalendarCall call = releaseSharedCalendarEvent(invite, false);
        writeLog(inviteId, "CANCEL_CALENDAR", call == null
                        ? DingTalkCalendarClient.CalendarCall.ok(eventId, "shared-keep")
                        : call,
                SecurityUtils.getCurrentUserId(), eventId, Map.of("eventId", eventId));
        if (call != null && !call.success()) {
            Long ownerInterviewer = invite.get("interviewerUserId") == null
                    ? interviewerUserId : (Long) invite.get("interviewerUserId");
            return interviewerName(ownerInterviewer);
        }
        return null;
    }

    /**
     * 释放共享日程：同 eventId 还有其它有效邀约时只摘掉当前人并刷新参与人；最后一条才真正删钉钉日程。
     * @return null 表示无需删除或已跳过；否则返回删除调用结果
     */
    private DingTalkCalendarClient.CalendarCall releaseSharedCalendarEvent(Map<String, Object> invite, boolean refreshAttendees) {
        String eventId = (String) invite.get("eventId");
        if (!StringUtils.hasText(eventId)) {
            return DingTalkCalendarClient.CalendarCall.ok(null, "no-event");
        }
        Long inviteId = ((Number) invite.get("id")).longValue();
        Integer siblings = jdbc.queryForObject("""
                SELECT COUNT(1) FROM hr_interview_invite
                WHERE dingtalk_event_id = ? AND is_active = 1 AND status = 'SUCCESS' AND id <> ?
                """, Integer.class, eventId, inviteId);
        String ownerUnionId = resolveCalendarOwnerUnionId(invite);
        if (siblings != null && siblings > 0) {
            if (refreshAttendees && StringUtils.hasText(ownerUnionId)) {
                refreshSharedEventAttendees(invite, ownerUnionId, eventId, inviteId);
            }
            return DingTalkCalendarClient.CalendarCall.ok(eventId, "shared-keep");
        }
        if (!StringUtils.hasText(ownerUnionId)) {
            return DingTalkCalendarClient.CalendarCall.fail("日程所有者未绑定钉钉，未取消日程");
        }
        return dingTalk.deleteEvent(ownerUnionId, eventId);
    }

    private void refreshSharedEventAttendees(Map<String, Object> invite, String ownerUnionId, String eventId, Long excludeInviteId) {
        java.util.LinkedHashSet<String> attendees = new java.util.LinkedHashSet<>();
        List<String> interviewers = jdbc.query("""
                SELECT d.dingtalk_union_id
                FROM hr_interview_invite i
                JOIN hr_user_dingtalk d ON d.user_id = i.interviewer_user_id AND d.is_active = 1
                WHERE i.dingtalk_event_id = ? AND i.is_active = 1 AND i.status = 'SUCCESS' AND i.id <> ?
                  AND d.dingtalk_union_id IS NOT NULL AND d.dingtalk_union_id <> ''
                """, (rs, rowNum) -> rs.getString(1), eventId, excludeInviteId);
        attendees.addAll(interviewers);
        List<String> ccs = jdbc.query("""
                SELECT DISTINCT d.dingtalk_union_id
                FROM hr_interview_invite_cc cc
                JOIN hr_interview_invite i ON i.id = cc.invite_id AND i.is_active = 1
                JOIN hr_user_dingtalk d ON d.user_id = cc.cc_user_id AND d.is_active = 1
                WHERE i.dingtalk_event_id = ? AND cc.is_active = 1
                  AND d.dingtalk_union_id IS NOT NULL AND d.dingtalk_union_id <> ''
                """, (rs, rowNum) -> rs.getString(1), eventId);
        attendees.addAll(ccs);
        attendees.remove(ownerUnionId);
        LocalDateTime start = (LocalDateTime) invite.get("interviewAt");
        int duration = invite.get("durationMin") == null ? 60 : ((Number) invite.get("durationMin")).intValue();
        String location = blank(invite.get("location"));
        String title = "面试邀约";
        dingTalk.updateEvent(ownerUnionId, eventId, title, "", start == null ? LocalDateTime.now() : start,
                duration, location, new ArrayList<>(attendees), false);
    }

    private String resolveCalendarOwnerUnionId(Map<String, Object> invite) {
        String calendarId = (String) invite.get("calendarId");
        if ("organizer".equals(calendarId)) {
            Long invitedBy = invite.get("invitedBy") == null ? SecurityUtils.getCurrentUserId() : (Long) invite.get("invitedBy");
            return findUnionId(invitedBy);
        }
        Long interviewerUserId = (Long) invite.get("interviewerUserId");
        return findUnionId(interviewerUserId);
    }

    /** 释放同场全部钉钉日程（按 eventId 去重删除一次）。 */
    private void releaseSessionCalendars(Long applicationId, Integer roundNo, LocalDateTime interviewAt) {
        if (applicationId == null || roundNo == null) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT id, interviewer_user_id, invited_by, dingtalk_event_id, dingtalk_calendar_id,
                       interview_at, duration_min, location
                FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                  AND dingtalk_event_id IS NOT NULL AND dingtalk_event_id <> ''
                """, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("interviewerUserId", rs.getLong("interviewer_user_id"));
            row.put("invitedBy", rs.getObject("invited_by") == null ? null : rs.getLong("invited_by"));
            row.put("eventId", rs.getString("dingtalk_event_id"));
            row.put("calendarId", rs.getString("dingtalk_calendar_id"));
            row.put("interviewAt", rs.getTimestamp("interview_at") == null ? null : rs.getTimestamp("interview_at").toLocalDateTime());
            row.put("durationMin", rs.getObject("duration_min") == null ? 60 : rs.getInt("duration_min"));
            row.put("location", rs.getString("location"));
            return row;
        }, applicationId, roundNo, interviewAt);
        java.util.Set<String> deleted = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            String eventId = (String) row.get("eventId");
            if (!StringUtils.hasText(eventId) || !deleted.add(eventId)) {
                continue;
            }
            String ownerUnionId = resolveCalendarOwnerUnionId(row);
            if (StringUtils.hasText(ownerUnionId)) {
                dingTalk.deleteEvent(ownerUnionId, eventId);
            }
        }
        jdbc.update("""
                UPDATE hr_interview_invite
                SET dingtalk_event_id = NULL, dingtalk_calendar_id = NULL
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                """, applicationId, roundNo, interviewAt);
    }

    /**
     * 同场多面试官：优先在创建人主日历建一条日程，所有面试官作为参与人，避免创建人收到 N 份相同日程。
     * 创建人未绑钉钉时回退为每人各自日历建一条（不再把创建人加为参与人）。
     * @return 未绑定钉钉的面试官姓名列表
     */
    private java.util.List<String> attachSessionCalendars(java.util.List<Long> inviteIds, HrInviteCreateDTO dto, int duration) {
        java.util.List<String> unbound = new ArrayList<>();
        if (inviteIds == null || inviteIds.isEmpty()) {
            return unbound;
        }
        Map<String, Object> basePerson = loadInvitePerson(null, dto.getApplicationId());
        if (basePerson == null) {
            throw new BusinessException("投递不存在");
        }
        String organizerName = currentOrganizerName();
        String title = "【" + ROUND_NAME.getOrDefault(dto.getRoundNo(), dto.getRoundNo() + "面") + "】"
                + basePerson.get("candidate_name") + " - " + basePerson.get("job_name");
        ResumeFile resume = loadResume(dto.getApplicationId());
        if (resume == null) {
            throw new BusinessException("面试日程必须附带简历，请先为候选人上传简历");
        }
        String description = buildCalendarDescription(dto, basePerson, resume, organizerName);

        java.util.List<Long> boundInviteIds = new ArrayList<>();
        java.util.List<String> attendeeUnionIds = new ArrayList<>();
        java.util.LinkedHashMap<Long, Map<String, Object>> personByInvite = new java.util.LinkedHashMap<>();
        for (Long inviteId : inviteIds) {
            if (inviteId == null) {
                continue;
            }
            Long interviewerUserId = jdbc.query("SELECT interviewer_user_id FROM hr_interview_invite WHERE id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, inviteId);
            if (interviewerUserId == null) {
                continue;
            }
            dingTalkScheduleRuleService.assertBookable(
                    interviewerUserId, DingTalkScheduleRuleService.ACTION_INTERVIEW,
                    dto.getInterviewAt(), duration, true);
            Map<String, Object> person = loadInvitePerson(interviewerUserId, dto.getApplicationId());
            if (person == null) {
                markNoCalendar(inviteId);
                unbound.add(interviewerName(interviewerUserId));
                continue;
            }
            personByInvite.put(inviteId, person);
            String unionId = findUnionId(interviewerUserId);
            if (!StringUtils.hasText(unionId)) {
                markNoCalendar(inviteId);
                writeRoundAndStage(copyDto(dto, interviewerUserId), person);
                unbound.add(interviewerName(interviewerUserId));
                continue;
            }
            boundInviteIds.add(inviteId);
            if (!attendeeUnionIds.contains(unionId)) {
                attendeeUnionIds.add(unionId);
            }
        }
        // 抄送人并入同一条日程的参与人，不再单独建抄送日程
        java.util.Set<Long> interviewerSet = new java.util.HashSet<>();
        for (Long inviteId : boundInviteIds) {
            Long uid = jdbc.query("SELECT interviewer_user_id FROM hr_interview_invite WHERE id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, inviteId);
            if (uid != null) {
                interviewerSet.add(uid);
            }
        }
        for (String ccUnionId : resolveCcAttendeeUnionIds(dto, boundInviteIds.isEmpty() ? null : boundInviteIds.getFirst(), interviewerSet)) {
            if (!attendeeUnionIds.contains(ccUnionId)) {
                attendeeUnionIds.add(ccUnionId);
            }
        }
        if (boundInviteIds.isEmpty()) {
            return unbound;
        }

        Long organizerUserId = SecurityUtils.getCurrentUserId();
        String organizerUnionId = findUnionId(organizerUserId);
        // 优先创建人日历；创建人未绑定时用第一位面试官日历。参与人 = 其余面试官 + 抄送人
        String ownerUnionId = StringUtils.hasText(organizerUnionId) ? organizerUnionId : attendeeUnionIds.getFirst();
        String calendarMode = StringUtils.hasText(organizerUnionId) ? "organizer" : "primary";
        List<String> attendees = attendeeUnionIds.stream()
                .filter(id -> !id.equals(ownerUnionId))
                .toList();
        DingTalkCalendarClient.CalendarCall call = dingTalk.createEvent(
                ownerUnionId, title, description, dto.getInterviewAt(), duration, dto.getLocation(), attendees, false);
        Long logInviteId = boundInviteIds.getFirst();
        writeLog(logInviteId, "CREATE_CALENDAR", call, organizerUserId, call.eventId(),
                Map.of("title", title, "start", String.valueOf(dto.getInterviewAt()), "unionId", blank(ownerUnionId),
                        "organizer", organizerName, "attendees", String.valueOf(attendees.size()), "mode", "shared-" + calendarMode));
        if (!call.success()) {
            for (Long inviteId : boundInviteIds) {
                jdbc.update("UPDATE hr_interview_invite SET status = 'FAILED', fail_reason = ? WHERE id = ?",
                        cut(call.message()), inviteId);
            }
            return unbound;
        }
        for (Long inviteId : boundInviteIds) {
            jdbc.update("""
                    UPDATE hr_interview_invite
                    SET status = 'SUCCESS', dingtalk_event_id = ?, dingtalk_calendar_id = ?, fail_reason = NULL
                    WHERE id = ?
                    """, call.eventId(), calendarMode, inviteId);
            Map<String, Object> person = personByInvite.get(inviteId);
            Long interviewerUserId = jdbc.query("SELECT interviewer_user_id FROM hr_interview_invite WHERE id = ?",
                    rs -> rs.next() ? rs.getLong(1) : null, inviteId);
            HrInviteCreateDTO one = copyDto(dto, interviewerUserId);
            writeRoundAndStage(one, person);
            notifyInterviewer(inviteId, one, person, resume);
        }
        return unbound;
    }

    /** 抄送人钉钉 unionId（排除面试官）；未绑定的抄送人跳过，仅影响日程参与人。 */
    private List<String> resolveCcAttendeeUnionIds(HrInviteCreateDTO dto, Long inviteId, java.util.Set<Long> interviewerIds) {
        List<Long> ccIds = resolveCcUserIds(dto);
        if (ccIds.isEmpty() && inviteId != null && dto.getCcUserIds() == null) {
            ccIds = loadInviteCcIds(inviteId);
        }
        if (ccIds.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> unions = new java.util.LinkedHashSet<>();
        for (Long ccUserId : ccIds) {
            if (ccUserId == null || (interviewerIds != null && interviewerIds.contains(ccUserId))) {
                continue;
            }
            String unionId = findUnionId(ccUserId);
            if (StringUtils.hasText(unionId)) {
                unions.add(unionId);
            }
        }
        return new ArrayList<>(unions);
    }

    private Map<String, Object> loadInvitePerson(Long interviewerUserId, Long applicationId) {
        if (interviewerUserId == null) {
            return jdbc.query("""
                    SELECT c.display_name candidate_name, COALESCE(r.job_name, '') job_name, c.phone, c.email,
                           '' nickname, '' interviewer_phone
                    FROM hr_application a
                    JOIN hr_candidate c ON c.id = a.candidate_id
                    LEFT JOIN hr_requisition r ON r.id = a.requisition_id
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
            }, applicationId);
        }
        return jdbc.query("""
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
        }, interviewerUserId, applicationId);
    }

    private HrInviteCreateDTO copyDto(HrInviteCreateDTO dto, Long interviewerUserId) {
        HrInviteCreateDTO one = new HrInviteCreateDTO();
        one.setApplicationId(dto.getApplicationId());
        one.setRoundNo(dto.getRoundNo());
        one.setInterviewerUserId(interviewerUserId);
        one.setInterviewAt(dto.getInterviewAt());
        one.setDurationMin(dto.getDurationMin());
        one.setLocation(dto.getLocation());
        one.setCcUserIds(dto.getCcUserIds());
        return one;
    }

    private String attachCalendar(Long inviteId, HrInviteCreateDTO dto, int duration) {
        java.util.List<String> unbound = attachSessionCalendars(List.of(inviteId), dto, duration);
        return unbound.isEmpty() ? null : unbound.getFirst();
    }

    private void notifyInterviewer(Long inviteId, HrInviteCreateDTO dto, Map<String, Object> person, ResumeFile resume) {
        notifyWorkNotice(inviteId, dto.getInterviewerUserId(), person, dto, resume, "NOTIFY_INTERVIEWER",
                currentOrganizerName());
    }

    /** 抄送人只发工作通知，并回写共享日程 eventId；不再单独建抄送日程。 */
    private void notifyRoundCc(Long inviteId, HrInviteCreateDTO dto) {
        if (inviteId == null || dto.getApplicationId() == null || dto.getRoundNo() == null) {
            return;
        }
        List<Long> ccIds = resolveCcUserIds(dto);
        if (ccIds.isEmpty() && dto.getCcUserIds() == null) {
            ccIds = loadInviteCcIds(inviteId);
        }
        if (ccIds.isEmpty()) {
            return;
        }
        java.util.Set<Long> interviewers = new java.util.HashSet<>(interviewerIds(dto));
        interviewers.addAll(jdbc.query("""
                SELECT interviewer_user_id FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND is_active = 1 AND interview_at <=> ?
                """, (rs, rowNum) -> rs.getLong(1), dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewAt()));
        Map<String, Object> person = jdbc.query("""
                SELECT c.display_name candidate_name, COALESCE(r.job_name, '') job_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                WHERE a.id = ? AND a.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("candidate_name", rs.getString("candidate_name"));
            row.put("job_name", rs.getString("job_name"));
            return row;
        }, dto.getApplicationId());
        if (person == null) {
            return;
        }
        ResumeFile resume = loadResume(dto.getApplicationId());
        String organizerName = currentOrganizerName();
        // 清理历史「每人一条抄送日程」；与面试共享的 eventId 不会被误删
        releaseCcCalendarsForSession(dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewAt());
        String sharedEventId = jdbc.query("""
                SELECT dingtalk_event_id FROM hr_interview_invite
                WHERE id = ? AND is_active = 1 AND status = 'SUCCESS'
                  AND dingtalk_event_id IS NOT NULL AND dingtalk_event_id <> ''
                """, rs -> rs.next() ? rs.getString(1) : null, inviteId);
        for (Long ccUserId : ccIds) {
            if (ccUserId == null || interviewers.contains(ccUserId)) {
                continue;
            }
            if (StringUtils.hasText(sharedEventId)) {
                saveCcEventIdForSession(dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewAt(), ccUserId, sharedEventId);
                writeLog(inviteId, "LINK_CC_CALENDAR",
                        DingTalkCalendarClient.CalendarCall.ok(sharedEventId, "shared-attendee"),
                        SecurityUtils.getCurrentUserId(), sharedEventId,
                        Map.of("ccUserId", String.valueOf(ccUserId), "mode", "shared-attendee"));
            } else if (!StringUtils.hasText(findUnionId(ccUserId))) {
                writeLog(inviteId, "LINK_CC_CALENDAR",
                        DingTalkCalendarClient.CalendarCall.fail("抄送人未绑定钉钉，未加入共享日程"),
                        SecurityUtils.getCurrentUserId(), null,
                        Map.of("ccUserId", String.valueOf(ccUserId)));
            }
            notifyWorkNotice(inviteId, ccUserId, person, dto, resume, "NOTIFY_CC", organizerName);
        }
    }

    private void saveCcEventIdForSession(Long applicationId, Integer roundNo, LocalDateTime interviewAt,
                                         Long ccUserId, String eventId) {
        if (applicationId == null || roundNo == null || ccUserId == null) {
            return;
        }
        jdbc.update("""
                UPDATE hr_interview_invite_cc cc
                JOIN hr_interview_invite i ON i.id = cc.invite_id
                SET cc.dingtalk_event_id = ?
                WHERE i.application_id = ? AND i.round_no = ? AND i.is_active = 1 AND i.interview_at <=> ?
                  AND cc.cc_user_id = ? AND cc.is_active = 1
                """, eventId, applicationId, roundNo, interviewAt, ccUserId);
    }

    /**
     * 清理抄送人侧历史独立日程。
     * 若 eventId 与同场面试共享日程相同，只清空字段，不调用删除（由面试日程释放路径统一处理）。
     */
    private void releaseCcCalendarsForSession(Long applicationId, Integer roundNo, LocalDateTime interviewAt) {
        if (applicationId == null || roundNo == null) {
            return;
        }
        java.util.Set<String> sharedInviteEventIds = new java.util.HashSet<>(jdbc.query("""
                SELECT DISTINCT dingtalk_event_id FROM hr_interview_invite
                WHERE application_id = ? AND round_no = ? AND interview_at <=> ?
                  AND dingtalk_event_id IS NOT NULL AND dingtalk_event_id <> ''
                  AND status IN ('SUCCESS', 'CANCEL_FAILED')
                """, (rs, rowNum) -> rs.getString(1), applicationId, roundNo, interviewAt));
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT DISTINCT cc.cc_user_id, cc.dingtalk_event_id, cc.invite_id
                FROM hr_interview_invite_cc cc
                JOIN hr_interview_invite i ON i.id = cc.invite_id
                WHERE i.application_id = ? AND i.round_no = ? AND i.interview_at <=> ?
                  AND cc.dingtalk_event_id IS NOT NULL AND cc.dingtalk_event_id <> ''
                """, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("ccUserId", rs.getLong("cc_user_id"));
            row.put("eventId", rs.getString("dingtalk_event_id"));
            row.put("inviteId", rs.getLong("invite_id"));
            return row;
        }, applicationId, roundNo, interviewAt);
        java.util.Set<String> done = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            String eventId = (String) row.get("eventId");
            if (!StringUtils.hasText(eventId) || !done.add(eventId)) {
                continue;
            }
            if (sharedInviteEventIds.contains(eventId)) {
                continue;
            }
            Long ccUserId = (Long) row.get("ccUserId");
            Long inviteId = (Long) row.get("inviteId");
            String unionId = findUnionId(ccUserId);
            if (!StringUtils.hasText(unionId)) {
                writeLog(inviteId, "CANCEL_CC_CALENDAR",
                        DingTalkCalendarClient.CalendarCall.fail("抄送人未绑定钉钉，未取消日程"),
                        SecurityUtils.getCurrentUserId(), eventId,
                        Map.of("eventId", eventId, "ccUserId", String.valueOf(ccUserId)));
                continue;
            }
            DingTalkCalendarClient.CalendarCall call = dingTalk.deleteEvent(unionId, eventId);
            writeLog(inviteId, "CANCEL_CC_CALENDAR", call, SecurityUtils.getCurrentUserId(), eventId,
                    Map.of("eventId", eventId, "ccUserId", String.valueOf(ccUserId)));
        }
        jdbc.update("""
                UPDATE hr_interview_invite_cc cc
                JOIN hr_interview_invite i ON i.id = cc.invite_id
                SET cc.dingtalk_event_id = NULL
                WHERE i.application_id = ? AND i.round_no = ? AND i.interview_at <=> ?
                """, applicationId, roundNo, interviewAt);
    }

    private List<Long> resolveCcUserIds(HrInviteCreateDTO dto) {
        if (dto.getCcUserIds() != null) {
            return dto.getCcUserIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        }
        if (dto.getApplicationId() == null || dto.getRoundNo() == null) {
            return List.of();
        }
        Long requisitionId = jdbc.query("""
                SELECT requisition_id FROM hr_application WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getObject(1, Long.class) : null, dto.getApplicationId());
        if (requisitionId == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT cc_user_id FROM hr_requisition_round_cc
                WHERE requisition_id = ? AND round_no = ? AND is_active = 1
                ORDER BY id
                """, (rs, rowNum) -> rs.getLong(1), requisitionId, dto.getRoundNo());
    }

    private List<Long> loadInviteCcIds(Long inviteId) {
        if (inviteId == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT cc_user_id FROM hr_interview_invite_cc
                WHERE invite_id = ? AND is_active = 1
                ORDER BY id
                """, (rs, rowNum) -> rs.getLong(1), inviteId);
    }

    private void replaceInviteCc(Long inviteId, List<Long> ccUserIds) {
        if (inviteId == null) {
            return;
        }
        jdbc.update("UPDATE hr_interview_invite_cc SET is_active = 0 WHERE invite_id = ?", inviteId);
        if (ccUserIds == null || ccUserIds.isEmpty()) {
            return;
        }
        for (Long userId : ccUserIds) {
            if (userId == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO hr_interview_invite_cc (invite_id, cc_user_id, create_by, is_active)
                    VALUES (?, ?, ?, 1)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """, inviteId, userId, SecurityUtils.getCurrentUsername());
        }
    }

    private void notifyWorkNotice(Long inviteId, Long userId, Map<String, Object> person, HrInviteCreateDTO dto,
                                  ResumeFile resume, String action, String organizerName) {
        String dingUserId = findDingUserId(userId);
        String title = "NOTIFY_CC".equals(action) ? "面试邀约抄送通知" : "面试邀约通知";
        String round = ROUND_NAME.getOrDefault(dto.getRoundNo(), dto.getRoundNo() + "面");
        String when = formatInterviewAt(dto.getInterviewAt());
        String organizer = StringUtils.hasText(organizerName) ? organizerName : currentOrganizerName();
        String markdown = "### " + title + "\n\n"
                + "- **组织人**：" + organizer + "\n"
                + "- **候选人**：" + blank(person.get("candidate_name")) + "\n"
                + "- **岗位**：" + blank(person.get("job_name")) + "\n"
                + "- **轮次**：" + round + "\n"
                + "- **时间**：" + when + "\n";
        if (resume == null) {
            markdown += "\n简历：未上传\n";
        } else if (resume.path() != null && Files.isRegularFile(resume.path())) {
            markdown += "\n简历：见下一条文件消息（" + resume.fileName() + "）\n";
        } else {
            markdown += "\n简历：" + resume.fileName() + "（已上传，请在系统中查看）\n";
        }
        Path resumePath = resume == null ? null : resume.path();
        String resumeName = resume == null ? null : resume.fileName();
        DingTalkCalendarClient.NoticeCall notice = dingTalk.notifyInterview(
                dingUserId, title, markdown, resumePath, resumeName);
        writeLog(inviteId, action,
                notice.success()
                        ? DingTalkCalendarClient.CalendarCall.ok(null,
                        notice.resumeSent() ? "{\"resumeSent\":true}" : "{\"resumeSent\":false}")
                        : DingTalkCalendarClient.CalendarCall.fail(notice.message()),
                SecurityUtils.getCurrentUserId(), null,
                Map.of("dingUserId", blank(dingUserId), "userId", String.valueOf(userId),
                        "resume", resumeName == null ? "" : resumeName, "organizer", organizer));
    }

    private String buildCalendarDescription(HrInviteCreateDTO dto, Map<String, Object> person, ResumeFile resume,
                                            String organizerName) {
        String round = ROUND_NAME.getOrDefault(dto.getRoundNo(), dto.getRoundNo() + "面");
        String organizer = StringUtils.hasText(organizerName) ? organizerName : currentOrganizerName();
        String resumeLine = resume == null
                ? "未上传（请联系招聘负责人）"
                : resume.fileName();
        return "组织人：" + organizer
                + "\n候选人：" + blank(person.get("candidate_name"))
                + "\n岗位：" + blank(person.get("job_name"))
                + "\n轮次：" + round
                + "\n时间：" + formatInterviewAt(dto.getInterviewAt())
                + "\n简历：" + resumeLine;
    }

    private static String formatInterviewAt(LocalDateTime at) {
        if (at == null) {
            return "-";
        }
        String text = at.toString().replace('T', ' ');
        return text.length() >= 16 ? text.substring(0, 16) : text;
    }

    /** 组织人 = 当前登录用户（昵称优先，否则用户名） */
    private String currentOrganizerName() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return SecurityUtils.getCurrentUsername();
        }
        String name = jdbc.query("SELECT COALESCE(NULLIF(TRIM(nickname), ''), username) FROM sys_user WHERE user_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, userId);
        return name == null || name.isBlank() ? SecurityUtils.getCurrentUsername() : name.trim();
    }

    private ResumeFile loadResume(Long applicationId) {
        if (applicationId == null) {
            return null;
        }
        return jdbc.query("""
                SELECT file_name, storage_path FROM hr_resume_file
                WHERE application_id = ? AND is_active = 1
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            String fileName = rs.getString("file_name");
            String storagePath = rs.getString("storage_path");
            String displayName = StringUtils.hasText(fileName) ? fileName.trim() : null;
            Path path = fileStorage.resolveUploadPath(storagePath);
            if (path != null && Files.isRegularFile(path)) {
                if (displayName == null) {
                    displayName = path.getFileName().toString();
                }
                return new ResumeFile(displayName, path);
            }
            // 本机无文件时（连远程库）从公网 uploads 拉临时文件，供钉钉附件发送
            Path downloaded = downloadResumeFromPublic(storagePath, displayName);
            if (downloaded != null) {
                if (displayName == null) {
                    displayName = downloaded.getFileName().toString();
                }
                return new ResumeFile(displayName, downloaded);
            }
            // 库里有简历记录：文案不能写成「未上传」
            if (displayName != null || StringUtils.hasText(storagePath)) {
                return new ResumeFile(displayName != null ? displayName : "已上传简历", null);
            }
            return null;
        }, applicationId);
    }

    private Path downloadResumeFromPublic(String storagePath, String fileName) {
        String publicUrl = fileStorage.toPublicUrl(storagePath);
        if (!StringUtils.hasText(publicUrl) || !publicUrl.startsWith("http")) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(publicUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 300) {
                return null;
            }
            String suffix = ".bin";
            String name = StringUtils.hasText(fileName) ? fileName : storagePath;
            int dot = name == null ? -1 : name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                suffix = name.substring(dot);
                if (suffix.length() > 16) {
                    suffix = ".bin";
                }
            }
            Path temp = Files.createTempFile("hr-resume-", suffix);
            try (InputStream in = response.body()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(temp) <= 0) {
                Files.deleteIfExists(temp);
                return null;
            }
            temp.toFile().deleteOnExit();
            return temp;
        } catch (Exception ex) {
            return null;
        }
    }

    private long insertInvite(HrInviteCreateDTO dto, int duration) {
        jdbc.update("""
                INSERT INTO hr_interview_invite (application_id, round_no, interviewer_user_id, interview_at, duration_min, location, status, invited_by, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'FAILED', ?, ?, 1)
                """, dto.getApplicationId(), dto.getRoundNo(), dto.getInterviewerUserId(), dto.getInterviewAt(),
                duration, dto.getLocation(), SecurityUtils.getCurrentUserId(), SecurityUtils.getCurrentUsername());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static void validateInterviewAt(LocalDateTime interviewAt) {
        if (interviewAt == null) {
            throw new BusinessException("请选择开始时间");
        }
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        if (!interviewAt.isAfter(now)) {
            throw new BusinessException("不能预约已经过去的时间");
        }
        LocalDate today = now.toLocalDate();
        LocalDate day = interviewAt.toLocalDate();
        if (day.isAfter(ChinaHoliday.maxBookableDate(today))) {
            throw new BusinessException("最多只能预约未来 " + ChinaHoliday.BOOKING_MAX_DAYS + " 天内的面试");
        }
        if (ChinaHoliday.isOffDay(day)) {
            throw new BusinessException("不能预约国家法定节假日");
        }
        LocalTime time = interviewAt.toLocalTime().withSecond(0).withNano(0);
        if (interviewAt.getSecond() != 0 || interviewAt.getNano() != 0 || time.getMinute() % 30 != 0) {
            throw new BusinessException("面试开始时间须为半小时整点（如 09:30、10:00）");
        }
        if (time.isBefore(INTERVIEW_START) || time.isAfter(INTERVIEW_END)) {
            throw new BusinessException("面试开始时间须在每天 09:30～17:30 之间");
        }
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
        hrMasterService.refreshDingTalkBindingQuietly(userId);
        return jdbc.query("SELECT dingtalk_union_id FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
                rs -> rs.next() ? rs.getString(1) : null, userId);
    }

    private String findDingUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        hrMasterService.refreshDingTalkBindingQuietly(userId);
        return jdbc.query("SELECT dingtalk_user_id FROM hr_user_dingtalk WHERE user_id = ? AND is_active = 1",
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

    private record ResumeFile(String fileName, Path path) {
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
