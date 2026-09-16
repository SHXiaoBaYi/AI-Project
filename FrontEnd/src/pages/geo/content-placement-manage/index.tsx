import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Modal, Progress, Tabs, Tag, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PlacementExecutionPanel from '@/components/geo/content-placement/PlacementExecutionPanel';
import PublisherWeeklyBoard from '@/components/geo/content-placement/PublisherWeeklyBoard';
import { AGG_COLOR, SOURCE_COLOR } from '@/components/geo/content-placement/constants';
import {
  createGeoContentPlacementApi,
  deleteGeoContentPlacementApi,
  generateSimilarGeoContentPlacementApi,
  getGeoContentPlacementListApi,
  getGeoOwnerOptionsApi,
  getGeoTopicOptionsApi,
  importGeoContentPlacementApi,
  updateGeoContentPlacementApi,
} from '@/api/geo';
import type { GeoContentPlacementListItem, GeoOwnerOption, GeoTopic } from '@/types/geo';
import { GEO_CONTENT_AGG_STATUS, GEO_CONTENT_SOURCES } from '@/constants/geo';

/** 管理视角：目标问题生成/分配 + 发布人周看板 */
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
  const [sourceOpen, setSourceOpen] = useState(false);
  const [sourceRecord, setSourceRecord] = useState<GeoContentPlacementListItem | null>(null);
  const [generatingId, setGeneratingId] = useState<number | null>(null);

  useEffect(() => {
    void (async () => {
      const [ownerOpts, topicOpts] = await Promise.all([getGeoOwnerOptionsApi(), getGeoTopicOptionsApi()]);
      setOwners(ownerOpts);
      setTopics(topicOpts);
    })();
  }, []);

  const userOptions = owners.map((u) => ({ label: u.displayName, value: u.userId }));
  const topicOptions = topics.map((t) => ({ label: t.topicName, value: t.id }));

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
      width: 240,
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
      title: '备注',
      dataIndex: 'remark',
      search: false,
      hideInTable: true,
      valueType: 'textarea',
    },
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
              onClick: () => handleGenerateSimilar(record),
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

  const questionPanel = (
    <>
      <BaseProTable<GeoContentPlacementListItem>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='目标问题 · 分配 · 进度'
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
    </>
  );

  return (
    <Tabs
      type='card'
      items={[
        { key: 'board', label: '发布人周看板', children: <PublisherWeeklyBoard /> },
        { key: 'questions', label: '目标问题管理', children: questionPanel },
      ]}
    />
  );
});

export default ContentPlacementManagePage;
