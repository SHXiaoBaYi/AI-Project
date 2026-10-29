import { memo, useCallback, useEffect, useState } from 'react';
import { App, Card, Col, DatePicker, Modal, Row, Select, Space, Statistic, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { getHrKpiAssessmentApi, getHrUsersApi, updateHrKpiTaskDueApi, type HrKpiAssessment } from '@/api/hr';

const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '未完成', value: 'OPEN' },
  { label: '已完成', value: 'DONE' },
];

function fmt(value?: string) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 16);
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

  const taskColumns: ColumnsType<HrKpiAssessment['tasks'][number]> = [
    {
      title: '操作',
      fixed: 'left',
      width: 100,
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
    { title: '负责人', dataIndex: 'ownerName', width: 100 },
    { title: '岗位', dataIndex: 'jobName', ellipsis: true },
    {
      title: '优先级',
      dataIndex: 'priorityCode',
      width: 80,
      render: (v: string) => <Tag color={v === 'P0' ? 'red' : 'blue'}>{v || '—'}</Tag>,
    },
    { title: '槽位', dataIndex: 'slotNo', width: 70 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v: string) => (v === 'DONE' ? <Tag color='success'>已完成</Tag> : <Tag color='processing'>未完成</Tag>),
    },
    { title: '得分', dataIndex: 'score', width: 70, render: (v?: number) => v ?? 0 },
    { title: '发布', dataIndex: 'publishedAt', width: 140, render: fmt },
    { title: '到期', dataIndex: 'dueAt', width: 140, render: fmt },
    {
      title: '完成方式',
      dataIndex: 'doneReason',
      width: 140,
      render: (v?: string) => (v === 'ONBOARD' ? '入职' : v === 'QUALIFIED_RESUME' ? '合格简历' : v || '—'),
    },
  ];

  return (
    <div className='flex flex-col gap-3 p-1'>
      <Card size='small'>
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
            style={{ minWidth: 220 }}
            options={userOptions}
            value={owners}
            onChange={setOwners}
            maxTagCount='responsive'
            optionFilterProp='label'
            showSearch
          />
          <span className='text-sm text-neutral-500'>状态</span>
          <Select
            style={{ width: 120 }}
            options={STATUS_OPTIONS}
            value={status}
            onChange={setStatus}
          />
        </Space>
      </Card>

      <Row gutter={[12, 12]}>
        {(data?.owners ?? []).map((o) => (
          <Col
            key={o.ownerUserId}
            xs={24}
            sm={12}
            lg={8}
            xl={6}
          >
            <Card size='small'>
              <div className='mb-2 font-medium'>{o.ownerName || o.ownerUserId}</div>
              <Row gutter={8}>
                <Col span={8}>
                  <Statistic
                    title='得分'
                    value={o.score}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='完成'
                    value={`${o.doneCount}/${o.dueCount}`}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='未完成'
                    value={o.openCount}
                  />
                </Col>
              </Row>
              <div className='mt-2 text-xs text-neutral-500'>
                P0 {o.p0Done}/{o.p0Due} · P1 {o.p1Done}/{o.p1Due}
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

      <Card
        size='small'
        title='任务明细'
      >
        <Table
          rowKey='id'
          size='small'
          loading={loading}
          dataSource={data?.tasks ?? []}
          columns={taskColumns}
          scroll={{ x: 1200 }}
          pagination={{ pageSize: 20, showSizeChanger: true }}
        />
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
