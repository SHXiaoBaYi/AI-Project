/** 本机访问：用于密码登录暗门、数据权限配置入口 */
export function isLocalhostHost() {
  const host = window.location.hostname.toLowerCase();
  return host === 'localhost' || host === '127.0.0.1' || host === '[::1]';
}

/** @deprecated 保留兼容；请用 DATA_SCOPE_OPERATORS */
export const DATA_SCOPE_OPERATOR = 'bella';

/** 生产/线上可进「数据权限配置」的用户名（与后端 LocalhostAccess 保持一致；含王方扬 thh/tbb 别名账号） */
export const DATA_SCOPE_OPERATORS = ['bella', 'wangfangyang', 'thh', 'tbb'] as const;

/** 数据权限配置页：localhost 或名单内用户可见 */
export function canSeeDataScopePage(username?: string | null) {
  if (isLocalhostHost()) return true;
  if (!username) return false;
  return (DATA_SCOPE_OPERATORS as readonly string[]).includes(username.toLowerCase());
}

/** 电商运营入口：本机/bella，或后端下发的 ecomAllowed（ACL）；与数据权限名单解耦 */
export function canSeeEcomModule(username?: string | null, ecomAllowed?: boolean | null) {
  if (ecomAllowed) return true;
  if (isLocalhostHost()) return true;
  return !!username && username.toLowerCase() === DATA_SCOPE_OPERATOR;
}
