import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Input, Modal, Space, Tag, Tooltip, Tree, Upload } from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import type { DataNode } from 'antd/es/tree';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoSellDomainApi,
  createGeoSellPointApi,
  deleteGeoSellDomainApi,
  deleteGeoSellPointApi,
  deleteGeoSellPointBatchApi,
  getGeoSellDomainTreeApi,
  getGeoSellPointListApi,
  importGeoSellPointApi,
  importGeoSellPointDefaultApi,
  previewGeoSellPointContextApi,
  updateGeoSellDomainApi,
  updateGeoSellPointApi,
} from '@/api/geo';
import type { GeoSellDomain, GeoSellKind, GeoSellPoint, GeoSellPointContext } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

type SellKind = GeoSellKind | 'all';

function toTreeData(nodes: GeoSellDomain[]): DataNode[] {
  return (nodes ?? []).map((n) => ({
    key: String(n.id),
    title: n.name,
    pointCount: n.pointCount ?? 0,
    children: n.children?.length ? toTreeData(n.children) : undefined,
  }));
}

function findDomain(nodes: GeoSellDomain[], id: number): GeoSellDomain | null {
  for (const n of nodes) {
    if (n.id === id) return n;
    const child = findDomain(n.children ?? [], id);
    if (child) return child;
  }
  return null;
}

function flattenDomains(nodes: GeoSellDomain[], acc: { label: string; value: number }[] = [], prefix = '') {
  for (const n of nodes ?? []) {
    acc.push({ label: prefix ? `${prefix} / ${n.name}` : n.name, value: n.id });
    if (n.children?.length) flattenDomains(n.children, acc, prefix ? `${prefix} / ${n.name}` : n.name);
  }
  return acc;
}

function normalizeKind(k?: string | null): GeoSellKind {
  if (k === 'quality' || k === 'social' || k === 'authority') return k;
  return 'simple';
}

function kindLabel(k?: string | null) {
  switch (normalizeKind(k)) {
    case 'quality':
      return '品控';
    case 'social':
      return '社会/品牌';
    case 'authority':
      return '权威背书';
    default:
      return '通用主张';
  }
}

const STATUS_FIELD: ProColumnType<GeoSellPoint> = {
  title: '状态',
  dataIndex: 'status',
  width: 90,
  valueType: 'select',
  valueEnum: {
    active: { text: '生效', status: 'Success' },
    draft: { text: '草稿', status: 'Default' },
    disabled: { text: '停用', status: 'Error' },
  },
  fieldProps: {
    allowClear: true,
    options: [
      { label: '生效', value: 'active' },
      { label: '草稿', value: 'draft' },
      { label: '停用', value: 'disabled' },
    ],
  },
};

