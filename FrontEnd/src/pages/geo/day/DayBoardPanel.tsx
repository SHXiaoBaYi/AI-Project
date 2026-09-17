import { memo, useEffect, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { getGeoDailyBoardApi, getGeoLatestInspectDateApi, getGeoPlatformsApi, getGeoTopicOptionsApi } from '@/api/geo';
import type { GeoDailyBoard, GeoTopic } from '@/types/geo';
import { GeoBoardDimensionTabs } from '@/components/geo/GeoBoardDimensionTabs';
import { GeoCompareSummaryCards, GeoTrendBoard } from '@/components/geo/GeoTrendBoard';
import { GeoDailySummaryBoard } from '@/components/geo/GeoDailySummaryBoard';
import { GeoTopicPlatformTofuBoard } from '@/components/geo/GeoTopicPlatformTofuBoard';
import { DEMO_DATA_END, DEMO_DATA_PIVOT, DEMO_DATA_START, demoRangeByGrain } from '@/constants/demoData';
import { GEO_TERM_TYPES } from '@/constants/geo';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { toDayjs } from '@/utils/geoBoardQuery';

const emptyBoard: GeoDailyBoard = {
  mentionChart: [],
  firstMentionChart: [],
  recommendChart: [],
  rows: [],
  summaryGroups: [],
  ownerMentionChart: [],
  ownerFirstMentionChart: [],
  ownerRecommendChart: [],
  ownerRows: [],
  ownerSummaryGroups: [],
};

function rangeAround(anchor: Dayjs): [Dayjs, Dayjs] {
  return [anchor.subtract(13, 'day'), anchor];
}

const DayBoardPage = memo(function DayBoardPage() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoDailyBoard>(emptyBoard);
  const [defaultDays, setDefaultDays] = useState<[Dayjs, Dayjs]>(() => demoRangeByGrain('day', DEMO_DATA_PIVOT));
  const [activeRange, setActiveRange] = useState<[Dayjs, Dayjs]>(() => demoRangeByGrain('day', DEMO_DATA_PIVOT));
  const [ready, setReady] = useState(false);
  const [boardLoading, setBoardLoading] = useState(false);
  const [activeDate, setActiveDate] = useState<string>();

  const load = async (values?: Record<string, any>, fallbackRange?: [Dayjs, Dayjs]) => {
    const base = fallbackRange ?? defaultDays;
    const start = toDayjs(values?.dateRange?.[0]) ?? base[0];
    const end = toDayjs(values?.dateRange?.[1]) ?? base[1];
    const nextRange: [Dayjs, Dayjs] = [start.startOf('day'), end.endOf('day')];
    const query = {
      startDate: nextRange[0].format('YYYY-MM-DD'),
      endDate: nextRange[1].format('YYYY-MM-DD'),
      topicId: values?.topicId as number | undefined,
      keyword: values?.keyword?.trim() || undefined,
      termType: values?.termType || undefined,
      platforms: values?.platforms?.length ? (values.platforms as string[]) : undefined,
    };

    setBoardLoading(true);
    try {
      const data = await getGeoDailyBoardApi(query);
      setActiveRange(nextRange);

      const nextBoard: GeoDailyBoard = {
        mentionChart: data?.mentionChart ?? [],
        firstMentionChart: data?.firstMentionChart ?? [],
        recommendChart: data?.recommendChart ?? [],
        sampleChart: data?.sampleChart ?? [],
        rankChart: data?.rankChart ?? [],
        negativeChart: data?.negativeChart ?? [],
        compareSummary: data?.compareSummary,
        negativeCount: data?.negativeCount ?? 0,
        negativeRows: data?.negativeRows ?? [],
        rows: data?.rows ?? [],
        summaryGroups: data?.summaryGroups ?? [],
        ownerMentionChart: data?.ownerMentionChart ?? [],
        ownerFirstMentionChart: data?.ownerFirstMentionChart ?? [],
        ownerRecommendChart: data?.ownerRecommendChart ?? [],
        ownerRows: data?.ownerRows ?? [],
        ownerSummaryGroups: data?.ownerSummaryGroups ?? [],
      };
      setBoard(nextBoard);

      const dates = [
        ...new Set([
          ...(nextBoard.summaryGroups?.map((g) => g.inspectDate) || []),
          ...(nextBoard.ownerSummaryGroups?.map((g) => g.inspectDate) || []),
          ...(nextBoard.rows?.map((r) => r.dateLabel) || []),
          ...(nextBoard.ownerRows?.map((r) => r.dateLabel) || []),
        ]),
      ].sort((a, b) => b.localeCompare(a));
      if (dates[0]) setActiveDate(dates[0]);
    } finally {
      setBoardLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [topicList, platformList, latest] = await Promise.all([
        getGeoTopicOptionsApi(),
        getGeoPlatformsApi(),
        getGeoLatestInspectDateApi().catch(() => ({ inspectDate: undefined as string | undefined })),
      ]);
      if (cancelled) return;
      setTopics(topicList);
      setPlatforms(platformList);
      const anchor = latest?.inspectDate ? dayjs(latest.inspectDate) : DEMO_DATA_PIVOT;
      const inDemo =
        anchor.isValid() && !anchor.isBefore(dayjs(DEMO_DATA_START)) && !anchor.isAfter(dayjs(DEMO_DATA_END));
      const nextRange = inDemo ? rangeAround(anchor) : demoRangeByGrain('day', DEMO_DATA_PIVOT);
      setDefaultDays(nextRange);
      setReady(true);
      void load({ dateRange: nextRange }, nextRange);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const topicRows = board.rows ?? [];
  const ownerRows = board.ownerRows ?? [];

  if (!ready) {
    return <Card loading />;
  }

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          key={defaultDays[0].format('YYYY-MM-DD') + defaultDays[1].format('YYYY-MM-DD')}
          initialValues={{ dateRange: defaultDays }}
          onFinish={async (v) => {
            await load(v);
            return true;
          }}
          onReset={() => {
            void load({ dateRange: defaultDays }, defaultDays);
          }}
        >
          <ProFormDateRangePicker
            name='dateRange'
            label='日期范围'
            fieldProps={{ placeholder: ['开始日期', '结束日期'] }}
          />
          <ProFormSelect
            name='termType'
            label='话题类型'
            allowClear
            options={[...GEO_TERM_TYPES]}
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
            label='AI平台'
            allowClear
            options={platforms.map((p) => ({ label: p, value: p }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
        </QueryFilter>
      </Card>

      <GeoCompareSummaryCards
        title='露出率对比（同比 / 环比）'
        summary={board.compareSummary}
      />

      {boardLoading ? <Card loading /> : null}

      <GeoTopicPlatformTofuBoard
        grain='day'
        range={activeRange}
      />

      <GeoBoardDimensionTabs
        topic={
          <>
            <GeoDailySummaryBoard
              dimension='topic'
              groups={board.summaryGroups}
              activeDate={activeDate}
              onActiveDateChange={setActiveDate}
            />
            <GeoTrendBoard
              mentionChart={board.mentionChart}
              firstMentionChart={board.firstMentionChart}
              recommendChart={board.recommendChart}
              rows={topicRows.map((r) => ({ ...r, axisLabel: r.dateLabel }))}
              axisTitle='日期'
              groupTitle='话题'
              tableTitle='日报明细（话题维度，完整日期范围）'
            />
          </>
        }
        owner={
          <>
            <GeoDailySummaryBoard
              dimension='owner'
              groups={board.ownerSummaryGroups}
              activeDate={activeDate}
              onActiveDateChange={setActiveDate}
            />
            <GeoTrendBoard
              mentionChart={board.ownerMentionChart}
              firstMentionChart={board.ownerFirstMentionChart}
              recommendChart={board.ownerRecommendChart}
              rows={ownerRows.map((r) => ({
                ...r,
                axisLabel: r.dateLabel,
                topicName: r.ownerName || r.topicName,
              }))}
              axisTitle='日期'
              groupTitle='负责人'
              tableTitle='日报明细（负责人维度，完整日期范围）'
            />
          </>
        }
      />
    </div>
  );
});

export default DayBoardPage;
