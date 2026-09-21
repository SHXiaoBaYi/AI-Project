import request from '@/api/request';
import { getToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';

export interface HrBoardQuery {
  startDate?: string;
  endDate?: string;
  deptId?: number;
  locationCode?: string;
  channelCode?: string;
  requisitionId?: number;
  ownerUserId?: number;
  priority?: number;
  status?: string;
  jobName?: string;
  targetText?: string;
  candidateName?: string;
  stageCode?: string;
  submitterName?: string;
  submitterUserId?: number;
  drillKind?: 'STAGE' | 'HC' | 'ROUND';
  roundNo?: number;
  grain?: 'day' | 'week' | 'month' | 'year';
}

export interface HrFunnelNode {
  stageCode: string;
  stageName: string;
  uncollected: boolean;
  count: number;
  conversion?: number | null;
  mom?: number | null;
  yoy?: number | null;
}

export interface HrBoard {
  funnel: HrFunnelNode[];
  cycle: {
    avgDays?: number | null;
    screenToFirstDays?: number | null;
    firstToSecondDays?: number | null;
    byJob: { name: string; days: number }[];
    trend: { axis: string; series: string; value: number }[];
  };
  hc: {
    demand: number;
    arrived: number;
    gap: number;
    closed: number;
    paused: number;
    completionRate?: number | null;
    byDept: { deptName: string; demand: number; arrived: number; gap: number }[];
    rows: {
      id: number;
      jobName: string;
      location: string;
      status: string;
      priority?: number | null;
      targetText: string;
      receivedDate: string;
      onboardDate?: string | null;
      headcount: number;
      arrived: number;
      gap: number;
      warning: boolean;
    }[];
    trend: { axis: string; series: string; value: number }[];
  };
  interview: {
    rounds: { roundNo: number; roundName: string; count: number }[];
    interviewers: { name: string; days: number }[];
    trend: { axis: string; series: string; value: number }[];
  };
}

export interface HrDrillRow {
  applicationId: number;
  requisitionId?: number;
  candidateName: string;
  jobName?: string;
  channel?: string;
  stageName?: string;
  submitter?: string;
  submittedAt?: string;
  interviewer?: string;
  interviewAt?: string;
}

export function getHrBoardApi(data: HrBoardQuery) {
  return request.post<unknown, HrBoard>('/hr/board', data);
}

export function getHrDrillApi(data: HrBoardQuery) {
  return request.post<unknown, HrDrillRow[]>('/hr/board/drill', data);
}

export function getHrMetricsApi() {
  return request.get<unknown, { name: string; formula: string }[]>('/hr/board/metrics');
}

export function saveHrViewApi(viewName: string, filter: HrBoardQuery) {
  return request.post('/hr/board/view', { viewName, filter, isDefault: false });
}

export function getHrViewsApi() {
  return request.get<unknown, { id: number; viewName: string; filterJson: HrBoardQuery }[]>('/hr/board/view');
}

export function getHrRequisitionsApi(data: HrBoardQuery = {}) {
  return request.post<unknown, Record<string, unknown>[]>('/hr/requisition/list', data);
}

export function getHrApplicationsApi(data: HrBoardQuery = {}) {
  return request.post<unknown, Record<string, unknown>[]>('/hr/application/list', data);
}

export function saveHrApplicationApi(data: Record<string, unknown>) {
  return data.id
    ? request.put<unknown, number>('/hr/application', data)
    : request.post<unknown, number>('/hr/application', data);
}

export function uploadHrResumeApi(applicationId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, void>(`/hr/application/${applicationId}/resume`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function deleteHrApplicationApi(id: number) {
  return request.delete(`/hr/application/${id}`);
}

export function deleteHrApplicationBatchApi(ids: number[]) {
  return request.delete('/hr/application/batch', { data: ids });
}

export function getHrStagesApi() {
  return request.get<unknown, { stageCode?: string; stage_code?: string; stageName?: string; stage_name?: string }[]>(
    '/hr/stage/options',
  );
}

export function getHrApplicationApi(id: number) {
  return request.get<unknown, Record<string, unknown>>(`/hr/application/${id}`);
}

export function getHrDepartmentsApi() {
  return request.get<unknown, Record<string, unknown>[]>('/hr/department/tree');
}

export function saveHrDepartmentApi(data: {
  id?: number;
  parentId?: number;
  name: string;
  leaderUserId?: number;
  sortOrder?: number;
}) {
  return data.id ? request.put('/hr/department', data) : request.post('/hr/department', data);
}

export function getHrTargetOptionsApi() {
  return request.get<unknown, { id: number; name: string; sort_no?: number; sortNo?: number }[]>('/hr/target/options');
}

export function saveHrTargetApi(data: { id?: number; name: string; sortNo?: number }) {
  return data.id ? request.put('/hr/target', data) : request.post('/hr/target', data);
}

export function deleteHrTargetApi(id: number) {
  return request.delete(`/hr/target/${id}`);
}

export function getHrUsersApi(scope?: 'owner' | 'interviewer') {
  return request.get<unknown, { userId: number; username: string; nickname: string; dingtalkBound: number }[]>(
    '/hr/user/options',
    { params: scope ? { scope } : undefined },
  );
}

export function saveHrRequisitionApi(data: Record<string, unknown>) {
  return data.id ? request.put('/hr/requisition', data) : request.post('/hr/requisition', data);
}

export function changeHrRequisitionStatusApi(id: number, status: string) {
  return request.post(`/hr/requisition/${id}/status`, { status });
}

export function deleteHrRequisitionApi(id: number) {
  return request.delete(`/hr/requisition/${id}`);
}

export function deleteHrRequisitionBatchApi(ids: number[]) {
  return request.delete('/hr/requisition/batch', { data: ids });
}

export function getHrChannelsApi() {
  return request.get<unknown, { channelCode: string; channelName: string }[]>('/hr/channel/options');
}

export function getHrSchoolsApi(data: {
  pageNum?: number;
  pageSize?: number;
  kind?: 'DOMESTIC' | 'QS';
  name?: string;
  region?: string;
  tags?: string;
}) {
  return request.post<
    unknown,
    {
      total: number;
      rows: {
        id: number;
        name: string;
        nameEn?: string;
        code?: string;
        schoolType?: string;
        tags?: string;
        eduLevel?: string;
        region?: string;
        authority?: string;
        qsRank?: string;
        rankNo?: number;
        abbr?: string;
        score?: number;
        intro?: string;
      }[];
    }
  >('/hr/school/list', data);
}

export function createHrInviteApi(data: {
  applicationId: number;
  roundNo: number;
  interviewerUserId?: number;
  interviewerUserIds?: number[];
  interviewAt: string;
  durationMin?: number;
  location?: string;
}) {
  return request.post<unknown, { id: number; warning?: string }>('/hr/invite', data);
}

export function cancelHrInviteApi(id: number) {
  return request.post(`/hr/invite/${id}/cancel`);
}

export async function fetchHrResumeApi(id: number) {
  const token = getToken();
  const res = await fetch(withBase(`/api/hr/application/${id}/resume`), {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    let msg = '简历打不开';
    try {
      const body = (await res.json()) as { msg?: string };
      if (body?.msg) msg = body.msg;
    } catch {
      /* 非 JSON */
    }
    throw new Error(msg);
  }
  return { blob: await res.blob(), contentType: res.headers.get('content-type') || '' };
}

export function listHrInvitesApi(data: Record<string, unknown>) {
  return request.post<unknown, Record<string, unknown>[]>('/hr/invite/list', data);
}

export function updateHrInviteApi(id: number, data: Record<string, unknown>) {
  return request.put<unknown, { id: number; warning?: string }>(`/hr/invite/${id}`, data);
}

export function createHrInviteCalendarApi(id: number) {
  return request.post(`/hr/invite/${id}/calendar`);
}

export function createHrInviteCalendarBatchApi(ids: number[]) {
  return request.post<unknown, { id?: number; warning?: string; created?: number; unboundInterviewers?: string[] }>(
    '/hr/invite/calendar/batch',
    ids,
  );
}

export function deleteHrInviteApi(id: number) {
  return request.delete<unknown, { id: number; warning?: string }>(`/hr/invite/${id}`);
}

export function deleteHrInviteBatchApi(ids: number[]) {
  return request.delete<unknown, { id: number; warning?: string }>('/hr/invite/batch', { data: ids });
}

export function forwardHrInviteApi(id: number, interviewerUserId: number) {
  return request.post<unknown, { id: number; warning?: string }>(`/hr/invite/${id}/forward`, { interviewerUserId });
}

export function addHrInviteInterviewerApi(id: number, interviewerUserId: number) {
  return request.post<unknown, { id: number; warning?: string }>(`/hr/invite/${id}/interviewer`, { interviewerUserId });
}

export function listHrInterviewRecordsApi(data: Record<string, unknown>) {
  return request.post<unknown, Record<string, unknown>[]>('/hr/interview-record/list', data);
}

export function saveHrInterviewRecordApi(data: Record<string, unknown>) {
  return request.post<unknown, string>('/hr/interview-record', data);
}

export function listHrInterviewReviewsApi(applicationId: number) {
  return request.get<
    unknown,
    {
      kind: string;
      roundNo: number;
      roundName?: string;
      interviewerName?: string;
      conclusion?: string;
      comment?: string;
      interviewedAt?: string;
    }[]
  >(`/hr/application/${applicationId}/reviews`);
}

export function saveHrInterviewVerdictApi(data: {
  applicationId: number;
  roundNo: number;
  conclusion: string;
  comment?: string;
}) {
  return request.post<unknown, string>('/hr/application/verdict', data);
}

export function deleteHrInterviewOwnApi(id: number) {
  return request.delete(`/hr/interview-record/mine/${id}`);
}

export function deleteHrInterviewRecordApi(id: number) {
  return request.delete(`/hr/interview-record/${id}`);
}

export function deleteHrInterviewRecordBatchApi(ids: number[]) {
  return request.delete('/hr/interview-record/batch', { data: ids });
}

export function listMyHrInvitesApi() {
  return request.post<unknown, Record<string, unknown>[]>('/hr/invite/mine', {});
}

export function previewHrDingTalkApi(userId: number) {
  return request.get<unknown, { phone: string; dingtalkUserId: string; unionId: string }>(
    `/hr/dingtalk/preview/${userId}`,
  );
}

export function bindHrDingTalkApi(userId: number) {
  return request.post('/hr/dingtalk/bind', { userId });
}

export function unbindHrDingTalkApi(userId: number) {
  return request.delete(`/hr/dingtalk/${userId}`);
}

export function getHrAiProvidersApi() {
  return request.get<unknown, { provider: string; label: string; model?: string; available: boolean }[]>(
    '/hr/application/ai/providers',
  );
}

export function listHrApplicationAiApi(applicationId: number) {
  return request.get<
    unknown,
    {
      id: number;
      providerName?: string;
      modelName?: string;
      score?: number;
      prosCons?: string;
      interviewAdvice?: string;
      createTime?: string;
    }[]
  >(`/hr/application/${applicationId}/ai`);
}

export function generateHrApplicationAiApi(data: { applicationId: number; provider: string }) {
  return request.post<
    unknown,
    {
      id: number;
      providerName?: string;
      modelName?: string;
      score?: number;
      prosCons?: string;
      interviewAdvice?: string;
      createTime?: string;
    }
  >('/hr/application/ai', data, { timeout: 180000 });
}

export interface HrApplicationImportError {
  rowIndex: number;
  message: string;
}

export interface HrApplicationImportResult {
  success: boolean;
  total: number;
  successCount: number;
  errors: HrApplicationImportError[];
}

export function importHrApplicationsApi(file: File, onProgress?: (percent: number) => void) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, HrApplicationImportResult>('/hr/application/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    onUploadProgress: (event) => {
      if (!event.total) return;
      onProgress?.(Math.min(90, Math.round((event.loaded / event.total) * 90)));
    },
  });
}

export async function downloadHrApplicationTemplateApi() {
  const token = getToken();
  const res = await fetch(withBase('/api/hr/application/import/template'), {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error('模板下载失败');
  const blob = await res.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = '候选人导入模板.xlsx';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(objectUrl);
}

export async function downloadHrDrillApi(data: HrBoardQuery) {
  const token = getToken();
  const res = await fetch(withBase('/api/hr/board/export'), {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    throw new Error('导出失败');
  }
  const blob = await res.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = '招聘明细.xlsx';
  a.click();
  window.URL.revokeObjectURL(objectUrl);
}
