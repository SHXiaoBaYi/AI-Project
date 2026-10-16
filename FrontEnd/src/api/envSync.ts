import request from './request';

export type EnvSyncResult = {
  kind: 'DATA' | 'SCHEMA' | string;
  message: string;
  elapsedMs: number;
  details?: string[];
};

/** 生产数据 → 测试（仅数据） */
export function syncProdDataToTestApi() {
  return request.post<unknown, EnvSyncResult>('/system/env-sync/data-from-prod', {}, { timeout: 30 * 60 * 1000 });
}

/** 测试表结构 → 生产（只增不删） */
export function syncTestSchemaToProdApi() {
  return request.post<unknown, EnvSyncResult>('/system/env-sync/schema-to-prod', {}, { timeout: 30 * 60 * 1000 });
}
