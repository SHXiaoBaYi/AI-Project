import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Breadcrumb, Button, Card, Col, Drawer, Row, Table, message } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { getGeoNegativeDailyApi, getGeoTopicPlatformChartsApi } from '@/api/geo';
import type { GeoBoardQuery, GeoChartPoint, GeoDailyVO } from '@/types/geo';
import type { DemoBoardGrain } from '@/constants/demoData';

const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Column })));

function ChartFallback({ height = 240 }: { height?: number }) {
  return (
    <div
      className='flex items-center justify-center text-sm text-neutral-400'
      style={{ height }}
    >
      图表加载中…
    </div>
  );
}

function formatAxis(raw: string): string {
  if (!raw) return '-';
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (day) return `${day[1].slice(2)}/${day[2]}/${day[3]}`;
  const mon = /^(\d{4})-(\d{2})$/.exec(raw);
  if (mon) return `${mon[1].slice(2)}/${mon[2]}`;
  const week = /^(\d{4})-W(\d{2})$/i.exec(raw);
  if (week) return `${week[1].slice(2)}/W${week[2]}`;
  if (/^\d{4}$/.test(raw)) return raw.slice(2);
  return raw;
}

type MetricKind = 'rank' | 'sample' | 'negative';
type DrillLevel = 'topic' | 'question' | 'platform';

function pickChart(
  data: { rankChart?: GeoChartPoint[]; sampleChart?: GeoChartPoint[]; negativeChart?: GeoChartPoint[] } | null,
  metric: MetricKind,
): GeoChartPoint[] {
  if (!data) return [];
  if (metric === 'rank') return data.rankChart || [];
  if (metric === 'sample') return data.sampleChart || [];
  return data.negativeChart || [];
}

function seriesHint(level: DrillLevel, isNegative: boolean): string {
  if (level === 'topic') return '横轴=日期，系列=话题；点击柱体下钻到目标问题';
  if (level === 'question') return '横轴=日期，系列=目标问题；点击柱体下钻到各平台';
  if (isNegative) return '横轴=日期，系列=平台；点击柱体查看该平台负面/错误明细';
  return '横轴=日期，系列=平台（已到最细层级）';
}

