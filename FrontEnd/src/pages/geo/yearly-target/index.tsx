import { memo, useEffect, useState, lazy, Suspense } from 'react';
import {
  ProFormDatePicker,
  ProFormDateRangePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  QueryFilter,
} from '@ant-design/pro-components';
import { App, Button, Card, Popconfirm, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { GeoBoardDimensionTabs } from '@/components/geo/GeoBoardDimensionTabs';
import { GeoCompareSummaryCards, buildCompareSummaryFromRows } from '@/components/geo/GeoTrendBoard';
import {
  deleteGeoYearTargetApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
  getGeoYearlyBoardApi,
  getGeoYearTargetsApi,
  saveGeoYearTargetApi,
} from '@/api/geo';
import type { GeoTopic, GeoYearTarget, GeoYearlyBoard, GeoYearlyRow } from '@/types/geo';
import { DEMO_DATA_PIVOT, demoRangeByGrain } from '@/constants/demoData';
import { GEO_TERM_TYPES } from '@/constants/geo';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { toDayjs } from '@/utils/geoBoardQuery';

const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Line })));
const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Column })));

const defaultYears = demoRangeByGrain('year', DEMO_DATA_PIVOT);

function YearlyBoardSlice({
  rows,
  actualChart,
  achieveChart,
  compareSummary,
  groupTitle,
  persistedPeriodCount,
  onConfigureTarget,
}: {
  rows: GeoYearlyRow[];
  actualChart: GeoYearlyBoard['actualChart'];
  achieveChart: GeoYearlyBoard['achieveChart'];
  compareSummary?: GeoYearlyBoard['compareSummary'];
  groupTitle: string;
  persistedPeriodCount?: number;
  onConfigureTarget?: () => void;
}) {
  return (
    <>
      <GeoCompareSummaryCards
        title={`年报同比 / 环比看板（${groupTitle}）`}
        alwaysShow
        summary={
          compareSummary ??
          buildCompareSummaryFromRows(
            (rows ?? []).map((r) => ({
              axisLabel: r.periodLabel,
              sampleCount: r.sampleCount,
              mentionRate: r.actualRate,
              firstMentionRate: r.achieveRate,
              recommendCount: r.sampleCount,
              mentionRateMom: r.actualRateMom,
              mentionRateYoy: r.actualRateYoy,
              firstMentionRateMom: r.achieveRateMom,
              firstMentionRateYoy: r.achieveRateYoy,
              topicName: r.ownerName || r.topicName,
            })),
            '环比/同比=上一年度同期',
          )
        }
      />
      <Card title='实际达成%（折线，系列=平台）'>
        <Suspense fallback={<div className='h-[280px]' />}>
          <Line
            data={actualChart}
            xField='axis'
            yField='value'
            colorField='series'
            height={280}
          />
        </Suspense>
      </Card>
      <Card title='目标达成率%（柱状，系列=平台）'>
        <Suspense fallback={<div className='h-[260px]' />}>
          <Column
            data={achieveChart}
            xField='axis'
            yField='value'
            colorField='series'
            height={260}
          />
        </Suspense>
      </Card>
      <Card
        title={`达成明细（${groupTitle}，已落库周期 ${persistedPeriodCount ?? 0}）`}
        extra={
          onConfigureTarget ? (
            <PermissionButton
              perm='geo:yearly:target'
              onClick={onConfigureTarget}
            >
              配置目标
            </PermissionButton>
          ) : null
        }
      >
        <div className='mb-3 text-sm text-neutral-600'>
          年报数据由定时任务自动落库，页面仅支持查看；目标配置仍可维护。
        </div>
        <Table
          rowKey={(r) => `${r.periodLabel}-${r.ownerName || r.topicName}-${r.platform}`}
          dataSource={rows}
          size='small'
          pagination={{
            pageSize: 20,
            hideOnSinglePage: true,
            showSizeChanger: false,
            showTotal: (total) => `共 ${total} 条`,
          }}
          scroll={{ x: 'max-content', y: 300 }}
          columns={[
            { title: '时间', dataIndex: 'periodLabel' },
            {
              title: groupTitle,
              render: (_, r) => (groupTitle === '负责人' ? r.ownerName || r.topicName || '-' : r.topicName),
            },
            { title: '平台', dataIndex: 'platform' },
            { title: '目标%', dataIndex: 'targetRate' },
            { title: '实际达成%', dataIndex: 'actualRate' },
            {
              title: '实际环比',
              dataIndex: 'actualRateMom',
              render: (v) => (v == null ? '-' : `${v > 0 ? '+' : ''}${v}pp`),
            },
            {
              title: '实际同比',
              dataIndex: 'actualRateYoy',
              render: (v) => (v == null ? '-' : `${v > 0 ? '+' : ''}${v}pp`),
            },
            { title: '达成率%', dataIndex: 'achieveRate' },
            {
              title: '达成环比',
              dataIndex: 'achieveRateMom',
              render: (v) => (v == null ? '-' : `${v > 0 ? '+' : ''}${v}pp`),
            },
            {
              title: '达成同比',
              dataIndex: 'achieveRateYoy',
              render: (v) => (v == null ? '-' : `${v > 0 ? '+' : ''}${v}pp`),
            },
            { title: '样本', dataIndex: 'sampleCount' },
            {
              title: '来源',
              dataIndex: 'fromSnapshot',
              width: 90,
              render: (v: boolean | undefined) => (v ? <Tag color='success'>已落库</Tag> : <Tag>实时</Tag>),
            },
          ]}
        />
      </Card>
    </>
  );
}

