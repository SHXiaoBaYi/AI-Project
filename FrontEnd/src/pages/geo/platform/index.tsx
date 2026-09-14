import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { createGeoPlatformApi, deleteGeoPlatformApi, getGeoPlatformListApi, updateGeoPlatformApi } from '@/api/geo';
import type { GeoPlatform } from '@/types/geo';

const PlatformPage = memo(function PlatformPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoPlatform | null>(null);

  const columns: ProColumnType<GeoPlatform>[] = [
    {
      title: '平台名称',
      dataIndex: 'platformName',
      formItemProps: { rules: [{ required: true, message: '请输入平台名称' }] },
    },
    { title: '排序', dataIndex: 'sortOrder', search: false, valueType: 'digit' },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, record) => (
        <ActionButtons
          items={[
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
        request={async (params) => {
          const res = await getGeoPlatformListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            platformName: params.platformName,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() => [
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
        initialValues={editing ?? { sortOrder: 0 }}
        onFinish={async (values) => {
          if (editing) {
            await updateGeoPlatformApi({ ...values, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoPlatformApi(values);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default PlatformPage;
