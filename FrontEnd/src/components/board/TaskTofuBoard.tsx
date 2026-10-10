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
  writerUserId?: number;
  writerName?: string;
  contentPlatform?: string;
  aiPlatform?: string;
};

type ChartDef = {
  chartType: BoardTaskTofuChartType;
  title: string;
  hint: string;
  /** 发布数量：平台级点开明细 */
  publishDetail?: boolean;
  /** 环比/同比可为负，只允许点表格下钻，不响应柱体点击 */
  tableDrill?: boolean;
  /** 员工收录：按撰写人下钻 */
  writerDrill?: boolean;
  /** 被引用内容发布平台：平台 → 话题 */
  contentPlatformThenTopic?: boolean;
};

type CiteTableRow = {
  series: string;
  drillKey: string;
  [axis: string]: string | number | undefined;
};

function formatSigned(value: unknown) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '-';
  const rounded = Math.round(value * 10) / 10;
  const text = Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
  return `${rounded > 0 ? `+${text}` : text}%`;
}

function buildCiteTable(charts: { axis: string; series: string; value: number; key?: string }[]) {
  const axes: string[] = [];
  const seen = new Set<string>();
  const rows = new Map<string, CiteTableRow>();
  for (const point of charts) {
    if (!seen.has(point.axis)) {
      seen.add(point.axis);
      axes.push(point.axis);
    }
    const drillKey = point.key || point.series;
    let row = rows.get(drillKey);
    if (!row) {
      row = { series: point.series, drillKey };
      rows.set(drillKey, row);
    }
    row[point.axis] = point.value;
  }
  return { axes, rows: [...rows.values()] };
}

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
    hint: '第1层：各话题被收录文章/发布文章 · 第2层：各AI平台收录率',
  },
  {
    chartType: 'employeeCiteCompare',
    title: '员工AI收录对比',
    hint: '第1层：撰写人 被收录/产出 · 第2层：各AI平台收录率',
    writerDrill: true,
  },
  {
    chartType: 'employeeCiteMom',
    title: '员工AI收录环比',
    hint: '撰写人 → AI平台 · 百分点，可为负 · 点表格下钻',
    tableDrill: true,
    writerDrill: true,
  },
  {
    chartType: 'employeeCiteYoy',
    title: '员工AI收录同比',
    hint: '撰写人 → AI平台 · 百分点，可为负 · 点表格下钻',
    tableDrill: true,
    writerDrill: true,
  },
  {
    chartType: 'topicCiteCount',
    title: '被引用内容发布平台数',
    hint: '第1层：各内容发布平台被引用次数 · 第2层：该平台在各话题被引用次数',
    contentPlatformThenTopic: true,
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
          writerUserId: next.writerUserId,
          writerName: next.writerName,
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
          writerUserId: data?.writerUserId ?? next.writerUserId,
          writerName: data?.writerName || next.writerName,
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
        // 被引用内容发布平台看板：第2层已是话题，不再下钻
        if (def.contentPlatformThenTopic) {
          return;
        }
        const id = Number(key);
        if (!Number.isFinite(id)) {
          message.warning('无法识别话题');
          return;
        }
        void load({ ...drill, topicId: id, topicName: series || key });
        return;
      }
      if (level === 'question') {
        void load({ ...drill, targetQuestion: key });
        return;
      }
      if (level === 'writer') {
        const uid = key.startsWith('n:') ? undefined : Number(key);
        void load({
          ...drill,
          writerUserId: Number.isFinite(uid as number) ? (uid as number) : undefined,
          writerName: series || (key.startsWith('n:') ? key.slice(2) : key),
        });
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
      if (level === 'contentPlatform') {
        if (def.contentPlatformThenTopic) {
          void load({ ...drill, contentPlatform: key });
          return;
        }
        if (def.publishDetail) {
          void openPublishDrawer(key, drill);
        }
        return;
      }
      // aiPlatform leaf: no further drill
    },
    [def.contentPlatformThenTopic, def.publishDetail, drill, level, load, openPublishDrawer],
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
    // 被引用内容发布平台：话题层 → 回平台层
    if (def.contentPlatformThenTopic && level === 'topic' && drill.contentPlatform) {
      void load({});
      return;
    }
    // 员工收录：AI平台层 → 回撰写人层
    if (level === 'aiPlatform' && (drill.writerUserId != null || !!drill.writerName)) {
      void load({});
      return;
    }
    // 话题收录率：AI平台层 → 回话题层
    if (level === 'aiPlatform' && drill.topicId != null && def.chartType === 'citeRate') {
      void load({});
      return;
    }
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
  const citeTable = def.tableDrill ? buildCiteTable(charts) : null;

  const isEmployeeRootChart = def.writerDrill || def.chartType.startsWith('employee');
  const showBack =
    level === 'question' ||
    (level === 'employee' && !isEmployeeRootChart) ||
    (level === 'topic' && !!drill.contentPlatform && !!def.contentPlatformThenTopic) ||
    (level === 'contentPlatform' && !!def.publishDetail) ||
    level === 'aiPlatform';

  const crumbs = [
    {
      title: def.contentPlatformThenTopic
        ? '内容发布平台'
        : def.writerDrill || def.chartType.startsWith('employee')
          ? '撰写人'
          : '话题',
    },
    ...(drill.contentPlatform && def.contentPlatformThenTopic ? [{ title: drill.contentPlatform }] : []),
    ...(drill.topicName && !def.contentPlatformThenTopic ? [{ title: drill.topicName }] : []),
    ...(drill.topicName && def.contentPlatformThenTopic && level === 'topic' ? [{ title: '各话题' }] : []),
    ...(drill.targetQuestion
      ? [{ title: drill.targetQuestion.length > 14 ? `${drill.targetQuestion.slice(0, 13)}…` : drill.targetQuestion }]
      : []),
    ...(drill.publisherName ? [{ title: drill.publisherName }] : []),
    ...(drill.writerName ? [{ title: drill.writerName }] : []),
    ...(level === 'contentPlatform' && def.publishDetail ? [{ title: '内容平台' }] : []),
    ...(level === 'aiPlatform' ? [{ title: 'AI平台' }] : []),
  ];

  const detailColumns: ColumnsType<BoardTaskPublishDetail> = [
    { title: '发布时间', dataIndex: 'publishTime', width: 170 },
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
              ...(def.tableDrill
                ? {
                    y: {
                      labelFormatter: (value: unknown) =>
                        formatSigned(typeof value === 'number' ? value : Number(value)),
                    },
                  }
                : {}),
            }}
            {...boardColumnChartProps}
            {...(def.tableDrill
              ? {
                  label: {
                    ...boardColumnChartProps.label,
                    text: (datum: Record<string, unknown>) => {
                      const text = formatSigned(datum?.value);
                      return text === '-' ? '' : text;
                    },
                  },
                }
              : {})}
            onReady={def.tableDrill ? undefined : bindChartClick}
          />
        </BoardColumnScrollArea>
      </Suspense>
      {def.tableDrill ? (
        <Table<CiteTableRow>
          className='mt-3'
          size='small'
          bordered
          pagination={false}
          rowKey='drillKey'
          scroll={{ x: 'max-content', y: 280 }}
          locale={{ emptyText: '暂无数据' }}
          dataSource={citeTable?.rows || []}
          columns={[
            {
              title: level === 'aiPlatform' ? 'AI平台' : '撰写人',
              dataIndex: 'series',
              width: 140,
              fixed: 'left',
              render: (name: string, row) =>
                level === 'writer' || level === 'employee' ? (
                  <Button
                    type='link'
                    size='small'
                    className='px-0'
                    onClick={() => onSeriesClick(name, row.drillKey)}
                  >
                    {name}
                  </Button>
                ) : (
                  <span className='font-medium'>{name}</span>
                ),
            },
            ...(citeTable?.axes || []).map((axis) => ({
              title: formatAxis(axis),
              dataIndex: axis,
              width: 100,
              align: 'right' as const,
              render: (value: unknown) => {
                const text = formatSigned(value);
                const n = typeof value === 'number' ? value : 0;
                return (
                  <span className={n < 0 ? 'text-red-500' : n > 0 ? 'text-emerald-600' : 'text-neutral-500'}>
                    {text}
                  </span>
                );
              },
            })),
          ]}
        />
      ) : null}
      <div className='mt-1 text-xs text-neutral-400'>
        {def.tableDrill
          ? level === 'writer' || level === 'employee'
            ? '环比/同比可能为负，请点击表格中的撰写人姓名下钻，图表不可下钻'
            : '已到最细层级'
          : level === 'contentPlatform' && def.publishDetail
            ? '点击柱体查看该内容平台发布明细'
            : level === 'contentPlatform' && def.contentPlatformThenTopic
              ? '点击柱体查看该平台在各话题的被引用次数'
              : level === 'topic' && def.contentPlatformThenTopic
                ? '已到最细层级'
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
      <div className='text-xs text-neutral-400'>
        六个看板相互独立；页顶日期筛选统一生效。环比、同比可能为负，只点表格下钻；其余看板点击柱体下钻
      </div>
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
