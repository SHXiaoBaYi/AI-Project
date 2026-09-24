package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkBusyQueryDTO;
import com.base.admin.domain.dto.DingTalkScheduleRecommendDTO;
import com.base.admin.domain.dto.DingTalkScheduleRuleDTO;
import com.base.admin.domain.vo.DingTalkBusySlotVO;
import com.base.admin.domain.vo.DingTalkBusyUserVO;
import com.base.admin.domain.vo.DingTalkScheduleRecommendVO;
import com.base.admin.domain.vo.DingTalkScheduleRuleVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.ChinaHoliday;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 日程规则配置（仅本人可读写）。
 * 秘书机器人读取规则 + 钉钉闲忙 → 智能动作卡片推荐；
 * 动作「面试邀约」强制简历并带到日程。
 */
@Service
@RequiredArgsConstructor
public class DingTalkScheduleRuleService {

    public static final String ACTION_INTERVIEW = "interview";
    public static final String ACTION_MEETING = "meeting";
    public static final String ACTION_REPORT = "report";

    private static final Map<String, String> ACTION_LABELS = Map.of(
            ACTION_INTERVIEW, "面试邀约",
            ACTION_MEETING, "会议",
            ACTION_REPORT, "工作汇报");
    private static final List<String> ALL_ACTIONS = List.of(ACTION_INTERVIEW, ACTION_MEETING, ACTION_REPORT);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SLOT_LABEL = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 30);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 30);
    private static final int SLOT_STEP = 30;

    private static final TypeReference<List<DingTalkScheduleRuleVO.Window>> WINDOWS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DingTalkScheduleRuleVO.ActionPref>> ACTION_PREFS_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> ACTIONS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final DingTalkBusyService dingTalkBusyService;

    public DingTalkScheduleRuleVO getMine() {
        return loadOrDefault(requireCurrentUserId());
    }

    public DingTalkScheduleRuleVO loadOrDefault(Long userId) {
        if (userId == null) {
            return defaults(null);
        }
        DingTalkScheduleRuleVO stored = jdbc.query("""
                SELECT user_id, enabled, deny_holidays, buffer_min, look_ahead_days, recommend_limit, secretary_enabled,
                       windows_json, actions_json, action_prefs_json, blocked_windows_json, robot_hint,
                       prefer_duration_min, max_duration_min
                FROM sys_dingtalk_schedule_rule
                WHERE user_id = ? AND is_active = 1
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            DingTalkScheduleRuleVO vo = new DingTalkScheduleRuleVO();
            vo.setUserId(rs.getLong("user_id"));
            vo.setEnabled(rs.getInt("enabled") == 1);
            vo.setDenyHolidays(rs.getInt("deny_holidays") == 1);
            vo.setBufferMin(Math.max(0, rs.getInt("buffer_min")));
            int look = rs.getInt("look_ahead_days");
            vo.setLookAheadDays(look <= 0 ? ChinaHoliday.BOOKING_MAX_DAYS : Math.min(look, ChinaHoliday.BOOKING_MAX_DAYS));
            int limit = rs.getInt("recommend_limit");
            vo.setRecommendLimit(limit <= 0 ? 8 : Math.min(limit, 30));
            vo.setSecretaryEnabled(rs.getInt("secretary_enabled") == 1);
            vo.setRobotHint(rs.getString("robot_hint") == null ? "" : rs.getString("robot_hint"));
            vo.setBlockedWindows(parseWindows(rs.getString("blocked_windows_json")));
            List<DingTalkScheduleRuleVO.ActionPref> prefs = parseActionPrefs(rs.getString("action_prefs_json"));
            if (prefs.isEmpty()) {
                // 兼容旧数据：全局 windows + actions
                prefs = migrateLegacyPrefs(
                        parseWindows(rs.getString("windows_json")),
                        parseLegacyActions(rs.getString("actions_json")),
                        rs.getInt("prefer_duration_min"),
                        rs.getObject("max_duration_min") == null ? null : rs.getInt("max_duration_min"));
            }
            vo.setActionPrefs(normalizeActionPrefs(prefs));
            return vo;
        }, userId);
        return stored == null ? defaults(userId) : stored;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveMine(DingTalkScheduleRuleDTO dto) {
        Long userId = requireCurrentUserId();
        String username = SecurityUtils.getCurrentUsername();
        boolean enabled = Boolean.TRUE.equals(dto.getEnabled());
        boolean denyHolidays = dto.getDenyHolidays() == null || Boolean.TRUE.equals(dto.getDenyHolidays());
        boolean secretaryEnabled = dto.getSecretaryEnabled() == null || Boolean.TRUE.equals(dto.getSecretaryEnabled());
        int buffer = dto.getBufferMin() == null ? 0 : dto.getBufferMin();
        if (buffer < 0 || buffer > 240) {
            throw new BusinessException("场次间隔须在 0～240 分钟");
        }
        int lookAhead = dto.getLookAheadDays() == null ? ChinaHoliday.BOOKING_MAX_DAYS : dto.getLookAheadDays();
        if (lookAhead < 1 || lookAhead > ChinaHoliday.BOOKING_MAX_DAYS) {
            throw new BusinessException("推荐天数须在 1～" + ChinaHoliday.BOOKING_MAX_DAYS + " 天");
        }
        int recommendLimit = dto.getRecommendLimit() == null ? 8 : dto.getRecommendLimit();
        if (recommendLimit < 1 || recommendLimit > 30) {
            throw new BusinessException("推荐条数须在 1～30");
        }
        List<DingTalkScheduleRuleVO.Window> blocked = normalizeWindows(dto.getBlockedWindows(), "不安排时段");
        List<DingTalkScheduleRuleVO.ActionPref> prefs = normalizeActionPrefsFromDto(dto.getActionPrefs());
        boolean anyAction = prefs.stream().anyMatch(DingTalkScheduleRuleVO.ActionPref::isEnabled);
        if (!anyAction) {
            throw new BusinessException("请至少启用一种动作卡片");
        }
        if (enabled) {
            for (DingTalkScheduleRuleVO.ActionPref p : prefs) {
                if (p.isEnabled() && (p.getWindows() == null || p.getWindows().isEmpty())) {
                    throw new BusinessException(p.getActionLabel() + "：启用后请至少配置一个喜好时段");
                }
            }
        }
        String hint = dto.getRobotHint() == null ? "" : dto.getRobotHint().trim();
        if (hint.length() > 500) {
            throw new BusinessException("机器人提示最多 500 字");
        }
        String actionPrefsJson = writeJson(prefs);
        String blockedJson = writeJson(blocked);
        // 兼容旧列：汇总启用动作与合并 windows
        List<String> enabledActions = prefs.stream().filter(DingTalkScheduleRuleVO.ActionPref::isEnabled)
                .map(DingTalkScheduleRuleVO.ActionPref::getAction).toList();
        List<DingTalkScheduleRuleVO.Window> flatWindows = prefs.stream()
                .filter(DingTalkScheduleRuleVO.ActionPref::isEnabled)
                .flatMap(p -> p.getWindows().stream())
                .toList();

        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_dingtalk_schedule_rule WHERE user_id = ?", Integer.class, userId);
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE sys_dingtalk_schedule_rule
                    SET enabled = ?, deny_holidays = ?, buffer_min = ?, look_ahead_days = ?, recommend_limit = ?,
                        secretary_enabled = ?, action_prefs_json = ?, blocked_windows_json = ?,
                        windows_json = ?, actions_json = ?, robot_hint = ?, update_by = ?, is_active = 1
                    WHERE user_id = ?
                    """,
                    enabled ? 1 : 0, denyHolidays ? 1 : 0, buffer, lookAhead, recommendLimit,
                    secretaryEnabled ? 1 : 0, actionPrefsJson, blockedJson,
                    writeJson(flatWindows), writeJson(enabledActions), hint, username, userId);
            return;
        }
        jdbc.update("""
                INSERT INTO sys_dingtalk_schedule_rule
                  (user_id, enabled, deny_holidays, buffer_min, look_ahead_days, recommend_limit, secretary_enabled,
                   action_prefs_json, blocked_windows_json, windows_json, actions_json, robot_hint, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """,
                userId, enabled ? 1 : 0, denyHolidays ? 1 : 0, buffer, lookAhead, recommendLimit,
                secretaryEnabled ? 1 : 0, actionPrefsJson, blockedJson,
                writeJson(flatWindows), writeJson(enabledActions), hint, username);
    }

    /** 秘书机器人：闲忙 + 规则 → 某动作的推荐时段 */
    public DingTalkScheduleRecommendVO recommendMine(DingTalkScheduleRecommendDTO dto) {
        return recommendForUser(requireCurrentUserId(), dto);
    }

    public DingTalkScheduleRecommendVO recommendForUser(Long userId, DingTalkScheduleRecommendDTO dto) {
        if (userId == null) {
            throw new BusinessException("用户不存在");
        }
        DingTalkScheduleRuleVO rule = loadOrDefault(userId);
        if (!rule.isSecretaryEnabled()) {
            throw new BusinessException(hintOr(rule, "对方未开启秘书自动推荐"));
        }
        String action = normalizeAction(dto == null || !StringUtils.hasText(dto.getAction())
                ? ACTION_INTERVIEW : dto.getAction());
        DingTalkScheduleRuleVO.ActionPref pref = findAction(rule, action);
        if (pref == null || !pref.isEnabled()) {
            throw new BusinessException(hintOr(rule, "对方未开放该动作卡片"));
        }
        int duration = dto != null && dto.getDurationMin() != null ? dto.getDurationMin() : pref.getPreferDurationMin();
        if (duration < 15) {
            throw new BusinessException("时长至少 15 分钟");
        }
        if (pref.getMaxDurationMin() != null && duration > pref.getMaxDurationMin()) {
            throw new BusinessException("时长不能超过 " + pref.getMaxDurationMin() + " 分钟");
        }

        LocalDate today = LocalDate.now();
        LocalDate endDay = today.plusDays(Math.min(rule.getLookAheadDays(), ChinaHoliday.BOOKING_MAX_DAYS));
        LocalDateTime rangeStart = LocalDateTime.now().withSecond(0).withNano(0);
        if (rangeStart.toLocalTime().isBefore(DEFAULT_START)) {
            rangeStart = today.atTime(DEFAULT_START);
        }
        LocalDateTime rangeEnd = endDay.atTime(DEFAULT_END);

        DingTalkBusyQueryDTO busyQuery = new DingTalkBusyQueryDTO();
        busyQuery.setUserIds(List.of(userId));
        busyQuery.setStartTime(rangeStart);
        busyQuery.setEndTime(rangeEnd);
        List<DingTalkBusyUserVO> busyRows = dingTalkBusyService.query(busyQuery);
        DingTalkBusyUserVO busy = busyRows.isEmpty() ? null : busyRows.getFirst();

        DingTalkScheduleRecommendVO vo = new DingTalkScheduleRecommendVO();
        vo.setUserId(userId);
        vo.setAction(action);
        vo.setDurationMin(duration);
        if (busy == null) {
            vo.setMessage("无法查询闲忙");
            return vo;
        }
        vo.setDisplayName(StringUtils.hasText(busy.getNickname()) ? busy.getNickname() : busy.getUsername());
        if (StringUtils.hasText(busy.getError())) {
            vo.setMessage(busy.getError());
            return vo;
        }

        List<LocalDateTime[]> freeWindows = mergeFreeWindows(busy.getSlots(), rule.getBufferMin());
        List<DingTalkScheduleRecommendVO.Slot> slots = new ArrayList<>();
        for (LocalDateTime[] win : freeWindows) {
            collectSlots(slots, win[0], win[1], duration, rule, pref);
            if (slots.size() >= rule.getRecommendLimit()) {
                break;
            }
        }
        slots.sort(Comparator.comparing(DingTalkScheduleRecommendVO.Slot::getStart));
        if (slots.size() > rule.getRecommendLimit()) {
            slots = new ArrayList<>(slots.subList(0, rule.getRecommendLimit()));
        }
        vo.setSlots(slots);
        vo.setMessage(slots.isEmpty()
                ? "未来 " + rule.getLookAheadDays() + " 天内暂无可推荐空档"
                : "已按钉钉闲忙与「" + pref.getActionLabel() + "」偏好推荐 " + slots.size() + " 个时段");
        return vo;
    }

    /**
     * @param hasResume 仅 interview 必填；会议/汇报可为 false
     */
    public void assertBookable(Long targetUserId, String action, LocalDateTime start, int durationMin, boolean hasResume) {
        String act = normalizeAction(action);
        if (start == null) {
            throw new BusinessException("请选择开始时间");
        }
        if (durationMin <= 0) {
            throw new BusinessException("时长须大于 0 分钟");
        }
        LocalDateTime end = start.plusMinutes(durationMin);
        DingTalkScheduleRuleVO rule = loadOrDefault(targetUserId);
        DingTalkScheduleRuleVO.ActionPref pref = findAction(rule, act);
        if (pref == null || !pref.isEnabled()) {
            throw new BusinessException(hintOr(rule, "对方未开放该动作"));
        }
        if (ACTION_INTERVIEW.equals(act) && !hasResume) {
            throw new BusinessException("面试邀约必须附带简历，并带到钉钉日程");
        }
        if (pref.getMaxDurationMin() != null && durationMin > pref.getMaxDurationMin()) {
            throw new BusinessException("时长不能超过 " + pref.getMaxDurationMin() + " 分钟");
        }
        if (rule.isDenyHolidays() && ChinaHoliday.isOffDay(start.toLocalDate())) {
            throw new BusinessException("不能选择国家法定节假日");
        }
        try {
            ChinaHoliday.requireBookableStart(start);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        if (overlapsWindows(rule.getBlockedWindows(), start, end)) {
            throw new BusinessException(hintOr(rule, "该时段对方设置为不安排任何日程"));
        }
        if (!rule.isEnabled()) {
            return;
        }
        if (!withinWindows(pref.getWindows(), start, end)) {
            throw new BusinessException(hintOr(rule, "所选时间不在对方「" + pref.getActionLabel() + "」喜好时段内"));
        }
    }

    public static boolean isInterviewAction(String action) {
        return ACTION_INTERVIEW.equals(normalizeAction(action));
    }

    private void collectSlots(List<DingTalkScheduleRecommendVO.Slot> out, LocalDateTime freeStart, LocalDateTime freeEnd,
                              int durationMin, DingTalkScheduleRuleVO rule, DingTalkScheduleRuleVO.ActionPref pref) {
        LocalDateTime cursor = ceilToStep(freeStart);
        while (cursor != null && !cursor.plusMinutes(durationMin).isAfter(freeEnd)) {
            LocalDateTime start = cursor;
            LocalDateTime end = start.plusMinutes(durationMin);
            boolean dup = false;
            for (DingTalkScheduleRecommendVO.Slot s : out) {
                if (s.getStart() != null && s.getStart().equals(start)) {
                    dup = true;
                    break;
                }
            }
            if (!dup && fitsPreference(start, end, rule, pref)) {
                DingTalkScheduleRecommendVO.Slot slot = new DingTalkScheduleRecommendVO.Slot();
                slot.setStart(start);
                slot.setEnd(end);
                slot.setLabel(start.format(SLOT_LABEL) + "～" + end.format(HM) + "（" + durationMin + "分钟）");
                out.add(slot);
                if (out.size() >= rule.getRecommendLimit()) {
                    return;
                }
            }
            cursor = cursor.plusMinutes(SLOT_STEP);
            if (!cursor.toLocalTime().isBefore(DEFAULT_END)) {
                cursor = cursor.toLocalDate().plusDays(1).atTime(DEFAULT_START);
            }
        }
    }

    private boolean fitsPreference(LocalDateTime start, LocalDateTime end, DingTalkScheduleRuleVO rule,
                                   DingTalkScheduleRuleVO.ActionPref pref) {
        if (rule.isDenyHolidays() && ChinaHoliday.isOffDay(start.toLocalDate())) {
            return false;
        }
        try {
            ChinaHoliday.requireBookableStart(start);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (overlapsWindows(rule.getBlockedWindows(), start, end)) {
            return false;
        }
        if (!rule.isEnabled()) {
            LocalTime st = start.toLocalTime();
            LocalTime et = end.toLocalTime();
            return !st.isBefore(DEFAULT_START) && !et.isAfter(DEFAULT_END)
                    && start.toLocalDate().equals(end.toLocalDate());
        }
        return withinWindows(pref.getWindows(), start, end);
    }

    private static List<LocalDateTime[]> mergeFreeWindows(List<DingTalkBusySlotVO> slots, int bufferMin) {
        List<LocalDateTime[]> result = new ArrayList<>();
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
            boolean free = "FREE".equalsIgnoreCase(slot.getStatus());
            LocalDateTime start = slot.getStart();
            LocalDateTime end = slot.getEnd();
            if (!free) {
                if (runStart != null) {
                    result.add(new LocalDateTime[]{runStart, runEnd});
                    runStart = null;
                    runEnd = null;
                }
                lastBusyEnd = end.plusMinutes(Math.max(0, bufferMin));
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
            } else if (runEnd != null && (runEnd.equals(start) || runEnd.isAfter(start))
                    && runStart.toLocalDate().equals(start.toLocalDate())) {
                if (end.isAfter(runEnd)) {
                    runEnd = end;
                }
            } else {
                result.add(new LocalDateTime[]{runStart, runEnd});
                runStart = start;
                runEnd = end;
            }
        }
        if (runStart != null) {
            result.add(new LocalDateTime[]{runStart, runEnd});
        }
        return result;
    }

    private static LocalDateTime ceilToStep(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        LocalDateTime t = time.withSecond(0).withNano(0);
        int rem = t.getMinute() % SLOT_STEP;
        if (rem != 0) {
            t = t.plusMinutes(SLOT_STEP - rem);
        }
        return t;
    }

    private static boolean withinWindows(List<DingTalkScheduleRuleVO.Window> windows, LocalDateTime start, LocalDateTime end) {
        if (windows == null || windows.isEmpty() || !start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        int dow = start.getDayOfWeek().getValue();
        LocalTime st = start.toLocalTime();
        LocalTime et = end.toLocalTime();
        for (DingTalkScheduleRuleVO.Window w : windows) {
            if (w.getWeekdays() == null || !w.getWeekdays().contains(dow)) {
                continue;
            }
            LocalTime ws = parseHm(w.getStartTime());
            LocalTime we = parseHm(w.getEndTime());
            if (ws == null || we == null) {
                continue;
            }
            if (!st.isBefore(ws) && !et.isAfter(we)) {
                return true;
            }
        }
        return false;
    }

    /** 与窗口有交集即视为落在「不安排」内 */
    private static boolean overlapsWindows(List<DingTalkScheduleRuleVO.Window> windows, LocalDateTime start, LocalDateTime end) {
        if (windows == null || windows.isEmpty() || !start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        int dow = start.getDayOfWeek().getValue();
        LocalTime st = start.toLocalTime();
        LocalTime et = end.toLocalTime();
        for (DingTalkScheduleRuleVO.Window w : windows) {
            if (w.getWeekdays() == null || !w.getWeekdays().contains(dow)) {
                continue;
            }
            LocalTime ws = parseHm(w.getStartTime());
            LocalTime we = parseHm(w.getEndTime());
            if (ws == null || we == null) {
                continue;
            }
            if (st.isBefore(we) && et.isAfter(ws)) {
                return true;
            }
        }
        return false;
    }

    private static LocalTime parseHm(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim(), HM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private List<DingTalkScheduleRuleVO.Window> normalizeWindows(List<DingTalkScheduleRuleDTO.Window> raw, String label) {
        List<DingTalkScheduleRuleVO.Window> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (DingTalkScheduleRuleDTO.Window w : raw) {
            if (w == null) {
                continue;
            }
            LocalTime start = parseHm(w.getStartTime());
            LocalTime end = parseHm(w.getEndTime());
            if (start == null || end == null) {
                throw new BusinessException(label + "须为 HH:mm 格式");
            }
            if (!end.isAfter(start)) {
                throw new BusinessException(label + "结束须晚于开始");
            }
            Set<Integer> days = new LinkedHashSet<>();
            if (w.getWeekdays() != null) {
                for (Integer d : w.getWeekdays()) {
                    if (d != null && d >= 1 && d <= 7) {
                        days.add(d);
                    }
                }
            }
            if (days.isEmpty()) {
                throw new BusinessException(label + "请至少选择一个星期");
            }
            DingTalkScheduleRuleVO.Window vo = new DingTalkScheduleRuleVO.Window();
            vo.setWeekdays(new ArrayList<>(days));
            vo.setStartTime(start.format(HM));
            vo.setEndTime(end.format(HM));
            out.add(vo);
        }
        return out;
    }

    private List<DingTalkScheduleRuleVO.ActionPref> normalizeActionPrefsFromDto(List<DingTalkScheduleRuleDTO.ActionPref> raw) {
        Map<String, DingTalkScheduleRuleVO.ActionPref> map = new LinkedHashMap<>();
        for (String a : ALL_ACTIONS) {
            map.put(a, defaultActionPref(a));
        }
        if (raw != null) {
            for (DingTalkScheduleRuleDTO.ActionPref p : raw) {
                if (p == null) {
                    continue;
                }
                String act = normalizeAction(p.getAction());
                if (!map.containsKey(act)) {
                    continue;
                }
                DingTalkScheduleRuleVO.ActionPref vo = map.get(act);
                vo.setEnabled(p.getEnabled() == null || Boolean.TRUE.equals(p.getEnabled()));
                int prefer = p.getPreferDurationMin() == null ? vo.getPreferDurationMin() : p.getPreferDurationMin();
                if (prefer < 15 || prefer > 480) {
                    throw new BusinessException(vo.getActionLabel() + "默认时长须在 15～480 分钟");
                }
                vo.setPreferDurationMin(prefer);
                Integer max = p.getMaxDurationMin();
                if (max != null && max <= 0) {
                    throw new BusinessException(vo.getActionLabel() + "最长时长须大于 0");
                }
                if (max != null && prefer > max) {
                    throw new BusinessException(vo.getActionLabel() + "默认时长不能超过最长时长");
                }
                vo.setMaxDurationMin(max);
                vo.setWindows(normalizeWindows(p.getWindows(), vo.getActionLabel() + "喜好时段"));
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<DingTalkScheduleRuleVO.ActionPref> normalizeActionPrefs(List<DingTalkScheduleRuleVO.ActionPref> raw) {
        Map<String, DingTalkScheduleRuleVO.ActionPref> map = new LinkedHashMap<>();
        for (String a : ALL_ACTIONS) {
            map.put(a, defaultActionPref(a));
        }
        if (raw != null) {
            for (DingTalkScheduleRuleVO.ActionPref p : raw) {
                if (p == null) {
                    continue;
                }
                String act = normalizeAction(p.getAction());
                if (!map.containsKey(act)) {
                    continue;
                }
                DingTalkScheduleRuleVO.ActionPref base = map.get(act);
                base.setEnabled(p.isEnabled());
                base.setPreferDurationMin(Math.max(15, p.getPreferDurationMin()));
                base.setMaxDurationMin(p.getMaxDurationMin());
                base.setWindows(p.getWindows() == null ? new ArrayList<>() : p.getWindows());
                base.setActionLabel(ACTION_LABELS.get(act));
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<DingTalkScheduleRuleVO.ActionPref> migrateLegacyPrefs(
            List<DingTalkScheduleRuleVO.Window> windows, List<String> actions, int prefer, Integer max) {
        List<DingTalkScheduleRuleVO.ActionPref> list = new ArrayList<>();
        Set<String> enabled = new LinkedHashSet<>(actions == null || actions.isEmpty() ? ALL_ACTIONS : actions);
        for (String a : ALL_ACTIONS) {
            DingTalkScheduleRuleVO.ActionPref p = defaultActionPref(a);
            p.setEnabled(enabled.contains(a));
            p.setPreferDurationMin(prefer <= 0 ? 60 : prefer);
            p.setMaxDurationMin(max);
            p.setWindows(windows == null ? defaultWindows() : new ArrayList<>(windows));
            list.add(p);
        }
        return list;
    }

    private static DingTalkScheduleRuleVO.ActionPref defaultActionPref(String action) {
        DingTalkScheduleRuleVO.ActionPref p = new DingTalkScheduleRuleVO.ActionPref();
        p.setAction(action);
        p.setActionLabel(ACTION_LABELS.getOrDefault(action, action));
        p.setEnabled(true);
        p.setPreferDurationMin(ACTION_INTERVIEW.equals(action) ? 60 : 30);
        p.setWindows(defaultWindows());
        return p;
    }

    private static DingTalkScheduleRuleVO.ActionPref findAction(DingTalkScheduleRuleVO rule, String action) {
        if (rule.getActionPrefs() == null) {
            return null;
        }
        for (DingTalkScheduleRuleVO.ActionPref p : rule.getActionPrefs()) {
            if (p != null && action.equals(normalizeAction(p.getAction()))) {
                return p;
            }
        }
        return null;
    }

    private static String hintOr(DingTalkScheduleRuleVO rule, String fallback) {
        return StringUtils.hasText(rule.getRobotHint()) ? rule.getRobotHint() : fallback;
    }

    private static String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        return action.trim().toLowerCase(Locale.ROOT);
    }

    private List<DingTalkScheduleRuleVO.Window> parseWindows(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<DingTalkScheduleRuleVO.Window> list = objectMapper.readValue(json, WINDOWS_TYPE);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<DingTalkScheduleRuleVO.ActionPref> parseActionPrefs(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<DingTalkScheduleRuleVO.ActionPref> list = objectMapper.readValue(json, ACTION_PREFS_TYPE);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<String> parseLegacyActions(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>(ALL_ACTIONS);
        }
        try {
            List<String> list = objectMapper.readValue(json, ACTIONS_TYPE);
            return list == null || list.isEmpty() ? new ArrayList<>(ALL_ACTIONS) : list;
        } catch (Exception e) {
            return new ArrayList<>(ALL_ACTIONS);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("规则序列化失败");
        }
    }

    private DingTalkScheduleRuleVO defaults(Long userId) {
        DingTalkScheduleRuleVO vo = new DingTalkScheduleRuleVO();
        vo.setUserId(userId);
        vo.setEnabled(true);
        vo.setDenyHolidays(true);
        vo.setBufferMin(30);
        vo.setLookAheadDays(ChinaHoliday.BOOKING_MAX_DAYS);
        vo.setRecommendLimit(8);
        vo.setSecretaryEnabled(true);
        vo.setRobotHint("");
        vo.setBlockedWindows(new ArrayList<>());
        vo.setActionPrefs(normalizeActionPrefs(null));
        return vo;
    }

    private static List<DingTalkScheduleRuleVO.Window> defaultWindows() {
        DingTalkScheduleRuleVO.Window w = new DingTalkScheduleRuleVO.Window();
        w.setWeekdays(List.of(1, 2, 3, 4, 5));
        w.setStartTime("09:30");
        w.setEndTime("18:30");
        return new ArrayList<>(List.of(w));
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }
}
