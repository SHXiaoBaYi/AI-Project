import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Select, Space, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  deleteTrainQaCalibrateApi,
  listTrainQaCalibrateApi,
  rerecognizeTrainQaCalibrateApi,
  saveTrainQaCalibrateApi,
  type TrainQaCalibrate,
} from '@/api/train';
import { resolveUploadUrl } from '@/utils/uploadUrl';
import { isLocalhostHost } from '@/utils/localhostAccess';

const STATUS_OPTIONS = [
  { label: '准（优先命中）', value: 'APPROVED' },
  { label: '不准', value: 'REJECTED' },
  { label: '待审', value: 'PENDING' },
  { label: '文档已换版', value: 'STALE' },
];

function statusTag(status?: string) {
  switch (status) {
    case 'APPROVED':
      return <Tag color='success'>准</Tag>;
    case 'REJECTED':
      return <Tag color='error'>不准</Tag>;
    case 'PENDING':
      return <Tag color='processing'>待审</Tag>;
    case 'STALE':
      return <Tag>已换版</Tag>;
    default:
      return <Tag>{status || '-'}</Tag>;
  }
}

function imgUrl(src?: string) {
  if (!src) return '';
  if (/^(data|blob):/i.test(src)) return src;
  const path = src.startsWith('/uploads/') ? src : src.includes('/uploads/') ? src.slice(src.indexOf('/uploads/')) : '';
  if (path && isLocalhostHost()) return path;
  return resolveUploadUrl(src);
}

