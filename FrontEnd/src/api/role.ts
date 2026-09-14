import request from './request';
import type { PageResult, PageQuery } from '@/types/api';
import type { RoleVO, RoleDTO } from '@/types/role';

export function getRoleListApi(params: PageQuery & { roleName?: string; roleKey?: string; status?: number }) {
  return request.get<unknown, PageResult<RoleVO>>('/system/role/list', { params });
}

export function getRoleByIdApi(roleId: number) {
  return request.get<unknown, RoleVO>(`/system/role/${roleId}`);
}

export function createRoleApi(data: RoleDTO) {
  return request.post('/system/role', data);
}

export function updateRoleApi(data: RoleDTO) {
  return request.put('/system/role', data);
}

export function deleteRoleApi(roleId: number) {
  return request.delete(`/system/role/${roleId}`);
}

export function assignMenusApi(roleId: number, menuIds: number[]) {
  return request.put('/system/role/assignMenus', { roleId, menuIds });
}
