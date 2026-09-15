import dayjs, { type Dayjs } from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';

dayjs.extend(isoWeek);

/** QueryFilter 提交的日期可能是 Dayjs 或字符串，统一安全转换 */
export function toDayjs(value: unknown): Dayjs | null {
  if (value == null || value === '') return null;
  if (dayjs.isDayjs(value)) return value.isValid() ? value : null;

  const raw = String(value).trim();
  // 周选择器展示格式：2026-第36周
  const weekMatch = raw.match(/^(\d{4})-第(\d{1,2})周$/);
  if (weekMatch) {
    const parsed = dayjs().year(Number(weekMatch[1])).isoWeek(Number(weekMatch[2])).startOf('isoWeek');
    return parsed.isValid() ? parsed : null;
  }
  // 年选择器：2026
  if (/^\d{4}$/.test(raw)) {
    const parsed = dayjs(`${raw}-01-01`);
    return parsed.isValid() ? parsed : null;
  }

  const parsed = dayjs(raw);
  return parsed.isValid() ? parsed : null;
}

export function toDateStr(value: unknown, fallback?: Dayjs): string | undefined {
  const d = toDayjs(value) ?? fallback ?? null;
  return d ? d.format('YYYY-MM-DD') : undefined;
}

export function startOfIsoWeek(value: Dayjs) {
  return value.startOf('isoWeek');
}

export function endOfIsoWeek(value: Dayjs) {
  return value.endOf('isoWeek');
}
