import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Select } from 'antd';
import dayjs from 'dayjs';
import { useLocation, useNavigate } from 'react-router-dom';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import {
  deleteHrInterviewRecordBatchApi,
  getHrApplicationsApi,
  getHrRequisitionsApi,
  getHrUsersApi,
  listHrInterviewRecordsApi,
  saveHrInterviewRecordApi,
  saveHrInterviewVerdictApi,
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

interface RecordMember {
  id: number;
  interviewerUserId: number;
  interviewerName?: string;
  conclusion?: string;
  comment?: string;
  interviewedAt?: string;
}

interface RecordRow {
  id: number;
  recordIds: number[];
  applicationId: number;
  requisitionId?: number;
  inviteId?: number;
  displayName: string;
  jobName?: string;
  fileName?: string;
  roundNo: number;
  interviewerUserId: number;
  interviewerUserIds: number[];
  interviewerName?: string;
  conclusion?: string;
  conclusionsDiffer: boolean;
  comment?: string;
  interviewedAt?: string;
  currentStage?: string;
  hasVerdict: boolean;
  members: RecordMember[];
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

function conclusionLabel(value?: string) {
  return CONCLUSIONS.find((item) => item.value === value)?.label || '未填';
}

function mapRow(raw: Record<string, unknown>): RecordRow {
  const conclusion = textOf(raw.conclusion);
  const comment = textOf(raw.comment);
  const interviewerName = textOf(raw.interviewer_name);
  const interviewedAt =
    raw.interviewed_at == null ? undefined : String(raw.interviewed_at).replace('T', ' ').slice(0, 19);
  const interviewerUserId = Number(raw.interviewer_user_id);
  const id = Number(raw.id);
  return {
    id,
    recordIds: [id],
    applicationId: Number(raw.application_id),
    requisitionId: raw.requisition_id == null ? undefined : Number(raw.requisition_id),
    inviteId: raw.invite_id == null ? undefined : Number(raw.invite_id),
    displayName: String(raw.display_name ?? ''),
    jobName: textOf(raw.job_name),
    fileName: textOf(raw.file_name),
    roundNo: Number(raw.round_no),
    interviewerUserId,
    interviewerUserIds: [interviewerUserId],
    interviewerName,
    conclusion,
    conclusionsDiffer: false,
    comment,
    interviewedAt,
    currentStage: textOf(raw.current_stage),
    hasVerdict: Number(raw.has_verdict) === 1,
    members: [
      {
        id,
        interviewerUserId,
        interviewerName,
        conclusion,
        comment,
        interviewedAt,
      },
    ],
  };
}

function groupRecords(rows: RecordRow[]) {
  const grouped = new Map<string, RecordRow>();
  rows.forEach((row) => {
    const key = `${row.applicationId}|${row.roundNo}`;
    const existing = grouped.get(key);
    if (!existing) {
      grouped.set(key, {
        ...row,
        recordIds: [...row.recordIds],
        interviewerUserIds: [...row.interviewerUserIds],
        members: row.members.map((member) => ({ ...member })),
      });
      return;
    }
    existing.recordIds.push(row.id);
    existing.members.push({ ...row.members[0] });
    existing.hasVerdict = existing.hasVerdict || row.hasVerdict;
    if (!existing.interviewerUserIds.includes(row.interviewerUserId)) {
      existing.interviewerUserIds.push(row.interviewerUserId);
      existing.interviewerName = [existing.interviewerName, row.interviewerName].filter(Boolean).join('、');
    }
    if (row.interviewedAt && (!existing.interviewedAt || row.interviewedAt > existing.interviewedAt)) {
      existing.interviewedAt = row.interviewedAt;
    }
  });
  return [...grouped.values()].map((row) => {
    const filled = row.members.map((member) => member.conclusion).filter((item): item is string => Boolean(item));
    const unique = [...new Set(filled)];
    const differ = row.members.length > 1 && filled.length === row.members.length && unique.length > 1;
    const sameComment = row.members.every((member) => member.comment === row.members[0].comment);
    return {
      ...row,
      conclusionsDiffer: differ,
      conclusion: unique.length === 1 ? unique[0] : undefined,
      comment:
        row.members.length === 1 || sameComment
          ? row.members[0].comment
          : row.members.map((member) => `${member.interviewerName || '面试官'}：${member.comment || '—'}`).join('；'),
    };
  });
}

function groupKey(row: Pick<RecordRow, 'applicationId' | 'roundNo'>) {
  return `${row.applicationId}-${row.roundNo}`;
}

const RecordPage = memo(function RecordPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<string>>(new Set());
  const groupedRef = useRef<RecordRow[]>([]);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<RecordRow | null>(null);
  const [fromInvite, setFromInvite] = useState<FromInvite | null>(null);
  const [verdictTarget, setVerdictTarget] = useState<RecordRow | null>(null);
  const [verdictSaving, setVerdictSaving] = useState(false);
  const [verdictForm] = Form.useForm();
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
        width: 200,
        render: (_, record) => (
          <ActionButtons
            items={[
              ...(record.conclusionsDiffer && !record.hasVerdict
                ? [
                    {
                      key: 'verdict',
                      label: '联合评价',
                      perm: 'hr:record:edit',
                      onClick: () => {
                        verdictForm.resetFields();
                        setVerdictTarget(record);
                      },
                    },
                  ]
                : []),
              ...(!record.conclusionsDiffer
                ? [
                    {
                      key: 'edit',
                      label: '编辑',
                      perm: 'hr:record:edit',
                      onClick: () => {
                        setEditing(record);
                        setOpen(true);
                      },
                    },
                  ]
                : []),
              {
                key: 'delete',
                label: '删除',
                perm: 'hr:record:delete',
                confirmTitle: `确认删除「${record.displayName}」${ROUNDS.find((item) => item.value === record.roundNo)?.label || ''}的面试记录？`,
                onClick: async () => {
                  await deleteHrInterviewRecordBatchApi(record.recordIds);
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
        render: (_, record) =>
          record.conclusionsDiffer
            ? record.members
                .map((member) => `${member.interviewerName || '面试官'} ${conclusionLabel(member.conclusion)}`)
                .join('、')
            : conclusionLabel(record.conclusion),
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
    [appReq, candidates, fromInvite, message, plans, users, verdictForm],
  );

  return (
    <>
      <BaseProTable<RecordRow>
        rowKey={(row) => groupKey(row)}
        actionRef={actionRef}
        columns={columns}
        headerTitle='面试记录'
        request={async (params) => {
          const rows = await listHrInterviewRecordsApi({
            roundNo: params.roundNo,
          });
          const grouped = groupRecords(rows.map(mapRow)).filter((row) => {
            if (params.applicationId && row.applicationId !== Number(params.applicationId)) return false;
            if (params.interviewerUserId && !row.interviewerUserIds.includes(Number(params.interviewerUserId))) {
              return false;
            }
            return true;
          });
          groupedRef.current = grouped;
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const pageRows = grouped.slice(start, start + pageSize);
          currentPageKeysRef.current = new Set(pageRows.map((row) => groupKey(row)));
          return { data: pageRows, success: true, total: grouped.length };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => {
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as string[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as string[]).forEach((k) => global.add(k));
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
                  const ids = groupedRef.current
                    .filter((row) => selectedRowKeys.includes(groupKey(row)))
                    .flatMap((row) => row.recordIds);
                  await deleteHrInterviewRecordBatchApi(ids);
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
        title={editing ? '编辑面试记录' : '生成面试记录'}
        columns={columns as never}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing
            ? {
                applicationId: editing.applicationId,
                roundNo: editing.roundNo,
                interviewerUserIds: editing.interviewerUserIds,
                conclusion: editing.conclusion,
                comment: editing.members.every((member) => member.comment === editing.members[0]?.comment)
                  ? editing.members[0]?.comment
                  : undefined,
                interviewedAt: editing.interviewedAt,
              }
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
            id: editing?.members.find((member) => member.interviewerUserId === ids[0])?.id,
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
      <Modal
        title={
          verdictTarget
            ? `联合评价：${verdictTarget.displayName} · ${ROUNDS.find((item) => item.value === verdictTarget.roundNo)?.label || ''}`
            : '联合评价'
        }
        open={verdictTarget != null}
        confirmLoading={verdictSaving}
        okText='提交联合评价'
        maskClosable={false}
        onCancel={() => setVerdictTarget(null)}
        onOk={async () => {
          const values = await verdictForm.validateFields();
          if (!verdictTarget) return;
          setVerdictSaving(true);
          try {
            const msg = await saveHrInterviewVerdictApi({
              applicationId: verdictTarget.applicationId,
              roundNo: verdictTarget.roundNo,
              conclusion: values.conclusion,
              comment: values.comment,
            });
            message.success(msg || '已更新候选人阶段');
            setVerdictTarget(null);
            actionRef.current?.reload();
          } finally {
            setVerdictSaving(false);
          }
        }}
      >
        <Form
          form={verdictForm}
          layout='vertical'
        >
          <Form.Item
            name='conclusion'
            label='最终结论'
            rules={[{ required: true, message: '请选择结论' }]}
          >
            <Select
              options={CONCLUSIONS}
              placeholder='请选择结论'
            />
          </Form.Item>
          <Form.Item
            name='comment'
            label='联合评价'
          >
            <Input.TextArea
              rows={4}
              placeholder='填写联合评价，提交后更新候选人阶段'
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default RecordPage;
