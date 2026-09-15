import axios from 'axios';
import { message } from '@/store/slices/staticFunctionSlice';
import { getToken, removeToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';
import type { ApiResult } from '@/types/api';

const HTTP_UNAUTHORIZED = 401;
const HTTP_FORBIDDEN = 403;
const HTTP_SUCCESS = 200;
const CODE_LOGIN_CONFLICT = 40901;
const CODE_SESSION_KICKED = 4011;

const HTTP_STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数错误',
  404: '请求的资源不存在',
  405: '请求方法不允许',
  500: '服务器内部错误',
  502: '网关错误',
  503: '服务暂不可用',
  504: '网关超时',
};

let kicking = false;

/** 处理认证/授权失败，返回 true 表示已拦截 */
function handleAuthError(code: number, tip?: string): boolean {
  if (code === CODE_SESSION_KICKED || code === HTTP_UNAUTHORIZED) {
    if (kicking) {
      return true;
    }
    kicking = true;
    sessionStorage.setItem(
      'authError',
      tip || (code === CODE_SESSION_KICKED ? '该账号已在其他设备登录，您已被强制下线' : '登录已失效，请重新登录'),
    );
    removeToken();
    window.location.href = withBase('/login');
    return true;
  }
  if (code === HTTP_FORBIDDEN) {
    message.error('权限不足，请联系管理员');
    return true;
  }
  return false;
}

const service = axios.create({
  baseURL: withBase('/api'),
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

    if (res.code === CODE_LOGIN_CONFLICT) {
      const err = new Error(res.msg || '账号已在其他设备登录') as Error & { code: number };
      err.code = CODE_LOGIN_CONFLICT;
      return Promise.reject(err);
    }

    if (handleAuthError(res.code, res.msg)) {
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
    const body = error.response?.data as ApiResult<unknown> | undefined;
    if (body?.code === CODE_SESSION_KICKED || body?.code === CODE_LOGIN_CONFLICT) {
      if (body.code === CODE_LOGIN_CONFLICT) {
        const err = new Error(body.msg || '账号已在其他设备登录') as Error & { code: number };
        err.code = CODE_LOGIN_CONFLICT;
        return Promise.reject(err);
      }
      handleAuthError(CODE_SESSION_KICKED, body.msg);
      return Promise.reject(error);
    }
    if (!handleAuthError(httpStatus)) {
      const msg = HTTP_STATUS_MESSAGES[httpStatus] || error.message || '网络错误，请联系管理员';
      message.error(msg);
    }
    return Promise.reject(error);
  },
);

export default service;
export { CODE_LOGIN_CONFLICT, CODE_SESSION_KICKED };
