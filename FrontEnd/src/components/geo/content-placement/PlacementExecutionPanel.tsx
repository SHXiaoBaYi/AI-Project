import { memo, useEffect, useState } from 'react';
import type { ProColumnType } from '@ant-design/pro-components';
import { App, Button, Drawer, Modal, Space, Table, Tag } from 'antd';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ExternalLinkText } from '@/components/ExternalLinkDrawer';
import {
  createGeoContentPlacementCiteApi,
  createGeoContentPlacementItemApi,
  deleteGeoContentPlacementCiteApi,
  deleteGeoContentPlacementItemApi,
  getGeoContentPlacementCitesApi,
  getGeoContentPlacementItemsApi,
  getGeoPlatformOptionsApi,
  updateGeoContentPlacementCiteApi,
  updateGeoContentPlacementItemApi,
} from '@/api/geo';
import type {
  GeoContentPlacementCite,
  GeoContentPlacementItem,
  GeoContentPlacementListItem,
  GeoPlatform,
} from '@/types/geo';
import {
  GEO_CONTENT_FORMS,
  GEO_CONTENT_PUBLISH_STATUS,
  GEO_PLATFORM_TYPE_AI,
  GEO_PLATFORM_TYPE_CONTENT,
} from '@/constants/geo';
import { AGG_COLOR, STATUS_COLOR, derivePlacementProgress } from './constants';

type Props = {
  open: boolean;
  placement: GeoContentPlacementListItem | null;
  /** view=只读进度；edit=一线维护投放/引用 */
  mode?: 'view' | 'edit';
  editPerm?: string;
  onClose: () => void;
  onChanged?: (next: Partial<GeoContentPlacementListItem>) => void;
};

