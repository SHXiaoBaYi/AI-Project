import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal, Tag, Tabs, Typography } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoReviewKbApi,
  deleteGeoReviewKbApi,
  deleteGeoReviewKbBatchApi,
  getGeoReviewKbCategoriesApi,
  getGeoReviewKbListApi,
  seedGeoReviewKbApi,
  updateGeoReviewKbApi,
} from '@/api/geo';
import type { GeoReviewKb, GeoReviewKbCategory, GeoReviewKbStoreAttrs } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const ReviewKbPage = memo(function ReviewKbPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoReviewKb | null>(null);
  const [categories, setCategories] = useState<GeoReviewKbCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string>('writing_rule');

  useEffect(() => {
    void getGeoReviewKbCategoriesApi().then((list) => setCategories(list ?? []));
  }, []);

  const isStore = activeCategory === 'store';
  const categoryOptions = useMemo(() => categories.map((c) => ({ label: c.name, value: c.code })), [categories]);
  const activeMeta = useMemo(() => categories.find((c) => c.code === activeCategory), [categories, activeCategory]);

  const categoryName = (code?: string) => categories.find((c) => c.code === code)?.name || code || '-';

  const columns: ProColumnType<GeoReviewKb>[] = [
    {
      title: '分类',
      dataIndex: 'category',
      width: 140,
      valueType: 'select',
      hideInSearch: true,
      hideInTable: isStore,
      fieldProps: { options: categoryOptions, showSearch: true, optionFilterProp: 'label' },
      formItemProps: { rules: [{ required: true, message: '请选择分类' }] },
      render: (_, r) => categoryName(r.category),
    },
    {
      title: isStore ? '门店名称' : '标题',
      dataIndex: 'title',
      ellipsis: true,
      width: 200,
      formItemProps: { rules: [{ required: true, message: isStore ? '请填写门店名称' : '请填写标题' }] },
    },
    {
      title: '营业时间',
      dataIndex: ['storeAttrs', 'openHours'],
      width: 120,
      search: false,
      hideInTable: !isStore,
      hideInForm: !isStore,
    },
    {
      title: '地址',
      dataIndex: ['storeAttrs', 'address'],
      ellipsis: true,
      search: false,
      hideInTable: !isStore,
      hideInForm: !isStore,
      fieldProps: { placeholder: '省市区街道门牌等' },
    },
    {
      title: '座机',
      dataIndex: ['storeAttrs', 'landline'],
      width: 130,
      search: false,
      hideInTable: !isStore,
      hideInForm: !isStore,
    },
    {
      title: '手机',
      dataIndex: ['storeAttrs', 'mobile'],
      width: 130,
      search: false,
      hideInTable: !isStore,
      hideInForm: !isStore,
    },
    {
      title: '覆盖范围',
      dataIndex: ['storeAttrs', 'coverage'],
      width: 140,
      ellipsis: true,
      search: false,
      hideInTable: !isStore,
      hideInForm: !isStore,
      fieldProps: { placeholder: '如：天山区 / 四区覆盖说明' },
    },
    {
      title: '内容摘要',
      dataIndex: 'content',
      search: false,
      hideInForm: true,
      hideInTable: isStore,
      ellipsis: true,
      render: (_, r) => (
        <Typography.Text
          type='secondary'
          ellipsis={{ tooltip: r.content }}
          className='max-w-[420px]'
        >
          {(r.content || '').replace(/\s+/g, ' ').trim()}
        </Typography.Text>
      ),
    },
    {
      title: '编码',
      dataIndex: 'code',
      width: 150,
      ellipsis: true,
      search: false,
      hideInForm: !!editing,
    },
    {
      title: '内容',
      dataIndex: 'content',
      valueType: 'textarea',
      search: false,
      hideInTable: true,
      hideInForm: isStore,
      fieldProps: {
        rows: 5,
        placeholder: '一条一检查点/一规则/一品/一问，保持简短便于单独启停',
      },
      formItemProps: isStore ? undefined : { rules: [{ required: true, message: '请填写内容' }] },
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      width: 80,
      valueType: 'digit',
      search: false,
      fieldProps: { min: 0, precision: 0 },
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 90,
      valueType: 'switch',
      fieldProps: { checkedChildren: '是', unCheckedChildren: '否' },
      valueEnum: { 1: { text: '是' }, 0: { text: '否' } },
      initialValue: 1,
      render: (_, r) => (Number(r.enabled) === 1 ? <Tag color='success'>启用</Tag> : <Tag>停用</Tag>),
    },
    {
      title: '来源',
      dataIndex: 'sourceFile',
      width: 140,
      ellipsis: true,
      search: false,
      hideInTable: isStore,
    },
    createTimeRangeColumn<GeoReviewKb>(),
    createTimeDisplayColumn<GeoReviewKb>(),
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
              perm: 'geo:reviewKb:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:reviewKb:delete',
              confirmTitle: '确定删除该知识条目？',
              onClick: async () => {
                await deleteGeoReviewKbApi(record.id);
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
      <div className='mb-3 rounded-lg bg-white px-4 pt-2'>
        <Tabs
          activeKey={activeCategory}
          onChange={(key) => {
            setActiveCategory(key);
            setSelectedRowKeys([]);
          }}
          items={categories.map((c) => ({
            key: c.code,
            label: c.name,
          }))}
        />
      </div>
      <BaseProTable<GeoReviewKb>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle={activeMeta?.name || '内容审稿知识库'}
        params={{ category: activeCategory }}
        scroll={{ x: isStore ? 1400 : 1280 }}
        search={{ labelWidth: 'auto', defaultCollapsed: true }}
        request={async (params) => {
          const enabled =
            params.enabled === true || params.enabled === 1 || params.enabled === '1'
              ? 1
              : params.enabled === false || params.enabled === 0 || params.enabled === '0'
                ? 0
                : 1;
          const res = await getGeoReviewKbListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            category: (params.category as string) || activeCategory,
            title: params.title,
            enabled,
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
            key='seed'
            perm='geo:reviewKb:edit'
            onClick={() => {
              Modal.confirm({
                title: '从系统种子补齐缺失条目',
                content: '补齐原子条目与门店结构化字段，并删除旧整篇。不会覆盖你已改过的同编码正文。',
                onOk: async () => {
                  const n = await seedGeoReviewKbApi(false);
                  message.success(n > 0 ? `已处理 ${n} 条` : '无需补齐');
                  actionRef.current?.reload();
                },
              });
            }}
          >
            同步种子
          </PermissionButton>,
          <PermissionButton
            key='seed-force'
            perm='geo:reviewKb:edit'
            onClick={() => {
              Modal.confirm({
                title: '强制覆盖种子正文？',
                content: '用系统种子覆盖同编码条目（含门店结构化字段），手工修改会丢失。',
                okButtonProps: { danger: true },
                onOk: async () => {
                  const n = await seedGeoReviewKbApi(true);
                  message.success(`已覆盖/处理 ${n} 条`);
                  actionRef.current?.reload();
                },
              });
            }}
          >
            强制覆盖种子
          </PermissionButton>,
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='geo:reviewKb:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的条目');
                return;
              }
              Modal.confirm({
                title: '批量删除',
                content: `确定删除选中的 ${selectedRowKeys.length} 条？`,
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoReviewKbBatchApi(selectedRowKeys as number[]);
                  message.success('已删除');
                  setSelectedRowKeys([]);
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
            perm='geo:reviewKb:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            {isStore ? '新增门店' : '新增条目'}
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? (isStore ? '编辑门店' : '编辑审稿知识') : isStore ? '新增门店' : '新增审稿知识'}
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing
            ? {
                ...editing,
                enabled: Number(editing.enabled) === 1,
                storeAttrs: editing.storeAttrs || {},
              }
            : {
                category: activeCategory || 'writing_rule',
                enabled: true,
                sortOrder: 100,
                storeAttrs: {},
              }
        }
        onFinish={async (values) => {
          const storeAttrs = (values.storeAttrs || {}) as GeoReviewKbStoreAttrs;
          const payload = {
            category: values.category || activeCategory,
            code: values.code,
            title: values.title,
            content: isStore ? undefined : values.content,
            storeAttrs: isStore
              ? {
                  openHours: storeAttrs.openHours,
                  address: storeAttrs.address,
                  landline: storeAttrs.landline,
                  mobile: storeAttrs.mobile,
                  coverage: storeAttrs.coverage,
                }
              : undefined,
            sortOrder: values.sortOrder != null ? Number(values.sortOrder) : 100,
            enabled: values.enabled === true || values.enabled === 1 ? 1 : 0,
            sourceFile: values.sourceFile,
            remark: values.remark,
          };
          if (editing) {
            await updateGeoReviewKbApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoReviewKbApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default ReviewKbPage;
