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

/** v28 用系统维护的平台账号在服务器无头浏览器模拟登录并刷新登录态 */
export function browserLoginGeoAutoApi(data: GeoAutoBrowserLoginDTO) {
  return request.post<unknown, GeoBrowserLoginVO>('/geo/auto/browser/login', data);
}

/** v28 各平台浏览器自动监测账号与登录态就绪状态 */
export function getGeoAutoBrowserAccountsApi() {
  return request.get<unknown, GeoBrowserAccountVO[]>('/geo/auto/browser/accounts');
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
