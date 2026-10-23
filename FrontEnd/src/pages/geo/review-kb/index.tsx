import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Image, Modal, Tag, Tabs, Typography } from 'antd';
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
  updateGeoReviewKbApi,
} from '@/api/geo';
import type { GeoReviewKb, GeoReviewKbCategory, GeoReviewKbProductAttrs, GeoReviewKbStoreAttrs } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';
import { resolveUploadUrl } from '@/utils/uploadUrl';

/** 后端 hydrate 前/旧包：从 attrs JSON 兜底解析产品结构化字段 */
function resolveProductAttrs(row: GeoReviewKb): GeoReviewKbProductAttrs | undefined {
  if (row.productAttrs && (row.productAttrs.seriesCode || row.productAttrs.kind || row.productAttrs.imageUrl)) {
    return row.productAttrs;
  }
  const raw = row.attrs;
  if (!raw || typeof raw !== 'string') return row.productAttrs;
  try {
    const parsed = JSON.parse(raw) as GeoReviewKbProductAttrs;
    if (parsed && typeof parsed === 'object') return { ...parsed, ...row.productAttrs };
  } catch {
    /* ignore */
  }
  return row.productAttrs;
}

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
  const isProduct = activeCategory === 'product';
  const activeMeta = useMemo(() => categories.find((c) => c.code === activeCategory), [categories, activeCategory]);

  const columns: ProColumnType<GeoReviewKb>[] = [
    {
      title: isStore ? '门店名称' : isProduct ? '产品/系列' : '标题',
      dataIndex: 'title',
      ellipsis: true,
      width: isProduct ? 160 : 200,
      formItemProps: {
        rules: [{ required: true, message: isStore ? '请填写门店名称' : isProduct ? '请填写产品名称' : '请填写标题' }],
      },
    },
    {
      title: '产品图',
      dataIndex: ['productAttrs', 'imageUrl'],
      width: 88,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      render: (_, r) => {
        const url = resolveProductAttrs(r)?.imageUrl;
        if (!url) return <Typography.Text type='secondary'>—</Typography.Text>;
        return (
          <Image
            src={resolveUploadUrl(url)}
            width={56}
            height={56}
            className='rounded object-cover'
            preview={{ src: resolveUploadUrl(url) }}
          />
        );
      },
      fieldProps: { placeholder: '/uploads/geo/handbook/xxx.jpg' },
    },
    {
      title: '系列',
      dataIndex: ['productAttrs', 'seriesName'],
      width: 120,
      ellipsis: true,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      render: (_, r) => resolveProductAttrs(r)?.seriesName || '—',
    },
    {
      title: '类型',
      dataIndex: ['productAttrs', 'kind'],
      width: 80,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      valueType: 'select',
      fieldProps: {
        options: [
          { label: '系列口径', value: 'series' },
          { label: 'SKU', value: 'sku' },
        ],
      },
      render: (_, r) => {
        const k = resolveProductAttrs(r)?.kind;
        if (k === 'sku') return <Tag color='blue'>SKU</Tag>;
        if (k === 'series') return <Tag>系列</Tag>;
        return <Tag>口径</Tag>;
      },
    },
    {
      title: '净重',
      dataIndex: ['productAttrs', 'netWeight'],
      width: 80,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      render: (_, r) => resolveProductAttrs(r)?.netWeight || '—',
    },
    {
      title: '毛重',
      dataIndex: ['productAttrs', 'grossWeight'],
      width: 80,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      render: (_, r) => resolveProductAttrs(r)?.grossWeight || '—',
    },
    {
      title: '规格',
      dataIndex: ['productAttrs', 'specJin'],
      width: 90,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      render: (_, r) => resolveProductAttrs(r)?.specJin || '—',
    },
    {
      title: '参考价',
      dataIndex: ['productAttrs', 'priceYuan'],
      width: 100,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      fieldProps: { placeholder: '以最新线上为准' },
      render: (_, r) => resolveProductAttrs(r)?.priceYuan || '—',
    },
    {
      title: '部位',
      dataIndex: ['productAttrs', 'parts'],
      ellipsis: true,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      width: 180,
      render: (_, r) => resolveProductAttrs(r)?.parts || '—',
    },
    {
      title: '手册页',
      dataIndex: ['productAttrs', 'handbookPage'],
      width: 72,
      search: false,
      hideInTable: !isProduct,
      hideInForm: !isProduct,
      valueType: 'digit',
      render: (_, r) => resolveProductAttrs(r)?.handbookPage ?? '—',
    },
    {
      title: '系列编码',
      dataIndex: ['productAttrs', 'seriesCode'],
      width: 110,
      search: false,
      hideInTable: true,
      hideInForm: !isProduct,
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
      hideInTable: isStore || isProduct,
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
        placeholder: isProduct
          ? '系列口径正文；SKU 可留空由结构化字段拼装'
          : '一条一检查点/一规则/一品/一问，保持简短便于单独启停',
      },
      formItemProps: isStore || isProduct ? undefined : { rules: [{ required: true, message: '请填写内容' }] },
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
        scroll={{ x: isProduct ? 1680 : isStore ? 1400 : 1280 }}
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
            pageSize: params.pageSize ?? (activeCategory === 'product' ? 100 : 20),
            category: (params.category as string) || activeCategory,
            title: params.title,
            enabled,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          const rows = (res.rows || []).map((r) =>
            r.category === 'product' ? { ...r, productAttrs: resolveProductAttrs(r) } : r,
          );
          currentPageKeysRef.current = new Set(rows.map((r) => r.id));
          return { data: rows, success: true, total: res.total };
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
            {isStore ? '新增门店' : isProduct ? '新增产品' : '新增条目'}
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={
          editing
            ? isStore
              ? '编辑门店'
              : isProduct
                ? '编辑产品'
                : '编辑审稿知识'
            : isStore
              ? '新增门店'
              : isProduct
                ? '新增产品'
                : '新增审稿知识'
        }
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing
            ? {
                ...editing,
                enabled: Number(editing.enabled) === 1,
                storeAttrs: editing.storeAttrs || {},
                productAttrs: editing.productAttrs || {},
              }
            : {
                category: activeCategory || 'writing_rule',
                enabled: true,
                sortOrder: 100,
                storeAttrs: {},
                productAttrs: isProduct ? { kind: 'sku' } : {},
              }
        }
        onFinish={async (values) => {
          const storeAttrs = (values.storeAttrs || {}) as GeoReviewKbStoreAttrs;
          const productAttrs = (values.productAttrs || {}) as GeoReviewKbProductAttrs;
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
            productAttrs: isProduct
              ? {
                  kind: productAttrs.kind,
                  seriesCode: productAttrs.seriesCode,
                  seriesName: productAttrs.seriesName,
                  netWeight: productAttrs.netWeight,
                  grossWeight: productAttrs.grossWeight,
                  specJin: productAttrs.specJin,
                  priceYuan: productAttrs.priceYuan,
                  parts: productAttrs.parts,
                  imageUrl: productAttrs.imageUrl,
                  handbookPage:
                    productAttrs.handbookPage != null && productAttrs.handbookPage !== ('' as any)
                      ? Number(productAttrs.handbookPage)
                      : undefined,
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
