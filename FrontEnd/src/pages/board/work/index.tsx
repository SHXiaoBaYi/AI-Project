import { useState } from 'react';
import { Card, DatePicker, Radio, Space } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { TaskOpsBoard } from '@/components/board/TaskOpsBoard';
import { type DemoBoardGrain } from '@/constants/demoData';

function normalizeRange(grain: DemoBoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

function defaultRange(grain: DemoBoardGrain): [Dayjs, Dayjs] {
  const pivot = dayjs();
  if (grain === 'day') return [pivot.subtract(29, 'day').startOf('day'), pivot.endOf('day')];
  if (grain === 'week') return [pivot.subtract(3, 'week').startOf('week'), pivot.endOf('week')];
  if (grain === 'month') return [pivot.subtract(5, 'month').startOf('month'), pivot.endOf('month')];
  return [pivot.startOf('year'), pivot.endOf('year')];
}

/** 数据看板 · 任务：到期/完成指标 + 完成率下钻 */
export default function WorkBoardPage() {
  const [grain, setGrain] = useState<DemoBoardGrain>('week');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => defaultRange('week'));

  const onGrainChange = (g: DemoBoardGrain) => {
    setGrain(g);
    setRange(defaultRange(g));
  };

  const pickerProps =
    grain === 'week'
      ? { picker: 'week' as const }
      : grain === 'month'
        ? { picker: 'month' as const }
        : grain === 'year'
          ? { picker: 'year' as const }
          : { picker: 'date' as const };

  return (
    <div className='flex flex-col gap-4 p-4'>
      <Card
        size='small'
        title='任务看板筛选'
      >
        <Space wrap>
          <Radio.Group
            value={grain}
            optionType='button'
            options={[
              { label: '日', value: 'day' },
              { label: '周', value: 'week' },
              { label: '月', value: 'month' },
              { label: '年', value: 'year' },
            ]}
            onChange={(e) => onGrainChange(e.target.value)}
          />
          <DatePicker.RangePicker
            {...pickerProps}
            value={range}
            allowClear={false}
            onChange={(v) => {
              if (v?.[0] && v?.[1]) setRange(normalizeRange(grain, v[0], v[1]));
            }}
          />
          <span className='text-xs text-neutral-400'>仅作用于按时完成率、任务完成率</span>
        </Space>
      </Card>

      <TaskOpsBoard
        grain={grain}
        range={range}
      />
    </div>
  );
}
