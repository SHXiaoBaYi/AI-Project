import { useSelector } from 'react-redux';
import type { RootState } from '@/store';
import { hasPermission } from '@/utils/permission';
import { useMemo } from 'react';

export function usePermission() {
  const permissions = useSelector((state: RootState) => state.user.permissions);

  return useMemo(
    () => ({
      has: (perm: string) => hasPermission(perm, permissions),
    }),
    [permissions],
  );
}
