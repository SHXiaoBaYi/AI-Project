import request from './request';
import { getToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';
import type { PageQuery, PageResult } from '@/types/api';
import type {
  GeoBoardQuery,
  GeoDailyBatchDTO,
  GeoDailyBoard,
  GeoDailyBulkSaveDTO,
  GeoDailyBulkSaveResult,
  GeoDailyComboDetail,
  GeoDailyComboVO,
  GeoDailyDTO,
  GeoDailyGroup,
  GeoDailyVO,
  GeoImportResult,
  GeoOwnerOption,
  GeoPlatform,
  GeoPlatformAccount,
  GeoPlatformAccountDTO,
  GeoTopic,
  GeoTopicPlatformCharts,
  GeoPersistResult,
  GeoYearTarget,
  GeoYearlyBoard,
  GeoContentPlacementCite,
  GeoContentPlacementCiteDTO,
  GeoContentPlacementDetail,
  GeoContentPlacementDTO,
  GeoContentPlacementItem,
  GeoContentPlacementItemDTO,
  GeoContentPlacementListItem,
  GeoContentPlacementArticle,
  GeoContentPlacementProofFile,
  GeoTargetQuestionOption,
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

export function deleteGeoTopicBatchApi(ids: number[]) {
  return request.delete('/geo/topic/batch', { data: ids });
}

export function getGeoPlatformListApi(data: PageQuery & { platformName?: string; platformType?: string }) {
  return request.post<unknown, PageResult<GeoPlatform>>('/geo/platform/list', data);
}

export function getGeoPlatformOptionsApi(platformType?: string) {
  return request.get<unknown, GeoPlatform[]>('/geo/platform/options', {
    params: platformType ? { platformType } : undefined,
  });
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

export function deleteGeoPlatformBatchApi(ids: number[]) {
  return request.delete('/geo/platform/batch', { data: ids });
}

export function getGeoPlatformAccountListApi(
  data: PageQuery & {
    platformId?: number;
    account?: string;
    managerUserId?: number;
    holderUserId?: number;
    openerUserId?: number;
    recharged?: number;
    verified?: number;
    accountStatus?: string;
    loginMethod?: string;
  },
) {
  return request.post<unknown, PageResult<GeoPlatformAccount>>('/geo/platform-account/list', data);
}

export function createGeoPlatformAccountApi(data: GeoPlatformAccountDTO) {
  return request.post('/geo/platform-account', data);
}

export function updateGeoPlatformAccountApi(data: GeoPlatformAccountDTO) {
  return request.put('/geo/platform-account', data);
}

export function deleteGeoPlatformAccountApi(id: number) {
  return request.delete(`/geo/platform-account/${id}`);
}

export function deleteGeoPlatformAccountBatchApi(ids: number[]) {
  return request.delete('/geo/platform-account/batch', { data: ids });
}

export function getGeoDailyListApi(
  data: PageQuery & {
    startDate?: string;
    endDate?: string;
    topicId?: number;
    keyword?: string;
    termType?: string;
    ownerUserId?: number;
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

export function getGeoDailyComboListApi(
  data: PageQuery & {
    startDate?: string;
    endDate?: string;
    topicId?: number;
    keyword?: string;
    platforms?: string[];
  },
) {
  return request.post<unknown, PageResult<GeoDailyComboVO>>('/geo/daily/combo/list', data);
}

export function getGeoDailyComboDetailApi(data: {
  startDate?: string;
  endDate?: string;
  topicId: number;
  keywordExact: string;
  platform: string;
}) {
  return request.post<unknown, GeoDailyComboDetail>('/geo/daily/combo/detail', data);
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

export function deleteGeoDailyBatchApi(ids: number[]) {
  return request.delete('/geo/daily/batch', { data: ids });
}

export function importGeoDailyApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, GeoImportResult>('/geo/daily/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  });
}

export type GeoImportJob = {
  jobId: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  total: number;
  processed: number;
  percent: number;
  message?: string;
  result?: GeoImportResult;
};

function startImportJob(url: string, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, GeoImportJob>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
}

export function startGeoDailyImportApi(file: File) {
  return startImportJob('/geo/daily/import/start', file);
}

export function getGeoDailyImportProgressApi(jobId: string) {
  return request.get<unknown, GeoImportJob>(`/geo/daily/import/progress/${jobId}`);
}

export function startGeoContentPlacementImportApi(file: File) {
  return startImportJob('/geo/content-placement/import/start', file);
}

export function getGeoContentPlacementImportProgressApi(jobId: string) {
  return request.get<unknown, GeoImportJob>(`/geo/content-placement/import/progress/${jobId}`);
}

export async function waitGeoImportJob(
  start: () => Promise<GeoImportJob>,
  progress: (jobId: string) => Promise<GeoImportJob>,
  onProgress: (job: GeoImportJob) => void,
): Promise<GeoImportResult> {
  const started = await start();
  onProgress(started);
  let job = started;
  while (job.status === 'RUNNING') {
    await new Promise((resolve) => setTimeout(resolve, 500));
    job = await progress(started.jobId);
    onProgress(job);
  }
  if (job.status === 'FAILED') {
    throw geoImportError(job.message || '导入失败');
  }
  if (!job.result) {
    throw geoImportError('导入完成但没有返回结果');
  }
  return job.result;
}

function geoImportError(message: string) {
  const err = new Error(message) as Error & { geoImport: boolean };
  err.geoImport = true;
  return err;
}

export function downloadGeoDailyTemplateApi() {
  return downloadBlob(withBase('/api/geo/daily/import/template'), '日监测数据导入模板.xlsx');
}

export function uploadGeoScreenshotApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, { url: string }>('/geo/daily/screenshot', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function getGeoPlatformsApi() {
  // 日监测及相关筛选项仅展示 AI平台
  const list = await getGeoPlatformOptionsApi('AI平台');
  return list.map((p) => p.platformName);
}

export function getGeoOwnerOptionsApi() {
  return request.get<unknown, GeoOwnerOption[]>('/geo/daily/owners');
}

export function getGeoDailyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoDailyBoard>('/geo/day/board', data);
}

export function getGeoTopicPlatformChartsApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoTopicPlatformCharts>('/geo/day/topic-platform-charts', data);
}

export function getGeoYearlyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoYearlyBoard>('/geo/yearly/board', data);
}

export function persistGeoYearlyBoardApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoPersistResult>('/geo/yearly/persist', data);
}

export function seedGeoYearlySampleApi() {
  return request.post<unknown, string>('/geo/yearly/seed-sample');
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

export function getGeoContentPlacementListApi(
  data: PageQuery & {
    publisherUserId?: number;
    ownerUserId?: number;
    relatedUserId?: number;
    topicId?: number;
    targetQuestion?: string;
    title?: string;
    source?: string;
    aggregateStatus?: string;
  },
) {
  return request.post<unknown, PageResult<GeoContentPlacementListItem>>('/geo/content-placement/list', data);
}

export function getGeoContentPlacementDetailApi(id: number) {
  return request.get<unknown, GeoContentPlacementDetail>(`/geo/content-placement/${id}`);
}

export function getGeoContentPlacementItemsApi(id: number) {
  return request.get<unknown, GeoContentPlacementItem[]>(`/geo/content-placement/${id}/items`);
}

export function getGeoContentPlacementCitesApi(id: number, itemId?: number) {
  return request.get<unknown, GeoContentPlacementCite[]>(`/geo/content-placement/${id}/cites`, {
    params: itemId != null ? { itemId } : undefined,
  });
}

export function getGeoContentPlacementProofFilesApi(id: number) {
  return request.get<unknown, GeoContentPlacementProofFile[]>(`/geo/content-placement/${id}/proof-files`);
}

export function deleteGeoContentPlacementApi(id: number) {
  return request.delete(`/geo/content-placement/${id}`);
}

export function deleteGeoContentPlacementBatchApi(ids: number[]) {
  return request.delete('/geo/content-placement/batch', { data: ids });
}

export function createGeoContentPlacementApi(data: GeoContentPlacementDTO) {
  return request.post<unknown, number>('/geo/content-placement', data);
}

export function updateGeoContentPlacementApi(data: GeoContentPlacementDTO) {
  return request.put('/geo/content-placement', data);
}

export function createGeoContentPlacementItemApi(data: GeoContentPlacementItemDTO) {
  return request.post<unknown, number>('/geo/content-placement/items', data);
}

export function updateGeoContentPlacementItemApi(data: GeoContentPlacementItemDTO) {
  return request.put('/geo/content-placement/items', data);
}

export function deleteGeoContentPlacementItemApi(itemId: number) {
  return request.delete(`/geo/content-placement/items/${itemId}`);
}

export function deleteGeoContentPlacementItemBatchApi(itemIds: number[]) {
  return request.delete('/geo/content-placement/items/batch', { data: itemIds });
}

export function createGeoContentPlacementCiteApi(data: GeoContentPlacementCiteDTO) {
  return request.post<unknown, number>('/geo/content-placement/cites', data);
}

export function updateGeoContentPlacementCiteApi(data: GeoContentPlacementCiteDTO) {
  return request.put('/geo/content-placement/cites', data);
}

export function deleteGeoContentPlacementCiteApi(citeId: number) {
  return request.delete(`/geo/content-placement/cites/${citeId}`);
}

export function generateSimilarGeoContentPlacementApi(id: number, provider?: string, count?: number) {
  return request.post<
    unknown,
    {
      sourcePlacementId: number;
      sourceQuestion?: string;
      targetQuestion: string;
      sourceAiModel?: string;
      duplicated?: boolean;
    }[]
  >(`/geo/content-placement/${id}/generate-similar`, {
    provider: provider || undefined,
    count,
  });
}

export function generateSimilarGeoContentPlacementBatchApi(ids: number[], provider?: string, count?: number) {
  return request.post<
    unknown,
    {
      sourcePlacementId: number;
      sourceQuestion?: string;
      targetQuestion: string;
      sourceAiModel?: string;
      duplicated?: boolean;
    }[]
  >('/geo/content-placement/generate-similar/batch', {
    ids,
    provider: provider || undefined,
    count,
  });
}

export function saveSimilarGeoContentPlacementApi(
  items: { sourcePlacementId: number; targetQuestion: string; sourceAiModel?: string }[],
) {
  return request.post<unknown, GeoContentPlacementListItem[]>('/geo/content-placement/generate-similar/save', {
    items,
  });
}

export function getGeoContentPlacementArticleListApi(
  data: PageQuery & {
    publisherUserId?: number;
    ownerUserId?: number;
    topicId?: number;
    placementId?: number;
    targetQuestion?: string;
    publishStatus?: string;
    title?: string;
    platformName?: string;
    publishTimeStart?: string;
    publishTimeEnd?: string;
    cited?: number;
    citeSort?: 'asc' | 'desc';
  },
) {
  return request.post<unknown, PageResult<GeoContentPlacementArticle>>('/geo/content-placement/articles/list', data);
}

export function getGeoTargetQuestionsApi(topicId: number) {
  return request.get<unknown, GeoTargetQuestionOption[]>('/geo/content-placement/target-questions', {
    params: { topicId },
  });
}

export function getGeoAiProvidersApi() {
  return request.get<unknown, { provider: string; label: string; model: string; available: boolean; hint?: string }[]>(
    '/geo/content-placement/ai-providers',
  );
}

export function importGeoContentPlacementApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, GeoImportResult>('/geo/content-placement/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000,
  });
}

export function downloadGeoContentPlacementTemplateApi() {
  return downloadBlob(withBase('/api/geo/content-placement/import/template'), '内容投放导入模板.xlsx');
}

async function downloadBlob(url: string, filename: string) {
  const token = getToken();
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error('下载失败');
  }
  const blob = await res.blob();
  const objectUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.style.display = 'none';
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(objectUrl);
}

export function getGeoNegativeDailyApi(data: GeoBoardQuery) {
  return request.post<unknown, GeoDailyVO[]>('/geo/day/negatives', data);
}
