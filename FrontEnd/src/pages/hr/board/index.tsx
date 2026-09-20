import { lazy, Suspense, useEffect, useState } from 'react';
import { Button, Card, DatePicker, Drawer, Popover, Radio, Space, Table, message } from 'antd';
import { ProFormSelect, ProFormText, QueryFilter } from '@ant-design/pro-components';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { BoardColumnScrollArea } from '@/components/geo/BoardColumnScrollArea';
import { boardColumnChartProps } from '@/components/geo/boardColumnChartProps';
import { demoRangeByGrain, type DemoBoardGrain } from '@/constants/demoData';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import {
  downloadHrDrillApi,
  getHrBoardApi,
  getHrChannelsApi,
  getHrDepartmentsApi,
  getHrDrillApi,
  getHrMetricsApi,
  getHrTargetOptionsApi,
  getHrUsersApi,
  saveHrViewApi,
  type HrBoard,
  type HrBoardQuery,
  type HrDrillRow,
} from '@/api/hr';

const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Column })));

const STATUS = [
  { value: 'OPEN', label: '招聘中' },
  { value: 'DONE', label: '已完成' },
  { value: 'STOPPED', label: '停止招聘' },
  { value: 'PAUSED', label: '暂缓' },
  { value: 'ARCHIVED', label: '已归档' },
];

const PRIORITY = [
  { value: 1, label: '紧急' },
  { value: 2, label: '优先' },
  { value: 3, label: '常规' },
];

function pct(value?: number | null) {
  if (value == null) return '—';
  return `${(value * 100).toFixed(1)}%`;
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

function trendData(rows?: { axis: string; series: string; value: number }[]) {
  return (rows || []).map((item) => ({
    axis: formatAxis(item.axis),
    series: item.series,
    value: Number(item.value),
  }));
}

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

function NumberChart({ data }: { data: { axis: string; series: string; value: number }[] }) {
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
          height={220}
          stack={false}
          axis={{
            x: {
              labelTransform: 'rotate(28)',
              labelFontSize: 10,
              labelAutoHide: false,
              labelAutoRotate: false,
            },
          }}
          {...boardColumnChartProps}
        />
      </BoardColumnScrollArea>
    </Suspense>
  );
}

