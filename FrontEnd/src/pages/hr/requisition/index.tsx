import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  changeHrRequisitionStatusApi,
  deleteHrRequisitionApi,
  getHrDepartmentsApi,
  getHrRequisitionsApi,
  getHrUsersApi,
  saveHrRequisitionApi,
} from '@/api/hr';

const STATUS = [
  { label: '招聘中', value: 'OPEN' },
  { label: '已完成', value: 'DONE' },
  { label: '停止招聘', value: 'STOPPED' },
  { label: '暂缓', value: 'PAUSED' },
  { label: '已归档', value: 'ARCHIVED' },
];

const PRIORITY = [
  { label: '紧急', value: 1 },
  { label: '优先', value: 2 },
  { label: '常规', value: 3 },
];

const LOCATIONS = [
  { label: '上海', value: 'SH' },
  { label: '新疆', value: 'XJ' },
];

const TARGETS = [
  { label: '紧急-尽快', value: '紧急-尽快' },
  { label: '尽快', value: '尽快' },
  { label: '7月-尽快', value: '7月-尽快' },
  { label: '常规节奏持续招聘', value: '常规节奏持续招聘' },
];

const ROUND_COUNT = [
  { label: '一面', value: 1 },
  { label: '二面', value: 2 },
  { label: '三面', value: 3 },
  { label: '四面', value: 4 },
  { label: '五面', value: 5 },
];

const ROUND_INTERVIEWER = ['一面面试官', '二面面试官', '三面面试官', '四面面试官', '五面面试官'];

interface Dept {
  id: number;
  name: string;
  children?: Dept[];
}

interface Req {
  id: number;
  jobName: string;
  jobDesc?: string;
  status: string;
  locationCode: string;
  deptId?: number;
  deptName?: string;
  headcount: number;
  targetText?: string;
  priority?: number;
  receivedDate?: string;
  onboardDate?: string;
  ownerNames?: string;
  ownerUserIds?: number[];
  ownerUserId?: number;
  interviewFlow?: string;
  roundCount?: number;
  interviewer1?: number[];
  interviewer2?: number[];
  interviewer3?: number[];
  interviewer4?: number[];
  interviewer5?: number[];
}

function flattenDepts(rows: Dept[], prefix = ''): { value: number; label: string }[] {
  return rows.flatMap((row) => {
    const label = prefix ? `${prefix} / ${row.name}` : row.name;
    return [{ value: row.id, label }, ...flattenDepts(row.children || [], label)];
  });
}

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function dateOf(value: unknown) {
  if (value == null || value === '') return undefined;
  if (Array.isArray(value)) return dayjs(`${value[0]}-${value[1]}-${value[2]}`).format('YYYY-MM-DD');
  return String(value).slice(0, 10);
}

function roundsOf(value: unknown) {
  const text = textOf(value);
  const byNo = new Map<number, number[]>();
  if (!text) return byNo;
  text.split(',').forEach((part) => {
    const [no, userIds] = part.split(':');
    const roundNo = Number(no);
    const ids = (userIds || '')
      .split('|')
      .map((item) => Number(item))
      .filter((item) => item > 0);
    if (roundNo > 0 && ids.length) byNo.set(roundNo, ids);
  });
  return byNo;
}

function mapRow(raw: Record<string, unknown>): Req {
  const ownerIds = textOf(raw.owner_user_ids);
  const rounds = roundsOf(raw.interview_rounds);
  const roundCount = rounds.size ? Math.max(...rounds.keys()) : 0;
  return {
    id: Number(raw.id),
    jobName: String(raw.job_name ?? ''),
    jobDesc: textOf(raw.job_desc),
    status: String(raw.status ?? ''),
    locationCode: String(raw.location_code ?? ''),
    deptId: raw.dept_id == null ? undefined : Number(raw.dept_id),
    deptName: textOf(raw.dept_name),
    headcount: Number(raw.headcount ?? 1),
    targetText: textOf(raw.target_text)?.trim(),
    priority: raw.priority == null || raw.priority === '' ? undefined : Number(raw.priority),
    receivedDate: dateOf(raw.received_date),
    onboardDate: dateOf(raw.onboard_date),
    ownerNames: textOf(raw.owner_names),
    interviewFlow: textOf(raw.interview_flow),
    roundCount: roundCount || undefined,
    interviewer1: rounds.get(1),
    interviewer2: rounds.get(2),
    interviewer3: rounds.get(3),
    interviewer4: rounds.get(4),
    interviewer5: rounds.get(5),
    ownerUserIds: ownerIds
      ? ownerIds
          .split(',')
          .map((item) => Number(item))
          .filter((item) => item > 0)
      : [],
  };
}

