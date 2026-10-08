import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Card, Empty, Select, Space, Spin, Tag, Typography } from 'antd';
import { ClockCircleOutlined, EnvironmentOutlined, FlagOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import {
  getHrDepartmentsApi,
  getHrJobCardsApi,
  type HrJobCard,
  type HrJobCardGroup,
  type HrJobCardQuery,
  type HrJobCardStatus,
} from '@/api/hr';

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

function statusColor(status?: HrJobCardStatus) {
  if (status === 'PENDING') return 'gold';
  if (status === 'ACTIVE') return 'processing';
  return 'default';
}

function priorityColor(priority?: number) {
  if (priority === 1) return 'red';
  if (priority === 2) return 'orange';
  return 'blue';
}

function locationLabel(code?: string) {
  if (code === 'XJ') return '新疆';
  if (code === 'SH') return '上海';
  return code || '';
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className='min-w-0'>
      <div className='text-[11px] text-neutral-400'>{label}</div>
      <div
        className='truncate text-sm text-neutral-800'
        title={value || undefined}
      >
        {value?.trim() ? value : '—'}
      </div>
    </div>
  );
}

function JobCardItem({ job, onOwnerClick }: { job: HrJobCard; onOwnerClick: (ownerUserId?: number) => void }) {
  const firstOwnerId = job.ownerUserIds?.[0];
  return (
    <Card
      size='small'
      hoverable
      className='h-full shadow-sm transition-shadow hover:shadow-md'
      styles={{ body: { padding: 14 } }}
    >
      <div className='mb-2 flex flex-wrap items-start justify-between gap-2'>
        <Typography.Title
          level={5}
          className='!mb-0 !text-base'
          ellipsis={{ rows: 2, tooltip: job.jobName }}
        >
          {job.jobName}
        </Typography.Title>
        <Space
          size={4}
          wrap
        >
          <Tag color={statusColor(job.cardStatus)}>{job.cardStatusLabel || '—'}</Tag>
          {job.priorityLabel ? <Tag color={priorityColor(job.priority)}>{job.priorityLabel}</Tag> : null}
        </Space>
      </div>

      <div className='mb-3 grid grid-cols-2 gap-x-3 gap-y-2'>
        <Field
          label='薪资范围'
          value={job.salaryRange}
        />
        <Field
          label='简历要求'
          value={job.resumeRequirement}
        />
        <Field
          label='招聘起始'
          value={job.receivedDate?.slice(0, 10)}
        />
        <Field
          label='预计到岗'
          value={job.onboardDate?.slice(0, 10) || job.targetText}
        />
      </div>

      <div className='mb-3'>
        <div className='mb-0.5 text-[11px] text-neutral-400'>工作内容</div>
        <Typography.Paragraph
          className='!mb-0 text-sm text-neutral-700'
          ellipsis={{ rows: 2, tooltip: job.jobSummary }}
        >
          {job.jobSummary?.trim() || '—'}
        </Typography.Paragraph>
      </div>

      <div className='flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-neutral-100 pt-2 text-xs text-neutral-500'>
        {locationLabel(job.locationCode) ? (
          <span className='inline-flex items-center gap-1'>
            <EnvironmentOutlined />
            {locationLabel(job.locationCode)}
          </span>
        ) : null}
        {job.headcount != null ? (
          <span className='inline-flex items-center gap-1'>
            <TeamOutlined />
            HC {job.headcount}
          </span>
        ) : null}
        {job.candidateCount != null ? (
          <span className='inline-flex items-center gap-1'>
            <ClockCircleOutlined />
            候选人 {job.candidateCount}
          </span>
        ) : null}
        <button
          type='button'
          className='ml-auto inline-flex max-w-full items-center gap-1 truncate text-left text-blue-600 hover:underline'
          title={job.ownerNames || '未指定负责人'}
          onClick={() => onOwnerClick(firstOwnerId)}
        >
          <UserOutlined />
          <span className='truncate'>{job.ownerNames?.trim() || '未指定负责人'}</span>
          {firstOwnerId ? <FlagOutlined className='shrink-0 text-[10px]' /> : null}
        </button>
      </div>
    </Card>
  );
}

