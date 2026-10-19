import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Drawer, Empty, Input, Pagination, Select, Space, Spin, Table, Tabs, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ClockCircleOutlined, EyeOutlined, PlusOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeDrawer } from '@/components/hr/ResumeDrawer';
import { HrJobDetailDrawer } from '@/components/hr/HrJobDetailDrawer';
import ApplicationFormModal from '@/pages/hr/application/components/ApplicationFormModal';
import ImportApplicationModal from '@/pages/hr/application/components/ImportApplicationModal';
import {
  getHrChannelsApi,
  getHrDepartmentsApi,
  getHrDictOptionsApi,
  getHrJobCardsApi,
  getHrJobCandidatesApi,
  getHrJobCommStatsApi,
  getHrStageCatalogApi,
  getHrStagesApi,
  getHrUsersApi,
  type HrJobCard,
  type HrJobCardQuery,
  type HrJobCardStatus,
  type HrJobCandidate,
  type HrJobCommDetail,
  type HrJobCommPieSlice,
  type HrJobCommStats,
  type HrJobFunnelStatus,
} from '@/api/hr';

const Pie = lazy(() => import('@/components/geo/GeoAntCharts').then((mod) => ({ default: mod.Pie })));

const FUNNEL_LABEL: Record<HrJobFunnelStatus, string> = {
  PENDING: '未处理',
  INTERVIEWING: '约面中',
  ONBOARDED: '已入职',
  TODAY_SCREEN: '今日筛选简历',
  TODAY_PHONE: '今日电话沟通',
  TODAY_INVITE: '今日约面',
  TODAY_INTERVIEW: '今日实际面试',
  TODAY_INTERVIEW_PASS: '今日通过面试',
  BG_CHECK: '背调中',
  PENDING_ONBOARD: '待入职',
};

const CARD_STATUS: { label: string; value: HrJobCardStatus }[] = [
  { label: '待招', value: 'PENDING' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '已关闭', value: 'CLOSED' },
];

