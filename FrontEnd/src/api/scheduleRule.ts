import request from './request';

export type ScheduleRuleWindow = {
  weekdays: number[];
  startTime: string;
  endTime: string;
};

export type ScheduleActionPref = {
  action: string;
  actionLabel?: string;
  enabled: boolean;
  preferDurationMin: number;
  maxDurationMin?: number | null;
  windows: ScheduleRuleWindow[];
};

export type ScheduleInterviewFreq = {
  smallJobs?: string[];
  smallJobsDailyMin?: number;
  smallJobsDailyMax?: number;
  importantJobs?: string[];
  importantJobsDailyMax?: number;
};

/** 会议来源优先级码：顺序越靠前优先级越高 */
export type SchedulePriorityCode = 'SUPERVISOR' | 'CORE_PROJECT' | 'OTHER_BIZ' | 'CROSS_DEPT';

/** 偏好场景：外部汇报 / 内部会议等 */
export type SchedulePreferScene =
  'EXTERNAL_REPORT' | 'INTERNAL_MEETING' | 'EXTERNAL_MEETING' | 'INTERVIEW' | 'ONE_ON_ONE' | 'CUSTOM';

/** 偏好时段：上午 / 下午 / 自定义起止 */
export type SchedulePreferSlotKind = 'PERIOD' | 'RANGE';
export type SchedulePreferPeriod = 'MORNING' | 'AFTERNOON';

/** 结构化偏好：将「外部汇报」安排在 14:00~17:00；将「内部会议」安排在上午 */
export type SchedulePreferRule = {
  scene: SchedulePreferScene;
  /** scene=CUSTOM 时的自定义名称 */
  sceneLabel?: string;
  slotKind: SchedulePreferSlotKind;
  /** slotKind=PERIOD */
  period?: SchedulePreferPeriod;
  /** slotKind=RANGE */
  startTime?: string;
  endTime?: string;
};

export type ScheduleMeetingPriority = {
  /** 优先级顺序，默认：直接上级 > 核心项目部 > 其他业务部门 > 跨部门协作 */
  priorityOrder?: SchedulePriorityCode[];
  /** 直接上级：本地用户昵称/姓名，也可手填 */
  supervisorNames?: string[];
  coreProjectDeptIds?: number[];
  otherBizDeptIds?: number[];
  preferRules?: SchedulePreferRule[];
  interviewFreq?: ScheduleInterviewFreq;
};

export type ScheduleRule = {
  userId?: number;
  enabled: boolean;
  denyHolidays: boolean;
  bufferMin?: number;
  slotMin?: number;
  lookAheadDays?: number;
  recommendLimit?: number;
  /** 定数：永远 true，后端忽略关闭 */
  secretaryEnabled?: boolean;
  robotHint?: string;
  workStart?: string;
  workEnd?: string;
  morningStart?: string;
  morningEnd?: string;
  forenoonStart?: string;
  forenoonEnd?: string;
  lunchStart?: string;
  lunchEnd?: string;
  blockedWindows?: ScheduleRuleWindow[];
  deptMeetingWindows?: ScheduleRuleWindow[];
  meetingPriority?: ScheduleMeetingPriority;
  actionPrefs?: ScheduleActionPref[];
};

export type ScheduleRuleDTO = {
  enabled: boolean;
  denyHolidays: boolean;
  bufferMin?: number;
  slotMin?: number;
  lookAheadDays?: number;
  recommendLimit?: number;
  /** 定数：永远 true，后端忽略关闭 */
  secretaryEnabled?: boolean;
  robotHint?: string;
  workStart?: string;
  workEnd?: string;
  morningStart?: string;
  morningEnd?: string;
  forenoonStart?: string;
  forenoonEnd?: string;
  lunchStart?: string;
  lunchEnd?: string;
  blockedWindows: ScheduleRuleWindow[];
  deptMeetingWindows: ScheduleRuleWindow[];
  meetingPriority: ScheduleMeetingPriority;
  actionPrefs: ScheduleActionPref[];
};

export type ScheduleRuleOptions = {
  users: { value: number; label: string; username?: string }[];
  departments: { value: number; label: string }[];
  jobs: string[];
};

export function getMyScheduleRuleApi() {
  return request.get<unknown, ScheduleRule>('/system/schedule-rule/mine');
}

export function saveMyScheduleRuleApi(data: ScheduleRuleDTO) {
  return request.put<unknown, void>('/system/schedule-rule/mine', data);
}

export function getScheduleRuleOptionsApi() {
  return request.get<unknown, ScheduleRuleOptions>('/system/schedule-rule/options');
}
