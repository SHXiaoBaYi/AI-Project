import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Breadcrumb, Button, Card, Col, Drawer, Row, Table, message } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import { getGeoNegativeDailyApi, getGeoTopicPlatformChartsApi } from '@/api/geo';
import { BoardColumnScrollArea } from '@/components/geo/BoardColumnScrollArea';
import { bindBoardColumnDrill, buildBoardDrillLookup } from '@/components/geo/boardChartDrill';
import { boardColumnChartProps } from '@/components/geo/boardColumnChartProps';
import type { GeoBoardQuery, GeoChartPoint, GeoDailyVO, GeoTopicPlatformCharts } from '@/types/geo';
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

type MetricKind = 'rank' | 'sample' | 'negative' | 'firstRecommend' | 'top3Recommend';
type DrillLevel = 'topic' | 'platform' | 'keyword';

function pickChart(data: GeoTopicPlatformCharts | null, metric: MetricKind): GeoChartPoint[] {
  if (!data) return [];
  if (metric === 'rank') return data.rankChart || [];
  if (metric === 'sample') return data.sampleChart || [];
  if (metric === 'negative') return data.negativeChart || [];
  if (metric === 'firstRecommend') return data.firstRecommendChart || [];
  return data.top3RecommendChart || [];
}

function seriesHint(metric: MetricKind, level: DrillLevel): string {
  if (metric === 'sample') {
    if (level === 'topic') return '横轴=日期，系列=话题；同一问题跨两平台计 2；点击下钻各平台';
    if (level === 'platform') return '横轴=日期，系列=平台；点击查看该平台测了哪些问题';
    return '横轴=日期，系列=测试问题（已到最细）';
  }
  if (metric === 'negative') {
    if (level === 'topic') return '横轴=日期，系列=话题；负面问题总数（跨平台计 2）；点击下钻各平台';
    if (level === 'platform') return '横轴=日期，系列=平台；负面出现次数；点击查看负面内容';
    return '横轴=日期，系列=负面/错误内容；可点击查看明细';
  }
  if (metric === 'firstRecommend' || metric === 'top3Recommend') {
    const name = metric === 'firstRecommend' ? '首位' : '前三位';
    if (level === 'topic') return `横轴=日期，系列=话题；平均${name}推荐率=各平台率均值；点击下钻各平台`;
    if (level === 'platform') return `横轴=日期，系列=平台；${name}推荐率=推荐次数÷提及次数；点击下钻测试词`;
    return `横轴=日期，系列=测试词；${name}推荐率=推荐次数÷提及次数`;
  }
  if (level === 'topic') return '横轴=日期，系列=话题（全平台平均排名）；点击下钻到各平台';
  if (level === 'platform') return '横轴=日期，系列=平台；点击下钻到各测试词';
  return '横轴=日期，系列=测试词；平均排名=提及排名之和÷提及次数';
}