const PRIORITY = [
  { label: '紧急', value: 1 },
  { label: '优先', value: 2 },
  { label: '常规', value: 3 },
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

function fmtDate(value?: string) {
  if (!value) return '—';
  return String(value).slice(0, 10).replaceAll('-', '/');
}

function remainingLabel(hours?: number | null) {
  if (hours == null || Number.isNaN(hours)) return '—';
  if (hours < 0) return <span className='text-red-500'>已超时</span>;
  return (
    <span className='inline-flex items-center gap-1 text-orange-500'>
      <ClockCircleOutlined />
      剩余{hours.toFixed(2)}小时
    </span>
  );
}

function Metric({ value, label, onClick }: { value?: number; label: string; onClick: () => void }) {
  return (
    <button
      type='button'
      className='flex min-w-[72px] flex-1 flex-col items-center justify-center px-2 py-2 text-center hover:bg-neutral-50'
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
    >
      <span className='text-xl leading-none font-semibold text-neutral-800'>{value ?? 0}</span>
      <span className='mt-1 text-xs text-neutral-500'>{label}</span>
    </button>
  );
}

function JobRowCard({
  job,
  onOpen,
  onViewDetail,
}: {
  job: HrJobCard;
  onOpen: (job: HrJobCard, funnel?: HrJobFunnelStatus) => void;
  onViewDetail: (job: HrJobCard) => void;
}) {
  const tags: string[] = [];
  if (job.jobPriorityLabel) tags.push(job.jobPriorityLabel);
  if (job.priorityLabel) tags.push(`【${job.priorityLabel}】`);
  if (job.cardStatusLabel) tags.push(job.cardStatusLabel);

  return (
    <div
      className='cursor-pointer overflow-hidden rounded border border-neutral-200 bg-white hover:border-blue-300'
      onClick={() => onOpen(job)}
    >
      <div className='flex flex-wrap items-center gap-2 bg-neutral-100 px-4 py-2'>
        {tags.length === 0 ? (
          <span className='text-xs text-neutral-400'>未标注优先级</span>
        ) : (
          tags.map((tag) => (
            <Tag
              key={tag}
              className='m-0 rounded-full border-neutral-200 bg-white text-neutral-600'
            >
              {tag}
            </Tag>
          ))
        )}
        <Button
          type='link'
          size='small'
          className='h-auto px-1'
          icon={<EyeOutlined />}
          onClick={(e) => {
            e.stopPropagation();
            onViewDetail(job);
          }}
        >
          查看岗位详情
        </Button>
      </div>
      <div className='flex flex-col xl:flex-row xl:items-stretch'>
        <div className='min-w-0 flex-1 px-4 py-3'>
          <div className='flex flex-wrap items-baseline gap-x-4 gap-y-1'>
            <span
              className='max-w-full truncate text-base font-medium text-blue-600'
              title={job.jobName}
            >
              {job.jobName}
            </span>
            <span
              className='truncate text-sm text-orange-500'
              title={job.ownerNames || '未指定负责人'}
            >
              负责人：{job.ownerNames?.trim() || '未指定'}
            </span>
          </div>
          <div
            className='mt-2 truncate text-sm text-neutral-500'
            title={[job.workLocation, job.salaryRange, job.receivedDate].filter(Boolean).join(' | ')}
          >
            <span>{job.workLocation || job.deptName || '—'}</span>
            <span className='mx-2 text-neutral-300'>|</span>
            <span>年薪：{job.salaryRange?.trim() || '—'}</span>
            <span className='mx-2 text-neutral-300'>|</span>
            <span>招聘起始：{fmtDate(job.receivedDate)}</span>
          </div>
        </div>
        <div className='flex shrink-0 divide-x divide-neutral-200 overflow-x-auto border-t border-neutral-100 xl:border-t-0 xl:border-l'>
          <Metric
            value={job.todayScreenCount}
            label='今日筛选简历'
            onClick={() => onOpen(job, 'TODAY_SCREEN')}
          />
          <Metric
            value={job.pendingCount}
            label='未处理'
            onClick={() => onOpen(job, 'PENDING')}
          />
          <Metric
            value={job.todayPhoneCount}
            label='今日电话沟通'
            onClick={() => onOpen(job, 'TODAY_PHONE')}
          />
          <Metric
            value={job.interviewingCount}
            label='约面中'
            onClick={() => onOpen(job, 'INTERVIEWING')}
          />
          <Metric
            value={job.todayInviteCount}
            label='今日约面'
            onClick={() => onOpen(job, 'TODAY_INVITE')}
          />
          <Metric
            value={job.todayInterviewCount}
            label='今日实际面试'
            onClick={() => onOpen(job, 'TODAY_INTERVIEW')}
          />
          <Metric
            value={job.todayInterviewPassCount}
            label='今日通过面试'
            onClick={() => onOpen(job, 'TODAY_INTERVIEW_PASS')}
          />
          <Metric
            value={job.bgCheckCount}
            label='背调中'
            onClick={() => onOpen(job, 'BG_CHECK')}
          />
          <Metric
            value={job.pendingOnboardCount}
            label='待入职'
            onClick={() => onOpen(job, 'PENDING_ONBOARD')}
          />
          <Metric
            value={job.onboardedCount}
            label='已入职'
            onClick={() => onOpen(job, 'ONBOARDED')}
          />
        </div>
      </div>
    </div>
  );
}

const candidateColumns: ColumnsType<HrJobCandidate> = [
  {
    title: '候选人',
    dataIndex: 'displayName',
    width: 120,
    render: (_, record) => (
      <span className='inline-flex items-center gap-1.5'>
        <span className={`inline-block h-1.5 w-1.5 rounded-full ${record.pending ? 'bg-red-500' : 'bg-blue-500'}`} />
        <span className='text-blue-600'>{record.displayName || '—'}</span>
      </span>
    ),
  },
  {
    title: '基本信息',
    dataIndex: 'basicInfo',
    width: 160,
    ellipsis: true,
    render: (v) => v || '—',
  },
  {
    title: '人选编号',
    dataIndex: 'candidateNo',
    width: 110,
    render: (v) => v || '—',
  },
  {
    title: '目前职位',
    dataIndex: 'currentTitle',
    width: 160,
    ellipsis: true,
    render: (v) => v || '—',
  },
  {
    title: '目前公司',
    dataIndex: 'currentCompany',
    width: 200,
    ellipsis: true,
    render: (v) => v || '—',
  },
  {
    title: '提交时间',
    dataIndex: 'recommendedAt',
    width: 120,
    render: (v) => (v ? String(v).slice(0, 10) : '—'),
  },
  {
    title: '剩余处理时间',
    dataIndex: 'remainingHours',
    width: 150,
    render: (v) => remainingLabel(typeof v === 'number' ? v : null),
  },
  {
    title: '提交人',
    dataIndex: 'referrerName',
    width: 120,
    ellipsis: true,
    render: (v) =>
      v ? (
        <span className='inline-flex items-center gap-1'>
          <UserOutlined />
          {v}
        </span>
      ) : (
        '—'
      ),
  },
];

const COMM_DETAIL_COLUMNS: ColumnsType<HrJobCommDetail> = [
  {
    title: '类型',
    dataIndex: 'recordTypeLabel',
    width: 120,
    fixed: 'left',
    render: (v, row) => v || row.recordType || '—',
  },
  {
    title: '姓名',
    dataIndex: 'displayName',
    width: 100,
    ellipsis: true,
  },
  {
    title: '原因',
    dataIndex: 'reason',
    width: 160,
    ellipsis: true,
    render: (v) => v || '未填写',
  },
  {
    title: '备注',
    dataIndex: 'remark',
    width: 180,
    ellipsis: true,
    render: (v) => v || '—',
  },
  {
    title: '时间',
    dataIndex: 'occurredAt',
    width: 160,
    render: (v) => v || '—',
  },
  {
    title: '当前阶段',
    dataIndex: 'stageName',
    width: 120,
    ellipsis: true,
    render: (v, row) => v || row.currentStage || '—',
  },
];

type CommFilter = { recordType?: string; reason?: string };

function ReasonPie({
  title,
  data,
  activeReason,
  onSliceClick,
}: {
  title: string;
  data: HrJobCommPieSlice[];
  activeReason?: string;
  onSliceClick: (reason: string) => void;
}) {
  return (
    <Card
      size='small'
      title={title}
      className='shadow-sm'
      styles={{ body: { minHeight: 280 } }}
    >
      {data.length === 0 ? (
        <div className='flex h-[240px] items-center justify-center text-sm text-neutral-400'>暂无数据</div>
      ) : (
        <Suspense
          fallback={<div className='flex h-[240px] items-center justify-center text-sm text-neutral-400'>加载中…</div>}
        >
          <Pie
            data={data}
            angleField='value'
            colorField='name'
            height={240}
            legend={{ position: 'bottom' }}
            label={{ text: 'name' }}
            tooltip={{
              items: [{ channel: 'y', valueFormatter: (value: number) => `${value} 人` }],
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
                onSliceClick(reason);
              });
            }}
          />
        </Suspense>
      )}
      {activeReason ? (
        <div className='mt-1 text-center text-xs text-neutral-500'>已筛选原因：{activeReason}（再点同一块可取消）</div>
      ) : null}
    </Card>
  );
}

