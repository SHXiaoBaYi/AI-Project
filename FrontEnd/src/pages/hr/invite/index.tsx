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
  deleteHrInviteBatchApi,
  getHrApplicationsApi,
  getHrRequisitionsApi,
  getHrUsersApi,
  listHrInvitesApi,
  updateHrInviteApi,
} from '@/api/hr';
import {
  InviteCandidateSelect,
  buildAppReqMap,
  filterInviteableCandidates,
  mapApplicationCandidates,
  type InviteCandidateOption,
} from '@/components/hr/inviteRound';
import {
  BOOKING_MAX_DAYS,
  bookingDateTimeError,
  disabledBookingDate,
  interviewPastDisabledTime,
} from '@/utils/chinaHoliday';
import type { Dayjs } from 'dayjs';

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

/** 面试开始：每天 09:30～17:30，半小时一档（不含秒） */
const INTERVIEW_START_MIN = 9 * 60 + 30;
const INTERVIEW_END_MIN = 17 * 60 + 30;

function interviewAtDisabledTime(selected?: Dayjs | null) {
  const past = interviewPastDisabledTime(selected);
  return {
    disabledHours: () => {
      const hours = new Set<number>(past.disabledHours());
      for (let h = 0; h < 24; h++) {
        if (h < 9 || h > 17) hours.add(h);
      }
      return [...hours];
    },
    disabledMinutes: (hour: number) => {
      const blocked = new Set<number>(past.disabledMinutes(hour));
      if (hour === 9) blocked.add(0);
      return [...blocked];
    },
  };
}

function validateInterviewAt(_: unknown, value: unknown) {
  const d = dayjs.isDayjs(value) ? value : value ? dayjs(value as string) : null;
  if (!d || !d.isValid()) {
    return Promise.reject(new Error('请选择时间'));
  }
  if (d.minute() % 30 !== 0) {
    return Promise.reject(new Error('开始时间须为半小时整点（如 09:30、10:00）'));
  }
  const mins = d.hour() * 60 + d.minute();
  if (mins < INTERVIEW_START_MIN || mins > INTERVIEW_END_MIN) {
    return Promise.reject(new Error('开始时间须在每天 09:30～17:30 之间'));
  }
  const bookingErr = bookingDateTimeError(d);
  if (bookingErr) return Promise.reject(new Error(bookingErr));
  return Promise.resolve();
}

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
  ccUserIds: number[];
  ccNames?: string;
  interviewAt?: string;
  durationMin?: number;
  location?: string;
  status?: string;
  memberStatuses: string[];
  failReason?: string;
  hasRecord: boolean;
}

