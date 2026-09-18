import dayjs, { type Dayjs } from 'dayjs';
import { endOfIsoWeek, startOfIsoWeek } from '@/utils/geoBoardQuery';

/** 演示数据覆盖的业务日期（日监测 inspect_date / 看板区间） */
export const DEMO_DATA_START = '2014-01-01';
export const DEMO_DATA_END = '2015-12-31';

export type DemoBoardGrain = 'day' | 'week' | 'month' | 'year';

/** 指标筛选默认范围：按日近 1 个月，按周近 4 周，按月近 4 个月，按年近 2 年 */
export function demoRangeByGrain(grain: DemoBoardGrain, pivot: Dayjs = dayjs()): [Dayjs, Dayjs] {
  if (grain === 'day') {
    return [pivot.subtract(1, 'month').startOf('day'), pivot.endOf('day')];
  }
  if (grain === 'week') {
    return [startOfIsoWeek(pivot.subtract(3, 'week')), endOfIsoWeek(pivot)];
  }
  if (grain === 'month') {
    return [pivot.subtract(3, 'month').startOf('month'), pivot.endOf('month')];
  }
  return [pivot.subtract(1, 'year').startOf('year'), pivot.endOf('year')];
}

/** 列表「创建时间」默认筛到演示年（需配合演示数据 create_time 回填） */
export const DEMO_CREATE_TIME_RANGE: [Dayjs, Dayjs] = [
  dayjs(DEMO_DATA_START).startOf('day'),
  dayjs(DEMO_DATA_END).endOf('day'),
];