export default function HrJobBoardPage() {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<HrJobCard[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [depts, setDepts] = useState<{ value: number; label: string }[]>([]);
  const [owners, setOwners] = useState<{ value: number; label: string }[]>([]);
  const [priorities, setPriorities] = useState<{ value: string; label: string }[]>([]);
  const [addresses, setAddresses] = useState<{ value: number; label: string }[]>([]);
  const [filters, setFilters] = useState<HrJobCardQuery>({});
  const [drawerJob, setDrawerJob] = useState<HrJobCard | null>(null);
  const [drawerFunnel, setDrawerFunnel] = useState<HrJobFunnelStatus | undefined>();
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [drawerPage, setDrawerPage] = useState(1);
  const [drawerTotal, setDrawerTotal] = useState(0);
  const [candidates, setCandidates] = useState<HrJobCandidate[]>([]);
  const [resumeRow, setResumeRow] = useState<HrJobCandidate | null>(null);
  const [manualOpen, setManualOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [stages, setStages] = useState<{ value: string; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [commStats, setCommStats] = useState<HrJobCommStats | null>(null);
  const [commLoading, setCommLoading] = useState(false);
  const [commFilter, setCommFilter] = useState<CommFilter>({});
  const [drawerTab, setDrawerTab] = useState('candidates');
  const [detailJob, setDetailJob] = useState<HrJobCard | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getHrJobCardsApi({ ...filters, pageNum, pageSize });
      setRows(data?.rows || []);
      setTotal(data?.total || 0);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载岗位看板失败');
    } finally {
      setLoading(false);
    }
  }, [filters, message, pageNum, pageSize]);

  const loadCandidates = useCallback(
    async (job: HrJobCard, page: number, funnel?: HrJobFunnelStatus) => {
      setDrawerLoading(true);
      try {
        const res = await getHrJobCandidatesApi({
          requisitionId: job.id,
          funnelStatus: funnel,
          pageNum: page,
          pageSize: 10,
        });
        setCandidates(res?.rows || []);
        setDrawerTotal(res?.total || 0);
        setDrawerPage(page);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '加载候选人失败');
      } finally {
        setDrawerLoading(false);
      }
    },
    [message],
  );

  const loadCommStats = useCallback(
    async (job: HrJobCard) => {
      setCommLoading(true);
      try {
        setCommStats(await getHrJobCommStatsApi({ requisitionId: job.id }));
      } catch (e) {
        message.error(e instanceof Error ? e.message : '加载沟通统计失败');
        setCommStats(null);
      } finally {
        setCommLoading(false);
      }
    },
    [message],
  );

  const filteredCommDetails = useMemo(() => {
    const rows = commStats?.details || [];
    return rows.filter((row) => {
      if (commFilter.recordType && row.recordType !== commFilter.recordType) return false;
      if (commFilter.reason) {
        const reason = (row.reason || '未填写').trim();
        if (reason !== commFilter.reason) return false;
      }
      return true;
    });
  }, [commFilter, commStats?.details]);

  const togglePieFilter = (recordType: string, reason: string) => {
    setCommFilter((prev) => (prev.recordType === recordType && prev.reason === reason ? {} : { recordType, reason }));
  };

  useEffect(() => {
    getHrDepartmentsApi().then((tree) => setDepts(flattenDepts(tree as unknown as Dept[])));
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
    void getHrChannelsApi().then((rows) =>
      setChannels(
        (rows ?? []).map((item) => {
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
    );
    void getHrStageCatalogApi()
      .then((catalog) => {
        const stages = catalog?.stages ?? [];
        if (stages.length) {
          setStages(stages.map((item) => ({ value: String(item.code), label: String(item.name) })));
          return;
        }
        return getHrStagesApi().then((rows) =>
          setStages(
            (rows ?? []).map((item) => ({
              value: String(item.stageCode ?? item.stage_code),
              label: String(item.stageName ?? item.stage_name),
            })),
          ),
        );
      })
      .catch(() =>
        getHrStagesApi().then((rows) =>
          setStages(
            (rows ?? []).map((item) => ({
              value: String(item.stageCode ?? item.stage_code),
              label: String(item.stageName ?? item.stage_name),
            })),
          ),
        ),
      );
    void getHrUsersApi().then((list) =>
      setUsers(
        (list ?? []).map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname?: string; username?: string };
          return {
            value: Number(row.userId ?? row.user_id),
            label: String(row.nickname || row.username || ''),
          };
        }),
      ),
    );
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const patchFilter = (patch: HrJobCardQuery) => {
    setPageNum(1);
    setFilters((prev) => ({ ...prev, ...patch }));
  };

  const openJob = (job: HrJobCard, funnel?: HrJobFunnelStatus) => {
    setDrawerJob(job);
    setDrawerFunnel(funnel);
    setDrawerTab('candidates');
    setCommFilter({});
    void loadCandidates(job, 1, funnel);
    void loadCommStats(job);
  };

  const refreshDrawer = () => {
    if (!drawerJob) return;
    void loadCandidates(drawerJob, drawerPage, drawerFunnel);
    void loadCommStats(drawerJob);
    void load();
  };

  return (
    <div className='space-y-4'>
      <Card
        size='small'
        className='shadow-sm'
        styles={{ body: { padding: '12px 14px' } }}
      >
        <Space
          wrap
          size={[12, 8]}
        >
          <Input.Search
            allowClear
            placeholder='按岗位名称筛选'
            className='w-[200px]'
            defaultValue={filters.jobName}
            onSearch={(value) => patchFilter({ jobName: value.trim() || undefined })}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='按部门筛选'
            className='min-w-[180px]'
            options={depts}
            value={filters.deptId}
            onChange={(deptId) => patchFilter({ deptId })}
          />
          <Select
            allowClear
            showSearch
            mode='multiple'
            maxTagCount='responsive'
            optionFilterProp='label'
            placeholder='招聘负责人'
            className='min-w-[200px]'
            options={owners}
            value={filters.ownerUserIds}
            onChange={(ownerUserIds) =>
              patchFilter({ ownerUserIds: ownerUserIds?.length ? ownerUserIds : undefined, ownerUserId: undefined })
            }
          />
          <Select
            allowClear
            placeholder='按岗位状态筛选'
            className='min-w-[140px]'
            options={CARD_STATUS}
            value={filters.cardStatus}
            onChange={(cardStatus) => patchFilter({ cardStatus })}
          />
          <Select
            allowClear
            placeholder='按紧急程度筛选'
            className='min-w-[140px]'
            options={PRIORITY}
            value={filters.priority}
            onChange={(priority) => patchFilter({ priority })}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='按岗位优先级筛选'
            className='min-w-[180px]'
            options={priorities}
            value={filters.jobPriorityCode}
            onChange={(jobPriorityCode) => patchFilter({ jobPriorityCode })}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='按工作地址筛选'
            className='min-w-[180px]'
            options={addresses}
            value={filters.workAddressId}
            onChange={(workAddressId) => patchFilter({ workAddressId })}
          />
        </Space>
      </Card>

      <Spin spinning={loading}>
        {rows.length === 0 ? (
          <Card className='shadow-sm'>
            <Empty description='暂无符合条件的岗位' />
          </Card>
        ) : (
          <div className='space-y-3'>
            {rows.map((job) => (
              <JobRowCard
                key={job.id}
                job={job}
                onOpen={openJob}
                onViewDetail={setDetailJob}
              />
            ))}
          </div>
        )}
      </Spin>

      <div className='flex justify-end'>
        <Pagination
          current={pageNum}
          pageSize={pageSize}
          total={total}
          showSizeChanger
          showQuickJumper
          pageSizeOptions={[10, 20, 50]}
          showTotal={(n) => `共 ${n} 个岗位`}
          onChange={(page, size) => {
            setPageNum(page);
            setPageSize(size);
          }}
        />
      </div>

      <Drawer
        title={drawerJob ? `${drawerJob.jobName} · ${drawerFunnel ? FUNNEL_LABEL[drawerFunnel] : '候选人'}` : '候选人'}
        open={drawerJob != null}
        onClose={() => {
          setDrawerJob(null);
          setDrawerFunnel(undefined);
          setCandidates([]);
          setResumeRow(null);
          setCommStats(null);
          setCommFilter({});
          setDrawerTab('candidates');
        }}
        width={1100}
        destroyOnHidden
        extra={
          drawerJob ? (
            <Space wrap>
              <PermissionButton
                perm='hr:application:add'
                icon={<UploadOutlined />}
                onClick={() => setImportOpen(true)}
              >
                导入候选人
              </PermissionButton>
              <PermissionButton
                perm='hr:application:add'
                type='primary'
                icon={<PlusOutlined />}
                onClick={() => setManualOpen(true)}
              >
                新增候选人
              </PermissionButton>
            </Space>
          ) : null
        }
      >
        <Tabs
          activeKey={drawerTab}
          onChange={setDrawerTab}
          items={[
            {
              key: 'candidates',
              label: '候选人列表',
              children: (
                <Table<HrJobCandidate>
                  rowKey='applicationId'
                  size='small'
                  loading={drawerLoading}
                  dataSource={candidates}
                  columns={candidateColumns}
                  scroll={{ x: 1100 }}
                  onRow={(record) => ({
                    className: 'cursor-pointer',
                    onClick: () => setResumeRow(record),
                  })}
                  pagination={{
                    current: drawerPage,
                    pageSize: 10,
                    total: drawerTotal,
                    showSizeChanger: false,
                    onChange: (page) => drawerJob && void loadCandidates(drawerJob, page, drawerFunnel),
                  }}
                />
              ),
            },
            {
              key: 'comm',
              label: '沟通统计',
              children: (
                <Spin spinning={commLoading}>
                  <div className='space-y-4'>
                    <div className='grid gap-3 md:grid-cols-2'>
                      <ReasonPie
                        title='电话不通过原因'
                        data={commStats?.phoneFailReasons || []}
                        activeReason={commFilter.recordType === 'PHONE_FAIL' ? commFilter.reason : undefined}
                        onSliceClick={(reason) => togglePieFilter('PHONE_FAIL', reason)}
                      />
                      <ReasonPie
                        title='标记不合适原因'
                        data={commStats?.screenFailReasons || []}
                        activeReason={commFilter.recordType === 'SCREEN_FAIL' ? commFilter.reason : undefined}
                        onSliceClick={(reason) => togglePieFilter('SCREEN_FAIL', reason)}
                      />
                    </div>
                    <Card
                      size='small'
                      title={
                        <div className='flex flex-wrap items-center gap-2'>
                          <span>沟通明细</span>
                          {commFilter.reason ? (
                            <Tag
                              closable
                              onClose={() => setCommFilter({})}
                            >
                              {(commFilter.recordType === 'PHONE_FAIL' ? '电话不通过' : '标记不合适') +
                                ' · ' +
                                commFilter.reason}
                            </Tag>
                          ) : null}
                        </div>
                      }
                      className='shadow-sm'
                    >
                      <Table<HrJobCommDetail>
                        rowKey={(row) => `${row.recordType}-${row.applicationId}-${row.occurredAt || ''}`}
                        size='small'
                        dataSource={filteredCommDetails}
                        columns={COMM_DETAIL_COLUMNS}
                        scroll={{ x: 900 }}
                        pagination={{ pageSize: 10, showSizeChanger: false }}
                        locale={{ emptyText: <Empty description='暂无电话不通过或标记不合适记录' /> }}
                        onRow={(record) => ({
                          className: record.resumeName ? 'cursor-pointer' : undefined,
                          onClick: () => {
                            if (!record.applicationId) return;
                            setResumeRow({
                              applicationId: record.applicationId,
                              displayName: record.displayName,
                              resumeName: record.resumeName,
                            } as HrJobCandidate);
                          },
                        })}
                      />
                    </Card>
                  </div>
                </Spin>
              ),
            },
          ]}
        />
      </Drawer>
      {resumeRow ? (
        <ResumeDrawer
          applicationId={resumeRow.applicationId}
          fileName={resumeRow.resumeName}
          open
          onClose={() => setResumeRow(null)}
          onActionSuccess={refreshDrawer}
          showCommRecords
        />
      ) : null}
      <ApplicationFormModal
        open={manualOpen}
        editing={null}
        jobs={drawerJob ? [{ value: drawerJob.id, label: drawerJob.jobName || `岗位#${drawerJob.id}` }] : []}
        channels={channels}
        stages={stages}
        users={users}
        presetRequisitionId={drawerJob?.id}
        onOpenChange={setManualOpen}
        onSuccess={() => {
          if (drawerJob) {
            void loadCandidates(drawerJob, 1, drawerFunnel);
            void loadCommStats(drawerJob);
          }
          void load();
        }}
      />
      <ImportApplicationModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => {
          if (drawerJob) {
            void loadCandidates(drawerJob, 1, drawerFunnel);
            void loadCommStats(drawerJob);
          }
          void load();
        }}
      />
      <HrJobDetailDrawer
        open={detailJob != null}
        requisitionId={detailJob?.id}
        fallback={detailJob}
        onClose={() => setDetailJob(null)}
      />
    </div>
  );
}
