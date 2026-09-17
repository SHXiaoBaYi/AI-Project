import dayjs, { type Dayjs } from 'dayjs';
import { endOfIsoWeek, startOfIsoWeek } from '@/utils/geoBoardQuery';

/** 演示数据覆盖的业务日期（日监测 inspect_date / 看板区间） */
export const DEMO_DATA_START = '2014-01-01';
export const DEMO_DATA_END = '2015-12-31';

/** 看板默认锚点：演示数据末尾，保证日/周/月窗口落在有数区间内 */
export const DEMO_DATA_PIVOT = dayjs(DEMO_DATA_END);

export type DemoBoardGrain = 'day' | 'week' | 'month' | 'year';

/** 按粒度生成落在演示数据上的默认日期范围 */
export function demoRangeByGrain(grain: DemoBoardGrain, pivot: Dayjs = DEMO_DATA_PIVOT): [Dayjs, Dayjs] {
  if (grain === 'day') {
    return [pivot.subtract(29, 'day').startOf('day'), pivot.endOf('day')];
  }
  if (grain === 'week') {
    return [startOfIsoWeek(pivot.subtract(3, 'week')), endOfIsoWeek(pivot)];
  }
  if (grain === 'month') {
    return [pivot.subtract(5, 'month').startOf('month'), pivot.endOf('month')];
  }
  return [dayjs(DEMO_DATA_START).startOf('year'), dayjs(DEMO_DATA_END).endOf('year')];
}

/** 列表「创建时间」默认筛到演示年（需配合演示数据 create_time 回填） */
export const DEMO_CREATE_TIME_RANGE: [Dayjs, Dayjs] = [
  dayjs(DEMO_DATA_START).startOf('day'),
  dayjs(DEMO_DATA_END).endOf('day'),
];
