import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Modal, Select } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import {
  createHrInviteApi,
  createHrInviteCalendarBatchApi,
  deleteHrInviteBatchApi,
  getHrApplicationsApi,
  getHrRequisitionsApi,
  getHrUsersApi,
  listHrInvitesApi,
  updateHrInviteApi,
} from '@/api/hr';

const ROUNDS = [
  { value: 1, label: '一面' },
  { value: 2, label: '二面' },
  { value: 3, label: '三面' },
  { value: 4, label: '四面' },
  { value: 5, label: '五面' },
];

const STATUS = [
  { value: 'SUCCESS', label: '已建日程' },
  { value: 'NO_CALENDAR', label: '未建日程' },
  { value: 'FAILED', label: '日程失败' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'CANCEL_FAILED', label: '取消失败' },
];

interface InviteRow {
  id: number;
  inviteIds: number[];
  pendingInviteIds: number[];
  applicationId: number;
  displayName: string;
  jobName?: string;
  fileName?: string;
  roundNo: number;
  interviewerUserId: number;
  interviewerUserIds: number[];
  interviewerName?: string;
  interviewAt?: string;
  durationMin?: number;
  location?: string;
  status?: string;
  memberStatuses: string[];
  failReason?: string;
  hasRecord: boolean;
}

function InterviewerSelect({
  value,
  onChange,
  users,
  appReq,
  plans,
}: {
  value?: number[];
  onChange?: (value: number[]) => void;
  users: { value: number; label: string }[];
  appReq: Map<number, number>;
  plans: Map<number, Map<number, number[]>>;
}) {
  const form = Form.useFormInstance();
  const applicationId = Form.useWatch('applicationId', form);
  const roundNo = Form.useWatch('roundNo', form);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  const seen = useRef<string | null>(null);

  useEffect(() => {
    if (applicationId == null || roundNo == null) return;
    if (plans.size === 0 || appReq.size === 0) return;
    const key = `${applicationId}-${roundNo}`;
    if (seen.current === key) return;
    const opening = seen.current === null;
    seen.current = key;
    if (opening && value?.length) return;
    const reqId = appReq.get(Number(applicationId));
    const ids = reqId == null ? [] : (plans.get(reqId)?.get(Number(roundNo)) ?? []);
    onChangeRef.current?.(ids);
  }, [applicationId, roundNo, appReq, plans, value]);

  return (
    <Select
      mode='multiple'
      value={value}
      onChange={onChange}
      options={users}
      showSearch
      optionFilterProp='label'
      placeholder='选择候选人与轮次后自动带出'
      style={{ width: '100%' }}
    />
  );
}

function notifyCalendarResult(
  saved: { warning?: string; created?: number; unboundInterviewers?: string[] } | undefined,
  messageApi: { warning: (content: string, duration?: number) => void; success: (content: string) => void },
) {
  const names = saved?.unboundInterviewers?.filter(Boolean) ?? [];
  if (names.length) {
    Modal.warning({
      title: '以下面试官没有绑定钉钉',
      okText: '关闭',
      width: 480,
      maskClosable: false,
      content: (
        <div>
          <p className='mb-2'>
            {saved?.created ? `已创建 ${saved.created} 条钉钉日程。` : '未能创建钉钉日程。'}
            请联系行政绑定后再创建。
          </p>
          <ul className='max-h-60 overflow-auto rounded border border-neutral-200'>
            {names.map((name) => (
              <li
                key={name}
                className='border-b border-neutral-100 px-3 py-2 last:border-b-0'
              >
                {name}
              </li>
            ))}
          </ul>
        </div>
      ),
    });
    return;
  }
  if (saved?.warning) {
    messageApi.warning(saved.warning, 8);
    return;
  }
  messageApi.success(`已创建 ${saved?.created ?? 0} 条钉钉日程`);
}

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function mapRow(raw: Record<string, unknown>): InviteRow {
  const interviewerUserId = Number(raw.interviewer_user_id);
  const status = textOf(raw.status);
  const hasRecord = raw.record_id != null && raw.record_id !== '';
  return {
    id: Number(raw.id),
    inviteIds: [Number(raw.id)],
    pendingInviteIds: !hasRecord && (status === 'NO_CALENDAR' || status === 'FAILED') ? [Number(raw.id)] : [],
    applicationId: Number(raw.application_id),
    displayName: String(raw.display_name ?? ''),
    jobName: textOf(raw.job_name),
    fileName: textOf(raw.file_name),
    roundNo: Number(raw.round_no),
    interviewerUserId,
    interviewerUserIds: [interviewerUserId],
    interviewerName: textOf(raw.interviewer_name),
    interviewAt: raw.interview_at == null ? undefined : String(raw.interview_at).replace('T', ' ').slice(0, 19),
    durationMin: raw.duration_min == null ? 60 : Number(raw.duration_min),
    location: textOf(raw.location),
    status,
    memberStatuses: status ? [status] : [],
    failReason: textOf(raw.fail_reason),
    hasRecord,
  };
}

