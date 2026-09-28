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

    public DingTalkAssistantSuggestVO suggest(DingTalkAssistantSuggestDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BusinessException("请选择要查询的同事");
        }
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new BusinessException("请选择日期范围");
        }
        int duration = dto.getDurationMin() == null ? DEFAULT_DURATION : dto.getDurationMin();
        if (duration < 15 || duration > 240) {
            throw new BusinessException("期望时长须在 15～240 分钟之间");
        }

        // 空闲时段严格按「被问询人」的日程规则（工作时间/午休/缓存/不安排窗）
        DingTalkScheduleRuleVO rule = dingTalkScheduleRuleService.loadOrDefault(dto.getTargetUserId());
        RuleDayHours hours = resolveDayHours(rule);
        LocalDate startDay = dto.getStartTime().toLocalDate();
        LocalDate endDay = dto.getEndTime().toLocalDate();
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
        if (user == null) {
            vo.setTargetNickname("未知用户");
            vo.setError("未查到该用户");
            vo.setAdviceText("未查到该用户，请换人再试。");
            return vo;
        }
        vo.setTargetUsername(user.getUsername());
        vo.setTargetNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        if (StringUtils.hasText(user.getError())) {
            vo.setError(user.getError());
            vo.setAdviceText(vo.getTargetNickname() + "：" + user.getError());
            vo.setActions(baseActions(vo, null, duration));
            return vo;
        }

        List<DingTalkBusySlotVO> slots = applyRuleToBusySlots(user.getSlots(), rule, hours);
        List<DingTalkAssistantSuggestVO.FreeWindow> windows = findFreeWindows(slots, duration, hours);
        List<DingTalkAssistantSuggestVO.DayGroup> dayGroups = buildDayGroups(
                rangeStart, rangeEnd, windows, duration, rule, hours);
        vo.setFreeWindows(windows);
        vo.setDayGroups(dayGroups);
        vo.setAdviceText(buildAdvice(vo.getTargetNickname(), duration, dayGroups, hours));
        SlotPick pick = firstSlot(dayGroups, duration);
        vo.setActions(baseActions(vo, pick, duration));
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
    private static List<DingTalkAssistantSuggestVO.DayGroup> buildDayGroups(
            LocalDateTime rangeStart, LocalDateTime rangeEnd,
            List<DingTalkAssistantSuggestVO.FreeWindow> windows, int durationMin,
            DingTalkScheduleRuleVO rule, RuleDayHours hours) {
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
                                      RuleDayHours hours) {
        int slotCount = dayGroups == null ? 0 : dayGroups.stream().mapToInt(d -> d.getSlots() == null ? 0 : d.getSlots().size()).sum();
        int freeDays = dayGroups == null ? 0 : (int) dayGroups.stream().filter(d -> d.getSlots() != null && !d.getSlots().isEmpty()).count();
        String workLabel = "每天 " + hours.workStartLabel() + "～" + hours.workEndLabel();
        String lunchBit = hours.lunchStart().isBefore(hours.lunchEnd())
                ? "，已避开午休 " + hours.lunchStartLabel() + "～" + hours.lunchEndLabel()
                : "";
        String bufferBit = hours.bufferMin() > 0 ? "，日程缓存 " + hours.bufferMin() + " 分钟" : "";
        if (slotCount == 0) {
            return "按「" + name + "」的日程规则（" + workLabel + lunchBit + bufferBit + "）与钉钉闲忙，所选范围内没有连续 "
                    + durationMin + " 分钟空闲。";
        }
        return "按「" + name + "」的日程规则（" + workLabel + lunchBit + bufferBit + "）与钉钉闲忙，共有 "
                + freeDays + " 天有空、" + slotCount + " 个可约时段（每段 " + durationMin
                + " 分钟）。请按天切换查看后再发起面试/会议/汇报。";
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
                                                                        SlotPick pick, int durationMin) {
        List<DingTalkAssistantSuggestVO.Action> actions = new ArrayList<>();
        Map<String, Object> common = basePayload(vo, pick, durationMin);

        DingTalkAssistantSuggestVO.Action invite = new DingTalkAssistantSuggestVO.Action();
        invite.setType("CREATE_INVITE");
        invite.setLabel("发起面试邀约");
        invite.setHint("打开邀约表单，面试官与时间已预填");
        invite.setPayload(new LinkedHashMap<>(common));
        actions.add(invite);

        DingTalkAssistantSuggestVO.Action meeting = new DingTalkAssistantSuggestVO.Action();
        meeting.setType("CREATE_MEETING");
        meeting.setLabel("邀请他参加会议");
        meeting.setHint("填写会议主题/地点等（对齐钉钉日程），为双方建日程并通知你");
        meeting.setPayload(new LinkedHashMap<>(common));
        actions.add(meeting);

        DingTalkAssistantSuggestVO.Action report = new DingTalkAssistantSuggestVO.Action();
        report.setType("CREATE_REPORT_TASK");
        report.setLabel("跟他汇报工作");
        report.setHint("创建任务，并为双方建钉钉日程，同时通知你");
        report.setPayload(new LinkedHashMap<>(common));
        actions.add(report);

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
