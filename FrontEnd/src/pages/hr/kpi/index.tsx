import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Col, DatePicker, Drawer, Radio, Row, Select, Space, Statistic, Table, Tag } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import quarterOfYear from 'dayjs/plugin/quarterOfYear';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { boardColumnChartProps } from '@/components/geo/boardColumnChartProps';
import {
  downloadHrKpiApi,
  getHrKpiBoardApi,
  getHrUsersApi,
  type HrKpiBoard,
  type HrKpiGrain,
  type HrKpiQuery,
} from '@/api/hr';

dayjs.extend(quarterOfYear);

const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Column })));
const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Line })));
const Radar = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Radar })));

const GRAIN_OPTIONS: { label: string; value: HrKpiGrain }[] = [
  { label: '日', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
  { label: '季度', value: 'quarter' },
];

const IMPORTANCE = [
  { label: '高', value: 1 },
  { label: '中', value: 2 },
  { label: '低', value: 3 },
];

const URGENCY = [
  { label: '紧急', value: 1 },
  { label: '常规', value: 2 },
];

const DIFFICULTY = [
  { label: '高', value: 1 },
  { label: '中', value: 2 },
  { label: '低', value: 3 },
];

function pct(value?: number | null) {
  if (value == null || Number.isNaN(value)) return '—';
  return `${(value * 100).toFixed(1)}%`;
}

function num(value?: number | null, digits = 1) {
  if (value == null || Number.isNaN(value)) return '—';
  return Number(value).toFixed(digits);
}

function normalizeRange(grain: HrKpiGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'quarter') return [start.startOf('quarter'), end.endOf('quarter')];
  return [start.startOf('day'), end.endOf('day')];
}

function ChartFallback({ height = 280 }: { height?: number }) {
  return (
    <div
      className='flex items-center justify-center text-sm text-neutral-400'
      style={{ height }}
    >
      图表加载中…
    </div>
  );
}

