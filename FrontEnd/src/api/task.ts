import request from './request';
import type { PageQuery, PageResult } from '@/types/api';

export type SysTaskFile = {
  id: number;
  taskId?: number;
  bizType?: string;
  bizId?: number;
  fileName: string;
  fileUrl: string;
  fileSize?: number;
  contentType?: string;
  uploadUserName?: string;
  createTime?: string;
};

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
  completable?: boolean;
  requireProof?: boolean;
  bizAssign?: boolean;
  fileCount?: number;
  files?: SysTaskFile[];
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
  requireProof?: number | boolean;
};

export type SysTaskTypeDTO = {
  id?: number;
  typeName: string;
  sortOrder?: number;
  remark?: string;
  bizType?: string;
  assignField?: string;
  spawnTaskType?: string;
  requireProof?: boolean;
};

export type SysTaskTypeQuery = PageQuery & {
  typeName?: string;
};

export type SysTaskCompleteDTO = {
  remark?: string;
  files?: Array<{
    fileName: string;
    fileUrl: string;
    fileSize?: number;
    contentType?: string;
  }>;
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

export function batchDeleteTaskApi(data: { taskIds: number[] }) {
  return request.post<unknown, string>('/task/delete/batch', data);
}

export function assignTaskApi(id: number, data: { ownerUserId?: number; assigneeUserIds: number[] }) {
  return request.post(`/task/${id}/assign`, data);
}

export function batchAssignTaskApi(data: { taskIds: number[]; ownerUserId?: number; assigneeUserIds: number[] }) {
  return request.post<unknown, string>('/task/assign/batch', data);
}

export function completeTaskApi(id: number, data: SysTaskCompleteDTO) {
  return request.post(`/task/${id}/complete`, data);
}

export function batchCompleteTaskApi(data: { taskIds: number[]; remark?: string }) {
  return request.post<unknown, string>('/task/complete/batch', data);
}

export function uploadTaskFileApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<unknown, { url: string; fileName: string; fileSize: number; contentType: string }>(
    '/task/file/upload',
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
    },
  );
}

export function getTaskFilesApi(taskId: number) {
  return request.get<unknown, SysTaskFile[]>(`/task/${taskId}/files`);
}

export function getTaskFilesByBizApi(bizType: string, bizId: number) {
  return request.get<unknown, SysTaskFile[]>('/task/files/by-biz', {
    params: { bizType, bizId },
  });
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
