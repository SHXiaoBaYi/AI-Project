/** 本机访问：用于密码登录暗门、数据权限配置入口 */
export function isLocalhostHost() {
  const host = window.location.hostname.toLowerCase();
  return host === 'localhost' || host === '127.0.0.1' || host === '[::1]';
}

export const DATA_SCOPE_OPERATOR = 'bella';

/** 数据权限配置页：localhost 或 bella 可见 */
export function canSeeDataScopePage(username?: string | null) {
  if (isLocalhostHost()) return true;
  return !!username && username.toLowerCase() === DATA_SCOPE_OPERATOR;
}
