import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { Breadcrumb, Button, Card, Col, DatePicker, Radio, Row, Select, Space, Statistic, Tag, message } from 'antd';
import { ArrowDownOutlined, ArrowLeftOutlined, ArrowUpOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { boardChartDrillApi, type BoardChartDrill, type BoardChartStackItem } from '@/api/board';

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

const dateAxisProps = {
  labelTransform: 'rotate(35)' as const,
  labelFontSize: 11,
  labelAutoHide: false,
  labelAutoRotate: false,
};

function rangeByGrain(grain: BoardGrain, pivot: Dayjs = dayjs()): [Dayjs, Dayjs] {
  if (grain === 'day') {
    // 最近 30 天（含当天）
    return [pivot.subtract(29, 'day').startOf('day'), pivot.endOf('day')];
  }
  if (grain === 'week') {
    // 之前 4 周（含本周）
    return [pivot.subtract(3, 'week').startOf('week'), pivot.endOf('week')];
  }
  if (grain === 'month') {
    // 之前 6 个月（含本月）
    return [pivot.subtract(5, 'month').startOf('month'), pivot.endOf('month')];
  }
  // 之前 4 年（含本年）
  return [pivot.subtract(3, 'year').startOf('year'), pivot.endOf('year')];
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
};

export default function ChartDrillBoard({ domain, title }: Props) {
  const [dim, setDim] = useState<BoardDim>('topic');
  const [grain, setGrain] = useState<BoardGrain>('week');
  const [personRole, setPersonRole] = useState<BoardPersonRole>('writer');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => rangeByGrain('week'));
  const [stack, setStack] = useState<BoardChartStackItem[]>([]);
  const [data, setData] = useState<BoardChartDrill | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    async (nextStack: BoardChartStackItem[], clickKey?: string) => {
      setLoading(true);
      try {
        const res = await boardChartDrillApi({
          domain,
          dim: domain === 'task' && dim === 'topic' ? 'theme' : dim,
          personRole: domain === 'geo' ? personRole : undefined,
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
        series: p.series,
        seriesKey: p.seriesKey || p.series,
        drillable: p.drillable !== false && !!data?.chartDrillable,
      }));
  }, [data]);

  const onSeriesDrill = (payload?: { seriesKey?: string; series?: string; key?: string; drillable?: boolean }) => {
    if (!data?.chartDrillable || payload?.drillable === false) return;
    const seriesKey = payload?.seriesKey || payload?.key || payload?.series;
    if (!seriesKey) return;
    const trendHit = (data.trend || []).find((t) => t.seriesKey === seriesKey || t.series === seriesKey);
    const barHit = (data.bars || []).find(
      (b) =>
        b.key === seriesKey ||
        b.label === seriesKey ||
        (trendHit != null && (b.key === trendHit.seriesKey || b.label === trendHit.series)),
    );
    if (barHit?.drillable === false || trendHit?.drillable === false) return;
    void load(stack, barHit?.key || trendHit?.seriesKey || seriesKey);
  };

  const bindChartDrill = (plot: { chart?: { on: (event: string, handler: (evt: any) => void) => void } }) => {
    plot?.chart?.on?.('element:click', (evt: any) => {
      const datum = evt?.data?.data as
        { seriesKey?: string; series?: string; key?: string; drillable?: boolean } | undefined;
      if (!datum) return;
      onSeriesDrill({
        seriesKey: datum.seriesKey,
        series: datum.series,
        key: datum.key,
        drillable: datum.drillable,
      });
    });
  };

  const onBack = () => {
    if (!stack.length) return;
    const next = stack.slice(0, -1);
    setStack(next);
    void load(next);
  };

  const onGrainChange = (g: BoardGrain) => {
    setGrain(g);
    setRange(rangeByGrain(g));
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

  return (
    <div className='flex flex-col gap-4 p-4'>
      <Card
        size='small'
        title={title}
      >
        <Space
          wrap
          size='middle'
        >
          <Radio.Group
            value={dim}
            optionType='button'
            buttonStyle='solid'
            options={[
              { label: '话题+问题', value: 'topic' },
              { label: '人维', value: 'person' },
            ]}
            onChange={(e) => setDim(e.target.value)}
          />
          {domain === 'geo' && dim === 'person' ? (
            <Select
              style={{ width: 140 }}
              value={personRole}
              options={[
                { label: '撰写人', value: 'writer' },
                { label: '发布人', value: 'publisher' },
                { label: '监测负责人', value: 'owner' },
              ]}
              onChange={(v) => setPersonRole(v)}
            />
          ) : null}
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
              if (v?.[0] && v?.[1]) setRange(normalizeRange(grain, v[0], v[1]));
            }}
          />
        </Space>
      </Card>

      <Card
        size='small'
        loading={loading}
        title={
          <div className='flex flex-wrap items-center gap-3'>
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

        <div className='mb-2 text-sm font-medium text-neutral-700'>折线图（X 轴=日期，点击系列下钻）</div>
        <Suspense fallback={<ChartFallback height={300} />}>
          <Line
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

        <div className='mt-6 mb-2 text-sm font-medium text-neutral-700'>柱状图（X 轴=日期，点击系列下钻）</div>
        <Suspense fallback={<ChartFallback />}>
          <Column
            data={timeSeriesData}
            xField='axis'
            yField='value'
            colorField='series'
            height={380}
            group
            stack={false}
            legend={{ position: 'top' }}
            axis={{ x: dateAxisProps }}
            onReady={bindChartDrill}
          />
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
            所有图 X 轴均为日期（如 26/09/16，斜向）；同日多系列为不同主题/人，点击折线点或柱体下钻
          </div>
        )}
      </Card>
    </div>
  );
}
