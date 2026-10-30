import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Card, Col, DatePicker, Modal, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  backfillHrKpiAssessmentApi,
  getHrKpiAssessmentApi,
  getHrUsersApi,
  updateHrKpiTaskDueApi,
  type HrKpiAssessment,
} from '@/api/hr';

const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '未完成', value: 'OPEN' },
  { label: '已完成', value: 'DONE' },
];

function fmt(value?: string) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 16);
}

function pct(rate?: number) {
  if (rate == null || Number.isNaN(rate)) return '—';
  return `${(rate * 100).toFixed(1)}%`;
}

const AssessmentPage = memo(function AssessmentPage() {
  const { message } = App.useApp();
  const [month, setMonth] = useState<Dayjs>(dayjs().startOf('month'));
  const [owners, setOwners] = useState<number[]>([]);
  const [status, setStatus] = useState('');
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([]);
  const [data, setData] = useState<HrKpiAssessment | null>(null);
  const [loading, setLoading] = useState(false);
  const [dueEdit, setDueEdit] = useState<{ id: number; dueAt?: string; jobName?: string } | null>(null);
  const [dueValue, setDueValue] = useState<Dayjs | null>(null);
  const [savingDue, setSavingDue] = useState(false);
  const [backfilling, setBackfilling] = useState(false);
  const tableHostRef = useRef<HTMLDivElement>(null);
  const [tableBodyY, setTableBodyY] = useState(320);

  useEffect(() => {
    getHrUsersApi('owner').then((list) =>
      setUserOptions(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      ),
    );
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getHrKpiAssessmentApi({
        settleMonth: month.format('YYYY-MM-01'),
        ownerUserIds: owners.length ? owners : undefined,
        status: status || undefined,
      });
      setData(res);
    } finally {
      setLoading(false);
    }
  }, [month, owners, status]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const host = tableHostRef.current;
    if (!host || typeof ResizeObserver === 'undefined') return;
    const measure = () => {
      const head =
        (host.querySelector('.ant-table-header') as HTMLElement | null) ||
        (host.querySelector('.ant-table-thead') as HTMLElement | null);
      const pagination = host.querySelector('.ant-table-pagination') as HTMLElement | null;
      const headH = head?.offsetHeight || 39;
      const pageH = pagination ? pagination.offsetHeight + 16 : 56;
      setTableBodyY(Math.max(120, host.clientHeight - headH - pageH));
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(host);
    return () => ro.disconnect();
  }, [data?.tasks?.length, loading]);

  const baseScore = data?.baseScore ?? 30;
  const weightP0 = data?.weightP0 ?? 2;
  const weightP1 = data?.weightP1 ?? 1;

  const taskColumns: ColumnsType<HrKpiAssessment['tasks'][number]> = [
    {
      title: '操作',
      fixed: 'left',
      width: 72,
      render: (_, row) =>
        row.status === 'OPEN' ? (
          <a
            onClick={() => {
              setDueEdit({ id: row.id, dueAt: row.dueAt, jobName: row.jobName });
              setDueValue(row.dueAt ? dayjs(row.dueAt) : dayjs());
            }}
          >
            延期
          </a>
        ) : (
          '—'
        ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 88, ellipsis: true },
    { title: '岗位', dataIndex: 'jobName', ellipsis: true },
    {
      title: '优先级',
      dataIndex: 'priorityCode',
      width: 72,
      render: (v: string) => <Tag color={v === 'P0' ? 'red' : 'blue'}>{v || '—'}</Tag>,
    },
    { title: '槽位', dataIndex: 'slotNo', width: 56 },
    {
      title: '权重',
      dataIndex: 'weight',
      width: 64,
      render: (v, row) => {
        const w = (v as number | undefined) ?? (row.priorityCode === 'P0' ? weightP0 : weightP1);
        return Number(w).toFixed(1);
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v: string) => (v === 'DONE' ? <Tag color='success'>已完成</Tag> : <Tag color='processing'>未完成</Tag>),
    },
    {
      title: '计入权重',
      dataIndex: 'earnedWeight',
      width: 80,
      render: (v, row) => {
        if (v != null && v !== '') return Number(v).toFixed(1);
        return row.status === 'DONE'
          ? Number(row.weight ?? (row.priorityCode === 'P0' ? weightP0 : weightP1)).toFixed(1)
          : '0.0';
      },
    },
    { title: '发布', dataIndex: 'publishedAt', width: 128, render: fmt },
    { title: '到期', dataIndex: 'dueAt', width: 128, render: fmt },
    {
      title: '完成方式',
      dataIndex: 'doneReason',
      width: 100,
      ellipsis: true,
      render: (v?: string) => (v === 'ONBOARD' ? '入职' : v === 'QUALIFIED_RESUME' ? '合格简历' : v || '—'),
    },
  ];

  return (
    <div className='flex h-[calc(100vh-100px)] min-h-0 flex-col gap-3 overflow-hidden p-1'>
      <Card
        size='small'
        className='shrink-0 overflow-x-hidden'
      >
        <Space wrap>
          <span className='text-sm text-neutral-500'>核算月</span>
          <DatePicker
            picker='month'
            value={month}
            allowClear={false}
            onChange={(v) => v && setMonth(v.startOf('month'))}
          />
          <span className='text-sm text-neutral-500'>负责人</span>
          <Select
            mode='multiple'
            allowClear
            placeholder='全部'
            className='max-w-[280px] min-w-[180px]'
            options={userOptions}
            value={owners}
            onChange={setOwners}
            maxTagCount='responsive'
            optionFilterProp='label'
            showSearch
          />
          <span className='text-sm text-neutral-500'>状态</span>
          <Select
            className='w-[110px]'
            options={STATUS_OPTIONS}
            value={status}
            onChange={setStatus}
          />
          <PermissionButton
            perm='hr:kpi:view'
            loading={backfilling}
            onClick={async () => {
              setBackfilling(true);
              try {
                const n = await backfillHrKpiAssessmentApi();
                message.success(`历史回填完成，处理 ${n} 个 P0/P1 岗位`);
                load();
              } finally {
                setBackfilling(false);
              }
            }}
          >
            回填历史数据
          </PermissionButton>
          <Button
            onClick={() => load()}
            loading={loading}
          >
            刷新
          </Button>
        </Space>
        <Typography.Paragraph
          type='secondary'
          className='!mt-2 !mb-0 text-xs leading-relaxed'
        >
          {data?.scoreRuleHint ||
            `基准分${baseScore}分；得分=整体完成率×${baseScore}；P0权重${weightP0}、P1权重${weightP1}；完成计权重、未完成计0；无额外加减分`}
        </Typography.Paragraph>
      </Card>

      <div className='max-h-[38%] shrink-0 overflow-x-hidden overflow-y-auto'>
        <Row gutter={[12, 12]}>
          <Col
            xs={24}
            sm={12}
            lg={8}
            xl={6}
          >
            <Card
              size='small'
              className='overflow-hidden border-sky-200 bg-sky-50/60'
              loading={loading && !data}
            >
              <div className='mb-2 truncate font-medium text-sky-800'>整体完成率</div>
              <Row gutter={8}>
                <Col span={8}>
                  <Statistic
                    title={`得分/${baseScore}`}
                    value={data?.overallScore ?? 0}
                    precision={1}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='完成率'
                    value={pct(data?.overallCompletionRate)}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='加权'
                    value={`${Number(data?.overallDoneWeight ?? 0).toFixed(1)}/${Number(data?.overallTotalWeight ?? 0).toFixed(1)}`}
                  />
                </Col>
              </Row>
              <div className='mt-2 text-xs text-neutral-500'>
                P0权重{Number(weightP0).toFixed(1)} · P1权重{Number(weightP1).toFixed(1)}（考核计分规则）
              </div>
            </Card>
          </Col>
          {(data?.owners ?? []).map((o) => (
            <Col
              key={o.ownerUserId}
              xs={24}
              sm={12}
              lg={8}
              xl={6}
            >
              <Card
                size='small'
                className='overflow-hidden'
              >
                <div className='mb-2 truncate font-medium'>{o.ownerName || o.ownerUserId}</div>
                <Row gutter={8}>
                  <Col span={8}>
                    <Statistic
                      title={`得分/${baseScore}`}
                      value={o.score ?? 0}
                      precision={1}
                    />
                  </Col>
                  <Col span={8}>
                    <Statistic
                      title='完成率'
                      value={pct(o.completionRate)}
                    />
                  </Col>
                  <Col span={8}>
                    <Statistic
                      title='加权'
                      value={`${Number(o.doneWeight ?? 0).toFixed(1)}/${Number(o.totalWeight ?? 0).toFixed(1)}`}
                    />
                  </Col>
                </Row>
                <div className='mt-2 text-xs text-neutral-500'>
                  任务 {o.doneCount}/{o.dueCount} · 未完成 {o.openCount} · P0 {o.p0Done}/{o.p0Due} · P1 {o.p1Done}/
                  {o.p1Due}
                </div>
              </Card>
            </Col>
          ))}
          {!loading && !(data?.owners ?? []).length ? (
            <Col span={24}>
              <Card size='small'>
                <div className='py-6 text-center text-neutral-400'>本月暂无到期考核任务</div>
              </Card>
            </Col>
          ) : null}
        </Row>
      </div>

      <Card
        size='small'
        title='任务明细'
        className='flex min-h-0 flex-1 flex-col overflow-hidden [&_.ant-card-body]:flex [&_.ant-card-body]:min-h-0 [&_.ant-card-body]:flex-1 [&_.ant-card-body]:flex-col [&_.ant-card-body]:overflow-hidden [&_.ant-card-body]:!pt-3'
      >
        <div
          ref={tableHostRef}
          className='min-h-0 flex-1 overflow-hidden overflow-x-hidden'
        >
          <Table
            rowKey='id'
            size='small'
            loading={loading}
            dataSource={data?.tasks ?? []}
            columns={taskColumns}
            scroll={{ y: tableBodyY }}
            tableLayout='fixed'
            pagination={{ pageSize: 20, showSizeChanger: true, size: 'small' }}
          />
        </div>
      </Card>

      <Modal
        title={dueEdit ? `延期：${dueEdit.jobName || ''}` : '延期'}
        open={!!dueEdit}
        confirmLoading={savingDue}
        onCancel={() => setDueEdit(null)}
        onOk={async () => {
          if (!dueEdit || !dueValue) {
            message.warning('请选择新的到期时间');
            return;
          }
          setSavingDue(true);
          try {
            await updateHrKpiTaskDueApi(dueEdit.id, dueValue.format('YYYY-MM-DD HH:mm:ss'));
            message.success('已延期');
            setDueEdit(null);
            load();
          } finally {
            setSavingDue(false);
          }
        }}
      >
        <div className='mb-2 text-sm text-neutral-500'>任何人可将未完成任务的到期日改到其他月份</div>
        <DatePicker
          showTime
          className='w-full'
          value={dueValue}
          onChange={setDueValue}
        />
      </Modal>
    </div>
  );
});

export default AssessmentPage;
