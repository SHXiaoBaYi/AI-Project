import { memo, useEffect, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs from 'dayjs';
import { getGeoMonthlyBoardApi, getGeoPlatformsApi, getGeoTopicOptionsApi } from '@/api/geo';
import type { GeoMonthlyBoard, GeoTopic } from '@/types/geo';
import { GeoTrendBoard } from '@/components/geo/GeoTrendBoard';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';

const emptyBoard: GeoMonthlyBoard = { mentionChart: [], firstMentionChart: [], recommendChart: [], rows: [] };
const defaultMonths = [dayjs().subtract(5, 'month').startOf('month'), dayjs().endOf('month')];

const MonthlyPage = memo(function MonthlyPage() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoMonthlyBoard>(emptyBoard);

  const load = async (values?: Record<string, any>) => {
    const range = (values?.monthRange as dayjs.Dayjs[] | undefined) ?? defaultMonths;
    const data = await getGeoMonthlyBoardApi({
      startDate: (range[0] ?? dayjs()).startOf('month').format('YYYY-MM-DD'),
      endDate: (range[1] ?? dayjs()).endOf('month').format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword,
      platforms: values?.platforms,
    });
    setBoard({
      mentionChart: data?.mentionChart ?? [],
      firstMentionChart: data?.firstMentionChart ?? [],
      recommendChart: data?.recommendChart ?? [],
      rows: data?.rows ?? [],
      persistedPeriodCount: data?.persistedPeriodCount ?? 0,
    });
  };

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    load();
  }, []);

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ monthRange: defaultMonths }}
          onFinish={async (v) => {
            await load(v);
            return true;
          }}
        >
          <ProFormDateRangePicker
            name='monthRange'
            label='月份范围'
            fieldProps={{
              picker: 'month',
              format: 'YYYY-MM',
              placeholder: ['开始月', '结束月'],
            }}
          />
          <ProFormSelect
            name='topicId'
            label='话题'
            allowClear
            options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
          />
          <ProFormText
            name='keyword'
            label='关键字'
          />
          <ProFormSelect
            name='platforms'
            label='平台'
            allowClear
            options={platforms.map((p) => ({ label: p, value: p }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
        </QueryFilter>
      </Card>
      <Card size='small'>
        <span className='text-sm text-neutral-600'>
          月报只读查看；已结束月由定时任务自动落库（已落库周期 {board.persistedPeriodCount ?? 0}）
        </span>
      </Card>
      <GeoTrendBoard
        mentionChart={board.mentionChart}
        firstMentionChart={board.firstMentionChart}
        recommendChart={board.recommendChart}
        rows={(board.rows ?? []).map((r) => ({ ...r, axisLabel: r.monthLabel }))}
        axisTitle='月份'
        tableTitle='月报明细（只读，已落库优先）'
      />
    </div>
  );
});

export default MonthlyPage;
