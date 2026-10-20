import request from './request';
import type { PageResult } from '@/types/api';

export type RobotIntent = {
  id: number;
  intentCode: string;
  intentName: string;
  enabled: number;
  sampleUtterances?: string;
  handlerBean?: string;
  allowGroup?: number;
  allowDm?: number;
  sortOrder?: number;
  remark?: string;
};

export type LinkMonitor = {
  id: number;
  url: string;
  name?: string;
  remark?: string;
  scopeType?: string;
  source?: string;
  openConversationId?: string;
  conversationType?: string;
  groupTitle?: string;
  status: string;
  intervalSec?: number;
  lastCheckAt?: string;
  lastOk?: number;
  lastStatusCode?: number;
  lastError?: string;
  lastAlertAt?: string;
  createBy?: string;
  createTime?: string;
};

export type LinkMonitorFillContext = {
  sessionKey: string;
  state: string;
  draftUrls: string[];
  draftCount: number;
};

export type LinkMonitorFillSubmitResult = {
  message?: string;
  committed?: number;
  draftCount?: number;
  draftUrls?: string[];
  okUrls?: string[];
  fail?: string[];
  alertGroupHint?: string;
};

export function listRobotIntentsApi() {
  return request.get<unknown, RobotIntent[]>('/robot/intents');
}

export function updateRobotIntentApi(
  id: number,
  data: Partial<{
    intentName: string;
    enabled: number;
    sampleUtterances: string;
    allowGroup: number;
    allowDm: number;
    remark: string;
  }>,
) {
  return request.put(`/robot/intents/${id}`, data);
}

export function listRobotSessionsApi(params: { pageNum?: number; pageSize?: number; state?: string }) {
  return request.get<unknown, PageResult<Record<string, unknown>>>('/robot/sessions', { params });
}

export function endRobotSessionApi(id: number) {
  return request.post(`/robot/sessions/${id}/end`);
}

export function listRobotMessagesApi(params: { pageNum?: number; pageSize?: number; intentCode?: string }) {
  return request.get<unknown, PageResult<Record<string, unknown>>>('/robot/messages', { params });
}

export function listLinkMonitorsApi(params: {
  pageNum?: number;
  pageSize?: number;
  status?: string;
  keyword?: string;
}) {
  return request.get<unknown, PageResult<LinkMonitor>>('/robot/link-monitors', { params });
}

export function addLinkMonitorApi(data: { url: string; name?: string; remark?: string }) {
  return request.post<unknown, { id: number }>('/robot/link-monitors', data);
}

export function updateLinkMonitorApi(
  id: number,
  data: Partial<{ name: string; remark: string; intervalSec: number; timeoutMs: number; status: string }>,
) {
  return request.put(`/robot/link-monitors/${id}`, data);
}

export function deleteLinkMonitorApi(id: number) {
  return request.delete(`/robot/link-monitors/${id}`);
}

export function probeLinkMonitorApi(id: number) {
  return request.post(`/robot/link-monitors/${id}/probe`);
}

export function listLinkMonitorChecksApi(id: number) {
  return request.get<unknown, Record<string, unknown>[]>(`/robot/link-monitors/${id}/checks`);
}

export function listLinkMonitorAlertsApi(id: number) {
  return request.get<unknown, Record<string, unknown>[]>(`/robot/link-monitors/${id}/alerts`);
}

export function getLinkMonitorFillContextApi(ticket: string) {
  return request.get<unknown, LinkMonitorFillContext>('/robot/link-monitor/fill/context', {
    params: { ticket },
  });
}

export function submitLinkMonitorFillApi(data: {
  ticket: string;
  urlsText?: string;
  action: 'append' | 'commit' | 'cancel';
}) {
  return request.post<unknown, LinkMonitorFillSubmitResult>('/robot/link-monitor/fill/submit', data);
}
