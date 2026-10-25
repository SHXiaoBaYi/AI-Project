import { memo, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Card, Drawer, Form, Input, Modal, QRCode, Tag, Typography, Upload } from 'antd';
import { CopyOutlined, UploadOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  createTrainDocWithFileApi,
  deleteTrainDocApi,
  downloadTrainVersionApi,
  getTrainAssistantEntryApi,
  addTrainFileApi,
  listTrainDocsApi,
  listTrainFilesApi,
  listTrainVersionsApi,
  pickUploadFile,
  rebuildTrainVersionQaApi,
  saveTrainDocApi,
  uploadTrainVersionApi,
  type TrainAssistantEntry,
  type TrainDoc,
  type TrainDocFile,
  type TrainDocVersion,
} from '@/api/train';

const TRAIN_ACCEPT = '.doc,.docx,.pdf,.txt,.md';

const TrainDocPage = memo(function TrainDocPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>(null);
  const [entry, setEntry] = useState<TrainAssistantEntry | null>(null);
  const [docOpen, setDocOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<TrainDoc | null>(null);
  const [form] = Form.useForm();

  const [versionOpen, setVersionOpen] = useState(false);
  const [versionDoc, setVersionDoc] = useState<TrainDoc | null>(null);
  const [versions, setVersions] = useState<TrainDocVersion[]>([]);
  const [files, setFiles] = useState<TrainDocFile[]>([]);
  const [fileVersionId, setFileVersionId] = useState<number>();
  const [uploading, setUploading] = useState(false);
  const [addingFile, setAddingFile] = useState(false);
  const [structuring, setStructuring] = useState(false);
  const [newVersionOpen, setNewVersionOpen] = useState(false);
  const [uploadForm] = Form.useForm();
  const [fileForm] = Form.useForm();

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

  const downloadLatest = (record: TrainDoc) => {
    if (record.latestFileUrl) {
      window.open(record.latestFileUrl, '_blank', 'noopener,noreferrer');
      return;
    }
    if (!record.latestVersionId) {
      message.warning('暂无已上传版本');
      return;
    }
    void downloadTrainVersionApi(record.latestVersionId, record.latestFileName, record.latestFilePath).catch(() =>
      message.error('下载失败'),
    );
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
    fileForm.resetFields();
    setVersionOpen(true);
    const rows = await listTrainVersionsApi(record.id);
    setVersions(rows || []);
    const latest = (rows || []).find((v) => v.latest) || rows?.[0];
    setFileVersionId(latest?.id);
    if (latest?.id) {
      const docs = await listTrainFilesApi(latest.id);
      setFiles(docs || []);
    } else {
      setFiles([]);
    }
  };

  const reloadVersions = async () => {
    if (!versionDoc) return;
    const rows = await listTrainVersionsApi(versionDoc.id);
    setVersions(rows || []);
    const current = (rows || []).find((v) => v.id === fileVersionId) || (rows || []).find((v) => v.latest) || rows?.[0];
    setFileVersionId(current?.id);
    if (current?.id) {
      const docs = await listTrainFilesApi(current.id);
      setFiles(docs || []);
    } else {
      setFiles([]);
    }
    actionRef.current?.reload();
  };

  const loadFiles = async (versionId?: number) => {
    if (!versionId) {
      setFiles([]);
      return;
    }
    try {
      const docs = await listTrainFilesApi(versionId);
      setFiles(docs || []);
    } catch {
      setFiles([]);
    }
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
      message.success('新版本已建好，第一份文档的题目会拆出来');
      uploadForm.resetFields();
      setNewVersionOpen(false);
      await reloadVersions();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const handleAddFile = async () => {
    const values = await fileForm.validateFields();
    const file = pickUploadFile(values.file);
    if (!file || !versionDoc || !fileVersionId) {
      message.warning('请选择版本和本地文件');
      return;
    }
    setAddingFile(true);
    try {
      await addTrainFileApi(versionDoc.id, fileVersionId, file, values.title ? String(values.title).trim() : undefined);
      message.success('文档已挂到这个版本，题目会从这份文档拆出');
      fileForm.resetFields();
      await loadFiles(fileVersionId);
      actionRef.current?.reload();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '添加失败');
    } finally {
      setAddingFile(false);
    }
  };

  const handleStructure = async () => {
    if (!versionDoc || !fileVersionId) return;
    setStructuring(true);
    try {
      await rebuildTrainVersionQaApi(versionDoc.id, fileVersionId);
      message.success('这一版里每份文档的题目都已拆出');
      await loadFiles(fileVersionId);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '拆题失败');
    } finally {
      setStructuring(false);
    }
  };

  const currentVersion = versions.find((v) => v.id === fileVersionId);
  const versionName = currentVersion?.versionLabel || (currentVersion ? `v${currentVersion.versionNo}` : '');
  const documents: TrainDocFile[] = files.length
    ? files
    : currentVersion?.fileName
      ? [
          {
            id: -currentVersion.id,
            docId: versionDoc?.id,
            versionId: currentVersion.id,
            title: currentVersion.fileName,
            fileName: currentVersion.fileName,
          },
        ]
      : [];

  const openQuestions = (row: TrainDocFile) => {
    const docId = row.docId || versionDoc?.id;
    if (!docId) return;
    const params = new URLSearchParams({ docId: String(docId) });
    if (row.versionId) params.set('versionId', String(row.versionId));
    if (row.id > 0) params.set('fileId', String(row.id));
    navigate(`/train/item?${params.toString()}`);
  };

  const columns: ProColumnType<TrainDoc>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 280,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'qa',
              label: '题目',
              perm: 'train:doc:list',
              disabled: !record.latestVersionId,
              onClick: () => navigate(`/train/item?docId=${record.id}`),
            },
            {
              key: 'version',
              label: '文档',
              perm: 'train:doc:list',
              onClick: () => void openVersions(record),
            },
            {
              key: 'download',
              label: '下载最新',
              perm: 'train:doc:list',
              disabled: !record.latestVersionId,
              onClick: () => downloadLatest(record),
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
      width: 80,
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
    <div className='flex h-full flex-col gap-4 p-4'>
      <Card
        size='small'
        title='钉钉答疑入口（长期有效）'
        className='shrink-0'
      >
        {entry?.ready && entry.pageUrl ? (
          <div className='flex flex-wrap items-start gap-6'>
            <QRCode
              value={entry.dingTalkUrl || entry.pageUrl}
              size={128}
              status='active'
            />
            <div className='min-w-0 flex-1 space-y-2'>
              <Typography.Paragraph
                type='secondary'
                className='!mb-0'
              >
                仅本企业钉钉员工扫码可进；浏览器打开无效。页面与二维码长期有效。
              </Typography.Paragraph>
              <div className='flex flex-wrap items-center gap-2'>
                <Typography.Text
                  code
                  className='break-all'
                >
                  {entry.pageUrl}
                </Typography.Text>
                <PermissionButton
                  type='link'
                  size='small'
                  icon={<CopyOutlined />}
                  perm='train:doc:list'
                  onClick={() => void copyText(entry.pageUrl)}
                >
                  复制链接
                </PermissionButton>
              </div>
              {entry.dingTalkUrl ? (
                <div className='flex flex-wrap items-center gap-2'>
                  <Typography.Text type='secondary'>钉钉 schema：</Typography.Text>
                  <Typography.Text
                    code
                    className='text-xs break-all'
                  >
                    {entry.dingTalkUrl}
                  </Typography.Text>
                  <PermissionButton
                    type='link'
                    size='small'
                    icon={<CopyOutlined />}
                    perm='train:doc:list'
                    onClick={() => void copyText(entry.dingTalkUrl)}
                  >
                    复制
                  </PermissionButton>
                </div>
              ) : null}
            </div>
          </div>
        ) : (
          <Typography.Text type='secondary'>{entry?.message || '加载中…'}</Typography.Text>
        )}
      </Card>

      <BaseProTable<TrainDoc>
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        headerTitle='收银操作培训文档'
        toolBarRender={() => [
          <PermissionButton
            key='upload'
            type='primary'
            icon={<UploadOutlined />}
            perm='train:doc:upload'
            onClick={openLocalUpload}
          >
            本地上传文档
          </PermissionButton>,
        ]}
        request={async () => {
          const rows = await listTrainDocsApi('cashier');
          return { data: rows || [], success: true, total: rows?.length || 0 };
        }}
      />

      <Modal
        title={editing ? '编辑培训文档' : '本地上传培训文档'}
        open={docOpen}
        onCancel={() => setDocOpen(false)}
        onOk={() => void handleSaveDoc()}
        confirmLoading={saving}
        destroyOnHidden
        width={520}
      >
        <Form
          form={form}
          layout='vertical'
          className='pt-2'
        >
          <Form.Item
            name='title'
            label='标题'
            rules={[{ required: true, message: '请输入标题' }]}
          >
            <Input
              maxLength={120}
              placeholder='如：收银操作培训手册'
            />
          </Form.Item>
          <Form.Item
            name='description'
            label='说明'
          >
            <Input.TextArea
              rows={2}
              maxLength={500}
              placeholder='可选'
            />
          </Form.Item>
          {!editing ? (
            <>
              <Form.Item
                name='versionLabel'
                label='首版版本号'
                initialValue='v1'
              >
                <Input
                  maxLength={32}
                  placeholder='v1'
                />
              </Form.Item>
              <Form.Item
                name='file'
                label='培训文档文件'
                valuePropName='fileList'
                getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
                rules={[{ required: true, message: '请选择本地 .doc / .docx 等文件' }]}
              >
                <Upload
                  beforeUpload={() => false}
                  maxCount={1}
                  accept={TRAIN_ACCEPT}
                >
                  <PermissionButton
                    icon={<UploadOutlined />}
                    perm='train:doc:upload'
                  >
                    选择文件
                  </PermissionButton>
                  <p className='ant-upload-hint'>
                    支持 .doc / .docx / pdf / txt，按「问：」「答：」书写，单份不超过 10MB
                  </p>
                </Upload>
              </Form.Item>
            </>
          ) : null}
        </Form>
      </Modal>

      <Drawer
        title={versionDoc ? `${versionDoc.title}` : '收银文档'}
        open={versionOpen}
        onClose={() => setVersionOpen(false)}
        width={760}
      >
        <div className='mb-3 text-sm text-neutral-500'>一个版本下面挂多份收银文档。每份文档单独拆成题目。</div>
        <div className='mb-4 flex flex-wrap gap-2'>
          {versions.map((v) => {
            const name = v.versionLabel || `v${v.versionNo}`;
            const active = v.id === fileVersionId;
            return (
              <button
                key={v.id}
                type='button'
                className={`rounded-md border px-3 py-1 text-sm ${active ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-neutral-200 bg-white text-neutral-700'}`}
                onClick={() => {
                  setFileVersionId(v.id);
                  void loadFiles(v.id);
                }}
              >
                {name}
                {v.latest ? ' · 最新' : ''}
              </button>
            );
          })}
          <PermissionButton
            perm='train:doc:upload'
            onClick={() => {
              uploadForm.resetFields();
              setNewVersionOpen(true);
            }}
          >
            新建版本
          </PermissionButton>
        </div>

        <div className='mb-2 flex items-center justify-between'>
          <div className='text-base font-medium'>{versionName || '当前版本'} 的文档</div>
          <PermissionButton
            perm='train:doc:upload'
            loading={structuring}
            disabled={!fileVersionId}
            onClick={() => void handleStructure()}
          >
            拆出全部题目
          </PermissionButton>
        </div>

        <BaseProTable<TrainDocFile>
          rowKey='id'
          search={false}
          pagination={false}
          toolBarRender={false}
          dataSource={documents}
          columns={[
            {
              title: '操作',
              valueType: 'option',
              width: 100,
              render: (_, row) => (
                <PermissionButton
                  type='link'
                  size='small'
                  perm='train:doc:list'
                  onClick={() => openQuestions(row)}
                >
                  查看题目
                </PermissionButton>
              ),
            },
            {
              title: '收银文档',
              dataIndex: 'title',
              ellipsis: true,
              render: (_, row) => row.title || row.fileName || '—',
            },
            { title: '文件', dataIndex: 'fileName', ellipsis: true },
            {
              title: '题目',
              dataIndex: 'questionCount',
              width: 90,
              render: (_, row) =>
                row.questionCount == null ? '—' : row.questionCount === 0 ? '未拆题' : row.questionCount,
            },
          ]}
        />

        <Form
          form={fileForm}
          layout='vertical'
          className='mt-4'
        >
          <div className='mb-2 text-base font-medium'>往 {versionName || '当前版本'} 再加一份文档</div>
          <Form.Item
            name='title'
            label='文档名称'
          >
            <Input
              maxLength={128}
              placeholder='不填就用文件名'
            />
          </Form.Item>
          <Form.Item
            name='file'
            label='文件'
            valuePropName='fileList'
            getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
            rules={[{ required: true, message: '请选择文件' }]}
          >
            <Upload
              beforeUpload={() => false}
              maxCount={1}
              accept={TRAIN_ACCEPT}
            >
              <PermissionButton
                icon={<UploadOutlined />}
                perm='train:doc:upload'
              >
                选择文件
              </PermissionButton>
              <p className='ant-upload-hint'>按「问：」「答：」书写，单份不超过 10MB。加上后会拆出这份额文档的题目。</p>
            </Upload>
          </Form.Item>
          <PermissionButton
            type='primary'
            loading={addingFile}
            perm='train:doc:upload'
            disabled={!fileVersionId}
            onClick={() => void handleAddFile()}
          >
            加到 {versionName || '当前版本'}
          </PermissionButton>
        </Form>
      </Drawer>

      <Modal
        title={
          versionName
            ? `新建版本（当前最新是 ${versions.find((v) => v.latest)?.versionLabel || versionName}）`
            : '新建版本'
        }
        open={newVersionOpen}
        onCancel={() => setNewVersionOpen(false)}
        footer={null}
        destroyOnHidden
      >
        <Form
          form={uploadForm}
          layout='vertical'
        >
          <Form.Item
            name='versionLabel'
            label='版本号'
          >
            <Input
              maxLength={32}
              placeholder='如 v4，不填就自动递增'
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input
              maxLength={200}
              placeholder='可选'
            />
          </Form.Item>
          <Form.Item
            name='file'
            label='这一版的第一份文档'
            valuePropName='fileList'
            getValueFromEvent={(e) => (Array.isArray(e) ? e : e?.fileList)}
            rules={[{ required: true, message: '请选择文件' }]}
          >
            <Upload
              beforeUpload={() => false}
              maxCount={1}
              accept={TRAIN_ACCEPT}
            >
              <PermissionButton
                icon={<UploadOutlined />}
                perm='train:doc:upload'
              >
                选择文件
              </PermissionButton>
            </Upload>
          </Form.Item>
          <PermissionButton
            type='primary'
            loading={uploading}
            perm='train:doc:upload'
            onClick={() => void handleUpload()}
          >
            创建并设为最新
          </PermissionButton>
        </Form>
      </Modal>
    </div>
  );
});

export default TrainDocPage;