/** 单个独立豆腐块看板：话题 → 目标问题 → 平台 */
function IndependentTofuCard({
  title,
  hint,
  metric,
  grain,
  range,
  enablePlatformDetail,
}: {
  title: string;
  hint: string;
  metric: MetricKind;
  grain: DemoBoardGrain;
  range: [Dayjs, Dayjs];
  /** 负面看板：平台级点击打开明细 */
  enablePlatformDetail?: boolean;
}) {
  const [level, setLevel] = useState<DrillLevel>('topic');
  const [topicId, setTopicId] = useState<number>();
  const [topicName, setTopicName] = useState<string>();
  const [keyword, setKeyword] = useState<string>();
  const [charts, setCharts] = useState<GeoChartPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerPlatform, setDrawerPlatform] = useState<string>();
  const [negatives, setNegatives] = useState<GeoDailyVO[]>([]);
  const [negLoading, setNegLoading] = useState(false);

  const load = useCallback(
    async (next: { topicId?: number; topicName?: string; keyword?: string }) => {
      setLoading(true);
      try {
        const query: GeoBoardQuery = {
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          grain,
          topicId: next.topicId,
          keyword: next.keyword,
        };
        const data = await getGeoTopicPlatformChartsApi(query);
        const nextLevel = (data?.level as DrillLevel) || 'topic';
        setLevel(nextLevel);
        setTopicId(data?.topicId ?? next.topicId);
        setTopicName(data?.topicName || next.topicName);
        setKeyword(data?.keyword || next.keyword);
        setCharts(pickChart(data, metric));
      } catch (e: any) {
        message.error(e?.message || `${title}加载失败`);
      } finally {
        setLoading(false);
      }
    },
    [grain, range, metric, title],
  );

  useEffect(() => {
    setLevel('topic');
    setTopicId(undefined);
    setTopicName(undefined);
    setKeyword(undefined);
    void load({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grain, range]);

  const openPlatformDrawer = useCallback(
    async (platform: string) => {
      if (!topicId || !keyword) return;
      setDrawerPlatform(platform);
      setDrawerOpen(true);
      setNegLoading(true);
      try {
        const rows = await getGeoNegativeDailyApi({
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          topicId,
          keyword,
          platforms: [platform],
        });
        setNegatives(rows || []);
      } catch (e: any) {
        message.error(e?.message || '加载负面明细失败');
        setNegatives([]);
      } finally {
        setNegLoading(false);
      }
    },
    [keyword, range, topicId],
  );

  const onSeriesClick = useCallback(
    (series?: string, drillKey?: string) => {
      const key = drillKey || series;
      if (!key) return;
      if (level === 'topic') {
        const id = Number(key);
        if (!Number.isFinite(id) || id <= 0) {
          message.warning('无法识别话题');
          return;
        }
        void load({ topicId: id, topicName: series || key });
        return;
      }
      if (level === 'question') {
        void load({ topicId, topicName, keyword: key });
        return;
      }
      if (level === 'platform' && enablePlatformDetail) {
        void openPlatformDrawer(key);
      }
    },
    [enablePlatformDetail, level, load, openPlatformDrawer, topicId, topicName],
  );

  const drillRef = useRef(onSeriesClick);
  drillRef.current = onSeriesClick;

  const bindChartClick = useCallback(
    (plot: { chart?: { on: (event: string, handler: (evt: any) => void) => void } }) => {
      const c = plot?.chart;
      if (!c?.on) return;
      c.on('element:click', (evt: any) => {
        const raw = evt?.data?.data ?? evt?.data ?? {};
        const datum = (Array.isArray(raw) ? raw[0] : raw) as Record<string, unknown> | undefined;
        if (!datum) return;
        const series = datum.fullSeries != null ? String(datum.fullSeries) : String(datum.series ?? '');
        const drillKey = datum.drillKey != null ? String(datum.drillKey) : undefined;
        drillRef.current(series, drillKey);
      });
    },
    [],
  );

  const onBack = () => {
    if (level === 'platform') {
      void load({ topicId, topicName, keyword: undefined });
      return;
    }
    if (level === 'question') {
      void load({});
    }
  };

  const shortSeries = (v: string) => (v && v.length > 12 ? `${v.slice(0, 11)}…` : v);

  const chartData = (charts || []).map((p) => ({
    axis: formatAxis(p.axis),
    series: shortSeries(p.series),
    fullSeries: p.series,
    value: p.value,
    drillKey: p.key,
  }));

  const negColumns: ColumnsType<GeoDailyVO> = [
    { title: '日期', dataIndex: 'inspectDate', width: 110 },
    { title: '平台', dataIndex: 'platform', width: 100 },
    { title: '目标问题', dataIndex: 'keyword', ellipsis: true },
    { title: '排名', dataIndex: 'rankNo', width: 70, render: (v) => v ?? '-' },
    { title: '负面/错误内容', dataIndex: 'negativeContent', ellipsis: true },
  ];

  return (
    <Card
      size='small'
      className='h-full'
      loading={loading}
      title={
        <div className='flex flex-wrap items-center gap-2'>
          <span className='font-medium'>{title}</span>
          {level !== 'topic' ? (
            <Button
              type='link'
              size='small'
              icon={<ArrowLeftOutlined />}
              onClick={onBack}
            >
              返回上一级
            </Button>
          ) : null}
        </div>
      }
    >
      <Breadcrumb
        className='mb-2 text-xs'
        items={[
          { title: '话题' },
          ...(topicName ? [{ title: topicName }] : []),
          ...(keyword ? [{ title: keyword.length > 16 ? `${keyword.slice(0, 15)}…` : keyword }] : []),
          ...(level === 'platform' ? [{ title: '各平台' }] : []),
        ]}
      />
      <div className='mb-2 text-xs text-neutral-400'>{hint}</div>
      <Suspense fallback={<ChartFallback />}>
        <Column
          key={`${metric}-${level}-${topicId || 'root'}-${keyword || ''}-${chartData.length}`}
          data={chartData}
          xField='axis'
          yField='value'
          colorField='series'
          height={240}
          group
          stack={false}
          legend={{ position: 'top' }}
          axis={{
            x: {
              labelTransform: 'rotate(28)',
              labelFontSize: 10,
              labelAutoHide: false,
              labelAutoRotate: false,
            },
          }}
          onReady={bindChartClick}
        />
      </Suspense>
      <div className='mt-1 text-xs text-neutral-400'>{seriesHint(level, !!enablePlatformDetail)}</div>

      <Drawer
        title={`负面/错误明细 · ${keyword || ''} · ${drawerPlatform || ''}`}
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        <Table
          size='small'
          loading={negLoading}
          rowKey={(r) => String(r.id)}
          dataSource={negatives}
          columns={negColumns}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          scroll={{ x: 'max-content' }}
        />
      </Drawer>
    </Card>
  );
}

/** GEO：三个互相独立的豆腐块（共用父级日期筛选） */
export function GeoTopicPlatformTofuBoard({ grain, range }: { grain: DemoBoardGrain; range: [Dayjs, Dayjs] }) {
  return (
    <div className='flex flex-col gap-3'>
      <div className='text-xs text-neutral-400'>三个看板相互独立：首屏=各话题 → 点击下钻目标问题 → 再点击看各平台</div>
      <Row gutter={[12, 12]}>
        <Col
          xs={24}
          lg={8}
        >
          <IndependentTofuCard
            title='露出排名'
            hint='纵轴=平均排名（越低越好）'
            metric='rank'
            grain={grain}
            range={range}
          />
        </Col>
        <Col
          xs={24}
          lg={8}
        >
          <IndependentTofuCard
            title='测试问题数'
            hint='纵轴=测试问题数量'
            metric='sample'
            grain={grain}
            range={range}
          />
        </Col>
        <Col
          xs={24}
          lg={8}
        >
          <IndependentTofuCard
            title='负面/错误'
            hint='纵轴=负面条数；平台级可点开明细'
            metric='negative'
            grain={grain}
            range={range}
            enablePlatformDetail
          />
        </Col>
      </Row>
    </div>
  );
}
