import { lazy, Suspense, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Button, Card, Col, DatePicker, Drawer, Radio, Row, Select, Space, Table } from 'antd';
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { BoardColumnScrollArea } from '@/components/geo/BoardColumnScrollArea';
import { boardColumnChartProps } from '@/components/geo/boardColumnChartProps';
import { demoRangeByGrain, type DemoBoardGrain } from '@/constants/demoData';
import { downloadChartImage } from '@/utils/downloadChartImage';
import {
  downloadHrDrillApi,
  getHrBoardApi,
  getHrBoardJobsApi,
  getHrChannelsApi,
  getHrDrillApi,
  getHrUsersApi,
  type HrBoard,
  type HrBoardQuery,
  type HrChartPoint,
  type HrDrillRow,
  type HrFunnelNode,
  type HrInterviewRecordRow,
  type HrProgressRow,
} from '@/api/hr';

const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Column })));
const Funnel = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Funnel })));
const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Line })));
const Pie = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Pie })));

const PRIORITY = [
  { value: 1, label: '紧急' },
  { value: 2, label: '优先' },
  { value: 3, label: '常规' },
];

type DrillMode = 'funnel' | 'hc' | 'interview' | 'interviewer' | 'failReason' | 'volume';

function pct(value?: number | null) {
  if (value == null || Number.isNaN(value)) return '—';
  return `${(value * 100).toFixed(1)}%`;
}

function signedPct(value?: number | null) {
  if (value == null || Number.isNaN(value)) return '—';
  const text = `${(value * 100).toFixed(1)}%`;
  return value > 0 ? `+${text}` : text;
}

function formatAxis(raw: string) {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (day) return `${day[1].slice(2)}/${day[2]}/${day[3]}`;
  const month = /^(\d{4})-(\d{2})$/.exec(raw);
  if (month) return `${month[1].slice(2)}/${month[2]}`;
  const week = /^(\d{4})-W(\d{2})$/i.exec(raw);
  if (week) return `${week[1].slice(2)}/W${week[2]}`;
  return raw;
}

function trendData(rows?: HrChartPoint[]) {
  return (rows || []).map((item) => ({
    axis: formatAxis(item.axis),
    rawAxis: item.axis,
    series: item.series,
    seriesKey: item.seriesKey || item.series,
    value: Number(item.value),
    drillable: item.drillable === true,
  }));
}

function ChartFallback({ height = 320 }: { height?: number }) {
  return (
    <div
      className='flex items-center justify-center text-sm text-neutral-400'
      style={{ height }}
    >
      图表加载中…
    </div>
  );
}

