import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Breadcrumb, Button, Card, Col, DatePicker, Radio, Row, Space, Statistic, Tag, message } from 'antd';
import { ArrowDownOutlined, ArrowLeftOutlined, ArrowUpOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { boardChartDrillApi, type BoardChartDrill, type BoardChartStackItem } from '@/api/board';
import { BoardColumnScrollArea } from '@/components/geo/BoardColumnScrollArea';
import { boardColumnChartProps } from '@/components/geo/boardColumnChartProps';
import { DEMO_DATA_PIVOT, demoRangeByGrain } from '@/constants/demoData';

const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Column })));
const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Line })));

export type BoardDomain = 'geo' | 'task';
export type BoardDim = 'topic' | 'person';
export type BoardGrain = 'day' | 'week' | 'month' | 'year';
export type BoardPersonRole = 'writer' | 'publisher' | 'owner';

function ChartFallback({ height = 360 }: { height?: number }) {
  return (
    <div
      className='flex items-center justify-center text-sm text-neutral-400'
      style={{ height }}
    >
      图表加载中…
    </div>
  );
}

function DeltaTag({ value, suffix = '' }: { value?: number | null; suffix?: string }) {
  if (value == null || Number.isNaN(value)) return <span className='text-neutral-400'>-</span>;
  if (value > 0) {
    return (
      <Tag
        color='success'
        icon={<ArrowUpOutlined />}
      >
        +{value}
        {suffix}
      </Tag>
    );
  }
  if (value < 0) {
    return (
      <Tag
        color='error'
        icon={<ArrowDownOutlined />}
      >
        {value}
        {suffix}
      </Tag>
    );
  }
  return <Tag>0{suffix}</Tag>;
}

