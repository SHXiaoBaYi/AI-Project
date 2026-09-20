import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Modal, Select } from 'antd';
import dayjs from 'dayjs';
import { useLocation, useNavigate } from 'react-router-dom';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import {
  deleteHrInterviewRecordApi,
  deleteHrInterviewRecordBatchApi,
  getHrApplicationsApi,
  getHrRequisitionsApi,
  getHrUsersApi,
  listHrInterviewRecordsApi,
  saveHrInterviewRecordApi,
} from '@/api/hr';

const ROUNDS = [
  { value: 1, label: '一面' },
  { value: 2, label: '二面' },
  { value: 3, label: '三面' },
  { value: 4, label: '四面' },
  { value: 5, label: '五面' },
];

const CONCLUSIONS = [
  { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '未通过' },
  { value: 'PENDING', label: '待定' },
];

interface RecordRow {
  id: number;
  applicationId: number;
  requisitionId?: number;
  inviteId?: number;
  displayName: string;
  jobName?: string;
  fileName?: string;
  roundNo: number;
  interviewerUserId: number;
  interviewerName?: string;
  conclusion?: string;
  comment?: string;
  interviewedAt?: string;
}

interface FromInvite {
  inviteId: number;
  applicationId: number;
  roundNo: number;
  interviewerUserId?: number;
  interviewAt?: string;
}

function InterviewerSelect({
  value,
  onChange,
  users,
  appReq,
  plans,
  fallback,
}: {
  value?: number[];
  onChange?: (value: number[]) => void;
  users: { value: number; label: string }[];
  appReq: Map<number, number>;
  plans: Map<number, Map<number, number[]>>;
  fallback?: FromInvite | null;
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
    const fromProcess = reqId == null ? [] : (plans.get(reqId)?.get(Number(roundNo)) ?? []);
    const sameInvite =
      fallback != null &&
      Number(applicationId) === fallback.applicationId &&
      Number(roundNo) === fallback.roundNo &&
      fallback.interviewerUserId;
    const ids = fromProcess.length ? fromProcess : sameInvite ? [fallback.interviewerUserId as number] : fromProcess;
    onChangeRef.current?.(ids);
  }, [applicationId, roundNo, appReq, plans, fallback, value]);

  return (
    <Select
      mode='multiple'
      value={value}
      onChange={onChange}
      options={users}
      showSearch
      optionFilterProp='label'
      placeholder='选择候选人与轮次后，按面试流程带出，可再改'
      style={{ width: '100%' }}
    />
  );
}

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function mapRow(raw: Record<string, unknown>): RecordRow {
  return {
    id: Number(raw.id),
    applicationId: Number(raw.application_id),
    requisitionId: raw.requisition_id == null ? undefined : Number(raw.requisition_id),
    inviteId: raw.invite_id == null ? undefined : Number(raw.invite_id),
    displayName: String(raw.display_name ?? ''),
    jobName: textOf(raw.job_name),
    fileName: textOf(raw.file_name),
    roundNo: Number(raw.round_no),
    interviewerUserId: Number(raw.interviewer_user_id),
    interviewerName: textOf(raw.interviewer_name),
    conclusion: textOf(raw.conclusion),
    comment: textOf(raw.comment),
    interviewedAt: raw.interviewed_at == null ? undefined : String(raw.interviewed_at).replace('T', ' ').slice(0, 19),
  };
}

const RecordPage = memo(function RecordPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<RecordRow | null>(null);
  const [fromInvite, setFromInvite] = useState<FromInvite | null>(null);
  const [candidates, setCandidates] = useState<{ value: number; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [appReq, setAppReq] = useState<Map<number, number>>(new Map());
  const [plans, setPlans] = useState<Map<number, Map<number, number[]>>>(new Map());

  useEffect(() => {
    const incoming = (location.state as { fromInvite?: FromInvite } | null)?.fromInvite;
    if (!incoming) return;
    setEditing(null);
    setFromInvite(incoming);
    setOpen(true);
    navigate('/hr/record', { replace: true, state: null });
  }, [location.state, navigate]);

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
          const row = item as { userId?: number; user_id?: number; nickname?: string; username?: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname || row.username || '' };
        }),
      ),
    );
  }, []);

  const columns: ProColumnType<RecordRow>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 140,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '修改',
                perm: 'hr:record:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'delete',
                label: '删除',
                perm: 'hr:record:delete',
                confirmTitle: `确认删除「${record.displayName}」的这条评语？`,
                onClick: async () => {
                  await deleteHrInterviewRecordApi(record.id);
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
        colProps: { span: 24 },
        formItemProps: { rules: [{ required: true, message: '请选择面试官' }] },
        formItemRender: () => (
          <InterviewerSelect
            users={users}
            appReq={appReq}
            plans={plans}
            fallback={fromInvite}
          />
        ),
      },
      {
        title: '结论',
        dataIndex: 'conclusion',
        valueType: 'select',
        search: false,
        fieldProps: { options: CONCLUSIONS },
        formItemProps: { rules: [{ required: true, message: '请选择结论' }] },
        render: (_, record) => CONCLUSIONS.find((item) => item.value === record.conclusion)?.label || '—',
      },
      {
        title: '评语',
        dataIndex: 'comment',
        valueType: 'textarea',
        search: false,
        ellipsis: true,
      },
      {
        title: '面试时间',
        dataIndex: 'interviewedAt',
        valueType: 'dateTime',
        search: false,
      },
    ],
    [appReq, candidates, editing, fromInvite, message, plans, users],
  );

  return (
    <>
      <BaseProTable<RecordRow>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='面试记录'
        request={async (params) => {
          const rows = await listHrInterviewRecordsApi({
            roundNo: params.roundNo,
            interviewerUserId: params.interviewerUserId,
          });
          const mapped = rows
            .map(mapRow)
            .filter((row) => !params.applicationId || row.applicationId === Number(params.applicationId));
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const pageRows = mapped.slice(start, start + pageSize);
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.id));
          return { data: pageRows, success: true, total: mapped.length };
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
            perm='hr:record:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的面试记录');
                return;
              }
              Modal.confirm({
                title: '批量删除面试记录',
                content: `确定要删除选中的 ${selectedRowKeys.length} 条面试记录吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteHrInterviewRecordBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条面试记录`);
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
            perm='hr:record:add'
            onClick={() => {
              setEditing(null);
              setFromInvite(null);
              setOpen(true);
            }}
          >
            新增
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '修改面试记录' : '生成面试记录'}
        columns={columns as never}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing
            ? { ...editing, interviewerUserIds: [editing.interviewerUserId] }
            : {
                applicationId: fromInvite?.applicationId,
                roundNo: fromInvite?.roundNo,
                interviewedAt: fromInvite?.interviewAt,
              }
        }
        onFinish={async (values) => {
          const form = values as RecordRow & { interviewerUserIds?: number[] };
          const ids = form.interviewerUserIds ?? [];
          if (!ids.length) {
            message.warning('请选择面试官');
            return false;
          }
          const interviewedAt = form.interviewedAt
            ? dayjs(form.interviewedAt).format('YYYY-MM-DD HH:mm:ss')
            : undefined;
          const msg = await saveHrInterviewRecordApi({
            id: editing?.id,
            applicationId: form.applicationId,
            requisitionId: editing?.requisitionId,
            inviteId: editing?.inviteId ?? fromInvite?.inviteId,
            roundNo: form.roundNo,
            interviewerUserIds: ids,
            conclusion: form.conclusion,
            comment: form.comment,
            interviewedAt,
          });
          message.success(msg || '已保存');
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default RecordPage;
