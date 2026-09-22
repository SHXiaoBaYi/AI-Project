import { useEffect, useMemo, useRef, useState } from 'react';
import { App, Form, Input, InputNumber, Modal, Select } from 'antd';
import { DatePicker } from 'antd';
import dayjs from 'dayjs';
import {
  createHrInviteApi,
  getHrApplicationsApi,
  getHrRequisitionsApi,
  getHrUsersApi,
  updateHrInviteApi,
} from '@/api/hr';

const ROUNDS = [
  { value: 1, label: '一面' },
  { value: 2, label: '二面' },
  { value: 3, label: '三面' },
  { value: 4, label: '四面' },
  { value: 5, label: '五面' },
];

function parseRoundPeople(text: unknown) {
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
}

function RoundPeopleSelect({
  value,
  onChange,
  users,
  appReq,
  plans,
  placeholder,
  autoFill,
}: {
  value?: number[];
  onChange?: (value: number[]) => void;
  users: { value: number; label: string }[];
  appReq: Map<number, number>;
  plans: Map<number, Map<number, number[]>>;
  placeholder: string;
  autoFill: boolean;
}) {
  const form = Form.useFormInstance();
  const applicationId = Form.useWatch('applicationId', form);
  const roundNo = Form.useWatch('roundNo', form);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const seen = useRef<string | null>(null);

  useEffect(() => {
    if (!autoFill) return;
    if (applicationId == null || roundNo == null) return;
    if (plans.size === 0 || appReq.size === 0) return;
    const key = `${applicationId}-${roundNo}`;
    if (seen.current === key) return;
    const opening = seen.current === null;
    seen.current = key;
    if (opening && Array.isArray(value) && value.length > 0) return;
    const reqId = appReq.get(Number(applicationId));
    const ids = reqId == null ? [] : (plans.get(reqId)?.get(Number(roundNo)) ?? []);
    onChangeRef.current?.(ids);
  }, [applicationId, roundNo, appReq, plans, value, autoFill]);

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

export type InviteFormValues = {
  applicationId?: number;
  roundNo?: number;
  interviewerUserIds?: number[];
  ccUserIds?: number[];
  interviewAt?: dayjs.Dayjs | string;
  durationMin?: number;
  location?: string;
};

export type InviteFormPreset = {
  applicationId: number;
  displayName: string;
  jobName?: string;
  roundNo: number;
  interviewerUserIds?: number[];
  ccUserIds?: number[];
  durationMin?: number;
  location?: string;
  interviewAt?: string;
};

type InviteFormModalProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
  /** 从候选人列表带入：锁定候选人与轮次 */
  preset?: InviteFormPreset | null;
  lockCandidate?: boolean;
  lockRound?: boolean;
  /** 编辑已有邀约时传入主键 */
  editingId?: number | null;
  editingInitial?: InviteFormValues | null;
  /** 助手等场景预填，不锁定字段 */
  seed?: InviteFormValues | null;
};

