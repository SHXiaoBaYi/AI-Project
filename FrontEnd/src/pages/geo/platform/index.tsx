import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PlatformAccountPanel from '@/components/geo/PlatformAccountPanel';
import {
  createGeoPlatformApi,
  deleteGeoPlatformApi,
  deleteGeoPlatformBatchApi,
  getGeoPlatformListApi,
  updateGeoPlatformApi,
} from '@/api/geo';
import type { GeoPlatform } from '@/types/geo';
import { GEO_PLATFORM_TYPE_DEFAULT, GEO_PLATFORM_TYPES } from '@/constants/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const PlatformPage = memo(function PlatformPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoPlatform | null>(null);
  const [accountOpen, setAccountOpen] = useState(false);
  const [accountPlatform, setAccountPlatform] = useState<GeoPlatform | null>(null);

  const columns: ProColumnType<GeoPlatform>[] = [
    {
      title: '平台名称',
      dataIndex: 'platformName',
      formItemProps: { rules: [{ required: true, message: '请输入平台名称' }] },
    },
    {
      title: '平台类型',
      dataIndex: 'platformType',
      width: 140,
      valueType: 'select',
      valueEnum: {
        AI平台: { text: 'AI平台' },
        内容发布平台: { text: '内容发布平台' },
      },
      fieldProps: { options: [...GEO_PLATFORM_TYPES] },
      formItemProps: { rules: [{ required: true, message: '请选择平台类型' }] },
      render: (_, r) => r.platformType || GEO_PLATFORM_TYPE_DEFAULT,
    },
    {
      title: '登录地址',
      dataIndex: 'loginUrl',
      width: 220,
      search: false,
      ellipsis: true,
      fieldProps: { placeholder: '如 https://www.xxx.com/login' },
      render: (_, r) => r.loginUrl || '-',
    },
    { title: '排序', dataIndex: 'sortOrder', search: false, valueType: 'digit' },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
    createTimeRangeColumn<GeoPlatform>(),
    createTimeDisplayColumn<GeoPlatform>(),
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'accounts',
              label: '账号管理',
              perm: 'geo:platformAccount:list',
              onClick: () => {
                setAccountPlatform(record);
                setAccountOpen(true);
              },
            },
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:platform:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:platform:delete',
              confirmTitle: '确定删除该平台？',
              onClick: async () => {
                await deleteGeoPlatformApi(record.id);
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
      <BaseProTable<GeoPlatform>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='平台管理'
        scroll={{ x: 1000 }}
        request={async (params) => {
          const res = await getGeoPlatformListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            platformName: params.platformName,
            platformType: params.platformType,
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
            perm='geo:platform:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的平台');
                return;
              }
              Modal.confirm({
                title: '批量删除平台',
                content: `确定要删除选中的 ${selectedRowKeys.length} 个平台吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoPlatformBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 个平台`);
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
            perm='geo:platform:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增平台
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑平台' : '新增平台'}
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={editing ?? { sortOrder: 0, platformType: GEO_PLATFORM_TYPE_DEFAULT, loginUrl: '' }}
        onFinish={async (values) => {
          const payload = {
            ...values,
            platformType: values.platformType || GEO_PLATFORM_TYPE_DEFAULT,
            loginUrl: values.loginUrl || '',
          };
          if (editing) {
            await updateGeoPlatformApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoPlatformApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />

      <Modal
        title={accountPlatform ? `${accountPlatform.platformName} · 账号管理` : '账号管理'}
        open={accountOpen}
        onCancel={() => {
          setAccountOpen(false);
          setAccountPlatform(null);
        }}
        footer={null}
        width={1100}
        destroyOnHidden
        styles={{ body: { paddingTop: 8 } }}
      >
        {accountPlatform ? (
          <PlatformAccountPanel
            key={accountPlatform.id}
            fixedPlatformId={accountPlatform.id}
            fixedPlatformName={accountPlatform.platformName}
            embedded
          />
        ) : null}
      </Modal>
    </>
  );
});

export default PlatformPage;
