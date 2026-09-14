import request from './request';
import type { MenuTree } from '@/types/menu';
import type { UserInfo } from '@/types/user';

export function loginApi(username: string, password: string) {
  return request.post<unknown, { token: string }>('/auth/login', { username, password });
}

export function getUserInfoApi() {
  return request.get<unknown, UserInfo & { menus: MenuTree[] }>('/auth/info');
}