export default function InviteFormModal({
  open,
  onOpenChange,
  onSuccess,
  preset,
  lockCandidate = false,
  lockRound = false,
  editingId,
  editingInitial,
  seed,
}: InviteFormModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm<InviteFormValues>();
  const [saving, setSaving] = useState(false);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [candidates, setCandidates] = useState<{ value: number; label: string }[]>([]);
  const [appReq, setAppReq] = useState<Map<number, number>>(new Map());
  const [plans, setPlans] = useState<Map<number, Map<number, number[]>>>(new Map());
  const [ccPlans, setCcPlans] = useState<Map<number, Map<number, number[]>>>(new Map());

  useEffect(() => {
    if (!open) return;
    getHrUsersApi().then((list) =>
      setUsers(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      ),
    );
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
      const nextInterviewers = new Map<number, Map<number, number[]>>();
      const nextCcs = new Map<number, Map<number, number[]>>();
      rows.forEach((row) => {
        nextInterviewers.set(Number(row.id), parseRoundPeople(row.interview_rounds));
        nextCcs.set(Number(row.id), parseRoundPeople(row.interview_round_ccs));
      });
      setPlans(nextInterviewers);
      setCcPlans(nextCcs);
    });
  }, [open]);

  const candidateOptions = useMemo(() => {
    if (!preset) return candidates;
    const exists = candidates.some((item) => item.value === preset.applicationId);
    if (exists) return candidates;
    return [
      {
        value: preset.applicationId,
        label: `${preset.displayName} · ${preset.jobName || '未定岗'}`,
      },
      ...candidates,
    ];
  }, [candidates, preset]);

  useEffect(() => {
    if (!open) return;
    if (editingInitial) {
      form.setFieldsValue({
        ...editingInitial,
        interviewAt: editingInitial.interviewAt ? dayjs(editingInitial.interviewAt) : undefined,
      });
      return;
    }
    if (preset) {
      form.setFieldsValue({
        applicationId: preset.applicationId,
        roundNo: preset.roundNo,
        interviewerUserIds: preset.interviewerUserIds,
        ccUserIds: preset.ccUserIds,
        durationMin: preset.durationMin ?? 60,
        location: preset.location,
        interviewAt: preset.interviewAt ? dayjs(preset.interviewAt) : undefined,
      });
      return;
    }
    if (seed) {
      form.setFieldsValue({
        roundNo: seed.roundNo ?? 1,
        durationMin: seed.durationMin ?? 60,
        applicationId: seed.applicationId,
        interviewerUserIds: seed.interviewerUserIds,
        ccUserIds: seed.ccUserIds,
        location: seed.location,
        interviewAt: seed.interviewAt ? dayjs(seed.interviewAt) : undefined,
      });
      return;
    }
    form.setFieldsValue({ roundNo: 1, durationMin: 60, interviewerUserIds: undefined, ccUserIds: undefined });
  }, [open, preset, editingInitial, seed, form]);

  const title = editingId ? '编辑邀约' : preset ? `邀约：${preset.displayName}` : '新增邀约';
  const autoFillPeople = !editingId;

  return (
    <Modal
      title={title}
      open={open}
      confirmLoading={saving}
      okText={editingId ? '保存' : '发起邀约'}
      destroyOnHidden
      onCancel={() => onOpenChange(false)}
      onOk={async () => {
        const values = await form.validateFields();
        const ids = values.interviewerUserIds ?? [];
        if (!ids.length) {
          message.warning('请选择面试官');
          return;
        }
        const payload = {
          applicationId: Number(values.applicationId),
          roundNo: Number(values.roundNo),
          interviewerUserIds: ids,
          ccUserIds: values.ccUserIds ?? [],
          interviewAt: dayjs(values.interviewAt).format('YYYY-MM-DD HH:mm:ss'),
          durationMin: values.durationMin,
          location: values.location,
        };
        setSaving(true);
        try {
          if (editingId) {
            const saved = await updateHrInviteApi(editingId, payload);
            if (saved?.warning) message.warning(saved.warning, 8);
            else message.success(ids.length > 1 ? `已保存这场邀约，共 ${ids.length} 名面试官` : '已保存');
          } else {
            const saved = await createHrInviteApi(payload);
            if (saved?.warning) message.warning(saved.warning, 8);
            else message.success(`已为 ${ids.length} 名面试官发起邀约，钉钉日程已创建`);
          }
          onOpenChange(false);
          onSuccess?.();
        } finally {
          setSaving(false);
        }
      }}
    >
      <Form
        form={form}
        layout='vertical'
        className='mt-2'
      >
        <Form.Item
          name='applicationId'
          label='候选人'
          rules={[{ required: true, message: '请选择候选人' }]}
        >
          <Select
            options={candidateOptions}
            showSearch
            optionFilterProp='label'
            placeholder='请选择候选人'
            disabled={lockCandidate}
          />
        </Form.Item>
        <Form.Item
          name='roundNo'
          label='轮次'
          rules={[{ required: true, message: '请选择轮次' }]}
        >
          <Select
            options={ROUNDS}
            placeholder='请选择轮次'
            disabled={lockRound}
          />
        </Form.Item>
        <Form.Item
          name='interviewerUserIds'
          label='面试官'
          rules={[{ required: true, message: '请选择面试官' }]}
        >
          <RoundPeopleSelect
            users={users}
            appReq={appReq}
            plans={plans}
            placeholder='选择候选人与轮次后自动带出'
            autoFill={autoFillPeople}
          />
        </Form.Item>
        <Form.Item
          name='ccUserIds'
          label='抄送人'
        >
          <RoundPeopleSelect
            users={users}
            appReq={appReq}
            plans={ccPlans}
            placeholder='选择候选人与轮次后自动带出，可改'
            autoFill={autoFillPeople}
          />
        </Form.Item>
        <Form.Item
          name='interviewAt'
          label='开始时间'
          rules={[{ required: true, message: '请选择时间' }]}
        >
          <DatePicker
            showTime
            className='w-full'
            format='YYYY-MM-DD HH:mm'
          />
        </Form.Item>
        <Form.Item
          name='durationMin'
          label='时长（分钟）'
        >
          <InputNumber
            className='w-full'
            min={15}
            max={240}
          />
        </Form.Item>
        <Form.Item
          name='location'
          label='地点'
        >
          <Input placeholder='可选' />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 招聘需求配置的最大轮次 */
export function maxRoundOf(plans: Map<number, Map<number, number[]>>, requisitionId?: number) {
  if (requisitionId == null) return null;
  const rounds = plans.get(requisitionId);
  if (!rounds || rounds.size === 0) return null;
  return Math.max(...rounds.keys());
}
