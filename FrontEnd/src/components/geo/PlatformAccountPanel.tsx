import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoPlatformAccountApi,
  deleteGeoPlatformAccountApi,
  deleteGeoPlatformAccountBatchApi,
  getGeoOwnerOptionsApi,
  getGeoPlatformAccountListApi,
  getGeoPlatformOptionsApi,
  updateGeoPlatformAccountApi,
} from '@/api/geo';
import type { GeoOwnerOption, GeoPlatform, GeoPlatformAccount } from '@/types/geo';
import { GEO_PLATFORM_ACCOUNT_STATUS, GEO_PLATFORM_LOGIN_METHODS, GEO_PLATFORM_VERIFY_METHODS } from '@/constants/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';
import { browserLoginGeoAutoApi } from '@/api/geoAuto';
import { formatDateTime } from '@/utils/datetime';

const YES_NO = [
  { label: '是', value: 1 },
  { label: '否', value: 0 },
];

/** v28 浏览器自动监测登录方式 */
const BROWSER_LOGIN_TYPES = [
  { label: '账号密码模拟登录', value: 'PASSWORD' },
  { label: '人工导出登录态（STORAGE_STATE）', value: 'STORAGE_STATE' },
];

const BROWSER_LOGIN_STATUS: Record<string, { label: string; color: string }> = {
  READY: { label: '就绪', color: 'success' },
  FAILED: { label: '登录失败', color: 'error' },
  EXPIRED: { label: '已过期', color: 'warning' },
  NONE: { label: '未登录', color: 'default' },
};

type Props = {
  /** 固定平台时隐藏平台筛选，新增默认挂到该平台 */
  fixedPlatformId?: number;
  fixedPlatformName?: string;
  /** 嵌入弹窗时压缩高度 */
  embedded?: boolean;
};

