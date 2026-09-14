import request from './request';
import type { MenuTree } from '@/types/menu';
import type { UserInfo } from '@/types/user';

export function loginApi(username: string, password: string, force = false) {
  return request.post<unknown, { token: string }>('/auth/login', { username, password, force });
}

export function getUserInfoApi() {
  return request.get<unknown, UserInfo & { menus: MenuTree[] }>('/auth/info');
}

export function checkSessionApi() {
  return request.get<unknown, void>('/auth/session');
}

export function logoutApi() {
  return request.post<unknown, void>('/auth/logout');
}