function DeptSection({ group, onOwnerClick }: { group: HrJobCardGroup; onOwnerClick: (ownerUserId?: number) => void }) {
  return (
    <section className='space-y-3'>
      <div className='flex items-baseline gap-2 border-b border-neutral-200 pb-2'>
        <Typography.Title
          level={5}
          className='!mb-0'
        >
          {group.deptName}
        </Typography.Title>
        <Typography.Text
          type='secondary'
          className='text-xs'
        >
          {group.jobCount} 个岗位
        </Typography.Text>
      </div>
      <div className='grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3'>
        {group.jobs.map((job) => (
          <JobCardItem
            key={job.id}
            job={job}
            onOwnerClick={onOwnerClick}
          />
        ))}
      </div>
    </section>
  );
}

export default function HrJobBoardPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [groups, setGroups] = useState<HrJobCardGroup[]>([]);
  const [depts, setDepts] = useState<{ value: number; label: string }[]>([]);
  const [filters, setFilters] = useState<HrJobCardQuery>({});

  const totalJobs = useMemo(() => groups.reduce((sum, g) => sum + (g.jobCount || 0), 0), [groups]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getHrJobCardsApi(filters);
      setGroups(data?.groups || []);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载岗位看板失败');
    } finally {
      setLoading(false);
    }
  }, [filters, message]);

  useEffect(() => {
    getHrDepartmentsApi().then((tree) => setDepts(flattenDepts(tree as unknown as Dept[])));
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onOwnerClick = (ownerUserId?: number) => {
    if (!ownerUserId) {
      message.info('该岗位尚未关联系统负责人，无法打开 KPI');
      return;
    }
    navigate(`/hr/board?tab=analytics&ownerUserId=${ownerUserId}`);
  };

  return (
    <div className='space-y-4'>
      <div className='flex flex-wrap items-end justify-between gap-3'>
        <div>
          <Typography.Title
            level={5}
            className='!mb-0'
          >
            岗位看板
          </Typography.Title>
          <Typography.Text
            type='secondary'
            className='text-xs'
          >
            按部门分组展示岗位信息；点击负责人可跳转招聘看板查看其 KPI。
          </Typography.Text>
        </div>
        <Typography.Text
          type='secondary'
          className='text-xs'
        >
          共 {totalJobs} 个岗位 / {groups.length} 个部门
        </Typography.Text>
      </div>

      <Card
        size='small'
        className='shadow-sm'
        styles={{ body: { padding: '12px 14px' } }}
      >
        <Space
          wrap
          size={[12, 8]}
        >
          <Select
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='按部门筛选'
            className='min-w-[200px]'
            options={depts}
            value={filters.deptId}
            onChange={(deptId) => setFilters((prev) => ({ ...prev, deptId }))}
          />
          <Select
            allowClear
            placeholder='按岗位状态筛选'
            className='min-w-[160px]'
            options={CARD_STATUS}
            value={filters.cardStatus}
            onChange={(cardStatus) => setFilters((prev) => ({ ...prev, cardStatus }))}
          />
          <Select
            allowClear
            placeholder='按紧急程度筛选'
            className='min-w-[160px]'
            options={PRIORITY}
            value={filters.priority}
            onChange={(priority) => setFilters((prev) => ({ ...prev, priority }))}
          />
        </Space>
      </Card>

      <Spin spinning={loading}>
        {groups.length === 0 ? (
          <Card className='shadow-sm'>
            <Empty description='暂无符合条件的岗位' />
          </Card>
        ) : (
          <div className='space-y-6'>
            {groups.map((group) => (
              <DeptSection
                key={group.deptId ?? group.deptName}
                group={group}
                onOwnerClick={onOwnerClick}
              />
            ))}
          </div>
        )}
      </Spin>
    </div>
  );
}
