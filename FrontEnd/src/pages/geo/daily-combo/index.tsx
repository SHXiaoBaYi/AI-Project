import { lazy, memo, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Card, Drawer, Space, Table, Tag } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import GeoScreenshot from '@/components/geo/GeoScreenshot';
import { ExternalLinkText } from '@/components/ExternalLinkDrawer';
import {
  getGeoDailyComboDetailApi,
  getGeoDailyComboListApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
} from '@/api/geo';
import type { GeoDailyComboDetail, GeoDailyComboVO, GeoDailyVO, GeoTopic } from '@/types/geo';
import { demoRangeByGrain } from '@/constants/demoData';

const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Line })));

function ChartFallback({ height = 260 }: { height?: number }) {
  return (
    <div
      className='flex items-center justify-center text-neutral-400'
      style={{ height }}
    />
  );
}

type LinePoint = { axis: string; value: number; series: string };

const DailyComboPage = memo(function DailyComboPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>(() => {
    const [start, end] = demoRangeByGrain('month');
    return [start, end];
  });
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<GeoDailyComboDetail | null>(null);
  const [active, setActive] = useState<GeoDailyComboVO | null>(null);

  useEffect(() => {
    void getGeoTopicOptionsApi().then(setTopics);
    void getGeoPlatformsApi().then(setPlatforms);
  }, []);

  const openDetail = useCallback(
    async (row: GeoDailyComboVO) => {
      setActive(row);
      setDetailOpen(true);
      setDetailLoading(true);
      try {
        const data = await getGeoDailyComboDetailApi({
          topicId: row.topicId,
          keywordExact: row.keyword,
          platform: row.platform,
          startDate: dateRange[0].format('YYYY-MM-DD'),
          endDate: dateRange[1].format('YYYY-MM-DD'),
        });
        setDetail(data);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '加载明细失败');
        setDetail(null);
      } finally {
        setDetailLoading(false);
      }
    },
    [dateRange, message],
  );

  const mentionLine: LinePoint[] = useMemo(() => {
    const series = detail?.series ?? [];
    return series.map((p) => ({
      axis: p.dateLabel || p.inspectDate,
      value: p.mentioned === 1 ? 1 : 0,
      series: '露出',
    }));
  }, [detail]);

  const rankLine: LinePoint[] = useMemo(() => {
    const series = detail?.series ?? [];
    return series
      .filter((p) => p.rankNo != null)
      .map((p) => ({
        axis: p.dateLabel || p.inspectDate,
        value: Number(p.rankNo),
        series: '排名',
      }));
  }, [detail]);

  const columns: ProColumnType<GeoDailyComboVO>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 100,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'detail',
                label: '明细',
                onClick: () => void openDetail(record),
              },
            ]}
          />
        ),
      },
      {
        title: '日期范围',
        dataIndex: 'dateRange',
        valueType: 'dateRange',
        hideInTable: true,
        initialValue: dateRange,
        fieldProps: {
          allowClear: false,
          onChange: (value: [Dayjs, Dayjs] | null) => {
            if (value?.[0] && value?.[1]) {
              setDateRange([value[0].startOf('day'), value[1].endOf('day')]);
            }
          },
        },
        search: {
          transform: (value) => ({
            startDate: value?.[0] ? dayjs(value[0]).format('YYYY-MM-DD') : undefined,
            endDate: value?.[1] ? dayjs(value[1]).format('YYYY-MM-DD') : undefined,
          }),
        },
      },
      {
        title: '话题',
        dataIndex: 'topicId',
        width: 140,
        valueType: 'select',
        fieldProps: {
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
          options: topics.map((t) => ({ label: t.topicName, value: t.id })),
        },
        render: (_, r) => r.topicName || '—',
      },
      {
        title: '关键字',
        dataIndex: 'keyword',
        ellipsis: true,
        width: 260,
        fieldProps: { placeholder: '关键字模糊搜索' },
      },
      {
        title: '平台',
        dataIndex: 'platforms',
        width: 120,
        valueType: 'select',
        fieldProps: {
          mode: 'multiple',
          maxTagCount: 'responsive',
          allowClear: true,
          options: platforms.map((p) => ({ label: p, value: p })),
        },
        search: {
          transform: (value) => ({ platforms: value }),
        },
        render: (_, r) => r.platform,
      },
      {
        title: '监测天数',
        dataIndex: 'dayCount',
        width: 90,
        search: false,
      },
      {
        title: '露出天数',
        dataIndex: 'mentionCount',
        width: 90,
        search: false,
      },
      {
        title: '负面天数',
        dataIndex: 'negativeCount',
        width: 90,
        search: false,
      },
      {
        title: '平均排名',
        dataIndex: 'avgRank',
        width: 90,
        search: false,
        render: (_, r) => (r.avgRank == null ? '—' : r.avgRank),
      },
      {
        title: '首/末日',
        dataIndex: 'firstDate',
        width: 180,
        search: false,
        render: (_, r) => `${r.firstDate || '—'} ~ ${r.lastDate || '—'}`,
      },
      {
        title: '最近露出',
        dataIndex: 'latestMentioned',
        width: 90,
        search: false,
        render: (_, r) => (r.latestMentioned === 1 ? <Tag color='success'>是</Tag> : <Tag>否</Tag>),
      },
      {
        title: '最近排名',
        dataIndex: 'latestRankNo',
        width: 90,
        search: false,
        render: (_, r) => r.latestRankNo ?? '—',
      },
      {
        title: '最近推荐',
        dataIndex: 'latestRecommendStatus',
        width: 120,
        search: false,
        ellipsis: true,
        render: (_, r) => r.latestRecommendStatus || '—',
      },
    ],
    [topics, platforms, dateRange, openDetail],
  );

  const dayColumns = useMemo(
    () => [
      { title: '巡查日期', dataIndex: 'inspectDate', width: 120 },
      {
        title: '露出',
        dataIndex: 'mentioned',
        width: 80,
        render: (v: number) => (v === 1 ? <Tag color='success'>是</Tag> : <Tag>否</Tag>),
      },
      { title: '排名', dataIndex: 'rankNo', width: 80, render: (v: number | undefined) => v ?? '—' },
      { title: '推荐状态', dataIndex: 'recommendStatus', width: 120, ellipsis: true },
      {
        title: '负面/错误',
        dataIndex: 'negativeContent',
        ellipsis: true,
        render: (v: string | undefined) => v || '—',
      },
      {
        title: '截图',
        dataIndex: 'screenshotUrl',
        width: 80,
        render: (_: unknown, r: GeoDailyVO) => (
          <GeoScreenshot
            src={r.screenshotUrl}
            trigger='link'
          />
        ),
      },
      {
        title: '第三方链接',
        dataIndex: 'thirdPartyUrl',
        width: 160,
        ellipsis: true,
        render: (_: unknown, r: GeoDailyVO) => (
          <ExternalLinkText
            href={r.thirdPartyUrl}
            drawerTitle='第三方页面'
          />
        ),
      },
      { title: '竞品', dataIndex: 'competitors', ellipsis: true, render: (v: string | undefined) => v || '—' },
      { title: '话题类型', dataIndex: 'termType', width: 90, render: (v: string | undefined) => v || '日巡查' },
    ],
    [],
  );

  return (
    <>
      <BaseProTable<GeoDailyComboVO>
        rowKey={(r) => `${r.topicId}|${r.keyword}|${r.platform}`}
        actionRef={actionRef}
        columns={columns}
        headerTitle='话题 × 关键字 × 平台'
        params={{
          _range: `${dateRange[0].format('YYYY-MM-DD')}_${dateRange[1].format('YYYY-MM-DD')}`,
        }}
        request={async (params) => {
          const startDate = (params.startDate as string | undefined) || dateRange[0].format('YYYY-MM-DD');
          const endDate = (params.endDate as string | undefined) || dateRange[1].format('YYYY-MM-DD');
          const res = await getGeoDailyComboListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            startDate,
            endDate,
            topicId: params.topicId as number | undefined,
            keyword: params.keyword ? String(params.keyword) : undefined,
            platforms: params.platforms as string[] | undefined,
          });
          return { data: res?.rows ?? [], success: true, total: res?.total ?? 0 };
        }}
      />

      <Drawer
        title={active ? `${active.topicName} · ${active.keyword} · ${active.platform}` : '日期明细'}
        width={960}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setDetail(null);
          setActive(null);
        }}
        destroyOnClose
      >
        <Space
          direction='vertical'
          size={16}
          className='w-full'
        >
          <Card
            size='small'
            title='露出趋势（按日，1=露出）'
            loading={detailLoading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Line
                data={mentionLine}
                xField='axis'
                yField='value'
                colorField='series'
                height={260}
                meta={{ value: { min: 0, max: 1 } }}
              />
            </Suspense>
          </Card>
          <Card
            size='small'
            title='排名趋势（按日，数值越小越好）'
            loading={detailLoading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Line
                data={rankLine}
                xField='axis'
                yField='value'
                colorField='series'
                height={260}
              />
            </Suspense>
          </Card>
          <Card
            size='small'
            title='日期明细'
            loading={detailLoading}
          >
            <Table<GeoDailyVO>
              rowKey='id'
              size='small'
              pagination={{ pageSize: 10 }}
              scroll={{ x: 1100 }}
              dataSource={detail?.days ?? []}
              columns={dayColumns as never}
            />
          </Card>
        </Space>
      </Drawer>
    </>
  );
});

export default DailyComboPage;
