import request from './request';

export type DingTalkAppVO = {
  appId: string;
  agentId: string;
  clientId: string;
  clientSecretMasked?: string;
  hasClientSecret?: boolean;
  enabled: number;
};

export type DingTalkAppDTO = {
  appId: string;
  agentId: string;
  clientId: string;
  clientSecret?: string;
  enabled: number;
};

export function getDingTalkAppApi() {
  return request.get<unknown, DingTalkAppVO>('/system/dingtalk');
}

export function saveDingTalkAppApi(data: DingTalkAppDTO) {
  return request.put('/system/dingtalk', data);
}

export function testDingTalkAppApi(data: DingTalkAppDTO) {
  return request.post('/system/dingtalk/test', data);
}

export type DingTalkBusyUserOption = {
  userId: number;
  username: string;
  nickname: string;
};

export type DingTalkBusySlot = {
  status: string;
  statusLabel: string;
  start: string;
  end: string;
};

export type DingTalkBusyUser = {
  userId: number;
  username: string;
  nickname: string;
  error?: string;
  slots: DingTalkBusySlot[];
};

export function getDingTalkBusyUsersApi() {
  return request.get<unknown, DingTalkBusyUserOption[]>('/system/dingtalk/busy/users');
}

export function queryDingTalkBusyApi(data: { userIds: number[]; startTime: string; endTime: string }) {
  return request.post<unknown, DingTalkBusyUser[]>('/system/dingtalk/busy/query', data);
}