function sessionKey(row: Pick<InviteRow, 'applicationId' | 'roundNo' | 'interviewAt'>) {
  return `${row.applicationId}|${row.roundNo}|${row.interviewAt ?? ''}`;
}

function groupSessions(rows: InviteRow[]) {
  const grouped = new Map<string, InviteRow>();
  rows.forEach((row) => {
    const key = sessionKey(row);
    const existing = grouped.get(key);
    if (!existing) {
      grouped.set(key, {
        ...row,
        inviteIds: [...row.inviteIds],
        pendingInviteIds: [...row.pendingInviteIds],
        interviewerUserIds: [...row.interviewerUserIds],
        memberStatuses: [...row.memberStatuses],
        hasRecord: row.hasRecord,
      });
      return;
    }
    existing.inviteIds.push(row.id);
    existing.pendingInviteIds.push(...row.pendingInviteIds);
    existing.hasRecord = existing.hasRecord || row.hasRecord;
    if (!existing.interviewerUserIds.includes(row.interviewerUserId)) {
      existing.interviewerUserIds.push(row.interviewerUserId);
      existing.interviewerName = [existing.interviewerName, row.interviewerName].filter(Boolean).join('、');
    }
    if (row.status) existing.memberStatuses.push(row.status);
    if (row.failReason && existing.failReason !== row.failReason) {
      existing.failReason = [existing.failReason, row.failReason].filter(Boolean).join('；');
    }
  });
  return [...grouped.values()].map((row) => {
    const unique = [...new Set(row.memberStatuses)];
    return unique.length <= 1 ? { ...row, status: unique[0] } : { ...row, status: 'MIXED' };
  });
}

