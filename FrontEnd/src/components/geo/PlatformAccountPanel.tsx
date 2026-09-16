import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoPlatformAccountApi,
  deleteGeoPlatformAccountApi,
  getGeoOwnerOptionsApi,
  getGeoPlatformAccountListApi,
  getGeoPlatformOptionsApi,
  updateGeoPlatformAccountApi,
} from '@/api/geo';
import type { GeoOwnerOption, GeoPlatform, GeoPlatformAccount } from '@/types/geo';
import { GEO_PLATFORM_ACCOUNT_STATUS, GEO_PLATFORM_LOGIN_METHODS, GEO_PLATFORM_VERIFY_METHODS } from '@/constants/geo';

const YES_NO = [
  { label: '是', value: 1 },
  { label: '否', value: 0 },
];

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
      hideInSearch: !!fixedPlatformId,
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
    {
      title: '操作',
      valueType: 'option',
      width: 140,
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
        scroll={embedded ? { y: 420 } : undefined}
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
          });
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() => [
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
        onOpenChange={(v) => {
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
              }
            : {
                platformId: fixedPlatformId,
                sortOrder: 0,
                recharged: 0,
                verified: 0,
                accountStatus: '正常',
                loginMethod: '账号密码',
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
