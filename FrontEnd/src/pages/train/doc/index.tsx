import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Card, Drawer, Form, Input, Modal, QRCode, Space, Spin, Tag, Typography, Upload } from 'antd';
import { CopyOutlined, UploadOutlined } from '@ant-design/icons';
import { renderAsync } from 'docx-preview';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  createTrainDocWithFileApi,
  deleteTrainDocApi,
  downloadTrainVersionApi,
  fetchTrainVersionFileApi,
  getTrainAssistantEntryApi,
  listTrainDocsApi,
  listTrainVersionsApi,
  pickUploadFile,
  previewTrainVersionApi,
  saveTrainDocApi,
  uploadTrainVersionApi,
  type TrainAssistantEntry,
  type TrainDoc,
  type TrainDocPreview,
  type TrainDocVersion,
} from '@/api/train';

const TRAIN_ACCEPT = '.doc,.docx,.pdf,.txt,.md';

async function blobLooksLikeDocx(blob: Blob) {
  const head = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
  // PK\x03\x04 — zip/docx
  return head.length >= 2 && head[0] === 0x50 && head[1] === 0x4b;
}

async function blobLooksLikePdf(blob: Blob) {
  const head = new Uint8Array(await blob.slice(0, 5).arrayBuffer());
  return head.length >= 4 && head[0] === 0x25 && head[1] === 0x50 && head[2] === 0x44 && head[3] === 0x46;
}

