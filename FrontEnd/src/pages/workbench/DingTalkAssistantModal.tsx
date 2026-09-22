import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Radio, Select, Space, Switch, Tabs } from 'antd';
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

type Props = {
  open: boolean;
  onClose: () => void;
  onOpenBusy?: (userIds: number[]) => void;
};

type ChatRole = 'user' | 'assistant' | 'system';

type ChatMessage = {
  id: string;
  role: ChatRole;
  text?: string;
  suggest?: DingTalkAssistantSuggest;
  card?: 'meeting' | 'report';
  time: string;
};

function msgId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

const DingTalkAssistantModal = memo(function DingTalkAssistantModal({ open, onClose, onOpenBusy }: Props) {
  const { message } = App.useApp();
  const { has } = usePermission();
  const listRef = useRef<HTMLDivElement>(null);
  const [users, setUsers] = useState<DingTalkBusyUserOption[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [targetUserId, setTargetUserId] = useState<number>();
  const [durationMin, setDurationMin] = useState(60);
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().hour(9).minute(0).second(0),
    dayjs().add(4, 'day').hour(18).minute(0).second(0),
  ]);
  const [querying, setQuerying] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [selectedKey, setSelectedKey] = useState<string>();
  const [activeDay, setActiveDay] = useState<string>();
  const [lastSuggest, setLastSuggest] = useState<DingTalkAssistantSuggest | null>(null);
  const [activeCard, setActiveCard] = useState<'meeting' | 'report' | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteSeed, setInviteSeed] = useState<InviteFormValues | null>(null);
  const [actionSaving, setActionSaving] = useState(false);
  const [meetingForm] = Form.useForm();
  const [reportForm] = Form.useForm();

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
          text: '你好，我是日程助手。选一位同事和一段时间，我可以根据钉钉闲忙给出建议，并帮你发起面试邀约、邀请开会或安排工作汇报。',
          time: dayjs().format('HH:mm'),
        },
      ]);
    }
  }, [open, messages.length]);

  useEffect(() => {
    if (!open) return;
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages, activeCard, open]);

  const dayGroups = useMemo(() => lastSuggest?.dayGroups ?? [], [lastSuggest]);

  const selectedSlot = useMemo(() => {
    for (const day of dayGroups) {
      for (const slot of day.slots ?? []) {
        if (`${slot.start}|${slot.end}` === selectedKey) return slot;
      }
    }
    return dayGroups[0]?.slots?.[0] ?? null;
  }, [dayGroups, selectedKey]);

  const targetLabel = useMemo(() => {
    const user = users.find((u) => u.userId === targetUserId);
    return user ? user.nickname || user.username : '';
  }, [users, targetUserId]);

  const slotStart = (slot: DingTalkAssistantSlot | null | undefined) =>
    slot ? dayjs(slot.start).format('YYYY-MM-DD HH:mm:ss') : undefined;

  const push = (msg: Omit<ChatMessage, 'id' | 'time'> & { time?: string }) => {
    setMessages((prev) => [...prev, { ...msg, id: msgId(), time: msg.time || dayjs().format('HH:mm') }]);
  };

  const handleAsk = async () => {
    if (!targetUserId) {
      message.warning('请选择同事');
      return;
    }
    if (!range?.[0] || !range?.[1] || !range[0].isBefore(range[1])) {
      message.warning('请选择有效的时间范围');
      return;
    }
    const userText = `查一下「${targetLabel}」在 ${range[0].format('MM-DD HH:mm')} ~ ${range[1].format('MM-DD HH:mm')} 是否有连续 ${durationMin} 分钟空闲？`;
    push({ role: 'user', text: userText });
    setQuerying(true);
    setActiveCard(null);
    try {
      const data = await suggestDingTalkAssistantApi({
        targetUserId,
        startTime: range[0].format('YYYY-MM-DD HH:mm:ss'),
        endTime: range[1].format('YYYY-MM-DD HH:mm:ss'),
        durationMin,
      });
      setLastSuggest(data);
      const firstDay = data.dayGroups?.[0];
      const firstSlot = firstDay?.slots?.[0];
      setActiveDay(firstDay?.day);
      setSelectedKey(firstSlot ? `${firstSlot.start}|${firstSlot.end}` : undefined);
      push({ role: 'assistant', text: data.adviceText || '暂无建议', suggest: data });
    } catch (err) {
      push({
        role: 'assistant',
        text: err instanceof Error ? err.message : '查询失败，请稍后重试',
      });
    } finally {
      setQuerying(false);
    }
  };

  const openMeetingCard = () => {
    const start = slotStart(selectedSlot);
    meetingForm.setFieldsValue({
      title: targetLabel ? `与${targetLabel}的会议` : '会议',
      startTime: start ? dayjs(start) : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: durationMin || 60,
      location: '',
      description: '',
      onlineMeeting: true,
    });
    setActiveCard('meeting');
    push({ role: 'system', text: '请填写会议信息（字段对齐钉钉日程）', card: 'meeting' });
  };

  const openReportCard = () => {
    const start = slotStart(selectedSlot);
    reportForm.setFieldsValue({
      title: targetLabel ? `工作汇报 · ${targetLabel}` : '工作汇报',
      startTime: start ? dayjs(start) : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: durationMin || 60,
      location: '',
      content: '',
    });
    setActiveCard('report');
    push({ role: 'system', text: '确认汇报安排后，将创建任务、双方钉钉日程，并通知你', card: 'report' });
  };

  const runAction = (action: DingTalkAssistantAction) => {
    const payload = action.payload ?? {};
    const userId = Number(payload.targetUserId ?? targetUserId);
    const minutes = Number(payload.durationMin ?? durationMin) || 60;
    const suggestedStart = selectedSlot
      ? slotStart(selectedSlot)
      : payload.suggestedStart
        ? String(payload.suggestedStart)
        : undefined;

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
      openMeetingCard();
      return;
    }
    if (action.type === 'CREATE_REPORT_TASK') {
      if (!has('task:add') && !has('*:*:*')) {
        message.warning('没有创建任务的权限');
        return;
      }
      openReportCard();
    }
  };

  const submitMeeting = async () => {
    if (!targetUserId) {
      message.warning('请先选择同事');
      return;
    }
    const values = await meetingForm.validateFields();
    setActionSaving(true);
    try {
      const result = await createDingTalkAssistantMeetingApi({
        targetUserId,
        title: values.title,
        startTime: dayjs(values.startTime).format('YYYY-MM-DD HH:mm:ss'),
        durationMin: values.durationMin,
        location: values.location,
        description: values.description,
        onlineMeeting: !!values.onlineMeeting,
      });
      setActiveCard(null);
      push({ role: 'user', text: `邀请「${targetLabel}」参加会议：${values.title}` });
      push({ role: 'assistant', text: result.message || '会议已创建' });
      message.success(result.message || '会议已创建');
    } finally {
      setActionSaving(false);
    }
  };

  const submitReport = async () => {
    if (!targetUserId) {
      message.warning('请先选择同事');
      return;
    }
    const values = await reportForm.validateFields();
    setActionSaving(true);
    try {
      const result = await createDingTalkAssistantReportApi({
        targetUserId,
        title: values.title,
        content: values.content,
        location: values.location,
        startTime: dayjs(values.startTime).format('YYYY-MM-DD HH:mm:ss'),
        durationMin: values.durationMin,
      });
      setActiveCard(null);
      push({ role: 'user', text: `安排与「${targetLabel}」的工作汇报` });
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
        width={780}
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
            className='min-h-0 flex-1 space-y-3 overflow-y-auto bg-neutral-50 px-4 py-3'
          >
            {messages.map((msg) => {
              const isUser = msg.role === 'user';
              return (
                <div
                  key={msg.id}
                  className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-[88%] rounded-2xl px-3 py-2 text-sm shadow-sm ${
                      isUser
                        ? 'rounded-br-md bg-blue-600 text-white'
                        : msg.role === 'system'
                          ? 'rounded-bl-md border border-amber-200 bg-amber-50 text-neutral-800'
                          : 'rounded-bl-md border border-neutral-200 bg-white text-neutral-800'
                    }`}
                  >
                    {msg.text ? <div className='whitespace-pre-wrap'>{msg.text}</div> : null}
                    {msg.suggest?.dayGroups?.length ? (
                      <div className='mt-2'>
                        <div className='mb-1 text-xs text-neutral-500'>
                          推荐时段（按天浏览，每段 {msg.suggest.durationMin || durationMin} 分钟）
                        </div>
                        <Tabs
                          size='small'
                          activeKey={activeDay || msg.suggest.dayGroups[0]?.day}
                          onChange={(key) => {
                            setActiveDay(key);
                            const day = msg.suggest?.dayGroups?.find((d) => d.day === key);
                            const first = day?.slots?.[0];
                            if (first) setSelectedKey(`${first.start}|${first.end}`);
                          }}
                          items={msg.suggest.dayGroups.map((day: DingTalkAssistantDayGroup) => ({
                            key: day.day,
                            label: `${day.dayLabel}（${day.slots?.length || 0}）`,
                            children: (
                              <Radio.Group
                                className='flex max-h-40 w-full flex-col gap-1 overflow-y-auto'
                                value={selectedKey}
                                onChange={(e) => setSelectedKey(e.target.value)}
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
                            key={action.type}
                            size='small'
                            type={
                              action.type === 'CREATE_MEETING' || action.type === 'CREATE_INVITE'
                                ? 'primary'
                                : 'default'
                            }
                            title={action.hint}
                            onClick={() => runAction(action)}
                          >
                            {action.label}
                          </Button>
                        ))}
                      </div>
                    ) : null}
                    {msg.card === 'meeting' && activeCard === 'meeting' ? (
                      <Form
                        form={meetingForm}
                        layout='vertical'
                        className='mt-2'
                        size='small'
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
                            showTime={{ minuteStep: 15, format: 'HH:mm' }}
                            className='w-full'
                            format='YYYY-MM-DD HH:mm'
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
                        <Space>
                          <Button
                            type='primary'
                            loading={actionSaving}
                            onClick={() => void submitMeeting()}
                          >
                            创建会议日程
                          </Button>
                          <Button
                            onClick={() => setActiveCard(null)}
                            disabled={actionSaving}
                          >
                            取消
                          </Button>
                        </Space>
                      </Form>
                    ) : null}
                    {msg.card === 'report' && activeCard === 'report' ? (
                      <Form
                        form={reportForm}
                        layout='vertical'
                        className='mt-2'
                        size='small'
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
                            showTime={{ minuteStep: 15, format: 'HH:mm' }}
                            className='w-full'
                            format='YYYY-MM-DD HH:mm'
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
                        <Space>
                          <Button
                            type='primary'
                            loading={actionSaving}
                            onClick={() => void submitReport()}
                          >
                            确认安排
                          </Button>
                          <Button
                            onClick={() => setActiveCard(null)}
                            disabled={actionSaving}
                          >
                            取消
                          </Button>
                        </Space>
                      </Form>
                    ) : null}
                    <div className={`mt-1 text-[10px] ${isUser ? 'text-white/70' : 'text-neutral-400'}`}>
                      {msg.time}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className='shrink-0 border-t border-neutral-200 bg-white px-4 py-3'>
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
                showTime={{ minuteStep: 30, format: 'HH:mm' }}
                className='w-full md:col-span-2'
                format='YYYY-MM-DD HH:mm'
                value={range}
                onChange={(value) => {
                  if (value?.[0] && value?.[1]) setRange([value[0], value[1]]);
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
    </>
  );
});

export default DingTalkAssistantModal;