function RoundPeopleSelect({
  value,
  onChange,
  users,
  appReq,
  plans,
  placeholder,
}: {
  value?: number[];
  onChange?: (value: number[]) => void;
  users: { value: number; label: string }[];
  appReq: Map<number, number>;
  plans: Map<number, Map<number, number[]>>;
  placeholder: string;
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
    // 编辑打开时保留已有值（含空数组）；新增或切换候选人/轮次时按需求配置带出
    if (opening && value != null) return;
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
      placeholder={placeholder}
      style={{ width: '100%' }}
      allowClear
    />
  );
}

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function mapRow(raw: Record<string, unknown>): InviteRow {
  const interviewerUserId = Number(raw.interviewer_user_id);
  const status = textOf(raw.status);
  const hasRecord = raw.record_id != null && raw.record_id !== '';
  const ccUserIds = String(raw.cc_user_ids ?? '')
    .split('|')
    .map((item) => Number(item))
    .filter((item) => item > 0);
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
    ccUserIds,
    ccNames: textOf(raw.cc_names),
    interviewAt: raw.interview_at == null ? undefined : String(raw.interview_at).replace('T', ' ').slice(0, 16),
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
        ccUserIds: [...row.ccUserIds],
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
    row.ccUserIds.forEach((id) => {
      if (!existing.ccUserIds.includes(id)) existing.ccUserIds.push(id);
    });
    if (row.ccNames && existing.ccNames !== row.ccNames) {
      existing.ccNames = [existing.ccNames, row.ccNames].filter(Boolean).join('、');
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
  const [candidates, setCandidates] = useState<InviteCandidateOption[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [jobOptions, setJobOptions] = useState<{ value: string; label: string }[]>([]);
  const [appReq, setAppReq] = useState<Map<number, number>>(new Map());
  const [plans, setPlans] = useState<Map<number, Map<number, number[]>>>(new Map());
  const [ccPlans, setCcPlans] = useState<Map<number, Map<number, number[]>>>(new Map());

  useEffect(() => {
    getHrApplicationsApi().then((rows) => {
      const mapped = mapApplicationCandidates(rows as Record<string, unknown>[]);
      setCandidates(mapped);
      setAppReq(buildAppReqMap(mapped));
    });
    getHrRequisitionsApi().then((rows) => {
      const nextInterviewers = new Map<number, Map<number, number[]>>();
      const nextCcs = new Map<number, Map<number, number[]>>();
      const parseRounds = (text: unknown) => {
        const rounds = new Map<number, number[]>();
        String(text ?? '')
          .split(',')
          .filter(Boolean)
          .forEach((part) => {
            const [round, ids] = part.split(':');
            rounds.set(
              Number(round),
              (ids ?? '')
                .split('|')
                .filter(Boolean)
                .map(Number)
                .filter((id) => id > 0),
            );
          });
        return rounds;
      };
      const jobs = new Set<string>();
      rows.forEach((row) => {
        nextInterviewers.set(Number(row.id), parseRounds(row.interview_rounds));
        nextCcs.set(Number(row.id), parseRounds(row.interview_round_ccs));
        const name = String(row.job_name ?? '').trim();
        if (name) jobs.add(name);
      });
      setPlans(nextInterviewers);
      setCcPlans(nextCcs);
      setJobOptions([...jobs].sort().map((name) => ({ value: name, label: name })));
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

  const formCandidates = useMemo(
    () => filterInviteableCandidates(candidates, plans, [editing?.applicationId]),
    [candidates, plans, editing?.applicationId],
  );

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
        title: '岗位',
        dataIndex: 'jobName',
        valueType: 'select',
        hideInForm: true,
        fieldProps: { options: jobOptions, showSearch: true, optionFilterProp: 'label', allowClear: true },
      },
      {
        title: '候选人',
        dataIndex: 'applicationId',
        valueType: 'select',
        hideInForm: true,
        fieldProps: { options: candidates, showSearch: true, optionFilterProp: 'label', allowClear: true },
        render: (_, record) => record.displayName,
      },
      {
        title: '候选人',
        dataIndex: 'applicationId',
        hideInTable: true,
        hideInSearch: true,
        search: false,
        formItemProps: {
          rules: [{ required: true, message: '请选择候选人' }],
          extra: '仅展示阶段可邀约的候选人；选中后自动带出轮次、面试官与抄送人，可再改',
        },
        formItemRender: () => (
          <InviteCandidateSelect
            options={formCandidates}
            plans={plans}
            autoRound={!editing}
          />
        ),
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
        search: false,
        fieldProps: { options: ROUNDS },
        formItemProps: { rules: [{ required: true, message: '请选择轮次' }] },
        render: (_, record) => ROUNDS.find((item) => item.value === record.roundNo)?.label,
      },
      {
        title: '面试官',
        dataIndex: 'interviewerUserId',
        valueType: 'select',
        hideInForm: true,
        fieldProps: { options: users, showSearch: true, optionFilterProp: 'label', allowClear: true },
        render: (_, record) => record.interviewerName,
      },
      {
        title: '抄送人',
        dataIndex: 'ccNames',
        search: false,
        hideInForm: true,
        ellipsis: true,
        render: (_, record) => record.ccNames || '-',
      },
      {
        title: '面试官',
        dataIndex: 'interviewerUserIds',
        hideInTable: true,
        hideInSearch: true,
        search: false,
        hideInForm: false,
        colProps: { span: 24 },
        formItemProps: { rules: [{ required: true, message: '请选择面试官' }] },
        formItemRender: () => (
          <RoundPeopleSelect
            users={users}
            appReq={appReq}
            plans={plans}
            placeholder='选择候选人与轮次后自动带出，可改'
          />
        ),
      },
      {
        title: '抄送人',
        dataIndex: 'ccUserIds',
        hideInTable: true,
        hideInSearch: true,
        search: false,
        hideInForm: false,
        colProps: { span: 24 },
        formItemRender: () => (
          <RoundPeopleSelect
            users={users}
            appReq={appReq}
            plans={ccPlans}
            placeholder='选择候选人与轮次后自动带出，可改'
          />
        ),
      },
      {
        title: '开始时间',
        dataIndex: 'interviewAt',
        valueType: 'dateTime',
        search: false,
        fieldProps: {
          format: 'YYYY-MM-DD HH:mm',
          disabledDate: disabledBookingDate,
          showTime: {
            format: 'HH:mm',
            minuteStep: 30,
            hideDisabledOptions: true,
            showSecond: false,
          },
          disabledTime: (date: Dayjs) => interviewAtDisabledTime(date),
        },
        formItemProps: {
          extra: `每天 09:30～17:30，半小时一档；不可选过去、法定节假日，最多未来 ${BOOKING_MAX_DAYS} 天`,
          rules: [{ required: true, validator: validateInterviewAt }],
        },
        render: (_, record) => record.interviewAt || '—',
      },
      {
        title: '邀约时间',
        dataIndex: 'inviteTimeRange',
        valueType: 'dateRange',
        hideInTable: true,
        hideInForm: true,
        fieldProps: { allowClear: true },
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
        title: '邀约状态',
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
    [appReq, candidates, ccPlans, editing, formCandidates, jobOptions, message, plans, users],
  );

  return (
    <>
      <BaseProTable<InviteRow>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='面试邀约记录'
        request={async (params) => {
          const rows = await listHrInvitesApi({});
          let grouped = groupSessions(rows.map(mapRow));
          if (params.jobName) {
            grouped = grouped.filter((row) => row.jobName === String(params.jobName));
          }
          if (params.applicationId) {
            grouped = grouped.filter((row) => row.applicationId === Number(params.applicationId));
          }
          if (params.interviewerUserId) {
            grouped = grouped.filter((row) => row.interviewerUserIds.includes(Number(params.interviewerUserId)));
          }
          if (params.status) {
            grouped = grouped.filter((row) => row.memberStatuses.includes(String(params.status)));
          }
          const range = params.inviteTimeRange as [string, string] | undefined;
          if (range?.[0] && range?.[1]) {
            const start = dayjs(range[0]).startOf('day');
            const end = dayjs(range[1]).endOf('day');
            grouped = grouped.filter((row) => {
              if (!row.interviewAt) return false;
              const at = dayjs(row.interviewAt);
              return !at.isBefore(start) && !at.isAfter(end);
            });
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
          editing
            ? {
                ...editing,
                interviewerUserIds: editing.interviewerUserIds,
                ccUserIds: editing.ccUserIds ?? [],
              }
            : { roundNo: 1, durationMin: 60 }
        }
        onFinish={async (values) => {
          const form = values as InviteRow & { interviewerUserIds?: number[]; ccUserIds?: number[] };
          const ids = form.interviewerUserIds ?? [];
          if (!ids.length) {
            message.warning('请选择面试官');
            return false;
          }
          const payload = {
            applicationId: form.applicationId,
            roundNo: form.roundNo,
            interviewerUserIds: ids,
            ccUserIds: form.ccUserIds ?? [],
            interviewAt: dayjs(form.interviewAt).second(0).format('YYYY-MM-DD HH:mm:ss'),
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
              message.success(ids.length > 1 ? `已保存这场邀约，共 ${ids.length} 名面试官` : '已保存邀约');
            }
          } else {
            const saved = await createHrInviteApi(payload);
            if (saved?.warning) message.warning(saved.warning, 8);
            else message.success(`已为 ${ids.length} 名面试官发起邀约`);
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default InvitePage;
