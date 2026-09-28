package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkAssistantMeetingDTO;
import com.base.admin.domain.dto.DingTalkAssistantReportDTO;
import com.base.admin.domain.dto.DingTalkAssistantScheduleUpdateDTO;
import com.base.admin.domain.dto.DingTalkAssistantSuggestDTO;
import com.base.admin.domain.dto.DingTalkBusyQueryDTO;
import com.base.admin.domain.dto.SysTaskDTO;
import com.base.admin.domain.vo.DingTalkAssistantActionResultVO;
import com.base.admin.domain.vo.DingTalkAssistantScheduleVO;
import com.base.admin.domain.vo.DingTalkAssistantSuggestVO;
import com.base.admin.domain.vo.DingTalkBusySlotVO;
import com.base.admin.domain.vo.DingTalkBusyUserVO;
import com.base.admin.domain.vo.DingTalkScheduleRuleVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.ChinaHoliday;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DingTalkAssistantService {

    private static final int DEFAULT_DURATION = 60;
    private static final int MAX_SLOTS_PER_DAY = 24;
    private static final int DEFAULT_SLOT_STEP_MIN = 30;
    /** 无个人规则时的默认工作时段 */
    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 30);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(18, 30);
    private static final LocalTime DEFAULT_LUNCH_START = LocalTime.of(13, 0);
    private static final LocalTime DEFAULT_LUNCH_END = LocalTime.of(14, 0);
    private static final DateTimeFormatter LABEL_DAY = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_TAB = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final DingTalkBusyService busyService;
    private final DingTalkCalendarClient dingTalk;
    private final HrMasterService hrMasterService;
    private final SysTaskService taskService;
    private final JdbcTemplate jdbc;
    private final DingTalkScheduleRuleService dingTalkScheduleRuleService;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public DingTalkAssistantSuggestVO suggest(DingTalkAssistantSuggestDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BusinessException("请选择要查询的同事");
        }

        DingTalkScheduleRuleVO rule = dingTalkScheduleRuleService.loadOrDefault(dto.getTargetUserId());
        RuleDayHours hours = resolveDayHours(rule);
        ParsedIntent intent = resolveIntent(dto, rule, hours);

        int duration = intent.durationMin();
        if (duration < 15 || duration > 240) {
            throw new BusinessException("期望时长须在 15～240 分钟之间");
        }

        LocalDate startDay = intent.rangeStart().toLocalDate();
        LocalDate endDay = intent.rangeEnd().toLocalDate();
        if (endDay.isBefore(startDay)) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        LocalDateTime rangeStart = startDay.atTime(hours.workStart());
        LocalDateTime rangeEnd = endDay.atTime(hours.workEnd());

        DingTalkBusyQueryDTO query = new DingTalkBusyQueryDTO();
        query.setUserIds(List.of(dto.getTargetUserId()));
        query.setStartTime(rangeStart);
        query.setEndTime(rangeEnd);
        List<DingTalkBusyUserVO> rows = busyService.query(query, hours.workStart(), hours.workEnd());
        DingTalkBusyUserVO user = rows.isEmpty() ? null : rows.getFirst();

        DingTalkAssistantSuggestVO vo = new DingTalkAssistantSuggestVO();
        vo.setTargetUserId(dto.getTargetUserId());
        vo.setDurationMin(duration);
        vo.setWorkStart(hours.workStartLabel());
        vo.setWorkEnd(hours.workEndLabel());
        vo.setLunchStart(hours.lunchStartLabel());
        vo.setLunchEnd(hours.lunchEndLabel());
        vo.setBufferMin(hours.bufferMin());
        vo.setIntentAction(intent.action());
        vo.setJobName(intent.jobName());
        vo.setJobMatchNote(intent.jobMatchNote());
        vo.setRuleSummary(buildRuleSummary(null, hours, rule, intent));
        if (user == null) {
            vo.setTargetNickname("未知用户");
            vo.setError("未查到该用户");
            vo.setBusySummary("未能查询到该用户的钉钉闲忙。");
            vo.setAdviceText("未查到该用户，请换人再试。");
            vo.setNextStepText("请换一位已绑定钉钉的同事再问一次。");
            return vo;
        }
        vo.setTargetUsername(user.getUsername());
        vo.setTargetNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        vo.setRuleSummary(buildRuleSummary(vo.getTargetNickname(), hours, rule, intent));
        if (StringUtils.hasText(user.getError())) {
            vo.setError(user.getError());
            vo.setBusySummary("查询「" + vo.getTargetNickname() + "」钉钉闲忙失败：" + user.getError());
            vo.setAdviceText(vo.getTargetNickname() + "：" + user.getError());
            vo.setNextStepText("请确认对方已绑定钉钉并可查询闲忙后重试，或改约其他人。");
            vo.setActions(List.of());
            return vo;
        }

        List<DingTalkBusySlotVO> slots = applyRuleToBusySlots(user.getSlots(), rule, hours);
        List<DingTalkAssistantSuggestVO.FreeWindow> windows = findFreeWindows(slots, duration, hours);
        List<DingTalkAssistantSuggestVO.DayGroup> dayGroups = buildDayGroups(
                rangeStart, rangeEnd, windows, duration, rule, hours, intent.action());

        int lookAhead = resolveLookAheadDays(rule);
        boolean expanded = false;
        boolean searchedLookAhead = false;
        LocalDate displayStart = startDay;
        LocalDate displayEnd = endDay;
        if (countSlots(dayGroups) == 0) {
            LocalDate today = LocalDate.now();
            LocalDate expandEnd = today.plusDays(lookAhead - 1L);
            boolean askedCoversLookAhead = !startDay.isAfter(today) && !endDay.isBefore(expandEnd);
            searchedLookAhead = askedCoversLookAhead;
            if (!askedCoversLookAhead) {
                LocalDateTime expandRangeStart = today.atTime(hours.workStart());
                LocalDateTime expandRangeEnd = expandEnd.atTime(hours.workEnd());
                DingTalkBusyQueryDTO expandQuery = new DingTalkBusyQueryDTO();
                expandQuery.setUserIds(List.of(dto.getTargetUserId()));
                expandQuery.setStartTime(expandRangeStart);
                expandQuery.setEndTime(expandRangeEnd);
                List<DingTalkBusyUserVO> expandRows = busyService.query(
                        expandQuery, hours.workStart(), hours.workEnd());
                DingTalkBusyUserVO expandUser = expandRows.isEmpty() ? null : expandRows.getFirst();
                if (expandUser != null && !StringUtils.hasText(expandUser.getError())) {
                    slots = applyRuleToBusySlots(expandUser.getSlots(), rule, hours);
                    windows = findFreeWindows(slots, duration, hours);
                    dayGroups = keepDaysWithSlots(buildDayGroups(
                            expandRangeStart, expandRangeEnd, windows, duration, rule, hours, intent.action()));
                    expanded = countSlots(dayGroups) > 0;
                    searchedLookAhead = true;
                    displayStart = today;
                    displayEnd = expandEnd;
                }
            } else {
                dayGroups = List.of();
            }
        }

        vo.setFreeWindows(windows);
        vo.setDayGroups(dayGroups);
        vo.setBusySummary(buildBusySummary(vo.getTargetNickname(), slots, windows, duration,
                displayStart, displayEnd, intent));
        SlotPick pick = firstSlot(dayGroups, duration);
        boolean hasSlot = pick != null && pick.start() != null;
        if (!hasSlot) {
            dayGroups = List.of();
            vo.setDayGroups(dayGroups);
        }
        vo.setAdviceText(buildAdvice(vo.getTargetNickname(), duration, dayGroups, hours, intent,
                startDay, endDay, lookAhead, expanded, hasSlot, searchedLookAhead));
        vo.setActions(hasSlot ? baseActions(vo, pick, duration, intent.action()) : List.of());
        vo.setNextStepText(buildNextStepText(vo.getTargetNickname(), duration, dayGroups, pick, intent,
                startDay, endDay, lookAhead, expanded, searchedLookAhead));
        return vo;
    }

    @Transactional
    public DingTalkAssistantActionResultVO createMeeting(DingTalkAssistantMeetingDTO dto) {
        validateBookableStart(dto.getStartTime());
        BoundUser querier = requireCurrentBound();
        BoundUser target = requireBound(dto.getTargetUserId(), "被邀请人");
        int duration = dto.getDurationMin() == null ? DEFAULT_DURATION : dto.getDurationMin();
        // 普通会议不强制简历
        dingTalkScheduleRuleService.assertBookable(
                target.userId(), DingTalkScheduleRuleService.ACTION_MEETING, dto.getStartTime(), duration, false);
        String title = dto.getTitle().trim();
        String description = blankToEmpty(dto.getDescription());
        String location = blankToEmpty(dto.getLocation());
        boolean online = Boolean.TRUE.equals(dto.getOnlineMeeting());
        if (online && !StringUtils.hasText(location)) {
            location = "钉钉视频会议";
        }

        CalendarPair calendars = createSharedCalendar(
                querier, target, title, description, dto.getStartTime(), duration, location, online);
        if (!calendars.querierOk()) {
            throw new BusinessException(buildActionMessage("会议", calendars, false));
        }
        Long recordId = insertRecord("MEETING", title, description, location, online ? 1 : 0,
                dto.getStartTime(), duration, querier, target,
                calendars.querierEventId(), null, null);

        String when = dto.getStartTime().format(NOTICE_TIME);
        String markdown = "### 会议已创建\n\n"
                + "- **主题**：" + title + "\n"
                + "- **对象**：" + target.nickname() + "\n"
                + "- **时间**：" + when + "（" + duration + " 分钟）\n"
                + "- **地点**：" + (StringUtils.hasText(location) ? location : "—") + "\n"
                + "- **视频会议**：" + (online ? "是" : "否") + "\n";
        if (StringUtils.hasText(description)) {
            markdown += "- **说明**：" + description + "\n";
        }
        boolean noticeSent = notifyQuerier(querier, "会议邀请已创建", markdown);

        return actionResult(recordId, calendars, noticeSent, null, "会议");
    }

    @Transactional
    public DingTalkAssistantActionResultVO createReport(DingTalkAssistantReportDTO dto) {
        validateBookableStart(dto.getStartTime());
        BoundUser querier = requireCurrentBound();
        BoundUser target = requireBound(dto.getTargetUserId(), "汇报对象");
        int duration = dto.getDurationMin() == null ? DEFAULT_DURATION : dto.getDurationMin();
        dingTalkScheduleRuleService.assertBookable(
                target.userId(), DingTalkScheduleRuleService.ACTION_REPORT, dto.getStartTime(), duration, false);
        String title = StringUtils.hasText(dto.getTitle())
                ? dto.getTitle().trim()
                : "工作汇报 · " + querier.nickname() + " → " + target.nickname();
        String content = StringUtils.hasText(dto.getContent())
                ? dto.getContent().trim()
                : "由日程助手创建。请双方同步工作进展。";
        String location = blankToEmpty(dto.getLocation());

        SysTaskDTO task = new SysTaskDTO();
        task.setTitle(title);
        task.setContent(content);
        task.setTaskType("日常");
        task.setPriority(2);
        task.setStatus("未开始");
        task.setProgress(0);
        task.setOwnerUserId(querier.userId());
        task.setAssigneeUserIds(List.of(target.userId()));
        task.setPlanStartTime(dto.getStartTime());
        task.setPlanEndTime(dto.getStartTime().plusMinutes(duration));
        task.setBizType("dingtalk_assistant");
        task.setRemark("日程助手 · 汇报工作");
        Long taskId = taskService.create(task);

        CalendarPair calendars = createSharedCalendar(
                querier, target, title, content, dto.getStartTime(), duration, location, false);
        if (!calendars.querierOk()) {
            throw new BusinessException(buildActionMessage("工作汇报", calendars, false));
        }
        Long recordId = insertRecord("REPORT", title, content, location, 0,
                dto.getStartTime(), duration, querier, target,
                calendars.querierEventId(), null, taskId);

        String when = dto.getStartTime().format(NOTICE_TIME);
        String markdown = "### 工作汇报已安排\n\n"
                + "- **主题**：" + title + "\n"
                + "- **对象**：" + target.nickname() + "\n"
                + "- **时间**：" + when + "（" + duration + " 分钟）\n"
                + "- **任务ID**：" + taskId + "\n";
        boolean noticeSent = notifyQuerier(querier, "工作汇报已安排", markdown);

        DingTalkAssistantActionResultVO vo = actionResult(recordId, calendars, noticeSent, taskId, "工作汇报");
        vo.setSuccess(true);
        vo.setMessage(vo.getMessage() + " 任务已创建。");
        return vo;
    }

    public List<DingTalkAssistantScheduleVO> listSchedules(String kind, String status) {
        Long me = SecurityUtils.getCurrentUserId();
        if (me == null) {
            throw new BusinessException("请先登录");
        }
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.kind, s.title, s.description, s.location, s.online_meeting,
                       s.start_time, s.duration_min, s.end_time,
                       s.querier_user_id, q.nickname querier_nickname, q.username querier_username,
                       s.target_user_id, t.nickname target_nickname, t.username target_username,
                       s.querier_event_id, s.target_event_id, s.task_id, s.status, s.create_time
                FROM sys_dingtalk_assistant_event s
                LEFT JOIN sys_user q ON q.user_id = s.querier_user_id
                LEFT JOIN sys_user t ON t.user_id = s.target_user_id
                WHERE s.is_active = 1 AND (s.querier_user_id = ? OR s.target_user_id = ?)
                """);
        List<Object> args = new ArrayList<>();
        args.add(me);
        args.add(me);
        if (StringUtils.hasText(kind)) {
            sql.append(" AND s.kind = ? ");
            args.add(kind.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND s.status = ? ");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY s.start_time DESC, s.id DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            DingTalkAssistantScheduleVO vo = new DingTalkAssistantScheduleVO();
            vo.setId(rs.getLong("id"));
            vo.setKind(rs.getString("kind"));
            vo.setKindLabel("REPORT".equals(vo.getKind()) ? "工作汇报" : "会议");
            vo.setTitle(rs.getString("title"));
            vo.setDescription(rs.getString("description"));
            vo.setLocation(rs.getString("location"));
            vo.setOnlineMeeting(rs.getInt("online_meeting"));
            Timestamp start = rs.getTimestamp("start_time");
            Timestamp end = rs.getTimestamp("end_time");
            Timestamp created = rs.getTimestamp("create_time");
            vo.setStartTime(start == null ? null : start.toLocalDateTime());
            vo.setEndTime(end == null ? null : end.toLocalDateTime());
            vo.setCreateTime(created == null ? null : created.toLocalDateTime());
            vo.setDurationMin(rs.getInt("duration_min"));
            vo.setQuerierUserId(rs.getLong("querier_user_id"));
            String qn = rs.getString("querier_nickname");
            vo.setQuerierNickname(StringUtils.hasText(qn) ? qn : rs.getString("querier_username"));
            vo.setTargetUserId(rs.getLong("target_user_id"));
            String tn = rs.getString("target_nickname");
            vo.setTargetNickname(StringUtils.hasText(tn) ? tn : rs.getString("target_username"));
            vo.setQuerierEventId(rs.getString("querier_event_id"));
            vo.setTargetEventId(rs.getString("target_event_id"));
            Object taskId = rs.getObject("task_id");
            vo.setTaskId(taskId == null ? null : ((Number) taskId).longValue());
            vo.setStatus(rs.getString("status"));
            vo.setStatusLabel("CANCELLED".equals(vo.getStatus()) ? "已取消" : "有效");
            return vo;
        }, args.toArray());
    }

    @Transactional
    public DingTalkAssistantActionResultVO updateSchedule(DingTalkAssistantScheduleUpdateDTO dto) {
        validateBookableStart(dto.getStartTime());
        ScheduleRow row = loadMine(dto.getId());
        if ("CANCELLED".equals(row.status())) {
            throw new BusinessException("已取消的日程不能编辑");
        }
        BoundUser querier = requireBound(row.querierUserId(), "当前用户");
        BoundUser target = requireBound(row.targetUserId(), "对方");
        int duration = dto.getDurationMin() == null ? row.durationMin() : dto.getDurationMin();
        String title = dto.getTitle().trim();
        String description = blankToEmpty(dto.getDescription());
        String location = blankToEmpty(dto.getLocation());
        boolean online = dto.getOnlineMeeting() == null ? row.onlineMeeting() == 1 : Boolean.TRUE.equals(dto.getOnlineMeeting());
        if (online && !StringUtils.hasText(location)) {
            location = "钉钉视频会议";
        }
        List<String> attendees = List.of(target.unionId());

        String querierEventId = row.querierEventId();
        String targetEventId = row.targetEventId();
        String querierErr = null;
        boolean querierOk = false;

        // 只维护发起人侧那一条共享日程；对方通过参与人同步，不再单独建第二条
        if (StringUtils.hasText(querierEventId)) {
            DingTalkCalendarClient.CalendarCall call = dingTalk.updateEvent(
                    querier.unionId(), querierEventId, title, description, dto.getStartTime(), duration, location, attendees, online);
            querierOk = call.success();
            querierErr = call.message();
            if (!querierOk) {
                DingTalkCalendarClient.CalendarCall recreate = dingTalk.createEvent(
                        querier.unionId(), title, description, dto.getStartTime(), duration, location, attendees, online);
                querierOk = recreate.success();
                querierEventId = recreate.eventId();
                querierErr = recreate.message();
            }
        } else {
            DingTalkCalendarClient.CalendarCall recreate = dingTalk.createEvent(
                    querier.unionId(), title, description, dto.getStartTime(), duration, location, attendees, online);
            querierOk = recreate.success();
            querierEventId = recreate.eventId();
            querierErr = recreate.message();
        }

        // 历史数据曾在对方日历另建一条，更新时顺手删掉，避免继续重复
        if (StringUtils.hasText(targetEventId)) {
            dingTalk.deleteEvent(target.unionId(), targetEventId);
            targetEventId = null;
        }

        if (!querierOk) {
            throw new BusinessException("更新钉钉日程失败：" + blankToEmpty(querierErr));
        }

        jdbc.update("""
                UPDATE sys_dingtalk_assistant_event
                SET title = ?, description = ?, location = ?, online_meeting = ?,
                    start_time = ?, duration_min = ?, end_time = ?,
                    querier_event_id = ?, target_event_id = ?, update_by = ?
                WHERE id = ? AND is_active = 1
                """, title, description, location, online ? 1 : 0,
                dto.getStartTime(), duration, dto.getStartTime().plusMinutes(duration),
                querierEventId, targetEventId, SecurityUtils.getCurrentUsername(), dto.getId());

        CalendarPair pair = new CalendarPair(true, querierEventId, null, true, null, null);
        boolean noticeSent = notifyQuerier(querier, "助手日程已更新",
                "### 日程已更新\n\n- **主题**：" + title + "\n- **时间**：" + dto.getStartTime().format(NOTICE_TIME) + "\n");
        return actionResult(dto.getId(), pair, noticeSent, row.taskId(), "日程更新");
    }

    @Transactional
    public DingTalkAssistantActionResultVO cancelSchedule(Long id) {
        ScheduleRow row = loadMine(id);
        if ("CANCELLED".equals(row.status())) {
            throw new BusinessException("该日程已取消");
        }
        BoundUser querier = requireBound(row.querierUserId(), "当前用户");
        BoundUser target = requireBound(row.targetUserId(), "对方");
        String querierErr = null;
        String targetErr = null;
        boolean querierOk = true;
        boolean targetOk = true;
        if (StringUtils.hasText(row.querierEventId())) {
            DingTalkCalendarClient.CalendarCall call = dingTalk.deleteEvent(querier.unionId(), row.querierEventId());
            querierOk = call.success();
            querierErr = call.message();
        }
        if (StringUtils.hasText(row.targetEventId())) {
            DingTalkCalendarClient.CalendarCall call = dingTalk.deleteEvent(target.unionId(), row.targetEventId());
            targetOk = call.success();
            targetErr = call.message();
        }
        jdbc.update("""
                UPDATE sys_dingtalk_assistant_event
                SET status = 'CANCELLED', update_by = ?
                WHERE id = ? AND is_active = 1
                """, SecurityUtils.getCurrentUsername(), id);
        boolean noticeSent = notifyQuerier(querier, "助手日程已取消",
                "### 日程已取消\n\n- **主题**：" + row.title() + "\n- **原时间**："
                        + (row.startTime() == null ? "—" : row.startTime().format(NOTICE_TIME)) + "\n");
        DingTalkAssistantActionResultVO vo = new DingTalkAssistantActionResultVO();
        vo.setRecordId(id);
        vo.setSuccess(true);
        vo.setNoticeSent(noticeSent);
        StringBuilder msg = new StringBuilder("已取消助手日程记录。");
        if (!querierOk) {
            msg.append(" 你的钉钉日程取消失败：").append(blankToEmpty(querierErr)).append("。");
        }
        if (!targetOk) {
            msg.append(" 对方钉钉日程取消失败：").append(blankToEmpty(targetErr)).append("。");
        }
        if (noticeSent) {
            msg.append(" 已通知你。");
        }
        vo.setMessage(msg.toString());
        return vo;
    }

    private Long insertRecord(String kind, String title, String description, String location, int online,
                              LocalDateTime start, int duration, BoundUser querier, BoundUser target,
                              String querierEventId, String targetEventId, Long taskId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO sys_dingtalk_assistant_event
                    (kind, title, description, location, online_meeting, start_time, duration_min, end_time,
                     querier_user_id, target_user_id, querier_event_id, target_event_id, task_id, status, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, kind);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setString(4, location);
            ps.setInt(5, online);
            ps.setTimestamp(6, Timestamp.valueOf(start));
            ps.setInt(7, duration);
            ps.setTimestamp(8, Timestamp.valueOf(start.plusMinutes(duration)));
            ps.setLong(9, querier.userId());
            ps.setLong(10, target.userId());
            ps.setString(11, querierEventId);
            ps.setString(12, targetEventId);
            if (taskId == null) {
                ps.setObject(13, null);
            } else {
                ps.setLong(13, taskId);
            }
            ps.setString(14, SecurityUtils.getCurrentUsername());
            return ps;
        }, keys);
        Number key = keys.getKey();
        return key == null ? null : key.longValue();
    }

    private ScheduleRow loadMine(Long id) {
        Long me = SecurityUtils.getCurrentUserId();
        if (me == null) {
            throw new BusinessException("请先登录");
        }
        ScheduleRow row = jdbc.query("""
                SELECT id, kind, title, description, location, online_meeting, start_time, duration_min,
                       querier_user_id, target_user_id, querier_event_id, target_event_id, task_id, status
                FROM sys_dingtalk_assistant_event
                WHERE id = ? AND is_active = 1 AND querier_user_id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            Timestamp start = rs.getTimestamp("start_time");
            Object taskId = rs.getObject("task_id");
            return new ScheduleRow(
                    rs.getLong("id"),
                    rs.getString("kind"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("location"),
                    rs.getInt("online_meeting"),
                    start == null ? null : start.toLocalDateTime(),
                    rs.getInt("duration_min"),
                    rs.getLong("querier_user_id"),
                    rs.getLong("target_user_id"),
                    rs.getString("querier_event_id"),
                    rs.getString("target_event_id"),
                    taskId == null ? null : ((Number) taskId).longValue(),
                    rs.getString("status"));
        }, id, me);
        if (row == null) {
            throw new BusinessException("助手日程不存在或无权操作");
        }
        return row;
    }

    private DingTalkAssistantActionResultVO actionResult(Long recordId, CalendarPair calendars, boolean noticeSent,
                                                         Long taskId, String kind) {
        DingTalkAssistantActionResultVO vo = new DingTalkAssistantActionResultVO();
        vo.setRecordId(recordId);
        vo.setSuccess(calendars.querierOk() || calendars.targetOk());
        vo.setQuerierEventId(calendars.querierEventId());
        vo.setTargetEventId(calendars.targetEventId());
        vo.setTaskId(taskId);
        vo.setNoticeSent(noticeSent);
        vo.setMessage(buildActionMessage(kind, calendars, noticeSent));
        return vo;
    }

    /**
     * 只在发起人主日历建一条日程，并把对方列为参与人。
     * 若双方各建一条，钉钉会同步出两条一模一样的日程。
     */
    private CalendarPair createSharedCalendar(BoundUser querier, BoundUser target, String title, String description,
                                              LocalDateTime start, int duration, String location, boolean online) {
        List<String> attendees = List.of(target.unionId());
        DingTalkCalendarClient.CalendarCall call = dingTalk.createEvent(
                querier.unionId(), title, description, start, duration, location, attendees, online);
        return new CalendarPair(
                call.success(), call.eventId(), call.message(),
                call.success(), null, null);
    }

    private boolean notifyQuerier(BoundUser querier, String title, String markdown) {
        if (!StringUtils.hasText(querier.dingUserId())) {
            return false;
        }
        return dingTalk.notifyMarkdown(querier.dingUserId(), title, markdown).success();
    }

    private static String buildActionMessage(String kind, CalendarPair calendars, boolean noticeSent) {
        StringBuilder sb = new StringBuilder();
        if (calendars.querierOk()) {
            sb.append("已创建钉钉日程，并邀请对方参加。");
        } else {
            sb.append(kind).append("日程创建失败：").append(blankToEmpty(calendars.querierError()));
            return sb.toString();
        }
        if (noticeSent) {
            sb.append(" 已向你发送钉钉工作通知。");
        } else {
            sb.append(" 未能向你发送钉钉工作通知（请确认已绑定钉钉 userid）。");
        }
        return sb.toString();
    }

    private static void validateBookableStart(LocalDateTime start) {
        try {
            ChinaHoliday.requireBookableStart(start);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private BoundUser requireCurrentBound() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return requireBound(userId, "当前用户");
    }

    private BoundUser requireBound(Long userId, String who) {
        // 建日程前按手机号重绑企业身份，避免仍用旧的个人钉钉账号 unionId
        hrMasterService.refreshDingTalkBindingQuietly(userId);
        BoundUser user = jdbc.query("""
                SELECT u.user_id, u.username, u.nickname, d.dingtalk_union_id, d.dingtalk_user_id
                FROM sys_user u
                LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                WHERE u.user_id = ? AND u.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new BoundUser(
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    StringUtils.hasText(rs.getString("nickname")) ? rs.getString("nickname") : rs.getString("username"),
                    rs.getString("dingtalk_union_id"),
                    rs.getString("dingtalk_user_id"));
        }, userId);
        if (user == null) {
            throw new BusinessException(who + "不存在");
        }
        if (!StringUtils.hasText(user.unionId())) {
            throw new BusinessException(who + "「" + user.nickname() + "」未绑定钉钉，无法创建日程");
        }
        return user;
    }

    /**
     * 按查询范围内自然日建 Tab；空闲切分落在被问询人工作时段内，并避开其午休/不安排窗。
     */
    private List<DingTalkAssistantSuggestVO.DayGroup> buildDayGroups(
            LocalDateTime rangeStart, LocalDateTime rangeEnd,
            List<DingTalkAssistantSuggestVO.FreeWindow> windows, int durationMin,
            DingTalkScheduleRuleVO rule, RuleDayHours hours, String action) {
        Map<String, DingTalkAssistantSuggestVO.DayGroup> map = new LinkedHashMap<>();
        if (rangeStart != null && rangeEnd != null && !rangeStart.toLocalDate().isAfter(rangeEnd.toLocalDate())) {
            for (LocalDate day = rangeStart.toLocalDate(); !day.isAfter(rangeEnd.toLocalDate()); day = day.plusDays(1)) {
                if (hours.denyHolidays() && ChinaHoliday.isOffDay(day)) {
                    continue;
                }
                String key = day.format(DAY_KEY);
                DingTalkAssistantSuggestVO.DayGroup g = new DingTalkAssistantSuggestVO.DayGroup();
                g.setDay(key);
                g.setDayLabel(day.format(DAY_TAB) + " " + WEEK[day.getDayOfWeek().getValue() - 1]);
                map.put(key, g);
            }
        }
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        int step = hours.slotStepMin();
        String act = StringUtils.hasText(action) ? action : "busy_query";
        boolean requireActionWindow = DingTalkScheduleRuleService.ACTION_INTERVIEW.equals(act)
                || DingTalkScheduleRuleService.ACTION_MEETING.equals(act)
                || DingTalkScheduleRuleService.ACTION_REPORT.equals(act);
        if (windows != null) {
            for (DingTalkAssistantSuggestVO.FreeWindow window : windows) {
                if (window.getStart() == null || window.getEnd() == null) {
                    continue;
                }
                LocalDateTime cursor = ceilToSlot(window.getStart(), hours);
                LocalDateTime windowEnd = clipToWorkEnd(window.getEnd(), hours);
                if (cursor == null || windowEnd == null || !cursor.isBefore(windowEnd)) {
                    continue;
                }
                while (!cursor.plusMinutes(durationMin).isAfter(windowEnd)) {
                    LocalDateTime slotEnd = cursor.plusMinutes(durationMin);
                    if (slotEnd.toLocalTime().isAfter(hours.workEnd())
                            || cursor.toLocalTime().isBefore(hours.workStart())
                            || (hours.denyHolidays() && ChinaHoliday.isOffDay(cursor.toLocalDate()))
                            || overlapsLunch(cursor, slotEnd, hours)
                            || overlapsBlocked(rule, cursor, slotEnd)) {
                        cursor = cursor.plusMinutes(step);
                        continue;
                    }
                    if (requireActionWindow
                            && !dingTalkScheduleRuleService.isSlotAllowed(rule, act, cursor, slotEnd)) {
                        cursor = cursor.plusMinutes(step);
                        continue;
                    }
                    if (!cursor.isAfter(now)) {
                        cursor = cursor.plusMinutes(step);
                        continue;
                    }
                    String key = cursor.toLocalDate().format(DAY_KEY);
                    DingTalkAssistantSuggestVO.DayGroup group = map.get(key);
                    if (group == null) {
                        cursor = cursor.plusMinutes(step);
                        continue;
                    }
                    if (group.getSlots().size() >= MAX_SLOTS_PER_DAY) {
                        break;
                    }
                    DingTalkAssistantSuggestVO.SlotOption slot = new DingTalkAssistantSuggestVO.SlotOption();
                    slot.setStart(cursor);
                    slot.setEnd(slotEnd);
                    slot.setLabel(cursor.format(LABEL_TIME) + "–" + slotEnd.format(LABEL_TIME));
                    group.getSlots().add(slot);
                    cursor = cursor.plusMinutes(step);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    private static SlotPick firstSlot(List<DingTalkAssistantSuggestVO.DayGroup> dayGroups, int duration) {
        if (dayGroups == null) {
            return null;
        }
        for (DingTalkAssistantSuggestVO.DayGroup day : dayGroups) {
            if (day.getSlots() != null && !day.getSlots().isEmpty()) {
                DingTalkAssistantSuggestVO.SlotOption slot = day.getSlots().getFirst();
                return new SlotPick(slot.getStart(), slot.getEnd() == null ? slot.getStart().plusMinutes(duration) : slot.getEnd());
            }
        }
        return null;
    }

    /** 合并连续 FREE 格为窗口；裁到被问询人工作时段，并应用日程缓存 */
    private static List<DingTalkAssistantSuggestVO.FreeWindow> findFreeWindows(
            List<DingTalkBusySlotVO> slots, int durationMin, RuleDayHours hours) {
        List<DingTalkAssistantSuggestVO.FreeWindow> result = new ArrayList<>();
        if (slots == null || slots.isEmpty()) {
            return result;
        }
        LocalDateTime runStart = null;
        LocalDateTime runEnd = null;
        LocalDateTime lastBusyEnd = null;
        for (DingTalkBusySlotVO slot : slots) {
            if (slot == null || slot.getStart() == null || slot.getEnd() == null) {
                continue;
            }
            LocalDateTime[] clipped = clipToWorkDay(slot.getStart(), slot.getEnd(), hours);
            if (clipped == null) {
                if (runStart != null) {
                    addIfLongEnough(result, runStart, runEnd, durationMin);
                    runStart = null;
                    runEnd = null;
                }
                continue;
            }
            LocalDateTime start = clipped[0];
            LocalDateTime end = clipped[1];
            boolean free = "FREE".equalsIgnoreCase(slot.getStatus());
            if (!free) {
                if (runStart != null) {
                    addIfLongEnough(result, runStart, runEnd, durationMin);
                    runStart = null;
                    runEnd = null;
                }
                lastBusyEnd = end.plusMinutes(Math.max(0, hours.bufferMin()));
                continue;
            }
            if (lastBusyEnd != null && start.isBefore(lastBusyEnd)) {
                start = lastBusyEnd;
            }
            if (!start.isBefore(end)) {
                continue;
            }
            if (runStart == null) {
                runStart = start;
                runEnd = end;
            } else if (runEnd != null
                    && (runEnd.equals(start) || runEnd.isAfter(start))
                    && runStart.toLocalDate().equals(start.toLocalDate())) {
                if (end.isAfter(runEnd)) {
                    runEnd = end;
                }
            } else {
                addIfLongEnough(result, runStart, runEnd, durationMin);
                runStart = start;
                runEnd = end;
            }
        }
        if (runStart != null) {
            addIfLongEnough(result, runStart, runEnd, durationMin);
        }
        return result;
    }

    /** 裁到同一自然日的被问询人工作时段；节假日（若禁止）或无交集则返回 null */
    private static LocalDateTime[] clipToWorkDay(LocalDateTime start, LocalDateTime end, RuleDayHours hours) {
        if (start == null || end == null || !start.isBefore(end)) {
            return null;
        }
        LocalDate day = start.toLocalDate();
        if (hours.denyHolidays() && ChinaHoliday.isOffDay(day)) {
            return null;
        }
        LocalDateTime dayStart = day.atTime(hours.workStart());
        LocalDateTime dayEnd = day.atTime(hours.workEnd());
        LocalDateTime s = start.isBefore(dayStart) ? dayStart : start;
        LocalDateTime e = end.isAfter(dayEnd) ? dayEnd : end;
        if (end.toLocalDate().isAfter(day)) {
            e = dayEnd;
        }
        if (!s.isBefore(e)) {
            return null;
        }
        return new LocalDateTime[]{s, e};
    }

    private static LocalDateTime ceilToSlot(LocalDateTime time, RuleDayHours hours) {
        if (time == null) {
            return null;
        }
        LocalDateTime t = time.withSecond(0).withNano(0);
        if (t.toLocalTime().isBefore(hours.workStart())) {
            t = t.toLocalDate().atTime(hours.workStart());
        }
        int step = hours.slotStepMin();
        int rem = t.getMinute() % step;
        if (rem != 0) {
            t = t.plusMinutes(step - rem);
        }
        if (!t.toLocalTime().isBefore(hours.workEnd())) {
            return null;
        }
        return t;
    }

    private static LocalDateTime clipToWorkEnd(LocalDateTime time, RuleDayHours hours) {
        if (time == null) {
            return null;
        }
        LocalDateTime dayEnd = time.toLocalDate().atTime(hours.workEnd());
        return time.isAfter(dayEnd) ? dayEnd : time;
    }

    private static void addIfLongEnough(List<DingTalkAssistantSuggestVO.FreeWindow> out,
                                        LocalDateTime start, LocalDateTime end, int durationMin) {
        if (start == null || end == null || !start.isBefore(end)) {
            return;
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < durationMin) {
            return;
        }
        DingTalkAssistantSuggestVO.FreeWindow window = new DingTalkAssistantSuggestVO.FreeWindow();
        window.setStart(start);
        window.setEnd(end);
        window.setAvailableMin((int) minutes);
        window.setLabel(formatWindow(start, end, (int) minutes));
        out.add(window);
    }

    private static String formatWindow(LocalDateTime start, LocalDateTime end, int minutes) {
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return start.format(LABEL_DAY) + "–" + end.format(LABEL_TIME) + "（" + minutes + " 分钟）";
        }
        return start.format(LABEL_DAY) + "–" + end.format(LABEL_DAY) + "（" + minutes + " 分钟）";
    }

    private static String buildAdvice(String name, int durationMin, List<DingTalkAssistantSuggestVO.DayGroup> dayGroups,
                                      RuleDayHours hours, ParsedIntent intent,
                                      LocalDate askedStart, LocalDate askedEnd, int lookAhead,
                                      boolean expanded, boolean hasSlot, boolean searchedLookAhead) {
        String workLabel = "每天 " + hours.workStartLabel() + "～" + hours.workEndLabel();
        String lunchBit = hours.lunchStart().isBefore(hours.lunchEnd())
                ? "，已避开午休 " + hours.lunchStartLabel() + "～" + hours.lunchEndLabel()
                : "";
        String bufferBit = hours.bufferMin() > 0 ? "，日程缓存 " + hours.bufferMin() + " 分钟" : "";
        String intentBit = intentActionLabel(intent.action());
        String jobBit = StringUtils.hasText(intent.jobName()) ? "（岗位「" + intent.jobName() + "」）" : "";
        String asked = askedStart.equals(askedEnd) ? askedStart.toString() : askedStart + "～" + askedEnd;
        if (!hasSlot) {
            if (searchedLookAhead) {
                return "抱歉，「" + name + "」在你关心的时间（" + asked + "）没有连续 "
                        + durationMin + " 分钟可用空档；按对方日程配置向前看 " + lookAhead
                        + " 天内也暂无可约时段（依据：" + workLabel + lunchBit + bufferBit
                        + "）。建议换一天再问，或缩短时长后重试。";
            }
            return "抱歉，「" + name + "」在你关心的时间（" + asked + "）没有连续 "
                    + durationMin + " 分钟可用空档（依据：" + workLabel + lunchBit + bufferBit
                    + "）。建议换一天再问，或缩短时长后重试。";
        }
        int slotCount = countSlots(dayGroups);
        int freeDays = dayGroups == null ? 0
                : (int) dayGroups.stream().filter(d -> d.getSlots() != null && !d.getSlots().isEmpty()).count();
        if (expanded) {
            return "「" + name + "」在你问的时间（" + asked + "）没有空档；已按对方日程配置扩查最近 "
                    + lookAhead + " 天，找到 " + freeDays + " 天共 " + slotCount
                    + " 个可约时段（每段 " + durationMin + " 分钟，「" + intentBit + "」" + jobBit
                    + "）。请选一个时段，再点下方动作继续。";
        }
        return "按「" + intentBit + "」" + jobBit + "意图，综合规则与闲忙后，共有 " + freeDays + " 天有空、" + slotCount
                + " 个可约时段（每段 " + durationMin + " 分钟）。请选一个时段，再点下方动作继续。";
    }

    private static String buildRuleSummary(String name, RuleDayHours hours, DingTalkScheduleRuleVO rule, ParsedIntent intent) {
        String who = StringUtils.hasText(name) ? "「" + name + "」" : "对方";
        StringBuilder sb = new StringBuilder();
        sb.append(who).append("的日程规则要点：\n");
        sb.append("· 工作时间 ").append(hours.workStartLabel()).append("～").append(hours.workEndLabel());
        if (hours.lunchStart().isBefore(hours.lunchEnd())) {
            sb.append("\n· 午休 ").append(hours.lunchStartLabel()).append("～").append(hours.lunchEndLabel());
        }
        if (hours.bufferMin() > 0) {
            sb.append("\n· 日程缓存 ").append(hours.bufferMin()).append(" 分钟");
        }
        int lookAhead = resolveLookAheadDays(rule);
        sb.append("\n· 向前推荐 ").append(lookAhead).append(" 天");
        sb.append("\n· 格子步长 ").append(hours.slotStepMin()).append(" 分钟");
        if (hours.denyHolidays()) {
            sb.append("\n· 法定节假日不安排");
        }
        int blocked = rule == null || rule.getBlockedWindows() == null ? 0 : rule.getBlockedWindows().size();
        if (blocked > 0) {
            sb.append("\n· 另有 ").append(blocked).append(" 段「不安排」窗口");
        }
        if (StringUtils.hasText(intent.jobMatchNote())) {
            sb.append("\n· ").append(intent.jobMatchNote());
        }
        sb.append("\n· 本次按时长 ").append(intent.durationMin()).append(" 分钟、动作「")
                .append(intentActionLabel(intent.action())).append("」筛选可约空档");
        return sb.toString();
    }

    private static String buildBusySummary(String name, List<DingTalkBusySlotVO> slots,
                                           List<DingTalkAssistantSuggestVO.FreeWindow> windows,
                                           int durationMin, LocalDate startDay, LocalDate endDay, ParsedIntent intent) {
        int busy = 0;
        int free = 0;
        if (slots != null) {
            for (DingTalkBusySlotVO slot : slots) {
                if (slot == null || !StringUtils.hasText(slot.getStatus())) {
                    continue;
                }
                if ("FREE".equalsIgnoreCase(slot.getStatus())) {
                    free++;
                } else {
                    busy++;
                }
            }
        }
        int windowCount = windows == null ? 0 : windows.size();
        String range = startDay.equals(endDay)
                ? startDay.toString()
                : startDay + "～" + endDay;
        return "「" + name + "」钉钉闲忙（" + range + "）：忙段 " + busy + "，闲段 " + free
                + "；合并后可容纳 " + durationMin + " 分钟的连续空闲窗 " + windowCount + " 段"
                + "（已按「" + intentActionLabel(intent.action()) + "」偏好窗过滤）。";
    }

    private static String buildNextStepText(String name, int durationMin,
                                            List<DingTalkAssistantSuggestVO.DayGroup> dayGroups, SlotPick pick,
                                            ParsedIntent intent, LocalDate askedStart, LocalDate askedEnd,
                                            int lookAhead, boolean expanded, boolean searchedLookAhead) {
        int slotCount = countSlots(dayGroups);
        if (slotCount <= 0 || pick == null || pick.start() == null) {
            String asked = askedStart.equals(askedEnd) ? askedStart.toString() : askedStart + "～" + askedEnd;
            if (searchedLookAhead) {
                return "当前没有可执行的下一步动作。你问的时间（" + asked + "）以及对方配置的最近 "
                        + lookAhead + " 天内都暂无可约空档；可换日期、缩短时长或改约其他人后再问。";
            }
            return "当前没有可执行的下一步动作。你问的时间（" + asked
                    + "）暂无可约空档；可换日期、缩短时长或改约其他人后再问。";
        }
        String nearest = pick.start().format(LABEL_DAY) + "–" + pick.end().format(LABEL_TIME);
        if (expanded) {
            return "建议下一步：原问时间无空，已给出最近可约时段（优先 " + nearest
                    + "）。选中后可继续发起相应动作。";
        }
        if (DingTalkScheduleRuleService.ACTION_INTERVIEW.equals(intent.action())) {
            return "建议下一步：先选中推荐时段（优先 " + nearest + "），再发起面试邀约"
                    + (StringUtils.hasText(intent.jobName()) ? "（「" + intent.jobName() + "」）" : "") + "。";
        }
        if (DingTalkScheduleRuleService.ACTION_MEETING.equals(intent.action())) {
            return "建议下一步：先选中推荐时段（优先 " + nearest + "），再邀请开会。";
        }
        if (DingTalkScheduleRuleService.ACTION_REPORT.equals(intent.action())) {
            return "建议下一步：先选中推荐时段（优先 " + nearest + "），再安排工作汇报。";
        }
        return "建议下一步：先选中一个推荐时段（优先 " + nearest
                + "），再发起面试邀约 / 邀请开会 / 安排工作汇报。";
    }

    private static int resolveLookAheadDays(DingTalkScheduleRuleVO rule) {
        int look = rule != null && rule.getLookAheadDays() > 0 ? rule.getLookAheadDays() : 7;
        return Math.min(Math.max(look, 1), ChinaHoliday.BOOKING_MAX_DAYS);
    }

    private static int countSlots(List<DingTalkAssistantSuggestVO.DayGroup> dayGroups) {
        if (dayGroups == null || dayGroups.isEmpty()) {
            return 0;
        }
        return dayGroups.stream().mapToInt(d -> d.getSlots() == null ? 0 : d.getSlots().size()).sum();
    }

    private static List<DingTalkAssistantSuggestVO.DayGroup> keepDaysWithSlots(
            List<DingTalkAssistantSuggestVO.DayGroup> dayGroups) {
        if (dayGroups == null || dayGroups.isEmpty()) {
            return dayGroups == null ? List.of() : dayGroups;
        }
        List<DingTalkAssistantSuggestVO.DayGroup> kept = new ArrayList<>();
        for (DingTalkAssistantSuggestVO.DayGroup g : dayGroups) {
            if (g != null && g.getSlots() != null && !g.getSlots().isEmpty()) {
                kept.add(g);
            }
        }
        return kept;
    }

    private static String intentActionLabel(String action) {
        if (DingTalkScheduleRuleService.ACTION_INTERVIEW.equals(action)) {
            return "面试";
        }
        if (DingTalkScheduleRuleService.ACTION_MEETING.equals(action)) {
            return "开会";
        }
        if (DingTalkScheduleRuleService.ACTION_REPORT.equals(action)) {
            return "汇报";
        }
        return "查闲忙";
    }

    /** 将午休、不安排时段标为忙，供后续合并空闲窗 */
    private static List<DingTalkBusySlotVO> applyRuleToBusySlots(
            List<DingTalkBusySlotVO> slots, DingTalkScheduleRuleVO rule, RuleDayHours hours) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        List<DingTalkBusySlotVO> out = new ArrayList<>(slots.size());
        for (DingTalkBusySlotVO slot : slots) {
            if (slot == null || slot.getStart() == null || slot.getEnd() == null) {
                continue;
            }
            DingTalkBusySlotVO copy = new DingTalkBusySlotVO();
            copy.setStart(slot.getStart());
            copy.setEnd(slot.getEnd());
            boolean free = "FREE".equalsIgnoreCase(slot.getStatus());
            if (free && (overlapsLunch(slot.getStart(), slot.getEnd(), hours)
                    || overlapsBlocked(rule, slot.getStart(), slot.getEnd()))) {
                copy.setStatus("BUSY");
                copy.setStatusLabel("忙");
            } else {
                copy.setStatus(slot.getStatus());
                copy.setStatusLabel(slot.getStatusLabel());
            }
            out.add(copy);
        }
        return out;
    }

    private static RuleDayHours resolveDayHours(DingTalkScheduleRuleVO rule) {
        LocalTime workStart = parseHmOr(rule == null ? null : rule.getWorkStart(), DEFAULT_WORK_START);
        LocalTime workEnd = parseHmOr(rule == null ? null : rule.getWorkEnd(), DEFAULT_WORK_END);
        if (!workStart.isBefore(workEnd)) {
            workStart = DEFAULT_WORK_START;
            workEnd = DEFAULT_WORK_END;
        }
        LocalTime lunchStart = parseHmOr(rule == null ? null : rule.getLunchStart(), DEFAULT_LUNCH_START);
        LocalTime lunchEnd = parseHmOr(rule == null ? null : rule.getLunchEnd(), DEFAULT_LUNCH_END);
        if (!lunchStart.isBefore(lunchEnd)
                || lunchEnd.isBefore(workStart) || !lunchStart.isBefore(workEnd)) {
            // 午休非法或不在工作日内时视为无午休
            lunchStart = LocalTime.MIDNIGHT;
            lunchEnd = LocalTime.MIDNIGHT;
        }
        int buffer = rule == null ? 15 : Math.max(0, rule.getBufferMin());
        int slotStep = rule == null ? DEFAULT_SLOT_STEP_MIN : rule.getSlotMin();
        if (slotStep != 10 && slotStep != 20 && slotStep != 30 && slotStep != 40 && slotStep != 50 && slotStep != 60) {
            slotStep = DEFAULT_SLOT_STEP_MIN;
        }
        boolean denyHolidays = rule == null || rule.isDenyHolidays();
        return new RuleDayHours(workStart, workEnd, lunchStart, lunchEnd, buffer, slotStep, denyHolidays);
    }

    private static LocalTime parseHmOr(String raw, LocalTime fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception ignored) {
            try {
                return LocalTime.parse(raw.trim(), LABEL_TIME);
            } catch (Exception ex) {
                return fallback;
            }
        }
    }

    private static LocalTime parseHmNullable(String raw) {
        return parseHmOr(raw, null);
    }

    private static boolean overlapsLunch(LocalDateTime start, LocalDateTime end, RuleDayHours hours) {
        if (hours.lunchStart().equals(hours.lunchEnd()) || start == null || end == null
                || !start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        LocalTime st = start.toLocalTime();
        LocalTime et = end.toLocalTime();
        return st.isBefore(hours.lunchEnd()) && et.isAfter(hours.lunchStart());
    }

    private static boolean overlapsBlocked(DingTalkScheduleRuleVO rule, LocalDateTime start, LocalDateTime end) {
        if (rule == null || rule.getBlockedWindows() == null || rule.getBlockedWindows().isEmpty()
                || start == null || end == null || !start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        int dow = start.getDayOfWeek().getValue();
        LocalTime st = start.toLocalTime();
        LocalTime et = end.toLocalTime();
        for (DingTalkScheduleRuleVO.Window w : rule.getBlockedWindows()) {
            if (w == null || w.getWeekdays() == null || !w.getWeekdays().contains(dow)) {
                continue;
            }
            LocalTime ws = parseHmNullable(w.getStartTime());
            LocalTime we = parseHmNullable(w.getEndTime());
            if (ws == null || we == null || !ws.isBefore(we)) {
                continue;
            }
            if (st.isBefore(we) && et.isAfter(ws)) {
                return true;
            }
        }
        return false;
    }

    private ParsedIntent resolveIntent(DingTalkAssistantSuggestDTO dto, DingTalkScheduleRuleVO rule, RuleDayHours hours) {
        ParsedIntent heuristic = parseIntentHeuristic(dto, rule, hours);
        if (StringUtils.hasText(dto.getMessage()) && aiChatService.isEnabled()) {
            try {
                ParsedIntent ai = parseIntentWithAi(dto.getMessage(), heuristic, rule, hours);
                if (ai != null) {
                    return mergeJobMatch(ai, rule);
                }
            } catch (Exception ignored) {
                // AI 失败时回退启发式
            }
        }
        return mergeJobMatch(heuristic, rule);
    }

    private ParsedIntent parseIntentWithAi(String message, ParsedIntent fallback,
                                           DingTalkScheduleRuleVO rule, RuleDayHours hours) {
        LocalDate today = LocalDate.now();
        String system = """
                你是日程助手意图解析器。只输出 JSON，不要 Markdown。
                字段：
                action: interview|meeting|report|busy_query
                jobName: 岗位名或 null（仅面试时尽量提取，如「品牌总监」）
                durationMin: 15~240 的整数或 null
                startDate: yyyy-MM-dd 或 null（相对词「今天/明天/后天/本周/下周」请输出 null，由服务端自己算）
                endDate: yyyy-MM-dd 或 null
                规则：提到面试/邀约/候选人 → interview；开会/会议 → meeting；汇报 → report；只问有没有空 → busy_query。
                今天是 %s。日期必须 ≥ 今天；拿不准就输出 null。
                """.formatted(today);
        String raw = aiChatService.chat(system, "用户原话：\n" + message);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String json = raw.trim();
        int l = json.indexOf('{');
        int r = json.lastIndexOf('}');
        if (l >= 0 && r > l) {
            json = json.substring(l, r + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String action = normalizeAction(textOrNull(node, "action"), fallback.action());
            Integer duration = intOrNull(node, "durationMin");
            if (duration == null) {
                duration = fallback.durationMin();
            }
            String jobName = textOrNull(node, "jobName");
            if (!StringUtils.hasText(jobName)) {
                jobName = fallback.jobName();
            }
            // 相对日期词 / 启发式已给出的范围优先；AI 日期若过期或缺失则回退
            LocalDateTime rangeStart = fallback.rangeStart();
            LocalDateTime rangeEnd = fallback.rangeEnd();
            if (!hasRelativeDayWord(message)) {
                LocalDate start = parseDateOrNull(textOrNull(node, "startDate"));
                LocalDate end = parseDateOrNull(textOrNull(node, "endDate"));
                if (start != null && end != null && !end.isBefore(start)
                        && !start.isBefore(today) && !end.isBefore(today)) {
                    rangeStart = start.atTime(hours.workStart());
                    rangeEnd = end.atTime(hours.workEnd());
                }
            }
            duration = clampDuration(duration, action, rule);
            return new ParsedIntent(action, jobName, null, duration, rangeStart, rangeEnd);
        } catch (Exception e) {
            return null;
        }
    }

    private ParsedIntent parseIntentHeuristic(DingTalkAssistantSuggestDTO dto, DingTalkScheduleRuleVO rule, RuleDayHours hours) {
        String msg = dto.getMessage() == null ? "" : dto.getMessage();
        String action = normalizeAction(dto.getAction(), null);
        if (!StringUtils.hasText(action)) {
            if (containsAny(msg, "面试", "邀约", "候选人", "一面", "二面", "终面")) {
                action = DingTalkScheduleRuleService.ACTION_INTERVIEW;
            } else if (containsAny(msg, "汇报")) {
                action = DingTalkScheduleRuleService.ACTION_REPORT;
            } else if (containsAny(msg, "开会", "会议", "约个会")) {
                action = DingTalkScheduleRuleService.ACTION_MEETING;
            } else {
                action = "busy_query";
            }
        }

        String jobName = StringUtils.hasText(dto.getJobName()) ? dto.getJobName().trim() : extractJobName(msg, rule);

        Integer duration = dto.getDurationMin();
        if (duration == null) {
            duration = extractDuration(msg);
        }
        duration = clampDuration(duration, action, rule);

        LocalDateTime[] range = resolveQueryRange(msg, dto.getStartTime(), dto.getEndTime(), rule, hours);
        return new ParsedIntent(action, jobName, null, duration, range[0], range[1]);
    }

    /**
     * 解析查询日期范围。优先级：口语相对词 → 口语显式日期 → 入参（且未过期）→ lookAhead 默认。
     */
    private static LocalDateTime[] resolveQueryRange(String msg, LocalDateTime dtoStart, LocalDateTime dtoEnd,
                                                     DingTalkScheduleRuleVO rule, RuleDayHours hours) {
        LocalDate today = LocalDate.now();
        int lookAhead = rule != null && rule.getLookAheadDays() > 0 ? rule.getLookAheadDays() : 7;
        lookAhead = Math.min(lookAhead, ChinaHoliday.BOOKING_MAX_DAYS);

        LocalDateTime rangeStart = null;
        LocalDateTime rangeEnd = null;

        if (StringUtils.hasText(msg)) {
            if (msg.contains("明天")) {
                LocalDate d = today.plusDays(1);
                rangeStart = d.atTime(hours.workStart());
                rangeEnd = d.atTime(hours.workEnd());
            } else if (msg.contains("后天")) {
                LocalDate d = today.plusDays(2);
                rangeStart = d.atTime(hours.workStart());
                rangeEnd = d.atTime(hours.workEnd());
            } else if (msg.contains("今天")) {
                rangeStart = today.atTime(hours.workStart());
                rangeEnd = today.atTime(hours.workEnd());
            } else if (msg.contains("下周")) {
                LocalDate start = today.plusWeeks(1).with(java.time.DayOfWeek.MONDAY);
                rangeStart = start.atTime(hours.workStart());
                rangeEnd = start.plusDays(4).atTime(hours.workEnd());
            } else if (msg.contains("本周") || msg.contains("这周")) {
                rangeStart = today.atTime(hours.workStart());
                rangeEnd = today.plusDays(Math.min(4, lookAhead - 1L)).atTime(hours.workEnd());
            } else {
                LocalDate[] explicitRange = extractDateRange(msg);
                LocalDate explicitStart = extractSingleDate(msg);
                if (explicitRange != null) {
                    rangeStart = explicitRange[0].atTime(hours.workStart());
                    rangeEnd = explicitRange[1].atTime(hours.workEnd());
                } else if (explicitStart != null) {
                    rangeStart = explicitStart.atTime(hours.workStart());
                    rangeEnd = explicitStart.atTime(hours.workEnd());
                }
            }
        }

        if (rangeStart == null || rangeEnd == null) {
            if (dtoStart != null && dtoEnd != null && !dtoEnd.isBefore(dtoStart)
                    && !dtoStart.toLocalDate().isBefore(today) && !dtoEnd.toLocalDate().isBefore(today)) {
                rangeStart = dtoStart.toLocalDate().atTime(hours.workStart());
                rangeEnd = dtoEnd.toLocalDate().atTime(hours.workEnd());
            } else {
                rangeStart = today.atTime(hours.workStart());
                rangeEnd = today.plusDays(lookAhead - 1L).atTime(hours.workEnd());
            }
        }

        // 兜底：整段已过期则夹到今天起 lookAhead
        if (rangeEnd.toLocalDate().isBefore(today)) {
            rangeStart = today.atTime(hours.workStart());
            rangeEnd = today.plusDays(lookAhead - 1L).atTime(hours.workEnd());
        } else if (rangeStart.toLocalDate().isBefore(today)) {
            rangeStart = today.atTime(hours.workStart());
        }
        if (rangeEnd.isBefore(rangeStart)) {
            rangeEnd = rangeStart.toLocalDate().atTime(hours.workEnd());
        }
        return new LocalDateTime[]{rangeStart, rangeEnd};
    }

    private static boolean hasRelativeDayWord(String msg) {
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("今天") || msg.contains("明天") || msg.contains("后天")
                || msg.contains("本周") || msg.contains("这周") || msg.contains("下周")
                || (msg.contains("近") && msg.contains("天"))
                || (msg.contains("未来") && msg.contains("天"))
                || (msg.contains("接下来") && msg.contains("天"));
    }

    private ParsedIntent mergeJobMatch(ParsedIntent intent, DingTalkScheduleRuleVO rule) {
        if (!DingTalkScheduleRuleService.ACTION_INTERVIEW.equals(intent.action())) {
            return intent;
        }
        String note;
        String matched = matchInterviewJob(intent.jobName(), rule);
        if (!StringUtils.hasText(intent.jobName())) {
            note = "未识别到具体岗位；按通用「面试邀约」偏好时长与时段推荐（未套用面试频次岗位名单）";
        } else if (matched == null) {
            note = "岗位「" + intent.jobName() + "」未出现在对方面试频次规则的小岗位/重要岗位名单中；"
                    + "仍按通用面试偏好推荐，请人工确认是否合适";
        } else if ("small".equals(matched)) {
            note = "岗位「" + intent.jobName() + "」匹配小岗位面试频次规则";
        } else {
            note = "岗位「" + intent.jobName() + "」匹配重要岗位面试频次规则";
        }
        return new ParsedIntent(intent.action(), intent.jobName(), note, intent.durationMin(),
                intent.rangeStart(), intent.rangeEnd());
    }

    private static String matchInterviewJob(String jobName, DingTalkScheduleRuleVO rule) {
        if (!StringUtils.hasText(jobName) || rule == null || rule.getMeetingPriority() == null
                || rule.getMeetingPriority().getInterviewFreq() == null) {
            return null;
        }
        DingTalkScheduleRuleVO.InterviewFreq freq = rule.getMeetingPriority().getInterviewFreq();
        if (listContainsJob(freq.getImportantJobs(), jobName)) {
            return "important";
        }
        if (listContainsJob(freq.getSmallJobs(), jobName)) {
            return "small";
        }
        return null;
    }

    private static boolean listContainsJob(List<String> jobs, String jobName) {
        if (jobs == null || !StringUtils.hasText(jobName)) {
            return false;
        }
        String target = jobName.trim();
        for (String j : jobs) {
            if (!StringUtils.hasText(j)) {
                continue;
            }
            String x = j.trim();
            if (x.equalsIgnoreCase(target) || x.contains(target) || target.contains(x)) {
                return true;
            }
        }
        return false;
    }

    private static String extractJobName(String msg, DingTalkScheduleRuleVO rule) {
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        Matcher m = Pattern.compile("面试(?:一个|一位|下)?([^，。！？\\s]{2,20})").matcher(msg);
        if (m.find()) {
            String raw = m.group(1).replaceAll("(的)?(岗位|职位|候选人)?$", "").trim();
            if (StringUtils.hasText(raw) && !raw.equals("人")) {
                return raw;
            }
        }
        // 对照规则名单做包含匹配
        if (rule != null && rule.getMeetingPriority() != null && rule.getMeetingPriority().getInterviewFreq() != null) {
            DingTalkScheduleRuleVO.InterviewFreq freq = rule.getMeetingPriority().getInterviewFreq();
            List<String> all = new ArrayList<>();
            if (freq.getImportantJobs() != null) {
                all.addAll(freq.getImportantJobs());
            }
            if (freq.getSmallJobs() != null) {
                all.addAll(freq.getSmallJobs());
            }
            String hit = null;
            for (String j : all) {
                if (StringUtils.hasText(j) && msg.contains(j.trim())) {
                    if (hit == null || j.trim().length() > hit.length()) {
                        hit = j.trim();
                    }
                }
            }
            return hit;
        }
        return null;
    }

    private static Integer extractDuration(String msg) {
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        if (msg.contains("半小时") || msg.contains("半个小时")) {
            return 30;
        }
        if (msg.contains("一个半小时") || msg.contains("1.5小时")) {
            return 90;
        }
        if (msg.contains("两小时") || msg.contains("2小时")) {
            return 120;
        }
        if (msg.contains("一小时") || msg.contains("1小时")) {
            return 60;
        }
        Matcher min = Pattern.compile("(\\d{1,3})\\s*分钟").matcher(msg);
        if (min.find()) {
            return Integer.parseInt(min.group(1));
        }
        Matcher hour = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*小时").matcher(msg);
        if (hour.find()) {
            return (int) Math.round(Double.parseDouble(hour.group(1)) * 60);
        }
        return null;
    }

    private static LocalDate[] extractDateRange(String msg) {
        Matcher m = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})\\s*[~～\\-到至]\\s*(\\d{1,2})[./-](\\d{1,2})").matcher(msg);
        if (!m.find()) {
            return null;
        }
        int y = LocalDate.now().getYear();
        LocalDate a = LocalDate.of(y, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        LocalDate b = LocalDate.of(y, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        if (a.isBefore(LocalDate.now().minusDays(1))) {
            a = a.plusYears(1);
        }
        if (b.isBefore(a)) {
            b = b.plusYears(1);
        }
        return new LocalDate[]{a, b};
    }

    private static LocalDate extractSingleDate(String msg) {
        Matcher m = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})(?!\\d)").matcher(msg);
        if (!m.find()) {
            return null;
        }
        int y = LocalDate.now().getYear();
        LocalDate d = LocalDate.of(y, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        if (d.isBefore(LocalDate.now().minusDays(1))) {
            d = d.plusYears(1);
        }
        return d;
    }

    private static int clampDuration(Integer duration, String action, DingTalkScheduleRuleVO rule) {
        int d = duration == null ? preferDuration(action, rule) : duration;
        if (d < 15) {
            d = 15;
        }
        if (d > 240) {
            d = 240;
        }
        DingTalkScheduleRuleVO.ActionPref pref = findActionPref(rule, action);
        if (pref != null && pref.getMaxDurationMin() != null && d > pref.getMaxDurationMin()) {
            d = pref.getMaxDurationMin();
        }
        return d;
    }

    private static int preferDuration(String action, DingTalkScheduleRuleVO rule) {
        DingTalkScheduleRuleVO.ActionPref pref = findActionPref(rule, action);
        if (pref != null && pref.getPreferDurationMin() > 0) {
            return pref.getPreferDurationMin();
        }
        return DingTalkScheduleRuleService.ACTION_INTERVIEW.equals(action) ? 60 : DEFAULT_DURATION;
    }

    private static DingTalkScheduleRuleVO.ActionPref findActionPref(DingTalkScheduleRuleVO rule, String action) {
        if (rule == null || rule.getActionPrefs() == null || !StringUtils.hasText(action)) {
            return null;
        }
        for (DingTalkScheduleRuleVO.ActionPref p : rule.getActionPrefs()) {
            if (p != null && action.equalsIgnoreCase(p.getAction())) {
                return p;
            }
        }
        return null;
    }

    private static String normalizeAction(String raw, String fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        String a = raw.trim().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "interview", "面试", "面试邀约" -> DingTalkScheduleRuleService.ACTION_INTERVIEW;
            case "meeting", "开会", "会议" -> DingTalkScheduleRuleService.ACTION_MEETING;
            case "report", "汇报" -> DingTalkScheduleRuleService.ACTION_REPORT;
            case "busy_query", "busy", "闲忙" -> "busy_query";
            default -> fallback == null ? "busy_query" : fallback;
        };
    }

    private static boolean containsAny(String text, String... keys) {
        if (!StringUtils.hasText(text) || keys == null) {
            return false;
        }
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText(null);
        return StringUtils.hasText(v) && !"null".equalsIgnoreCase(v) ? v.trim() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        if (node.get(field).isInt() || node.get(field).isLong()) {
            return node.get(field).asInt();
        }
        try {
            return Integer.parseInt(node.get(field).asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private record ParsedIntent(String action, String jobName, String jobMatchNote, int durationMin,
                                LocalDateTime rangeStart, LocalDateTime rangeEnd) {
    }

    private record RuleDayHours(
            LocalTime workStart,
            LocalTime workEnd,
            LocalTime lunchStart,
            LocalTime lunchEnd,
            int bufferMin,
            int slotStepMin,
            boolean denyHolidays
    ) {
        String workStartLabel() {
            return workStart.format(LABEL_TIME);
        }

        String workEndLabel() {
            return workEnd.format(LABEL_TIME);
        }

        String lunchStartLabel() {
            return lunchStart.format(LABEL_TIME);
        }

        String lunchEndLabel() {
            return lunchEnd.format(LABEL_TIME);
        }
    }

    private static List<DingTalkAssistantSuggestVO.Action> baseActions(DingTalkAssistantSuggestVO vo,
                                                                        SlotPick pick, int durationMin, String action) {
        List<DingTalkAssistantSuggestVO.Action> actions = new ArrayList<>();
        Map<String, Object> common = basePayload(vo, pick, durationMin);
        String act = StringUtils.hasText(action) ? action : "busy_query";

        if (DingTalkScheduleRuleService.ACTION_INTERVIEW.equals(act) || "busy_query".equals(act)) {
            DingTalkAssistantSuggestVO.Action invite = new DingTalkAssistantSuggestVO.Action();
            invite.setType("CREATE_INVITE");
            invite.setLabel("发起面试邀约");
            invite.setHint("打开邀约表单，面试官与时间已预填");
            invite.setPayload(new LinkedHashMap<>(common));
            actions.add(invite);
        }

        if (DingTalkScheduleRuleService.ACTION_MEETING.equals(act) || "busy_query".equals(act)) {
            DingTalkAssistantSuggestVO.Action meeting = new DingTalkAssistantSuggestVO.Action();
            meeting.setType("CREATE_MEETING");
            meeting.setLabel("邀请他参加会议");
            meeting.setHint("填写会议主题/地点等（对齐钉钉日程），为双方建日程并通知你");
            meeting.setPayload(new LinkedHashMap<>(common));
            actions.add(meeting);
        }

        if (DingTalkScheduleRuleService.ACTION_REPORT.equals(act) || "busy_query".equals(act)) {
            DingTalkAssistantSuggestVO.Action report = new DingTalkAssistantSuggestVO.Action();
            report.setType("CREATE_REPORT_TASK");
            report.setLabel("跟他汇报工作");
            report.setHint("创建任务，并为双方建钉钉日程，同时通知你");
            report.setPayload(new LinkedHashMap<>(common));
            actions.add(report);
        }

        DingTalkAssistantSuggestVO.Action busy = new DingTalkAssistantSuggestVO.Action();
        busy.setType("VIEW_BUSY");
        busy.setLabel("查看完整闲忙");
        busy.setHint("打开闲忙表核对明细");
        busy.setPayload(new LinkedHashMap<>(common));
        actions.add(busy);

        return actions;
    }

    private static Map<String, Object> basePayload(DingTalkAssistantSuggestVO vo, SlotPick pick, int durationMin) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetUserId", vo.getTargetUserId());
        payload.put("targetNickname", vo.getTargetNickname());
        payload.put("targetUsername", vo.getTargetUsername());
        payload.put("durationMin", durationMin);
        if (pick != null && pick.start() != null) {
            payload.put("suggestedStart", pick.start().format(API_TIME));
            if (pick.end() != null) {
                payload.put("suggestedEnd", pick.end().format(API_TIME));
            }
        }
        return payload;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record SlotPick(LocalDateTime start, LocalDateTime end) {
    }

    private record BoundUser(Long userId, String username, String nickname, String unionId, String dingUserId) {
    }

    private record CalendarPair(boolean querierOk, String querierEventId, String querierError,
                                boolean targetOk, String targetEventId, String targetError) {
    }

    private record ScheduleRow(Long id, String kind, String title, String description, String location, int onlineMeeting,
                               LocalDateTime startTime, int durationMin, Long querierUserId, Long targetUserId,
                               String querierEventId, String targetEventId, Long taskId, String status) {
    }
}
