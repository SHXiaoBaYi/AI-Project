import request from '@/api/request';

export type EcomAccessScope = {
  fullAccess?: boolean;
  enabled?: boolean;
  allPlatforms?: boolean;
  allShops?: boolean;
  platforms?: string[];
  shopIds?: number[];
};

export type EcomAccess = {
  allowed: boolean;
  canReturnMain: boolean;
  canManageAcl?: boolean;
  scope?: EcomAccessScope;
};

export type EcomReportType = {
  code: string;
  label: string;
  grain: string;
  platform: string;
};

export type EcomImportConflict = {
  bizDate: string;
  dimension: string;
  reason: string;
};

export type EcomImportResult = {
  needConfirm: boolean;
  batchId?: number;
  platform?: string;
  shopId?: number;
  shopCode?: string;
  shopName?: string;
  reportType?: string;
  reportLabel?: string;
  totalRows: number;
  successRows: number;
  skippedLockedRows: number;
  failRows: number;
  forcedRows?: number;
  message?: string;
  lockedConflicts?: EcomImportConflict[];
};

export type EcomImportJob = {
  jobId: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NEED_CONFIRM';
  total: number;
  processed: number;
  percent: number;
  message?: string;
  result?: EcomImportResult;
};

export type EcomPageResult<T> = {
  total: number;
  rows: T[];
};

export function getEcomAccessApi() {
  return request.get<unknown, EcomAccess>('/ecom/access');
}

export function listEcomReportTypesApi() {
  return request.get<unknown, EcomReportType[]>('/ecom/report-types');
}

export function listEcomShopsApi(platform?: string) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/shops', {
    params: platform ? { platform } : undefined,
  });
}

export function pageEcomFactsApi(params: {
  reportType: string;
  shopId?: number;
  platform?: string;
  startDate?: string;
  endDate?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request.get<unknown, EcomPageResult<Record<string, unknown>>>('/ecom/facts', { params });
}

export function listEcomBoardApi(params: {
  periodType?: string;
  platform?: string;
  shopId?: number;
  startDate?: string;
  endDate?: string;
}) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/board', { params });
}

export function compareEcomBoardApi(periodType: string, periodKey: string) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/board/compare', {
    params: { periodType, periodKey },
  });
}

export function persistEcomBoardApi(data: {
  periodType?: string;
  startDate?: string;
  endDate?: string;
  shopId?: number;
}) {
  return request.post<unknown, Record<string, unknown>>('/ecom/board/persist', data);
}

export function listEcomImportBatchesApi(limit = 20) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/import/batches', {
    params: { limit },
  });
}

export function startEcomImportApi(file: File, opts?: { ignoreLocked?: boolean; forceUpdate?: boolean }) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, EcomImportJob>('/ecom/import/start', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: {
      ignoreLocked: opts?.ignoreLocked ? 'true' : 'false',
      forceUpdate: opts?.forceUpdate ? 'true' : 'false',
    },
    timeout: 60000,
  });
}

export function getEcomImportProgressApi(jobId: string) {
  return request.get<unknown, EcomImportJob>(`/ecom/import/progress/${jobId}`);
}

export async function waitEcomImportJob(
  start: () => Promise<EcomImportJob>,
  onProgress: (job: EcomImportJob) => void,
): Promise<EcomImportJob> {
  const started = await start();
  onProgress(started);
  let job = started;
  while (job.status === 'RUNNING') {
    await new Promise((r) => setTimeout(r, 400));
    job = await getEcomImportProgressApi(started.jobId);
    onProgress(job);
  }
  return job;
}

export function downloadEcomTemplateApi(reportType: string, label: string) {
  return request
    .get<unknown, Blob>(`/ecom/import/template/${encodeURIComponent(reportType)}`, {
      responseType: 'blob',
    })
    .then((blob) => {
      const href = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = href;
      a.download = `${label}-导入模板.xlsx`;
      a.click();
      URL.revokeObjectURL(href);
    });
}

export function archiveEcomWeekApi(data?: { platform?: string; shopId?: number; anchorDate?: string }) {
  return request.post<unknown, Record<string, unknown>>('/ecom/archive/week', data ?? {});
}

export function listEcomArchivePeriodsApi(shopId?: number) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/archive/periods', {
    params: shopId ? { shopId } : undefined,
  });
}

export function listEcomAclApi() {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/acl');
}

export function listEcomAclUsersApi(keyword?: string) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/acl/users', {
    params: keyword ? { keyword } : undefined,
  });
}

export function saveEcomAclApi(data: {
  userId: number;
  enabled?: boolean;
  allPlatforms?: boolean;
  allShops?: boolean;
  platforms?: string[];
  shopIds?: number[];
  remark?: string;
}) {
  return request.put('/ecom/acl', data);
}

export function removeEcomAclApi(userId: number) {
  return request.delete(`/ecom/acl/${userId}`);
}

export function listEcomTargetsApi(params?: { platform?: string; shopId?: number; periodType?: string }) {
  return request.get<unknown, Record<string, unknown>[]>('/ecom/targets', { params });
}

export function saveEcomTargetApi(data: {
  platform?: string;
  shopId?: number;
  periodType: string;
  periodKey: string;
  periodLabel?: string;
  targetGmv?: number;
  targetOrder?: number;
  remark?: string;
}) {
  return request.put('/ecom/targets', data);
}

export function removeEcomTargetApi(id: number) {
  return request.delete(`/ecom/targets/${id}`);
}