const TrainDocPage = memo(function TrainDocPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [entry, setEntry] = useState<TrainAssistantEntry | null>(null);
  const [docOpen, setDocOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<TrainDoc | null>(null);
  const [form] = Form.useForm();

  const [versionOpen, setVersionOpen] = useState(false);
  const [versionDoc, setVersionDoc] = useState<TrainDoc | null>(null);
  const [versions, setVersions] = useState<TrainDocVersion[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadForm] = Form.useForm();

  const [previewOpen, setPreviewOpen] = useState(false);
  const [preview, setPreview] = useState<TrainDocPreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewMode, setPreviewMode] = useState<'docx' | 'pdf' | 'html' | ''>('');
  const [previewError, setPreviewError] = useState('');
  const [pdfUrl, setPdfUrl] = useState<string>();
  const [docxBlob, setDocxBlob] = useState<Blob | null>(null);
  const docxRef = useRef<HTMLDivElement>(null);
  const pdfUrlRef = useRef<string>();

  useEffect(() => {
    void getTrainAssistantEntryApi()
      .then(setEntry)
      .catch(() => setEntry({ ready: false, message: '入口信息加载失败' }));
  }, []);

  const copyText = async (text?: string) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      message.success('已复制');
    } catch {
      message.error('复制失败，请手动选择链接');
    }
  };

  const openDocEdit = (record?: TrainDoc) => {
    setEditing(record ?? null);
    form.resetFields();
    form.setFieldsValue({
      title: record?.title || '收银操作培训手册',
      description: record?.description || '',
      file: undefined,
      versionLabel: record ? undefined : 'v1',
    });
    setDocOpen(true);
  };

  const openLocalUpload = () => openDocEdit();

  const handleSaveDoc = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing?.id) {
        await saveTrainDocApi({
          id: editing.id,
          title: String(values.title).trim(),
          category: 'cashier',
          description: values.description ? String(values.description).trim() : undefined,
        });
        message.success('已保存');
      } else {
        const file = pickUploadFile(values.file);
        if (!file) {
          message.warning('请选择本地培训文档文件');
          return;
        }
        await createTrainDocWithFileApi({
          file,
          title: String(values.title).trim(),
          description: values.description ? String(values.description).trim() : undefined,
          versionLabel: values.versionLabel ? String(values.versionLabel).trim() : 'v1',
        });
        message.success('已上传并创建文档');
      }
      setDocOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const openVersions = async (record: TrainDoc) => {
    setVersionDoc(record);
    uploadForm.resetFields();
    setVersionOpen(true);
    const rows = await listTrainVersionsApi(record.id);
    setVersions(rows || []);
  };

  const reloadVersions = async () => {
    if (!versionDoc) return;
    const rows = await listTrainVersionsApi(versionDoc.id);
    setVersions(rows || []);
    actionRef.current?.reload();
  };

  const handleUpload = async () => {
    const values = await uploadForm.validateFields();
    const file = pickUploadFile(values.file);
    if (!file || !versionDoc) {
      message.warning('请选择本地文件');
      return;
    }
    setUploading(true);
    try {
      await uploadTrainVersionApi(versionDoc.id, file, values.versionLabel, values.remark);
      message.success('版本已上传，并设为最新版');
      uploadForm.resetFields();
      await reloadVersions();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const resetPreviewMedia = () => {
    if (pdfUrlRef.current) {
      URL.revokeObjectURL(pdfUrlRef.current);
      pdfUrlRef.current = undefined;
    }
    setPdfUrl(undefined);
    setDocxBlob(null);
    setPreviewMode('');
    setPreviewError('');
  };

  const openPreview = async (versionId: number) => {
    resetPreviewMedia();
    setPreview(null);
    setPreviewOpen(true);
    setPreviewLoading(true);
    try {
      // 元信息轻量；docx/pdf 再拉原文件，避免后端把整份图文转 HTML 超时
      const data = await previewTrainVersionApi(versionId);
      setPreview(data);
      const name = (data.fileName || '').toLowerCase();
      const mode = (data.renderMode || '').toLowerCase();

      if (data.fileMissing || data.fileReady === false) {
        setPreviewError('原文件在服务器上不存在。若只在本地联调上传过，请在线上重新上传该版本。');
        setPreviewMode(data.html ? 'html' : '');
        return;
      }

      const needFile =
        mode === 'docx' || mode === 'pdf' || name.endsWith('.docx') || name.endsWith('.pdf') || name.endsWith('.doc');
      if (!needFile) {
        setPreviewMode('html');
        return;
      }

      const { blob, contentType } = await fetchTrainVersionFileApi(versionId);
      const ct = (contentType || '').toLowerCase();

      if (mode === 'pdf' || name.endsWith('.pdf') || ct.includes('pdf') || (await blobLooksLikePdf(blob))) {
        const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
        pdfUrlRef.current = url;
        setPdfUrl(url);
        setPreviewMode('pdf');
        return;
      }

      if (
        mode === 'docx' ||
        name.endsWith('.docx') ||
        ct.includes('wordprocessingml') ||
        ct.includes('officedocument.word') ||
        (await blobLooksLikeDocx(blob))
      ) {
        setDocxBlob(blob);
        setPreviewMode('docx');
        return;
      }

      setPreviewMode('html');
    } catch (e) {
      const tip = e instanceof Error ? e.message : '预览失败';
      setPreviewError(
        /timeout|超时|exceeded/i.test(tip)
          ? '预览超时。大文档请确认线上已重新上传原文件；docx 将按原文件渲染，无需服务端转图。'
          : tip,
      );
      setPreviewMode('html');
    } finally {
      setPreviewLoading(false);
    }
  };

  useEffect(() => {
    if (!previewOpen || previewMode !== 'docx' || !docxBlob || !docxRef.current) return;
    let cancelled = false;
    const container = docxRef.current;
    container.innerHTML = '';
    setPreviewLoading(true);
    renderAsync(docxBlob, container, undefined, {
      className: 'train-docx-preview',
      inWrapper: true,
      ignoreWidth: false,
      breakPages: true,
    })
      .then(() => {
        if (!cancelled) setPreviewLoading(false);
      })
      .catch(() => {
        if (!cancelled) {
          setPreviewLoading(false);
          setPreviewError('原文件预览失败，已切换为文本预览');
          setPreviewMode('html');
        }
      });
    return () => {
      cancelled = true;
      container.innerHTML = '';
    };
  }, [docxBlob, previewMode, previewOpen]);

  const columns: ProColumnType<TrainDoc>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'upload',
              label: '上传版本',
              perm: 'train:doc:upload',
              onClick: () => void openVersions(record),
            },
            {
              key: 'version',
              label: '版本',
              perm: 'train:doc:list',
              onClick: () => void openVersions(record),
            },
            {
              key: 'preview',
              label: '预览最新',
              perm: 'train:doc:list',
              disabled: !record.latestVersionId,
              onClick: () => record.latestVersionId && void openPreview(record.latestVersionId),
            },
            {
              key: 'edit',
              label: '编辑',
              perm: 'train:doc:edit',
              onClick: () => openDocEdit(record),
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'train:doc:delete',
              confirmTitle: `确认删除「${record.title}」及其全部版本？`,
              onClick: async () => {
                await deleteTrainDocApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    {
      title: '最新版本',
      dataIndex: 'latestVersionLabel',
      width: 120,
      search: false,
      render: (_, r) =>
        r.latestVersionLabel ? (
          <Tag color='blue'>{r.latestVersionLabel}</Tag>
        ) : (
          <span className='text-neutral-400'>未上传</span>
        ),
    },
    {
      title: '文件名',
      dataIndex: 'latestFileName',
      ellipsis: true,
      search: false,
      render: (_, r) => r.latestFileName || '—',
    },
    {
      title: '版本数',
      dataIndex: 'versionCount',
      width: 90,
      search: false,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      search: false,
    },
  ];

  return (
    <div className='flex flex-col gap-4'>
      <Card
        size='small'
        title='钉钉答疑入口（长期有效）'
      >
        {entry?.ready && entry.pageUrl ? (
          <div className='flex flex-col gap-4 sm:flex-row sm:items-start'>
            <div className='flex flex-col items-center gap-2 rounded-lg border border-neutral-200 bg-white p-3'>
              <QRCode
                value={entry.pageUrl}
                size={168}
                errorLevel='M'
              />
              <Typography.Text type='secondary'>请用钉钉扫一扫</Typography.Text>
            </div>
            <div className='min-w-0 flex-1 space-y-2 text-sm'>
              <Typography.Paragraph
                type='secondary'
                className='!mb-2'
              >
                {entry.message ||
                  '二维码长期有效，无需反复生成。员工须用钉钉扫码，系统会校验是否为本企业员工后才能使用答疑。'}
              </Typography.Paragraph>
              <div>
                <div className='mb-1 text-neutral-500'>H5 地址</div>
                <Space wrap>
                  <Typography.Text
                    code
                    copyable={false}
                    className='break-all'
                  >
                    {entry.pageUrl}
                  </Typography.Text>
                  <PermissionButton
                    size='small'
                    icon={<CopyOutlined />}
                    perm='train:doc:list'
                    onClick={() => void copyText(entry.pageUrl)}
                  >
                    复制
                  </PermissionButton>
                </Space>
              </div>
              {entry.dingTalkUrl ? (
                <div>
                  <div className='mb-1 text-neutral-500'>钉钉内打开链接（可发工作通知）</div>
                  <Space wrap>
                    <Typography.Text
                      code
                      className='break-all'
                    >
                      {entry.dingTalkUrl}
                    </Typography.Text>
                    <PermissionButton
                      size='small'
                      icon={<CopyOutlined />}
                      perm='train:doc:list'
                      onClick={() => void copyText(entry.dingTalkUrl)}
                    >
                      复制
                    </PermissionButton>
                  </Space>
                </div>
              ) : null}
              <Typography.Text type='secondary'>CorpId：{entry.corpId || '—'}</Typography.Text>
            </div>
          </div>
        ) : (
          <Typography.Text type='danger'>{entry?.message || '正在加载入口…'}</Typography.Text>
        )}
      </Card>

      <BaseProTable<TrainDoc>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='收银操作培训文档'
        columns={columns}
        search={false}
        request={async () => {
          const rows = await listTrainDocsApi('cashier');
          return { data: rows || [], success: true, total: rows?.length || 0 };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='upload'
            type='primary'
            icon={<UploadOutlined />}
            perm='train:doc:upload'
            onClick={() => openLocalUpload()}
          >
            本地上传
          </PermissionButton>,
        ]}
      />

      <Modal
        title={editing ? '编辑培训文档' : '本地上传培训文档'}
        open={docOpen}
        onCancel={() => setDocOpen(false)}
        onOk={() => void handleSaveDoc()}
        confirmLoading={saving}
        okText={editing ? '保存' : '上传'}
        destroyOnHidden
        width={560}
      >
        <Form
          form={form}
          layout='vertical'
          className='pt-2'
        >
          {!editing ? (
            <Form.Item
              name='file'
              label='选择本地文件'
              valuePropName='fileList'
              getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
              rules={[{ required: true, message: '请选择本地 .doc / .docx 等文件' }]}
            >
              <Upload.Dragger
                beforeUpload={() => false}
                maxCount={1}
                accept={TRAIN_ACCEPT}
                multiple={false}
              >
                <p className='ant-upload-drag-icon'>
                  <UploadOutlined />
                </p>
                <p className='ant-upload-text'>点击或拖拽文件到此处</p>
                <p className='ant-upload-hint'>支持 .doc / .docx / pdf / txt，上传后作为首个版本</p>
              </Upload.Dragger>
            </Form.Item>
          ) : null}
          <Form.Item
            name='title'
            label='标题'
            rules={[{ required: true, message: '请输入标题' }]}
          >
            <Input
              maxLength={128}
              placeholder='如：收银操作培训手册'
            />
          </Form.Item>
          {!editing ? (
            <Form.Item
              name='versionLabel'
              label='版本标签'
            >
              <Input
                placeholder='默认 v1'
                maxLength={64}
              />
            </Form.Item>
          ) : null}
          <Form.Item
            name='description'
            label='说明'
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder='可选'
            />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={versionDoc ? `版本管理 · ${versionDoc.title}` : '版本管理'}
        open={versionOpen}
        onClose={() => setVersionOpen(false)}
        width={640}
      >
        <Form
          form={uploadForm}
          layout='vertical'
          className='mb-4 rounded-lg border border-neutral-200 p-3'
        >
          <Form.Item
            name='file'
            label='从本地上传新版本'
            valuePropName='fileList'
            getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
            rules={[{ required: true, message: '请选择本地文件' }]}
          >
            <Upload.Dragger
              beforeUpload={() => false}
              maxCount={1}
              accept={TRAIN_ACCEPT}
              multiple={false}
            >
              <p className='ant-upload-drag-icon'>
                <UploadOutlined />
              </p>
              <p className='ant-upload-text'>点击或拖拽本地文件到此处</p>
              <p className='ant-upload-hint'>支持 .doc / .docx / pdf / txt</p>
            </Upload.Dragger>
          </Form.Item>
          <Form.Item
            name='versionLabel'
            label='版本标签'
          >
            <Input
              placeholder='如 v3 / 2026-04 版，可空自动生成'
              maxLength={64}
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input
              placeholder='可选'
              maxLength={200}
            />
          </Form.Item>
          <PermissionButton
            type='primary'
            perm='train:doc:upload'
            loading={uploading}
            onClick={() => void handleUpload()}
          >
            上传并设为最新
          </PermissionButton>
        </Form>

        <BaseProTable<TrainDocVersion>
          rowKey='id'
          search={false}
          pagination={false}
          toolBarRender={false}
          dataSource={versions}
          columns={[
            {
              title: '操作',
              valueType: 'option',
              width: 140,
              render: (_, row) => (
                <Space size={0}>
                  <PermissionButton
                    type='link'
                    size='small'
                    perm='train:doc:list'
                    onClick={() => void openPreview(row.id)}
                  >
                    预览
                  </PermissionButton>
                  <PermissionButton
                    type='link'
                    size='small'
                    perm='train:doc:list'
                    onClick={() =>
                      void downloadTrainVersionApi(row.id, row.fileName).catch(() => message.error('下载失败'))
                    }
                  >
                    下载
                  </PermissionButton>
                </Space>
              ),
            },
            {
              title: '版本',
              dataIndex: 'versionLabel',
              width: 100,
              render: (_, r) => (
                <Space size={4}>
                  <span>{r.versionLabel || `v${r.versionNo}`}</span>
                  {r.latest ? <Tag color='green'>最新</Tag> : null}
                </Space>
              ),
            },
            { title: '文件', dataIndex: 'fileName', ellipsis: true },
            { title: '上传时间', dataIndex: 'createTime', width: 170 },
          ]}
        />
      </Drawer>

      <Drawer
        title={preview ? `预览 · ${preview.title}（${preview.versionLabel || ''}）` : '预览'}
        open={previewOpen}
        onClose={() => {
          setPreviewOpen(false);
          resetPreviewMedia();
        }}
        width={860}
        styles={{
          body: { display: 'flex', flexDirection: 'column', overflow: 'hidden', paddingTop: 12 },
        }}
        extra={
          preview?.id ? (
            <Typography.Link
              onClick={() =>
                void downloadTrainVersionApi(preview.id, preview.fileName).catch(() => message.error('下载失败'))
              }
            >
              下载原文件
            </Typography.Link>
          ) : null
        }
      >
        {previewLoading ? (
          <div className='mb-2 flex shrink-0 items-center gap-2 text-sm text-neutral-400'>
            <Spin size='small' />
            正在加载文档预览…
          </div>
        ) : null}
        {previewError ? <div className='mb-2 shrink-0 text-sm text-red-500'>{previewError}</div> : null}
        {previewMode === 'pdf' && pdfUrl ? (
          <iframe
            title='培训文档预览'
            src={pdfUrl}
            className='min-h-0 w-full flex-1 border-0'
          />
        ) : null}
        {previewMode === 'docx' ? (
          <div
            ref={docxRef}
            className='train-docx-host min-h-0 w-full flex-1 overflow-auto bg-[#f5f5f5] p-3 [&_.train-docx-preview]:mx-auto [&_.train-docx-preview]:bg-white [&_.train-docx-preview]:shadow-sm'
          />
        ) : null}
        {previewMode === 'html' ? (
          preview?.html ? (
            <div
              className='prose min-h-0 max-w-none flex-1 overflow-auto rounded-md bg-white p-4 text-sm leading-7 text-neutral-800 [&_img]:my-3 [&_img]:block [&_img]:max-w-full'
              dangerouslySetInnerHTML={{ __html: preview.html }}
            />
          ) : !previewLoading ? (
            <div className='text-neutral-400'>暂无预览内容，请下载原文件查看</div>
          ) : null
        ) : null}
      </Drawer>
    </div>
  );
});

export default TrainDocPage;
