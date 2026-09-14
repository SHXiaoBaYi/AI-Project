const ADMIN_PERM = '*:*:*';

export function hasPermission(perm: string, permissions: string[]): boolean {
  if (permissions.includes(ADMIN_PERM)) return true;
  return permissions.includes(perm);
}
