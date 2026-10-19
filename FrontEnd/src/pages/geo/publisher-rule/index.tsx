import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoPublisherRuleApi,
  deleteGeoPublisherRuleApi,
  deleteGeoPublisherRuleBatchApi,
  getGeoOwnerOptionsApi,
  getGeoPublisherRuleListApi,
  updateGeoPublisherRuleApi,
} from '@/api/geo';
import type { GeoContentPublisherRule, GeoOwnerOption } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const PublisherRulePage = memo(function PublisherRulePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoContentPublisherRule | null>(null);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);

  useEffect(() => {
    void getGeoOwnerOptionsApi().then((list) => setOwners(list ?? []));
  }, []);

  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);

  const columns: ProColumnType<GeoContentPublisherRule>[] = [
    {
      title: '发布人',
      dataIndex: 'publisherUserId',
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: userOptions,
        placeholder: '请选择发布人',
      },
      formItemProps: { rules: [{ required: true, message: '请选择发布人' }] },
      render: (_, record) => record.publisherName || '-',
    },
    {
      title: '默认发布人',
      dataIndex: 'isDefault',
      width: 120,
      valueType: 'switch',
      fieldProps: { checkedChildren: '是', unCheckedChildren: '否' },
      valueEnum: {
        1: { text: '是' },
        0: { text: '否' },
      },
      formItemProps: { rules: [{ required: true, message: '请选择是否默认' }] },
      render: (_, record) => (Number(record.isDefault) === 1 ? '是' : '否'),
    },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
    createTimeRangeColumn<GeoContentPublisherRule>(),
    createTimeDisplayColumn<GeoContentPublisherRule>(),
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
              perm: 'geo:publisherRule:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:publisherRule:delete',
              confirmTitle: '确定删除该发布人规则？',
              onClick: async () => {
                await deleteGeoPublisherRuleApi(record.id);
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
      <BaseProTable<GeoContentPublisherRule>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='发布人规则'
        scroll={{ x: 800 }}
        request={async (params) => {
          const isDefault =
            params.isDefault === true || params.isDefault === 1 || params.isDefault === '1'
              ? 1
              : params.isDefault === false || params.isDefault === 0 || params.isDefault === '0'
                ? 0
                : undefined;
          const res = await getGeoPublisherRuleListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            publisherUserId: params.publisherUserId,
            isDefault,
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
            perm='geo:publisherRule:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的规则');
                return;
              }
              Modal.confirm({
                title: '批量删除发布人规则',
                content: `确定要删除选中的 ${selectedRowKeys.length} 条规则吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoPublisherRuleBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条规则`);
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
            perm='geo:publisherRule:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增规则
          </PermissionButton>,
        ]}
      />
      <p className='px-4 pb-2 text-xs text-neutral-400'>
        标记为「默认」的发布人会在新建/导入文章未指定发布人时自动套用（当前默认一般为小穆）。
      </p>
      <TableModal
        readonly={false}
        title={editing ? '编辑发布人规则' : '新增发布人规则'}
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={editing ? { ...editing, isDefault: Number(editing.isDefault) === 1 } : { isDefault: true }}
        onFinish={async (values) => {
          const payload = {
            publisherUserId: values.publisherUserId,
            isDefault: values.isDefault === true || values.isDefault === 1 ? 1 : 0,
            remark: values.remark,
          };
          if (editing) {
            await updateGeoPublisherRuleApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoPublisherRuleApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default PublisherRulePage;