const PlatformAccountPanel = memo(function PlatformAccountPanel({
  fixedPlatformId,
  fixedPlatformName,
  embedded = false,
}: Props) {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoPlatformAccount | null>(null);
  const [platforms, setPlatforms] = useState<GeoPlatform[]>([]);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);

  useEffect(() => {
    void Promise.all([getGeoPlatformOptionsApi(), getGeoOwnerOptionsApi()]).then(([plats, users]) => {
      setPlatforms(plats ?? []);
      setOwners(users ?? []);
    });
  }, []);

  const platformOptions = useMemo(
    () =>
      platforms.map((p) => ({
        label: `${p.platformName}${p.platformType ? `（${p.platformType}）` : ''}`,
        value: p.id,
      })),
    [platforms],
  );
  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);

  const columns: ProColumnType<GeoPlatformAccount>[] = [
    {
      title: '平台',
      dataIndex: 'platformId',
      width: 160,
      valueType: 'select',
      hideInTable: !!fixedPlatformId,
      search: !fixedPlatformId,
      hideInForm: !!fixedPlatformId,
      fieldProps: {
        options: platformOptions,
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        disabled: !!fixedPlatformId,
      },
      formItemProps: fixedPlatformId ? undefined : { rules: [{ required: true, message: '请选择平台' }] },
      render: (_, r) => r.platformName || '-',
    },
    {
      title: '账号',
      dataIndex: 'account',
      width: 140,
      formItemProps: { rules: [{ required: true, message: '请输入账号' }] },
      ellipsis: true,
    },
    {
      title: '密码',
      dataIndex: 'password',
      width: 120,
      search: false,
      valueType: 'password',
      fieldProps: {
        placeholder: editing?.hasPassword ? '留空则不修改' : '请输入密码',
        autoComplete: 'new-password',
      },
      render: (_, r) =>
        r.hasPassword ? <span className='font-mono text-sm'>{r.passwordMasked}</span> : <Tag>未设置</Tag>,
    },
    {
      title: '平台昵称',
      dataIndex: 'accountNickname',
      width: 120,
      search: false,
      ellipsis: true,
    },
    {
      title: '管理人',
      dataIndex: 'managerUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        options: userOptions,
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
      },
      render: (_, r) => r.managerName || '-',
    },
    {
      title: '当前持有人',
      dataIndex: 'holderUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        options: userOptions,
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
      },
      render: (_, r) => r.holderName || '-',
    },
    {
      title: '开通人',
      dataIndex: 'openerUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        options: userOptions,
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
      },
      render: (_, r) => r.openerName || '-',
    },
    {
      title: '是否充值',
      dataIndex: 'recharged',
      width: 100,
      valueType: 'select',
      fieldProps: { options: YES_NO, allowClear: true },
      render: (_, r) => (r.recharged === 1 ? <Tag color='success'>是</Tag> : <Tag>否</Tag>),
    },
    {
      title: '开通时间',
      dataIndex: 'openTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '到期时间',
      dataIndex: 'expireTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '登录方式',
      dataIndex: 'loginMethod',
      width: 120,
      valueType: 'select',
      fieldProps: { options: [...GEO_PLATFORM_LOGIN_METHODS], allowClear: true },
    },
    {
      title: '是否验证',
      dataIndex: 'verified',
      width: 100,
      valueType: 'select',
      fieldProps: { options: YES_NO, allowClear: true },
      render: (_, r) => (r.verified === 1 ? <Tag color='processing'>已验证</Tag> : <Tag>未验证</Tag>),
    },
    {
      title: '验证方式',
      dataIndex: 'verifyMethod',
      width: 120,
      valueType: 'select',
      fieldProps: { options: [...GEO_PLATFORM_VERIFY_METHODS], allowClear: true },
      search: false,
    },
    {
      title: '绑定手机',
      dataIndex: 'bindPhone',
      width: 130,
      search: false,
      hideInTable: true,
    },
    {
      title: '绑定邮箱',
      dataIndex: 'bindEmail',
      width: 160,
      search: false,
      hideInTable: true,
      ellipsis: true,
    },
    {
      title: '账号状态',
      dataIndex: 'accountStatus',
      width: 100,
      valueType: 'select',
      fieldProps: { options: [...GEO_PLATFORM_ACCOUNT_STATUS], allowClear: true },
      render: (_, r) => {
        const s = r.accountStatus || '正常';
        const color = s === '正常' ? 'success' : s === '过期' ? 'warning' : 'default';
        return <Tag color={color}>{s}</Tag>;
      },
    },
    {
      title: '浏览器监测',
      dataIndex: 'browserEnabled',
      width: 110,
      valueType: 'select',
      fieldProps: { options: YES_NO, allowClear: false },
      render: (_, r) =>
        r.browserEnabled === 1 ? <Tag color='processing'>已启用</Tag> : <Tag>未启用</Tag>,
    },
    {
      title: '浏览器登录方式',
      dataIndex: 'browserLoginType',
      width: 180,
      valueType: 'select',
      fieldProps: { options: BROWSER_LOGIN_TYPES, allowClear: false },
      search: false,
      render: (_, r) =>
        r.browserLoginType === 'STORAGE_STATE' ? '人工登录态' : '账号密码登录',
    },
    {
      title: '登录态',
      dataIndex: 'browserLoginStatus',
      width: 110,
      search: false,
      hideInForm: true,
      render: (_, r) => {
        const meta = BROWSER_LOGIN_STATUS[r.browserLoginStatus || 'NONE'] || BROWSER_LOGIN_STATUS.NONE;
        return (
          <Tag color={meta.color} title={r.browserLoginError || undefined}>
            {meta.label}
          </Tag>
        );
      },
    },
    {
      title: '浏览器登录时间',
      dataIndex: 'browserLoginAt',
      width: 170,
      search: false,
      hideInForm: true,
      hideInTable: true,
      render: (_, r) => (r.browserLoginAt ? formatDateTime(r.browserLoginAt) : '-'),
    },
    {
      title: '最近登录',
      dataIndex: 'lastLoginTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
      hideInTable: true,
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      width: 80,
      valueType: 'digit',
      search: false,
      hideInTable: true,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      search: false,
      ellipsis: true,
      valueType: 'textarea',
    },
    createTimeRangeColumn<GeoPlatformAccount>(),
    createTimeDisplayColumn<GeoPlatformAccount>(),
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:platformAccount:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'browserLogin',
              label: '模拟登录',
              perm: 'geo:platformAccount:edit',
              disabled: record.browserEnabled !== 1,
              onClick: async () => {
                const hide = message.loading('正在用平台账号在服务器模拟登录…', 0);
                try {
                  const res = await browserLoginGeoAutoApi({
                    platform: record.platformName || record.account,
                    accountId: record.id,
                    forceRefresh: true,
                  });
                  if (res.success) {
                    message.success(`登录态已刷新（${res.source || 'READY'}）`);
                  } else {
                    message.warning(res.error || '模拟登录未完成，请查看诊断信息');
                  }
                  actionRef.current?.reload();
                } catch {
                  message.error('模拟登录请求失败');
                } finally {
                  hide();
                }
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:platformAccount:delete',
              confirmTitle: '确定删除该平台账号？',
              onClick: async () => {
                await deleteGeoPlatformAccountApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
  ];

  return (
    <>
      <BaseProTable<GeoPlatformAccount>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle={fixedPlatformId ? `${fixedPlatformName || '平台'} · 账号` : '平台账号管理'}
        search={embedded ? { defaultCollapsed: true } : undefined}
        scroll={embedded ? { x: 1400, y: 420 } : { x: 1400 }}
        request={async (params) => {
          const res = await getGeoPlatformAccountListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            platformId: fixedPlatformId ?? params.platformId,
            account: params.account,
            managerUserId: params.managerUserId,
            holderUserId: params.holderUserId,
            openerUserId: params.openerUserId,
            recharged: params.recharged,
            verified: params.verified,
            accountStatus: params.accountStatus,
            loginMethod: params.loginMethod,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          currentPageKeysRef.current = new Set(res.rows.map((r) => r.id));
          return { data: res.rows, success: true, total: res.total };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, _rows, { type }) => {
            if (type === 'none') {
              return setSelectedRowKeys([]);
            }
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='geo:platformAccount:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的账号');
                return;
              }
              Modal.confirm({
                title: '批量删除平台账号',
                content: `确定要删除选中的 ${selectedRowKeys.length} 个账号吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoPlatformAccountBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 个账号`);
                  setSelectedRowKeys([]);
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:platformAccount:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增账号
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑平台账号' : '新增平台账号'}
        columns={columns as any}
        open={open}
        onOpenChange={(v: boolean) => {
          setOpen(v);
          if (!v) setEditing(null);
        }}
        modalProps={{ width: 860 }}
        initialValues={
          editing
            ? {
                ...editing,
                password: undefined,
                recharged: editing.recharged ?? 0,
                verified: editing.verified ?? 0,
                accountStatus: editing.accountStatus || '正常',
                browserEnabled: editing.browserEnabled ?? 0,
                browserLoginType: editing.browserLoginType || 'PASSWORD',
              }
            : {
                platformId: fixedPlatformId,
                sortOrder: 0,
                recharged: 0,
                verified: 0,
                accountStatus: '正常',
                loginMethod: '账号密码',
                browserEnabled: 0,
                browserLoginType: 'PASSWORD',
              }
        }
        onFinish={async (values) => {
          const payload = {
            ...values,
            platformId: fixedPlatformId ?? values.platformId,
            account: values.account,
            password: values.password || undefined,
            recharged: values.recharged === 1 || values.recharged === true ? 1 : 0,
            verified: values.verified === 1 || values.verified === true ? 1 : 0,
            accountStatus: values.accountStatus || '正常',
            browserEnabled: values.browserEnabled === 1 || values.browserEnabled === true ? 1 : 0,
            browserLoginType: values.browserLoginType || 'PASSWORD',
          };
          if (editing) {
            await updateGeoPlatformAccountApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoPlatformAccountApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default PlatformAccountPanel;
