import { memo, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Tag } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoStylePackApi,
  deleteGeoStylePackApi,
  deleteGeoStylePackBatchApi,
  getGeoStylePackListApi,
  updateGeoStylePackApi,
} from '@/api/geo';
import type { GeoArticleStylePack } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const StylePackPage = memo(function StylePackPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoArticleStylePack | null>(null);

  const formColumns: ProColumnType<GeoArticleStylePack>[] = useMemo(
    () => [
      {
        title: '编码',
        dataIndex: 'code',
        formItemProps: { rules: [{ required: true, message: '请填写编码' }] },
        fieldProps: { placeholder: '如 zhihu_explain', disabled: !!editing },
      },
      {
        title: '名称',
        dataIndex: 'name',
        formItemProps: { rules: [{ required: true, message: '请填写名称' }] },
      },
      {
        title: '平台提示',
        dataIndex: 'platformHint',
        valueType: 'select',
        fieldProps: {
          allowClear: true,
          options: [
            { label: '知乎', value: 'zhihu' },
            { label: '头条', value: 'toutiao' },
            { label: '搜狐', value: 'sohu' },
            { label: '网易', value: 'netease' },
            { label: '通用', value: 'general' },
          ],
        },
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
        title: '语气节奏',
        dataIndex: 'toneGuide',
        valueType: 'textarea',
        colProps: { span: 24 },
        fieldProps: { rows: 3, placeholder: '先答结论再拆论据…' },
      },
      {
        title: '结构说明',
        dataIndex: 'structureGuide',
        valueType: 'textarea',
        colProps: { span: 24 },
        fieldProps: { rows: 3, placeholder: '导语 → h2 分论点 → 收束…' },
      },
      {
        title: '可摘句式',
        dataIndex: 'samplePhrases',
        valueType: 'textarea',
        colProps: { span: 24 },
        fieldProps: {
          rows: 3,
          placeholder: 'JSON 数组，或逗号分隔。成文时最多摘用 1～2 句',
        },
        tooltip: '如 ["先说结论：","落到选择上，可以看这几条"]',
      },
      {
        title: '品牌软约束',
        dataIndex: 'brandGuide',
        valueType: 'textarea',
        colProps: { span: 24 },
        fieldProps: { rows: 2, placeholder: '全文品牌名建议不超过3次…' },
      },
    ],
    [editing],
  );

  const columns: ProColumnType<GeoArticleStylePack>[] = useMemo(
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
                perm: 'geo:stylePack:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                perm: 'geo:stylePack:delete',
                confirmTitle: '确定删除该风格包？',
                onClick: async () => {
                  await deleteGeoStylePackApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
      { title: '名称', dataIndex: 'name', ellipsis: true },
      { title: '编码', dataIndex: 'code', width: 140, copyable: true },
      {
        title: '关键词',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: { placeholder: '名称/编码' },
      },
      {
        title: '平台',
        dataIndex: 'platformHint',
        width: 100,
        valueType: 'select',
        valueEnum: {
          zhihu: { text: '知乎' },
          toutiao: { text: '头条' },
          sohu: { text: '搜狐' },
          netease: { text: '网易' },
          general: { text: '通用' },
        },
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
        title: '语气',
        dataIndex: 'toneGuide',
        search: false,
        ellipsis: true,
        width: 220,
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
      <div className='rounded-lg border border-sky-100 bg-sky-50/80 px-3 py-2 text-xs text-sky-900'>
        风格包供 AI 成文学结构与语气；句式字段仅作样本，成文时最多摘用 1～2 句。品牌露出为软约束，不强制精确次数。
      </div>
      <BaseProTable<GeoArticleStylePack>
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
            perm='geo:stylePack:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增风格包
          </PermissionButton>,
          <PermissionButton
            key='batch'
            danger
            perm='geo:stylePack:delete'
            disabled={!selectedRowKeys.length}
            onClick={() => {
              modal.confirm({
                title: `删除选中的 ${selectedRowKeys.length} 条？`,
                onOk: async () => {
                  await deleteGeoStylePackBatchApi(selectedRowKeys.map(Number));
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
          const res = await getGeoStylePackListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            keyword: params.keyword || params.name,
            platformHint: params.platformHint,
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
        title={editing ? '编辑风格包' : '新增风格包'}
        columns={formColumns as any}
        initialValues={
          editing
            ? { ...editing, enabled: editing.enabled === 1 }
            : { enabled: true, sortOrder: 0, samplePhrases: '[]' }
        }
        onFinish={async (values) => {
          const payload = {
            ...values,
            enabled: values.enabled === true || values.enabled === 1 ? 1 : 0,
          };
          if (editing?.id) {
            await updateGeoStylePackApi({ ...payload, id: editing.id, code: editing.code });
            message.success('已更新');
          } else {
            await createGeoStylePackApi(payload);
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

export default StylePackPage;
