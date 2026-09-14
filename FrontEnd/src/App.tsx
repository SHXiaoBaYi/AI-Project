import { useEffect, useRef } from 'react';
import { RouterProvider } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { Spin, App as AntdApp, ConfigProvider } from 'antd';
import { createAppRouter } from '@/router';
import { getInfo } from '@/store/slices/userSlice';
import { StaticFunctionCapture } from '@/store/slices/staticFunctionSlice';
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
