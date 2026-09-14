import { useState } from 'react';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { ProLayout, PageContainer } from '@ant-design/pro-components';
import { LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { getIconComponent } from '@/utils/iconMap';
import { Dropdown, theme } from 'antd';
import { useSelector, useDispatch } from 'react-redux';
import { logout } from '@/store/slices/userSlice';
import { resetMenus } from '@/store/slices/menuSlice';
import type { RootState, AppDispatch } from '@/store';
import type { MenuTree } from '@/types/menu';
import avatarPng from '@/assets/avatar.png';

function convertMenusToRoute(menus: MenuTree[], parentPath = ''): any[] {
  return menus.map((item) => {
    const cleanPath = (item.path || '').replace(/^\//, '');
    const relativePath =
      parentPath && cleanPath.startsWith(parentPath + '/') ? cleanPath.slice(parentPath.length + 1) : cleanPath;
    const fullPath = parentPath ? `${parentPath}/${relativePath}` : relativePath;
    const IconComp = item.icon ? getIconComponent(item.icon) : undefined;
    return {
      path: relativePath || undefined,
      name: item.menuName,
      icon: IconComp ? <IconComp /> : undefined,
      children: item.children?.length ? convertMenusToRoute(item.children, fullPath) : undefined,
    };
  });
}

const HeaderLogo = (
  <div
    className='flex h-8 w-8 items-center justify-center rounded'
    style={{ background: 'linear-gradient(135deg, var(--ant-color-primary), var(--ant-color-primary-hover))' }}
  >
    <span className='text-base font-bold text-white'>B</span>
  </div>
);

export default function BasicLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch<AppDispatch>();
  const menus = useSelector((state: RootState) => state.menu.menus);
  const userInfo = useSelector((state: RootState) => state.user.userInfo);
  const { token } = theme.useToken();

  const route = {
    path: '/',
    routes: convertMenusToRoute(menus),
  };

  const handleLogout = () => {
    dispatch(logout());
    dispatch(resetMenus());
    navigate('/login', { replace: true });
  };

  return (
    <ProLayout
      title='Base Admin'
      layout='mix'
      collapsed={collapsed}
      onCollapse={setCollapsed}
      collapsedButtonRender={false}
      location={{ pathname: location.pathname }}
      route={route}
      token={{
        sider: {
          colorMenuBackground: '#ffffff',
          colorTextMenu: '#333',
          colorTextMenuSelected: token.colorPrimary,
          colorBgMenuItemSelected: token.colorPrimaryBg,
        },
        bgLayout: '#f5f6fa',
        header: { colorBgHeader: '#fff' },
        pageContainer: {
          paddingInlinePageContainerContent: 20,
          paddingBlockPageContainerContent: 20,
        },
      }}
      logo={HeaderLogo}
      headerTitleRender={(logo, title) => (
        <div
          onClick={() => navigate('/workbench')}
          className='flex cursor-pointer'
        >
          {logo}
          {title}
        </div>
      )}
      menuItemRender={(item, dom) => {
        const targetPath = item.path || '/workbench';
        const normalizedPath = targetPath.startsWith('/') ? targetPath : `/${targetPath}`;
        return <Link to={normalizedPath}>{dom}</Link>;
      }}
      avatarProps={{
        icon: (
          <img
            src={avatarPng}
            alt='avatar'
          />
        ),
        title: userInfo?.nickname || userInfo?.username,
        render: (_, defaultDom) => (
          <Dropdown
            menu={{
              items: [
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出登录',
                  onClick: handleLogout,
                },
              ],
            }}
          >
            {defaultDom}
          </Dropdown>
        ),
      }}
      menuFooterRender={() => {
        return (
          <div
            className='flex h-10 items-center justify-center gap-2 border-t border-gray-100'
            onClick={() => setCollapsed(!collapsed)}
          >
            {collapsed ? (
              <MenuUnfoldOutlined style={{ fontSize: '20px' }} />
            ) : (
              <MenuFoldOutlined style={{ fontSize: '20px' }} />
            )}
            {collapsed ? null : <span>收起</span>}
          </div>
        );
      }}
    >
      <PageContainer pageHeaderRender={false}>
        <Outlet />
      </PageContainer>
    </ProLayout>
  );
}
