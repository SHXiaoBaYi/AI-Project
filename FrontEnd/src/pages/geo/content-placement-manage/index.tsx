import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, InputNumber, Modal, Progress, Select, Table, Tag, Upload } from 'antd';
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
  deleteGeoContentPlacementBatchApi,
  generateSimilarGeoContentPlacementApi,
  generateSimilarGeoContentPlacementBatchApi,
  saveSimilarGeoContentPlacementApi,
  getGeoAiProvidersApi,
  getGeoContentPlacementListApi,
  getGeoOwnerOptionsApi,
  getGeoTopicOptionsApi,
  downloadGeoContentPlacementTemplateApi,
  waitGeoImportJob,
  startGeoContentPlacementImportApi,
  getGeoContentPlacementImportProgressApi,
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

type SimilarPreview = {
  key: string;
  sourcePlacementId: number;
  sourceQuestion?: string;
  targetQuestion: string;
  sourceAiModel?: string;
  duplicated?: boolean;
};

/** 管理视角：目标问题生成/分配与投放进度 */
const ContentPlacementManagePage = memo(function ContentPlacementManagePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importPercent, setImportPercent] = useState(0);
  const [importText, setImportText] = useState('');
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
  const [batchGenerating, setBatchGenerating] = useState(false);
  const [aiProviders, setAiProviders] = useState<AiProviderOption[]>([]);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [batchGenerateOpen, setBatchGenerateOpen] = useState(false);
  const [generateRecord, setGenerateRecord] = useState<GeoContentPlacementListItem | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<string>('local');
  const [generateCount, setGenerateCount] = useState(5);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewRows, setPreviewRows] = useState<SimilarPreview[]>([]);
  const [pickedKeys, setPickedKeys] = useState<string[]>([]);
  const [savingPreview, setSavingPreview] = useState(false);

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

  const openPreview = (rows: SimilarPreview[]) => {
    setPreviewRows(rows);
    setPickedKeys(rows.filter((row) => !row.duplicated).map((row) => row.key));
    setPreviewOpen(true);
  };

  const handleGenerateSimilar = async () => {
    if (!generateRecord) return;
    const count = Math.min(10, Math.max(1, generateCount || 1));
    setGeneratingId(generateRecord.id);
    try {
      const rows = await generateSimilarGeoContentPlacementApi(generateRecord.id, selectedProvider, count);
      openPreview(
        (rows ?? []).map((row, index) => ({
          key: `${row.sourcePlacementId}-${index}`,
          sourcePlacementId: row.sourcePlacementId,
          sourceQuestion: row.sourceQuestion,
          targetQuestion: row.targetQuestion,
          sourceAiModel: row.sourceAiModel,
          duplicated: !!row.duplicated,
        })),
      );
      setGenerateOpen(false);
    } finally {
      setGeneratingId(null);
    }
  };

  const handleBatchGenerateSimilar = async () => {
    if (selectedRowKeys.length === 0) return;
    const count = Math.min(10, Math.max(1, generateCount || 1));
    setBatchGenerating(true);
    try {
      const rows = await generateSimilarGeoContentPlacementBatchApi(
        selectedRowKeys as number[],
        selectedProvider,
        count,
      );
      openPreview(
        (rows ?? []).map((row, index) => ({
          key: `${row.sourcePlacementId}-${index}`,
          sourcePlacementId: row.sourcePlacementId,
          sourceQuestion: row.sourceQuestion,
          targetQuestion: row.targetQuestion,
          sourceAiModel: row.sourceAiModel,
          duplicated: !!row.duplicated,
        })),
      );
      setBatchGenerateOpen(false);
    } finally {
      setBatchGenerating(false);
    }
  };

  const handleSavePreview = async () => {
    const picked = previewRows.filter((row) => pickedKeys.includes(row.key) && !row.duplicated);
    if (picked.length === 0) {
      message.warning('请勾选要落库的相似问题');
      return;
    }
    setSavingPreview(true);
    try {
      const saved = await saveSimilarGeoContentPlacementApi(
        picked.map((row) => ({
          sourcePlacementId: row.sourcePlacementId,
          targetQuestion: row.targetQuestion,
          sourceAiModel: row.sourceAiModel,
        })),
      );
      message.success(`已落库 ${saved?.length ?? picked.length} 条，并生成对应任务`);
      setPreviewOpen(false);
      setPreviewRows([]);
      setPickedKeys([]);
      setGenerateRecord(null);
      setSelectedRowKeys([]);
      currentPageKeysRef.current = new Set();
      actionRef.current?.reload();
    } finally {
      setSavingPreview(false);
    }
  };

  const renderAggTag = (record: GeoContentPlacementListItem, status: string) => {
    if (status === '待投放') {
      return (
        <Tag
          className='w-fit cursor-pointer !border-transparent !bg-red-600 !text-white'
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
    );
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
          return renderAggTag(record, status);
        }
        return (
          <div className='flex min-w-[140px] flex-col gap-1'>
            {renderAggTag(record, status)}
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
    createTimeRangeColumn<GeoContentPlacementListItem>(),
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
        scroll={{ x: 1400 }}
        rowClassName={(record) => (record.aggregateStatus === '待投放' ? 'bg-red-50' : '')}
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
            key='batchGenerate'
            perm='geo:content:generate'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要生成相似问题的记录');
                return;
              }
              setBatchGenerateOpen(true);
            }}
          >
            批量生成相似问题
          </PermissionButton>,
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='geo:content:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的记录');
                return;
              }
              Modal.confirm({
                title: '批量删除内容投放',
                content: `确定要删除选中的 ${selectedRowKeys.length} 条目标问题吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoContentPlacementBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条`);
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
        <div className='mb-1 text-sm text-neutral-700'>生成数量</div>
        <InputNumber
          className='mb-3 w-full'
          min={1}
          max={10}
          precision={0}
          value={generateCount}
          onChange={(value) => setGenerateCount(value ?? 1)}
        />
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

      <Modal
        title='批量生成相似问题'
        open={batchGenerateOpen}
        onCancel={() => {
          if (batchGenerating) return;
          setBatchGenerateOpen(false);
        }}
        onOk={() => void handleBatchGenerateSimilar()}
        okText={batchGenerating ? '生成中...' : '开始生成'}
        confirmLoading={batchGenerating}
        destroyOnHidden
      >
        <div className='mb-3 text-sm text-neutral-600'>已选 {selectedRowKeys.length} 条记录，每条按下面的数量生成</div>
        <div className='mb-1 text-sm text-neutral-700'>每条生成数量</div>
        <InputNumber
          className='mb-3 w-full'
          min={1}
          max={10}
          precision={0}
          value={generateCount}
          onChange={(value) => setGenerateCount(value ?? 1)}
        />
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
      </Modal>

      <Modal
        title='选择要落库的相似问题'
        open={previewOpen}
        width={760}
        maskClosable={false}
        okText='落库并生成任务'
        cancelText='放弃'
        confirmLoading={savingPreview}
        onCancel={() => {
          if (savingPreview) return;
          setPreviewOpen(false);
          setPreviewRows([]);
          setPickedKeys([]);
        }}
        onOk={() => void handleSavePreview()}
      >
        <p className='mb-3 text-sm text-neutral-500'>
          还没有写入数据库，也没有生成任务。勾选需要的问题后再落库。已存在的问题不能选择。
        </p>
        <Table<SimilarPreview>
          rowKey='key'
          size='small'
          pagination={false}
          dataSource={previewRows}
          scroll={{ y: 360 }}
          rowSelection={{
            selectedRowKeys: pickedKeys,
            onChange: (keys) => setPickedKeys(keys as string[]),
            getCheckboxProps: (row) => ({ disabled: !!row.duplicated }),
          }}
          columns={[
            {
              title: '相似问题',
              dataIndex: 'targetQuestion',
              render: (value: string, row) => (
                <span>
                  {value}
                  {row.duplicated ? (
                    <Tag
                      className='ml-2'
                      color='default'
                    >
                      已存在
                    </Tag>
                  ) : null}
                </span>
              ),
            },
            { title: '来源问题', dataIndex: 'sourceQuestion', width: 220, ellipsis: true },
            { title: '模型', dataIndex: 'sourceAiModel', width: 140, ellipsis: true },
          ]}
        />
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
        {importing ? (
          <div className='mt-4'>
            <Progress
              percent={importPercent}
              status='active'
            />
            <div className='text-sm text-neutral-500'>{importText || '正在导入'}</div>
          </div>
        ) : null}
        <div className='mt-4 flex justify-end'>
          <Button
            type='primary'
            loading={importing}
            disabled={!file || importing}
            onClick={async () => {
              if (!file || importing) return;
              setImporting(true);
              setImportPercent(0);
              setImportText('正在上传');
              try {
                const res = await waitGeoImportJob(
                  () => startGeoContentPlacementImportApi(file),
                  getGeoContentPlacementImportProgressApi,
                  (job) => {
                    setImportPercent(job.percent ?? 0);
                    setImportText(job.message || '正在导入');
                  },
                );
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
              } catch (e) {
                const err = e as { geoImport?: boolean };
                if (err.geoImport) {
                  message.error(e instanceof Error ? e.message : '导入失败');
                }
              } finally {
                setImporting(false);
              }
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
