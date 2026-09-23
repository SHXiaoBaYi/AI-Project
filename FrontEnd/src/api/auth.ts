import request from './request';
import type { MenuTree } from '@/types/menu';
import type { UserInfo } from '@/types/user';

export type DingTalkLoginConfig = {
  enabled: number;
  clientId: string;
  corpId?: string;
  exclusiveLogin?: boolean;
  message?: string;
};

export function getDingTalkLoginConfigApi() {
  return request.get<unknown, DingTalkLoginConfig>('/auth/dingtalk/config');
}

export function dingTalkLoginApi(authCode: string, force = false) {
  return request.post<unknown, { token: string }>('/auth/dingtalk/login', { authCode, force });
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
