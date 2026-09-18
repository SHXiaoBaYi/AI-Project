import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Modal, Progress, Select, Tag, Upload } from 'antd';
import { InboxOutlined, DownloadOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PlacementExecutionPanel from '@/components/geo/content-placement/PlacementExecutionPanel';
import PlacementProofFilesModal from '@/components/geo/content-placement/PlacementProofFilesModal';
import { AGG_COLOR, SOURCE_COLOR } from '@/components/geo/content-placement/constants';
import {
  createGeoContentPlacementApi,
  deleteGeoContentPlacementApi,
  generateSimilarGeoContentPlacementApi,
  getGeoAiProvidersApi,
  getGeoContentPlacementListApi,
  getGeoOwnerOptionsApi,
  getGeoTopicOptionsApi,
  importGeoContentPlacementApi,
  downloadGeoContentPlacementTemplateApi,
  updateGeoContentPlacementApi,
} from '@/api/geo';
import type { GeoContentPlacementListItem, GeoOwnerOption, GeoTopic } from '@/types/geo';
import { GEO_CONTENT_AGG_STATUS, GEO_CONTENT_SOURCES } from '@/constants/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

type AiProviderOption = {
  provider: string;
  label: string;
  model: string;
  available: boolean;
  hint?: string;
};

/** 管理视角：目标问题生成/分配与投放进度 */
const ContentPlacementManagePage = memo(function ContentPlacementManagePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [placementOpen, setPlacementOpen] = useState(false);
  const [editingPlacement, setEditingPlacement] = useState<GeoContentPlacementListItem | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentPlacement, setCurrentPlacement] = useState<GeoContentPlacementListItem | null>(null);
  const [proofOpen, setProofOpen] = useState(false);
  const [proofPlacement, setProofPlacement] = useState<GeoContentPlacementListItem | null>(null);
  const [sourceOpen, setSourceOpen] = useState(false);
  const [sourceRecord, setSourceRecord] = useState<GeoContentPlacementListItem | null>(null);
  const [generatingId, setGeneratingId] = useState<number | null>(null);
  const [aiProviders, setAiProviders] = useState<AiProviderOption[]>([]);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [generateRecord, setGenerateRecord] = useState<GeoContentPlacementListItem | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<string>('local');

  useEffect(() => {
    void (async () => {
      const [ownerOpts, topicOpts] = await Promise.all([getGeoOwnerOptionsApi(), getGeoTopicOptionsApi()]);
      setOwners(ownerOpts);
      setTopics(topicOpts);
    })();
  }, []);

  useEffect(() => {
    void getGeoAiProvidersApi()
      .then((list) => {
        setAiProviders(list ?? []);
        const preferred =
          list?.find((p) => p.available && p.provider !== 'local')?.provider ||
          list?.find((p) => p.available)?.provider ||
          'local';
        setSelectedProvider(preferred);
      })
      .catch(() => {
        setAiProviders([{ provider: 'local', label: '本地模板', model: 'local-template', available: true }]);
        setSelectedProvider('local');
      });
  }, []);

  const userOptions = owners.map((u) => ({ label: u.displayName, value: u.userId }));
  const topicOptions = topics.map((t) => ({ label: t.topicName, value: t.id }));

  const openGenerateModal = (record: GeoContentPlacementListItem) => {
    setGenerateRecord(record);
    setGenerateOpen(true);
    void getGeoAiProvidersApi()
      .then((list) => {
        const next = list ?? [];
        setAiProviders(next);
        const preferred =
          next.find((p) => p.available && p.provider !== 'local')?.provider ||
          next.find((p) => p.available)?.provider ||
          'local';
        setSelectedProvider(preferred);
      })
      .catch(() => {
        setAiProviders([{ provider: 'local', label: '本地模板', model: 'local-template', available: true }]);
        setSelectedProvider('local');
      });
  };

  const handleGenerateSimilar = async () => {
    if (!generateRecord) return;
    setGeneratingId(generateRecord.id);
    try {
      const rows = await generateSimilarGeoContentPlacementApi(generateRecord.id, selectedProvider);
      const providerLabel = aiProviders.find((p) => p.provider === selectedProvider)?.label || selectedProvider;
      message.success(`已用「${providerLabel}」生成 ${rows.length} 条相似问题（待分配）`);
      setGenerateOpen(false);
      setGenerateRecord(null);
      actionRef.current?.reload();
    } finally {
      setGeneratingId(null);
    }
  };

  const columns: ProColumnType<GeoContentPlacementListItem>[] = [
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
      title: '撰写人',
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
      width: 240,
      formItemProps: { rules: [{ required: true, message: '请输入目标问题' }] },
    },
    {
      title: '来源',
      dataIndex: 'source',
      width: 160,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_SOURCES.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_SOURCES], allowClear: true },
      hideInForm: true,
      render: (_, record) => {
        const source = record.source || '手动新增';
        if (source === 'AI生成') {
          const model = record.sourceAiModel?.trim();
          return (
            <Tag
              color={SOURCE_COLOR[source]}
              className='cursor-pointer'
              onClick={() => {
                setSourceRecord(record);
                setSourceOpen(true);
              }}
            >
              {model ? `AI生成 · ${model}` : 'AI生成 · 查看参照'}
            </Tag>
          );
        }
        return <Tag color={SOURCE_COLOR[source] || 'default'}>{source}</Tag>;
      },
    },
    {
      title: '投放进度',
      dataIndex: 'aggregateStatus',
      width: 180,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_AGG_STATUS.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_AGG_STATUS], allowClear: true },
      hideInForm: true,
      render: (_, record) => {
        const status = record.aggregateStatus || '未投放';
        if (record.publishProgress == null || !record.platformCount) {
          return (
            <Tag
              color={AGG_COLOR[status] || 'default'}
              className='cursor-pointer'
              onClick={() => {
                setCurrentPlacement(record);
                setDrawerOpen(true);
              }}
            >
              {status}
            </Tag>
          );
        }
        return (
          <div className='flex min-w-[140px] flex-col gap-1'>
            <Tag
              color={AGG_COLOR[status] || 'default'}
              className='w-fit cursor-pointer'
              onClick={() => {
                setCurrentPlacement(record);
                setDrawerOpen(true);
              }}
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
      title: '引用数',
      dataIndex: 'citeCount',
      width: 80,
      search: false,
      hideInForm: true,
      render: (v) => v ?? 0,
    },
    {
      title: '附件',
      dataIndex: 'proofFileCount',
      width: 90,
      search: false,
      hideInForm: true,
      render: (_, r) => {
        const n = r.proofFileCount ?? 0;
        if (n <= 0) return '-';
        return (
          <a
            onClick={(e) => {
              e.preventDefault();
              setProofPlacement(r);
              setProofOpen(true);
            }}
          >
            {n} 个
          </a>
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
    createTimeRangeColumn<GeoContentPlacementListItem>({ defaultDemoRange: true }),
    createTimeDisplayColumn<GeoContentPlacementListItem>(),
    {
      title: '操作',
      valueType: 'option',
      width: 280,
      hideInForm: true,
      render: (_, record) => (
        <ActionButtons
          maxVisible={4}
          items={[
            {
              key: 'progress',
              label: '进度详情',
              perm: 'geo:content:list',
              onClick: () => {
                setCurrentPlacement(record);
                setDrawerOpen(true);
              },
            },
            {
              key: 'generate',
              label: generatingId === record.id ? '生成中...' : '生成相似问题',
              perm: 'geo:content:generate',
              disabled: generatingId === record.id,
              onClick: () => openGenerateModal(record),
            },
            {
              key: 'edit',
              label: '分配/编辑',
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
              confirmTitle: '确定删除该目标问题？',
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

  return (
    <>
      <BaseProTable<GeoContentPlacementListItem>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='投放管理'
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
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
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
            新增目标问题
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
        title={editingPlacement ? '分配 / 编辑目标问题' : '新增目标问题'}
        columns={columns as any}
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

      <Modal
        title='生成相似问题'
        open={generateOpen}
        onCancel={() => {
          if (generatingId) return;
          setGenerateOpen(false);
          setGenerateRecord(null);
        }}
        onOk={() => void handleGenerateSimilar()}
        okText={generatingId ? '生成中...' : '开始生成'}
        confirmLoading={!!generatingId}
        destroyOnHidden
      >
        <div className='mb-3 text-sm text-neutral-600'>源问题：{generateRecord?.targetQuestion || '-'}</div>
        <div className='mb-1 text-sm text-neutral-700'>选择生成模型</div>
        <Select
          className='w-full'
          value={selectedProvider}
          onChange={setSelectedProvider}
          options={aiProviders.map((p) => ({
            value: p.provider,
            label: `${p.label}（${p.model}）`,
          }))}
        />
        <div className='mt-2 text-xs text-neutral-500'>
          {aiProviders.find((p) => p.provider === selectedProvider)?.hint ||
            '未在「系统管理 → AI模型配置」中填写 Key 时，仅可使用本地模板启发式生成。'}
        </div>
      </Modal>

      <BaseModalForm
        title='导入内容投放 Excel'
        open={importOpen}
        onOpenChange={(v) => {
          if (!v) setFile(null);
          setImportOpen(v);
        }}
        submitter={false}
      >
        <div className='mb-3 flex flex-wrap items-center gap-2'>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => {
              downloadGeoContentPlacementTemplateApi().catch(() => message.error('模板下载失败'));
            }}
          >
            下载导入模板
          </Button>
          <span className='text-sm text-neutral-400'>
            黄底红字为必填；橙色行为模板示例，导入时会自动跳过。话题对得上自动关联，对不上自动建档并关联
          </span>
        </div>
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
              const head = `导入完成：新增 ${res.insertCount}，更新 ${res.updateCount}，失败 ${res.failureCount}`;
              const detail = (res.errors ?? [])
                .slice(0, 3)
                .map((e) => `第${e.rowIndex}行：${e.message}`)
                .join('；');
              if (res.failureCount > 0) {
                message.warning(detail ? `${head}。${detail}` : head);
              } else {
                message.success(head);
              }
              setImportOpen(false);
              setFile(null);
              actionRef.current?.reload();
            }}
          >
            开始导入
          </Button>
        </div>
      </BaseModalForm>

      <PlacementExecutionPanel
        open={drawerOpen}
        placement={currentPlacement}
        mode='view'
        onClose={() => {
          setDrawerOpen(false);
          setCurrentPlacement(null);
          actionRef.current?.reload();
        }}
      />

      <PlacementProofFilesModal
        open={proofOpen}
        placementId={proofPlacement?.id ?? null}
        title={proofPlacement ? `附件：${proofPlacement.targetQuestion || proofPlacement.id}` : '附件'}
        onClose={() => {
          setProofOpen(false);
          setProofPlacement(null);
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
            <div className='mb-1 text-neutral-500'>生成模型</div>
            <div>{sourceRecord?.sourceAiModel || '-'}</div>
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
    </>
  );
});

export default ContentPlacementManagePage;