const PlacementExecutionPanel = memo(function PlacementExecutionPanel({
  open,
  placement,
  mode = 'edit',
  editPerm = 'geo:content:work',
  onClose,
  onChanged,
}: Props) {
  const { message } = App.useApp();
  const editable = mode === 'edit';
  const [contentPlatforms, setContentPlatforms] = useState<GeoPlatform[]>([]);
  const [aiPlatforms, setAiPlatforms] = useState<GeoPlatform[]>([]);
  const [loading, setLoading] = useState(false);
  const [items, setItems] = useState<GeoContentPlacementItem[]>([]);
  const [itemOpen, setItemOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<GeoContentPlacementItem | null>(null);
  const [citeOpen, setCiteOpen] = useState(false);
  const [citeLoading, setCiteLoading] = useState(false);
  const [citeItem, setCiteItem] = useState<GeoContentPlacementItem | null>(null);
  const [cites, setCites] = useState<GeoContentPlacementCite[]>([]);
  const [citeFormOpen, setCiteFormOpen] = useState(false);
  const [editingCite, setEditingCite] = useState<GeoContentPlacementCite | null>(null);

  useEffect(() => {
    if (!open) return;
    void (async () => {
      const [content, ai] = await Promise.all([
        getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_CONTENT),
        getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_AI),
      ]);
      setContentPlatforms(content);
      setAiPlatforms(ai);
    })();
  }, [open]);

  const reloadItems = async (placementId: number) => {
    setLoading(true);
    try {
      const list = await getGeoContentPlacementItemsApi(placementId);
      setItems(list);
      onChanged?.(derivePlacementProgress(list));
    } finally {
      setLoading(false);
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

  useEffect(() => {
    if (open && placement?.id) {
      void reloadItems(placement.id);
    } else {
      setItems([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, placement?.id]);

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
      title: '内容形态',
      dataIndex: 'contentForm',
      valueType: 'select',
      fieldProps: { options: [...GEO_CONTENT_FORMS] },
      formItemProps: { rules: [{ required: true, message: '请选择内容形态' }] },
    },
    {
      title: '发布状态',
      dataIndex: 'publishStatus',
      valueType: 'select',
      fieldProps: { options: [...GEO_CONTENT_PUBLISH_STATUS] },
      formItemProps: { rules: [{ required: true, message: '请选择状态' }] },
    },
    { title: '投放链接', dataIndex: 'publishUrl' },
    { title: '发布时间', dataIndex: 'publishTime', valueType: 'date' },
    { title: '备注', dataIndex: 'remark', valueType: 'textarea' },
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
    { title: '引用链接', dataIndex: 'citeUrl' },
    { title: '备注', dataIndex: 'remark', valueType: 'textarea' },
  ];

  return (
    <>
      <Drawer
        title={placement?.targetQuestion || '发布详情'}
        width={960}
        open={open}
        onClose={onClose}
        destroyOnClose
        extra={
          editable ? (
            <PermissionButton
              type='primary'
              perm={editPerm}
              onClick={() => {
                setEditingItem(null);
                setItemOpen(true);
              }}
            >
              新增发布详情
            </PermissionButton>
          ) : null
        }
      >
        {placement ? (
          <div className='mb-4 space-y-1 text-sm text-neutral-600'>
            <div>
              发布人：{placement.publisherName || '-'}　归属人：{placement.ownerName || '-'}　话题：
              {placement.topicName || '-'}
            </div>
            <div>目标问题：{placement.targetQuestion || '-'}</div>
            <div>
              投放进度：
              <Tag
                color={AGG_COLOR[placement.aggregateStatus || '未投放'] || 'default'}
                className='ml-1'
              >
                {placement.aggregateStatus || '未投放'}
              </Tag>
              {placement.publishProgress != null && placement.platformCount
                ? `（${placement.successCount || 0}/${placement.platformCount}，${placement.publishProgress}%）`
                : ''}
            </div>
          </div>
        ) : null}
        <Table<GeoContentPlacementItem>
          rowKey='id'
          loading={loading}
          dataSource={items}
          pagination={false}
          size='small'
          scroll={{ x: 'max-content' }}
          columns={[
            ...(editable
              ? [
                  {
                    title: '操作',
                    width: 120,
                    fixed: 'left' as const,
                    render: (_: unknown, row: GeoContentPlacementItem) => (
                      <Space size='small'>
                        <PermissionButton
                          type='link'
                          className='px-0'
                          perm={editPerm}
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
                          perm={editPerm}
                          onClick={async () => {
                            await deleteGeoContentPlacementItemApi(row.id);
                            message.success('已删除');
                            if (placement) await reloadItems(placement.id);
                          }}
                        >
                          删除
                        </PermissionButton>
                      </Space>
                    ),
                  } as any,
                ]
              : []),
            { title: '标题', dataIndex: 'title', ellipsis: true, width: 180 },
            {
              title: '形态',
              dataIndex: 'contentForm',
              width: 70,
              render: (v?: string) => v || '图文',
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
              render: (v?: string) => (
                <ExternalLinkText
                  href={v}
                  drawerTitle='投放链接'
                />
              ),
            },
            { title: '平台名称', dataIndex: 'platformName', width: 100 },
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
                  onClick={async () => {
                    if (!placement) return;
                    setCiteItem(row);
                    setCiteOpen(true);
                    await reloadCites(placement.id, row.id);
                  }}
                >
                  {count ? `${count} 条` : editable ? '管理' : '查看'}
                </Button>
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
            title: placement?.title || '',
            contentForm: '图文',
            publishStatus: '未投放',
          }
        }
        onFinish={async (values) => {
          if (!placement) return false;
          const payload = {
            placementId: placement.id,
            title: values.title,
            platformName: values.platformName,
            contentForm: values.contentForm || '图文',
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
          await reloadItems(placement.id);
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
          if (placement) void reloadItems(placement.id);
        }}
        footer={null}
        width={820}
        destroyOnClose
      >
        {editable ? (
          <div className='mb-3 flex justify-end'>
            <PermissionButton
              type='primary'
              perm={editPerm}
              onClick={() => {
                setEditingCite(null);
                setCiteFormOpen(true);
              }}
            >
              新增引用
            </PermissionButton>
          </div>
        ) : null}
        <Table<GeoContentPlacementCite>
          rowKey='id'
          loading={citeLoading}
          dataSource={cites}
          pagination={false}
          size='small'
          scroll={{ x: 'max-content' }}
          columns={[
            ...(editable
              ? [
                  {
                    title: '操作',
                    width: 120,
                    fixed: 'left' as const,
                    render: (_: unknown, row: GeoContentPlacementCite) => (
                      <Space size='small'>
                        <PermissionButton
                          type='link'
                          className='px-0'
                          perm={editPerm}
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
                          perm={editPerm}
                          onClick={async () => {
                            await deleteGeoContentPlacementCiteApi(row.id);
                            message.success('已删除');
                            if (placement && citeItem) await reloadCites(placement.id, citeItem.id);
                          }}
                        >
                          删除
                        </PermissionButton>
                      </Space>
                    ),
                  } as any,
                ]
              : []),
            { title: '提问问题', dataIndex: 'askQuestion', ellipsis: true },
            { title: 'AI平台', dataIndex: 'aiPlatform', width: 90 },
            {
              title: '引用链接',
              dataIndex: 'citeUrl',
              ellipsis: true,
              render: (v?: string) => (
                <ExternalLinkText
                  href={v}
                  drawerTitle='引用链接'
                />
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
          if (!placement || !citeItem) return false;
          const payload = {
            placementId: placement.id,
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
          await reloadCites(placement.id, citeItem.id);
          return true;
        }}
      />
    </>
  );
});

export default PlacementExecutionPanel;
