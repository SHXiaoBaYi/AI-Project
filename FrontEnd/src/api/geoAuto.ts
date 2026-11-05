import request from './request';
import type { PageQuery, PageResult } from '@/types/api';
import type {
  GeoAutoMatrixRow,
  GeoAutoPlatformOption,
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

export type { GeoAutoMatrixRow };
