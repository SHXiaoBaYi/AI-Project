import request from './request';
import type { PageQuery, PageResult } from '@/types/api';

export type SysTask = {
  id: number;
  title: string;
  content?: string;
  taskType?: string;
  priority?: number;
  status?: string;
  overdue?: boolean;
  progress?: number;
  creatorUserId?: number;
  creatorName?: string;
  ownerUserId?: number;
  ownerName?: string;
  assigneeUserIds?: number[];
  assigneeNames?: string;
  planStartTime?: string;
  planEndTime?: string;
  actualStartTime?: string;
  actualEndTime?: string;
  bizType?: string;
  bizId?: number;
  bizTitle?: string;
  remark?: string;
  createTime?: string;
  deletable?: boolean;
  assignable?: boolean;
};

export type SysTaskDTO = {
  id?: number;
  title: string;
  content?: string;
  taskType?: string;
  priority?: number;
  status?: string;
  progress?: number;
  ownerUserId: number;
  assigneeUserIds: number[];
  planStartTime?: string;
  planEndTime?: string;
  bizType?: string;
  bizId?: number;
  bizTitle?: string;
  remark?: string;
};

export type SysTaskQuery = PageQuery & {
  title?: string;
  taskType?: string;
  status?: string;
  statuses?: string[];
  priority?: number;
  ownerUserId?: number;
  assigneeUserId?: number;
  creatorUserId?: number;
  mineOnly?: boolean;
  overdueOnly?: boolean;
};

export type SysTaskType = {
  id: number;
  typeName: string;
  sortOrder?: number;
  remark?: string;
  bizType?: string;
  assignField?: string;
  spawnTaskType?: string;
};

export type SysTaskTypeDTO = {
  id?: number;
  typeName: string;
  sortOrder?: number;
  remark?: string;
  bizType?: string;
  assignField?: string;
  spawnTaskType?: string;
};

export type SysTaskTypeQuery = PageQuery & {
  typeName?: string;
};

export function getTaskListApi(data: SysTaskQuery) {
  return request.post<unknown, PageResult<SysTask>>('/task/list', data);
}

export function getTaskByIdApi(id: number) {
  return request.get<unknown, SysTask>(`/task/${id}`);
}

export function createTaskApi(data: SysTaskDTO) {
  return request.post<unknown, number>('/task', data);
}

export function updateTaskApi(data: SysTaskDTO) {
  return request.put('/task', data);
}

export function deleteTaskApi(id: number) {
  return request.delete(`/task/${id}`);
}

export function assignTaskApi(id: number, data: { ownerUserId?: number; assigneeUserIds: number[] }) {
  return request.post(`/task/${id}/assign`, data);
}

export function getTaskTypeListApi(data: SysTaskTypeQuery) {
  return request.post<unknown, PageResult<SysTaskType>>('/task/type/list', data);
}

export function getTaskTypeOptionsApi() {
  return request.get<unknown, SysTaskType[]>('/task/type/options');
}

export function createTaskTypeApi(data: SysTaskTypeDTO) {
  return request.post('/task/type', data);
}

export function updateTaskTypeApi(data: SysTaskTypeDTO) {
  return request.put('/task/type', data);
}

export function deleteTaskTypeApi(id: number) {
  return request.delete(`/task/type/${id}`);
}