const TrainQaCalibratePage = memo(function TrainQaCalibratePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<TrainQaCalibrate | null>(null);
  const [form] = Form.useForm();

  const openEdit = (record?: TrainQaCalibrate) => {
    setEditing(record ?? null);
    form.setFieldsValue({
      userQuestion: record?.userQuestion || '',
      aliases: record?.aliases || '',
      topicTitle: record?.topicTitle || '',
      answer: record?.answer || '',
      answerHtml: record?.answerHtml || '',
      status: record?.status || 'APPROVED',
      remark: record?.remark || '',
      docId: record?.docId,
      versionId: record?.versionId,
    });
    setOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await saveTrainQaCalibrateApi({
        id: editing?.id,
        docId: values.docId ? Number(values.docId) : editing?.docId,
        versionId: values.versionId ? Number(values.versionId) : editing?.versionId,
        userQuestion: String(values.userQuestion).trim(),
        aliases: values.aliases ? String(values.aliases) : undefined,
        topicTitle: values.topicTitle ? String(values.topicTitle).trim() : undefined,
        answer: values.answer ? String(values.answer) : undefined,
        answerHtml: values.answerHtml ? String(values.answerHtml) : undefined,
        images: editing?.images,
        status: values.status,
        remark: values.remark ? String(values.remark).trim() : undefined,
      });
      message.success('已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<TrainQaCalibrate>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 220,
      fixed: 'left',
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '校准',
              perm: 'train:qa:edit',
              onClick: () => openEdit(record),
            },
            {
              key: 'rerecognize',
              label: '重新识别',
              perm: 'train:qa:recognize',
              confirmTitle: '将按当前绑定版本文档重新识别，结果进入「待审」，是否继续？',
              onClick: async () => {
                await rerecognizeTrainQaCalibrateApi(record.id);
                message.success('已重新识别，请审核后标为「准」');
                actionRef.current?.reload();
              },
            },
            ...(record.status === 'APPROVED'
              ? []
              : [
                  {
                    key: 'approve',
                    label: '标为准',
                    perm: 'train:qa:edit',
                    onClick: async () => {
                      await saveTrainQaCalibrateApi({
                        id: record.id,
                        docId: record.docId,
                        versionId: record.versionId,
                        userQuestion: record.userQuestion || '',
                        aliases: record.aliases,
                        topicTitle: record.topicTitle,
                        answer: record.answer,
                        answerHtml: record.answerHtml,
                        images: record.images,
                        status: 'APPROVED',
                        remark: record.remark,
                      });
                      message.success('已标为准，下次同意图将优先命中');
                      actionRef.current?.reload();
                    },
                  },
                ]),
            {
              key: 'delete',
              label: '删除',
              perm: 'train:qa:edit',
              confirmTitle: `确认删除「${record.userQuestion}」？`,
              onClick: async () => {
                await deleteTrainQaCalibrateApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      fieldProps: { options: STATUS_OPTIONS, allowClear: true },
      render: (_, r) => statusTag(r.status),
    },
    {
      title: '用户问法',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '问法/话题/答案' },
    },
    {
      title: '仅最新版',
      dataIndex: 'onlyLatest',
      hideInTable: true,
      valueType: 'select',
      fieldProps: {
        allowClear: true,
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
      },
    },
    { title: '用户问法', dataIndex: 'userQuestion', ellipsis: true, search: false, width: 180 },
    { title: '话题标题', dataIndex: 'topicTitle', ellipsis: true, search: false, width: 180 },
    {
      title: '答案摘要',
      dataIndex: 'answer',
      ellipsis: true,
      search: false,
      width: 220,
      render: (_, r) => r.answer || (r.answerHtml ? '（图文 HTML）' : '-'),
    },
    {
      title: '版本',
      search: false,
      width: 120,
      render: (_, r) => (
        <Space size={4}>
          <span>{r.versionLabel || r.versionId || '-'}</span>
          {r.latestVersion ? <Tag color='blue'>最新</Tag> : <Tag>旧版</Tag>}
        </Space>
      ),
    },
    { title: '命中', dataIndex: 'hitCount', search: false, width: 70 },
    { title: '更新人', dataIndex: 'updateBy', search: false, width: 90 },
    { title: '更新时间', dataIndex: 'updateTime', search: false, width: 170 },
  ];

  return (
    <>
      <BaseProTable<TrainQaCalibrate>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='答疑校准'
        columns={columns}
        scroll={{ x: 1200 }}
        request={async (params) => {
          const res = await listTrainQaCalibrateApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            status: params.status ? String(params.status) : undefined,
            keyword: params.keyword ? String(params.keyword) : undefined,
            onlyLatest: params.onlyLatest === true || params.onlyLatest === 'true' ? true : undefined,
          });
          return { data: res.rows || [], total: res.total || 0, success: true };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='train:qa:edit'
            onClick={() => openEdit()}
          >
            手工新增
          </PermissionButton>,
        ]}
      />

      <Modal
        title={editing ? '校准答案' : '手工新增校准'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        width={720}
        destroyOnHidden
      >
        <Form
          form={form}
          layout='vertical'
          className='mt-2'
        >
          <Form.Item
            name='userQuestion'
            label='用户问法（下次优先命中键）'
            rules={[{ required: true, message: '请输入问法' }]}
          >
            <Input placeholder='如：如何寄存' />
          </Form.Item>
          <Form.Item
            name='aliases'
            label='同义问法（每行一条）'
          >
            <Input.TextArea
              rows={2}
              placeholder={'怎么寄存\n寄存怎么操作'}
            />
          </Form.Item>
          <Form.Item
            name='topicTitle'
            label='文档话题标题'
          >
            <Input placeholder='如：问：前台寄存产品怎么操作？' />
          </Form.Item>
          <Form.Item
            name='answer'
            label='答法纯文本'
          >
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item
            name='answerHtml'
            label='答法 HTML（图文混排，可选）'
          >
            <Input.TextArea
              rows={4}
              placeholder='含 <img src="/uploads/..."> 的 HTML'
            />
          </Form.Item>
          <Form.Item
            name='status'
            label='状态'
            rules={[{ required: true }]}
          >
            <Select options={STATUS_OPTIONS} />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input />
          </Form.Item>
          {editing?.images?.length ? (
            <div className='mb-2'>
              <div className='mb-1 text-sm text-neutral-500'>当前配图</div>
              <div className='flex flex-wrap gap-2'>
                {editing.images.map((src) => (
                  <img
                    key={src}
                    src={imgUrl(src)}
                    alt=''
                    className='h-16 w-16 rounded border border-neutral-200 object-cover'
                  />
                ))}
              </div>
            </div>
          ) : null}
        </Form>
      </Modal>
    </>
  );
});

export default TrainQaCalibratePage;
