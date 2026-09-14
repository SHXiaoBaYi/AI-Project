import { lazy, Suspense, type LazyExoticComponent } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { Result, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import BasicLayout from '@/layouts';
import componentMap from './componentMap';
import type { MenuTree } from '@/types/menu';
import { PageLoading } from '@ant-design/pro-components';

// eslint-disable-next-line react-refresh/only-export-components
function PagePlaceholder() {
  const navigate = useNavigate();
  return (
    <Result
      status='warning'
      title='页面开发中'
      subTitle='该菜单对应的页面组件尚未创建，请在 src/pages/ 下添加对应路径的 index.tsx 文件'
      extra={
        <Button
          type='primary'
          onClick={() => navigate('/workbench')}
        >
          返回首页
        </Button>
      }
    />
  );
}

const Login = lazy(() => import('@/pages/login'));
const NotFound = lazy(() => import('@/pages/404'));
const Demo = lazy(() => import('@/pages/test/demo'));

function withSuspense(Component: LazyExoticComponent<any>) {
  return (
    <Suspense fallback={<PageLoading />}>
      <Component />
    </Suspense>
  );
}

function buildDynamicRoutes(menus: MenuTree[], parentPath = ''): any[] {
  if (!menus || !Array.isArray(menus)) return [];
  return menus
    .filter((menu) => menu.path)
    .map((menu) => {
      const cleanPath = menu.path.replace(/^\//, '');
      const relativePath =
        parentPath && cleanPath.startsWith(parentPath + '/') ? cleanPath.slice(parentPath.length + 1) : cleanPath;
      const fullPath = parentPath ? `${parentPath}/${relativePath}` : relativePath;
      const elementKey = '/' + fullPath;
      const hasComponent = !!componentMap[elementKey];
      const hasChildren = menu.children && menu.children.length > 0;

      if (!hasComponent && hasChildren) {
        return {
          path: relativePath,
          children: buildDynamicRoutes(menu.children, fullPath),
        };
      }

      return {
        path: relativePath,
        element: hasComponent ? withSuspense(componentMap[elementKey]) : <PagePlaceholder />,
        children: hasChildren ? buildDynamicRoutes(menu.children, fullPath) : undefined,
      };
    });
}

export function createAppRouter(menus: MenuTree[], isLoggedIn: boolean) {
  if (!isLoggedIn) {
    return createBrowserRouter([
      {
        path: '/login',
        element: <Login />,
      },
      {
        path: '*',
        element: (
          <Navigate
            to='/login'
            replace
          />
        ),
      },
    ]);
  }

  const dynamicRoutes = buildDynamicRoutes(menus);

  return createBrowserRouter([
    {
      path: '/login',
      element: (
        <Navigate
          to='/'
          replace
        />
      ),
    },
    {
      path: '/',
      element: <BasicLayout />,
      children: [
        {
          index: true,
          element: (
            <Navigate
              to='/workbench'
              replace
            />
          ),
        },
        // 测试页面需要放到动态路由前
        {
          path: '/demo',
          element: withSuspense(Demo),
        },
        ...dynamicRoutes,
        { path: '*', element: <NotFound /> },
      ],
    },
  ]);
}
