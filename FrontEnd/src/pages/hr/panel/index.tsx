import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { DownloadOutlined } from '@ant-design/icons';
import { Button, Card, DatePicker, Drawer, Radio, Space, Statistic, Table, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import { HrJobDetailDrawer } from '@/components/hr/HrJobDetailDrawer';
import { demoRangeByGrain, type DemoBoardGrain } from '@/constants/demoData';
import { downloadElementImage } from '@/utils/downloadElementImage';
import {
  getHrDepartmentsApi,
  getHrDictOptionsApi,
  getHrPanelApi,
  getHrPanelDetailApi,
  getHrUsersApi,
  type HrPanelJobRow,
  type HrPanelMetric,
  type HrPanelPersonRow,
  type HrPanelQuery,
  type HrPanelStats,
} from '@/api/hr';

const PANEL_STATUS = [
  { label: '招聘中', value: 'OPEN' },
  { label: '已关闭', value: 'CLOSED' },
];

interface Dept {
  id: number;
  name: string;
  children?: Dept[];
}

function flattenDepts(rows: Dept[], prefix = ''): { value: number; label: string }[] {
  return rows.flatMap((row) => {
    const label = prefix ? `${prefix} / ${row.name}` : row.name;
    return [{ value: row.id, label }, ...flattenDepts(row.children || [], label)];
  });
}

function numId(value: unknown): number | undefined {
  if (value == null || value === '') return undefined;
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

function normalizeRange(grain: DemoBoardGrain, start: Dayjs, end: Dayjs): [Dayjs, Dayjs] {
  if (grain === 'week') return [start.startOf('week'), end.endOf('week')];
  if (grain === 'month') return [start.startOf('month'), end.endOf('month')];
  if (grain === 'year') return [start.startOf('year'), end.endOf('year')];
  return [start.startOf('day'), end.endOf('day')];
}

const STATS: { key: keyof HrPanelStats; metric: HrPanelMetric; title: string; suffix: string }[] = [
  { key: 'openJobCount', metric: 'OPEN_JOB', title: '目前在招岗位', suffix: '个' },
  { key: 'bgCheckCount', metric: 'BG_CHECK', title: '进入背调', suffix: '人' },
  { key: 'pendingOnboardCount', metric: 'PENDING_ONBOARD', title: '进入待入职', suffix: '人' },
  { key: 'todayScreenCount', metric: 'TODAY_SCREEN', title: '筛选简历', suffix: '人' },
  { key: 'todayPhoneCount', metric: 'TODAY_PHONE', title: '电话沟通', suffix: '人' },
  { key: 'todayInviteCount', metric: 'TODAY_INVITE', title: '约面', suffix: '人' },
  { key: 'todayInterviewCount', metric: 'TODAY_INTERVIEW', title: '实际面试', suffix: '人' },
  { key: 'todayInterviewPassCount', metric: 'TODAY_INTERVIEW_PASS', title: '通过面试', suffix: '人' },
];

const HrPanelPage = memo(function HrPanelPage() {
  const actionRef = useRef<ActionType>(null);
  const sheetRef = useRef<HTMLDivElement>(null);
  const [exporting, setExporting] = useState(false);
  const [stats, setStats] = useState<HrPanelStats>({});
  const [depts, setDepts] = useState<{ value: number; label: string }[]>([]);
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [priorities, setPriorities] = useState<{ value: string; label: string }[]>([]);
  const [addresses, setAddresses] = useState<{ value: number; label: string }[]>([]);
  const [grain, setGrain] = useState<DemoBoardGrain>('day');
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf('day'), dayjs().endOf('day')]);
  const lastQueryRef = useRef<HrPanelQuery>({});
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerTitle, setDrawerTitle] = useState('');
  const [drawerMetric, setDrawerMetric] = useState<HrPanelMetric | null>(null);
  const [drawerKind, setDrawerKind] = useState<'JOB' | 'PERSON'>('PERSON');
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [drawerPage, setDrawerPage] = useState(1);
  const [drawerTotal, setDrawerTotal] = useState(0);
  const [jobRows, setJobRows] = useState<HrPanelJobRow[]>([]);
  const [peopleRows, setPeopleRows] = useState<HrPanelPersonRow[]>([]);
  const [jobDetail, setJobDetail] = useState<HrPanelJobRow | null>(null);

  useEffect(() => {
    void getHrDepartmentsApi().then((tree) => setDepts(flattenDepts((tree ?? []) as unknown as Dept[])));
    void getHrUsersApi('owner').then((list) =>
      setOwners(
        (list ?? []).map((item) => ({
          value: Number(
            (item as { userId?: number; user_id?: number }).userId ?? (item as { user_id?: number }).user_id,
          ),
          label: String((item as { nickname?: string }).nickname || ''),
        })),
      ),
    );
    void getHrDictOptionsApi('JOB_PRIORITY').then((rows) =>
      setPriorities(
        (rows ?? []).map((row) => ({
          value: row.code || '',
          label: [row.code, row.name].filter(Boolean).join(' '),
        })),
      ),
    );
    void getHrDictOptionsApi('WORK_ADDRESS').then((rows) =>
      setAddresses(
        (rows ?? []).map((row) => ({
          value: row.id,
          label: row.category ? `${row.category} · ${row.name}` : row.name,
        })),
      ),
    );
  }, []);

  const onGrainChange = (g: DemoBoardGrain) => {
    setGrain(g);
    setRange(g === 'day' ? [dayjs().startOf('day'), dayjs().endOf('day')] : demoRangeByGrain(g));
  };

  const pickerProps =
    grain === 'week'
      ? { picker: 'week' as const }
      : grain === 'month'
        ? { picker: 'month' as const }
        : grain === 'year'
          ? { picker: 'year' as const }
          : { picker: 'date' as const };

  const dateQuery = useMemo(() => {
    const [start, end] = normalizeRange(grain, range[0], range[1]);
    return {
      grain,
      startDate: start.format('YYYY-MM-DD'),
      endDate: end.format('YYYY-MM-DD'),
    };
  }, [grain, range]);

  const columns: ProColumnType<HrPanelJobRow>[] = useMemo(
    () => [
      {
        title: '岗位状态',
        dataIndex: 'panelStatus',
        valueType: 'select',
        width: 100,
        initialValue: 'OPEN',
        fieldProps: { options: PANEL_STATUS, allowClear: true },
        render: (_, record) => (
          <Tag color={record.panelStatus === 'OPEN' ? 'processing' : 'default'}>{record.panelStatusLabel || '—'}</Tag>
        ),
      },
      {
        title: '需求部门',
        dataIndex: 'deptId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: depts, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: '需求部门',
        dataIndex: 'deptName',
        search: false,
        width: 140,
        ellipsis: true,
        render: (_, record) => record.deptName || '—',
      },
      {
        title: '岗位名称',
        dataIndex: 'jobName',
        ellipsis: true,
        width: 160,
        render: (_, record) => (
          <Button
            type='link'
            className='h-auto max-w-full truncate px-0'
            onClick={() => setJobDetail(record)}
          >
            {record.jobName || '—'}
          </Button>
        ),
      },
      {
        title: 'HC',
        dataIndex: 'headcount',
        search: false,
        width: 70,
        render: (_, record) => (record.headcount == null ? '—' : record.headcount),
      },
      {
        title: '招聘负责人',
        dataIndex: 'ownerUserIds',
        valueType: 'select',
        hideInTable: true,
        fieldProps: {
          mode: 'multiple',
          maxTagCount: 'responsive',
          options: owners,
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
        },
      },
      {
        title: '负责人',
        dataIndex: 'ownerName',
        search: false,
        width: 120,
        ellipsis: true,
        render: (_, record) => record.ownerName || '—',
      },
      {
        title: '岗位优先级',
        dataIndex: 'jobPriorityCode',
        valueType: 'select',
        width: 160,
        ellipsis: true,
        fieldProps: { options: priorities, allowClear: true, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.jobPriorityLabel || '—',
      },
      {
        title: '工作地址',
        dataIndex: 'workAddressId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: addresses, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: '工作地点',
        dataIndex: 'workLocation',
        search: false,
        width: 120,
        ellipsis: true,
        render: (_, record) => record.workLocation || '—',
      },
      {
        title: '接收日',
        dataIndex: 'receivedDate',
        search: false,
        width: 120,
        render: (_, record) => record.receivedDate || '—',
      },
      {
        title: '任务截止日期',
        dataIndex: 'deadlineDate',
        search: false,
        width: 130,
        render: (_, record) => record.deadlineDate || '—',
      },
      {
        title: '招聘天数',
        dataIndex: 'recruitingDays',
        search: false,
        width: 100,
        render: (_, record) => (record.recruitingDays == null ? '—' : record.recruitingDays),
      },
      {
        title: '区间进展',
        dataIndex: 'progressSummary',
        search: false,
        ellipsis: true,
        width: 280,
        render: (_, record) => record.progressSummary || '无',
      },
    ],
    [addresses, depts, owners, priorities],
  );

  const jobColumns: ColumnsType<HrPanelJobRow> = useMemo(
    () => [
      {
        title: '岗位状态',
        dataIndex: 'panelStatusLabel',
        width: 90,
        render: (_, record) => (
          <Tag color={record.panelStatus === 'OPEN' ? 'processing' : 'default'}>{record.panelStatusLabel || '—'}</Tag>
        ),
      },
      { title: '需求部门', dataIndex: 'deptName', ellipsis: true, width: 140, render: (v) => v || '—' },
      {
        title: '岗位名称',
        dataIndex: 'jobName',
        ellipsis: true,
        width: 160,
        render: (_, record) => (
          <Button
            type='link'
            className='h-auto max-w-full truncate px-0'
            onClick={() => setJobDetail(record)}
          >
            {record.jobName || '—'}
          </Button>
        ),
      },
      { title: 'HC', dataIndex: 'headcount', width: 70, render: (v) => (v == null ? '—' : v) },
      { title: '负责人', dataIndex: 'ownerName', ellipsis: true, width: 120, render: (v) => v || '—' },
      { title: '岗位优先级', dataIndex: 'jobPriorityLabel', width: 160, render: (v) => v || '—' },
      { title: '工作地点', dataIndex: 'workLocation', width: 120, render: (v) => v || '—' },
      { title: '接收日', dataIndex: 'receivedDate', width: 120, render: (v) => v || '—' },
      { title: '任务截止日期', dataIndex: 'deadlineDate', width: 130, render: (v) => v || '—' },
      { title: '招聘天数', dataIndex: 'recruitingDays', width: 90, render: (v) => (v == null ? '—' : v) },
      { title: '区间进展', dataIndex: 'progressSummary', ellipsis: true, render: (v) => v || '无' },
    ],
    [],
  );

  const peopleColumns: ColumnsType<HrPanelPersonRow> = useMemo(
    () => [
      { title: '候选人', dataIndex: 'candidateName', width: 120, render: (v) => v || '—' },
      { title: '岗位名称', dataIndex: 'jobName', ellipsis: true, width: 160, render: (v) => v || '—' },
      { title: '需求部门', dataIndex: 'deptName', ellipsis: true, width: 140, render: (v) => v || '—' },
      { title: '负责人', dataIndex: 'ownerName', width: 120, ellipsis: true, render: (v) => v || '—' },
      { title: '当前阶段', dataIndex: 'stageName', width: 120, render: (v) => v || '—' },
      { title: '发生时间', dataIndex: 'occurredAt', width: 150, render: (v) => v || '—' },
      { title: '结果', dataIndex: 'resultLabel', width: 100, render: (v) => v || '—' },
    ],
    [],
  );

  const loadDetail = async (metric: HrPanelMetric, page = 1) => {
    setDrawerLoading(true);
    try {
      const res = await getHrPanelDetailApi({
        ...lastQueryRef.current,
        metric,
        pageNum: page,
        pageSize: 10,
      });
      setDrawerKind(res?.kind === 'JOB' ? 'JOB' : 'PERSON');
      setDrawerTitle(res?.title || '');
      setDrawerTotal(res?.total ?? 0);
      setJobRows(res?.jobs ?? []);
      setPeopleRows(res?.people ?? []);
      setDrawerPage(page);
    } finally {
      setDrawerLoading(false);
    }
  };

  const openDetail = (metric: HrPanelMetric, title: string) => {
    setDrawerMetric(metric);
    setDrawerTitle(title);
    setDrawerOpen(true);
    void loadDetail(metric, 1);
  };

  const exportSheet = async () => {
    const sheet = sheetRef.current;
    if (!sheet || exporting) return;
    setExporting(true);
    const hide = message.loading('正在生成图片…', 0);
    try {
      await downloadElementImage(sheet, `招聘面板-${dateQuery.startDate}_${dateQuery.endDate}.png`);
      message.success('已导出图片');
    } catch {
      message.error('导出图片失败');
    } finally {
      hide();
      setExporting(false);
    }
  };

  return (
    <div
      ref={sheetRef}
      className='flex flex-col gap-4'
    >
      <Card
        size='small'
        title='招聘面板筛选'
        extra={
          <span data-export-ignore>
            <Button
              type='primary'
              ghost
              icon={<DownloadOutlined />}
              loading={exporting}
              onClick={() => void exportSheet()}
            >
              导出图片
            </Button>
          </span>
        }
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
              }
            }}
          />
          <span className='text-xs text-neutral-500'>
            当前统计：{dateQuery.startDate} ~ {dateQuery.endDate}
            （「目前在招岗位」与下方列表均为当前筛选下的需求条数，不受日期影响；进入背调/待入职、筛选简历、电话、约面、面试及列表「区间进展」按该区间）
          </span>
        </Space>
      </Card>

      <BaseProTable<HrPanelJobRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='招聘面板'
        columns={columns}
        params={dateQuery}
        pagination={{ defaultPageSize: 10 }}
        scroll={{ x: 1480 }}
        tableExtraRender={() => (
          <div className='mb-3 grid grid-cols-2 gap-3 md:grid-cols-4'>
            {STATS.map((item) => (
              <Card
                key={item.key}
                size='small'
                className='cursor-pointer hover:border-blue-300'
                onClick={() => openDetail(item.metric, item.title)}
              >
                <Statistic
                  title={item.title}
                  value={stats[item.key] ?? 0}
                  suffix={item.suffix}
                  valueStyle={{ color: '#1677ff' }}
                />
              </Card>
            ))}
          </div>
        )}
        request={async (params) => {
          const startDate = String(params.startDate || dateQuery.startDate || '');
          const endDate = String(params.endDate || dateQuery.endDate || '');
          const grain = (params.grain as HrPanelQuery['grain']) || dateQuery.grain;
          const query: HrPanelQuery = {
            grain,
            startDate: startDate || undefined,
            endDate: endDate || undefined,
            panelStatus: (params.panelStatus as string) || undefined,
            deptId: numId(params.deptId),
            jobName: (params.jobName as string) || undefined,
            ownerUserIds: Array.isArray(params.ownerUserIds)
              ? (params.ownerUserIds as unknown[]).map((v) => Number(v)).filter((n) => Number.isFinite(n) && n > 0)
              : undefined,
            jobPriorityCode: (params.jobPriorityCode as string) || undefined,
            workAddressId: numId(params.workAddressId),
          };
          lastQueryRef.current = query;
          const res = await getHrPanelApi({
            ...query,
            pageNum: params.current,
            pageSize: params.pageSize,
          });
          setStats(res?.stats ?? {});
          return { data: res?.rows ?? [], success: true, total: res?.total ?? 0 };
        }}
      />
      <Drawer
        title={drawerTitle || '指标明细'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={960}
        destroyOnHidden
      >
        {drawerKind === 'JOB' ? (
          <Table<HrPanelJobRow>
            rowKey='id'
            size='small'
            loading={drawerLoading}
            dataSource={jobRows}
            columns={jobColumns}
            scroll={{ x: 1200 }}
            pagination={{
              current: drawerPage,
              pageSize: 10,
              total: drawerTotal,
              showSizeChanger: false,
              onChange: (page) => drawerMetric && void loadDetail(drawerMetric, page),
            }}
          />
        ) : (
          <Table<HrPanelPersonRow>
            rowKey='applicationId'
            size='small'
            loading={drawerLoading}
            dataSource={peopleRows}
            columns={peopleColumns}
            scroll={{ x: 1000 }}
            pagination={{
              current: drawerPage,
              pageSize: 10,
              total: drawerTotal,
              showSizeChanger: false,
              onChange: (page) => drawerMetric && void loadDetail(drawerMetric, page),
            }}
          />
        )}
      </Drawer>
      <HrJobDetailDrawer
        open={jobDetail != null}
        requisitionId={jobDetail?.id}
        fallback={{
          jobName: jobDetail?.jobName,
          deptName: jobDetail?.deptName,
          cardStatusLabel: jobDetail?.panelStatusLabel,
          ownerName: jobDetail?.ownerName,
          headcount: jobDetail?.headcount,
          workLocation: jobDetail?.workLocation,
          jobPriorityLabel: jobDetail?.jobPriorityLabel,
          jobPriorityCode: jobDetail?.jobPriorityCode,
          receivedDate: jobDetail?.receivedDate,
          status: jobDetail?.panelStatus,
          statusLabel: jobDetail?.panelStatusLabel,
        }}
        onClose={() => setJobDetail(null)}
      />
    </div>
  );
});

export default HrPanelPage;
