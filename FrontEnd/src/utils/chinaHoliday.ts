import dayjs, { type Dayjs } from 'dayjs';

/** 可查询 / 可预约的最长跨度：今天起未来 14 天（含今天） */
export const BOOKING_MAX_DAYS = 14;

/**
 * 国务院办公厅公布的放假调休「休息日」（不含调休上班日）。
 * 依据：国办发明电〔2024〕12号（2025）、国办发明电〔2025〕7号（2026）。
 */
const HOLIDAY_OFF_DAYS = new Set<string>([
  // 2025
  '2025-01-01',
  '2025-01-28',
  '2025-01-29',
  '2025-01-30',
  '2025-01-31',
  '2025-02-01',
  '2025-02-02',
  '2025-02-03',
  '2025-02-04',
  '2025-04-04',
  '2025-04-05',
  '2025-04-06',
  '2025-05-01',
  '2025-05-02',
  '2025-05-03',
  '2025-05-04',
  '2025-05-05',
  '2025-05-31',
  '2025-06-01',
  '2025-06-02',
  '2025-10-01',
  '2025-10-02',
  '2025-10-03',
  '2025-10-04',
  '2025-10-05',
  '2025-10-06',
  '2025-10-07',
  '2025-10-08',
  // 2026
  '2026-01-01',
  '2026-01-02',
  '2026-01-03',
  '2026-02-15',
  '2026-02-16',
  '2026-02-17',
  '2026-02-18',
  '2026-02-19',
  '2026-02-20',
  '2026-02-21',
  '2026-02-22',
  '2026-02-23',
  '2026-04-04',
  '2026-04-05',
  '2026-04-06',
  '2026-05-01',
  '2026-05-02',
  '2026-05-03',
  '2026-05-04',
  '2026-05-05',
  '2026-06-19',
  '2026-06-20',
  '2026-06-21',
  '2026-09-25',
  '2026-09-26',
  '2026-09-27',
  '2026-10-01',
  '2026-10-02',
  '2026-10-03',
  '2026-10-04',
  '2026-10-05',
  '2026-10-06',
  '2026-10-07',
]);

function dayKey(date: Dayjs | string | Date) {
  return dayjs(date).format('YYYY-MM-DD');
}

/** 是否为国家法定放假日（国务院放假调休中的休息日） */
export function isChinaHoliday(date: Dayjs | string | Date | null | undefined): boolean {
  if (date == null) return false;
  const d = dayjs(date);
  return d.isValid() && HOLIDAY_OFF_DAYS.has(dayKey(d));
}

/** 可预约/可查询窗口：今天 00:00 ～ 今天+14天 23:59:59 */
export function bookingWindow(now?: Dayjs | null) {
  const base = dayjs.isDayjs(now) && now.isValid() ? now : dayjs();
  const min = base.startOf('day');
  const max = base.add(BOOKING_MAX_DAYS, 'day').endOf('day');
  return { min, max };
}

/**
 * DatePicker / RangePicker 的 disabledDate。
 * 注意：antd 可能传入第二参数 info（选区信息），不能当成「当前时间」。
 */
export function disabledBookingDate(current: Dayjs | null): boolean {
  if (!current || !dayjs.isDayjs(current) || !current.isValid()) return true;
  const { min, max } = bookingWindow();
  if (current.isBefore(min, 'day') || current.isAfter(max, 'day')) return true;
  return isChinaHoliday(current);
}

/** 将日期落到可预约窗口内（保留时分） */
export function clampToBookingWindow(date: Dayjs, now?: Dayjs | null): Dayjs {
  const { min, max } = bookingWindow(now);
  if (date.isBefore(min)) return min.hour(date.hour()).minute(date.minute()).second(0);
  if (date.isAfter(max)) return max.startOf('day').hour(date.hour()).minute(date.minute()).second(0);
  return date;
}

/** 校验可预约开始时间；通过返回 null，否则返回错误文案 */
export function bookingDateTimeError(value: Dayjs | null | undefined, now?: Dayjs | null): string | null {
  const clock = dayjs.isDayjs(now) && now.isValid() ? now : dayjs();
  if (!value || !dayjs.isDayjs(value) || !value.isValid()) return '请选择时间';
  if (isChinaHoliday(value)) return '不能选择国家法定节假日';
  const { min, max } = bookingWindow(clock);
  if (value.isBefore(min, 'day') || !value.isAfter(clock)) return '不能选择已经过去的时间';
  if (value.isAfter(max)) return `最多只能预约未来 ${BOOKING_MAX_DAYS} 天内的时间`;
  return null;
}

/** 校验查询日期范围（按天） */
export function bookingRangeError(
  from: Dayjs | null | undefined,
  to: Dayjs | null | undefined,
  now?: Dayjs | null,
): string | null {
  const clock = dayjs.isDayjs(now) && now.isValid() ? now : dayjs();
  if (!from || !to || !from.isValid() || !to.isValid()) return '请选择有效的日期范围';
  if (from.isAfter(to, 'day')) return '请选择有效的日期范围';
  const { min, max } = bookingWindow(clock);
  if (from.isBefore(min, 'day') || to.isBefore(min, 'day')) return '不能查询已经过去的日期';
  if (from.isAfter(max, 'day') || to.isAfter(max, 'day')) {
    return `最多只能查看未来 ${BOOKING_MAX_DAYS} 天内的闲忙`;
  }
  return null;
}

/** 面试开始时间：当天若已过某些半点则禁用 */
export function interviewPastDisabledTime(selected?: Dayjs | null) {
  const clock = dayjs();
  const base = selected && dayjs.isDayjs(selected) && selected.isValid() ? selected : clock;
  const sameDay = base.isSame(clock, 'day');
  return {
    disabledHours: () => {
      const hours: number[] = [];
      for (let h = 0; h < 24; h++) {
        if (h < 9 || h > 17) hours.push(h);
        else if (sameDay && h < clock.hour()) hours.push(h);
      }
      return hours;
    },
    disabledMinutes: (hour: number) => {
      const blocked: number[] = [];
      if (hour === 9) blocked.push(0);
      if (sameDay && hour === clock.hour()) {
        for (const m of [0, 30]) {
          if (m <= clock.minute()) blocked.push(m);
        }
      }
      return [...new Set(blocked)];
    },
  };
}

/** 通用预约：禁用当天已过去的时分（不限制工作时段） */
export function bookingPastDisabledTime(selected?: Dayjs | null) {
  const clock = dayjs();
  const base = selected && dayjs.isDayjs(selected) && selected.isValid() ? selected : clock;
  const sameDay = base.isSame(clock, 'day');
  return {
    disabledHours: () => {
      if (!sameDay) return [];
      const hours: number[] = [];
      for (let h = 0; h < 24; h++) {
        if (h < clock.hour()) hours.push(h);
      }
      return hours;
    },
    disabledMinutes: (hour: number) => {
      if (!sameDay || hour !== clock.hour()) return [];
      const blocked: number[] = [];
      for (let m = 0; m < 60; m++) {
        if (m <= clock.minute()) blocked.push(m);
      }
      return blocked;
    },
  };
}
