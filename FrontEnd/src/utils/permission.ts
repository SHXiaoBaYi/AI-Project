const ADMIN_PERM = '*:*:*';

export function hasPermission(perm: string, permissions: string[]): boolean {
  if (!permissions?.length) return false;
  if (permissions.includes(ADMIN_PERM)) return true;
  if (permissions.includes(perm)) return true;
  // "hr:*" → 任意 hr:xxx；"hr:record:*" → 任意 hr:record:xxx
  if (perm.endsWith(':*')) {
    const prefix = perm.slice(0, -1);
    return permissions.some((p) => typeof p === 'string' && p.startsWith(prefix));
  }
  return false;
}

/** 是否具备任意招聘权限 */
export function hasAnyHrPermission(permissions: string[]): boolean {
  return hasPermission('hr:*', permissions);
}
