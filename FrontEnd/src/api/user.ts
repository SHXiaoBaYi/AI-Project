import request from './request';
import type { PageResult, PageQuery } from '@/types/api';
import type { UserVO, UserDTO, UserImportResult } from '@/types/user';
import { getToken } from '@/utils/auth';

export function getUserListApi(
  data: PageQuery & {
    username?: string;
    nickname?: string;
    phone?: string;
    email?: string;
    roleIds?: number[];
    status?: number;
  },
) {
  return request.post<unknown, PageResult<UserVO>>('/system/user/list', data);
}

export function getUserByIdApi(userId: number) {
  return request.get<unknown, UserVO>(`/system/user/${userId}`);
}

export function createUserApi(data: UserDTO) {
  return request.post('/system/user', data);
}

export function updateUserApi(data: UserDTO) {
  return request.put('/system/user', data);
}

export function deleteUserApi(userId: number) {
  return request.delete(`/system/user/${userId}`);
}

export function deleteUserBatchApi(ids: number[]) {
  return request.delete('/system/user/batch', { data: ids });
}

export function resetPwdApi(userId: number, password: string) {
  return request.put('/system/user/resetPwd', { userId, password });
}

export function changeStatusApi(userId: number, status: number) {
  return request.put('/system/user/changeStatus', { userId, status });
}

export function assignRolesApi(userId: number, roleIds: number[]) {
  return request.put('/system/user/assignRoles', { userId, roleIds });
}

export function importUsersApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, UserImportResult>('/system/user/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
}

/** 通用 blob 下载：fetch → blob → 触发浏览器保存 */
async function downloadBlob(
  url: string,
  filename: string,
  init?: { method?: string; headers?: Record<string, string>; body?: string },
) {
  const token = getToken();
  const res = await fetch(url, {
    ...init,
    headers: { ...init?.headers, Authorization: `Bearer ${token}` },
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

export function exportUsersApi(ids: number[]) {
  return downloadBlob('/api/system/user/export', '用户导出数据.xlsx', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ids),
  });
}

export function downloadTemplateApi() {
  return downloadBlob('/api/system/user/import/template', '用户导入模板.xlsx');
}