function normalizeRange(grain: DemoBoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

export default function HrBoardPage() {
  const [grain, setGrain] = useState<DemoBoardGrain>('week');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => demoRangeByGrain('week'));
  const [query, setQuery] = useState<HrBoardQuery>({});
  const [board, setBoard] = useState<HrBoard | null>(null);
  const [metrics, setMetrics] = useState<{ name: string; formula: string }[]>([]);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [depts, setDepts] = useState<{ value: number; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [targets, setTargets] = useState<{ value: string; label: string }[]>([]);
  const [drillOpen, setDrillOpen] = useState(false);
  const [drillTitle, setDrillTitle] = useState('');
  const [drillRows, setDrillRows] = useState<HrDrillRow[]>([]);
  const [drillQuery, setDrillQuery] = useState<HrBoardQuery>({});

  const payload = (): HrBoardQuery => ({
    startDate: range[0].format('YYYY-MM-DD'),
    endDate: range[1].format('YYYY-MM-DD'),
    grain,
    ...query,
  });

  const load = async () => {
    setBoard(await getHrBoardApi(payload()));
  };

  const onGrainChange = (next: DemoBoardGrain) => {
    setGrain(next);
    setRange(demoRangeByGrain(next));
  };

  const pickerProps =
    grain === 'week'
      ? { picker: 'week' as const }
      : grain === 'month'
        ? { picker: 'month' as const }
        : grain === 'year'
          ? { picker: 'year' as const }
          : { picker: 'date' as const };

  useEffect(() => {
    load().catch(() => undefined);
  }, [grain, range[0].valueOf(), range[1].valueOf(), JSON.stringify(query)]);

  useEffect(() => {
    getHrMetricsApi()
      .then(setMetrics)
      .catch(() => undefined);
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
    getHrDepartmentsApi()
      .then((tree) => {
        const walk = (
          nodes: { id: number; name: string; children?: { id: number; name: string }[] }[],
          prefix = '',
        ): { value: number; label: string }[] =>
          nodes.flatMap((node) => {
            const label = prefix ? `${prefix} / ${node.name}` : node.name;
            return [
              { value: node.id, label },
              ...walk(
                (node.children || []) as { id: number; name: string; children?: { id: number; name: string }[] }[],
                label,
              ),
            ];
          });
        setDepts(walk(tree as { id: number; name: string; children?: { id: number; name: string }[] }[]));
      })
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
    getHrTargetOptionsApi()
      .then((rows) => setTargets(rows.map((row) => ({ value: row.name, label: row.name }))))
      .catch(() => undefined);
  }, []);

  const openDrill = async (extra: HrBoardQuery, title: string) => {
    const next = { ...payload(), ...extra };
    setDrillQuery(next);
    setDrillTitle(title);
    setDrillRows(await getHrDrillApi(next));
    setDrillOpen(true);
  };

  return (
    <div className='flex flex-col gap-4'>
      <Card
        size='small'
        title='招聘看板'
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
              if (value?.[0] && value?.[1]) setRange(normalizeRange(grain, value[0], value[1]));
            }}
          />
          <span className='text-xs text-neutral-400'>同时作用于下方看板</span>
        </Space>
        <QueryFilter
          className='mt-3'
          {...BUTTERFLY_SEARCH}
          onFinish={async (values) => {
            setQuery({
              jobName: values.jobName || undefined,
              deptId: values.deptId,
              ownerUserId: values.ownerUserId,
              locationCode: values.locationCode,
              status: values.status,
              priority: values.priority,
              targetText: values.targetText || undefined,
              channelCode: values.channelCode,
            });
            return true;
          }}
          onReset={() => setQuery({})}
        >
          <ProFormText
            name='jobName'
            label='岗位'
          />
          <ProFormSelect
            name='deptId'
            label='部门'
            options={depts}
            fieldProps={{ allowClear: true, showSearch: true, optionFilterProp: 'label' }}
          />
          <ProFormSelect
            name='ownerUserId'
            label='负责人'
            options={users}
            fieldProps={{ allowClear: true, showSearch: true, optionFilterProp: 'label' }}
          />
          <ProFormSelect
            name='locationCode'
            label='地点'
            options={[
              { value: 'SH', label: '上海' },
              { value: 'XJ', label: '新疆' },
            ]}
          />
          <ProFormSelect
            name='status'
            label='状态'
            options={STATUS}
          />
          <ProFormSelect
            name='priority'
            label='优先级'
            options={PRIORITY}
          />
          <ProFormSelect
            name='targetText'
            label='目标到岗'
            options={targets}
          />
          <ProFormSelect
            name='channelCode'
            label='渠道'
            options={channels}
          />
        </QueryFilter>
        <Space
          wrap
          className='mt-2'
        >
          <Button
            onClick={async () => {
              await saveHrViewApi(`视图${dayjs().format('MMDD-HHmm')}`, payload());
              message.success('已保存当前筛选');
            }}
          >
            保存视图
          </Button>
          <Popover
            title='指标说明'
            content={
              <div className='max-w-md'>
                {metrics.map((item) => (
                  <p key={item.name}>
                    <b>{item.name}：</b>
                    {item.formula}
                  </p>
                ))}
              </div>
            }
          >
            <Button>指标说明</Button>
          </Popover>
          <PermissionButton
            perm='hr:board:export'
            onClick={() => downloadHrDrillApi({ ...payload(), drillKind: 'STAGE' })}
          >
            导出明细
          </PermissionButton>
        </Space>
      </Card>

      <Card
        size='small'
        title='招聘漏斗'
      >
        <div className='flex flex-col gap-2'>
          {(board?.funnel || []).map((node) => (
            <button
              key={node.stageCode}
              type='button'
              className='flex items-center justify-between rounded border border-neutral-200 px-3 py-2 text-left hover:border-blue-400'
              onClick={() => {
                if (!node.uncollected) openDrill({ drillKind: 'STAGE', stageCode: node.stageCode }, node.stageName);
              }}
            >
              <span>{node.stageName}</span>
              <span className='text-sm text-neutral-500'>
                {node.uncollected ? '未采集' : `${node.count} 人`} · 转化 {pct(node.conversion)} · 环比 {pct(node.mom)}{' '}
                · 同比 {pct(node.yoy)}
              </span>
            </button>
          ))}
        </div>
      </Card>

      <Card
        size='small'
        title='招聘周期'
      >
        <div className='mb-2 text-xs text-neutral-400'>横轴=日期，单位=天</div>
        <NumberChart data={trendData(board?.cycle.trend)} />
      </Card>

      <Card
        size='small'
        title='HC 完成'
      >
        <div className='mb-2 text-xs text-neutral-400'>横轴=日期，单位=人</div>
        <NumberChart data={trendData(board?.hc.trend)} />
      </Card>

      <Card
        size='small'
        title='面试场次'
      >
        <div className='mb-2 text-xs text-neutral-400'>横轴=日期，单位=场</div>
        <NumberChart data={trendData(board?.interview.trend)} />
      </Card>

      <Card
        size='small'
        title='岗位进度'
      >
        <Table
          rowKey='id'
          size='small'
          pagination={{ pageSize: 8 }}
          dataSource={board?.hc.rows || []}
          columns={[
            { title: '岗位', dataIndex: 'jobName' },
            { title: '地点', dataIndex: 'location', width: 80 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (status: string) => STATUS.find((item) => item.value === status)?.label || status,
            },
            {
              title: '优先级',
              dataIndex: 'priority',
              width: 80,
              render: (priority: number) => PRIORITY.find((item) => item.value === priority)?.label || '—',
            },
            { title: '目标', dataIndex: 'targetText' },
            { title: '接收日', dataIndex: 'receivedDate', width: 120 },
            { title: '入职日', dataIndex: 'onboardDate', width: 120 },
            { title: '缺口', dataIndex: 'gap', width: 70 },
            {
              title: '预警',
              dataIndex: 'warning',
              width: 80,
              render: (warning: boolean) => (warning ? <span className='text-red-500'>紧急未到岗</span> : '—'),
            },
          ]}
        />
      </Card>

      <Drawer
        title={drillTitle}
        size='large'
        open={drillOpen}
        onClose={() => setDrillOpen(false)}
      >
        <PermissionButton
          perm='hr:board:export'
          className='mb-3'
          onClick={() => downloadHrDrillApi(drillQuery)}
        >
          导出
        </PermissionButton>
        <Table
          rowKey='applicationId'
          size='small'
          dataSource={drillRows}
          columns={[
            { title: '候选人', dataIndex: 'candidateName' },
            { title: '岗位', dataIndex: 'jobName' },
            { title: '渠道', dataIndex: 'channel' },
            { title: '阶段', dataIndex: 'stageName' },
            { title: '提交人', dataIndex: 'submitter' },
            { title: '投递日期', dataIndex: 'submittedAt' },
          ]}
        />
      </Drawer>
    </div>
  );
}
