import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Breadcrumb, Button, Card, Col, Drawer, Row, Table, message } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import {
  boardTaskTofuChartApi,
  boardTaskTofuPublishDetailApi,
  type BoardTaskPublishDetail,
  type BoardTaskTofuChartType,
  type BoardTaskTofuQuery,
} from '@/api/board';
import { BoardColumnScrollArea } from '@/components/geo/BoardColumnScrollArea';
import { boardColumnChartProps } from '@/components/geo/boardColumnChartProps';
import type { DemoBoardGrain } from '@/constants/demoData';

const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Column })));

function ChartFallback({ height = 220 }: { height?: number }) {
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

type DrillState = {
  topicId?: number;
  topicName?: string;
  targetQuestion?: string;
  publisherUserId?: number;
  publisherName?: string;
  contentPlatform?: string;
  aiPlatform?: string;
};

type ChartDef = {
  chartType: BoardTaskTofuChartType;
  title: string;
  hint: string;
  /** 发布数量：平台级点开明细 */
  publishDetail?: boolean;
};

const CHARTS: ChartDef[] = [
  {
    chartType: 'publishCount',
    title: '话题发布数量',
    hint: '话题 → 目标问题 → 员工 → 内容平台',
    publishDetail: true,
  },
  {
    chartType: 'citeRate',
    title: '话题被AI收录率',
    hint: '话题 → 目标问题 → 员工 → AI平台',
  },
  {
    chartType: 'employeeCiteCompare',
    title: '员工AI收录对比',
    hint: '员工 → AI平台',
  },
  {
    chartType: 'employeeCiteMom',
    title: '员工AI收录环比',
    hint: '员工 → AI平台 · 纵轴=百分点',
  },
  {
    chartType: 'employeeCiteYoy',
    title: '员工AI收录同比',
    hint: '员工 → AI平台 · 纵轴=百分点',
  },
  {
    chartType: 'topicCiteCount',
    title: '话题被AI平台引用数',
    hint: '话题 → 目标问题 → AI平台',
  },
];

function IndependentTaskTofuCard({
  def,
  grain,
  range,
}: {
  def: ChartDef;
  grain: DemoBoardGrain;
  range: [Dayjs, Dayjs];
}) {
  const [level, setLevel] = useState('topic');
  const [drill, setDrill] = useState<DrillState>({});
  const [metricLabel, setMetricLabel] = useState(def.title);
  const [charts, setCharts] = useState<{ axis: string; series: string; value: number; key?: string }[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerTitle, setDrawerTitle] = useState('');
  const [details, setDetails] = useState<BoardTaskPublishDetail[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  const load = useCallback(
    async (next: DrillState) => {
      setLoading(true);
      try {
        const query: BoardTaskTofuQuery = {
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          grain,
          chartType: def.chartType,
          topicId: next.topicId,
          targetQuestion: next.targetQuestion,
          publisherUserId: next.publisherUserId,
          publisherName: next.publisherName,
          contentPlatform: next.contentPlatform,
          aiPlatform: next.aiPlatform,
        };
        const data = await boardTaskTofuChartApi(query);
        setLevel(data?.level || 'topic');
        setMetricLabel(data?.metricLabel || def.title);
        setDrill({
          topicId: data?.topicId ?? next.topicId,
          topicName: data?.topicName || next.topicName,
          targetQuestion: data?.targetQuestion || next.targetQuestion,
          publisherUserId: data?.publisherUserId ?? next.publisherUserId,
          publisherName: data?.publisherName || next.publisherName,
          contentPlatform: data?.contentPlatform || next.contentPlatform,
          aiPlatform: data?.aiPlatform || next.aiPlatform,
        });
        setCharts(data?.chart || []);
      } catch (e: any) {
        message.error(e?.message || `${def.title}加载失败`);
      } finally {
        setLoading(false);
      }
    },
    [def.chartType, def.title, grain, range],
  );

  useEffect(() => {
    setDrill({});
    void load({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grain, range]);

  const openPublishDrawer = useCallback(
    async (platform: string, state: DrillState) => {
      setDrawerTitle(`发布明细 · ${platform}`);
      setDrawerOpen(true);
      setDetailLoading(true);
      try {
        const rows = await boardTaskTofuPublishDetailApi({
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          grain,
          chartType: 'publishCount',
          topicId: state.topicId,
          targetQuestion: state.targetQuestion,
          publisherUserId: state.publisherUserId,
          publisherName: state.publisherName,
          contentPlatform: platform,
        });
        setDetails(rows || []);
      } catch (e: any) {
        message.error(e?.message || '加载发布明细失败');
        setDetails([]);
      } finally {
        setDetailLoading(false);
      }
    },
    [grain, range],
  );

  const onSeriesClick = useCallback(
    (series?: string, drillKey?: string) => {
      const key = drillKey || series;
      if (!key) return;
      if (level === 'topic') {
        const id = Number(key);
        if (!Number.isFinite(id)) {
          message.warning('无法识别话题');
          return;
        }
        void load({ topicId: id, topicName: series || key });
        return;
      }
      if (level === 'question') {
        void load({ ...drill, targetQuestion: key });
        return;
      }
      if (level === 'employee') {
        const uid = key.startsWith('n:') ? undefined : Number(key);
        void load({
          ...drill,
          publisherUserId: Number.isFinite(uid as number) ? (uid as number) : undefined,
          publisherName: series || (key.startsWith('n:') ? key.slice(2) : key),
        });
        return;
      }
      if (level === 'contentPlatform' && def.publishDetail) {
        void openPublishDrawer(key, drill);
        return;
      }
      // aiPlatform leaf: no further drill
    },
    [def.publishDetail, drill, level, load, openPublishDrawer],
  );

  const clickRef = useRef(onSeriesClick);
  clickRef.current = onSeriesClick;

  const bindChartClick = useCallback(
    (plot: { chart?: { on: (event: string, handler: (evt: any) => void) => void } }) => {
      const c = plot?.chart;
      if (!c?.on) return;
      c.on('element:click', (evt: any) => {
        const raw = evt?.data?.data ?? evt?.data ?? {};
        const datum = (Array.isArray(raw) ? raw[0] : raw) as Record<string, unknown> | undefined;
        if (!datum) return;
        const series = datum.fullSeries != null ? String(datum.fullSeries) : String(datum.series ?? '');
        const key = datum.drillKey != null ? String(datum.drillKey) : undefined;
        clickRef.current(series, key);
      });
    },
    [],
  );

  const onBack = () => {
    if (
      level === 'contentPlatform' ||
      (level === 'aiPlatform' && (drill.publisherUserId != null || !!drill.publisherName))
    ) {
      void load({
        topicId: drill.topicId,
        topicName: drill.topicName,
        targetQuestion: drill.targetQuestion,
        publisherUserId: undefined,
        publisherName: undefined,
      });
      return;
    }
    if (level === 'aiPlatform' && drill.targetQuestion) {
      void load({
        topicId: drill.topicId,
        topicName: drill.topicName,
        targetQuestion: drill.targetQuestion,
      });
      return;
    }
    if (level === 'employee') {
      void load({ topicId: drill.topicId, topicName: drill.topicName, targetQuestion: undefined });
      return;
    }
    if (level === 'question') {
      void load({});
      return;
    }
    if (level === 'aiPlatform') {
      void load({});
    }
  };

  // 系列必须用完整原文；截断会把目标问题前缀撞名合并成少量柱
  const chartData = charts.map((p) => ({
    axis: formatAxis(p.axis),
    series: p.series,
    fullSeries: p.series,
    value: p.value,
    drillKey: p.key,
  }));

  const isEmployeeRootChart = def.chartType.startsWith('employee');
  const showBack =
    level === 'question' ||
    (level === 'employee' && !isEmployeeRootChart) ||
    level === 'contentPlatform' ||
    level === 'aiPlatform';

  const crumbs = [
    { title: def.chartType.startsWith('employee') ? '员工' : '话题' },
    ...(drill.topicName ? [{ title: drill.topicName }] : []),
    ...(drill.targetQuestion
      ? [{ title: drill.targetQuestion.length > 14 ? `${drill.targetQuestion.slice(0, 13)}…` : drill.targetQuestion }]
      : []),
    ...(drill.publisherName ? [{ title: drill.publisherName }] : []),
    ...(level === 'contentPlatform' ? [{ title: '内容平台' }] : []),
    ...(level === 'aiPlatform' ? [{ title: 'AI平台' }] : []),
  ];

  const detailColumns: ColumnsType<BoardTaskPublishDetail> = [
    { title: '发布时间', dataIndex: 'publishTime', width: 110 },
    { title: '话题', dataIndex: 'topicName', width: 100 },
    { title: '目标问题', dataIndex: 'targetQuestion', ellipsis: true },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '发布人', dataIndex: 'publisherName', width: 90 },
    { title: '平台', dataIndex: 'publishPlatform', width: 90 },
    { title: '状态', dataIndex: 'publishStatus', width: 90 },
    {
      title: '链接',
      dataIndex: 'publishUrl',
      ellipsis: true,
      render: (v) =>
        v ? (
          <a
            href={v}
            target='_blank'
            rel='noreferrer'
          >
            打开
          </a>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <Card
      size='small'
      className='h-full'
      loading={loading}
      title={
        <div className='flex flex-wrap items-center gap-2'>
          <span className='font-medium'>{def.title}</span>
          {showBack ? (
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
        items={crumbs}
      />
      <div className='mb-2 text-xs text-neutral-400'>
        {def.hint} · {metricLabel} · 横轴=日期
      </div>
      <Suspense fallback={<ChartFallback />}>
        <BoardColumnScrollArea data={chartData}>
          <Column
            key={`${def.chartType}-${level}-${chartData.length}-${drill.topicId || ''}-${drill.targetQuestion || ''}`}
            data={chartData}
            xField='axis'
            yField='value'
            colorField='series'
            height={220}
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
      <div className='mt-1 text-xs text-neutral-400'>
        {level === 'contentPlatform' && def.publishDetail
          ? '点击柱体查看该内容平台发布明细'
          : level === 'aiPlatform'
            ? '已到最细层级'
            : '点击柱体下钻下一级'}
      </div>

      <Drawer
        title={drawerTitle}
        width={860}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnClose
      >
        <Table
          size='small'
          loading={detailLoading}
          rowKey={(r) => `${r.itemId || r.placementId}-${r.publishPlatform}`}
          dataSource={details}
          columns={detailColumns}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          scroll={{ x: 'max-content' }}
        />
      </Drawer>
    </Card>
  );
}

/** 员工收录看板：六个互相独立的分组柱状豆腐块 */
export function TaskTofuBoard({ grain, range }: { grain: DemoBoardGrain; range: [Dayjs, Dayjs] }) {
  return (
    <div className='flex flex-col gap-3'>
      <div className='text-xs text-neutral-400'>六个看板相互独立；页顶日期筛选统一生效；点击柱体下钻</div>
      <Row gutter={[12, 12]}>
        {CHARTS.map((def) => (
          <Col
            key={def.chartType}
            xs={24}
            lg={12}
            xl={8}
          >
            <IndependentTaskTofuCard
              def={def}
              grain={grain}
              range={range}
            />
          </Col>
        ))}
      </Row>
    </div>
  );
}
