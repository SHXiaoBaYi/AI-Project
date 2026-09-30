import request from './request';

export type DingTalkAppVO = {
  appId: string;
  agentId: string;
  clientId: string;
  corpId?: string;
  clientSecretMasked?: string;
  hasClientSecret?: boolean;
  enabled: number;
  /** local=本机 online=线上；wangfangyang/thh/tbb 可改 */
  robotRoute?: 'local' | 'online' | string;
  canEditRobotRoute?: boolean;
};

export type DingTalkAppDTO = {
  appId: string;
  agentId: string;
  clientId: string;
  corpId?: string;
  clientSecret?: string;
  enabled: number;
  robotRoute?: 'local' | 'online' | string;
};

export function getDingTalkAppApi() {
  return request.get<unknown, DingTalkAppVO>('/system/dingtalk');
}

export function saveDingTalkAppApi(data: DingTalkAppDTO) {
  return request.put('/system/dingtalk', data);
}

export function testDingTalkAppApi(data: DingTalkAppDTO) {
  return request.post('/system/dingtalk/test', data);
}

export type DingTalkBusyUserOption = {
  userId: number;
  username: string;
  nickname: string;
  dingtalkBound?: number;
};

export type DingTalkBusySlot = {
  status: string;
  statusLabel: string;
  start: string;
  end: string;
};

export type DingTalkBusyUser = {
  userId: number;
  username: string;
  nickname: string;
  error?: string;
  slots: DingTalkBusySlot[];
};

export function getDingTalkBusyUsersApi() {
  return request.get<unknown, DingTalkBusyUserOption[]>('/system/dingtalk/busy/users');
}

export function queryDingTalkBusyApi(data: { userIds: number[]; startTime: string; endTime: string }) {
  return request.post<unknown, DingTalkBusyUser[]>('/system/dingtalk/busy/query', data);
}

export type DingTalkAssistantFreeWindow = {
  start: string;
  end: string;
  availableMin: number;
  label: string;
};

export type DingTalkAssistantSlot = {
  start: string;
  end: string;
  label: string;
};

export type DingTalkAssistantDayGroup = {
  day: string;
  dayLabel: string;
  slots: DingTalkAssistantSlot[];
};

export type DingTalkAssistantAction = {
  type: string;
  label: string;
  hint?: string;
  payload?: Record<string, unknown>;
};

export type DingTalkAssistantSuggest = {
  targetUserId: number;
  targetNickname?: string;
  targetUsername?: string;
  durationMin: number;
  /** 被问询人日程规则中的工作/午休时段 */
  workStart?: string;
  workEnd?: string;
  lunchStart?: string;
  lunchEnd?: string;
  bufferMin?: number;
  error?: string;
  adviceText?: string;
  /** 日程规则摘要 */
  ruleSummary?: string;
  /** 钉钉闲忙摘要 */
  busySummary?: string;
  /** 建议的下一个动作 */
  nextStepText?: string;
  intentAction?: string;
  jobName?: string;
  jobMatchNote?: string;
  dayGroups?: DingTalkAssistantDayGroup[];
  freeWindows: DingTalkAssistantFreeWindow[];
  actions: DingTalkAssistantAction[];
};

export function suggestDingTalkAssistantApi(data: {
  targetUserId: number;
  message?: string;
  startTime?: string;
  endTime?: string;
  durationMin?: number;
  action?: string;
  jobName?: string;
}) {
  return request.post<unknown, DingTalkAssistantSuggest>('/system/dingtalk/assistant/suggest', data);
}

export type DingTalkAssistantActionResult = {
  success: boolean;
  message?: string;
  querierEventId?: string;
  targetEventId?: string;
  taskId?: number;
  noticeSent?: boolean;
};

export function createDingTalkAssistantMeetingApi(data: {
  targetUserId: number;
  title: string;
  startTime: string;
  durationMin?: number;
  location?: string;
  description?: string;
  onlineMeeting?: boolean;
}) {
  return request.post<unknown, DingTalkAssistantActionResult>('/system/dingtalk/assistant/meeting', data);
}

export function createDingTalkAssistantReportApi(data: {
  targetUserId: number;
  startTime: string;
  durationMin?: number;
  title?: string;
  content?: string;
  location?: string;
}) {
  return request.post<unknown, DingTalkAssistantActionResult>('/system/dingtalk/assistant/report', data);
}

export type DingTalkAssistantSchedule = {
  id: number;
  kind: string;
  kindLabel?: string;
  title: string;
  description?: string;
  location?: string;
  onlineMeeting?: number;
  startTime: string;
  durationMin: number;
  endTime?: string;
  querierUserId?: number;
  querierNickname?: string;
  targetUserId?: number;
  targetNickname?: string;
  querierEventId?: string;
  targetEventId?: string;
  taskId?: number;
  status: string;
  statusLabel?: string;
  createTime?: string;
};

export function listDingTalkAssistantSchedulesApi(params?: { kind?: string; status?: string }) {
  return request.get<unknown, DingTalkAssistantSchedule[]>('/system/dingtalk/assistant/schedules', { params });
}

export function updateDingTalkAssistantScheduleApi(data: {
  id: number;
  title: string;
  startTime: string;
  durationMin?: number;
  location?: string;
  description?: string;
  onlineMeeting?: boolean;
}) {
  return request.put<unknown, DingTalkAssistantActionResult>('/system/dingtalk/assistant/schedules', data);
}

export function cancelDingTalkAssistantScheduleApi(id: number) {
  return request.delete<unknown, DingTalkAssistantActionResult>(`/system/dingtalk/assistant/schedules/${id}`);
}

export type DingTalkIntentExecuteResult = {
  status: 'done' | 'need_candidate' | string;
  message?: string;
  candidates?: Array<{ name: string; jobName?: string; ticket: string }>;
};

export function executeDingTalkIntentApi(ticket: string) {
  return request.post<unknown, DingTalkIntentExecuteResult>('/system/dingtalk/assistant/intent/execute', { ticket });
}