function IndependentTofuCard({
  title,
  hint,
  metric,
  grain,
  range,
  enableNegativeDetail,
}: {
  title: string;
  hint: string;
  metric: MetricKind;
  grain: DemoBoardGrain;
  range: [Dayjs, Dayjs];
  enableNegativeDetail?: boolean;
}) {
  const [level, setLevel] = useState<DrillLevel>('topic');
  const [topicId, setTopicId] = useState<number>();
  const [topicName, setTopicName] = useState<string>();
  const [platform, setPlatform] = useState<string>();
  const [charts, setCharts] = useState<GeoChartPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerTitle, setDrawerTitle] = useState('');
  const [negatives, setNegatives] = useState<GeoDailyVO[]>([]);
  const [negLoading, setNegLoading] = useState(false);

  const load = useCallback(
    async (next: { topicId?: number; topicName?: string; platform?: string }) => {
      setLoading(true);
      try {
        const query: GeoBoardQuery = {
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          grain,
          chartMetric: metric,
          topicId: next.topicId,
          platform: next.platform,
        };
        const data = await getGeoTopicPlatformChartsApi(query);
        setLevel(((data?.level as DrillLevel) || 'topic') as DrillLevel);
        setTopicId(data?.topicId ?? next.topicId);
        setTopicName(data?.topicName || next.topicName);
        setPlatform(data?.platform || next.platform);
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
    setPlatform(undefined);
    void load({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grain, range]);

  const openNegativeDrawer = useCallback(
    async (contentOrPlatform: string) => {
      if (!topicId || !platform) return;
      setDrawerTitle(`负面明细 · ${topicName || ''} · ${platform}`);
      setDrawerOpen(true);
      setNegLoading(true);
      try {
        const rows = await getGeoNegativeDailyApi({
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          topicId,
          platforms: [platform],
        });
        const filtered = (rows || []).filter((r) => {
          if (!contentOrPlatform) return true;
          return (r.negativeContent || '').includes(contentOrPlatform) || r.negativeContent === contentOrPlatform;
        });
        setNegatives(filtered.length ? filtered : rows || []);
      } catch (e: any) {
        message.error(e?.message || '加载负面明细失败');
        setNegatives([]);
      } finally {
        setNegLoading(false);
      }
    },
    [platform, range, topicId, topicName],
  );

  const onSeriesClick = useCallback(
    (series?: string, drillKey?: string) => {
      const key = drillKey || series;
      if (!key) return;
      if (level === 'topic') {
        let id = Number(key);
        let name = series || key;
        if (!Number.isFinite(id) || id <= 0) {
          const hit = charts.find((p) => p.series === series || p.series === key || p.key === key);
          id = Number(hit?.key);
          name = hit?.series || name;
        }
        if (!Number.isFinite(id) || id <= 0) {
          message.warning('无法识别话题');
          return;
        }
        void load({ topicId: id, topicName: name });
        return;
      }
      if (level === 'platform') {
        void load({ topicId, topicName, platform: key });
        return;
      }
      if (level === 'keyword' && enableNegativeDetail) {
        void openNegativeDrawer(series || key);
      }
    },
    [charts, enableNegativeDetail, level, load, openNegativeDrawer, topicId, topicName],
  );

  const drillRef = useRef(onSeriesClick);
  drillRef.current = onSeriesClick;
  const drillLookupRef = useRef(new Map<string, string>());

  const bindChartClick = useCallback(
    (plot: { chart?: { on?: (event: string, handler: (evt: unknown) => void) => void } }) => {
      bindBoardColumnDrill(
        plot,
        (hit) => {
          drillRef.current(hit.series, hit.drillKey);
        },
        drillLookupRef,
      );
    },
    [],
  );

  const onBack = () => {
    if (level === 'keyword') {
      void load({ topicId, topicName, platform: undefined });
      return;
    }
    if (level === 'platform') {
      void load({});
    }
  };

  const chartData = (charts || []).map((p) => ({
    axis: formatAxis(p.axis),
    series: p.series,
    fullSeries: p.series,
    value: p.value,
    drillKey: p.key ?? p.series,
    key: p.key ?? p.series,
  }));
  drillLookupRef.current = buildBoardDrillLookup(chartData);

  const negColumns: ColumnsType<GeoDailyVO> = [
    { title: '日期', dataIndex: 'inspectDate', width: 110 },
    { title: '平台', dataIndex: 'platform', width: 100 },
    { title: '测试问题', dataIndex: 'keyword', ellipsis: true },
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
          ...(platform ? [{ title: platform }] : []),
          ...(level === 'keyword'
            ? [{ title: metric === 'negative' ? '负面内容' : metric === 'sample' ? '测试问题' : '测试词' }]
            : []),
        ]}
      />
      <div className='mb-2 text-xs text-neutral-400'>{hint}</div>
      <Suspense fallback={<ChartFallback />}>
        <BoardColumnScrollArea
          data={chartData}
          onSeriesDrill={(name) => {
            const key = drillLookupRef.current.get(name) || name;
            onSeriesClick(name, key);
          }}
        >
          <Column
            key={`${metric}-${level}-${topicId || 'root'}-${platform || ''}-${chartData.length}`}
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
            {...boardColumnChartProps}
            onReady={bindChartClick}
          />
        </BoardColumnScrollArea>
      </Suspense>
      <div className='mt-1 text-xs text-neutral-400'>{seriesHint(metric, level)}</div>

      <Drawer
        title={drawerTitle}
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

/** GEO：五个互相独立的豆腐块（共用父级日期筛选） */
export function GeoTopicPlatformTofuBoard({ grain, range }: { grain: DemoBoardGrain; range: [Dayjs, Dayjs] }) {
  return (
    <div className='flex flex-col gap-3'>
      <div className='text-xs text-neutral-400'>
        统一下钻：话题 → AI平台 → 测试词/问题/负面内容；点柱或双击图例均可下钻；露出率类话题层取各平台率算术平均
      </div>
      <Row gutter={[12, 12]}>
        <Col
          xs={24}
          lg={8}
        >
          <IndependentTofuCard
            title='露出排名'
            hint='纵轴=平均排名（越低越好；avg=提及排名之和÷提及次数）'
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
            title='首位推荐率'
            hint='纵轴=首位推荐率%（首位次数÷提及次数；话题层=各平台均值）'
            metric='firstRecommend'
            grain={grain}
            range={range}
          />
        </Col>
        <Col
          xs={24}
          lg={8}
        >
          <IndependentTofuCard
            title='前三位推荐率'
            hint='纵轴=前三位推荐率%（前三次数÷提及次数；话题层=各平台均值）'
            metric='top3Recommend'
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
            hint='纵轴=测试问题数（同一问题跨两平台计 2）'
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
            hint='纵轴=负面问题数；最细层可点开内容明细'
            metric='negative'
            grain={grain}
            range={range}
            enableNegativeDetail
          />
        </Col>
      </Row>
    </div>
  );
}
