import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Drawer, Modal, Progress, Space, Table, Tag, Typography, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoContentPlacementApi,
  createGeoContentPlacementCiteApi,
  createGeoContentPlacementItemApi,
  deleteGeoContentPlacementApi,
  deleteGeoContentPlacementCiteApi,
  deleteGeoContentPlacementItemApi,
  generateSimilarGeoContentPlacementApi,
  getGeoContentPlacementCitesApi,
  getGeoContentPlacementItemsApi,
  getGeoContentPlacementListApi,
  getGeoOwnerOptionsApi,
  getGeoPlatformOptionsApi,
  getGeoTopicOptionsApi,
  importGeoContentPlacementApi,
  updateGeoContentPlacementApi,
  updateGeoContentPlacementCiteApi,
  updateGeoContentPlacementItemApi,
} from '@/api/geo';
import type {
  GeoContentPlacementCite,
  GeoContentPlacementItem,
  GeoContentPlacementListItem,
  GeoOwnerOption,
  GeoPlatform,
  GeoTopic,
} from '@/types/geo';
import {
  GEO_CONTENT_AGG_STATUS,
  GEO_CONTENT_PUBLISH_STATUS,
  GEO_CONTENT_SOURCES,
  GEO_PLATFORM_TYPE_AI,
  GEO_PLATFORM_TYPE_CONTENT,
} from '@/constants/geo';

const AGG_COLOR: Record<string, string> = {
  投放完成: 'success',
  部分投放: 'warning',
  未投放: 'default',
};

const STATUS_COLOR: Record<string, string> = {
  投放成功: 'success',
  审核未通过: 'error',
  未投放: 'default',
};

const SOURCE_COLOR: Record<string, string> = {
  导入: 'blue',
  手动新增: 'default',
  AI生成: 'purple',
};

const isHttpUrl = (v?: string) => !!v && /^https?:\/\//i.test(v);

const derivePlacementProgress = (list: GeoContentPlacementItem[]) => {
  const platformCount = list.length;
  const successCount = list.filter((i) => i.publishStatus === '投放成功').length;
  let aggregateStatus = '未投放';
  if (platformCount > 0) {
    if (successCount === platformCount) aggregateStatus = '投放完成';
    else if (successCount > 0) aggregateStatus = '部分投放';
  }
  return {
    aggregateStatus,
    publishProgress: platformCount > 0 ? Math.round((successCount * 100) / platformCount) : null,
    platformCount,
    successCount,
  };
};

