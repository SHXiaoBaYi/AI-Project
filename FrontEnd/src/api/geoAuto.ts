import request from './request';
import type { PageQuery, PageResult } from '@/types/api';
import type {
  GeoAutoBrowserLoginDTO,
  GeoAutoMatrixRow,
  GeoAutoPlatformOption,
  GeoAutoProbeDTO,
  GeoAutoQuestion,
  GeoAutoQuestionDTO,
  GeoAutoQuestionOption,
  GeoAutoQuestionQuery,
  GeoAutoResult,
  GeoAutoResultDetail,
  GeoAutoResultMatrix,
  GeoAutoResultQuery,
  GeoAutoRun,
  GeoAutoSeedResult,
  GeoAutoTask,
  GeoAutoTaskDTO,
  GeoAutoTaskQuery,
  GeoBrowserAccountVO,
  GeoBrowserLoginBoothVO,
  GeoBrowserLoginVO,
  GeoBrowserProbeVO,
} from '@/types/geoAuto';

// ==================== 监控问题库 ====================

export function getGeoAutoQuestionListApi(data: GeoAutoQuestionQuery) {
  return request.post<unknown, PageResult<GeoAutoQuestion>>('/geo/auto/questions/list', data);
}

export function getGeoAutoQuestionOptionsApi(topicId?: number) {
  return request.get<unknown, GeoAutoQuestionOption[]>('/geo/auto/questions/options', {
    params: topicId ? { topicId } : undefined,
  });
}

export function createGeoAutoQuestionApi(data: GeoAutoQuestionDTO) {
  return request.post('/geo/auto/questions', data);
}

export function updateGeoAutoQuestionApi(data: GeoAutoQuestionDTO) {
  return request.put('/geo/auto/questions', data);
}

export function deleteGeoAutoQuestionApi(id: number) {
  return request.delete(`/geo/auto/questions/${id}`);
}

export function deleteGeoAutoQuestionBatchApi(ids: number[]) {
  return request.delete('/geo/auto/questions/batch', { data: ids });
}

export function setGeoAutoQuestionEnabledApi(ids: number[], enabled: number) {
  return request.post('/geo/auto/questions/enabled', { ids, enabled });
}

export function seedGeoAutoQuestionsApi() {
  return request.post<unknown, GeoAutoSeedResult>('/geo/auto/questions/seed');
}

// ==================== 监测任务 ====================

export function getGeoAutoTaskListApi(data: GeoAutoTaskQuery) {
  return request.post<unknown, PageResult<GeoAutoTask>>('/geo/auto/tasks/list', data);
}

export function getGeoAutoPlatformOptionsApi() {
  return request.get<unknown, GeoAutoPlatformOption[]>('/geo/auto/platforms/options');
}

export function createGeoAutoTaskApi(data: GeoAutoTaskDTO) {
  return request.post('/geo/auto/tasks', data);
}

export function updateGeoAutoTaskApi(data: GeoAutoTaskDTO) {
  return request.put('/geo/auto/tasks', data);
}

export function deleteGeoAutoTaskApi(id: number) {
  return request.delete(`/geo/auto/tasks/${id}`);
}

export function toggleGeoAutoTaskApi(id: number, enabled: number) {
  return request.post(`/geo/auto/tasks/${id}/enabled/${enabled}`);
}

export function runGeoAutoTaskApi(id: number) {
  return request.post<unknown, GeoAutoRun>(`/geo/auto/tasks/${id}/run`);
}

// ==================== 运行批次与结果 ====================

export function getGeoAutoRunListApi(params: PageQuery & { taskId?: number }) {
  return request.get<unknown, PageResult<GeoAutoRun>>('/geo/auto/runs', { params });
}

export function getGeoAutoRunApi(id: number) {
  return request.get<unknown, GeoAutoRun>(`/geo/auto/runs/${id}`);
}

export function getGeoAutoResultListApi(data: GeoAutoResultQuery) {
  return request.post<unknown, PageResult<GeoAutoResult>>('/geo/auto/results/list', data);
}

export function getGeoAutoResultDetailApi(id: number) {
  return request.get<unknown, GeoAutoResultDetail>(`/geo/auto/results/${id}`);
}

export function getGeoAutoResultMatrixApi(data: GeoAutoResultQuery) {
  return request.post<unknown, GeoAutoResultMatrix>('/geo/auto/results/matrix', data);
}

// ==================== 浏览器通道取证 ====================

/** 单题采集试跑：回答/思考过程/官方分享链接/整页截图/诊断（不落业务库） */
export function probeGeoAutoBrowserApi(data: GeoAutoProbeDTO) {
  return request.post<unknown, GeoBrowserProbeVO>('/geo/auto/browser/probe', data);
}

/** 人工补录真实 C 端截图 */
export function uploadGeoAutoScreenshotApi(resultId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, { screenshotUrl: string }>(`/geo/auto/results/${resultId}/screenshot`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** v28 用系统维护的平台账号在服务器有界面浏览器模拟登录并刷新登录态 */
export function browserLoginGeoAutoApi(data: GeoAutoBrowserLoginDTO) {
  return request.post<unknown, GeoBrowserLoginVO>('/geo/auto/browser/login', data);
}

/** v28 各平台浏览器自动监测账号与登录态就绪状态 */
export function getGeoAutoBrowserAccountsApi() {
  return request.get<unknown, GeoBrowserAccountVO[]>('/geo/auto/browser/accounts');
}

/** 打开服务器登录窗口 */
export function startGeoBrowserBoothApi(accountId: number) {
  return request.post<unknown, GeoBrowserLoginBoothVO>('/geo/auto/browser/booth/start', { accountId });
}

/** 刷新服务器登录窗口画面 */
export function snapshotGeoBrowserBoothApi(accountId: number) {
  return request.get<unknown, GeoBrowserLoginBoothVO>('/geo/auto/browser/booth', { params: { accountId } });
}

/** 向服务器登录窗口发送点击或输入 */
export function inputGeoBrowserBoothApi(data: {
  accountId: number;
  action: 'click' | 'type' | 'key';
  x?: number;
  y?: number;
  text?: string;
  key?: string;
}) {
  return request.post<unknown, GeoBrowserLoginBoothVO>('/geo/auto/browser/booth/input', data);
}

/** 登录完成后保存该账号自己的登录态 */
export function finishGeoBrowserBoothApi(accountId: number) {
  return request.post<unknown, GeoBrowserLoginBoothVO>('/geo/auto/browser/booth/finish', { accountId });
}

/** 关闭服务器登录窗口且不保存 */
export function cancelGeoBrowserBoothApi(accountId: number) {
  return request.post<unknown, GeoBrowserLoginBoothVO>('/geo/auto/browser/booth/cancel', { accountId });
}

/** 把 storageState 绑定到指定平台账号 */
export function uploadAccountBrowserStateApi(accountId: number, file: File) {
  const formData = new FormData();
  formData.append('accountId', String(accountId));
  formData.append('file', file);
  return request.post<unknown, { accountId: number }>('/geo/auto/browser/state/account', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 上传平台登录态 storageState JSON */
export function uploadGeoAutoBrowserStateApi(platform: string, file: File) {
  const formData = new FormData();
  formData.append('platform', platform);
  formData.append('file', file);
  return request.post<unknown, { platform: string }>('/geo/auto/browser/state', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export type { GeoAutoMatrixRow };
