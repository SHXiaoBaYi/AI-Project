import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Image, Input, Modal, Select, Space, Tag, Upload } from 'antd';
import { CloudUploadOutlined, DeleteOutlined, EditOutlined, PlusOutlined, TagsOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  batchUpdateGeoMediaMetaApi,
  batchUploadGeoMediaApi,
  createGeoMediaApi,
  deleteGeoMediaApi,
  deleteGeoMediaBatchApi,
  getGeoMediaListApi,
  getGeoTopicOptionsApi,
  updateGeoMediaApi,
  uploadGeoMediaApi,
} from '@/api/geo';
import type { GeoMediaAsset, GeoTopic } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';
import { withBase } from '@/utils/basePath';

function resolveImg(url?: string) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  return withBase(`/api${url.startsWith('/') ? url : `/${url}`}`);
}

function splitTags(raw: string) {
  return raw
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean);
}

const emptyForm = () => ({
  title: '',
  fileUrl: '',
  sourceType: 'own' as string,
  licenseNote: '',
  brandVisible: 0,
  caption: '',
  altText: '',
  status: 'active',
  remark: '',
  topicIds: [] as number[],
  tags: '',
  usageHint: '',
  sceneTags: '',
});

const MediaPage = memo(function MediaPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [open, setOpen] = useState(false);
  const [batchUploadOpen, setBatchUploadOpen] = useState(false);
  const [batchMetaOpen, setBatchMetaOpen] = useState(false);
  const [editing, setEditing] = useState<GeoMediaAsset | null>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [uploading, setUploading] = useState(false);
  const [batchFiles, setBatchFiles] = useState<File[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [batchMeta, setBatchMeta] = useState({
    usageHint: '',
    tags: '',
    sceneTags: '',
    topicIds: [] as number[],
    clearTopics: false,
    brandVisible: undefined as number | undefined,
    status: undefined as string | undefined,
  });
  const [batchUploadMeta, setBatchUploadMeta] = useState({
    usageHint: '',
    tags: '',
    sceneTags: '',
    sourceType: 'own',
    licenseNote: '',
  });

  useEffect(() => {
    void getGeoTopicOptionsApi()
      .then((list) => setTopics(list ?? []))
      .catch(() => setTopics([]));
  }, []);

  const topicOptions = useMemo(() => (topics ?? []).map((t) => ({ label: t.topicName, value: t.id })), [topics]);

  const resetForm = useCallback(() => {
    setForm(emptyForm());
    setEditing(null);
  }, []);

  const openCreate = () => {
    resetForm();
    setOpen(true);
  };

  const openEdit = (row: GeoMediaAsset) => {
    setEditing(row);
    setForm({
      title: row.title || '',
      fileUrl: row.fileUrl || '',
      sourceType: row.sourceType || 'own',
      licenseNote: row.licenseNote || '',
      brandVisible: row.brandVisible ? 1 : 0,
      caption: row.caption || '',
      altText: row.altText || '',
      status: row.status || 'active',
      remark: row.remark || '',
      topicIds: row.topicIds ?? [],
      tags: (row.tags ?? []).join(','),
      usageHint: row.usageHint || '',
      sceneTags: (row.sceneTags ?? []).join(','),
    });
    setOpen(true);
  };

  const submit = async () => {
    if (!form.title.trim()) {
      message.warning('请填写标题');
      return;
    }
    if (!form.fileUrl.trim()) {
      message.warning('请先上传图片');
      return;
    }
    if (form.sourceType === 'stock_licensed' && !form.licenseNote.trim()) {
      message.warning('授权库存图请填写授权说明');
      return;
    }
    const payload = {
      title: form.title.trim(),
      fileUrl: form.fileUrl.trim(),
      sourceType: form.sourceType,
      licenseNote: form.licenseNote.trim(),
      brandVisible: form.brandVisible,
      caption: form.caption.trim(),
      altText: form.altText.trim(),
      status: form.status,
      remark: form.remark.trim(),
      topicIds: form.topicIds,
      tags: splitTags(form.tags),
      usageHint: form.usageHint.trim(),
      sceneTags: splitTags(form.sceneTags),
    };
    if (editing?.id) {
      await updateGeoMediaApi({ ...payload, id: editing.id });
      message.success('已更新');
    } else {
      await createGeoMediaApi(payload);
      message.success('已新增');
    }
    setOpen(false);
    resetForm();
    actionRef.current?.reload();
  };

  const submitBatchUpload = async () => {
    if (!batchFiles.length) {
      message.warning('请选择图片');
      return;
    }
    if (batchUploadMeta.sourceType === 'stock_licensed' && !batchUploadMeta.licenseNote.trim()) {
      message.warning('授权库存图请填写授权说明');
      return;
    }
    setUploading(true);
    try {
      const res = await batchUploadGeoMediaApi(batchFiles, {
        usageHint: batchUploadMeta.usageHint.trim() || undefined,
        tags: batchUploadMeta.tags.trim() || undefined,
        sceneTags: batchUploadMeta.sceneTags.trim() || undefined,
        sourceType: batchUploadMeta.sourceType,
        licenseNote: batchUploadMeta.licenseNote.trim() || undefined,
      });
      message.success(`已入库 ${res.count} 张，可再「批量改用途」细化`);
      setBatchUploadOpen(false);
      setBatchFiles([]);
      actionRef.current?.reload();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '批量上传失败');
    } finally {
      setUploading(false);
    }
  };

  const submitBatchMeta = async () => {
    if (!selectedRowKeys.length) {
      message.warning('请先勾选素材');
      return;
    }
    const payload: Parameters<typeof batchUpdateGeoMediaMetaApi>[0] = {
      ids: selectedRowKeys.map(Number),
    };
    if (batchMeta.usageHint.trim()) payload.usageHint = batchMeta.usageHint.trim();
    if (batchMeta.tags.trim()) payload.tags = splitTags(batchMeta.tags);
    if (batchMeta.sceneTags.trim()) payload.sceneTags = splitTags(batchMeta.sceneTags);
    if (batchMeta.clearTopics) payload.topicIds = [];
    else if (batchMeta.topicIds.length) payload.topicIds = batchMeta.topicIds;
    if (batchMeta.brandVisible != null) payload.brandVisible = batchMeta.brandVisible;
    if (batchMeta.status) payload.status = batchMeta.status;
    if (
      !payload.usageHint &&
      !payload.tags &&
      !payload.sceneTags &&
      payload.topicIds == null &&
      payload.brandVisible == null &&
      !payload.status
    ) {
      message.warning('请至少填写一项要修改的内容');
      return;
    }
    const res = await batchUpdateGeoMediaMetaApi(payload);
    message.success(`已更新 ${res.updated} 条`);
    setBatchMetaOpen(false);
    actionRef.current?.reload();
  };

  const columns: ProColumnType<GeoMediaAsset>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        key: 'option',
        width: 120,
        fixed: 'left',
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined />,
                perm: 'geo:media:edit',
                onClick: () => openEdit(record),
              },
              {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined />,
                perm: 'geo:media:delete',
                confirmTitle: '确定删除该素材？',
                onClick: async () => {
                  await deleteGeoMediaApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
      {
        title: '预览',
        dataIndex: 'fileUrl',
        search: false,
        width: 88,
        render: (_, r) => (
          <Image
            src={r.publicUrl || resolveImg(r.fileUrl)}
            width={56}
            height={56}
            className='rounded object-cover'
            fallback=''
          />
        ),
      },
      { title: '标题', dataIndex: 'title', ellipsis: true },
      {
        title: '用途/可用于',
        dataIndex: 'usageHint',
        search: false,
        ellipsis: true,
        width: 200,
        render: (_, r) => r.usageHint || <span className='text-neutral-400'>未填写</span>,
      },
      {
        title: '标签',
        dataIndex: 'tags',
        search: false,
        width: 160,
        render: (_, r) =>
          (r.tags ?? []).length ? (
            <Space
              size={[4, 4]}
              wrap
            >
              {(r.tags ?? []).slice(0, 4).map((t) => (
                <Tag key={t}>{t}</Tag>
              ))}
            </Space>
          ) : (
            '-'
          ),
      },
      {
        title: '关键词',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: { placeholder: '标题/用途/标签' },
      },
      {
        title: '话题(软优先)',
        dataIndex: 'topicNames',
        search: false,
        width: 140,
        render: (_, r) =>
          (r.topicNames ?? []).length ? (
            <Space
              size={[4, 4]}
              wrap
            >
              {(r.topicNames ?? []).map((n) => (
                <Tag key={n}>{n}</Tag>
              ))}
            </Space>
          ) : (
            <span className='text-neutral-400'>未绑</span>
          ),
      },
      {
        title: '话题筛选',
        dataIndex: 'topicId',
        hideInTable: true,
        valueType: 'select',
        fieldProps: {
          options: topicOptions,
          showSearch: true,
          optionFilterProp: 'label',
          allowClear: true,
        },
      },
      {
        title: '来源',
        dataIndex: 'sourceType',
        width: 100,
        valueType: 'select',
        valueEnum: {
          own: { text: '自有' },
          stock_licensed: { text: '授权库存' },
        },
      },
      {
        title: '图中品牌',
        dataIndex: 'brandVisible',
        width: 90,
        valueType: 'select',
        valueEnum: {
          1: { text: '有', status: 'Warning' },
          0: { text: '无', status: 'Default' },
        },
        render: (_, r) => (r.brandVisible ? <Tag color='orange'>有</Tag> : <Tag>无</Tag>),
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 90,
        valueType: 'select',
        valueEnum: {
          active: { text: '生效', status: 'Success' },
          draft: { text: '草稿', status: 'Default' },
          disabled: { text: '停用', status: 'Error' },
        },
      },
      createTimeDisplayColumn(),
      createTimeRangeColumn(),
    ],
    [message, topicOptions],
  );

  return (
    <div className='flex h-full flex-col gap-3 p-4'>
      <div className='rounded-lg border border-amber-100 bg-amber-50/80 px-3 py-2 text-xs text-amber-800'>
        按「用途 / 标签」语义召回；成文时模型按段落意图自动挂图（每节可为 0
        张）。话题仅软优先，非必填。支持批量上传与批量改用途。
      </div>
      <BaseProTable<GeoMediaAsset>
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        scroll={{ x: 1200 }}
        rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            icon={<PlusOutlined />}
            perm='geo:media:add'
            onClick={openCreate}
          >
            新增素材
          </PermissionButton>,
          <PermissionButton
            key='batchUpload'
            icon={<CloudUploadOutlined />}
            perm='geo:media:add'
            onClick={() => {
              setBatchFiles([]);
              setBatchUploadMeta({
                usageHint: '',
                tags: '',
                sceneTags: '',
                sourceType: 'own',
                licenseNote: '',
              });
              setBatchUploadOpen(true);
            }}
          >
            批量上传
          </PermissionButton>,
          <PermissionButton
            key='batchMeta'
            icon={<TagsOutlined />}
            perm='geo:media:edit'
            disabled={!selectedRowKeys.length}
            onClick={() => {
              setBatchMeta({
                usageHint: '',
                tags: '',
                sceneTags: '',
                topicIds: [],
                clearTopics: false,
                brandVisible: undefined,
                status: undefined,
              });
              setBatchMetaOpen(true);
            }}
          >
            批量改用途
          </PermissionButton>,
          <PermissionButton
            key='batchDel'
            danger
            perm='geo:media:delete'
            disabled={!selectedRowKeys.length}
            onClick={() => {
              modal.confirm({
                title: `删除选中的 ${selectedRowKeys.length} 条素材？`,
                onOk: async () => {
                  await deleteGeoMediaBatchApi(selectedRowKeys.map(Number));
                  message.success('已删除');
                  setSelectedRowKeys([]);
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
        ]}
        request={async (params) => {
          const res = await getGeoMediaListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            title: params.title,
            keyword: params.keyword,
            topicId: params.topicId,
            status: params.status,
            sourceType: params.sourceType,
            brandVisible: params.brandVisible != null ? Number(params.brandVisible) : undefined,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          return { data: res.rows, total: res.total, success: true };
        }}
      />

      <Modal
        title={editing ? '编辑素材' : '新增素材'}
        open={open}
        onCancel={() => {
          setOpen(false);
          resetForm();
        }}
        onOk={() => void submit()}
        width={640}
        destroyOnHidden
      >
        <div className='flex flex-col gap-3'>
          <label className='text-xs text-neutral-500'>图片</label>
          <Upload
            listType='picture-card'
            showUploadList={false}
            accept='image/png,image/jpeg,image/jpg,image/gif,image/webp'
            customRequest={async ({ file, onSuccess, onError }) => {
              setUploading(true);
              try {
                const res = await uploadGeoMediaApi(file as File);
                setForm((f) => ({ ...f, fileUrl: res.fileUrl }));
                onSuccess?.(res);
              } catch (e) {
                onError?.(e as Error);
                message.error(e instanceof Error ? e.message : '上传失败');
              } finally {
                setUploading(false);
              }
            }}
          >
            {form.fileUrl ? (
              <img
                src={resolveImg(form.fileUrl)}
                alt=''
                className='h-full w-full object-cover'
              />
            ) : (
              <div className='text-neutral-400'>{uploading ? '上传中…' : <PlusOutlined />}</div>
            )}
          </Upload>
          <label className='text-xs text-neutral-500'>标题</label>
          <Input
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            placeholder='素材标题'
          />
          <label className='text-xs text-neutral-500'>可用于哪里（推荐填写，供 AI 挂图）</label>
          <Input.TextArea
            value={form.usageHint}
            onChange={(e) => setForm((f) => ({ ...f, usageHint: e.target.value }))}
            placeholder='如：开篇氛围图；认证背书小节；牧场实景对比'
            autoSize={{ minRows: 2, maxRows: 4 }}
          />
          <label className='text-xs text-neutral-500'>标签（逗号分隔）</label>
          <Input
            value={form.tags}
            onChange={(e) => setForm((f) => ({ ...f, tags: e.target.value }))}
            placeholder='牧场,认证,包装'
          />
          <label className='text-xs text-neutral-500'>场景标签（逗号分隔）</label>
          <Input
            value={form.sceneTags}
            onChange={(e) => setForm((f) => ({ ...f, sceneTags: e.target.value }))}
            placeholder='lead,evidence,close'
          />
          <label className='text-xs text-neutral-500'>关联话题（可选，软优先）</label>
          <Select
            mode='multiple'
            className='w-full'
            options={topicOptions}
            value={form.topicIds}
            showSearch
            optionFilterProp='label'
            placeholder='不绑也可被语义召回'
            onChange={(v) => setForm((f) => ({ ...f, topicIds: v }))}
            allowClear
          />
          <div className='grid grid-cols-2 gap-3'>
            <div>
              <label className='text-xs text-neutral-500'>来源</label>
              <Select
                className='w-full'
                value={form.sourceType}
                options={[
                  { label: '自有图', value: 'own' },
                  { label: '授权库存图', value: 'stock_licensed' },
                ]}
                onChange={(v) => setForm((f) => ({ ...f, sourceType: v }))}
              />
            </div>
            <div>
              <label className='text-xs text-neutral-500'>图中是否已有品牌</label>
              <Select
                className='w-full'
                value={form.brandVisible}
                options={[
                  { label: '无', value: 0 },
                  { label: '有（该节少写文字品牌）', value: 1 },
                ]}
                onChange={(v) => setForm((f) => ({ ...f, brandVisible: v }))}
              />
            </div>
          </div>
          {form.sourceType === 'stock_licensed' ? (
            <>
              <label className='text-xs text-neutral-500'>授权说明</label>
              <Input
                value={form.licenseNote}
                onChange={(e) => setForm((f) => ({ ...f, licenseNote: e.target.value }))}
                placeholder='如：某图库商用授权编号…'
              />
            </>
          ) : null}
          <label className='text-xs text-neutral-500'>图注</label>
          <Input
            value={form.caption}
            onChange={(e) => setForm((f) => ({ ...f, caption: e.target.value }))}
          />
          <label className='text-xs text-neutral-500'>状态</label>
          <Select
            className='w-full'
            value={form.status}
            options={[
              { label: '生效', value: 'active' },
              { label: '草稿', value: 'draft' },
              { label: '停用', value: 'disabled' },
            ]}
            onChange={(v) => setForm((f) => ({ ...f, status: v }))}
          />
        </div>
      </Modal>

      <Modal
        title='批量上传'
        open={batchUploadOpen}
        onCancel={() => !uploading && setBatchUploadOpen(false)}
        onOk={() => void submitBatchUpload()}
        confirmLoading={uploading}
        okText={uploading ? '上传中…' : '开始入库'}
        width={560}
        destroyOnHidden
      >
        <div className='flex flex-col gap-3'>
          <Upload
            multiple
            accept='image/png,image/jpeg,image/jpg,image/gif,image/webp'
            beforeUpload={() => false}
            fileList={batchFiles.map((f, i) => ({ uid: String(i), name: f.name, status: 'done' as const }))}
            onChange={(info) => {
              const files = info.fileList.map((f) => f.originFileObj).filter((f): f is File => !!f);
              setBatchFiles(files.slice(0, 40));
            }}
          >
            <Button icon={<CloudUploadOutlined />}>选择多张图片（最多 40）</Button>
          </Upload>
          <p className='text-xs text-neutral-500'>已选 {batchFiles.length} 张；标题默认用文件名。</p>
          <label className='text-xs text-neutral-500'>统一用途（推荐）</label>
          <Input.TextArea
            value={batchUploadMeta.usageHint}
            onChange={(e) => setBatchUploadMeta((m) => ({ ...m, usageHint: e.target.value }))}
            placeholder='如：牧场实景，适合对比/证据小节'
            autoSize={{ minRows: 2, maxRows: 3 }}
          />
          <label className='text-xs text-neutral-500'>统一标签</label>
          <Input
            value={batchUploadMeta.tags}
            onChange={(e) => setBatchUploadMeta((m) => ({ ...m, tags: e.target.value }))}
            placeholder='牧场,实景'
          />
          <label className='text-xs text-neutral-500'>统一场景标签</label>
          <Input
            value={batchUploadMeta.sceneTags}
            onChange={(e) => setBatchUploadMeta((m) => ({ ...m, sceneTags: e.target.value }))}
            placeholder='evidence'
          />
          <label className='text-xs text-neutral-500'>来源</label>
          <Select
            className='w-full'
            value={batchUploadMeta.sourceType}
            options={[
              { label: '自有图', value: 'own' },
              { label: '授权库存图', value: 'stock_licensed' },
            ]}
            onChange={(v) => setBatchUploadMeta((m) => ({ ...m, sourceType: v }))}
          />
          {batchUploadMeta.sourceType === 'stock_licensed' ? (
            <Input
              value={batchUploadMeta.licenseNote}
              onChange={(e) => setBatchUploadMeta((m) => ({ ...m, licenseNote: e.target.value }))}
              placeholder='授权说明'
            />
          ) : null}
        </div>
      </Modal>

      <Modal
        title={`批量改用途（已选 ${selectedRowKeys.length} 条）`}
        open={batchMetaOpen}
        onCancel={() => setBatchMetaOpen(false)}
        onOk={() => void submitBatchMeta()}
        width={560}
        destroyOnHidden
      >
        <div className='mb-2 text-xs text-neutral-500'>只填写要覆盖的字段；留空表示不改该项。</div>
        <div className='flex flex-col gap-3'>
          <label className='text-xs text-neutral-500'>可用于哪里</label>
          <Input.TextArea
            value={batchMeta.usageHint}
            onChange={(e) => setBatchMeta((m) => ({ ...m, usageHint: e.target.value }))}
            placeholder='覆盖写入用途说明'
            autoSize={{ minRows: 2, maxRows: 4 }}
          />
          <label className='text-xs text-neutral-500'>标签</label>
          <Input
            value={batchMeta.tags}
            onChange={(e) => setBatchMeta((m) => ({ ...m, tags: e.target.value }))}
            placeholder='覆盖标签，逗号分隔'
          />
          <label className='text-xs text-neutral-500'>场景标签</label>
          <Input
            value={batchMeta.sceneTags}
            onChange={(e) => setBatchMeta((m) => ({ ...m, sceneTags: e.target.value }))}
            placeholder='覆盖场景标签'
          />
          <label className='text-xs text-neutral-500'>关联话题（可选）</label>
          <Select
            mode='multiple'
            className='w-full'
            options={topicOptions}
            value={batchMeta.topicIds}
            disabled={batchMeta.clearTopics}
            showSearch
            optionFilterProp='label'
            placeholder='覆盖话题关联'
            onChange={(v) => setBatchMeta((m) => ({ ...m, topicIds: v, clearTopics: false }))}
            allowClear
          />
          <label className='flex items-center gap-2 text-xs text-neutral-600'>
            <input
              type='checkbox'
              checked={batchMeta.clearTopics}
              onChange={(e) =>
                setBatchMeta((m) => ({
                  ...m,
                  clearTopics: e.target.checked,
                  topicIds: e.target.checked ? [] : m.topicIds,
                }))
              }
            />
            清空话题关联
          </label>
          <div className='grid grid-cols-2 gap-3'>
            <div>
              <label className='text-xs text-neutral-500'>图中品牌</label>
              <Select
                className='w-full'
                allowClear
                placeholder='不改'
                value={batchMeta.brandVisible}
                options={[
                  { label: '无', value: 0 },
                  { label: '有', value: 1 },
                ]}
                onChange={(v) => setBatchMeta((m) => ({ ...m, brandVisible: v }))}
              />
            </div>
            <div>
              <label className='text-xs text-neutral-500'>状态</label>
              <Select
                className='w-full'
                allowClear
                placeholder='不改'
                value={batchMeta.status}
                options={[
                  { label: '生效', value: 'active' },
                  { label: '草稿', value: 'draft' },
                  { label: '停用', value: 'disabled' },
                ]}
                onChange={(v) => setBatchMeta((m) => ({ ...m, status: v }))}
              />
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
});

export default MediaPage;
