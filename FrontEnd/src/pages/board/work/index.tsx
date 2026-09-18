import { useEffect, useState } from 'react';
import { Card, DatePicker, Radio, Select, Space } from 'antd';
import type { Dayjs } from 'dayjs';
import { TaskOpsBoard } from '@/components/board/TaskOpsBoard';
import { getGeoOwnerOptionsApi } from '@/api/geo';
import { getTaskTypeOptionsApi } from '@/api/task';
import { demoRangeByGrain, type DemoBoardGrain } from '@/constants/demoData';

function normalizeRange(grain: DemoBoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

function defaultRange(grain: DemoBoardGrain): [Dayjs, Dayjs] {
  return demoRangeByGrain(grain);
}

/** 数据看板 · 任务：到期/完成指标 + 完成率下钻 */
export default function WorkBoardPage() {
  const [grain, setGrain] = useState<DemoBoardGrain>('week');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => defaultRange('week'));
  const [filterUserIds, setFilterUserIds] = useState<number[]>([]);
  const [taskTypes, setTaskTypes] = useState<string[]>([]);
  const [userOptions, setUserOptions] = useState<{ label: string; value: number }[]>([]);
  const [typeOptions, setTypeOptions] = useState<{ label: string; value: string }[]>([]);

  useEffect(() => {
    void getGeoOwnerOptionsApi().then((list) => {
      setUserOptions((list ?? []).map((u) => ({ label: u.displayName, value: u.userId })));
    });
    void getTaskTypeOptionsApi().then((list) => {
      setTypeOptions((list ?? []).map((t) => ({ label: t.typeName, value: t.typeName })));
    });
  }, []);

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
          <Select
            mode='multiple'
            allowClear
            showSearch
            optionFilterProp='label'
            maxTagCount='responsive'
            placeholder='人员'
            className='min-w-[180px]'
            options={userOptions}
            value={filterUserIds}
            onChange={(v) => setFilterUserIds(v ?? [])}
          />
          <Select
            mode='multiple'
            allowClear
            showSearch
            optionFilterProp='label'
            maxTagCount='responsive'
            placeholder='任务类型'
            className='min-w-[200px]'
            options={typeOptions}
            value={taskTypes}
            onChange={(v) => setTaskTypes(v ?? [])}
          />
          <span className='text-xs text-neutral-400'>筛选同时作用于下方四个看板</span>
        </Space>
      </Card>

      <TaskOpsBoard
        grain={grain}
        range={range}
        filterUserIds={filterUserIds}
        taskTypes={taskTypes}
      />
    </div>
  );
}
