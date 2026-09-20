import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Modal, Select } from 'antd';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import {
  createHrInviteApi,
  deleteHrInviteApi,
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
  { value: 'FAILED', label: '日程失败' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'CANCEL_FAILED', label: '取消失败' },
];

interface InviteRow {
  id: number;
  applicationId: number;
  displayName: string;
  jobName?: string;
  fileName?: string;
  roundNo: number;
  interviewerUserId: number;
  interviewerName?: string;
  interviewAt?: string;
  durationMin?: number;
  location?: string;
  status?: string;
  failReason?: string;
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
    const key = `${applicationId}-${roundNo}`;
    if (seen.current === key) return;
    const first = seen.current === null;
    seen.current = key;
    if (first && value?.length) return;
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

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function mapRow(raw: Record<string, unknown>): InviteRow {
  return {
    id: Number(raw.id),
    applicationId: Number(raw.application_id),
    displayName: String(raw.display_name ?? ''),
    jobName: textOf(raw.job_name),
    fileName: textOf(raw.file_name),
    roundNo: Number(raw.round_no),
    interviewerUserId: Number(raw.interviewer_user_id),
    interviewerName: textOf(raw.interviewer_name),
    interviewAt: raw.interview_at == null ? undefined : String(raw.interview_at).replace('T', ' ').slice(0, 19),
    durationMin: raw.duration_min == null ? 60 : Number(raw.duration_min),
    location: textOf(raw.location),
    status: textOf(raw.status),
    failReason: textOf(raw.fail_reason),
  };
}

const InvitePage = memo(function InvitePage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
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
        width: 220,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '修改',
                perm: 'hr:invite:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'record',
                label: '生成面试记录',
                perm: 'hr:record:add',
                onClick: () => {
                  navigate('/hr/record', {
                    state: {
                      fromInvite: {
                        inviteId: record.id,
                        applicationId: record.applicationId,
                        roundNo: record.roundNo,
                        interviewerUserId: record.interviewerUserId,
                        interviewAt: record.interviewAt,
                      },
                    },
                  });
                },
              },
              {
                key: 'delete',
                label: '删除',
                perm: 'hr:invite:delete',
                confirmTitle: `确认删除「${record.displayName}」这场邀约？钉钉日程会一并取消。`,
                onClick: async () => {
                  await deleteHrInviteApi(record.id);
                  message.success('已删除');
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
        formItemProps: { rules: [{ required: true, message: '请选择面试官' }] },
        renderFormItem: () => (
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
        render: (_, record) => STATUS.find((item) => item.value === record.status)?.label || record.status,
      },
      {
        title: '失败原因',
        dataIndex: 'failReason',
        search: false,
        hideInForm: true,
        ellipsis: true,
      },
    ],
    [appReq, candidates, editing, message, navigate, plans, users],
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
            interviewerUserId: params.interviewerUserId,
            status: params.status,
          });
          const mapped = rows.map(mapRow);
          const filtered = params.applicationId
            ? mapped.filter((row) => row.applicationId === Number(params.applicationId))
            : mapped;
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const pageRows = filtered.slice(start, start + pageSize);
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.id));
          return { data: pageRows, success: true, total: filtered.length };
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
            key='del'
            color='danger'
            variant='filled'
            perm='hr:invite:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的面试邀约');
                return;
              }
              Modal.confirm({
                title: '批量删除面试邀约',
                content: `确定要删除选中的 ${selectedRowKeys.length} 条邀约吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteHrInviteBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条邀约`);
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
        title={editing ? '修改邀约' : '新增邀约'}
        columns={columns as never}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing ? { ...editing, interviewerUserIds: [editing.interviewerUserId] } : { roundNo: 1, durationMin: 60 }
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
            await updateHrInviteApi(editing.id, payload);
            message.success(
              ids.length > 1
                ? `已保存，并为另外 ${ids.length - 1} 名面试官新建了邀约`
                : '已保存，钉钉日程已按新时间重建',
            );
          } else {
            await createHrInviteApi(payload);
            message.success(`已为 ${ids.length} 名面试官发起邀约。钉钉未配置或未绑定时，会提示不能自动创建日程`);
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default InvitePage;
