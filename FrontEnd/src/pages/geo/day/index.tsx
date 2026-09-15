import { memo, useEffect, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getGeoDailyBoardApi,
  getGeoDailyListApi,
  getGeoLatestInspectDateApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
} from '@/api/geo';
import type { GeoDailyBoard, GeoDailyVO, GeoTopic } from '@/types/geo';
import { GeoBoardDimensionTabs } from '@/components/geo/GeoBoardDimensionTabs';
import { GeoTrendBoard } from '@/components/geo/GeoTrendBoard';
import { GeoDailySummaryBoard } from '@/components/geo/GeoDailySummaryBoard';
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
  const [records, setRecords] = useState<GeoDailyVO[]>([]);
  const [defaultDays, setDefaultDays] = useState<[Dayjs, Dayjs]>(rangeAround(dayjs()));
  const [ready, setReady] = useState(false);
  const [activeDate, setActiveDate] = useState<string>();

  const load = async (values?: Record<string, any>, fallbackRange?: [Dayjs, Dayjs]) => {
    const base = fallbackRange ?? defaultDays;
    const start = toDayjs(values?.dateRange?.[0]) ?? base[0];
    const end = toDayjs(values?.dateRange?.[1]) ?? base[1];
    const query = {
      startDate: start.format('YYYY-MM-DD'),
      endDate: end.format('YYYY-MM-DD'),
      topicId: values?.topicId as number | undefined,
      keyword: values?.keyword?.trim() || undefined,
      platforms: values?.platforms?.length ? (values.platforms as string[]) : undefined,
    };

    const [data, list] = await Promise.all([
      getGeoDailyBoardApi(query),
      getGeoDailyListApi({
        ...query,
        pageNum: 1,
        pageSize: 5000,
      }),
    ]);

    const nextBoard: GeoDailyBoard = {
      mentionChart: data?.mentionChart ?? [],
      firstMentionChart: data?.firstMentionChart ?? [],
      recommendChart: data?.recommendChart ?? [],
      rows: data?.rows ?? [],
      summaryGroups: data?.summaryGroups ?? [],
      ownerMentionChart: data?.ownerMentionChart ?? [],
      ownerFirstMentionChart: data?.ownerFirstMentionChart ?? [],
      ownerRecommendChart: data?.ownerRecommendChart ?? [],
      ownerRows: data?.ownerRows ?? [],
      ownerSummaryGroups: data?.ownerSummaryGroups ?? [],
    };
    const nextRecords = list?.rows ?? [];
    setBoard(nextBoard);
    setRecords(nextRecords);

    const dates = [
      ...new Set(
        nextRecords
          .map((r) => r.inspectDate)
          .filter(Boolean)
          .concat(nextBoard.summaryGroups?.map((g) => g.inspectDate) || [])
          .concat(nextBoard.ownerSummaryGroups?.map((g) => g.inspectDate) || [])
          .concat(nextBoard.rows?.map((r) => r.dateLabel) || [])
          .concat(nextBoard.ownerRows?.map((r) => r.dateLabel) || []),
      ),
    ].sort((a, b) => b.localeCompare(a));
    if (dates[0]) setActiveDate(dates[0]);
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
      const anchor = latest?.inspectDate ? dayjs(latest.inspectDate) : dayjs();
      const nextRange = rangeAround(anchor.isValid() ? anchor : dayjs());
      setDefaultDays(nextRange);
      setReady(true);
      await load({ dateRange: nextRange }, nextRange);
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

      <GeoBoardDimensionTabs
        topic={
          <>
            <GeoDailySummaryBoard
              dimension='topic'
              groups={board.summaryGroups}
              records={records}
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
