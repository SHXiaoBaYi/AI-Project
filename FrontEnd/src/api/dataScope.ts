import request from './request';

export type UserDataScopeVO = {
  userId: number;
  username: string;
  nickname: string;
  mode: 'DEFAULT' | 'PERSON' | 'SELF' | string;
  targetUserIds?: number[];
};

export function getDataScopeAccessApi() {
  return request.get<unknown, { allowed: boolean }>('/system/data-scope/access');
}

export function listDataScopeUsersApi() {
  return request.get<unknown, UserDataScopeVO[]>('/system/data-scope/users');
}

export function getDataScopeApi(userId: number) {
  return request.get<unknown, UserDataScopeVO>(`/system/data-scope/${userId}`);
}

export function saveDataScopeApi(data: { userId: number; mode: string; targetUserIds?: number[] }) {
  return request.put('/system/data-scope', data);
}
