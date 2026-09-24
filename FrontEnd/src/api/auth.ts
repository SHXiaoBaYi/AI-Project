import request from './request';
import type { MenuTree } from '@/types/menu';
import type { UserInfo } from '@/types/user';

export type LoginOptions = {
  passwordLoginEnabled: boolean;
  dingTalkEnabled: boolean;
  clientId?: string;
  corpId?: string;
  exclusiveLogin?: boolean;
  message?: string;
};

export type DingTalkLoginConfig = {
  enabled: number;
  clientId: string;
  corpId?: string;
  exclusiveLogin?: boolean;
  message?: string;
};

export function getLoginOptionsApi() {
  return request.get<unknown, LoginOptions>('/auth/login-options');
}

export function getDingTalkLoginConfigApi() {
  return request.get<unknown, DingTalkLoginConfig>('/auth/dingtalk/config');
}

export function dingTalkLoginApi(authCode: string, force = false, forceTicket?: string) {
  return request.post<unknown, { token: string }>('/auth/dingtalk/login', {
    authCode: forceTicket ? undefined : authCode,
    force,
    forceTicket,
  });
}

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