const RequisitionPage = memo(function RequisitionPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Req | null>(null);
  const [depts, setDepts] = useState<{ value: number; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [interviewers, setInterviewers] = useState<{ value: number; label: string }[]>([]);

  useEffect(() => {
    getHrDepartmentsApi().then((tree) => setDepts(flattenDepts(tree as unknown as Dept[])));
    const toOptions = (list: Record<string, unknown>[]) =>
      list.map((item) => ({
        value: Number(item.userId ?? item.user_id),
        label: String(item.nickname ?? ''),
      }));
    getHrUsersApi('owner').then((list) => setUsers(toOptions(list as Record<string, unknown>[])));
    getHrUsersApi('interviewer').then((list) => setInterviewers(toOptions(list as Record<string, unknown>[])));
  }, []);

  const columns: ProColumnType<Req>[] = useMemo(
    () => [
      {
        title: '岗位',
        dataIndex: 'jobName',
        fieldProps: { maxLength: 128 },
        formItemProps: { rules: [{ required: true, message: '请填写岗位' }] },
      },
      {
        title: '部门',
        dataIndex: 'deptId',
        valueType: 'select',
        fieldProps: { options: depts, allowClear: true, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.deptName || '未分配',
      },
      {
        title: '负责人',
        dataIndex: 'ownerUserId',
        valueType: 'select',
        hideInTable: true,
        hideInForm: true,
        fieldProps: { options: users, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: '负责人',
        dataIndex: 'ownerNames',
        search: false,
        hideInForm: true,
        render: (_, record) => record.ownerNames || '—',
      },
      {
        title: '负责人',
        dataIndex: 'ownerUserIds',
        valueType: 'select',
        search: false,
        hideInTable: true,
        fieldProps: { mode: 'multiple', options: users, allowClear: true, optionFilterProp: 'label' },
      },
      {
        title: '地点',
        dataIndex: 'locationCode',
        valueType: 'select',
        fieldProps: { options: LOCATIONS },
        formItemProps: { rules: [{ required: true, message: '请选择工作地' }] },
        render: (_, record) => (record.locationCode === 'XJ' ? '新疆' : '上海'),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        hideInForm: true,
        fieldProps: { options: STATUS, allowClear: true },
        render: (_, record) => STATUS.find((item) => item.value === record.status)?.label || record.status,
      },
      {
        title: '优先级',
        dataIndex: 'priority',
        valueType: 'select',
        width: 90,
        fieldProps: { options: PRIORITY, allowClear: true },
        render: (_, record) => PRIORITY.find((item) => item.value === record.priority)?.label || '—',
      },
      {
        title: '目标到岗',
        dataIndex: 'targetText',
        valueType: 'select',
        width: 160,
        fieldProps: { options: TARGETS, allowClear: true },
        formItemProps: { rules: [{ required: true, message: '请选择目标到岗' }] },
      },
      {
        title: '人数',
        dataIndex: 'headcount',
        valueType: 'digit',
        width: 80,
        search: false,
        fieldProps: { min: 1, precision: 0 },
      },
      {
        title: '接收日',
        dataIndex: 'receivedDate',
        valueType: 'date',
        width: 120,
        search: false,
        formItemProps: { rules: [{ required: true, message: '请选择接收日' }] },
      },
      {
        title: '入职日',
        dataIndex: 'onboardDate',
        valueType: 'date',
        width: 120,
        search: false,
      },
      {
        title: '面试流程',
        dataIndex: 'interviewFlow',
        search: false,
        hideInForm: true,
        ellipsis: true,
        render: (_, record) => record.interviewFlow || '未配置',
      },
      {
        title: '岗位职责',
        dataIndex: 'jobDesc',
        valueType: 'textarea',
        search: false,
        hideInTable: true,
        fieldProps: { rows: 4, maxLength: 4000, placeholder: '这个岗位要做什么' },
      },
      {
        title: '需要几面',
        dataIndex: 'roundCount',
        valueType: 'select',
        search: false,
        hideInTable: true,
        formItemProps: { rules: [{ required: true, message: '请选择需要几面' }] },
        fieldProps: { options: ROUND_COUNT, placeholder: '默认二面' },
      },
      {
        valueType: 'dependency',
        name: ['roundCount'],
        hideInTable: true,
        hideInSearch: true,
        columns: ({ roundCount }: { roundCount?: number }) =>
          ROUND_INTERVIEWER.slice(0, Number(roundCount || 0)).map((title, index) => ({
            title,
            dataIndex: `interviewer${index + 1}`,
            valueType: 'select' as const,
            formItemProps: { rules: [{ required: true, message: `请选择${title}` }] },
            fieldProps: {
              options: interviewers,
              showSearch: true,
              optionFilterProp: 'label',
              mode: 'multiple',
              allowClear: true,
            },
          })),
      },
      {
        title: '操作',
        valueType: 'option',
        width: 220,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '修改',
                perm: 'hr:requisition:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              ...(record.status === 'OPEN'
                ? [
                    {
                      key: 'pause',
                      label: '暂缓',
                      perm: 'hr:requisition:edit',
                      confirmTitle: `确认暂缓「${record.jobName}」？`,
                      onClick: async () => {
                        await changeHrRequisitionStatusApi(record.id, 'PAUSED');
                        message.success('已暂缓');
                        actionRef.current?.reload();
                      },
                    },
                  ]
                : []),
              ...(record.status !== 'ARCHIVED'
                ? [
                    {
                      key: 'archive',
                      label: '归档',
                      perm: 'hr:requisition:edit',
                      confirmTitle: `确认归档「${record.jobName}」？`,
                      onClick: async () => {
                        await changeHrRequisitionStatusApi(record.id, 'ARCHIVED');
                        message.success('已归档');
                        actionRef.current?.reload();
                      },
                    },
                  ]
                : []),
              ...(record.status === 'PAUSED' || record.status === 'ARCHIVED'
                ? [
                    {
                      key: 'restore',
                      label: '恢复',
                      perm: 'hr:requisition:edit',
                      confirmTitle: `确认恢复「${record.jobName}」为招聘中？`,
                      onClick: async () => {
                        await changeHrRequisitionStatusApi(record.id, 'OPEN');
                        message.success('已恢复');
                        actionRef.current?.reload();
                      },
                    },
                  ]
                : []),
              {
                key: 'delete',
                label: '删除',
                perm: 'hr:requisition:delete',
                confirmTitle: `确认删除「${record.jobName}」？删除后不再出现在列表和看板。`,
                onClick: async () => {
                  await deleteHrRequisitionApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
    ],
    [depts, interviewers, message, users],
  );

  return (
    <>
      <BaseProTable<Req>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='招聘需求'
        request={async (params) => {
          const rows = (await getHrRequisitionsApi({
            jobName: params.jobName,
            deptId: params.deptId,
            ownerUserId: params.ownerUserId,
            locationCode: params.locationCode,
            status: params.status,
            priority: params.priority,
            targetText: params.targetText,
          })) as unknown as Record<string, unknown>[];
          const mapped = rows.map(mapRow);
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          return { data: mapped.slice(start, start + pageSize), success: true, total: mapped.length };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='hr:requisition:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '修改招聘需求' : '新增招聘需求'}
        columns={columns as never}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing ?? {
            locationCode: 'SH',
            headcount: 1,
            targetText: '尽快',
            roundCount: 2,
            receivedDate: dayjs().format('YYYY-MM-DD'),
          }
        }
        onFinish={async (values) => {
          const form = values as Req;
          const count = Number(form.roundCount || 0);
          const picked = [
            form.interviewer1,
            form.interviewer2,
            form.interviewer3,
            form.interviewer4,
            form.interviewer5,
          ];
          await saveHrRequisitionApi({
            id: editing?.id,
            jobName: form.jobName,
            jobDesc: form.jobDesc,
            locationCode: form.locationCode,
            deptId: form.deptId,
            headcount: form.headcount,
            targetText: form.targetText,
            priority: form.priority,
            receivedDate: dateOf(form.receivedDate),
            onboardDate: form.onboardDate ? dateOf(form.onboardDate) : null,
            ownerUserIds: form.ownerUserIds || [],
            interviewRounds: Array.from({ length: count }, (_, index) => ({
              roundNo: index + 1,
              interviewerUserIds: picked[index] || [],
            })),
          });
          message.success(editing ? '已保存' : '已新增');
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default RequisitionPage;
