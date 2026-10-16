import { useCallback, useEffect, useState } from 'react';
import { App, Card, Drawer, Empty, Input, Pagination, Select, Space, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ClockCircleOutlined, PlusOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeDrawer } from '@/components/hr/ResumeDrawer';
import ApplicationFormModal from '@/pages/hr/application/components/ApplicationFormModal';
import ImportApplicationModal from '@/pages/hr/application/components/ImportApplicationModal';
import {
  getHrChannelsApi,
  getHrDepartmentsApi,
  getHrDictOptionsApi,
  getHrJobCardsApi,
  getHrJobCandidatesApi,
  getHrStagesApi,
  getHrUsersApi,
  type HrJobCard,
  type HrJobCardQuery,
  type HrJobCardStatus,
  type HrJobCandidate,
  type HrJobFunnelStatus,
} from '@/api/hr';

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

function JobRowCard({ job, onOpen }: { job: HrJobCard; onOpen: (job: HrJobCard, funnel?: HrJobFunnelStatus) => void }) {
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
            value={job.pendingCount}
            label='未处理'
            onClick={() => onOpen(job, 'PENDING')}
          />
          <Metric
            value={job.interviewingCount}
            label='约面中'
            onClick={() => onOpen(job, 'INTERVIEWING')}
          />
          <Metric
            value={job.onboardedCount}
            label='已入职'
            onClick={() => onOpen(job, 'ONBOARDED')}
          />
          <Metric
            value={job.todayScreenCount}
            label='今日筛选简历'
            onClick={() => onOpen(job, 'TODAY_SCREEN')}
          />
          <Metric
            value={job.todayPhoneCount}
            label='今日电话沟通'
            onClick={() => onOpen(job, 'TODAY_PHONE')}
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
    title: '推荐时间',
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
    title: '负责人',
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
    void getHrStagesApi().then((rows) =>
      setStages(
        (rows ?? []).map((item) => ({
          value: String(item.stageCode ?? item.stage_code),
          label: String(item.stageName ?? item.stage_name),
        })),
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
    void loadCandidates(job, 1, funnel);
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
            optionFilterProp='label'
            placeholder='按负责人筛选'
            className='min-w-[160px]'
            options={owners}
            value={filters.ownerUserId}
            onChange={(ownerUserId) => patchFilter({ ownerUserId })}
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
      </Drawer>
      {resumeRow ? (
        <ResumeDrawer
          applicationId={resumeRow.applicationId}
          fileName={resumeRow.resumeName}
          open
          onClose={() => setResumeRow(null)}
          onActionSuccess={() => {
            if (drawerJob) void loadCandidates(drawerJob, drawerPage, drawerFunnel);
            void load();
          }}
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
          if (drawerJob) void loadCandidates(drawerJob, 1, drawerFunnel);
          void load();
        }}
      />
      <ImportApplicationModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => {
          if (drawerJob) void loadCandidates(drawerJob, 1, drawerFunnel);
          void load();
        }}
      />
    </div>
  );
}
