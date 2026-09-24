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

export type ScheduleRule = {
  userId?: number;
  enabled: boolean;
  denyHolidays: boolean;
  bufferMin?: number;
  lookAheadDays?: number;
  recommendLimit?: number;
  secretaryEnabled?: boolean;
  robotHint?: string;
  blockedWindows?: ScheduleRuleWindow[];
  actionPrefs?: ScheduleActionPref[];
};

export type ScheduleRuleDTO = {
  enabled: boolean;
  denyHolidays: boolean;
  bufferMin?: number;
  lookAheadDays?: number;
  recommendLimit?: number;
  secretaryEnabled?: boolean;
  robotHint?: string;
  blockedWindows: ScheduleRuleWindow[];
  actionPrefs: ScheduleActionPref[];
};

export function getMyScheduleRuleApi() {
  return request.get<unknown, ScheduleRule>('/system/schedule-rule/mine');
}

export function saveMyScheduleRuleApi(data: ScheduleRuleDTO) {
  return request.put<unknown, void>('/system/schedule-rule/mine', data);
}
