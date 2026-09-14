import axios from 'axios';
import { message } from '@/store/slices/staticFunctionSlice';
import { getToken, removeToken } from '@/utils/auth';
import type { ApiResult } from '@/types/api';

const HTTP_UNAUTHORIZED = 401; // 未认证
const HTTP_FORBIDDEN = 403; // 已认证，未授权
const HTTP_SUCCESS = 200; // 业务成功

/** HTTP 状态码对应的错误提示 */
const HTTP_STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数错误',
  404: '请求的资源不存在',
  405: '请求方法不允许',
  500: '服务器内部错误',
  502: '网关错误',
  503: '服务暂不可用',
  504: '网关超时',
};

/** 处理认证/授权失败，返回 true 表示已拦截 */
function handleAuthError(code: number): boolean {
  if (code === HTTP_UNAUTHORIZED) {
    sessionStorage.setItem('authError', '登录已失效，请重新登录');
    removeToken();
    window.location.href = '/login';
    return true;
  }
  if (code === HTTP_FORBIDDEN) {
    message.error('权限不足，请联系管理员');
    return true;
  }
  return false;
}

const service = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

service.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult<unknown>;
    if (handleAuthError(res.code)) {
      return Promise.reject(new Error(res.msg));
    }
    if (res.code !== HTTP_SUCCESS) {
      message.error(res.msg || '请求失败');
      return Promise.reject(new Error(res.msg || '请求失败'));
    }
    return res.data as any;
  },
  (error) => {
    const httpStatus = error.response?.status;
    if (!handleAuthError(httpStatus)) {
      const msg = HTTP_STATUS_MESSAGES[httpStatus] || error.message || '网络错误，请联系管理员';
      message.error(msg);
    }
    return Promise.reject(error);
  },
);

export default service;
