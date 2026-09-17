import { useState } from 'react';
import { Card, DatePicker, Radio, Space } from 'antd';
import type { Dayjs } from 'dayjs';
import ChartDrillBoard from '@/components/board/ChartDrillBoard';
import { GeoTopicPlatformTofuBoard } from '@/components/geo/GeoTopicPlatformTofuBoard';
import { DEMO_DATA_PIVOT, demoRangeByGrain, type DemoBoardGrain } from '@/constants/demoData';

function normalizeRange(grain: DemoBoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

/** 数据看板 · GEO：统一日期筛选驱动三个豆腐块 + 话题下钻看板 */
export default function GeoBoardPage() {
  const [grain, setGrain] = useState<DemoBoardGrain>('week');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => demoRangeByGrain('week', DEMO_DATA_PIVOT));

  const onGrainChange = (g: DemoBoardGrain) => {
    setGrain(g);
    setRange(demoRangeByGrain(g, DEMO_DATA_PIVOT));
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
        title='GEO 看板筛选'
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
          <span className='text-xs text-neutral-400'>同时作用于下方四个看板</span>
        </Space>
      </Card>

      <Card
        size='small'
        title='话题 × AI 平台数据面板'
      >
        <GeoTopicPlatformTofuBoard
          grain={grain}
          range={range}
        />
      </Card>

      <ChartDrillBoard
        domain='geo'
        title='数据看板 · GEO（话题下钻）'
        grain={grain}
        range={range}
        hideTimeFilter
      />
    </div>
  );
}
