import request from './request';

export type DataScopeOption = {
  value: number;
  label: string;
  extra?: string;
};

export type UserDataScopeGeo = {
  enabled?: boolean;
  topicIds?: number[];
  platformIds?: number[];
  selfOwnerOnly?: boolean;
  selfWriterOnly?: boolean;
  selfPublisherOnly?: boolean;
};

export type UserDataScopeHr = {
  enabled?: boolean;
  deptIds?: number[];
  personMode?: 'DEFAULT' | 'PERSON' | 'SELF' | string;
  targetUserIds?: number[];
};

export type UserDataScopeTask = {
  enabled?: boolean;
  taskTypeIds?: number[];
  ownerOnly?: boolean;
  assigneeOnly?: boolean;
};

export type UserDataScopeVO = {
  userId: number;
  username: string;
  nickname: string;
  globalAll?: boolean;
  summary?: string;
  mode?: string;
  targetUserIds?: number[];
  geo?: UserDataScopeGeo;
  hr?: UserDataScopeHr;
  task?: UserDataScopeTask;
};

export type UserDataScopeMeta = {
  topics: DataScopeOption[];
  platforms: DataScopeOption[];
  departments: DataScopeOption[];
  taskTypes: DataScopeOption[];
};

export function getDataScopeAccessApi() {
  return request.get<unknown, { allowed: boolean }>('/system/data-scope/access');
}

export function getDataScopeMetaApi() {
  return request.get<unknown, UserDataScopeMeta>('/system/data-scope/meta');
}

export function listDataScopeUsersApi() {
  return request.get<unknown, UserDataScopeVO[]>('/system/data-scope/users');
}

export function getDataScopeApi(userId: number) {
  return request.get<unknown, UserDataScopeVO>(`/system/data-scope/${userId}`);
}

export function saveDataScopeApi(data: {
  userId: number;
  globalAll?: boolean;
  geo?: UserDataScopeGeo;
  hr?: UserDataScopeHr;
  task?: UserDataScopeTask;
}) {
  return request.put('/system/data-scope', data);
}
