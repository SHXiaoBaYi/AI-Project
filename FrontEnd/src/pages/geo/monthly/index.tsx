import { memo, useEffect, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import { Card } from 'antd';
import dayjs from 'dayjs';
import { getGeoMonthlyBoardApi, getGeoPlatformsApi, getGeoTopicOptionsApi } from '@/api/geo';
import type { GeoMonthlyBoard, GeoTopic } from '@/types/geo';
import { GeoBoardDimensionTabs } from '@/components/geo/GeoBoardDimensionTabs';
import { GeoTrendBoard } from '@/components/geo/GeoTrendBoard';
import { GEO_TERM_TYPES } from '@/constants/geo';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { toDayjs } from '@/utils/geoBoardQuery';

const emptyBoard: GeoMonthlyBoard = { mentionChart: [], firstMentionChart: [], recommendChart: [], rows: [] };
const defaultMonths = [dayjs().subtract(5, 'month').startOf('month'), dayjs().endOf('month')];

const MonthlyPage = memo(function MonthlyPage() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoMonthlyBoard>(emptyBoard);

  const load = async (values?: Record<string, any>) => {
    const start = toDayjs(values?.monthRange?.[0]) ?? defaultMonths[0];
    const end = toDayjs(values?.monthRange?.[1]) ?? defaultMonths[1];
    const data = await getGeoMonthlyBoardApi({
      startDate: start.startOf('month').format('YYYY-MM-DD'),
      endDate: end.endOf('month').format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword?.trim() || undefined,
      termType: values?.termType || undefined,
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
    void load({ monthRange: defaultMonths });
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
          onReset={() => {
            void load({ monthRange: defaultMonths });
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
            label='平台'
            allowClear
            options={platforms.map((p) => ({ label: p, value: p }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
        </QueryFilter>
      </Card>
      <Card size='small'>
        <span className='text-sm text-neutral-600'>
          月报只读查看；已结束月读落库快照（已落库周期 {board.persistedPeriodCount ?? 0}
          ）；当前月实时聚合；筛话题类型时按实时聚合
        </span>
      </Card>
      <GeoBoardDimensionTabs
        topic={
          <GeoTrendBoard
            showCompare
            compareHint='环比=上一月；同比=去年同月'
            compareSummary={board.compareSummary}
            mentionChart={board.mentionChart}
            firstMentionChart={board.firstMentionChart}
            recommendChart={board.recommendChart}
            rows={(board.rows ?? []).map((r) => ({ ...r, axisLabel: r.monthLabel }))}
            axisTitle='月份'
            groupTitle='话题'
            tableTitle='月报明细（话题维度）'
          />
        }
        owner={
          <GeoTrendBoard
            showCompare
            compareHint='环比=上一月；同比=去年同月'
            compareSummary={board.ownerCompareSummary}
            mentionChart={board.ownerMentionChart}
            firstMentionChart={board.ownerFirstMentionChart}
            recommendChart={board.ownerRecommendChart}
            rows={(board.ownerRows ?? []).map((r) => ({
              ...r,
              axisLabel: r.monthLabel,
              topicName: r.ownerName || r.topicName,
            }))}
            axisTitle='月份'
            groupTitle='负责人'
            tableTitle='月报明细（负责人维度）'
          />
        }
      />
    </div>
  );
});

export default MonthlyPage;
