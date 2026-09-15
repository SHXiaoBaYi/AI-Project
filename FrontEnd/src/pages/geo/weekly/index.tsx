import { memo, useEffect, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs from 'dayjs';
import { getGeoPlatformsApi, getGeoTopicOptionsApi, getGeoWeeklyBoardApi } from '@/api/geo';
import type { GeoTopic, GeoWeeklyBoard } from '@/types/geo';
import { GeoBoardDimensionTabs } from '@/components/geo/GeoBoardDimensionTabs';
import { GeoTrendBoard } from '@/components/geo/GeoTrendBoard';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { endOfIsoWeek, startOfIsoWeek, toDayjs } from '@/utils/geoBoardQuery';

const emptyBoard: GeoWeeklyBoard = { mentionChart: [], firstMentionChart: [], recommendChart: [], rows: [] };
const defaultWeeks = [startOfIsoWeek(dayjs().subtract(4, 'week')), endOfIsoWeek(dayjs())];

const WeeklyPage = memo(function WeeklyPage() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoWeeklyBoard>(emptyBoard);

  const load = async (values?: Record<string, any>) => {
    const start = toDayjs(values?.weekRange?.[0]) ?? defaultWeeks[0];
    const end = toDayjs(values?.weekRange?.[1]) ?? defaultWeeks[1];
    const data = await getGeoWeeklyBoardApi({
      startDate: startOfIsoWeek(start).format('YYYY-MM-DD'),
      endDate: endOfIsoWeek(end).format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword?.trim() || undefined,
      platforms: values?.platforms?.length ? values.platforms : undefined,
    });
    setBoard({
      mentionChart: data?.mentionChart ?? [],
      firstMentionChart: data?.firstMentionChart ?? [],
      recommendChart: data?.recommendChart ?? [],
      rows: data?.rows ?? [],
      persistedPeriodCount: data?.persistedPeriodCount ?? 0,
      compareSummary: data?.compareSummary,
      ownerMentionChart: data?.ownerMentionChart ?? [],
      ownerFirstMentionChart: data?.ownerFirstMentionChart ?? [],
      ownerRecommendChart: data?.ownerRecommendChart ?? [],
      ownerRows: data?.ownerRows ?? [],
      ownerCompareSummary: data?.ownerCompareSummary,
    });
  };

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    void load({ weekRange: defaultWeeks });
  }, []);

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ weekRange: defaultWeeks }}
          onFinish={async (v) => {
            await load(v);
            return true;
          }}
          onReset={() => {
            void load({ weekRange: defaultWeeks });
          }}
        >
          <ProFormDateRangePicker
            name='weekRange'
            label='周范围'
            fieldProps={{
              picker: 'week',
              format: 'YYYY-[第]ww[周]',
              placeholder: ['开始周', '结束周'],
            }}
          />
          <ProFormSelect
            name='topicId'
            label='话题'
            allowClear
            showSearch
            optionFilterProp='label'
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
          周报只读查看；已结束周由定时任务自动落库（已落库周期 {board.persistedPeriodCount ?? 0}）
        </span>
      </Card>
      <GeoBoardDimensionTabs
        topic={
          <GeoTrendBoard
            showCompare
            compareHint='环比=上一周；同比=去年同周'
            compareSummary={board.compareSummary}
            mentionChart={board.mentionChart}
            firstMentionChart={board.firstMentionChart}
            recommendChart={board.recommendChart}
            rows={(board.rows ?? []).map((r) => ({ ...r, axisLabel: r.weekLabel }))}
            axisTitle='周次'
            groupTitle='话题'
            tableTitle='周报明细（话题维度）'
          />
        }
        owner={
          <GeoTrendBoard
            showCompare
            compareHint='环比=上一周；同比=去年同周'
            compareSummary={board.ownerCompareSummary}
            mentionChart={board.ownerMentionChart}
            firstMentionChart={board.ownerFirstMentionChart}
            recommendChart={board.ownerRecommendChart}
            rows={(board.ownerRows ?? []).map((r) => ({
              ...r,
              axisLabel: r.weekLabel,
              topicName: r.ownerName || r.topicName,
            }))}
            axisTitle='周次'
            groupTitle='负责人'
            tableTitle='周报明细（负责人维度）'
          />
        }
      />
    </div>
  );
});

export default WeeklyPage;
