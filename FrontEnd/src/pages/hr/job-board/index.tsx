import { useCallback, useEffect, useState } from 'react';
import { App, Card, Drawer, Empty, Input, Pagination, Select, Space, Spin, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ClockCircleOutlined, UserOutlined } from '@ant-design/icons';
import { ResumeDrawer } from '@/components/hr/ResumeDrawer';
import {
  getHrDepartmentsApi,
  getHrDictOptionsApi,
  getHrJobCardsApi,
  getHrJobCandidatesApi,
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
      className='flex min-w-[88px] flex-1 flex-col items-center justify-center px-3 py-1 text-center hover:bg-neutral-50'
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
      <div className='flex flex-col md:flex-row md:items-stretch'>
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
        <div className='flex divide-x divide-neutral-200 border-t border-neutral-100 md:w-[300px] md:border-t-0 md:border-l'>
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
    </div>
  );
}
