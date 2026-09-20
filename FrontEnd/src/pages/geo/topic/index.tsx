import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoTopicApi,
  deleteGeoTopicApi,
  deleteGeoTopicBatchApi,
  getGeoTopicListApi,
  updateGeoTopicApi,
} from '@/api/geo';
import type { GeoTopic } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const TopicPage = memo(function TopicPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoTopic | null>(null);

  const columns: ProColumnType<GeoTopic>[] = [
    {
      title: '话题名称',
      dataIndex: 'topicName',
      formItemProps: { rules: [{ required: true, message: '请输入话题名称' }] },
    },
    { title: '开始优化时间', dataIndex: 'optimizeWeek', search: false },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
    createTimeRangeColumn<GeoTopic>(),
    createTimeDisplayColumn<GeoTopic>(),
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
              perm: 'geo:topic:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:topic:delete',
              confirmTitle: '确定删除该话题？',
              onClick: async () => {
                await deleteGeoTopicApi(record.id);
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
      <BaseProTable<GeoTopic>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='话题管理'
        scroll={{ x: 800 }}
        request={async (params) => {
          const res = await getGeoTopicListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            topicName: params.topicName,
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
            perm='geo:topic:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的话题');
                return;
              }
              Modal.confirm({
                title: '批量删除话题',
                content: `确定要删除选中的 ${selectedRowKeys.length} 个话题吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoTopicBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 个话题`);
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
            perm='geo:topic:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增话题
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑话题' : '新增话题'}
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={editing ?? {}}
        onFinish={async (values) => {
          if (editing) {
            await updateGeoTopicApi({ ...values, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoTopicApi(values);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default TopicPage;
