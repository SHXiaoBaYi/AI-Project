import request from './request';
import type { PageQuery, PageResult } from '@/types/api';
import type {
  GeoBoardQuery,
  GeoDailyBatchDTO,
  GeoDailyBoard,
  GeoDailyBulkSaveDTO,
  GeoDailyBulkSaveResult,
  GeoDailyDTO,
  GeoDailyGroup,
  GeoDailyVO,
  GeoImportResult,
  GeoMonthlyBoard,
  GeoPersistResult,
  GeoPlatform,
  GeoTopic,
  GeoWeeklyBoard,
  GeoYearTarget,
  GeoYearlyBoard,
} from '@/types/geo';

export function getGeoTopicListApi(data: PageQuery & { topicName?: string }) {
  return request.post<unknown, PageResult<GeoTopic>>('/geo/topic/list', data);
}

export function getGeoTopicOptionsApi() {
  return request.get<unknown, GeoTopic[]>('/geo/topic/options');
}

export function createGeoTopicApi(data: Partial<GeoTopic>) {
  return request.post('/geo/topic', data);
}

export function updateGeoTopicApi(data: Partial<GeoTopic>) {
  return request.put('/geo/topic', data);
}

export function deleteGeoTopicApi(id: number) {
  return request.delete(`/geo/topic/${id}`);
}

export function getGeoPlatformListApi(data: PageQuery & { platformName?: string }) {
  return request.post<unknown, PageResult<GeoPlatform>>('/geo/platform/list', data);
}

export function getGeoPlatformOptionsApi() {
  return request.get<unknown, GeoPlatform[]>('/geo/platform/options');
}

export function createGeoPlatformApi(data: Partial<GeoPlatform>) {
  return request.post('/geo/platform', data);
}

export function updateGeoPlatformApi(data: Partial<GeoPlatform>) {
  return request.put('/geo/platform', data);
}

export function deleteGeoPlatformApi(id: number) {
  return request.delete(`/geo/platform/${id}`);
}

export function getGeoDailyListApi(
  data: PageQuery & {
    startDate?: string;
    endDate?: string;
    topicId?: number;
    keyword?: string;
    ownerName?: string;
    platforms?: string[];
    mentioned?: number;
    rankNoMin?: number;
    rankNoMax?: number;
    recommendStatus?: string;
    hasScreenshot?: number;
    hasThirdPartyUrl?: number;
    competitors?: string;
    boardLocked?: number;
    updateTimeStart?: string;
    updateTimeEnd?: string;
  },
) {
  return request.post<unknown, PageResult<GeoDailyVO>>('/geo/daily/list', data);
}

export function createGeoDailyApi(data: GeoDailyDTO) {
  return request.post('/geo/daily', data);
}

export function updateGeoDailyApi(data: GeoDailyDTO) {
  return request.put('/geo/daily', data);
}

export function saveGeoDailyBatchApi(data: GeoDailyBatchDTO, isEdit: boolean) {
  return isEdit ? request.put('/geo/daily/batch', data) : request.post('/geo/daily/batch', data);
}

export function saveGeoDailyBulkApi(data: GeoDailyBulkSaveDTO) {
  return request.post<unknown, GeoDailyBulkSaveResult>('/geo/daily/bulk', data);
}

export function getGeoDailyGroupApi(inspectDate: string, keyword: string) {
  return request.get<unknown, GeoDailyGroup>('/geo/daily/group', { params: { inspectDate, keyword } });
}

export function getGeoLatestInspectDateApi() {
  return request.get<unknown, { inspectDate?: string }>('/geo/daily/latest-date');
}

export function deleteGeoDailyApi(id: number) {
  return request.delete(`/geo/daily/${id}`);
}

export function importGeoDailyApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, GeoImportResult>('/geo/daily/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  });
}

export function uploadGeoScreenshotApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, { url: string }>('/geo/daily/screenshot', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function getGeoPlatformsApi() {
  const list = await getGeoPlatformOptionsApi();
  return list.map((p) => p.platformName);
}

export function getGeoWeeklyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoWeeklyBoard>('/geo/weekly/board', data);
}

export function persistGeoWeeklyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoPersistResult>('/geo/weekly/persist', data);
}

export function getGeoMonthlyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoMonthlyBoard>('/geo/monthly/board', data);
}

export function persistGeoMonthlyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoPersistResult>('/geo/monthly/persist', data);
}

export function getGeoDailyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoDailyBoard>('/geo/day/board', data);
}

export function getGeoYearlyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoYearlyBoard>('/geo/yearly/board', data);
}

export function persistGeoYearlyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoPersistResult>('/geo/yearly/persist', data);
}

export function getGeoYearTargetsApi() {
  return request.get<unknown, GeoYearTarget[]>('/geo/yearly/targets');
}

export function saveGeoYearTargetApi(data: Partial<GeoYearTarget>) {
  return request.post('/geo/yearly/targets', data);
}

export function deleteGeoYearTargetApi(id: number) {
  return request.delete(`/geo/yearly/targets/${id}`);
}