const InvitePage = memo(function InvitePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const groupedRef = useRef<InviteRow[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<InviteRow | null>(null);
  const [candidates, setCandidates] = useState<{ value: number; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [appReq, setAppReq] = useState<Map<number, number>>(new Map());
  const [plans, setPlans] = useState<Map<number, Map<number, number[]>>>(new Map());

  useEffect(() => {
    getHrApplicationsApi().then((rows) => {
      const next = new Map<number, number>();
      setCandidates(
        (rows as Record<string, unknown>[]).map((row) => {
          if (row.requisition_id != null) next.set(Number(row.id), Number(row.requisition_id));
          return {
            value: Number(row.id),
            label: `${row.display_name} · ${row.job_name || '未定岗'}`,
          };
        }),
      );
      setAppReq(next);
    });
    getHrRequisitionsApi().then((rows) => {
      const next = new Map<number, Map<number, number[]>>();
      rows.forEach((row) => {
        const rounds = new Map<number, number[]>();
        String(row.interview_rounds ?? '')
          .split(',')
          .filter(Boolean)
          .forEach((part) => {
            const [round, ids] = part.split(':');
            rounds.set(Number(round), (ids ?? '').split('|').filter(Boolean).map(Number));
          });
        next.set(Number(row.id), rounds);
      });
      setPlans(next);
    });
    getHrUsersApi().then((list) =>
      setUsers(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      ),
    );
  }, []);

  const columns: ProColumnType<InviteRow>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 300,
        render: (_, record) => (
          <ActionButtons
            maxVisible={4}
            items={[
              {
                key: 'edit',
                label: '编辑',
                perm: 'hr:invite:edit',
                disabled: record.hasRecord,
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              ...(record.pendingInviteIds.length
                ? [
                    {
                      key: 'calendar',
                      label: '创建钉钉日程',
                      perm: 'hr:invite:edit',
                      confirmTitle: `确认为「${record.displayName}」这场邀约创建钉钉日程？面试官需已绑定钉钉。`,
                      onClick: async () => {
                        const saved = await createHrInviteCalendarBatchApi(record.pendingInviteIds);
                        notifyCalendarResult(saved, message);
                        actionRef.current?.reload();
                      },
                    },
                  ]
                : []),
              {
                key: 'delete',
                label: '删除',
                perm: 'hr:invite:delete',
                disabled: record.hasRecord,
                confirmTitle: `确认删除「${record.displayName}」这场邀约？同一场的面试官都会删除，已建的钉钉日程会一并取消。`,
                onClick: async () => {
                  const saved = await deleteHrInviteBatchApi(record.inviteIds);
                  if (saved?.warning) message.warning(saved.warning, 8);
                  else message.success('已删除这场邀约');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
      {
        title: '候选人',
        dataIndex: 'applicationId',
        valueType: 'select',
        fieldProps: { options: candidates, showSearch: true, optionFilterProp: 'label' },
        formItemProps: { rules: [{ required: true, message: '请选择候选人' }] },
        render: (_, record) => record.displayName,
      },
      {
        title: '岗位',
        dataIndex: 'jobName',
        search: false,
        hideInForm: true,
      },
      {
        title: '简历',
        dataIndex: 'fileName',
        search: false,
        hideInForm: true,
        render: (_, record) => (
          <ResumeViewButton
            applicationId={record.applicationId}
            fileName={record.fileName}
          />
        ),
      },
      {
        title: '轮次',
        dataIndex: 'roundNo',
        valueType: 'select',
        fieldProps: { options: ROUNDS },
        formItemProps: { rules: [{ required: true, message: '请选择轮次' }] },
        render: (_, record) => ROUNDS.find((item) => item.value === record.roundNo)?.label,
      },
      {
        title: '面试官',
        dataIndex: 'interviewerUserId',
        valueType: 'select',
        hideInForm: true,
        fieldProps: { options: users, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.interviewerName,
      },
      {
        title: '面试官',
        dataIndex: 'interviewerUserIds',
        hideInTable: true,
        hideInSearch: true,
        hideInForm: false,
        colProps: { span: 24 },
        formItemProps: { rules: [{ required: true, message: '请选择面试官' }] },
        formItemRender: () => (
          <InterviewerSelect
            users={users}
            appReq={appReq}
            plans={plans}
          />
        ),
      },
      {
        title: '开始时间',
        dataIndex: 'interviewAt',
        valueType: 'dateTime',
        search: false,
        formItemProps: { rules: [{ required: true, message: '请选择时间' }] },
      },
      {
        title: '时长',
        dataIndex: 'durationMin',
        valueType: 'digit',
        search: false,
        fieldProps: { min: 15, max: 240 },
      },
      {
        title: '地点',
        dataIndex: 'location',
        search: false,
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        hideInForm: true,
        fieldProps: { options: STATUS, allowClear: true },
        render: (_, record) => {
          if (record.status === 'MIXED') {
            return [...new Set(record.memberStatuses)]
              .map((item) => STATUS.find((status) => status.value === item)?.label || item)
              .join('、');
          }
          return STATUS.find((item) => item.value === record.status)?.label || record.status;
        },
      },
      {
        title: '失败原因',
        dataIndex: 'failReason',
        search: false,
        hideInForm: true,
        ellipsis: true,
      },
    ],
    [appReq, candidates, editing, message, plans, users],
  );

  return (
    <>
      <BaseProTable<InviteRow>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='面试邀约记录'
        request={async (params) => {
          const rows = await listHrInvitesApi({
            candidateName: params.displayName,
            roundNo: params.roundNo,
          });
          let grouped = groupSessions(rows.map(mapRow));
          if (params.applicationId) {
            grouped = grouped.filter((row) => row.applicationId === Number(params.applicationId));
          }
          if (params.interviewerUserId) {
            grouped = grouped.filter((row) => row.interviewerUserIds.includes(Number(params.interviewerUserId)));
          }
          if (params.status) {
            grouped = grouped.filter((row) => row.memberStatuses.includes(String(params.status)));
          }
          groupedRef.current = grouped;
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const pageRows = grouped.slice(start, start + pageSize);
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.id));
          return { data: pageRows, success: true, total: grouped.length };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => {
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='calendar'
            perm='hr:invite:edit'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要创建钉钉日程的面试邀约');
                return;
              }
              const selected = groupedRef.current.filter((row) => selectedRowKeys.map(Number).includes(row.id));
              if (selected.every((row) => row.hasRecord)) {
                message.warning('所选场次都已有面试记录，不能创建钉钉日程');
                return;
              }
              const inviteIds = selected.filter((row) => !row.hasRecord).flatMap((row) => row.pendingInviteIds);
              if (inviteIds.length === 0) {
                message.warning('所选场次都已经有钉钉日程，或已有面试记录');
                return;
              }
              Modal.confirm({
                title: '批量创建钉钉日程',
                content: `将为选中场次里尚未建日程的 ${inviteIds.length} 条面试官邀约创建钉钉日程。面试官未绑定钉钉的不能创建。`,
                okText: '创建',
                cancelText: '取消',
                onOk: async () => {
                  const saved = await createHrInviteCalendarBatchApi(inviteIds);
                  notifyCalendarResult(saved, message);
                  setSelectedRowKeys([]);
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量创建钉钉日程
          </PermissionButton>,
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='hr:invite:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的面试邀约');
                return;
              }
              const selected = groupedRef.current.filter((row) => selectedRowKeys.map(Number).includes(row.id));
              const deletable = selected.filter((row) => !row.hasRecord);
              if (deletable.length === 0) {
                message.warning('所选场次都已有面试记录，不能删除');
                return;
              }
              const inviteIds = deletable.flatMap((row) => row.inviteIds);
              const skipped = selected.length - deletable.length;
              Modal.confirm({
                title: '批量删除面试邀约',
                content: skipped
                  ? `将删除 ${deletable.length} 场邀约；另有 ${skipped} 场已有面试记录，会跳过。此操作不可撤销。`
                  : `确定要删除选中的 ${deletable.length} 场邀约吗？同一场的面试官都会删除，此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  const saved = await deleteHrInviteBatchApi(inviteIds);
                  if (saved?.warning) message.warning(saved.warning, 8);
                  else message.success(`已删除 ${deletable.length} 场邀约`);
                  setSelectedRowKeys([]);
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='hr:invite:add'
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
        title={editing ? '编辑邀约' : '新增邀约'}
        columns={columns as never}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing ? { ...editing, interviewerUserIds: editing.interviewerUserIds } : { roundNo: 1, durationMin: 60 }
        }
        onFinish={async (values) => {
          const form = values as InviteRow & { interviewerUserIds?: number[] };
          const ids = form.interviewerUserIds ?? [];
          if (!ids.length) {
            message.warning('请选择面试官');
            return false;
          }
          const payload = {
            applicationId: form.applicationId,
            roundNo: form.roundNo,
            interviewerUserIds: ids,
            interviewAt: dayjs(form.interviewAt).format('YYYY-MM-DD HH:mm:ss'),
            durationMin: form.durationMin,
            location: form.location,
          };
          if (editing) {
            if (editing.hasRecord) {
              message.warning('这场邀约已有面试记录，不能修改');
              return false;
            }
            const saved = await updateHrInviteApi(editing.id, payload);
            if (saved?.warning) {
              message.warning(saved.warning, 8);
            } else {
              message.success(
                ids.length > 1 ? `已保存这场邀约，共 ${ids.length} 名面试官` : '已保存，钉钉日程已按新时间重建',
              );
            }
          } else {
            const saved = await createHrInviteApi(payload);
            if (saved?.warning) message.warning(saved.warning, 8);
            else message.success(`已为 ${ids.length} 名面试官发起邀约，钉钉日程已创建`);
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default InvitePage;
