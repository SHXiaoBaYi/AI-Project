import { useState } from 'react';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { ProLayout, PageContainer } from '@ant-design/pro-components';
import {
  CloudDownloadOutlined,
  CloudUploadOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SafetyCertificateOutlined,
  ShoppingOutlined,
} from '@ant-design/icons';
import { getIconComponent } from '@/utils/iconMap';
import { App, Button, Dropdown, Space, theme } from 'antd';
import { useSelector, useDispatch } from 'react-redux';
import { logout, logoutRemote } from '@/store/slices/userSlice';
import { resetMenus } from '@/store/slices/menuSlice';
import type { RootState, AppDispatch } from '@/store';
import type { MenuTree } from '@/types/menu';
import avatarPng from '@/assets/avatar.png';
import PageErrorBoundary from '@/components/PageErrorBoundary';
import { ExternalLinkProvider } from '@/components/ExternalLinkDrawer';
import { resolveMenuFullPath } from '@/utils/menuPath';
import { withBase } from '@/utils/basePath';
import { canSeeDataScopePage, canSeeEcomModule } from '@/utils/localhostAccess';
import { syncProdDataToTestApi, syncTestSchemaToProdApi } from '@/api/envSync';

function convertMenusToRoute(menus: MenuTree[], parentPath = ''): any[] {
  return menus.map((item) => {
    const clean = (item.path || '').replace(/^\//, '');
    const hasChildren = !!item.children?.length;
    const fullPath = resolveMenuFullPath(item.path, parentPath);
    const IconComp = item.icon ? getIconComponent(item.icon) : undefined;

    // 分组目录 geo/config：子节点仍按 geo 解析；顶层 geo/system：按其 fullPath
    const isGroupingOnly = clean.includes('/') && !!parentPath;
    const childParent = isGroupingOnly ? parentPath : fullPath;
    const leafPath = `/${clean.includes('/') ? clean : fullPath}`;

    return {
      // ProLayout 用 path/key 做 React key；目录必须有唯一 key，避免多个 undefined → ``
      key: item.menuId != null ? `menu-${item.menuId}` : leafPath,
      path: hasChildren ? `/${fullPath}` : leafPath,
      name: item.menuName,
      icon: IconComp ? <IconComp /> : undefined,
      children: hasChildren ? convertMenusToRoute(item.children, childParent) : undefined,
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
  const [syncingData, setSyncingData] = useState(false);
  const [syncingSchema, setSyncingSchema] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch<AppDispatch>();
  const menus = useSelector((state: RootState) => state.menu.menus);
  const userInfo = useSelector((state: RootState) => state.user.userInfo);
  const { token } = theme.useToken();
  const { modal, message } = App.useApp();

  const route = {
    path: '/',
    routes: convertMenusToRoute(menus),
  };

  const handleLogout = async () => {
    await dispatch(logoutRemote());
    dispatch(logout());
    dispatch(resetMenus());
    // 退出后 App 会重建整棵 Router，旧 navigate 无法可靠切页；与 401 踢下线一致走整页跳转
    window.location.href = withBase('/login');
  };

  const showDataScope = canSeeDataScopePage(userInfo?.username);
  const showEcom = canSeeEcomModule(userInfo?.username, userInfo?.ecomAllowed);
  const showEnvSync = !!userInfo?.envSyncEnabled;

  const runSyncData = () => {
    modal.confirm({
      title: '同步生产数据到测试',
      content: '将用生产库「数据」覆盖测试库（TRUNCATE 后导入），不修改表结构。测试环境当前数据会被清空，确定继续？',
      okText: '开始同步',
      okButtonProps: { danger: true },
      onOk: async () => {
        setSyncingData(true);
        try {
          const res = await syncProdDataToTestApi();
          message.success(res?.message || '数据同步完成');
        } finally {
          setSyncingData(false);
        }
      },
    });
  };

  const runSyncSchema = () => {
    modal.confirm({
      title: '同步测试表结构到生产',
      content:
        '将把测试库相对生产「多出的表/列」推送到生产（只增不删，不会删生产列/表）。请确认测试结构已验收。确定继续？',
      okText: '推送到生产',
      okButtonProps: { danger: true },
      onOk: async () => {
        setSyncingSchema(true);
        try {
          const res = await syncTestSchemaToProdApi();
          message.success(res?.message || '表结构同步完成');
        } finally {
          setSyncingSchema(false);
        }
      },
    });
  };

  const avatarMenuItems = [
    ...(showEcom
      ? [
          {
            key: 'ecom',
            icon: <ShoppingOutlined />,
            label: '电商运营',
            onClick: () => navigate('/ecom/data'),
          },
        ]
      : []),
    ...(showDataScope
      ? [
          {
            key: 'data-scope',
            icon: <SafetyCertificateOutlined />,
            label: '数据权限配置',
            onClick: () => navigate('/system/data-scope'),
          },
        ]
      : []),
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  return (
    <ProLayout
      title='小巴依(上海)'
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
      actionsRender={() =>
        showEnvSync ? (
          <Space
            size={8}
            className='mr-2'
          >
            <Button
              size='small'
              type='primary'
              ghost
              icon={<CloudDownloadOutlined />}
              loading={syncingData}
              disabled={syncingSchema}
              onClick={runSyncData}
            >
              同步生产数据
            </Button>
            <Button
              size='small'
              danger
              ghost
              icon={<CloudUploadOutlined />}
              loading={syncingSchema}
              disabled={syncingData}
              onClick={runSyncSchema}
            >
              同步表结构到生产
            </Button>
          </Space>
        ) : (
          []
        )
      }
      menuItemRender={(item, dom) => {
        // 目录只展开，不跳转（避免进 /geo/config 等无页面路由）
        if (item.children?.length) {
          return dom;
        }
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
        render: (_, defaultDom) => <Dropdown menu={{ items: avatarMenuItems }}>{defaultDom}</Dropdown>,
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
        <ExternalLinkProvider>
          <PageErrorBoundary key={location.pathname}>
            <Outlet />
          </PageErrorBoundary>
        </ExternalLinkProvider>
      </PageContainer>
    </ProLayout>
  );
}
