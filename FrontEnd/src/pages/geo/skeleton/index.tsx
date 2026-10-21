import { memo, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Tag } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoSkeletonApi,
  deleteGeoSkeletonApi,
  deleteGeoSkeletonBatchApi,
  getGeoSkeletonListApi,
  updateGeoSkeletonApi,
} from '@/api/geo';
import type { GeoArticleSkeleton } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const DEFAULT_SCHEMA = `{
  "lead": "导语1-2段直答目标问题",
  "sections": [
    { "role": "argument", "countHint": "2-4" },
    { "role": "evidence", "optional": true },
    { "role": "close", "required": true }
  ],
  "imagePolicy": "每节0张起，由模型判定是否插图"
}`;

const SkeletonPage = memo(function SkeletonPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoArticleSkeleton | null>(null);

  const formColumns: ProColumnType<GeoArticleSkeleton>[] = useMemo(
    () => [
      {
        title: '编码',
        dataIndex: 'code',
        formItemProps: { rules: [{ required: true, message: '请填写编码' }] },
        fieldProps: { placeholder: '如 qa_sections_v1', disabled: !!editing },
      },
      {
        title: '名称',
        dataIndex: 'name',
        formItemProps: { rules: [{ required: true, message: '请填写名称' }] },
      },
      {
        title: '启用',
        dataIndex: 'enabled',
        valueType: 'switch',
        fieldProps: { checkedChildren: '开', unCheckedChildren: '关' },
        initialValue: 1,
        convertValue: (v) => (v === true || v === 1 ? 1 : 0),
        transform: (v) => ({ enabled: v === true || v === 1 ? 1 : 0 }),
      },
      {
        title: '排序',
        dataIndex: 'sortOrder',
        valueType: 'digit',
        initialValue: 0,
      },
      {
        title: '章节骨架 JSON',
        dataIndex: 'sectionSchema',
        valueType: 'textarea',
        colProps: { span: 24 },
        formItemProps: { rules: [{ required: true, message: '请填写骨架 JSON' }] },
        fieldProps: { rows: 12, className: 'font-mono text-xs' },
        tooltip: '须为合法 JSON，描述导语/小节角色/插图策略等',
      },
    ],
    [editing],
  );

  const columns: ProColumnType<GeoArticleSkeleton>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        key: 'option',
        width: 120,
        fixed: 'left',
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                perm: 'geo:skeleton:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                perm: 'geo:skeleton:delete',
                confirmTitle: '确定删除该骨架？',
                onClick: async () => {
                  await deleteGeoSkeletonApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
      { title: '名称', dataIndex: 'name', ellipsis: true },
      { title: '编码', dataIndex: 'code', width: 160, copyable: true },
      {
        title: '关键词',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: { placeholder: '名称/编码' },
      },
      {
        title: '启用',
        dataIndex: 'enabled',
        width: 80,
        valueType: 'select',
        valueEnum: {
          1: { text: '启用', status: 'Success' },
          0: { text: '停用', status: 'Default' },
        },
        render: (_, r) => (r.enabled === 1 ? <Tag color='success'>启用</Tag> : <Tag>停用</Tag>),
      },
      {
        title: '骨架摘要',
        dataIndex: 'sectionSchema',
        search: false,
        ellipsis: true,
        width: 360,
      },
      {
        title: '排序',
        dataIndex: 'sortOrder',
        search: false,
        width: 70,
      },
      createTimeDisplayColumn(),
      createTimeRangeColumn(),
    ],
    [message],
  );

  return (
    <div className='flex h-full flex-col gap-3 p-4'>
      <div className='rounded-lg border border-violet-100 bg-violet-50/70 px-3 py-2 text-xs text-violet-900'>
        骨架描述成文结构（导语、分论点、收束、插图策略）。流水线会优先取已启用且排序靠前的骨架。
      </div>
      <BaseProTable<GeoArticleSkeleton>
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        scroll={{ x: 1100 }}
        rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            icon={<PlusOutlined />}
            perm='geo:skeleton:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增骨架
          </PermissionButton>,
          <PermissionButton
            key='batch'
            danger
            perm='geo:skeleton:delete'
            disabled={!selectedRowKeys.length}
            onClick={() => {
              modal.confirm({
                title: `删除选中的 ${selectedRowKeys.length} 条？`,
                onOk: async () => {
                  await deleteGeoSkeletonBatchApi(selectedRowKeys.map(Number));
                  message.success('已删除');
                  setSelectedRowKeys([]);
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
        ]}
        request={async (params) => {
          const res = await getGeoSkeletonListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            keyword: params.keyword || params.name,
            enabled: params.enabled != null ? Number(params.enabled) : undefined,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          return { data: res.rows, total: res.total, success: true };
        }}
      />
      <TableModal
        readonly={false}
        open={open}
        onOpenChange={(v) => {
          setOpen(v);
          if (!v) setEditing(null);
        }}
        title={editing ? '编辑文章骨架' : '新增文章骨架'}
        columns={formColumns as any}
        initialValues={
          editing
            ? { ...editing, enabled: editing.enabled === 1 }
            : { enabled: true, sortOrder: 0, sectionSchema: DEFAULT_SCHEMA }
        }
        onFinish={async (values) => {
          const payload = {
            ...values,
            enabled: values.enabled === true || values.enabled === 1 ? 1 : 0,
          };
          if (editing?.id) {
            await updateGeoSkeletonApi({ ...payload, id: editing.id, code: editing.code });
            message.success('已更新');
          } else {
            await createGeoSkeletonApi(payload);
            message.success('已新增');
          }
          setOpen(false);
          setEditing(null);
          actionRef.current?.reload();
          return true;
        }}
      />
    </div>
  );
});

export default SkeletonPage;
