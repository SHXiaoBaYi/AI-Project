import { useEffect, useRef } from 'react';
import { RouterProvider } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { Spin, App as AntdApp, ConfigProvider } from 'antd';
import { createAppRouter } from '@/router';
import { getInfo } from '@/store/slices/userSlice';
import { StaticFunctionCapture } from '@/store/slices/staticFunctionSlice';
import { checkSessionApi } from '@/api/auth';
import type { RootState, AppDispatch } from '@/store';

export default function App() {
  const dispatch = useDispatch<AppDispatch>();
  const themeConfig = useSelector((state: RootState) => state.theme.config);
  const token = useSelector((state: RootState) => state.user.token);
  const menus = useSelector((state: RootState) => state.menu.menus);
  const routesLoaded = useSelector((state: RootState) => state.menu.routesLoaded);

  const fetchingRef = useRef(false);

  useEffect(() => {
    if (token && !routesLoaded && !fetchingRef.current) {
      fetchingRef.current = true;
      dispatch(getInfo()).finally(() => {
        fetchingRef.current = false;
      });
    }
  }, [token, routesLoaded, dispatch]);

  // 定时探活：被其他设备强制下线后尽快跳回登录页
  useEffect(() => {
    if (!token) {
      return;
    }
    const timer = window.setInterval(() => {
      checkSessionApi().catch(() => {
        // 4011 / 401 由 request 拦截器处理跳转
      });
    }, 10000);
    return () => window.clearInterval(timer);
  }, [token]);

  const isLoggedIn = !!token && routesLoaded;
  const showLoading = token && !routesLoaded;
  const router = createAppRouter(menus, isLoggedIn);

  return (
    <ConfigProvider theme={themeConfig}>
      <AntdApp message={{ maxCount: 1 }}>
        <StaticFunctionCapture />
        {showLoading ? (
          <div className='flex min-h-screen items-center justify-center'>
            <Spin size='large' />
          </div>
        ) : (
          <RouterProvider router={router} />
        )}
      </AntdApp>
    </ConfigProvider>
  );
}