const SellPointPage = memo(function SellPointPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [domains, setDomains] = useState<GeoSellDomain[]>([]);
  const [selectedDomainId, setSelectedDomainId] = useState<number | undefined>();
  const [pointOpen, setPointOpen] = useState(false);
  const [editing, setEditing] = useState<GeoSellPoint | null>(null);
  const [domainOpen, setDomainOpen] = useState(false);
  const [editingDomain, setEditingDomain] = useState<GeoSellDomain | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewTopic, setPreviewTopic] = useState('');
  const [previewQuestion, setPreviewQuestion] = useState('');
  const [previewCtx, setPreviewCtx] = useState<GeoSellPointContext | null>(null);

  const loadDomains = useCallback(async () => {
    try {
      const tree = await getGeoSellDomainTreeApi();
      setDomains(tree ?? []);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载卖点域失败');
    }
  }, [message]);

  useEffect(() => {
    void loadDomains();
  }, [loadDomains]);

  const domainOptions = useMemo(() => flattenDomains(domains), [domains]);
  const treeData = useMemo(() => toTreeData(domains), [domains]);

  const listKind: SellKind = useMemo(() => {
    if (selectedDomainId == null) return 'all';
    const d = findDomain(domains, selectedDomainId);
    return normalizeKind(d?.rootKind);
  }, [domains, selectedDomainId]);

  const formKind: GeoSellKind = useMemo(() => {
    if (editing?.kind) return normalizeKind(editing.kind);
    if (selectedDomainId != null) {
      const d = findDomain(domains, selectedDomainId);
      return normalizeKind(d?.rootKind);
    }
    return 'simple';
  }, [editing, domains, selectedDomainId]);

  const openEdit = useCallback((record: GeoSellPoint) => {
    setEditing(record);
    setPointOpen(true);
  }, []);

  const commonTailColumns = useCallback(
    (includeKindCol: boolean): ProColumnType<GeoSellPoint>[] => [
      ...(includeKindCol
        ? [
            {
              title: '类型',
              dataIndex: 'kind',
              width: 100,
              search: false,
              render: (_: unknown, r: GeoSellPoint) => kindLabel(r.kind),
            } as ProColumnType<GeoSellPoint>,
          ]
        : []),
      {
        title: '来源Sheet',
        dataIndex: 'sourceSheet',
        width: 160,
        ellipsis: true,
        valueType: 'select',
        fieldProps: {
          allowClear: true,
          options: [
            { label: '牧场与养殖方式', value: '牧场与养殖方式' },
            { label: '矩阵和客服…', value: '矩阵' },
            { label: '产品体验/品控', value: '产品体验' },
            { label: '社会责任/品牌', value: '社会' },
            { label: '专业背书/权威认证', value: '背书' },
          ],
        },
      },
      STATUS_FIELD,
      {
        title: '关键词检索',
        dataIndex: 'keyword',
        hideInTable: true,
      },
      createTimeRangeColumn<GeoSellPoint>(),
      createTimeDisplayColumn<GeoSellPoint>(),
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
                perm: 'geo:sellPoint:edit',
                onClick: () => openEdit(record),
              },
              {
                key: 'delete',
                label: '删除',
                perm: 'geo:sellPoint:delete',
                confirmTitle: '确定删除该卖点？',
                onClick: async () => {
                  await deleteGeoSellPointApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
    ],
    [message, openEdit],
  );

  const tableColumns: ProColumnType<GeoSellPoint>[] = useMemo(() => {
    const domainCol: ProColumnType<GeoSellPoint> = {
      title: '域',
      dataIndex: 'domainName',
      search: false,
      width: 140,
      ellipsis: true,
    };

    if (listKind === 'quality') {
      return [
        { title: '项目', dataIndex: 'itemName', ellipsis: true, render: (_, r) => r.itemName || r.title },
        domainCol,
        { title: '模块', dataIndex: 'moduleName', width: 140, ellipsis: true, search: false },
        { title: '小节', dataIndex: 'sectionName', width: 140, ellipsis: true, search: false },
        {
          title: '内容/标准/要求',
          dataIndex: 'standardText',
          search: false,
          width: 360,
          render: (_, r) => (
            <div className='line-clamp-3 max-w-lg whitespace-pre-wrap text-neutral-600'>
              {r.standardText || r.evidence || '-'}
            </div>
          ),
        },
        ...commonTailColumns(false),
      ];
    }

    if (listKind === 'social') {
      return [
        { title: '事件/卖点', dataIndex: 'eventName', ellipsis: true, render: (_, r) => r.eventName || r.title },
        domainCol,
        { title: '维度', dataIndex: 'dimension', width: 120, ellipsis: true, search: false },
        {
          title: '行动内容',
          dataIndex: 'actionContent',
          search: false,
          width: 320,
          render: (_, r) => (
            <div className='line-clamp-3 max-w-md whitespace-pre-wrap text-neutral-600'>
              {r.actionContent || r.evidence || '-'}
            </div>
          ),
        },
        {
          title: '备注',
          dataIndex: 'eventRemark',
          search: false,
          width: 160,
          ellipsis: true,
        },
        ...commonTailColumns(false),
      ];
    }

    if (listKind === 'authority') {
      return [
        { title: '背书渠道', dataIndex: 'channel', ellipsis: true, render: (_, r) => r.channel || r.title },
        domainCol,
        {
          title: '准入标准',
          dataIndex: 'admission',
          search: false,
          width: 220,
          render: (_, r) => (
            <div className='line-clamp-3 whitespace-pre-wrap text-neutral-600'>{r.admission || '-'}</div>
          ),
        },
        {
          title: '遴选流程',
          dataIndex: 'processText',
          search: false,
          width: 220,
          render: (_, r) => (
            <div className='line-clamp-3 whitespace-pre-wrap text-neutral-600'>{r.processText || '-'}</div>
          ),
        },
        {
          title: '背书意义',
          dataIndex: 'meaning',
          search: false,
          width: 220,
          render: (_, r) => <div className='line-clamp-3 whitespace-pre-wrap text-neutral-600'>{r.meaning || '-'}</div>,
        },
        ...commonTailColumns(false),
      ];
    }

    // simple / all
    return [
      { title: '标题', dataIndex: 'title', ellipsis: true },
      domainCol,
      {
        title: '主张',
        dataIndex: 'claim',
        ellipsis: true,
        width: 220,
        search: false,
        render: (v) => <span className='line-clamp-2 whitespace-pre-wrap text-neutral-700'>{v || '-'}</span>,
      },
      {
        title: '论据摘要',
        dataIndex: 'evidence',
        search: false,
        width: 320,
        render: (_, r) => (
          <div className='max-w-md'>
            <div className='line-clamp-3 whitespace-pre-wrap text-neutral-600'>{r.evidence || '-'}</div>
            {(r.evidenceLen ?? r.evidence?.length ?? 0) > 80 ? (
              <span className='text-xs text-neutral-400'>
                共 {r.evidenceLen ?? r.evidence?.length} 字 · 点行首展开看全文
              </span>
            ) : null}
          </div>
        ),
      },
      {
        title: '标签',
        dataIndex: 'tags',
        search: false,
        width: 140,
        render: (_, r) =>
          (r.tags ?? []).length ? (
            <Space
              size={[4, 4]}
              wrap
            >
              {r.tags!.map((t) => (
                <Tag key={t}>{t}</Tag>
              ))}
            </Space>
          ) : (
            '-'
          ),
      },
      {
        title: '级别',
        dataIndex: 'evidenceLevel',
        width: 90,
        valueType: 'select',
        valueEnum: { soft: { text: '软性' }, hard: { text: '硬事实' } },
        fieldProps: {
          allowClear: true,
          options: [
            { label: '软性', value: 'soft' },
            { label: '硬事实', value: 'hard' },
          ],
        },
      },
      ...commonTailColumns(listKind === 'all'),
    ];
  }, [commonTailColumns, listKind]);

  const pointFormColumns: ProColumnType<GeoSellPoint>[] = useMemo(() => {
    const domainField: ProColumnType<GeoSellPoint> = {
      title: '所属域',
      dataIndex: 'domainId',
      valueType: 'select',
      fieldProps: { options: domainOptions, showSearch: true, optionFilterProp: 'label' },
      formItemProps: { rules: [{ required: true, message: '请选择所属域' }] },
    };
    const statusField: ProColumnType<GeoSellPoint> = {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      fieldProps: {
        options: [
          { label: '生效', value: 'active' },
          { label: '草稿', value: 'draft' },
          { label: '停用', value: 'disabled' },
        ],
      },
    };

    if (formKind === 'quality') {
      return [
        domainField,
        { title: '模块', dataIndex: 'moduleName' },
        { title: '小节', dataIndex: 'sectionName' },
        {
          title: '项目',
          dataIndex: 'itemName',
          formItemProps: { rules: [{ required: true, message: '请输入项目' }] },
        },
        {
          title: '内容/标准/要求',
          dataIndex: 'standardText',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 4, maxRows: 24 } },
          formItemProps: { rules: [{ required: true, message: '请输入内容/标准/要求' }] },
        },
        statusField,
      ];
    }

    if (formKind === 'social') {
      return [
        domainField,
        { title: '维度', dataIndex: 'dimension' },
        {
          title: '事件/卖点名',
          dataIndex: 'eventName',
          formItemProps: { rules: [{ required: true, message: '请输入事件名' }] },
        },
        {
          title: '行动内容',
          dataIndex: 'actionContent',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 4, maxRows: 20 } },
          formItemProps: { rules: [{ required: true, message: '请输入行动内容' }] },
        },
        {
          title: '备注',
          dataIndex: 'eventRemark',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 2, maxRows: 8 } },
        },
        statusField,
      ];
    }

    if (formKind === 'authority') {
      return [
        domainField,
        {
          title: '背书渠道',
          dataIndex: 'channel',
          formItemProps: { rules: [{ required: true, message: '请输入背书渠道' }] },
        },
        {
          title: '准入标准',
          dataIndex: 'admission',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 3, maxRows: 16 } },
        },
        {
          title: '遴选流程',
          dataIndex: 'processText',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 3, maxRows: 16 } },
        },
        {
          title: '背书意义',
          dataIndex: 'meaning',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 3, maxRows: 16 } },
        },
        {
          title: '补充/总述',
          dataIndex: 'extraNote',
          valueType: 'textarea',
          colProps: { span: 24 },
          fieldProps: { autoSize: { minRows: 2, maxRows: 12 } },
        },
        statusField,
      ];
    }

    return [
      domainField,
      {
        title: '标题',
        dataIndex: 'title',
        formItemProps: { rules: [{ required: true, message: '请输入标题' }] },
      },
      {
        title: '主张',
        dataIndex: 'claim',
        valueType: 'textarea',
        colProps: { span: 24 },
        fieldProps: { autoSize: { minRows: 2, maxRows: 10 }, maxLength: 500, showCount: true },
      },
      {
        title: '论据',
        dataIndex: 'evidence',
        valueType: 'textarea',
        colProps: { span: 24 },
        fieldProps: { autoSize: { minRows: 4, maxRows: 24 } },
      },
      {
        title: '标签',
        dataIndex: 'tags',
        valueType: 'select',
        fieldProps: { mode: 'tags', tokenSeparators: [',', '，'] },
      },
      {
        title: '级别',
        dataIndex: 'evidenceLevel',
        valueType: 'select',
        fieldProps: {
          options: [
            { label: '软性', value: 'soft' },
            { label: '硬事实', value: 'hard' },
          ],
        },
      },
      statusField,
    ];
  }, [domainOptions, formKind]);

  const renderExpanded = (record: GeoSellPoint) => {
    const k = normalizeKind(record.kind);
    if (k === 'quality') {
      return (
        <div className='space-y-2 px-2 py-1 text-sm'>
          <div className='text-xs text-neutral-400'>
            {record.moduleName || '-'} / {record.sectionName || '-'}
          </div>
          <div className='font-medium'>{record.itemName || record.title}</div>
          <div className='max-h-80 overflow-auto rounded bg-neutral-50 p-3 whitespace-pre-wrap'>
            {record.standardText || '-'}
          </div>
        </div>
      );
    }
    if (k === 'social') {
      return (
        <div className='space-y-2 px-2 py-1 text-sm'>
          <div className='text-xs text-neutral-400'>{record.dimension || '-'}</div>
          <div className='font-medium'>{record.eventName || record.title}</div>
          <div className='max-h-80 overflow-auto rounded bg-neutral-50 p-3 whitespace-pre-wrap'>
            {record.actionContent || '-'}
          </div>
          {record.eventRemark ? <div className='text-neutral-500'>备注：{record.eventRemark}</div> : null}
        </div>
      );
    }
    if (k === 'authority') {
      return (
        <div className='space-y-3 px-2 py-1 text-sm'>
          <div className='font-medium'>{record.channel || record.title}</div>
          {[
            ['准入标准', record.admission],
            ['遴选流程', record.processText],
            ['背书意义', record.meaning],
            ['补充/总述', record.extraNote],
          ].map(([label, body]) =>
            body ? (
              <div key={label as string}>
                <div className='mb-1 text-xs font-medium text-neutral-400'>{label}</div>
                <div className='max-h-48 overflow-auto rounded bg-neutral-50 p-3 whitespace-pre-wrap'>{body}</div>
              </div>
            ) : null,
          )}
        </div>
      );
    }
    return (
      <div className='space-y-3 px-2 py-1 text-sm'>
        <div>
          <div className='mb-1 text-xs font-medium text-neutral-400'>主张</div>
          <div className='whitespace-pre-wrap text-neutral-800'>{record.claim || '-'}</div>
        </div>
        <div>
          <div className='mb-1 text-xs font-medium text-neutral-400'>
            论据全文{record.evidenceLen ? `（${record.evidenceLen} 字）` : ''}
          </div>
          <div className='max-h-80 overflow-auto rounded bg-neutral-50 p-3 whitespace-pre-wrap text-neutral-800'>
            {record.evidence || '-'}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className='flex h-full min-h-0 gap-3'>
      <aside className='flex w-64 shrink-0 flex-col rounded border border-neutral-100 bg-white p-3'>
        <div className='mb-2 flex items-center justify-between'>
          <span className='text-sm font-medium'>卖点域</span>
          <PermissionButton
            perm='geo:sellPoint:add'
            type='link'
            size='small'
            onClick={() => {
              setEditingDomain(null);
              setDomainOpen(true);
            }}
          >
            新增
          </PermissionButton>
        </div>
        <button
          type='button'
          className={`mb-1 w-full rounded px-2 py-1 text-left text-xs ${
            selectedDomainId == null ? 'bg-blue-50 text-blue-600' : 'text-neutral-500 hover:bg-neutral-50'
          }`}
          onClick={() => setSelectedDomainId(undefined)}
        >
          全部卖点
        </button>
        <Tree
          className='min-h-0 flex-1 overflow-auto'
          treeData={treeData}
          selectedKeys={selectedDomainId != null ? [String(selectedDomainId)] : []}
          onSelect={(keys) => {
            const key = keys[0];
            setSelectedDomainId(key != null ? Number(key) : undefined);
          }}
          defaultExpandAll
          blockNode
          styles={{ itemTitle: { width: '100%' } }}
          titleRender={(node) => {
            const id = Number(node.key);
            const name = String(node.title ?? '');
            const count = Number((node as DataNode & { pointCount?: number }).pointCount ?? 0);
            return (
              <div className='group flex w-full min-w-0 items-center justify-between gap-1'>
                <span className='min-w-0 truncate'>
                  {name}
                  {count > 0 ? <span className='ml-1 text-xs text-neutral-400'>({count})</span> : null}
                </span>
                <span
                  className='invisible flex shrink-0 items-center group-hover:visible'
                  onClick={(e) => e.stopPropagation()}
                >
                  <Tooltip title='编辑'>
                    <PermissionButton
                      perm='geo:sellPoint:edit'
                      type='text'
                      size='small'
                      className='!px-1 text-neutral-400 hover:!text-blue-500'
                      icon={<EditOutlined />}
                      onClick={() => {
                        const row = findDomain(domains, id);
                        if (!row) return;
                        setEditingDomain(row);
                        setDomainOpen(true);
                      }}
                    />
                  </Tooltip>
                  <Tooltip title='删除'>
                    <PermissionButton
                      perm='geo:sellPoint:delete'
                      type='text'
                      size='small'
                      danger
                      className='!px-1'
                      icon={<DeleteOutlined />}
                      onClick={() => {
                        modal.confirm({
                          title: '确定删除该域？',
                          content: name ? `「${name}」` : undefined,
                          onOk: async () => {
                            await deleteGeoSellDomainApi(id);
                            message.success('已删除');
                            if (selectedDomainId === id) setSelectedDomainId(undefined);
                            await loadDomains();
                            actionRef.current?.reload();
                          },
                        });
                      }}
                    />
                  </Tooltip>
                </span>
              </div>
            );
          }}
        />
      </aside>

      <div className='min-w-0 flex-1'>
        <BaseProTable<GeoSellPoint>
          rowKey='id'
          actionRef={actionRef}
          columns={tableColumns}
          headerTitle={listKind === 'all' ? '卖点库' : `卖点库 · ${kindLabel(listKind)}`}
          scroll={{ x: 1400 }}
          params={{ domainId: selectedDomainId, listKind }}
          expandable={{ expandedRowRender: renderExpanded }}
          rowSelection={{
            selectedRowKeys,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          toolBarRender={() => [
            <PermissionButton
              key='add'
              perm='geo:sellPoint:add'
              type='primary'
              onClick={() => {
                setEditing(null);
                setPointOpen(true);
              }}
            >
              新增{listKind !== 'all' ? kindLabel(listKind) : '卖点'}
            </PermissionButton>,
            <PermissionButton
              key='preview'
              perm='geo:sellPoint:list'
              onClick={() => {
                setPreviewCtx(null);
                setPreviewOpen(true);
              }}
            >
              预览召回
            </PermissionButton>,
            <Upload
              key='import'
              showUploadList={false}
              accept='.xlsx,.xls'
              beforeUpload={(file) => {
                void (async () => {
                  try {
                    const res = await importGeoSellPointApi(file);
                    message.success(
                      `导入完成：新增 ${res.insertCount}，更新 ${res.updateCount}，失败 ${res.failureCount}`,
                    );
                    await loadDomains();
                    actionRef.current?.reload();
                  } catch (e) {
                    message.error(e instanceof Error ? e.message : '导入失败');
                  }
                })();
                return false;
              }}
            >
              <PermissionButton perm='geo:sellPoint:import'>上传导入</PermissionButton>
            </Upload>,
            <PermissionButton
              key='default'
              perm='geo:sellPoint:import'
              onClick={() => {
                modal.confirm({
                  title: '从项目 docs/小巴依卖点.xlsx 初始化？',
                  content: '将按 sheet 全量替换：品控/权威等写入各自明细表，不再硬塞成标题+主张+论据。',
                  onOk: async () => {
                    const res = await importGeoSellPointDefaultApi();
                    message.success(
                      `导入完成：新增 ${res.insertCount}，更新 ${res.updateCount}，失败 ${res.failureCount}`,
                    );
                    await loadDomains();
                    actionRef.current?.reload();
                  },
                });
              }}
            >
              一键初始化
            </PermissionButton>,
            selectedRowKeys.length > 0 ? (
              <PermissionButton
                key='batchDel'
                perm='geo:sellPoint:delete'
                danger
                onClick={() => {
                  modal.confirm({
                    title: `确定删除选中的 ${selectedRowKeys.length} 条？`,
                    onOk: async () => {
                      await deleteGeoSellPointBatchApi(selectedRowKeys.map(Number));
                      message.success('已删除');
                      setSelectedRowKeys([]);
                      actionRef.current?.reload();
                    },
                  });
                }}
              >
                批量删除
              </PermissionButton>
            ) : null,
          ]}
          request={async (params) => {
            const domainId = selectedDomainId;
            const statusRaw = params.status as string | undefined;
            const status =
              statusRaw === '生效' || statusRaw === 'active'
                ? 'active'
                : statusRaw === '草稿' || statusRaw === 'draft'
                  ? 'draft'
                  : statusRaw === '停用' || statusRaw === 'disabled'
                    ? 'disabled'
                    : statusRaw || undefined;
            const levelRaw = params.evidenceLevel as string | undefined;
            const evidenceLevel =
              levelRaw === '软性' || levelRaw === 'soft'
                ? 'soft'
                : levelRaw === '硬事实' || levelRaw === 'hard'
                  ? 'hard'
                  : levelRaw || undefined;
            const res = await getGeoSellPointListApi({
              pageNum: params.current,
              pageSize: params.pageSize,
              domainId,
              title: params.title || params.itemName || params.eventName || params.channel,
              sourceSheet: params.sourceSheet,
              status,
              evidenceLevel,
              keyword: params.keyword,
              createTimeStart: params.createTimeStart,
              createTimeEnd: params.createTimeEnd,
            });
            currentPageKeysRef.current = new Set((res.rows ?? []).map((r) => r.id));
            return { data: res.rows ?? [], total: res.total ?? 0, success: true };
          }}
        />
      </div>

      <TableModal
        readonly={false}
        open={pointOpen}
        onOpenChange={setPointOpen}
        title={editing ? `编辑${kindLabel(formKind)}` : `新增${kindLabel(formKind)}`}
        columns={pointFormColumns as any}
        initialValues={
          editing
            ? {
                ...editing,
                tags: editing.tags ?? [],
                evidenceLevel: editing.evidenceLevel || 'soft',
                status: editing.status || 'active',
                channel: editing.channel || editing.title,
                itemName: editing.itemName || editing.title,
                eventName: editing.eventName || editing.title,
              }
            : {
                domainId: selectedDomainId,
                kind: formKind,
                evidenceLevel: formKind === 'quality' || formKind === 'authority' ? 'hard' : 'soft',
                status: 'active',
                tags: [],
              }
        }
        onFinish={async (values) => {
          const payload: Record<string, unknown> = {
            domainId: values.domainId as number,
            kind: formKind,
            status: values.status || 'active',
          };
          if (formKind === 'quality') {
            payload.moduleName = values.moduleName;
            payload.sectionName = values.sectionName;
            payload.itemName = String(values.itemName || '').trim();
            payload.standardText = values.standardText;
          } else if (formKind === 'social') {
            payload.dimension = values.dimension;
            payload.eventName = String(values.eventName || '').trim();
            payload.actionContent = values.actionContent;
            payload.eventRemark = values.eventRemark;
          } else if (formKind === 'authority') {
            payload.channel = String(values.channel || '').trim();
            payload.admission = values.admission;
            payload.processText = values.processText;
            payload.meaning = values.meaning;
            payload.extraNote = values.extraNote;
          } else {
            payload.title = String(values.title || '').trim();
            payload.claim = values.claim;
            payload.evidence = values.evidence;
            payload.tags = (values.tags as string[]) || [];
            payload.keywords = (values.tags as string[]) || [];
            payload.evidenceLevel = values.evidenceLevel || 'soft';
          }
          if (editing) {
            await updateGeoSellPointApi({ ...payload, id: editing.id } as any);
            message.success('已更新');
          } else {
            await createGeoSellPointApi(payload as any);
            message.success('已新增');
          }
          setEditing(null);
          actionRef.current?.reload();
          return true;
        }}
      />

      <TableModal
        readonly={false}
        open={domainOpen}
        onOpenChange={setDomainOpen}
        title={editingDomain ? '编辑域' : '新增域'}
        columns={[
          {
            title: '父域',
            dataIndex: 'parentId',
            valueType: 'select',
            fieldProps: {
              allowClear: true,
              options: [{ label: '根域', value: 0 }, ...domainOptions],
            },
            initialValue: 0,
          },
          {
            title: '名称',
            dataIndex: 'name',
            formItemProps: { rules: [{ required: true, message: '请输入名称' }] },
          },
          { title: '编码', dataIndex: 'code' },
          { title: '排序', dataIndex: 'sortOrder', valueType: 'digit', initialValue: 0 },
          {
            title: '启用',
            dataIndex: 'enabled',
            valueType: 'select',
            valueEnum: { 1: { text: '是' }, 0: { text: '否' } },
            fieldProps: {
              options: [
                { label: '是', value: 1 },
                { label: '否', value: 0 },
              ],
            },
            initialValue: 1,
          },
          { title: '备注', dataIndex: 'remark', valueType: 'textarea' },
        ]}
        initialValues={
          editingDomain
            ? {
                ...editingDomain,
                parentId: editingDomain.parentId ?? 0,
                enabled: editingDomain.enabled ?? 1,
              }
            : { parentId: selectedDomainId ?? 0, enabled: 1, sortOrder: 0 }
        }
        onFinish={async (values) => {
          const payload = {
            parentId: (values.parentId as number) ?? 0,
            name: String(values.name || '').trim(),
            code: values.code,
            sortOrder: values.sortOrder ?? 0,
            enabled: Number(values.enabled) === 0 ? 0 : 1,
            remark: values.remark,
          };
          if (editingDomain) {
            await updateGeoSellDomainApi({ ...payload, id: editingDomain.id });
            message.success('已更新');
          } else {
            await createGeoSellDomainApi(payload);
            message.success('已新增');
          }
          setEditingDomain(null);
          await loadDomains();
          return true;
        }}
      />

      <Modal
        title='预览 AI 召回上下文'
        open={previewOpen}
        onCancel={() => setPreviewOpen(false)}
        width={720}
        footer={[
          <Button
            key='close'
            onClick={() => setPreviewOpen(false)}
          >
            关闭
          </Button>,
          <Button
            key='run'
            type='primary'
            loading={previewLoading}
            onClick={() => {
              void (async () => {
                setPreviewLoading(true);
                try {
                  const ctx = await previewGeoSellPointContextApi({
                    topicName: previewTopic.trim() || undefined,
                    targetQuestion: previewQuestion.trim() || undefined,
                    limit: 12,
                  });
                  setPreviewCtx(ctx);
                } catch (e) {
                  message.error(e instanceof Error ? e.message : '预览失败');
                } finally {
                  setPreviewLoading(false);
                }
              })();
            }}
          >
            召回
          </Button>,
        ]}
      >
        <Space
          direction='vertical'
          className='w-full'
          size='middle'
        >
          <Input
            placeholder='话题（可选）'
            value={previewTopic}
            onChange={(e) => setPreviewTopic(e.target.value)}
          />
          <Input.TextArea
            placeholder='目标问题'
            rows={2}
            value={previewQuestion}
            onChange={(e) => setPreviewQuestion(e.target.value)}
          />
          {previewCtx ? (
            <div className='max-h-96 space-y-2 overflow-auto text-sm'>
              <div className='text-neutral-500'>
                命中 {(previewCtx.points ?? []).length} 条
                {(previewCtx.hardFacts ?? []).length ? ` · 硬事实 ${(previewCtx.hardFacts ?? []).length}` : ''}
              </div>
              {(previewCtx.points ?? []).map((p) => (
                <div
                  key={p.id}
                  className='rounded border border-neutral-100 p-2'
                >
                  <div className='font-medium'>
                    [{p.level}] {p.domain} · {p.title}
                    <span className='ml-2 text-xs text-neutral-400'>分 {p.score}</span>
                  </div>
                  <div className='text-neutral-600'>{p.claim}</div>
                  <div className='mt-1 line-clamp-3 text-xs text-neutral-400'>{p.evidence}</div>
                </div>
              ))}
            </div>
          ) : null}
        </Space>
      </Modal>
    </div>
  );
});

export default SellPointPage;