function normalizeRange(grain: DemoBoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

function MetricClick({
  label,
  value,
  danger,
  onClick,
}: {
  label: string;
  value: ReactNode;
  danger?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type='button'
      className={`flex min-w-[96px] flex-col items-start rounded-md border border-transparent px-2 py-1 text-left transition hover:border-neutral-200 hover:bg-neutral-50 ${
        danger ? 'text-red-600' : 'text-neutral-800'
      }`}
      onClick={onClick}
    >
      <span className='text-xs text-neutral-500'>{label}</span>
      <span className='text-2xl leading-tight font-semibold'>{value}</span>
    </button>
  );
}

function ChartExportCard({
  title,
  filename,
  extra,
  children,
  hint,
}: {
  title: ReactNode;
  filename: string;
  extra?: ReactNode;
  children: ReactNode;
  hint?: ReactNode;
}) {
  const chartRef = useRef<HTMLDivElement>(null);
  return (
    <Card
      size='small'
      title={title}
      extra={
        <Space>
          {extra}
          <Button
            type='link'
            icon={<DownloadOutlined />}
            onClick={() => downloadChartImage(chartRef.current, filename)}
          >
            导出图片
          </Button>
        </Space>
      }
    >
      {hint ? <div className='mb-2 text-xs text-neutral-400'>{hint}</div> : null}
      <div ref={chartRef}>{children}</div>
    </Card>
  );
}

function GroupedColumn({
  data,
  yTitle,
  valueSuffix,
  onDrill,
}: {
  data: ReturnType<typeof trendData>;
  yTitle?: string;
  valueSuffix?: string;
  onDrill?: (payload: { seriesKey: string; rawAxis: string; series: string }) => void;
}) {
  const drillRef = useRef(onDrill);
  drillRef.current = onDrill;
  if (!data.length) {
    return <div className='py-10 text-center text-sm text-neutral-400'>当前范围内暂无统计数据</div>;
  }
  return (
    <Suspense fallback={<ChartFallback />}>
      <BoardColumnScrollArea data={data}>
        <Column
          data={data}
          xField='axis'
          yField='value'
          colorField='series'
          height={320}
          stack={false}
          axis={{
            x: {
              labelTransform: 'rotate(28)',
              labelFontSize: 10,
              labelAutoHide: false,
              labelAutoRotate: false,
            },
            y: { title: yTitle },
          }}
          tooltip={{
            items: [
              {
                channel: 'y',
                valueFormatter: (value: number) => `${value}${valueSuffix || ''}`,
              },
            ],
          }}
          onReady={(plot: {
            chart?: {
              on: (event: string, handler: (evt: { data?: { data?: Record<string, unknown> } }) => void) => void;
            };
          }) => {
            plot.chart?.on('element:click', (evt) => {
              const raw = evt?.data?.data;
              if (!raw || raw.drillable !== true) return;
              const seriesKey = String(raw.seriesKey || raw.series || '');
              const rawAxis = String(raw.rawAxis || '');
              const series = String(raw.series || '');
              if (seriesKey) drillRef.current?.({ seriesKey, rawAxis, series });
            });
          }}
          {...boardColumnChartProps}
        />
      </BoardColumnScrollArea>
    </Suspense>
  );
}

function VolumeLine({
  data,
  onDrill,
}: {
  data: ReturnType<typeof trendData>;
  onDrill?: (payload: { seriesKey: string; rawAxis: string; series: string }) => void;
}) {
  const drillRef = useRef(onDrill);
  drillRef.current = onDrill;
  if (!data.length) {
    return <div className='py-10 text-center text-sm text-neutral-400'>当前范围内暂无统计数据</div>;
  }
  return (
    <Suspense fallback={<ChartFallback />}>
      <Line
        data={data}
        xField='axis'
        yField='value'
        colorField='series'
        height={320}
        point={{ size: 4 }}
        axis={{
          x: {
            labelTransform: 'rotate(28)',
            labelFontSize: 10,
            labelAutoHide: false,
            labelAutoRotate: false,
          },
          y: { title: '人' },
        }}
        tooltip={{
          items: [{ channel: 'y', valueFormatter: (value: number) => `${value} 人` }],
        }}
        onReady={(plot: {
          chart?: {
            on: (event: string, handler: (evt: { data?: { data?: Record<string, unknown> } }) => void) => void;
          };
        }) => {
          const handler = (evt: { data?: { data?: Record<string, unknown> } }) => {
            const raw = evt?.data?.data;
            if (!raw || raw.drillable !== true) return;
            const seriesKey = String(raw.seriesKey || raw.series || '');
            const rawAxis = String(raw.rawAxis || '');
            const series = String(raw.series || '');
            if (seriesKey) drillRef.current?.({ seriesKey, rawAxis, series });
          };
          plot.chart?.on('element:click', handler);
          plot.chart?.on('point:click', handler);
        }}
      />
    </Suspense>
  );
}

const progressColumns = [
  {
    title: '岗位名称',
    dataIndex: 'jobName',
    render: (value: string, record: HrProgressRow) => (
      <span className={record.warning ? 'font-medium text-red-600' : undefined}>{value}</span>
    ),
  },
  { title: '需求人数', dataIndex: 'headcount', width: 90 },
  {
    title: '目标到岗日期',
    dataIndex: 'targetDate',
    width: 130,
    render: (value: string | null | undefined, record: HrProgressRow) => value || record.targetText || '—',
  },
  { title: '当前已到岗', dataIndex: 'arrived', width: 100 },
  { title: '剩余缺口', dataIndex: 'gap', width: 90 },
  { title: '岗位状态', dataIndex: 'progressStatus', width: 110 },
  {
    title: '紧急等级',
    dataIndex: 'priorityLabel',
    width: 100,
    render: (value: string, record: HrProgressRow) => (
      <span className={record.warning ? 'text-red-600' : undefined}>{value || '—'}</span>
    ),
  },
];

export default function HrBoardPage() {
  const [grain, setGrain] = useState<DemoBoardGrain>('week');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => demoRangeByGrain('week'));
  const [ownerUserId, setOwnerUserId] = useState<number | undefined>();
  const [jobCategory, setJobCategory] = useState<string | undefined>();
  const [channelCode, setChannelCode] = useState<string | undefined>();
  const [priority, setPriority] = useState<number | undefined>();
  const [cycleJob, setCycleJob] = useState<string | undefined>();
  const [board, setBoard] = useState<HrBoard | null>(null);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [jobs, setJobs] = useState<{ value: string; label: string }[]>([]);
  const [drillOpen, setDrillOpen] = useState(false);
  const [drillTitle, setDrillTitle] = useState('');
  const [drillMode, setDrillMode] = useState<DrillMode>('funnel');
  const [drillRows, setDrillRows] = useState<HrDrillRow[]>([]);
  const [drillRequisitions, setDrillRequisitions] = useState<HrProgressRow[]>([]);
  const [drillInterviews, setDrillInterviews] = useState<HrInterviewRecordRow[]>([]);
  const [drillQuery, setDrillQuery] = useState<HrBoardQuery>({});

  const payload = (): HrBoardQuery => ({
    startDate: range[0].format('YYYY-MM-DD'),
    endDate: range[1].format('YYYY-MM-DD'),
    grain,
    ownerUserId,
    jobCategory,
    channelCode,
    priority,
    cycleJob,
  });

  const clearCycle = () => setCycleJob(undefined);

  useEffect(() => {
    let cancelled = false;
    getHrBoardApi(payload())
      .then((data) => {
        if (!cancelled) setBoard(data);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [grain, range[0].valueOf(), range[1].valueOf(), ownerUserId, jobCategory, channelCode, priority, cycleJob]);

  useEffect(() => {
    getHrChannelsApi()
      .then((rows) =>
        setChannels(
          rows.map((item) => {
            const row = item as {
              channelCode?: string;
              channel_code?: string;
              channelName?: string;
              channel_name?: string;
            };
            return {
              value: String(row.channelCode ?? row.channel_code),
              label: String(row.channelName ?? row.channel_name),
            };
          }),
        ),
      )
      .catch(() => undefined);
    getHrUsersApi('owner')
      .then((rows) =>
        setUsers(
          rows.map((row) => {
            const item = row as { userId?: number; user_id?: number; nickname: string };
            return { value: Number(item.userId ?? item.user_id), label: item.nickname };
          }),
        ),
      )
      .catch(() => undefined);
    getHrBoardJobsApi()
      .then((names) => setJobs((names || []).map((name) => ({ value: name, label: name }))))
      .catch(() => undefined);
  }, []);

  const onGrainChange = (next: DemoBoardGrain) => {
    setGrain(next);
    setRange(demoRangeByGrain(next));
    clearCycle();
  };

  const pickerProps =
    grain === 'week'
      ? { picker: 'week' as const }
      : grain === 'month'
        ? { picker: 'month' as const }
        : grain === 'year'
          ? { picker: 'year' as const }
          : { picker: 'date' as const };

  const funnelChart = useMemo(
    () =>
      (board?.funnel || [])
        .filter((node) => node.count > 0)
        .map((node) => ({ stage: node.stageName, count: node.count, stageCode: node.stageCode })),
    [board],
  );

  const openDrill = async (mode: DrillMode, title: string, extra: HrBoardQuery) => {
    const next: HrBoardQuery = { ...payload(), ...extra, cycleJob: undefined };
    setDrillQuery(next);
    setDrillTitle(title);
    setDrillMode(mode);
    const data = await getHrDrillApi(next);
    setDrillRows(data.candidates || []);
    setDrillRequisitions(data.requisitions || []);
    setDrillInterviews(data.interviews || []);
    setDrillOpen(true);
  };

  const openFunnel = (node: HrFunnelNode) =>
    openDrill('funnel', node.stageName, { drillKind: 'STAGE', stageCode: node.stageCode });

  const openHc = (metric: NonNullable<HrBoardQuery['hcMetric']>, title: string) =>
    openDrill('hc', title, { drillKind: 'HC', hcMetric: metric });

  const openInterview = (metric: NonNullable<HrBoardQuery['interviewMetric']>, title: string) =>
    openDrill('interview', title, { drillKind: 'INTERVIEW', interviewMetric: metric });

  const jobData = trendData(board?.jobCycle);
  const stageData = trendData(board?.stageCycle);
  const interviewerData = trendData(board?.interviewerPass);
  const volumeData = trendData(board?.interviewVolume);
  const failReasonData = (board?.failReasons || []).map((item) => ({
    name: item.name,
    value: Number(item.value),
    key: item.key || item.name,
  }));
  const hc = board?.hc;
  const interview = board?.interviewStats;

  const exportExcel = (extra: HrBoardQuery, filename?: string) =>
    downloadHrDrillApi({ ...payload(), ...extra, cycleJob: undefined }, filename);

  return (
    <div className='flex flex-col gap-4 p-4'>
      <Card
        size='small'
        title='招聘看板筛选'
      >
        <Space wrap>
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
            allowClear={false}
            onChange={(value) => {
              if (value?.[0] && value?.[1]) {
                setRange(normalizeRange(grain, value[0], value[1]));
                clearCycle();
              }
            }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='招聘负责人'
            className='min-w-[160px]'
            options={users}
            value={ownerUserId}
            onChange={(value) => {
              setOwnerUserId(value);
              clearCycle();
            }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='岗位类别'
            className='min-w-[180px]'
            options={jobs}
            value={jobCategory}
            onChange={(value) => {
              setJobCategory(value);
              clearCycle();
            }}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='招聘渠道'
            className='min-w-[140px]'
            options={channels}
            value={channelCode}
            onChange={(value) => {
              setChannelCode(value);
              clearCycle();
            }}
          />
          <Select
            allowClear
            placeholder='紧急等级'
            className='min-w-[120px]'
            options={PRIORITY}
            value={priority}
            onChange={(value) => {
              setPriority(value);
              clearCycle();
            }}
          />
          <span className='text-xs text-neutral-400'>筛选同时作用于下方全部图表</span>
        </Space>
      </Card>

      <ChartExportCard
        title='招聘漏斗'
        filename='招聘漏斗'
        hint='按投递日期统计。点击某一层查看该环节候选人，并可导出明细。转化率 = 本层人数 ÷ 上一层人数。'
      >
        <div className='grid grid-cols-1 gap-4 xl:grid-cols-2'>
          {funnelChart.length ? (
            <Suspense fallback={<ChartFallback height={420} />}>
              <Funnel
                key={funnelChart.map((item) => `${item.stageCode}:${item.count}`).join('|')}
                data={funnelChart}
                xField='stage'
                yField='count'
                height={420}
                legend={false}
                label={{
                  text: (datum: { stage?: string; count?: number }) => `${datum.stage ?? ''}  ${datum.count ?? 0}人`,
                }}
                onReady={(plot: {
                  chart?: {
                    on: (event: string, handler: (evt: { data?: { data?: { stage?: string } } }) => void) => void;
                  };
                }) => {
                  plot.chart?.on('element:click', (evt) => {
                    const stage = evt?.data?.data?.stage;
                    const node = (board?.funnel || []).find((item) => item.stageName === stage);
                    if (node) void openFunnel(node);
                  });
                }}
              />
            </Suspense>
          ) : (
            <div className='flex h-[420px] items-center justify-center text-sm text-neutral-400'>
              当前范围内暂无漏斗数据
            </div>
          )}
          <Table
            rowKey='stageCode'
            size='small'
            pagination={false}
            dataSource={board?.funnel || []}
            onRow={(record) => ({
              onClick: () => void openFunnel(record),
              className: 'cursor-pointer',
            })}
            columns={[
              { title: '环节', dataIndex: 'stageName' },
              { title: '人数', dataIndex: 'count', width: 72 },
              {
                title: '转化率',
                dataIndex: 'conversion',
                width: 150,
                render: (value: number | null, record) =>
                  record.conversionLabel ? `${record.conversionLabel} ${pct(value)}` : '—',
              },
              {
                title: '环比',
                dataIndex: 'mom',
                width: 88,
                render: (value: number | null) => signedPct(value),
              },
              {
                title: '同比',
                dataIndex: 'yoy',
                width: 88,
                render: (value: number | null) => signedPct(value),
              },
            ]}
          />
        </div>
      </ChartExportCard>

      <ChartExportCard
        title={
          <div className='flex flex-wrap items-center gap-3'>
            <span>招聘周期分析</span>
            <Button
              type='link'
              disabled={!cycleJob}
              icon={<ArrowLeftOutlined />}
              onClick={() => setCycleJob(undefined)}
            >
              返回上一级
            </Button>
            <span className='text-sm font-normal text-neutral-500'>{cycleJob ? cycleJob : '各岗位平均周期'}</span>
          </div>
        }
        filename='招聘周期分析'
        hint={
          cycleJob
            ? '只统计该岗位已入职的候选人。横轴为入职日期，柱子为五个阶段的平均天数。'
            : '只统计已入职的候选人。横轴为入职日期，柱子为各岗位平均周期（入职日 − 需求接收日）。点击岗位柱下钻到各阶段。'
        }
      >
        <GroupedColumn
          data={jobData}
          yTitle='天'
          valueSuffix=' 天'
          onDrill={({ seriesKey }) => {
            if (!cycleJob) setCycleJob(seriesKey);
          }}
        />
      </ChartExportCard>

      <ChartExportCard
        title='阶段招聘周期分析'
        filename='阶段招聘周期分析'
        hint='只统计该阶段结束时间已经有的记录。横轴为阶段结束日期，柱子为五个阶段的平均天数。'
      >
        <GroupedColumn
          data={stageData}
          yTitle='天'
          valueSuffix=' 天'
        />
      </ChartExportCard>

      <Card
        size='small'
        title='招聘完成情况'
      >
        <div className='mb-2 text-xs text-neutral-400'>点击指标查看对应需求明细。完成率 = 已到岗人数 ÷ 总HC需求数</div>
        <Row gutter={[12, 12]}>
          <Col
            xs={24}
            md={12}
            xl={8}
          >
            <Card
              size='small'
              className='h-full'
            >
              <Space
                wrap
                size={[8, 8]}
              >
                <MetricClick
                  label='总HC需求数'
                  value={hc?.demand ?? 0}
                  onClick={() => void openHc('DEMAND', '总HC需求')}
                />
                <MetricClick
                  label='已到岗人数'
                  value={hc?.arrived ?? 0}
                  onClick={() => void openHc('ARRIVED', '已到岗需求')}
                />
                <MetricClick
                  label='待招聘缺口'
                  value={hc?.gap ?? 0}
                  danger={(hc?.gap ?? 0) > 0}
                  onClick={() => void openHc('GAP', '待招聘缺口')}
                />
              </Space>
            </Card>
          </Col>
          <Col
            xs={24}
            md={12}
            xl={8}
          >
            <Card
              size='small'
              className='h-full'
            >
              <Space
                wrap
                size={[8, 8]}
              >
                <MetricClick
                  label='已关闭岗位数'
                  value={hc?.closed ?? 0}
                  onClick={() => void openHc('CLOSED', '已关闭岗位')}
                />
                <MetricClick
                  label='冻结HC数'
                  value={hc?.frozen ?? 0}
                  onClick={() => void openHc('FROZEN', '冻结HC')}
                />
              </Space>
            </Card>
          </Col>
          <Col
            xs={24}
            md={12}
            xl={8}
          >
            <Card
              size='small'
              className='h-full cursor-pointer transition hover:shadow-sm'
              onClick={() => void openHc('RATE', '招聘完成率相关需求')}
            >
              <div className='text-xs text-neutral-500'>招聘完成率</div>
              <div className='text-3xl font-semibold text-neutral-800'>{pct(hc?.completionRate)}</div>
              <div className='mt-1 text-sm text-neutral-600'>
                {hc?.arrived ?? 0} / {hc?.demand ?? 0}
              </div>
            </Card>
          </Col>
        </Row>
      </Card>

      <Card
        size='small'
        title='岗位进度明细'
        extra={
          <PermissionButton
            perm='hr:board:export'
            type='link'
            icon={<DownloadOutlined />}
            onClick={() => void exportExcel({ drillKind: 'PROGRESS', hcMetric: 'DEMAND' }, '岗位进度明细.xlsx')}
          >
            导出 Excel
          </PermissionButton>
        }
      >
        <div className='mb-2 text-xs text-neutral-400'>
          目标到岗日期临近 48 小时或已超期且未完成的岗位标红；无日期但标记为紧急的岗位也会预警。
        </div>
        <Table
          rowKey='id'
          size='small'
          pagination={{ pageSize: 8 }}
          dataSource={hc?.rows || []}
          columns={progressColumns}
          onRow={(record) => ({
            className: record.warning ? 'bg-red-50' : undefined,
          })}
        />
      </Card>

      <Card
        size='small'
        title='面试情况统计分析'
      >
        <div className='mb-2 text-xs text-neutral-400'>点击指标查看候选人明细。通过率 = 通过 ÷（通过+未通过）</div>
        <Row gutter={[12, 12]}>
          <Col
            xs={24}
            lg={12}
          >
            <Card
              size='small'
              title='数量'
              className='h-full'
            >
              <Space
                wrap
                size={[8, 8]}
              >
                <MetricClick
                  label='待邀约'
                  value={interview?.pendingInvite ?? 0}
                  onClick={() => void openInterview('PENDING_INVITE', '待邀约')}
                />
                <MetricClick
                  label='已邀约'
                  value={interview?.invited ?? 0}
                  onClick={() => void openInterview('INVITED', '已邀约')}
                />
                <MetricClick
                  label='到面人数'
                  value={interview?.showUp ?? 0}
                  onClick={() => void openInterview('SHOW_UP', '到面人数')}
                />
                <MetricClick
                  label='一面参与'
                  value={interview?.round1 ?? 0}
                  onClick={() => void openInterview('ROUND1', '一面参与')}
                />
                <MetricClick
                  label='复试参与'
                  value={interview?.retest ?? 0}
                  onClick={() => void openInterview('RETEST', '复试参与')}
                />
                <MetricClick
                  label='终面参与'
                  value={interview?.finalRound ?? 0}
                  onClick={() => void openInterview('FINAL', '终面参与')}
                />
              </Space>
            </Card>
          </Col>
          <Col
            xs={24}
            lg={12}
          >
            <Card
              size='small'
              title='通过率 / 爽约'
              className='h-full'
            >
              <Space
                wrap
                size={[8, 8]}
              >
                <MetricClick
                  label='一面通过率'
                  value={pct(interview?.round1PassRate)}
                  onClick={() => void openInterview('ROUND1_PASS', '一面通过')}
                />
                <MetricClick
                  label='复试通过率'
                  value={pct(interview?.retestPassRate)}
                  onClick={() => void openInterview('RETEST_PASS', '复试通过')}
                />
                <MetricClick
                  label='终面通过率'
                  value={pct(interview?.finalPassRate)}
                  onClick={() => void openInterview('FINAL_PASS', '终面通过')}
                />
                <MetricClick
                  label='爽约人数'
                  value={interview?.noShow ?? 0}
                  danger={(interview?.noShow ?? 0) > 0}
                  onClick={() => void openInterview('NO_SHOW', '面试爽约')}
                />
                <MetricClick
                  label='爽约率'
                  value={pct(interview?.noShowRate)}
                  danger={(interview?.noShow ?? 0) > 0}
                  onClick={() => void openInterview('NO_SHOW', '面试爽约')}
                />
              </Space>
            </Card>
          </Col>
        </Row>
      </Card>

      <ChartExportCard
        title='面试官维度统计'
        filename='面试官通过率'
        hint='横轴为面试评价日期，柱子为各面试官通过率。点击柱子查看该时段下该面试官的面试记录。'
      >
        <GroupedColumn
          data={interviewerData}
          yTitle='%'
          valueSuffix='%'
          onDrill={({ seriesKey, rawAxis, series }) =>
            void openDrill('interviewer', `${series} · ${formatAxis(rawAxis)}`, {
              drillKind: 'INTERVIEWER',
              interviewerUserId: Number(seriesKey),
              axis: rawAxis,
            })
          }
        />
      </ChartExportCard>

      <ChartExportCard
        title='面试淘汰原因统计'
        filename='面试淘汰原因'
        hint='按面试记录未通过原因统计。点击饼图某一块查看对应面试记录。'
      >
        {failReasonData.length ? (
          <Suspense fallback={<ChartFallback height={360} />}>
            <Pie
              data={failReasonData}
              angleField='value'
              colorField='name'
              height={360}
              legend={{ position: 'bottom' }}
              label={{ text: 'name' }}
              tooltip={{
                items: [{ channel: 'y', valueFormatter: (value: number) => `${value} 条` }],
              }}
              onReady={(plot: {
                chart?: {
                  on: (
                    event: string,
                    handler: (evt: { data?: { data?: { name?: string; key?: string } } }) => void,
                  ) => void;
                };
              }) => {
                plot.chart?.on('element:click', (evt) => {
                  const raw = evt?.data?.data;
                  const reason = raw?.key || raw?.name;
                  if (!reason) return;
                  void openDrill('failReason', `淘汰原因 · ${reason}`, {
                    drillKind: 'FAIL_REASON',
                    failReason: reason,
                  });
                });
              }}
            />
          </Suspense>
        ) : (
          <div className='py-10 text-center text-sm text-neutral-400'>当前范围内暂无淘汰原因数据</div>
        )}
      </ChartExportCard>

      <ChartExportCard
        title='面试量趋势图'
        filename='面试量趋势'
        hint='横轴为面试评价日期，折线为各需求去重面试候选人数。点击折线点查看该需求该时段候选人。'
      >
        <VolumeLine
          data={volumeData}
          onDrill={({ seriesKey, rawAxis, series }) =>
            void openDrill('volume', `${series} · ${formatAxis(rawAxis)}`, {
              drillKind: 'VOLUME',
              requisitionId: Number(seriesKey),
              axis: rawAxis,
            })
          }
        />
      </ChartExportCard>

      <Drawer
        title={drillTitle}
        size='large'
        open={drillOpen}
        onClose={() => setDrillOpen(false)}
      >
        <PermissionButton
          perm='hr:board:export'
          className='mb-3'
          icon={<DownloadOutlined />}
          onClick={() => void downloadHrDrillApi(drillQuery)}
        >
          导出 Excel
        </PermissionButton>
        {drillMode === 'hc' ? (
          <Table
            rowKey='id'
            size='small'
            pagination={{ pageSize: 10 }}
            dataSource={drillRequisitions}
            columns={progressColumns}
            onRow={(record) => ({
              className: record.warning ? 'bg-red-50' : undefined,
            })}
          />
        ) : null}
        {drillMode === 'interviewer' || drillMode === 'failReason' ? (
          <Table
            rowKey='recordId'
            size='small'
            pagination={{ pageSize: 10 }}
            dataSource={drillInterviews}
            columns={[
              { title: '候选人', dataIndex: 'candidateName' },
              { title: '岗位', dataIndex: 'jobName' },
              { title: '轮次', dataIndex: 'roundName', width: 80 },
              {
                title: '结论',
                dataIndex: 'conclusion',
                width: 90,
                render: (value: string) => (value === 'PASS' ? '通过' : value === 'FAIL' ? '未通过' : value || '—'),
              },
              { title: '未通过原因', dataIndex: 'failReason' },
              { title: '评语', dataIndex: 'comment', ellipsis: true },
              { title: '面试官', dataIndex: 'interviewerName', width: 100 },
              { title: '面试时间', dataIndex: 'interviewedAt', width: 170 },
            ]}
          />
        ) : null}
        {drillMode === 'funnel' || drillMode === 'interview' || drillMode === 'volume' ? (
          <Table
            rowKey='applicationId'
            size='small'
            pagination={{ pageSize: 10 }}
            dataSource={drillRows}
            columns={[
              { title: '候选人', dataIndex: 'candidateName' },
              { title: '岗位类别', dataIndex: 'jobName' },
              { title: '渠道', dataIndex: 'channel' },
              { title: '紧急等级', dataIndex: 'priorityLabel', width: 90 },
              { title: '招聘负责人', dataIndex: 'ownerName' },
              { title: '当前阶段', dataIndex: 'stageName' },
              { title: '到达日期', dataIndex: 'reachedAt', width: 120 },
              { title: '投递日期', dataIndex: 'submittedAt', width: 120 },
            ]}
          />
        ) : null}
      </Drawer>
    </div>
  );
}