export default function HrKpiPage() {
  const { message } = App.useApp();
  const [grain, setGrain] = useState<HrKpiGrain>('month');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    const end = dayjs();
    return [end.startOf('month'), end.endOf('month')];
  });
  const [ownerUserId, setOwnerUserId] = useState<number | undefined>();
  const [importanceLevel, setImportanceLevel] = useState<number | undefined>();
  const [urgencyLevel, setUrgencyLevel] = useState<number | undefined>();
  const [difficultyLevel, setDifficultyLevel] = useState<number | undefined>();
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [board, setBoard] = useState<HrKpiBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [onboardOpen, setOnboardOpen] = useState(false);
  const [overdueOpen, setOverdueOpen] = useState(false);

  useEffect(() => {
    void getHrUsersApi('owner')
      .then((rows) => {
        setUsers(
          (rows || []).map((row) => ({
            value: Number(row.userId),
            label: String(row.nickname || row.username || row.userId),
          })),
        );
      })
      .catch(() => undefined);
  }, []);

  const query = useMemo<HrKpiQuery>(() => {
    const [start, end] = normalizeRange(grain, range[0], range[1]);
    return {
      grain,
      startDate: start.format('YYYY-MM-DD'),
      endDate: end.format('YYYY-MM-DD'),
      ownerUserId,
      importanceLevel,
      urgencyLevel,
      difficultyLevel,
    };
  }, [difficultyLevel, grain, importanceLevel, ownerUserId, range, urgencyLevel]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getHrKpiBoardApi(query);
      setBoard(data);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载 KPI 失败');
    } finally {
      setLoading(false);
    }
  }, [message, query]);

  useEffect(() => {
    void load();
  }, [load]);

  const summary = board?.summary;
  const onboardTrend = useMemo(
    () => (board?.onboardTrend || []).map((item) => ({ ...item, value: Number(item.value) })),
    [board?.onboardTrend],
  );
  const stageCycle = useMemo(
    () => (board?.stageCycle || []).map((item) => ({ ...item, value: Number(item.value) })),
    [board?.stageCycle],
  );
  const gradeDistribution = useMemo(
    () => (board?.gradeDistribution || []).map((item) => ({ ...item, value: Number(item.value) })),
    [board?.gradeDistribution],
  );
  const funnel = useMemo(
    () => (board?.conversionFunnel || []).map((item) => ({ ...item, value: Number(item.value) })),
    [board?.conversionFunnel],
  );
  const radar = useMemo(
    () =>
      (board?.ownerRadar || []).map((item) => ({
        ownerName: item.ownerName || '未命名',
        metric: item.metric,
        value: Number(item.value),
      })),
    [board?.ownerRadar],
  );

  return (
    <div className='flex flex-col gap-4 p-4'>
      <Card size='small'>
        <Space
          wrap
          className='w-full justify-between'
        >
          <Space wrap>
            <Radio.Group
              optionType='button'
              options={GRAIN_OPTIONS}
              value={grain}
              onChange={(e) => {
                const next = e.target.value as HrKpiGrain;
                setGrain(next);
                setRange((prev) => normalizeRange(next, prev[0], prev[1]));
              }}
            />
            <DatePicker.RangePicker
              value={range}
              allowClear={false}
              onChange={(values) => {
                if (!values?.[0] || !values?.[1]) return;
                setRange(normalizeRange(grain, values[0], values[1]));
              }}
            />
            <Select
              allowClear
              placeholder='招聘负责人'
              className='min-w-[160px]'
              options={users}
              value={ownerUserId}
              onChange={setOwnerUserId}
              showSearch
              optionFilterProp='label'
            />
            <Select
              allowClear
              placeholder='重要性'
              className='min-w-[110px]'
              options={IMPORTANCE}
              value={importanceLevel}
              onChange={setImportanceLevel}
            />
            <Select
              allowClear
              placeholder='紧急程度'
              className='min-w-[110px]'
              options={URGENCY}
              value={urgencyLevel}
              onChange={setUrgencyLevel}
            />
            <Select
              allowClear
              placeholder='难度'
              className='min-w-[110px]'
              options={DIFFICULTY}
              value={difficultyLevel}
              onChange={setDifficultyLevel}
            />
          </Space>
          <Space>
            <Button
              onClick={() => void load()}
              loading={loading}
            >
              刷新
            </Button>
            <PermissionButton
              perm='hr:kpi:export'
              icon={<DownloadOutlined />}
              onClick={() => {
                void downloadHrKpiApi(query).catch(() => message.error('导出失败'));
              }}
            >
              导出报表
            </PermissionButton>
          </Space>
        </Space>
      </Card>

      <Row gutter={[12, 12]}>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
          >
            <Statistic
              title='在招岗位'
              value={summary?.openJobs ?? 0}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
            className='cursor-pointer hover:border-blue-300'
            onClick={() => setOnboardOpen(true)}
          >
            <Statistic
              title='成功入职'
              value={summary?.onboardedCount ?? 0}
              valueStyle={{ color: '#1677ff' }}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
          >
            <Statistic
              title='按期完成率'
              value={pct(summary?.onTimeRate)}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
            className='cursor-pointer hover:border-red-300'
            onClick={() => setOverdueOpen(true)}
          >
            <Statistic
              title='逾期岗位'
              value={summary?.overdueCount ?? 0}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
          >
            <Statistic
              title='超期完成'
              value={summary?.overdueCompletedCount ?? 0}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
          >
            <Statistic
              title='超额完成'
              value={summary?.overQuotaCount ?? 0}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
          >
            <Statistic
              title='筛选→入职'
              value={pct(summary?.conversionRate)}
            />
          </Card>
        </Col>
        <Col
          xs={12}
          sm={8}
          lg={3}
        >
          <Card
            size='small'
            loading={loading}
          >
            <Statistic
              title='平均周期(天)'
              value={num(summary?.avgCycleDays)}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={[12, 12]}>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='入职趋势'
            size='small'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Column
                {...boardColumnChartProps}
                data={onboardTrend}
                xField='axis'
                yField='value'
                seriesField='series'
                height={280}
              />
            </Suspense>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='岗位分级分布'
            size='small'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Column
                {...boardColumnChartProps}
                data={gradeDistribution}
                xField='axis'
                yField='value'
                seriesField='series'
                isGroup
                height={280}
              />
            </Suspense>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='转化漏斗（筛选→入职）'
            size='small'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Column
                {...boardColumnChartProps}
                data={funnel}
                xField='axis'
                yField='value'
                seriesField='series'
                height={280}
              />
            </Suspense>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='各阶段平均用时'
            size='small'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Line
                data={stageCycle}
                xField='axis'
                yField='value'
                seriesField='series'
                height={280}
                smooth
              />
            </Suspense>
          </Card>
        </Col>
        <Col xs={24}>
          <Card
            title='负责人绩效雷达'
            size='small'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback height={360} />}>
              <Radar
                data={radar}
                xField='metric'
                yField='value'
                seriesField='ownerName'
                height={360}
                meta={{ value: { alias: '得分' } }}
              />
            </Suspense>
          </Card>
        </Col>
      </Row>

      <Card
        title='招聘人员绩效'
        size='small'
        loading={loading}
      >
        <Table
          rowKey={(row) => String(row.ownerUserId ?? row.ownerName)}
          size='small'
          pagination={false}
          scroll={{ x: 1100 }}
          dataSource={board?.owners || []}
          columns={[
            {
              title: '操作',
              key: 'option',
              fixed: 'left',
              width: 90,
              render: (_, row) => (
                <Button
                  type='link'
                  size='small'
                  onClick={() => {
                    setOwnerUserId(row.ownerUserId);
                  }}
                >
                  筛选
                </Button>
              ),
            },
            { title: '负责人', dataIndex: 'ownerName', width: 120 },
            { title: '在招', dataIndex: 'openJobs', width: 80 },
            { title: '入职', dataIndex: 'onboardedCount', width: 80 },
            {
              title: '按期率',
              dataIndex: 'onTimeRate',
              width: 90,
              render: (v) => pct(v as number | null),
            },
            { title: '逾期岗', dataIndex: 'overdueCount', width: 80 },
            { title: '超期完成', dataIndex: 'overdueCompletedCount', width: 90 },
            { title: '超额', dataIndex: 'overQuotaCount', width: 80 },
            {
              title: '转化率',
              dataIndex: 'conversionRate',
              width: 90,
              render: (v) => pct(v as number | null),
            },
            {
              title: '平均周期',
              dataIndex: 'avgCycleDays',
              width: 100,
              render: (v) => num(v as number | null),
            },
          ]}
        />
      </Card>

      <Drawer
        title='成功入职明细'
        open={onboardOpen}
        onClose={() => setOnboardOpen(false)}
        width={720}
      >
        <Table
          rowKey='applicationId'
          size='small'
          dataSource={board?.onboardings || []}
          pagination={{ pageSize: 10 }}
          columns={[
            { title: '候选人', dataIndex: 'candidateName', width: 120 },
            { title: '岗位', dataIndex: 'jobName', ellipsis: true },
            { title: '负责人', dataIndex: 'ownerNames', width: 140 },
            { title: '入职日', dataIndex: 'onboardDate', width: 120 },
            { title: '周期(天)', dataIndex: 'cycleDays', width: 90 },
          ]}
        />
      </Drawer>

      <Drawer
        title='逾期岗位明细'
        open={overdueOpen}
        onClose={() => setOverdueOpen(false)}
        width={860}
      >
        <Table
          rowKey='id'
          size='small'
          dataSource={board?.overdueJobs || []}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 900 }}
          columns={[
            {
              title: '岗位',
              dataIndex: 'jobName',
              fixed: 'left',
              width: 160,
              ellipsis: true,
            },
            { title: '部门', dataIndex: 'deptName', width: 120 },
            { title: '负责人', dataIndex: 'ownerNames', width: 140 },
            {
              title: '分级',
              key: 'grade',
              width: 180,
              render: (_, row) => (
                <Space
                  size={4}
                  wrap
                >
                  {row.importanceLabel ? <Tag>重要:{row.importanceLabel}</Tag> : null}
                  {row.urgencyLabel ? (
                    <Tag color={row.urgencyLabel === '紧急' ? 'red' : 'default'}>{row.urgencyLabel}</Tag>
                  ) : null}
                  {row.difficultyLabel ? <Tag color='purple'>难度:{row.difficultyLabel}</Tag> : null}
                </Space>
              ),
            },
            { title: '目标日', dataIndex: 'targetDate', width: 110 },
            {
              title: '逾期天',
              dataIndex: 'overdueDays',
              width: 90,
              render: (v) => <span className='text-red-600'>{v ?? '—'}</span>,
            },
            {
              title: '进度',
              key: 'progress',
              width: 100,
              render: (_, row) => `${row.arrived ?? 0}/${row.headcount ?? 0}`,
            },
          ]}
        />
      </Drawer>
    </div>
  );
}
