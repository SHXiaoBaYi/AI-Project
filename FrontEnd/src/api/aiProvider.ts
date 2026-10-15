import request from './request';

export type AiProviderVO = {
  id: number;
  provider: string;
  providerName: string;
  apiKeyMasked: string;
  hasApiKey: boolean;
  model: string;
  baseUrl?: string;
  enabled: number;
  sortOrder?: number;
  remark?: string;
};

export type AiProviderUpdateDTO = {
  id: number;
  apiKey?: string;
  clearApiKey?: boolean;
  model: string;
  baseUrl?: string;
  enabled: number;
  remark?: string;
};

export type AiProviderTestResult = {
  success: boolean;
  provider?: string;
  model?: string;
  httpStatus?: number;
  message?: string;
  attempts?: string[];
};

export function getAiProviderListApi() {
  return request.get<unknown, AiProviderVO[]>('/system/ai-provider/list');
}

export function updateAiProviderApi(data: AiProviderUpdateDTO) {
  return request.put('/system/ai-provider', data);
}

export function testAiProviderApi(provider: string) {
  return request.post<unknown, AiProviderTestResult>(`/system/ai-provider/test/${provider}`, undefined, {
    timeout: 180000,
  });
}
