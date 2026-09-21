import { memo, useEffect, useState } from 'react';
import type { ProColumnType } from '@ant-design/pro-components';
import { App, Button, Drawer, Modal, Space, Table, Tag } from 'antd';
import dayjs from 'dayjs';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ExternalLinkText } from '@/components/ExternalLinkDrawer';
import GeoScreenshot from '@/components/geo/GeoScreenshot';
import { CiteScreenshotUpload } from '@/components/geo/CiteScreenshotUpload';
import {
  createGeoContentPlacementCiteApi,
  createGeoContentPlacementItemApi,
  deleteGeoContentPlacementCiteApi,
  deleteGeoContentPlacementItemApi,
  getGeoContentPlacementCitesApi,
  getGeoContentPlacementItemsApi,
  getGeoContentPlacementProofFilesApi,
  getGeoPlatformOptionsApi,
  updateGeoContentPlacementCiteApi,
  updateGeoContentPlacementItemApi,
} from '@/api/geo';
import type {
  GeoContentPlacementCite,
  GeoContentPlacementItem,
  GeoContentPlacementListItem,
  GeoContentPlacementProofFile,
  GeoPlatform,
} from '@/types/geo';
import {
  GEO_CONTENT_FORMS,
  GEO_CONTENT_PUBLISH_STATUS,
  GEO_PLATFORM_TYPE_AI,
  GEO_PLATFORM_TYPE_CONTENT,
} from '@/constants/geo';
import { AGG_COLOR, STATUS_COLOR, canOpenExternalInApp, derivePlacementProgress } from './constants';

type Props = {
  open: boolean;
  placement: GeoContentPlacementListItem | null;
  /** view=只读进度；edit=一线维护投放/引用 */
  mode?: 'view' | 'edit';
  editPerm?: string;
  onClose: () => void;
  onChanged?: (next: Partial<GeoContentPlacementListItem>) => void;
};

const fileHref = (url?: string) => {
  if (!url) return '#';
  if (/^https?:\/\//i.test(url)) return url;
  return `${String(import.meta.env.VITE_API_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')}${url}`;
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
  const [taskFiles, setTaskFiles] = useState<GeoContentPlacementProofFile[]>([]);

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

  const reloadTaskFiles = async (placementId: number) => {
    try {
      setTaskFiles(await getGeoContentPlacementProofFilesApi(placementId));
    } catch {
      setTaskFiles([]);
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
      void reloadTaskFiles(placement.id);
    } else {
      setItems([]);
      setTaskFiles([]);
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
    {
      title: '投放链接',
      dataIndex: 'publishUrl',
      formItemProps: {
        rules: [
          { required: true, message: '请填写投放链接' },
          {
            validator: (_: unknown, value: unknown) =>
              canOpenExternalInApp(String(value ?? ''))
                ? Promise.resolve()
                : Promise.reject(new Error('投放链接必须是 http 或 https 链接')),
          },
        ],
      },
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      valueType: 'dateTime',
      fieldProps: { format: 'YYYY-MM-DD HH:mm:ss' },
      formItemProps: { rules: [{ required: true, message: '请选择发布时间' }] },
    },
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
    {
      title: '引用链接',
      dataIndex: 'citeUrl',
      dependencies: ['screenshotUrl'],
      formItemProps: {
        extra: '与截图二选一，至少填一项',
        rules: [
          ({ getFieldValue }: { getFieldValue: (name: string) => unknown }) => ({
            validator(_: unknown, value: unknown) {
              if (String(value ?? '').trim() || String(getFieldValue('screenshotUrl') ?? '').trim()) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('请填写引用链接或上传截图'));
            },
          }),
        ],
      },
    },
    {
      title: '截图',
      dataIndex: 'screenshotUrl',
      colProps: { span: 24 },
      dependencies: ['citeUrl'],
      formItemProps: {
        extra: '与引用链接二选一，至少填一项',
        rules: [
          ({ getFieldValue }: { getFieldValue: (name: string) => unknown }) => ({
            validator(_: unknown, value: unknown) {
              if (String(value ?? '').trim() || String(getFieldValue('citeUrl') ?? '').trim()) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('请填写引用链接或上传截图'));
            },
          }),
        ],
      },
      formItemRender: () => <CiteScreenshotUpload />,
    },
    { title: '备注', dataIndex: 'remark', valueType: 'textarea' },
  ];

  return (
    <>
      <Drawer
        title={placement?.targetQuestion || '发布详情'}
        width='70%'
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
              发布人：{placement.publisherName || '-'}　撰写人：{placement.ownerName || '-'}　话题：
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
        {taskFiles.length > 0 ? (
          <div className='mb-4 rounded border border-neutral-200 bg-neutral-50 px-3 py-2'>
            <div className='mb-2 text-sm font-medium text-neutral-700'>附件</div>
            <ul className='m-0 list-none space-y-2 p-0'>
              {taskFiles.map((f) => (
                <li
                  key={f.id}
                  className='flex items-start justify-between gap-2 text-sm'
                >
                  <div className='min-w-0 flex-1'>
                    <div className='truncate font-medium'>{f.fileName}</div>
                    <div className='mt-0.5 text-xs text-neutral-500'>
                      {f.taskType ? `${f.taskType} · ` : ''}
                      任务：
                      {f.taskTitle?.trim() ? f.taskTitle : f.taskId != null ? `（未取到标题）#${f.taskId}` : '-'}
                    </div>
                    <div className='text-xs text-neutral-400'>
                      上传人：{f.uploadUserName || '-'}
                      {f.createTime ? ` · ${f.createTime}` : ''}
                    </div>
                  </div>
                  <a
                    className='shrink-0'
                    href={fileHref(f.fileUrl)}
                    target='_blank'
                    rel='noreferrer'
                    download={f.fileName}
                  >
                    查看/下载
                  </a>
                </li>
              ))}
            </ul>
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
              width: 170,
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
            publishTime: values.publishTime ? dayjs(values.publishTime).format('YYYY-MM-DD HH:mm:ss') : undefined,
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
            {
              title: '截图',
              dataIndex: 'screenshotUrl',
              width: 80,
              render: (v?: string) => (
                <GeoScreenshot
                  src={v}
                  trigger='link'
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
            screenshotUrl: values.screenshotUrl,
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
