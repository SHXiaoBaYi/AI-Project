import request from './request';
import type { MenuVO, MenuTree, MenuDTO } from '@/types/menu';

export function getMenuListApi() {
  return request.get<unknown, MenuVO[]>('/system/menu/list');
}

export function getMenuTreeApi() {
  return request.get<unknown, MenuTree[]>('/system/menu/tree');
}

export function getMenuByIdApi(menuId: number) {
  return request.get<unknown, MenuVO>(`/system/menu/${menuId}`);
}

export function createMenuApi(data: MenuDTO) {
  return request.post('/system/menu', data);
}

export function updateMenuApi(data: MenuDTO) {
  return request.put('/system/menu', data);
}

export function deleteMenuApi(menuId: number) {
  return request.delete(`/system/menu/${menuId}`);
}
