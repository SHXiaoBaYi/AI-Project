import { memo, useEffect, useMemo, useRef, useState } from 'react';
import { App, Avatar, Button, DatePicker, Form, Input, InputNumber, Mentions, Modal, Radio, Switch, Tabs } from 'antd';
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
  bookingPastDisabledTime,
  bookingRangeError,
  bookingWindow,
  disabledBookingDate,
  isChinaHoliday,
} from '@/utils/chinaHoliday';

const ASSISTANT_NAME = '日程助手';

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
  activeDay?: string;
  selectedKey?: string;
  context?: MessageContext;
  time: string;
};

type ActionTarget = {
  targetUserId: number;
  targetNickname: string;
};

type ParsedAsk = {
  user: DingTalkBusyUserOption;
  /** 仅当用户口头点名时长时才有 */
  durationMin?: number;
  /** 仅当用户口头点名日期时才有 */
  range?: [Dayjs, Dayjs];
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

/** 只传日期范围；工作时段由后端按被问询人日程规则决定 */
function toAskDateRange(from: Dayjs, to: Dayjs): [Dayjs, Dayjs] {
  const { min, max } = bookingWindow();
  let start = from.startOf('day');
  let end = to.startOf('day');
  if (start.isBefore(min, 'day')) start = min.startOf('day');
  if (end.isAfter(max, 'day')) end = max.startOf('day');
  if (end.isBefore(start, 'day')) end = start;
  return [start.hour(0).minute(0).second(0), end.hour(23).minute(59).second(0)];
}

function workHoursLabel(suggest?: DingTalkAssistantSuggest | null) {
  const ws = suggest?.workStart || '09:30';
  const we = suggest?.workEnd || '18:30';
  return `每天 ${ws}～${we}`;
}

function parseDurationMin(text: string): number | undefined {
  if (/半\s*小时/.test(text)) return 30;
  if (/一个半\s*小时|1\.5\s*小时/.test(text)) return 90;
  if (/两\s*小时|2\s*小时/.test(text)) return 120;
  if (/一\s*小时|1\s*小时/.test(text)) return 60;
  const minHit = text.match(/(\d+)\s*分钟/);
  if (minHit) {
    const n = Number(minHit[1]);
    if (Number.isFinite(n) && n >= 15 && n <= 240) return n;
  }
  const hourHit = text.match(/(\d+(?:\.\d+)?)\s*小时/);
  if (hourHit) {
    const n = Math.round(Number(hourHit[1]) * 60);
    if (Number.isFinite(n) && n >= 15 && n <= 240) return n;
  }
  return undefined;
}

function parseAskRange(text: string): [Dayjs, Dayjs] | undefined {
  const { min, max } = bookingWindow();
  const today = min.startOf('day');

  if (/今天/.test(text)) return toAskDateRange(today, today);
  if (/明天/.test(text)) {
    const d = today.add(1, 'day');
    return toAskDateRange(d, d);
  }
  if (/后天/.test(text)) {
    const d = today.add(2, 'day');
    return toAskDateRange(d, d);
  }
  if (/下周/.test(text)) {
    const start = today.add(1, 'week').startOf('week').add(1, 'day'); // 下周一
    const end = start.add(4, 'day');
    return toAskDateRange(start, end);
  }
  if (/本周|这周/.test(text)) {
    const end = today.add(4, 'day');
    return toAskDateRange(today, end.isAfter(max, 'day') ? max : end);
  }

  const nearHit = text.match(/(?:近|未来|接下来)\s*(\d+)\s*天/);
  if (nearHit) {
    const days = Math.min(Math.max(Number(nearHit[1]) || 5, 1), BOOKING_MAX_DAYS);
    return toAskDateRange(today, today.add(days - 1, 'day'));
  }

  const rangeHit = text.match(/(\d{1,2})[./-](\d{1,2})\s*[~～\-到至]\s*(\d{1,2})[./-](\d{1,2})/);
  if (rangeHit) {
    const y = today.year();
    let start = dayjs(`${y}-${rangeHit[1].padStart(2, '0')}-${rangeHit[2].padStart(2, '0')}`);
    let end = dayjs(`${y}-${rangeHit[3].padStart(2, '0')}-${rangeHit[4].padStart(2, '0')}`);
    if (!start.isValid() || !end.isValid()) return undefined;
    if (start.isBefore(today, 'day')) start = start.add(1, 'year');
    if (end.isBefore(start, 'day')) end = end.add(1, 'year');
    return toAskDateRange(start, end);
  }

  const singleHit = text.match(/(?:在|到)?\s*(\d{1,2})[./-](\d{1,2})(?:\s*这?\s*天)?/);
  if (singleHit && !/分钟|小时/.test(text.slice(Math.max(0, (singleHit.index ?? 0) - 2), (singleHit.index ?? 0) + 8))) {
    const y = today.year();
    let d = dayjs(`${y}-${singleHit[1].padStart(2, '0')}-${singleHit[2].padStart(2, '0')}`);
    if (!d.isValid()) return undefined;
    if (d.isBefore(today, 'day')) d = d.add(1, 'year');
    return toAskDateRange(d, d);
  }

  return undefined;
}

function matchUserByToken(token: string, users: DingTalkBusyUserOption[]): DingTalkBusyUserOption | null {
  const t = token.trim().replace(/[的地得]$/, '');
  if (!t) return null;
  const exact = users.find((u) => u.nickname === t || u.username === t);
  if (exact) return exact;
  const starts = users.filter(
    (u) =>
      (u.nickname && (u.nickname.startsWith(t) || t.startsWith(u.nickname))) ||
      (u.username && u.username.startsWith(t)),
  );
  if (starts.length === 1) return starts[0];
  const includes = users.filter(
    (u) => (u.nickname && u.nickname.includes(t)) || (u.username && u.username.includes(t)),
  );
  if (includes.length === 1) return includes[0];
  return null;
}

function resolveMentionedUser(text: string, users: DingTalkBusyUserOption[]): DingTalkBusyUserOption | null {
  const mentions = [...text.matchAll(/@([^\s@，,。！!？?\n]+)/g)];
  for (let i = mentions.length - 1; i >= 0; i -= 1) {
    const found = matchUserByToken(mentions[i][1], users);
    if (found) return found;
  }
  const quoted = text.match(/「([^」]+)」/);
  if (quoted) {
    const found = matchUserByToken(quoted[1], users);
    if (found) return found;
  }
  return null;
}

function parseAsk(text: string, users: DingTalkBusyUserOption[]): ParsedAsk | { error: string } {
  const user = resolveMentionedUser(text, users);
  if (!user) {
    return { error: '请先 @同事，例如：@张三 这周有没有空可以面试一个品牌总监？' };
  }
  if (user.dingtalkBound !== 1) {
    return { error: `「${user.nickname || user.username}」还未绑定钉钉，暂时查不了闲忙` };
  }
  const durationMin = parseDurationMin(text);
  const range = parseAskRange(text);
  if (range) {
    const rangeErr = bookingRangeError(range[0], range[1]);
    if (rangeErr) return { error: rangeErr };
  }
  return { user, durationMin, range };
}

/** 去掉法定节假日天、假日上的推荐时段，以及已经过去的时段 */
function filterSuggestHolidays(data: DingTalkAssistantSuggest): DingTalkAssistantSuggest {
  const now = dayjs();
  const dayGroups = (data.dayGroups ?? [])
    .filter((day) => !isChinaHoliday(day.day))
    .map((day) => ({
      ...day,
      slots: (day.slots ?? []).filter(
        (slot) => !isChinaHoliday(slot.start) && !isChinaHoliday(slot.end) && dayjs(slot.start).isAfter(now),
      ),
    }));
  return { ...data, dayGroups };
}

function initialOf(name: string) {
  const t = name.trim();
  return t ? t.slice(0, 1) : '?';
}

function DingTalkAssistantModal({ open, onClose, onOpenBusy }: Props) {
  const { message } = App.useApp();
  const { has } = usePermission();
  const userInfo = useSelector((state: RootState) => state.user.userInfo);
  const meName = userInfo?.nickname || userInfo?.username || '我';
  const meAvatar = userInfo?.avatar;
  const listRef = useRef<HTMLDivElement>(null);
  const [users, setUsers] = useState<DingTalkBusyUserOption[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [querying, setQuerying] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteSeed, setInviteSeed] = useState<InviteFormValues | null>(null);
  const [actionSaving, setActionSaving] = useState(false);
  const [meetingOpen, setMeetingOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [actionTarget, setActionTarget] = useState<ActionTarget | null>(null);
  const [meetingForm] = Form.useForm();
  const [reportForm] = Form.useForm();

  const mentionOptions = useMemo(
    () =>
      users.map((user) => ({
        key: String(user.userId),
        value: user.nickname || user.username,
        label: `${user.nickname || user.username}${user.username ? `（${user.username}）` : ''}${
          user.dingtalkBound === 1 ? '' : ' · 未绑定钉钉'
        }`,
        disabled: user.dingtalkBound !== 1,
      })),
    [users],
  );

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
          text:
            '你好，我是日程助手。\n' +
            '像钉钉聊天一样直接说就行，例如：\n' +
            '· @张三 这周有没有 60 分钟空闲？\n' +
            '· @李四 明天帮我看看半小时空档\n' +
            '· @王五 下周一到周五有没有一小时能约？\n' +
            '发给我后，我会结合对方日程规则和钉钉闲忙，直接回复可约时段和下一步建议。',
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

  const push = (msg: Omit<ChatMessage, 'id' | 'time'> & { time?: string }) => {
    const next: ChatMessage = { ...msg, id: msgId(), time: msg.time || dayjs().format('HH:mm') };
    setMessages((prev) => [...prev, next]);
    return next.id;
  };

  const handleAsk = async () => {
    const trimmed = draft.trim();
    if (!trimmed) {
      message.warning('先说一句吧，记得 @同事');
      return;
    }
    const parsed = parseAsk(trimmed, users);
    if ('error' in parsed) {
      message.warning(parsed.error);
      return;
    }
    const { user, durationMin, range } = parsed;
    const nickname = user.nickname || user.username;

    push({ role: 'user', text: trimmed });
    setDraft('');
    setQuerying(true);
    try {
      const payload: {
        targetUserId: number;
        message: string;
        startTime?: string;
        endTime?: string;
        durationMin?: number;
      } = {
        targetUserId: user.userId,
        message: trimmed,
      };
      if (durationMin != null) payload.durationMin = durationMin;
      // 相对日期（今天/明天等）只传原话，由后端按服务器日历解析，避免前后端日期被 AI/时区盖成过去
      const relativeDay = /今天|明天|后天|本周|这周|下周|(?:近|未来|接下来)\s*\d+\s*天/.test(trimmed);
      if (!relativeDay && range?.[0] && range?.[1]) {
        const [askStart, askEnd] = toAskDateRange(range[0], range[1]);
        payload.startTime = askStart.format('YYYY-MM-DD HH:mm:ss');
        payload.endTime = askEnd.format('YYYY-MM-DD HH:mm:ss');
      }
      const data = filterSuggestHolidays(await suggestDingTalkAssistantApi(payload));
      const displayName = data.targetNickname || nickname;
      const firstDay = data.dayGroups?.find((d) => (d.slots?.length ?? 0) > 0) ?? data.dayGroups?.[0];
      const firstSlot = firstDay?.slots?.[0];
      const replyText = [data.ruleSummary, data.busySummary, data.adviceText, data.nextStepText]
        .filter(Boolean)
        .join('\n\n');
      push({
        role: 'assistant',
        text: replyText || '暂无建议',
        suggest: data,
        activeDay: firstDay?.day,
        selectedKey: firstSlot ? `${firstSlot.start}|${firstSlot.end}` : undefined,
        context: {
          targetUserId: user.userId,
          targetNickname: displayName,
          durationMin: data.durationMin || durationMin || 60,
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
    const nickname = msg.context?.targetNickname || '';
    const userId = msg.context?.targetUserId;
    if (!userId) {
      message.warning('这条消息里没有同事信息，请重新 @ 询问');
      return;
    }
    const minutes = msg.context?.durationMin || 60;
    meetingForm.setFieldsValue({
      title: nickname ? `与${nickname}的会议` : '会议',
      startTime: start ? dayjs(start) : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: minutes,
      location: '',
      description: '',
      onlineMeeting: true,
    });
    setActionTarget({ targetUserId: userId, targetNickname: nickname });
    setMeetingOpen(true);
  };

  const openReportModal = (msg: ChatMessage) => {
    const slot = findSlot(msg.suggest, msg.selectedKey);
    const start = slotStart(slot);
    const nickname = msg.context?.targetNickname || '';
    const userId = msg.context?.targetUserId;
    if (!userId) {
      message.warning('这条消息里没有同事信息，请重新 @ 询问');
      return;
    }
    const minutes = msg.context?.durationMin || 60;
    reportForm.setFieldsValue({
      title: nickname ? `工作汇报 · ${nickname}` : '工作汇报',
      startTime: start ? dayjs(start) : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: minutes,
      location: '',
      content: '',
    });
    setActionTarget({ targetUserId: userId, targetNickname: nickname });
    setReportOpen(true);
  };

  const runAction = (msg: ChatMessage, action: DingTalkAssistantAction) => {
    const payload = action.payload ?? {};
    const ctx = msg.context;
    const userId = Number(payload.targetUserId ?? ctx?.targetUserId);
    const minutes = Number(payload.durationMin ?? ctx?.durationMin) || 60;
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
      message.warning('缺少同事信息');
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
      message.warning('缺少同事信息');
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
        title='日程助手'
        open={open}
        width={960}
        footer={null}
        maskClosable={false}
        destroyOnHidden
        onCancel={() => {
          if (querying || actionSaving) return;
          onClose();
        }}
        styles={{ body: { padding: 0 } }}
      >
        <div className='flex h-[72vh] flex-col'>
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
                          ? 'rounded-tr-sm bg-[#95ec69] text-neutral-800'
                          : 'rounded-tl-sm border border-neutral-100 bg-white text-neutral-800'
                      }`}
                    >
                      {msg.text ? <div className='whitespace-pre-wrap'>{msg.text}</div> : null}
                      {msg.suggest?.dayGroups?.length ? (
                        <div className='mt-2'>
                          <div className='mb-1 text-xs text-neutral-500'>
                            推荐时段（{workHoursLabel(msg.suggest)}，每段{' '}
                            {msg.suggest.durationMin || msg.context?.durationMin || 60} 分钟）
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
                        <div className='mt-2'>
                          <div className='mb-1 text-xs font-medium text-neutral-500'>建议的下一个动作</div>
                          <div className='flex flex-wrap gap-1.5'>
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
                        </div>
                      ) : null}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className='shrink-0 border-t border-neutral-200 bg-[#f7f7f7] px-3 py-2'>
            <div className='rounded-lg border border-neutral-200 bg-white focus-within:border-[#1f7aef]'>
              <Mentions
                value={draft}
                onChange={setDraft}
                prefix='@'
                options={mentionOptions}
                placeholder={
                  loadingUsers ? '正在加载同事…' : '输入 @ 选同事，再说时间，例如：@张三 这周有没有 60 分钟空闲？'
                }
                autoSize={{ minRows: messages.length <= 1 ? 5 : 3, maxRows: 8 }}
                className='!border-0 !shadow-none'
                disabled={querying}
                filterOption={(input, option) => {
                  const q = (input || '').toLowerCase();
                  const label = String(option?.label ?? option?.value ?? '').toLowerCase();
                  return !q || label.includes(q);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                    e.preventDefault();
                    void handleAsk();
                  }
                }}
              />
              <div className='flex items-center justify-between gap-2 border-t border-neutral-100 px-3 py-1.5'>
                <span className='text-xs text-neutral-400'>Ctrl+Enter 发送 · 最多未来 {BOOKING_MAX_DAYS} 天</span>
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
        </div>
      </Modal>

      <InviteFormModal
        open={inviteOpen}
        seed={inviteSeed}
        createDingTalkCalendar
        sendDingTalkWorkNotice={false}
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
            extra={`不可选过去、法定节假日，最多未来 ${BOOKING_MAX_DAYS} 天`}
            rules={[
              { required: true, message: '请选择开始时间' },
              {
                validator: async (_, value) => {
                  const err = bookingDateTimeError(value);
                  if (err) return Promise.reject(new Error(err));
                  return Promise.resolve();
                },
              },
            ]}
          >
            <DatePicker
              showTime={{ minuteStep: 15, format: 'HH:mm', showSecond: false, hideDisabledOptions: true }}
              className='w-full'
              format='YYYY-MM-DD HH:mm'
              disabledDate={disabledBookingDate}
              disabledTime={(date) => bookingPastDisabledTime(date)}
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
            extra={`不可选过去、法定节假日，最多未来 ${BOOKING_MAX_DAYS} 天`}
            rules={[
              { required: true, message: '请选择开始时间' },
              {
                validator: async (_, value) => {
                  const err = bookingDateTimeError(value);
                  if (err) return Promise.reject(new Error(err));
                  return Promise.resolve();
                },
              },
            ]}
          >
            <DatePicker
              showTime={{ minuteStep: 15, format: 'HH:mm', showSecond: false, hideDisabledOptions: true }}
              className='w-full'
              format='YYYY-MM-DD HH:mm'
              disabledDate={disabledBookingDate}
              disabledTime={(date) => bookingPastDisabledTime(date)}
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
}

export default memo(DingTalkAssistantModal);