function formatBoardAxis(raw: string): string {
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

function axisFieldLabel(field?: string): string {
  switch (field) {
    case 'topic':
      return '话题';
    case 'question':
      return '目标问题';
    case 'person':
      return '人';
    case 'platform':
      return '平台';
    case 'theme':
      return '主题';
    case 'stage':
      return '状态';
    default:
      return field || '';
  }
}

const dateAxisProps = {
  labelTransform: 'rotate(35)' as const,
  labelFontSize: 11,
  labelAutoHide: false,
  labelAutoRotate: false,
};

function rangeByGrain(grain: BoardGrain, pivot: Dayjs = DEMO_DATA_PIVOT): [Dayjs, Dayjs] {
  return demoRangeByGrain(grain, pivot);
}

function normalizeRange(grain: BoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

type Props = {
  domain: BoardDomain;
  title: string;
  /** 受控时间粒度（传入后与父级共用） */
  grain?: BoardGrain;
  /** 受控日期范围 */
  range?: [Dayjs, Dayjs];
  /** 隐藏本组件内日/周/月/年与日期选择（由父级统一提供时） */
  hideTimeFilter?: boolean;
};

export default function ChartDrillBoard({ domain, title, grain: grainProp, range: rangeProp, hideTimeFilter }: Props) {
  const controlled = grainProp != null && rangeProp != null;
  const [dim, setDim] = useState<BoardDim>('topic');
  const [innerGrain, setInnerGrain] = useState<BoardGrain>('week');
  const [personRole, setPersonRole] = useState<BoardPersonRole>('writer');
  const [innerRange, setInnerRange] = useState<[Dayjs, Dayjs]>(() => rangeByGrain('week'));
  const grain = controlled ? grainProp : innerGrain;
  const range = controlled ? rangeProp : innerRange;
  const [stack, setStack] = useState<BoardChartStackItem[]>([]);
  const [data, setData] = useState<BoardChartDrill | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    async (nextStack: BoardChartStackItem[], clickKey?: string) => {
      setLoading(true);
      try {
        const res = await boardChartDrillApi({
          domain,
          dim: domain === 'geo' ? 'topic' : domain === 'task' && dim === 'topic' ? 'theme' : dim,
          personRole: domain === 'task' && dim === 'person' ? personRole : undefined,
          grain,
          startDate: range[0].format('YYYY-MM-DD'),
          endDate: range[1].format('YYYY-MM-DD'),
          metric: domain === 'geo' ? 'mentionRate' : 'doneRate',
          stack: nextStack,
          clickKey,
        });
        setData(res);
        const crumbs = (res.breadcrumb || []).filter((c) => c.field !== 'root');
        setStack(crumbs.map((c) => ({ field: c.field, key: c.key, label: c.label || c.key })));
      } catch (e: any) {
        message.error(e?.message || '加载看板失败');
      } finally {
        setLoading(false);
      }
    },
    [domain, dim, grain, personRole, range],
  );

  useEffect(() => {
    setStack([]);
    void load([]);
  }, [dim, grain, personRole, range, load]);

  const timeSeriesData = useMemo(() => {
    return (data?.trend || [])
      .slice()
      .sort((a, b) => a.axis.localeCompare(b.axis))
      .map((p) => ({
        axis: formatBoardAxis(p.axis),
        value: p.value,
        // 用完整 seriesKey 着色，避免短标签撞名把多根柱合成一根
        series: p.seriesKey || p.series,
        seriesKey: p.seriesKey || p.series,
        drillable: p.drillable !== false && !!data?.chartDrillable,
      }));
  }, [data]);

  const onSeriesDrill = useCallback(
    (payload?: { seriesKey?: string; series?: string; key?: string; drillable?: boolean }) => {
      if (!data?.chartDrillable || payload?.drillable === false) return;
      // 优先完整 key：点中分组里的某一根柱 = 只下钻该主题
      const seriesKey = payload?.seriesKey || payload?.key;
      const seriesName = payload?.series;
      if (!seriesKey && !seriesName) return;

      const trendHit = (data.trend || []).find(
        (t) =>
          (seriesKey && (t.seriesKey === seriesKey || t.series === seriesKey)) ||
          (seriesName && (t.series === seriesName || t.seriesKey === seriesName)),
      );
      const barHit = (data.bars || []).find(
        (b) =>
          (seriesKey && (b.key === seriesKey || b.label === seriesKey)) ||
          (seriesName && (b.label === seriesName || b.key === seriesName)) ||
          (trendHit != null && (b.key === trendHit.seriesKey || b.label === trendHit.series)),
      );
      if (barHit?.drillable === false || trendHit?.drillable === false) return;

      const drillKey = barHit?.key || trendHit?.seriesKey || seriesKey || seriesName;
      if (!drillKey) return;
      // 日期范围不变：仍用当前 range，只追加 stack
      void load(stack, drillKey);
    },
    [data, load, stack],
  );

  const drillRef = useRef(onSeriesDrill);
  drillRef.current = onSeriesDrill;

  const bindChartDrill = useCallback(
    (plot: { chart?: { on: (event: string, handler: (evt: any) => void) => void } }) => {
      const chart = plot?.chart;
      if (!chart?.on) return;
      chart.on('element:click', (evt: any) => {
        const raw = evt?.data?.data ?? evt?.data ?? {};
        const datum = (Array.isArray(raw) ? raw[0] : raw) as Record<string, unknown> | undefined;
        if (!datum) return;
        // 分组柱：每根柱带独立 series / seriesKey
        const seriesKey = String(datum.seriesKey ?? datum.key ?? '');
        const series = String(datum.series ?? datum.color ?? '');
        const drillable = datum.drillable !== false;
        if (!seriesKey && !series) return;
        drillRef.current({
          seriesKey: seriesKey || undefined,
          series: series || undefined,
          drillable,
        });
      });
    },
    [],
  );

  const chartRenderKey = `${data?.axisField || 'root'}-${stack.map((s) => s.key).join('|')}-${timeSeriesData.length}`;

  const onBack = () => {
    if (!stack.length) return;
    const next = stack.slice(0, -1);
    setStack(next);
    void load(next);
  };

  const onGrainChange = (g: BoardGrain) => {
    if (controlled) return;
    setInnerGrain(g);
    setInnerRange(rangeByGrain(g));
  };

  const pickerProps =
    grain === 'week'
      ? { picker: 'week' as const }
      : grain === 'month'
        ? { picker: 'month' as const }
        : grain === 'year'
          ? { picker: 'year' as const }
          : { picker: 'date' as const };

  const pctSuffix = domain === 'geo' || data?.metric === 'doneRate' ? '%' : '';
  const deltaSuffix = domain === 'geo' || data?.metric === 'doneRate' ? 'pp' : '';

  const showFilterBar = !hideTimeFilter || domain !== 'geo';

  return (
    <div className={hideTimeFilter ? 'flex flex-col gap-4' : 'flex flex-col gap-4 p-4'}>
      {showFilterBar ? (
        <Card
          size='small'
          title={title}
        >
          <Space
            wrap
            size='middle'
          >
            {domain === 'geo' ? null : (
              <Radio.Group
                value={dim}
                optionType='button'
                buttonStyle='solid'
                options={[
                  { label: '主题', value: 'topic' },
                  { label: '人', value: 'person' },
                ]}
                onChange={(e) => setDim(e.target.value)}
              />
            )}
            {!hideTimeFilter ? (
              <>
                <Radio.Group
                  value={grain}
                  optionType='button'
                  options={[
                    { label: '日', value: 'day' },
                    { label: '周', value: 'week' },
                    { label: '月', value: 'month' },
                    { label: '年', value: 'year' },
                  ]}
                  onChange={(e) => onGrainChange(e.target.value)}
                />
                <DatePicker.RangePicker
                  {...pickerProps}
                  value={range}
                  onChange={(v) => {
                    if (controlled || !v?.[0] || !v?.[1]) return;
                    setInnerRange(normalizeRange(grain, v[0], v[1]));
                  }}
                />
              </>
            ) : null}
            <span className='text-xs text-neutral-400'>
              {domain === 'geo'
                ? '三级：话题 → 目标问题 → 平台'
                : dim === 'person'
                  ? '路径：人 → 主题 → 状态'
                  : '路径：主题 → 人 → 状态'}
            </span>
          </Space>
        </Card>
      ) : null}

      <Card
        size='small'
        loading={loading}
        title={
          <div className='flex flex-wrap items-center gap-3'>
            {!showFilterBar ? <span className='font-medium'>{title}</span> : null}
            <Button
              type='link'
              disabled={!stack.length}
              icon={<ArrowLeftOutlined />}
              onClick={onBack}
            >
              返回上一级
            </Button>
            <Breadcrumb
              items={(data?.breadcrumb || [{ field: 'root', key: 'root', label: '大盘' }]).map((c) => ({
                title: c.label || c.key,
              }))}
            />
            <span className='text-sm font-medium text-neutral-700'>{data?.title || '—'}</span>
            {!showFilterBar ? <span className='text-xs text-neutral-400'>三级：话题 → 目标问题 → 平台</span> : null}
          </div>
        }
      >
        <Row
          gutter={[16, 16]}
          className='mb-4'
        >
          <Col
            xs={24}
            md={8}
          >
            <Statistic
              title={data?.metricLabel || '本期'}
              value={data?.currentValue ?? 0}
              suffix={pctSuffix}
            />
          </Col>
          <Col
            xs={24}
            md={8}
          >
            <div className='text-sm text-neutral-500'>环比</div>
            <DeltaTag
              value={data?.mom}
              suffix={deltaSuffix}
            />
          </Col>
          <Col
            xs={24}
            md={8}
          >
            <div className='text-sm text-neutral-500'>同比</div>
            <DeltaTag
              value={data?.yoy}
              suffix={deltaSuffix}
            />
          </Col>
        </Row>

        <div className='mb-2 text-sm font-medium text-neutral-700'>
          折线图（X 轴=日期；点击系列下钻到下一级
          {data?.axisField ? ` · 当前按${axisFieldLabel(data.axisField)}` : ''}）
        </div>
        <Suspense fallback={<ChartFallback height={300} />}>
          <Line
            key={`line-${chartRenderKey}`}
            data={timeSeriesData}
            xField='axis'
            yField='value'
            colorField='series'
            height={300}
            legend={{ position: 'top' }}
            axis={{ x: dateAxisProps }}
            onReady={bindChartDrill}
          />
        </Suspense>

        <div className='mt-6 mb-2 text-sm font-medium text-neutral-700'>
          分组柱状图（X 轴=日期；点击分组中某一根柱下钻该系列，日期范围不变
          {data?.axisField ? ` · 当前按${axisFieldLabel(data.axisField)}` : ''}）
        </div>
        <Suspense fallback={<ChartFallback />}>
          <BoardColumnScrollArea data={timeSeriesData}>
            <Column
              key={`col-${chartRenderKey}`}
              data={timeSeriesData}
              xField='axis'
              yField='value'
              colorField='series'
              height={380}
              group
              stack={false}
              legend={{ position: 'top' }}
              axis={{ x: dateAxisProps }}
              {...boardColumnChartProps}
              onReady={bindChartDrill}
            />
          </BoardColumnScrollArea>
        </Suspense>

        <div className='mt-3 flex flex-wrap gap-2'>
          {(data?.bars || []).map((b) => (
            <Tag
              key={b.key}
              color={b.drillable && data?.chartDrillable ? 'processing' : 'default'}
              className={b.drillable && data?.chartDrillable ? 'cursor-pointer' : 'cursor-default'}
              onClick={() => onSeriesDrill({ seriesKey: b.key, series: b.label, drillable: b.drillable })}
            >
              {b.label}: {b.value}
              {b.mom != null ? ` 环比${b.mom > 0 ? '+' : ''}${b.mom}` : ''}
              {b.yoy != null ? ` 同比${b.yoy > 0 ? '+' : ''}${b.yoy}` : ''}
            </Tag>
          ))}
        </div>

        {!data?.chartDrillable ? (
          <div className='mt-2 text-xs text-neutral-400'>当前已到最细层级，折线/柱状均不可再下钻</div>
        ) : (
          <div className='mt-2 text-xs text-neutral-400'>
            点中分组里的某一根柱（或某条折线）只下钻该主题；日期范围保持不变
          </div>
        )}
      </Card>
    </div>
  );
}