const YearlyPage = memo(function YearlyPage() {
  const { message } = App.useApp();
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [board, setBoard] = useState<GeoYearlyBoard>({ actualChart: [], achieveChart: [], rows: [] });
  const [targets, setTargets] = useState<GeoYearTarget[]>([]);
  const [targetOpen, setTargetOpen] = useState(false);

  const loadBoard = async (values?: Record<string, any>) => {
    const start = toDayjs(values?.yearRange?.[0]) ?? defaultYears[0];
    const end = toDayjs(values?.yearRange?.[1]) ?? defaultYears[1];
    const data = await getGeoYearlyBoardApi({
      startDate: start.startOf('year').format('YYYY-MM-DD'),
      endDate: end.endOf('year').format('YYYY-MM-DD'),
      topicId: values?.topicId,
      keyword: values?.keyword?.trim() || undefined,
      termType: values?.termType || undefined,
      platforms: values?.platforms?.length ? values.platforms : undefined,
    });
    setBoard({
      actualChart: data?.actualChart ?? [],
      achieveChart: data?.achieveChart ?? [],
      rows: data?.rows ?? [],
      persistedPeriodCount: data?.persistedPeriodCount ?? 0,
      compareSummary: data?.compareSummary,
      ownerActualChart: data?.ownerActualChart ?? [],
      ownerAchieveChart: data?.ownerAchieveChart ?? [],
      ownerRows: data?.ownerRows ?? [],
      ownerCompareSummary: data?.ownerCompareSummary,
    });
  };

  const loadTargets = () => getGeoYearTargetsApi().then(setTargets);

  useEffect(() => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    void loadBoard({ yearRange: defaultYears });
    loadTargets();
  }, []);

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ yearRange: defaultYears }}
          onFinish={async (v) => {
            await loadBoard(v);
            return true;
          }}
          onReset={() => {
            void loadBoard({ yearRange: defaultYears });
          }}
        >
          <ProFormDateRangePicker
            name='yearRange'
            label='年份范围'
            fieldProps={{ picker: 'year', format: 'YYYY', placeholder: ['开始年', '结束年'] }}
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
      <GeoBoardDimensionTabs
        topic={
          <YearlyBoardSlice
            groupTitle='话题'
            rows={board.rows ?? []}
            actualChart={board.actualChart}
            achieveChart={board.achieveChart}
            compareSummary={board.compareSummary}
            persistedPeriodCount={board.persistedPeriodCount}
            onConfigureTarget={() => setTargetOpen(true)}
          />
        }
        owner={
          <YearlyBoardSlice
            groupTitle='负责人'
            rows={(board.ownerRows ?? []).map((r) => ({
              ...r,
              ownerName: r.ownerName || r.topicName,
            }))}
            actualChart={board.ownerActualChart ?? []}
            achieveChart={board.ownerAchieveChart ?? []}
            compareSummary={board.ownerCompareSummary}
            persistedPeriodCount={board.persistedPeriodCount}
          />
        }
      />
      <Card title='目标配置'>
        <Table
          rowKey='id'
          dataSource={targets}
          pagination={false}
          scroll={{ x: 'max-content' }}
          columns={[
            {
              title: '操作',
              width: 80,
              fixed: 'left',
              render: (_, r) => (
                <Popconfirm
                  title='确定删除该目标？'
                  onConfirm={async () => {
                    await deleteGeoYearTargetApi(r.id);
                    message.success('已删除');
                    loadTargets();
                    void loadBoard({ yearRange: defaultYears });
                  }}
                >
                  <Button
                    type='link'
                    size='small'
                    danger
                  >
                    删除
                  </Button>
                </Popconfirm>
              ),
            },
            { title: '时间段', dataIndex: 'periodLabel' },
            { title: '话题', dataIndex: 'topicId', render: (id) => topics.find((t) => t.id === id)?.topicName || id },
            { title: '开始', dataIndex: 'periodStart' },
            { title: '结束', dataIndex: 'periodEnd' },
            { title: '目标%', dataIndex: 'targetRate' },
          ]}
        />
      </Card>
      <BaseModalForm
        title='新增目标'
        open={targetOpen}
        onOpenChange={setTargetOpen}
        onFinish={async (values) => {
          await saveGeoYearTargetApi({
            ...values,
            periodStart: values.periodStart ? dayjs(values.periodStart).format('YYYY-MM-DD') : undefined,
            periodEnd: values.periodEnd ? dayjs(values.periodEnd).format('YYYY-MM-DD') : undefined,
          });
          message.success('已保存');
          loadTargets();
          void loadBoard({ yearRange: defaultYears });
          return true;
        }}
      >
        <ProFormText
          name='periodLabel'
          label='时间段名称'
          rules={[{ required: true }]}
          placeholder='如 全年 / 7-8月'
        />
        <ProFormDatePicker
          name='periodStart'
          label='区间开始'
        />
        <ProFormDatePicker
          name='periodEnd'
          label='区间结束'
        />
        <ProFormSelect
          name='topicId'
          label='话题'
          rules={[{ required: true }]}
          options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
        />
        <ProFormDigit
          name='targetRate'
          label='目标%'
          min={0}
          max={100}
          rules={[{ required: true }]}
          initialValue={80}
        />
      </BaseModalForm>
    </div>
  );
});

export default YearlyPage;
