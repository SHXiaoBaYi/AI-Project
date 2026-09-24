import { useSelector } from 'react-redux';
import type { RootState } from '@/store';
import { hasPermission } from '@/utils/permission';
import { useMemo } from 'react';

export function usePermission() {
  const permissions = useSelector((state: RootState) => {
    if (state.user.permissions?.length) return state.user.permissions;
    return state.user.userInfo?.permissions ?? [];
  });

  return useMemo(
    () => ({
      has: (perm: string) => hasPermission(perm, permissions),
      hasHr: () => hasPermission('hr:*', permissions),
    }),
    [permissions],
  );
}
