import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { Button, Result, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import type { RootState } from '@/store';
import { getEcomAccessApi } from '@/api/ecom';

const NAV = [
  { path: '/ecom/data', label: '数据中心' },
  { path: '/ecom/shops', label: '基础信息' },
  { path: '/ecom/board', label: '数据看板' },
  { path: '/ecom/acl', label: '授权管理', manageOnly: true },
];

export default function EcomLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const userInfo = useSelector((s: RootState) => s.user.userInfo);
  const [loading, setLoading] = useState(true);
  const [allowed, setAllowed] = useState(false);
  const [canReturnMain, setCanReturnMain] = useState(false);
  const [canManageAcl, setCanManageAcl] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const res = await getEcomAccessApi();
        if (!cancelled) {
          setAllowed(!!res?.allowed);
          setCanReturnMain(!!res?.canReturnMain);
          setCanManageAcl(!!res?.canManageAcl);
        }
      } catch {
        if (!cancelled) setAllowed(false);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [userInfo?.userId]);

  if (loading) {
    return (
      <div className='flex min-h-screen items-center justify-center'>
        <Spin size='large' />
      </div>
    );
  }

  if (!allowed) {
    return (
      <div className='flex min-h-screen items-center justify-center p-6'>
        <Result
          status='403'
          title='无权访问电商运营'
          subTitle='该模块不出现在系统菜单中；需本机、Bella 或 Bella 授权账号'
          extra={
            <Button
              type='primary'
              onClick={() => navigate('/workbench')}
            >
              返回工作台
            </Button>
          }
        />
      </div>
    );
  }

  const navItems = NAV.filter((item) => !item.manageOnly || canManageAcl);

  return (
    <div className='min-h-screen bg-[#f5f6fa]'>
      <header className='flex h-14 items-center justify-between border-b border-neutral-200 bg-white px-5'>
        <div className='flex items-center gap-6'>
          <div className='text-base font-semibold text-neutral-900'>电商运营</div>
          <nav className='flex flex-wrap items-center gap-1'>
            {navItems.map((item) => {
              const active = location.pathname.startsWith(item.path);
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={
                    active
                      ? 'rounded bg-[var(--ant-color-primary-bg)] px-3 py-1.5 text-sm font-medium text-[var(--ant-color-primary)]'
                      : 'rounded px-3 py-1.5 text-sm text-neutral-600 hover:bg-neutral-100'
                  }
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>
        {canReturnMain ? (
          <Button
            type='primary'
            onClick={() => navigate('/workbench')}
          >
            返回主系统
          </Button>
        ) : null}
      </header>
      <main className='mx-auto max-w-7xl p-5'>
        <Outlet />
      </main>
    </div>
  );
}
