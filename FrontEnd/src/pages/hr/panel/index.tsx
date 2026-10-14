import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { Card, Drawer, Statistic, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import BaseProTable from '@/components/BaseProTable';
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

const STATS: { key: keyof HrPanelStats; metric: HrPanelMetric; title: string; suffix: string }[] = [
  { key: 'openJobCount', metric: 'OPEN_JOB', title: '目前在招岗位', suffix: '个' },
  { key: 'bgCheckCount', metric: 'BG_CHECK', title: '背调中', suffix: '人' },
  { key: 'pendingOnboardCount', metric: 'PENDING_ONBOARD', title: '待入职', suffix: '人' },
  { key: 'todayScreenCount', metric: 'TODAY_SCREEN', title: '今日筛选简历', suffix: '人' },
  { key: 'todayPhoneCount', metric: 'TODAY_PHONE', title: '今日电话沟通', suffix: '人' },
  { key: 'todayInviteCount', metric: 'TODAY_INVITE', title: '今日约面', suffix: '人' },
  { key: 'todayInterviewCount', metric: 'TODAY_INTERVIEW', title: '今日实际面试', suffix: '人' },
  { key: 'todayInterviewPassCount', metric: 'TODAY_INTERVIEW_PASS', title: '今日通过面试', suffix: '人' },
];

const HrPanelPage = memo(function HrPanelPage() {
  const actionRef = useRef<ActionType>(null);
  const [stats, setStats] = useState<HrPanelStats>({});
  const [depts, setDepts] = useState<{ value: number; label: string }[]>([]);
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [priorities, setPriorities] = useState<{ value: string; label: string }[]>([]);
  const [addresses, setAddresses] = useState<{ value: number; label: string }[]>([]);
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

  const columns: ProColumnType<HrPanelJobRow>[] = useMemo(
    () => [
      {
        title: '岗位状态',
        dataIndex: 'panelStatus',
        valueType: 'select',
        width: 100,
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
      },
      {
        title: '负责人',
        dataIndex: 'ownerUserId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: owners, allowClear: true, showSearch: true, optionFilterProp: 'label' },
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
        title: '累计进展',
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
      { title: '岗位名称', dataIndex: 'jobName', ellipsis: true, width: 160 },
      { title: '负责人', dataIndex: 'ownerName', ellipsis: true, width: 120, render: (v) => v || '—' },
      { title: '岗位优先级', dataIndex: 'jobPriorityLabel', width: 160, render: (v) => v || '—' },
      { title: '工作地点', dataIndex: 'workLocation', width: 120, render: (v) => v || '—' },
      { title: '接收日', dataIndex: 'receivedDate', width: 120, render: (v) => v || '—' },
      { title: '任务截止日期', dataIndex: 'deadlineDate', width: 130, render: (v) => v || '—' },
      { title: '招聘天数', dataIndex: 'recruitingDays', width: 90, render: (v) => (v == null ? '—' : v) },
      { title: '累计进展', dataIndex: 'progressSummary', ellipsis: true, render: (v) => v || '无' },
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

  return (
    <>
      <BaseProTable<HrPanelJobRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='招聘面板'
        columns={columns}
        pagination={{ defaultPageSize: 10 }}
        scroll={{ x: 1400 }}
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
          const query: HrPanelQuery = {
            panelStatus: (params.panelStatus as string) || undefined,
            deptId: numId(params.deptId),
            jobName: (params.jobName as string) || undefined,
            ownerUserId: numId(params.ownerUserId),
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
    </>
  );
});

export default HrPanelPage;
