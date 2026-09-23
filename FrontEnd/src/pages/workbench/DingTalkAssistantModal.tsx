import { memo, useEffect, useRef, useState } from 'react';
import { App, Avatar, Button, DatePicker, Form, Input, InputNumber, Modal, Radio, Select, Switch, Tabs } from 'antd';
import { useSelector } from 'react-redux';
import dayjs, { type Dayjs } from 'dayjs';
import { usePermission } from '@/hooks/usePermission';
import InviteFormModal, { type InviteFormValues } from '@/components/hr/InviteFormModal';
import {
  createDingTalkAssistantMeetingApi,
  createDingTalkAssistantReportApi,
  getDingTalkBusyUsersApi,
  suggestDingTalkAssistantApi,
  type DingTalkAssistantAction,
  type DingTalkAssistantDayGroup,
  type DingTalkAssistantSlot,
  type DingTalkAssistantSuggest,
  type DingTalkBusyUserOption,
} from '@/api/dingtalk';
import type { RootState } from '@/store';
import {
  BOOKING_MAX_DAYS,
  bookingDateTimeError,
  bookingRangeError,
  bookingWindow,
  disabledBookingDate,
  isChinaHoliday,
} from '@/utils/chinaHoliday';

const ASSISTANT_NAME = '日程助手';
const WORK_START = { hour: 9, minute: 30 };
const WORK_END = { hour: 18, minute: 30 };

type Props = {
  open: boolean;
  onClose: () => void;
  onOpenBusy?: (userIds: number[]) => void;
};

type ChatRole = 'user' | 'assistant' | 'system';

type MessageContext = {
  targetUserId: number;
  targetNickname: string;
  durationMin: number;
};

type ChatMessage = {
  id: string;
  role: ChatRole;
  text?: string;
  suggest?: DingTalkAssistantSuggest;
  /** 本条建议当前选中的天 */
  activeDay?: string;
  /** 本条建议当前选中的时段 */
  selectedKey?: string;
  /** 本条建议对应的同事上下文（不受底部输入栏后续改动影响） */
  context?: MessageContext;
  time: string;
};

type ActionTarget = {
  targetUserId: number;
  targetNickname: string;
};

function msgId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function findSlot(suggest: DingTalkAssistantSuggest | undefined, selectedKey?: string): DingTalkAssistantSlot | null {
  const days = suggest?.dayGroups ?? [];
  if (!days.length) return null;
  if (selectedKey) {
    for (const day of days) {
      for (const slot of day.slots ?? []) {
        if (`${slot.start}|${slot.end}` === selectedKey) return slot;
      }
    }
  }
  return days[0]?.slots?.[0] ?? null;
}

function slotStart(slot: DingTalkAssistantSlot | null | undefined) {
  return slot ? dayjs(slot.start).format('YYYY-MM-DD HH:mm:ss') : undefined;
}

function toWorkRange(from: Dayjs, to: Dayjs): [Dayjs, Dayjs] {
  const { min, max } = bookingWindow();
  let start = from.startOf('day');
  let end = to.startOf('day');
  if (start.isBefore(min, 'day')) start = min.startOf('day');
  if (end.isAfter(max, 'day')) end = max.startOf('day');
  if (end.isBefore(start, 'day')) end = start;
  return [
    start.hour(WORK_START.hour).minute(WORK_START.minute).second(0),
    end.hour(WORK_END.hour).minute(WORK_END.minute).second(0),
  ];
}

function defaultAskRange(): [Dayjs, Dayjs] {
  const { min, max } = bookingWindow();
  let end = min.add(4, 'day');
  if (end.isAfter(max, 'day')) end = max.startOf('day');
  return toWorkRange(min, end);
}

/** 去掉法定节假日天，以及落在假日上的推荐时段 */
function filterSuggestHolidays(data: DingTalkAssistantSuggest): DingTalkAssistantSuggest {
  const dayGroups = (data.dayGroups ?? [])
    .filter((day) => !isChinaHoliday(day.day))
    .map((day) => ({
      ...day,
      slots: (day.slots ?? []).filter((slot) => !isChinaHoliday(slot.start) && !isChinaHoliday(slot.end)),
    }));
  return { ...data, dayGroups };
}