const ContentPlacementPage = memo(function ContentPlacementPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [contentPlatforms, setContentPlatforms] = useState<GeoPlatform[]>([]);
  const [aiPlatforms, setAiPlatforms] = useState<GeoPlatform[]>([]);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [topics, setTopics] = useState<GeoTopic[]>([]);

  const [placementOpen, setPlacementOpen] = useState(false);
  const [editingPlacement, setEditingPlacement] = useState<GeoContentPlacementListItem | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [currentPlacement, setCurrentPlacement] = useState<GeoContentPlacementListItem | null>(null);
  const [items, setItems] = useState<GeoContentPlacementItem[]>([]);
  const [itemOpen, setItemOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<GeoContentPlacementItem | null>(null);

  const [citeOpen, setCiteOpen] = useState(false);
  const [citeLoading, setCiteLoading] = useState(false);
  const [citeItem, setCiteItem] = useState<GeoContentPlacementItem | null>(null);
  const [cites, setCites] = useState<GeoContentPlacementCite[]>([]);
  const [citeFormOpen, setCiteFormOpen] = useState(false);
  const [editingCite, setEditingCite] = useState<GeoContentPlacementCite | null>(null);
  const [iframeUrl, setIframeUrl] = useState<string>();
  const [sourceOpen, setSourceOpen] = useState(false);
  const [sourceRecord, setSourceRecord] = useState<GeoContentPlacementListItem | null>(null);
  const [generatingId, setGeneratingId] = useState<number | null>(null);

  useEffect(() => {
    void (async () => {
      const [content, ai, ownerOpts, topicOpts] = await Promise.all([
        getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_CONTENT),
        getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_AI),
        getGeoOwnerOptionsApi(),
        getGeoTopicOptionsApi(),
      ]);
      setContentPlatforms(content);
      setAiPlatforms(ai);
      setOwners(ownerOpts);
      setTopics(topicOpts);
    })();
  }, []);

  const userOptions = owners.map((u) => ({ label: u.displayName, value: u.userId }));
  const topicOptions = topics.map((t) => ({ label: t.topicName, value: t.id }));

  const reloadItems = async (placementId: number) => {
    setDrawerLoading(true);
    try {
      const list = await getGeoContentPlacementItemsApi(placementId);
      setItems(list);
      setCurrentPlacement((prev) => (prev && prev.id === placementId ? { ...prev, ...derivePlacementProgress(list) } : prev));
    } finally {
      setDrawerLoading(false);
    }
  };

  const reloadCites = async (placementId: number, itemId: number) => {
    setCiteLoading(true);
    try {
      setCites(await getGeoContentPlacementCitesApi(placementId, itemId));
    } finally {
      setCiteLoading(false);
    }
  };

  const openPlatformDrawer = async (record: GeoContentPlacementListItem) => {
    setCurrentPlacement(record);
    setDrawerOpen(true);
    await reloadItems(record.id);
  };

  const openCiteModal = async (item: GeoContentPlacementItem) => {
    if (!currentPlacement) return;
    setCiteItem(item);
    setCiteOpen(true);
    await reloadCites(currentPlacement.id, item.id);
  };

  const openExternal = (url?: string) => {
    if (!isHttpUrl(url)) return;
    setIframeUrl(url);
  };

  const handleGenerateSimilar = async (record: GeoContentPlacementListItem) => {
    setGeneratingId(record.id);
    try {
      const rows = await generateSimilarGeoContentPlacementApi(record.id);
      message.success(`已生成 ${rows.length} 条相似问题（待分配发布人/归属人）`);
      actionRef.current?.reload();
    } finally {
      setGeneratingId(null);
    }
  };

  const placementColumns: ProColumnType<GeoContentPlacementListItem>[] = [
    {
      title: '发布人',
      dataIndex: 'publisherUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: userOptions,
        placeholder: '待分配',
      },
      formItemProps: { rules: [] },
      render: (_, r) => r.publisherName || '待分配',
    },
    {
      title: '归属人',
      dataIndex: 'ownerUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: userOptions,
        placeholder: '待分配',
      },
      formItemProps: { rules: [] },
      render: (_, r) => r.ownerName || '待分配',
    },
    {
      title: '话题',
      dataIndex: 'topicId',
      width: 140,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: topicOptions,
      },
      formItemProps: { rules: [{ required: true, message: '请选择话题' }] },
      render: (_, r) => r.topicName || '-',
    },
    {
      title: '目标问题',
      dataIndex: 'targetQuestion',
      ellipsis: true,
      width: 220,
      formItemProps: { rules: [{ required: true, message: '请输入目标问题' }] },
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_SOURCES.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_SOURCES], allowClear: true },
      hideInForm: true,
      render: (_, record) => {
        const source = record.source || '手动新增';
        if (source === 'AI生成') {
          return (
            <Tag
              color={SOURCE_COLOR[source]}
              className='cursor-pointer'
              onClick={() => {
                setSourceRecord(record);
                setSourceOpen(true);
              }}
            >
              AI生成 · 查看参照
            </Tag>
          );
        }
        return <Tag color={SOURCE_COLOR[source] || 'default'}>{source}</Tag>;
      },
    },
    {
      title: '投放进度',
      dataIndex: 'aggregateStatus',
      width: 160,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_AGG_STATUS.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_AGG_STATUS], allowClear: true },
      hideInForm: true,
      render: (_, record) => {
        const status = record.aggregateStatus || '未投放';
        if (record.publishProgress == null || !record.platformCount) {
          return <Tag color={AGG_COLOR[status] || 'default'}>{status}</Tag>;
        }
        return (
          <div className='flex min-w-[130px] flex-col gap-1'>
            <Tag
              color={AGG_COLOR[status] || 'default'}
              className='w-fit cursor-pointer'
              onClick={() => openPlatformDrawer(record)}
            >
              {status}
            </Tag>
            <Progress
              percent={record.publishProgress}
              size='small'
              format={(p) => `${record.successCount || 0}/${record.platformCount} (${p}%)`}
            />
          </div>
        );
      },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      search: false,
      hideInTable: true,
      valueType: 'textarea',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 300,
      hideInForm: true,
      render: (_, record) => (
        <ActionButtons
          maxVisible={4}
          items={[
            {
              key: 'detail',
              label: '发布详情',
              perm: 'geo:content:list',
              onClick: () => openPlatformDrawer(record),
            },
            {
              key: 'generate',
              label: generatingId === record.id ? '生成中...' : '生成相似问题',
              perm: 'geo:content:generate',
              disabled: generatingId === record.id,
              onClick: () => handleGenerateSimilar(record),
            },
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:content:edit',
              onClick: () => {
                setEditingPlacement(record);
                setPlacementOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:content:delete',
              confirmTitle: '确定删除该内容投放？',
              onClick: async () => {
                await deleteGeoContentPlacementApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
  ];

  const itemFormColumns: ProColumnType<GeoContentPlacementItem>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      formItemProps: { rules: [{ required: true, message: '请输入标题' }] },
    },
    {
      title: '发布平台',
      dataIndex: 'platformName',
      valueType: 'select',
      fieldProps: {
        options: contentPlatforms.map((p) => ({ label: p.platformName, value: p.platformName })),
        showSearch: true,
      },
      formItemProps: { rules: [{ required: true, message: '请选择发布平台' }] },
    },
    {
      title: '投放状态',
      dataIndex: 'publishStatus',
      valueType: 'select',
      fieldProps: { options: [...GEO_CONTENT_PUBLISH_STATUS] },
      formItemProps: { rules: [{ required: true, message: '请选择投放状态' }] },
    },
    {
      title: '投放链接',
      dataIndex: 'publishUrl',
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      valueType: 'date',
      fieldProps: { format: 'YYYY-MM-DD' },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      valueType: 'textarea',
    },
  ];

  const citeFormColumns: ProColumnType<GeoContentPlacementCite>[] = [
    {
      title: '提问问题',
      dataIndex: 'askQuestion',
      formItemProps: { rules: [{ required: true, message: '请输入提问问题' }] },
    },
    {
      title: 'AI平台',
      dataIndex: 'aiPlatform',
      valueType: 'select',
      fieldProps: {
        options: aiPlatforms.map((p) => ({ label: p.platformName, value: p.platformName })),
        showSearch: true,
      },
      formItemProps: { rules: [{ required: true, message: '请选择AI平台' }] },
    },
    {
      title: '引用链接',
      dataIndex: 'citeUrl',
    },
    {
      title: '备注',
      dataIndex: 'remark',
      valueType: 'textarea',
    },
  ];

  return (
    <>
      <BaseProTable<GeoContentPlacementListItem>
        rowKey='id'
        actionRef={actionRef}
        columns={placementColumns}
        headerTitle='内容投放管理'
        request={async (params) => {
          const res = await getGeoContentPlacementListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            publisherUserId: params.publisherUserId,
            ownerUserId: params.ownerUserId,
            topicId: params.topicId,
            targetQuestion: params.targetQuestion,
            source: params.source,
            aggregateStatus: params.aggregateStatus,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:content:add'
            onClick={() => {
              setEditingPlacement(null);
              setPlacementOpen(true);
            }}
          >
            新增内容投放
          </PermissionButton>,
          <PermissionButton
            key='import'
            perm='geo:content:import'
            onClick={() => setImportOpen(true)}
          >
            导入 Excel
          </PermissionButton>,
        ]}
      />

      <TableModal
        readonly={false}
        title={editingPlacement ? '编辑内容投放' : '新增内容投放'}
        columns={placementColumns as any}
        open={placementOpen}
        onOpenChange={setPlacementOpen}
        initialValues={
          editingPlacement
            ? {
                publisherUserId: editingPlacement.publisherUserId,
                ownerUserId: editingPlacement.ownerUserId,
                topicId: editingPlacement.topicId,
                targetQuestion: editingPlacement.targetQuestion,
                remark: editingPlacement.remark,
              }
            : {}
        }
        onFinish={async (values) => {
          const payload = {
            publisherUserId: values.publisherUserId ?? null,
            ownerUserId: values.ownerUserId ?? null,
            topicId: values.topicId,
            targetQuestion: values.targetQuestion,
            remark: values.remark,
          };
          if (editingPlacement) {
            await updateGeoContentPlacementApi({ ...payload, id: editingPlacement.id });
            message.success('已更新');
          } else {
            await createGeoContentPlacementApi(payload);
            message.success('已创建');
          }
          const topicOpts = await getGeoTopicOptionsApi();
          setTopics(topicOpts);
          actionRef.current?.reload();
          return true;
        }}
      />

      <BaseModalForm
        title='导入内容投放 Excel'
        open={importOpen}
        onOpenChange={(v) => {
          if (!v) setFile(null);
          setImportOpen(v);
        }}
        submitter={false}
      >
        <Upload.Dragger
          accept='.xlsx,.xls'
          maxCount={1}
          beforeUpload={(f) => {
            setFile(f);
            return false;
          }}
          onRemove={() => setFile(null)}
          fileList={file ? [{ uid: '1', name: file.name }] : []}
        >
          <p className='ant-upload-drag-icon'>
            <InboxOutlined />
          </p>
          <p>请上传「内容投放」格式的 Excel</p>
        </Upload.Dragger>
        <div className='mt-4 flex justify-end'>
          <Button
            type='primary'
            disabled={!file}
            onClick={async () => {
              if (!file) return;
              const res = await importGeoContentPlacementApi(file);
              message.success(`导入完成：新增 ${res.insertCount}，更新 ${res.updateCount}，失败 ${res.failureCount}`);
              setImportOpen(false);
              setFile(null);
              actionRef.current?.reload();
            }}
          >
            开始导入
          </Button>
        </div>
      </BaseModalForm>

      <Drawer
        title={currentPlacement?.targetQuestion || '发布详情'}
        width={960}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setItems([]);
          setCurrentPlacement(null);
          actionRef.current?.reload();
        }}
        destroyOnClose
        extra={
          <PermissionButton
            type='primary'
            perm='geo:content:edit'
            onClick={() => {
              setEditingItem(null);
              setItemOpen(true);
            }}
          >
            新增发布详情
          </PermissionButton>
        }
      >
        {currentPlacement ? (
          <div className='mb-4 space-y-1 text-sm text-neutral-600'>
            <div>
              发布人：{currentPlacement.publisherName || '-'}　归属人：{currentPlacement.ownerName || '-'}　话题：
              {currentPlacement.topicName || '-'}
            </div>
            <div>目标问题：{currentPlacement.targetQuestion || '-'}</div>
            <div>
              投放进度：
              {currentPlacement.aggregateStatus || '未投放'}
              {currentPlacement.publishProgress != null && currentPlacement.platformCount
                ? `（${currentPlacement.successCount || 0}/${currentPlacement.platformCount}，${currentPlacement.publishProgress}%）`
                : ''}
            </div>
          </div>
        ) : null}
        <Table<GeoContentPlacementItem>
          rowKey='id'
          loading={drawerLoading}
          dataSource={items}
          pagination={false}
          size='small'
          columns={[
            {
              title: '标题',
              dataIndex: 'title',
              ellipsis: true,
              width: 180,
            },
            {
              title: '发布状态',
              dataIndex: 'publishStatus',
              width: 110,
              render: (v: string) => <Tag color={STATUS_COLOR[v] || 'default'}>{v || '-'}</Tag>,
            },
            {
              title: '链接',
              dataIndex: 'publishUrl',
              ellipsis: true,
              render: (v?: string) =>
                isHttpUrl(v) ? (
                  <Typography.Link onClick={() => openExternal(v)}>{v}</Typography.Link>
                ) : (
                  <span>{v || '-'}</span>
                ),
            },
            {
              title: '平台名称',
              dataIndex: 'platformName',
              width: 100,
            },
            {
              title: '发布时间',
              dataIndex: 'publishTime',
              width: 110,
              render: (v?: string) => v || '-',
            },
            {
              title: '引用情况',
              dataIndex: 'citeCount',
              width: 100,
              render: (count: number, row) => (
                <Button
                  type='link'
                  className='px-0'
                  onClick={() => openCiteModal(row)}
                >
                  {count ? `${count} 条` : '管理'}
                </Button>
              ),
            },
            {
              title: '操作',
              width: 120,
              render: (_, row) => (
                <Space size='small'>
                  <PermissionButton
                    type='link'
                    className='px-0'
                    perm='geo:content:edit'
                    onClick={() => {
                      setEditingItem(row);
                      setItemOpen(true);
                    }}
                  >
                    编辑
                  </PermissionButton>
                  <PermissionButton
                    type='link'
                    danger
                    className='px-0'
                    perm='geo:content:edit'
                    onClick={async () => {
                      await deleteGeoContentPlacementItemApi(row.id);
                      message.success('已删除');
                      if (currentPlacement) await reloadItems(currentPlacement.id);
                      actionRef.current?.reload();
                    }}
                  >
                    删除
                  </PermissionButton>
                </Space>
              ),
            },
          ]}
        />
      </Drawer>

      <TableModal
        readonly={false}
        title={editingItem ? '编辑发布详情' : '新增发布详情'}
        columns={itemFormColumns as any}
        open={itemOpen}
        onOpenChange={setItemOpen}
        initialValues={
          editingItem ?? {
            title: currentPlacement?.title || '',
            publishStatus: '未投放',
          }
        }
        onFinish={async (values) => {
          if (!currentPlacement) return false;
          const payload = {
            placementId: currentPlacement.id,
            title: values.title,
            platformName: values.platformName,
            publishStatus: values.publishStatus,
            publishUrl: values.publishUrl,
            publishTime: values.publishTime,
            remark: values.remark,
          };
          if (editingItem) {
            await updateGeoContentPlacementItemApi({ ...payload, id: editingItem.id });
            message.success('已更新');
          } else {
            await createGeoContentPlacementItemApi(payload);
            message.success('已创建');
          }
          await reloadItems(currentPlacement.id);
          actionRef.current?.reload();
          return true;
        }}
      />

      <Modal
        title={`${citeItem?.platformName || '平台'} · 引用情况`}
        open={citeOpen}
        onCancel={() => {
          setCiteOpen(false);
          setCites([]);
          setCiteItem(null);
          if (currentPlacement) void reloadItems(currentPlacement.id);
        }}
        footer={null}
        width={820}
        destroyOnClose
      >
        <div className='mb-3 flex justify-end'>
          <PermissionButton
            type='primary'
            perm='geo:content:edit'
            onClick={() => {
              setEditingCite(null);
              setCiteFormOpen(true);
            }}
          >
            新增引用
          </PermissionButton>
        </div>
        <Table<GeoContentPlacementCite>
          rowKey='id'
          loading={citeLoading}
          dataSource={cites}
          pagination={false}
          size='small'
          columns={[
            {
              title: '提问问题',
              dataIndex: 'askQuestion',
              ellipsis: true,
            },
            {
              title: 'AI平台',
              dataIndex: 'aiPlatform',
              width: 90,
            },
            {
              title: '引用链接',
              dataIndex: 'citeUrl',
              ellipsis: true,
              render: (v?: string) =>
                isHttpUrl(v) ? (
                  <Typography.Link onClick={() => openExternal(v)}>{v}</Typography.Link>
                ) : (
                  '-'
                ),
            },
            {
              title: '操作',
              width: 120,
              render: (_, row) => (
                <Space size='small'>
                  <PermissionButton
                    type='link'
                    className='px-0'
                    perm='geo:content:edit'
                    onClick={() => {
                      setEditingCite(row);
                      setCiteFormOpen(true);
                    }}
                  >
                    编辑
                  </PermissionButton>
                  <PermissionButton
                    type='link'
                    danger
                    className='px-0'
                    perm='geo:content:edit'
                    onClick={async () => {
                      await deleteGeoContentPlacementCiteApi(row.id);
                      message.success('已删除');
                      if (currentPlacement && citeItem) {
                        await reloadCites(currentPlacement.id, citeItem.id);
                      }
                    }}
                  >
                    删除
                  </PermissionButton>
                </Space>
              ),
            },
          ]}
        />
      </Modal>

      <TableModal
        readonly={false}
        title={editingCite ? '编辑引用' : '新增引用'}
        columns={citeFormColumns as any}
        open={citeFormOpen}
        onOpenChange={setCiteFormOpen}
        initialValues={editingCite ?? { aiPlatform: aiPlatforms[0]?.platformName }}
        onFinish={async (values) => {
          if (!currentPlacement || !citeItem) return false;
          const payload = {
            placementId: currentPlacement.id,
            itemId: citeItem.id,
            askQuestion: values.askQuestion,
            aiPlatform: values.aiPlatform,
            citeUrl: values.citeUrl,
            remark: values.remark,
          };
          if (editingCite) {
            await updateGeoContentPlacementCiteApi({ ...payload, id: editingCite.id });
            message.success('已更新');
          } else {
            await createGeoContentPlacementCiteApi(payload);
            message.success('已创建');
          }
          await reloadCites(currentPlacement.id, citeItem.id);
          return true;
        }}
      />

      <Modal
        title='AI 生成参照'
        open={sourceOpen}
        onCancel={() => {
          setSourceOpen(false);
          setSourceRecord(null);
        }}
        footer={null}
        destroyOnClose
      >
        <div className='space-y-3 text-sm'>
          <div>
            <div className='mb-1 text-neutral-500'>当前目标问题</div>
            <div>{sourceRecord?.targetQuestion || '-'}</div>
          </div>
          <div>
            <div className='mb-1 text-neutral-500'>参照的目标问题</div>
            <div>{sourceRecord?.sourceTargetQuestion || '-'}</div>
          </div>
          {sourceRecord?.sourcePlacementId ? (
            <div className='text-neutral-400'>参照记录 ID：{sourceRecord.sourcePlacementId}</div>
          ) : null}
        </div>
      </Modal>

      <Drawer
        title='外部链接'
        width='70%'
        open={!!iframeUrl}
        onClose={() => setIframeUrl(undefined)}
        destroyOnClose
        extra={
          iframeUrl ? (
            <Button
              type='link'
              href={iframeUrl}
              target='_blank'
              rel='noreferrer'
            >
              新窗口打开
            </Button>
          ) : null
        }
      >
        {iframeUrl ? (
          <iframe
            title='content-placement-link'
            src={iframeUrl}
            className='h-[75vh] w-full border-0'
            sandbox='allow-scripts allow-same-origin allow-popups allow-forms'
          />
        ) : null}
      </Drawer>
    </>
  );
});

export default ContentPlacementPage;
