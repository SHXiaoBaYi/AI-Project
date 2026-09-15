import dayjs, { type ConfigType } from 'dayjs';

/** 统一日期时间展示/提交格式：年-月-日 时:分:秒 */
export const DATE_TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/** 仅日期 */
export const DATE_FORMAT = 'YYYY-MM-DD';

export function formatDateTime(value?: ConfigType | null): string {
  if (value == null || value === '') {
    return '-';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format(DATE_TIME_FORMAT) : String(value);
}

export function toDateTimeParam(value?: ConfigType | null): string | undefined {
  if (value == null || value === '') {
    return undefined;
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format(DATE_TIME_FORMAT) : undefined;
}
