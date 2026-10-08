import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Card, Drawer, Form, Input, Modal, QRCode, Space, Tag, Typography, Upload } from 'antd';
import { CopyOutlined, UploadOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  createTrainDocWithFileApi,
  deleteTrainDocApi,
  downloadTrainVersionApi,
  getTrainAssistantEntryApi,
  listTrainDocsApi,
  listTrainVersionsApi,
  pickUploadFile,
  saveTrainDocApi,
  uploadTrainVersionApi,
  type TrainAssistantEntry,
  type TrainDoc,
  type TrainDocVersion,
} from '@/api/train';

const TRAIN_ACCEPT = '.doc,.docx,.pdf,.txt,.md';

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
                  <p className='ant-upload-hint'>支持 .doc / .docx / pdf / txt，上传后作为首个版本</p>
                </Upload>
              </Form.Item>
            </>
          ) : null}
        </Form>
      </Modal>

      <Drawer
        title={versionDoc ? `版本管理 · ${versionDoc.title}` : '版本管理'}
        open={versionOpen}
        onClose={() => setVersionOpen(false)}
        width={720}
      >
        <Form
          form={uploadForm}
          layout='vertical'
          className='mb-4'
        >
          <Form.Item
            name='versionLabel'
            label='新版本号'
          >
            <Input
              maxLength={32}
              placeholder='如 v2（可空，自动递增）'
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
            label='本地文件'
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
              <p className='ant-upload-hint'>支持 .doc / .docx / pdf / txt</p>
            </Upload>
          </Form.Item>
          <PermissionButton
            type='primary'
            loading={uploading}
            perm='train:doc:upload'
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
              width: 90,
              render: (_, row) => (
                <PermissionButton
                  type='link'
                  size='small'
                  perm='train:doc:list'
                  onClick={() => {
                    if (row.fileUrl) {
                      window.open(row.fileUrl, '_blank', 'noopener,noreferrer');
                      return;
                    }
                    void downloadTrainVersionApi(row.id, row.fileName, row.filePath).catch(() =>
                      message.error('下载失败'),
                    );
                  }}
                >
                  下载
                </PermissionButton>
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
    </div>
  );
});

export default TrainDocPage;
