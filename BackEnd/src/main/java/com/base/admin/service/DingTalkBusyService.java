package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkBusyQueryDTO;
import com.base.admin.domain.vo.DingTalkBusySlotVO;
import com.base.admin.domain.vo.DingTalkBusyUserOptionVO;
import com.base.admin.domain.vo.DingTalkBusyUserVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.ChinaHoliday;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DingTalkBusyService {

    private static final int SLOT_MINUTES = 30;
    /** 工作时段：仅统计每天 09:30～18:30 */
    private static final LocalTime WORK_START = LocalTime.of(9, 30);
    private static final LocalTime WORK_END = LocalTime.of(18, 30);

    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalk;
    private final HrMasterService hrMasterService;

    public List<DingTalkBusyUserOptionVO> listBoundUsers() {
        return jdbc.query("""
                SELECT u.user_id, u.username, u.nickname,
                       CASE WHEN d.user_id IS NULL OR d.dingtalk_union_id IS NULL OR d.dingtalk_union_id = '' THEN 0 ELSE 1 END dingtalk_bound
                FROM sys_user u
                LEFT JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                WHERE u.is_active = 1 AND u.status = 0
                ORDER BY u.nickname, u.username
                """, (rs, rowNum) -> {
            DingTalkBusyUserOptionVO vo = new DingTalkBusyUserOptionVO();
            vo.setUserId(rs.getLong("user_id"));
            vo.setUsername(rs.getString("username"));
            vo.setNickname(rs.getString("nickname"));
            vo.setDingtalkBound(rs.getInt("dingtalk_bound"));
            return vo;
        });
    }

    public List<DingTalkBusyUserVO> query(DingTalkBusyQueryDTO dto) {
        return query(dto, WORK_START, WORK_END);
    }

    /**
     * 按指定工作时段切分闲忙格（日程助手按被问询人规则传入其 workStart/workEnd）。
     */
    public List<DingTalkBusyUserVO> query(DingTalkBusyQueryDTO dto, LocalTime workStart, LocalTime workEnd) {
        if (!dingTalk.configured()) {
            throw new BusinessException("钉钉应用还没配置或未启用，请到「系统管理 → 钉钉应用配置」填写");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        LocalTime dayStart = workStart == null ? WORK_START : workStart;
        LocalTime dayEnd = workEnd == null ? WORK_END : workEnd;
        if (!dayStart.isBefore(dayEnd)) {
            throw new BusinessException("工作时间结束须晚于开始");
        }
        LocalDate today = LocalDate.now();
        LocalDate maxDay = ChinaHoliday.maxBookableDate(today);
        LocalDateTime earliest = today.atTime(dayStart);
        LocalDateTime latest = maxDay.atTime(dayEnd);
        if (dto.getStartTime().toLocalDate().isBefore(today) || dto.getEndTime().toLocalDate().isBefore(today)) {
            throw new BusinessException("不能查询已经过去的日期");
        }
        if (dto.getStartTime().toLocalDate().isAfter(maxDay) || dto.getEndTime().toLocalDate().isAfter(maxDay)) {
            throw new BusinessException("最多只能查看未来 " + ChinaHoliday.BOOKING_MAX_DAYS + " 天内的闲忙");
        }
        // 法定节假日不生成时段（范围可跨过假日）；工作时段由入参决定
        LocalDateTime rangeStart = alignToWorkStart(
                dto.getStartTime().isBefore(earliest) ? earliest : dto.getStartTime(), dayStart, dayEnd);
        LocalDateTime rangeEnd = alignToWorkEnd(
                dto.getEndTime().isAfter(latest) ? latest : dto.getEndTime(), dayStart, dayEnd);
        if (!rangeStart.isBefore(rangeEnd)) {
            throw new BusinessException("所选范围不包含工作时段（每天 "
                    + dayStart + "～" + dayEnd + "）");
        }
        List<Long> userIds = dto.getUserIds().stream().filter(id -> id != null).distinct().toList();
        if (userIds.isEmpty()) {
            throw new BusinessException("请选择用户");
        }
        if (userIds.size() > 20) {
            throw new BusinessException("一次最多查询 20 个用户");
        }
        for (Long userId : userIds) {
            hrMasterService.refreshDingTalkBindingQuietly(userId);
        }
        Map<Long, BoundUser> bound = loadBound(userIds);
        List<DingTalkBusyUserVO> result = new ArrayList<>();
        for (Long userId : userIds) {
            BoundUser user = bound.get(userId);
            DingTalkBusyUserVO vo = new DingTalkBusyUserVO();
            vo.setUserId(userId);
            if (user == null) {
                vo.setNickname("未知用户");
                vo.setUsername("");
                vo.setError("该用户未绑定钉钉，无法查询闲忙");
                result.add(vo);
                continue;
            }
            vo.setUsername(user.username());
            vo.setNickname(StringUtils.hasText(user.nickname()) ? user.nickname() : user.username());
            DingTalkCalendarClient.BusySchedule schedule = dingTalk.queryBusy(user.unionId(), rangeStart, rangeEnd);
            vo.setError(schedule.error());
            if (!StringUtils.hasText(schedule.error())) {
                vo.setSlots(buildSlots(rangeStart, rangeEnd, schedule.items(), dayStart, dayEnd));
            }
            result.add(vo);
        }
        return result;
    }

    private Map<Long, BoundUser> loadBound(List<Long> userIds) {
        String placeholders = String.join(",", userIds.stream().map(id -> "?").toList());
        List<BoundUser> rows = jdbc.query("""
                SELECT u.user_id, u.username, u.nickname, d.dingtalk_union_id
                FROM sys_user u
                INNER JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                WHERE u.is_active = 1 AND u.user_id IN (%s)
                """.formatted(placeholders), (rs, rowNum) -> new BoundUser(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("dingtalk_union_id")), userIds.toArray());
        Map<Long, BoundUser> map = new LinkedHashMap<>();
        for (BoundUser row : rows) {
            if (StringUtils.hasText(row.unionId())) {
                map.put(row.userId(), row);
            }
        }
        return map;
    }

    private static List<DingTalkBusySlotVO> buildSlots(LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                                       List<DingTalkCalendarClient.BusyItem> items,
                                                       LocalTime workStart, LocalTime workEnd) {
        List<DingTalkCalendarClient.BusyItem> clipped = new ArrayList<>();
        if (items != null) {
            for (DingTalkCalendarClient.BusyItem item : items) {
                if (item.start() == null || item.end() == null) {
                    continue;
                }
                LocalDateTime start = item.start().isBefore(rangeStart) ? rangeStart : item.start();
                LocalDateTime end = item.end().isAfter(rangeEnd) ? rangeEnd : item.end();
                if (start.isBefore(end)) {
                    clipped.add(new DingTalkCalendarClient.BusyItem(item.status(), start, end));
                }
            }
        }
        List<DingTalkBusySlotVO> slots = new ArrayList<>();
        LocalDateTime cursor = alignToWorkStart(rangeStart, workStart, workEnd);
        while (cursor.isBefore(rangeEnd)) {
            if (ChinaHoliday.isOffDay(cursor.toLocalDate())) {
                cursor = cursor.toLocalDate().plusDays(1).atTime(workStart);
                continue;
            }
            if (cursor.toLocalTime().isBefore(workStart)) {
                cursor = cursor.toLocalDate().atTime(workStart);
                continue;
            }
            if (!cursor.toLocalTime().isBefore(workEnd)) {
                cursor = cursor.toLocalDate().plusDays(1).atTime(workStart);
                continue;
            }
            LocalDateTime dayWorkEnd = cursor.toLocalDate().atTime(workEnd);
            LocalDateTime boundary = nextHalfHour(cursor);
            LocalDateTime slotEnd = boundary;
            if (slotEnd.isAfter(dayWorkEnd)) {
                slotEnd = dayWorkEnd;
            }
            if (slotEnd.isAfter(rangeEnd)) {
                slotEnd = rangeEnd;
            }
            if (!cursor.isBefore(slotEnd)) {
                cursor = cursor.toLocalDate().plusDays(1).atTime(workStart);
                continue;
            }
            slots.add(slot(statusOf(cursor, slotEnd, clipped), cursor, slotEnd));
            cursor = slotEnd;
        }
        // 双保险：丢掉落在法定节假日上的时段
        slots.removeIf(s -> s.getStart() != null && ChinaHoliday.isOffDay(s.getStart().toLocalDate()));
        return slots;
    }

    /** 落到查询范围内的第一个工作时段起点（跳过法定节假日） */
    private static LocalDateTime alignToWorkStart(LocalDateTime time, LocalTime workStart, LocalTime workEnd) {
        LocalDateTime cursor = time;
        if (cursor.toLocalTime().isBefore(workStart)) {
            cursor = cursor.toLocalDate().atTime(workStart);
        } else if (!cursor.toLocalTime().isBefore(workEnd)) {
            cursor = cursor.toLocalDate().plusDays(1).atTime(workStart);
        }
        while (ChinaHoliday.isOffDay(cursor.toLocalDate())) {
            cursor = cursor.toLocalDate().plusDays(1).atTime(workStart);
        }
        return cursor;
    }

    /** 落到查询范围内的最后一个工作时段终点 */
    private static LocalDateTime alignToWorkEnd(LocalDateTime time, LocalTime workStart, LocalTime workEnd) {
        if (time.toLocalTime().isBefore(workStart)) {
            return time.toLocalDate().minusDays(1).atTime(workEnd);
        }
        if (time.toLocalTime().isAfter(workEnd)) {
            return time.toLocalDate().atTime(workEnd);
        }
        return time;
    }

    /** 对齐到下一个整点或半点。已经落在整点/半点上时，向后推 30 分钟。 */
    private static LocalDateTime nextHalfHour(LocalDateTime time) {
        LocalDateTime truncated = time.withSecond(0).withNano(0);
        boolean exact = truncated.equals(time) && truncated.getMinute() % SLOT_MINUTES == 0;
        if (exact) {
            return truncated.plusMinutes(SLOT_MINUTES);
        }
        int minute = truncated.getMinute();
        int remainder = minute % SLOT_MINUTES;
        if (remainder == 0) {
            return truncated.plusMinutes(SLOT_MINUTES);
        }
        return truncated.plusMinutes(SLOT_MINUTES - remainder);
    }

    /** 这一段里只要有忙，整段算忙；否则有暂定算暂定；都没有算闲。 */
    private static String statusOf(LocalDateTime start, LocalDateTime end, List<DingTalkCalendarClient.BusyItem> items) {
        boolean tentative = false;
        for (DingTalkCalendarClient.BusyItem item : items) {
            if (!item.start().isBefore(end) || !item.end().isAfter(start)) {
                continue;
            }
            if ("TENTATIVE".equals(normalizeStatus(item.status()))) {
                tentative = true;
            } else {
                return "BUSY";
            }
        }
        return tentative ? "TENTATIVE" : "FREE";
    }

    private static DingTalkBusySlotVO slot(String status, LocalDateTime start, LocalDateTime end) {
        DingTalkBusySlotVO vo = new DingTalkBusySlotVO();
        vo.setStatus(status);
        vo.setStatusLabel(label(status));
        vo.setStart(start);
        vo.setEnd(end);
        return vo;
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "BUSY";
        }
        String value = status.trim().toUpperCase();
        if ("TENTATIVE".equals(value) || "FREE".equals(value) || "BUSY".equals(value)) {
            return value;
        }
        return value;
    }

    private static String label(String status) {
        return switch (status) {
            case "FREE" -> "闲";
            case "TENTATIVE" -> "暂定";
            case "BUSY" -> "忙";
            default -> status;
        };
    }

    private record BoundUser(Long userId, String username, String nickname, String unionId) {
    }
}
