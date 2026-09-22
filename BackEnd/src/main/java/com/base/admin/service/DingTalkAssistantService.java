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
import com.base.admin.exception.BusinessException;
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
    private static final int MAX_WINDOWS = 8;
    private static final int MAX_SLOTS_PER_DAY = 24;
    private static final int SLOT_STEP_MIN = 30;
    private static final DateTimeFormatter LABEL_DAY = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter LABEL_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_TAB = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final DingTalkBusyService busyService;
    private final DingTalkCalendarClient dingTalk;
    private final SysTaskService taskService;
    private final JdbcTemplate jdbc;

    public DingTalkAssistantSuggestVO suggest(DingTalkAssistantSuggestDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BusinessException("请选择要查询的同事");
        }
        int duration = dto.getDurationMin() == null ? DEFAULT_DURATION : dto.getDurationMin();
        if (duration < 15 || duration > 240) {
            throw new BusinessException("期望时长须在 15～240 分钟之间");
        }

        DingTalkBusyQueryDTO query = new DingTalkBusyQueryDTO();
        query.setUserIds(List.of(dto.getTargetUserId()));
        query.setStartTime(dto.getStartTime());
        query.setEndTime(dto.getEndTime());
        List<DingTalkBusyUserVO> rows = busyService.query(query);
        DingTalkBusyUserVO user = rows.isEmpty() ? null : rows.getFirst();

        DingTalkAssistantSuggestVO vo = new DingTalkAssistantSuggestVO();
        vo.setTargetUserId(dto.getTargetUserId());
        vo.setDurationMin(duration);
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

        List<DingTalkAssistantSuggestVO.FreeWindow> windows = findFreeWindows(user.getSlots(), duration);
        List<DingTalkAssistantSuggestVO.DayGroup> dayGroups = buildDayGroups(windows, duration);
        vo.setFreeWindows(windows);
        vo.setDayGroups(dayGroups);
        vo.setAdviceText(buildAdvice(vo.getTargetNickname(), duration, dayGroups));
        SlotPick pick = firstSlot(dayGroups, duration);
        vo.setActions(baseActions(vo, pick, duration));
        return vo;
    }

    @Transactional
    public DingTalkAssistantActionResultVO createMeeting(DingTalkAssistantMeetingDTO dto) {
        BoundUser querier = requireCurrentBound();
        BoundUser target = requireBound(dto.getTargetUserId(), "被邀请人");
        int duration = dto.getDurationMin() == null ? DEFAULT_DURATION : dto.getDurationMin();
        String title = dto.getTitle().trim();
        String description = blankToEmpty(dto.getDescription());
        String location = blankToEmpty(dto.getLocation());
        boolean online = Boolean.TRUE.equals(dto.getOnlineMeeting());
        if (online && !StringUtils.hasText(location)) {
            location = "钉钉视频会议";
        }

        CalendarPair calendars = createPairCalendars(
                querier, target, title, description, dto.getStartTime(), duration, location, online);
        if (!calendars.querierOk() && !calendars.targetOk()) {
            throw new BusinessException(buildActionMessage("会议", calendars, false));
        }
        Long recordId = insertRecord("MEETING", title, description, location, online ? 1 : 0,
                dto.getStartTime(), duration, querier, target,
                calendars.querierEventId(), calendars.targetEventId(), null);

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
        BoundUser querier = requireCurrentBound();
        BoundUser target = requireBound(dto.getTargetUserId(), "汇报对象");
        int duration = dto.getDurationMin() == null ? DEFAULT_DURATION : dto.getDurationMin();
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

        CalendarPair calendars = createPairCalendars(
                querier, target, title, content, dto.getStartTime(), duration, location, false);
        Long recordId = insertRecord("REPORT", title, content, location, 0,
                dto.getStartTime(), duration, querier, target,
                calendars.querierEventId(), calendars.targetEventId(), taskId);

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
                WHERE s.is_active = 1 AND s.querier_user_id = ?
                """);
        List<Object> args = new ArrayList<>();
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
        List<String> both = List.of(querier.unionId(), target.unionId());

        String querierEventId = row.querierEventId();
        String targetEventId = row.targetEventId();
        String querierErr = null;
        String targetErr = null;
        boolean querierOk = false;
        boolean targetOk = false;

        if (StringUtils.hasText(querierEventId)) {
            DingTalkCalendarClient.CalendarCall call = dingTalk.updateEvent(
                    querier.unionId(), querierEventId, title, description, dto.getStartTime(), duration, location, both, online);
            querierOk = call.success();
            querierErr = call.message();
            if (!querierOk) {
                DingTalkCalendarClient.CalendarCall recreate = dingTalk.createEvent(
                        querier.unionId(), title, description, dto.getStartTime(), duration, location, both, online);
                querierOk = recreate.success();
                querierEventId = recreate.eventId();
                querierErr = recreate.message();
            }
        } else {
            DingTalkCalendarClient.CalendarCall recreate = dingTalk.createEvent(
                    querier.unionId(), title, description, dto.getStartTime(), duration, location, both, online);
            querierOk = recreate.success();
            querierEventId = recreate.eventId();
            querierErr = recreate.message();
        }

        if (StringUtils.hasText(targetEventId)) {
            DingTalkCalendarClient.CalendarCall call = dingTalk.updateEvent(
                    target.unionId(), targetEventId, title, description, dto.getStartTime(), duration, location, both, online);
            targetOk = call.success();
            targetErr = call.message();
            if (!targetOk) {
                DingTalkCalendarClient.CalendarCall recreate = dingTalk.createEvent(
                        target.unionId(), title, description, dto.getStartTime(), duration, location, both, online);
                targetOk = recreate.success();
                targetEventId = recreate.eventId();
                targetErr = recreate.message();
            }
        } else {
            DingTalkCalendarClient.CalendarCall recreate = dingTalk.createEvent(
                    target.unionId(), title, description, dto.getStartTime(), duration, location, both, online);
            targetOk = recreate.success();
            targetEventId = recreate.eventId();
            targetErr = recreate.message();
        }

        if (!querierOk && !targetOk) {
            throw new BusinessException("更新钉钉日程失败：" + blankToEmpty(querierErr) + " / " + blankToEmpty(targetErr));
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

        CalendarPair pair = new CalendarPair(querierOk, querierEventId, querierErr, targetOk, targetEventId, targetErr);
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

    private CalendarPair createPairCalendars(BoundUser querier, BoundUser target, String title, String description,
                                             LocalDateTime start, int duration, String location, boolean online) {
        List<String> both = List.of(querier.unionId(), target.unionId());
        DingTalkCalendarClient.CalendarCall forQuerier = dingTalk.createEvent(
                querier.unionId(), title, description, start, duration, location, both, online);
        DingTalkCalendarClient.CalendarCall forTarget = dingTalk.createEvent(
                target.unionId(), title, description, start, duration, location, both, online);
        return new CalendarPair(
                forQuerier.success(), forQuerier.eventId(), forQuerier.message(),
                forTarget.success(), forTarget.eventId(), forTarget.message());
    }

    private boolean notifyQuerier(BoundUser querier, String title, String markdown) {
        if (!StringUtils.hasText(querier.dingUserId())) {
            return false;
        }
        return dingTalk.notifyMarkdown(querier.dingUserId(), title, markdown).success();
    }

    private static String buildActionMessage(String kind, CalendarPair calendars, boolean noticeSent) {
        StringBuilder sb = new StringBuilder();
        if (calendars.querierOk() && calendars.targetOk()) {
            sb.append("已为你和对方创建钉钉日程。");
        } else if (calendars.querierOk()) {
            sb.append("已为你创建钉钉日程；对方日程失败：").append(blankToEmpty(calendars.targetError())).append("。");
        } else if (calendars.targetOk()) {
            sb.append("已为对方创建钉钉日程；你的日程失败：").append(blankToEmpty(calendars.querierError())).append("。");
        } else {
            sb.append(kind).append("日程创建失败：")
                    .append(blankToEmpty(calendars.querierError()))
                    .append(" / ")
                    .append(blankToEmpty(calendars.targetError()));
            return sb.toString();
        }
        if (noticeSent) {
            sb.append(" 已向你发送钉钉工作通知。");
        } else {
            sb.append(" 未能向你发送钉钉工作通知（请确认已绑定钉钉 userid）。");
        }
        return sb.toString();
    }

    private BoundUser requireCurrentBound() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return requireBound(userId, "当前用户");
    }

    private BoundUser requireBound(Long userId, String who) {
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

    private static List<DingTalkAssistantSuggestVO.DayGroup> buildDayGroups(
            List<DingTalkAssistantSuggestVO.FreeWindow> windows, int durationMin) {
        Map<String, DingTalkAssistantSuggestVO.DayGroup> map = new LinkedHashMap<>();
        for (DingTalkAssistantSuggestVO.FreeWindow window : windows) {
            if (window.getStart() == null || window.getEnd() == null) {
                continue;
            }
            LocalDateTime cursor = window.getStart();
            while (!cursor.plusMinutes(durationMin).isAfter(window.getEnd())) {
                LocalDate day = cursor.toLocalDate();
                String key = day.format(DAY_KEY);
                DingTalkAssistantSuggestVO.DayGroup group = map.computeIfAbsent(key, k -> {
                    DingTalkAssistantSuggestVO.DayGroup g = new DingTalkAssistantSuggestVO.DayGroup();
                    g.setDay(k);
                    g.setDayLabel(day.format(DAY_TAB) + " " + WEEK[day.getDayOfWeek().getValue() - 1]);
                    return g;
                });
                if (group.getSlots().size() >= MAX_SLOTS_PER_DAY) {
                    break;
                }
                LocalDateTime slotEnd = cursor.plusMinutes(durationMin);
                DingTalkAssistantSuggestVO.SlotOption slot = new DingTalkAssistantSuggestVO.SlotOption();
                slot.setStart(cursor);
                slot.setEnd(slotEnd);
                slot.setLabel(cursor.format(LABEL_TIME) + "–" + slotEnd.format(LABEL_TIME));
                group.getSlots().add(slot);
                cursor = cursor.plusMinutes(SLOT_STEP_MIN);
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

    private static List<DingTalkAssistantSuggestVO.FreeWindow> findFreeWindows(List<DingTalkBusySlotVO> slots, int durationMin) {
        List<DingTalkAssistantSuggestVO.FreeWindow> result = new ArrayList<>();
        if (slots == null || slots.isEmpty()) {
            return result;
        }
        LocalDateTime runStart = null;
        LocalDateTime runEnd = null;
        for (DingTalkBusySlotVO slot : slots) {
            if (slot == null || slot.getStart() == null || slot.getEnd() == null) {
                continue;
            }
            boolean free = "FREE".equalsIgnoreCase(slot.getStatus());
            if (free) {
                if (runStart == null) {
                    runStart = slot.getStart();
                    runEnd = slot.getEnd();
                } else if (runEnd != null && runEnd.equals(slot.getStart())) {
                    runEnd = slot.getEnd();
                } else {
                    addIfLongEnough(result, runStart, runEnd, durationMin);
                    runStart = slot.getStart();
                    runEnd = slot.getEnd();
                }
            } else if (runStart != null) {
                addIfLongEnough(result, runStart, runEnd, durationMin);
                runStart = null;
                runEnd = null;
            }
            if (result.size() >= MAX_WINDOWS) {
                return result;
            }
        }
        if (runStart != null) {
            addIfLongEnough(result, runStart, runEnd, durationMin);
        }
        return result;
    }

    private static void addIfLongEnough(List<DingTalkAssistantSuggestVO.FreeWindow> out,
                                        LocalDateTime start, LocalDateTime end, int durationMin) {
        if (start == null || end == null || !start.isBefore(end)) {
            return;
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < durationMin || out.size() >= MAX_WINDOWS) {
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

    private static String buildAdvice(String name, int durationMin, List<DingTalkAssistantSuggestVO.DayGroup> dayGroups) {
        int slotCount = dayGroups == null ? 0 : dayGroups.stream().mapToInt(d -> d.getSlots() == null ? 0 : d.getSlots().size()).sum();
        if (slotCount == 0) {
            return name + " 在所选范围内没有可按 " + durationMin + " 分钟切分的空闲时段。可以扩大时间范围，或改短期望时长后再问。";
        }
        return "根据钉钉日程，" + name + " 共有 " + dayGroups.size() + " 天、" + slotCount
                + " 个可约时段（每段 " + durationMin + " 分钟）。请按天切换查看，选中后再发起面试邀约、邀请开会或安排工作汇报。";
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
