package com.base.admin.service;

import com.base.admin.domain.dto.DingTalkBusyQueryDTO;
import com.base.admin.domain.vo.DingTalkBusySlotVO;
import com.base.admin.domain.vo.DingTalkBusyUserOptionVO;
import com.base.admin.domain.vo.DingTalkBusyUserVO;
import com.base.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DingTalkBusyService {

    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private final JdbcTemplate jdbc;
    private final DingTalkCalendarClient dingTalk;

    public List<DingTalkBusyUserOptionVO> listBoundUsers() {
        return jdbc.query("""
                SELECT u.user_id, u.username, u.nickname
                FROM sys_user u
                INNER JOIN hr_user_dingtalk d ON d.user_id = u.user_id AND d.is_active = 1
                WHERE u.is_active = 1 AND u.status = 0 AND d.dingtalk_union_id IS NOT NULL AND d.dingtalk_union_id <> ''
                ORDER BY u.nickname, u.username
                """, (rs, rowNum) -> {
            DingTalkBusyUserOptionVO vo = new DingTalkBusyUserOptionVO();
            vo.setUserId(rs.getLong("user_id"));
            vo.setUsername(rs.getString("username"));
            vo.setNickname(rs.getString("nickname"));
            return vo;
        });
    }

    public List<DingTalkBusyUserVO> query(DingTalkBusyQueryDTO dto) {
        if (!dingTalk.configured()) {
            throw new BusinessException("钉钉应用还没配置或未启用，请到「系统管理 → 钉钉应用配置」填写");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        if (Duration.between(dto.getStartTime(), dto.getEndTime()).compareTo(MAX_RANGE) > 0) {
            throw new BusinessException("查询范围不能超过 31 天");
        }
        List<Long> userIds = dto.getUserIds().stream().filter(id -> id != null).distinct().toList();
        if (userIds.isEmpty()) {
            throw new BusinessException("请选择用户");
        }
        if (userIds.size() > 20) {
            throw new BusinessException("一次最多查询 20 个用户");
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
            DingTalkCalendarClient.BusySchedule schedule = dingTalk.queryBusy(user.unionId(), dto.getStartTime(), dto.getEndTime());
            vo.setError(schedule.error());
            if (!StringUtils.hasText(schedule.error())) {
                vo.setSlots(buildSlots(dto.getStartTime(), dto.getEndTime(), schedule.items()));
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
                                                       List<DingTalkCalendarClient.BusyItem> items) {
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
        clipped.sort(Comparator.comparing(DingTalkCalendarClient.BusyItem::start));
        List<DingTalkBusySlotVO> slots = new ArrayList<>();
        LocalDateTime cursor = rangeStart;
        for (DingTalkCalendarClient.BusyItem item : merge(clipped)) {
            if (cursor.isBefore(item.start())) {
                slots.add(slot("FREE", cursor, item.start()));
            }
            if (cursor.isBefore(item.end())) {
                cursor = item.end();
            }
        }
        if (cursor.isBefore(rangeEnd)) {
            slots.add(slot("FREE", cursor, rangeEnd));
        }
        for (DingTalkCalendarClient.BusyItem item : clipped) {
            slots.add(slot(normalizeStatus(item.status()), item.start(), item.end()));
        }
        slots.sort(Comparator.comparing(DingTalkBusySlotVO::getStart).thenComparing(DingTalkBusySlotVO::getStatus));
        return slots;
    }

    private static List<DingTalkCalendarClient.BusyItem> merge(List<DingTalkCalendarClient.BusyItem> items) {
        List<DingTalkCalendarClient.BusyItem> merged = new ArrayList<>();
        for (DingTalkCalendarClient.BusyItem item : items) {
            if (merged.isEmpty()) {
                merged.add(item);
                continue;
            }
            DingTalkCalendarClient.BusyItem last = merged.get(merged.size() - 1);
            if (!item.start().isAfter(last.end())) {
                LocalDateTime end = item.end().isAfter(last.end()) ? item.end() : last.end();
                merged.set(merged.size() - 1, new DingTalkCalendarClient.BusyItem(last.status(), last.start(), end));
            } else {
                merged.add(item);
            }
        }
        return merged;
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
