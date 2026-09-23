package com.base.admin.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 国务院办公厅公布的放假调休「休息日」（不含调休上班日）。
 * 依据：国办发明电〔2024〕12号（2025）、国办发明电〔2025〕7号（2026）。
 */
public final class ChinaHoliday {

    /** 可查询 / 可预约：今天起未来天数（含今天） */
    public static final int BOOKING_MAX_DAYS = 14;

    private static final Set<LocalDate> OFF_DAYS = Set.of(
            LocalDate.parse("2025-01-01"),
            LocalDate.parse("2025-01-28"), LocalDate.parse("2025-01-29"), LocalDate.parse("2025-01-30"),
            LocalDate.parse("2025-01-31"), LocalDate.parse("2025-02-01"), LocalDate.parse("2025-02-02"),
            LocalDate.parse("2025-02-03"), LocalDate.parse("2025-02-04"),
            LocalDate.parse("2025-04-04"), LocalDate.parse("2025-04-05"), LocalDate.parse("2025-04-06"),
            LocalDate.parse("2025-05-01"), LocalDate.parse("2025-05-02"), LocalDate.parse("2025-05-03"),
            LocalDate.parse("2025-05-04"), LocalDate.parse("2025-05-05"),
            LocalDate.parse("2025-05-31"), LocalDate.parse("2025-06-01"), LocalDate.parse("2025-06-02"),
            LocalDate.parse("2025-10-01"), LocalDate.parse("2025-10-02"), LocalDate.parse("2025-10-03"),
            LocalDate.parse("2025-10-04"), LocalDate.parse("2025-10-05"), LocalDate.parse("2025-10-06"),
            LocalDate.parse("2025-10-07"), LocalDate.parse("2025-10-08"),
            LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), LocalDate.parse("2026-01-03"),
            LocalDate.parse("2026-02-15"), LocalDate.parse("2026-02-16"), LocalDate.parse("2026-02-17"),
            LocalDate.parse("2026-02-18"), LocalDate.parse("2026-02-19"), LocalDate.parse("2026-02-20"),
            LocalDate.parse("2026-02-21"), LocalDate.parse("2026-02-22"), LocalDate.parse("2026-02-23"),
            LocalDate.parse("2026-04-04"), LocalDate.parse("2026-04-05"), LocalDate.parse("2026-04-06"),
            LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-02"), LocalDate.parse("2026-05-03"),
            LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-05"),
            LocalDate.parse("2026-06-19"), LocalDate.parse("2026-06-20"), LocalDate.parse("2026-06-21"),
            LocalDate.parse("2026-09-25"), LocalDate.parse("2026-09-26"), LocalDate.parse("2026-09-27"),
            LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-03"),
            LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06"),
            LocalDate.parse("2026-10-07")
    );

    private ChinaHoliday() {
    }

    public static boolean isOffDay(LocalDate date) {
        return date != null && OFF_DAYS.contains(date);
    }

    public static boolean isOffDay(LocalDateTime dateTime) {
        return dateTime != null && isOffDay(dateTime.toLocalDate());
    }

    public static LocalDate maxBookableDate(LocalDate today) {
        return today.plusDays(BOOKING_MAX_DAYS);
    }
}