function initialOf(name: string) {
  const t = name.trim();
  return t ? t.slice(0, 1) : '?';
}

const DingTalkAssistantModal = memo(function DingTalkAssistantModal({ open, onClose, onOpenBusy }: Props) {
  const { message } = App.useApp();
  const { has } = usePermission();
  const userInfo = useSelector((state: RootState) => state.user.userInfo);
  const meName = userInfo?.nickname || userInfo?.username || '我';
  const meAvatar = userInfo?.avatar;
  const listRef = useRef<HTMLDivElement>(null);
  const [users, setUsers] = useState<DingTalkBusyUserOption[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [targetUserId, setTargetUserId] = useState<number>();
  const [durationMin, setDurationMin] = useState(60);
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => defaultAskRange());
  const [querying, setQuerying] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteSeed, setInviteSeed] = useState<InviteFormValues | null>(null);
  const [actionSaving, setActionSaving] = useState(false);
  const [meetingOpen, setMeetingOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [actionTarget, setActionTarget] = useState<ActionTarget | null>(null);
  const [meetingForm] = Form.useForm();
  const [reportForm] = Form.useForm();

  const patchMessage = (id: string, patch: Partial<ChatMessage>) => {
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, ...patch } : m)));
  };

  useEffect(() => {
    if (!open) return;
    setLoadingUsers(true);
    void getDingTalkBusyUsersApi()
      .then((rows) => setUsers(rows ?? []))
      .catch(() => setUsers([]))
      .finally(() => setLoadingUsers(false));
    if (messages.length === 0) {
      setMessages([
        {
          id: msgId(),
          role: 'assistant',
          text: '你好，我是日程助手。选一位同事和日期范围，我会按每天 09:30～18:30 的工作时段查钉钉闲忙并给出建议，也可帮你发起面试邀约、邀请开会或安排工作汇报。',
          time: dayjs().format('HH:mm'),
        },
      ]);
    }
  }, [open, messages.length]);

  useEffect(() => {
    if (!open) return;
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, open]);

  const composerTargetLabel = (() => {
    const user = users.find((u) => u.userId === targetUserId);
    return user ? user.nickname || user.username : '';
  })();

  const push = (msg: Omit<ChatMessage, 'id' | 'time'> & { time?: string }) => {
    const next: ChatMessage = { ...msg, id: msgId(), time: msg.time || dayjs().format('HH:mm') };
    setMessages((prev) => [...prev, next]);
    return next.id;
  };

  const handleAsk = async () => {
    if (!targetUserId) {
      message.warning('请选择同事');
      return;
    }
    if (!range?.[0] || !range?.[1] || range[0].isAfter(range[1], 'day')) {
      message.warning('请选择有效的日期范围');
      return;
    }
    const rangeErr = bookingRangeError(range[0], range[1]);
    if (rangeErr) {
      message.warning(rangeErr);
      return;
    }
    const nickname = composerTargetLabel;
    const askedDuration = durationMin;
    const askedUserId = targetUserId;
    const [askStart, askEnd] = toWorkRange(range[0], range[1]);
    if (!askStart.isBefore(askEnd)) {
      message.warning('请选择有效的日期范围');
      return;
    }
    const userText = `查一下「${nickname}」在 ${askStart.format('MM-DD')} ~ ${askEnd.format('MM-DD')}（每天 09:30～18:30）是否有连续 ${askedDuration} 分钟空闲？`;
    push({ role: 'user', text: userText });
    setQuerying(true);
    try {
      const data = filterSuggestHolidays(
        await suggestDingTalkAssistantApi({
          targetUserId: askedUserId,
          startTime: askStart.format('YYYY-MM-DD HH:mm:ss'),
          endTime: askEnd.format('YYYY-MM-DD HH:mm:ss'),
          durationMin: askedDuration,
        }),
      );
      const firstDay = data.dayGroups?.find((d) => (d.slots?.length ?? 0) > 0) ?? data.dayGroups?.[0];
      const firstSlot = firstDay?.slots?.[0];
      push({
        role: 'assistant',
        text: data.adviceText || '暂无建议',
        suggest: data,
        activeDay: firstDay?.day,
        selectedKey: firstSlot ? `${firstSlot.start}|${firstSlot.end}` : undefined,
        context: {
          targetUserId: askedUserId,
          targetNickname: data.targetNickname || nickname,
          durationMin: data.durationMin || askedDuration,
        },
      });
    } catch (err) {
      push({
        role: 'assistant',
        text: err instanceof Error ? err.message : '查询失败，请稍后重试',
      });
    } finally {
      setQuerying(false);
    }
  };

  const openMeetingModal = (msg: ChatMessage) => {
    const slot = findSlot(msg.suggest, msg.selectedKey);
    const start = slotStart(slot);
    const nickname = msg.context?.targetNickname || composerTargetLabel;
    const userId = msg.context?.targetUserId ?? targetUserId;
    if (!userId) {
      message.warning('请先选择同事');
      return;
    }
    const minutes = msg.context?.durationMin || durationMin || 60;
    meetingForm.setFieldsValue({
      title: nickname ? `与${nickname}的会议` : '会议',
      startTime: start ? dayjs(start) : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: minutes,
      location: '',
      description: '',
      onlineMeeting: true,
    });
    setActionTarget({ targetUserId: userId, targetNickname: nickname || '' });
    setMeetingOpen(true);
  };

  const openReportModal = (msg: ChatMessage) => {
    const slot = findSlot(msg.suggest, msg.selectedKey);
    const start = slotStart(slot);
    const nickname = msg.context?.targetNickname || composerTargetLabel;
    const userId = msg.context?.targetUserId ?? targetUserId;
    if (!userId) {
      message.warning('请先选择同事');
      return;
    }
    const minutes = msg.context?.durationMin || durationMin || 60;
    reportForm.setFieldsValue({
      title: nickname ? `工作汇报 · ${nickname}` : '工作汇报',
      startTime: start ? dayjs(start) : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: minutes,
      location: '',
      content: '',
    });
    setActionTarget({ targetUserId: userId, targetNickname: nickname || '' });
    setReportOpen(true);
  };

  const runAction = (msg: ChatMessage, action: DingTalkAssistantAction) => {
    const payload = action.payload ?? {};
    const ctx = msg.context;
    const userId = Number(payload.targetUserId ?? ctx?.targetUserId ?? targetUserId);
    const minutes = Number(payload.durationMin ?? ctx?.durationMin ?? durationMin) || 60;
    const slot = findSlot(msg.suggest, msg.selectedKey);
    const suggestedStart = slot ? slotStart(slot) : payload.suggestedStart ? String(payload.suggestedStart) : undefined;

    if (action.type === 'VIEW_BUSY') {
      onClose();
      onOpenBusy?.(userId ? [userId] : []);
      return;
    }
    if (action.type === 'CREATE_INVITE') {
      if (!has('hr:invite:add') && !has('*:*:*')) {
        message.warning('没有发起面试邀约的权限');
        return;
      }
      setInviteSeed({
        interviewerUserIds: userId ? [userId] : [],
        interviewAt: suggestedStart,
        durationMin: minutes,
        roundNo: 1,
      });
      setInviteOpen(true);
      return;
    }
    if (action.type === 'CREATE_MEETING') {
      openMeetingModal(msg);
      return;
    }
    if (action.type === 'CREATE_REPORT_TASK') {
      if (!has('task:add') && !has('*:*:*')) {
        message.warning('没有创建任务的权限');
        return;
      }
      openReportModal(msg);
    }
  };

  const submitMeeting = async () => {
    if (!actionTarget?.targetUserId) {
      message.warning('请先选择同事');
      return;
    }
    const values = await meetingForm.validateFields();
    const startErr = bookingDateTimeError(dayjs(values.startTime));
    if (startErr) {
      message.warning(startErr);
      return;
    }
    setActionSaving(true);
    try {
      const result = await createDingTalkAssistantMeetingApi({
        targetUserId: actionTarget.targetUserId,
        title: values.title,
        startTime: dayjs(values.startTime).second(0).format('YYYY-MM-DD HH:mm:ss'),
        durationMin: values.durationMin,
        location: values.location,
        description: values.description,
        onlineMeeting: !!values.onlineMeeting,
      });
      setMeetingOpen(false);
      setActionTarget(null);
      meetingForm.resetFields();
      push({ role: 'user', text: `邀请「${actionTarget.targetNickname}」参加会议：${values.title}` });
      push({ role: 'assistant', text: result.message || '会议已创建' });
      message.success(result.message || '会议已创建');
    } finally {
      setActionSaving(false);
    }
  };

  const submitReport = async () => {
    if (!actionTarget?.targetUserId) {
      message.warning('请先选择同事');
      return;
    }
    const values = await reportForm.validateFields();
    const startErr = bookingDateTimeError(dayjs(values.startTime));
    if (startErr) {
      message.warning(startErr);
      return;
    }
    setActionSaving(true);
    try {
      const result = await createDingTalkAssistantReportApi({
        targetUserId: actionTarget.targetUserId,
        title: values.title,
        content: values.content,
        location: values.location,
        startTime: dayjs(values.startTime).second(0).format('YYYY-MM-DD HH:mm:ss'),
        durationMin: values.durationMin,
      });
      setReportOpen(false);
      setActionTarget(null);
      reportForm.resetFields();
      push({ role: 'user', text: `安排与「${actionTarget.targetNickname}」的工作汇报` });
      push({ role: 'assistant', text: result.message || '汇报已安排' });
      message.success(result.message || '汇报已安排');
    } finally {
      setActionSaving(false);
    }
  };

  return (
    <>
      <Modal
        title='钉钉日程助手'
        open={open}
        width={1200}
        footer={null}
        maskClosable={false}
        destroyOnHidden
        onCancel={() => {
          if (querying || actionSaving) return;
          onClose();
        }}
        styles={{ body: { padding: 0 } }}
      >
        <div className='flex h-[70vh] flex-col'>
          <div
            ref={listRef}
            className='min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-3'
            style={{ background: 'linear-gradient(180deg, #e8eef5 0%, #f0f3f7 100%)' }}
          >
            {messages.map((msg) => {
              const isUser = msg.role === 'user';
              const isSystem = msg.role === 'system';
              const dayKey = msg.activeDay || msg.suggest?.dayGroups?.[0]?.day;
              const senderName = isUser ? meName : isSystem ? '系统' : ASSISTANT_NAME;
              if (isSystem) {
                return (
                  <div
                    key={msg.id}
                    className='flex justify-center'
                  >
                    <div className='max-w-[80%] rounded-md bg-black/5 px-3 py-1 text-center text-xs text-neutral-500'>
                      {msg.text}
                    </div>
                  </div>
                );
              }
              return (
                <div
                  key={msg.id}
                  className={`flex gap-2.5 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
                >
                  <Avatar
                    size={36}
                    src={isUser ? meAvatar || undefined : undefined}
                    className={`shrink-0 ${isUser ? 'bg-[#0089ff]' : 'bg-[#1f7aef]'}`}
                  >
                    {initialOf(senderName)}
                  </Avatar>
                  <div className={`flex max-w-[78%] min-w-0 flex-col ${isUser ? 'items-end' : 'items-start'}`}>
                    <div
                      className={`mb-1 flex items-center gap-1.5 text-xs text-neutral-500 ${isUser ? 'flex-row-reverse' : ''}`}
                    >
                      <span className='font-medium text-neutral-600'>{senderName}</span>
                      <span className='text-neutral-400'>{msg.time}</span>
                    </div>
                    <div
                      className={`relative rounded-lg px-3 py-2 text-sm leading-relaxed shadow-sm ${
                        isUser
                          ? 'rounded-tr-sm bg-[#cce7ff] text-neutral-800'
                          : 'rounded-tl-sm border border-neutral-100 bg-white text-neutral-800'
                      }`}
                    >
                      {msg.text ? <div className='whitespace-pre-wrap'>{msg.text}</div> : null}
                      {msg.context?.targetNickname ? (
                        <div className='mt-1 text-[11px] text-neutral-400'>关于：{msg.context.targetNickname}</div>
                      ) : null}
                      {msg.suggest?.dayGroups?.length ? (
                        <div className='mt-2'>
                          <div className='mb-1 text-xs text-neutral-500'>
                            推荐时段（工作日 09:30～18:30，每段{' '}
                            {msg.suggest.durationMin || msg.context?.durationMin || durationMin} 分钟）
                          </div>
                          <Tabs
                            size='small'
                            activeKey={dayKey}
                            onChange={(key) => {
                              const day = msg.suggest?.dayGroups?.find((d) => d.day === key);
                              const first = day?.slots?.[0];
                              patchMessage(msg.id, {
                                activeDay: key,
                                selectedKey: first ? `${first.start}|${first.end}` : msg.selectedKey,
                              });
                            }}
                            items={msg.suggest.dayGroups.map((day: DingTalkAssistantDayGroup) => ({
                              key: day.day,
                              label: `${day.dayLabel}（${day.slots?.length || 0}）`,
                              children: (
                                <Radio.Group
                                  className='flex max-h-40 w-full flex-col gap-1 overflow-y-auto'
                                  value={msg.selectedKey}
                                  onChange={(e) => patchMessage(msg.id, { selectedKey: e.target.value })}
                                >
                                  {(day.slots || []).map((slot) => {
                                    const key = `${slot.start}|${slot.end}`;
                                    return (
                                      <Radio
                                        key={key}
                                        value={key}
                                        className='!mr-0 rounded-lg border border-neutral-200 bg-neutral-50 px-2 py-1.5'
                                      >
                                        <span className='text-xs text-neutral-700'>{slot.label}</span>
                                      </Radio>
                                    );
                                  })}
                                </Radio.Group>
                              ),
                            }))}
                          />
                        </div>
                      ) : null}
                      {msg.suggest?.actions?.length ? (
                        <div className='mt-2 flex flex-wrap gap-1.5'>
                          {msg.suggest.actions.map((action) => (
                            <Button
                              key={`${msg.id}-${action.type}`}
                              size='small'
                              type={
                                action.type === 'CREATE_MEETING' || action.type === 'CREATE_INVITE'
                                  ? 'primary'
                                  : 'default'
                              }
                              title={action.hint}
                              onClick={() => runAction(msg, action)}
                            >
                              {action.label}
                            </Button>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className='shrink-0 border-t border-neutral-200 bg-white px-4 py-3'>
            <div className='mb-2 text-xs text-neutral-400'>
              闲忙仅统计每天 09:30～18:30；不可选过去、法定节假日，最多未来 {BOOKING_MAX_DAYS} 天
            </div>
            <div className='mb-2 grid gap-2 md:grid-cols-2'>
              <Select
                showSearch
                allowClear
                optionFilterProp='label'
                placeholder={loadingUsers ? '正在加载同事' : '选择要问的同事'}
                loading={loadingUsers}
                value={targetUserId}
                onChange={(id) => setTargetUserId(id)}
                options={users.map((user) => ({
                  value: user.userId,
                  disabled: user.dingtalkBound !== 1,
                  label: `${user.nickname || user.username}${user.username ? `（${user.username}）` : ''}${
                    user.dingtalkBound === 1 ? '' : ' · 未绑定钉钉'
                  }`,
                }))}
              />
              <div className='flex items-center gap-2'>
                <span className='shrink-0 text-xs text-neutral-500'>期望</span>
                <InputNumber
                  className='w-full'
                  min={15}
                  max={240}
                  step={15}
                  value={durationMin}
                  addonAfter='分钟'
                  onChange={(v) => setDurationMin(typeof v === 'number' ? v : 60)}
                />
              </div>
              <DatePicker.RangePicker
                className='w-full md:col-span-2'
                format='YYYY-MM-DD'
                value={range}
                disabledDate={disabledBookingDate}
                onChange={(value) => {
                  if (value?.[0] && value?.[1]) setRange(toWorkRange(value[0], value[1]));
                }}
              />
            </div>
            <div className='flex justify-end gap-2'>
              <Button onClick={onClose}>关闭</Button>
              <Button
                type='primary'
                loading={querying}
                onClick={() => void handleAsk()}
              >
                发送
              </Button>
            </div>
          </div>
        </div>
      </Modal>

      <InviteFormModal
        open={inviteOpen}
        seed={inviteSeed}
        onOpenChange={(next) => {
          setInviteOpen(next);
          if (!next) setInviteSeed(null);
        }}
      />

      <Modal
        title={actionTarget?.targetNickname ? `邀请「${actionTarget.targetNickname}」开会` : '邀请开会'}
        open={meetingOpen}
        width={520}
        maskClosable={false}
        destroyOnHidden
        confirmLoading={actionSaving}
        okText='创建会议日程'
        cancelText='取消'
        onOk={() => void submitMeeting()}
        onCancel={() => {
          if (actionSaving) return;
          setMeetingOpen(false);
          setActionTarget(null);
          meetingForm.resetFields();
        }}
      >
        <Form
          form={meetingForm}
          layout='vertical'
          className='pt-2'
        >
          <Form.Item
            name='title'
            label='主题'
            rules={[{ required: true, message: '请填写会议主题' }]}
          >
            <Input placeholder='与钉钉日程「主题」一致' />
          </Form.Item>
          <Form.Item
            name='startTime'
            label='开始时间'
            rules={[{ required: true, message: '请选择开始时间' }]}
          >
            <DatePicker
              showTime={{ minuteStep: 15, format: 'HH:mm', showSecond: false }}
              className='w-full'
              format='YYYY-MM-DD HH:mm'
              disabledDate={disabledBookingDate}
            />
          </Form.Item>
          <Form.Item
            name='durationMin'
            label='时长（分钟）'
            rules={[{ required: true, message: '请填写时长' }]}
          >
            <InputNumber
              className='w-full'
              min={15}
              max={240}
              step={15}
            />
          </Form.Item>
          <Form.Item
            name='location'
            label='地点'
          >
            <Input placeholder='会议室 / 线上地址' />
          </Form.Item>
          <Form.Item
            name='description'
            label='描述'
          >
            <Input.TextArea
              rows={2}
              placeholder='会议说明'
            />
          </Form.Item>
          <Form.Item
            name='onlineMeeting'
            label='钉钉视频会议'
            valuePropName='checked'
          >
            <Switch
              checkedChildren='开'
              unCheckedChildren='关'
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={actionTarget?.targetNickname ? `与「${actionTarget.targetNickname}」汇报工作` : '汇报工作'}
        open={reportOpen}
        width={520}
        maskClosable={false}
        destroyOnHidden
        confirmLoading={actionSaving}
        okText='确认安排'
        cancelText='取消'
        onOk={() => void submitReport()}
        onCancel={() => {
          if (actionSaving) return;
          setReportOpen(false);
          setActionTarget(null);
          reportForm.resetFields();
        }}
      >
        <Form
          form={reportForm}
          layout='vertical'
          className='pt-2'
        >
          <Form.Item
            name='title'
            label='主题'
          >
            <Input placeholder='工作汇报标题' />
          </Form.Item>
          <Form.Item
            name='startTime'
            label='开始时间'
            rules={[{ required: true, message: '请选择开始时间' }]}
          >
            <DatePicker
              showTime={{ minuteStep: 15, format: 'HH:mm', showSecond: false }}
              className='w-full'
              format='YYYY-MM-DD HH:mm'
              disabledDate={disabledBookingDate}
            />
          </Form.Item>
          <Form.Item
            name='durationMin'
            label='时长（分钟）'
            rules={[{ required: true, message: '请填写时长' }]}
          >
            <InputNumber
              className='w-full'
              min={15}
              max={240}
              step={15}
            />
          </Form.Item>
          <Form.Item
            name='location'
            label='地点'
          >
            <Input placeholder='可选' />
          </Form.Item>
          <Form.Item
            name='content'
            label='说明'
          >
            <Input.TextArea
              rows={2}
              placeholder='汇报要点'
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default DingTalkAssistantModal;
