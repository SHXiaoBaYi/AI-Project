import { memo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Image, Input, Modal, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  listTrainDocsApi,
  listTrainVersionsApi,
  pageTrainQaItemsApi,
  updateTrainQaItemApi,
  type TrainQaLibraryItem,
} from '@/api/train';
import { isLocalhostHost } from '@/utils/localhostAccess';
import { resolveUploadUrl } from '@/utils/uploadUrl';

function trainImageUrl(url?: string) {
  if (!url) return '';
  if (/^(data|blob):/i.test(url)) return url;
  const path = url.startsWith('/uploads/') ? url : url.includes('/uploads/') ? url.slice(url.indexOf('/uploads/')) : '';
  if (path && isLocalhostHost()) return path;
  return resolveUploadUrl(url);
}

function answerImageUrls(record: TrainQaLibraryItem) {
  const fromHtml = Array.from(record.answerHtml?.matchAll(/<img\b[^>]*\bsrc=["']([^"']+)["']/gi) || []).map(
    (m) => m[1],
  );
  const raw = fromHtml.length ? fromHtml : record.images || [];
  return raw.map((u) => trainImageUrl(u)).filter(Boolean);
}

function AnswerWithImages({ record, imagesOnly = false }: { record: TrainQaLibraryItem; imagesOnly?: boolean }) {
  const html = record.answerHtml || '';
  const urls = answerImageUrls(record);
  const htmlHasImg = /<img\b/i.test(html);
  if (imagesOnly) {
    if (!urls.length) return null;
    return (
      <Image.PreviewGroup>
        <div className='space-y-2'>
          {urls.map((src) => (
            <Image
              key={src}
              src={src}
              alt='培训配图'
              className='block h-auto max-w-full cursor-zoom-in rounded-md border border-neutral-200'
            />
          ))}
        </div>
      </Image.PreviewGroup>
    );
  }
  return (
    <div className='min-w-0'>
      {htmlHasImg ? (
        <div
          className='break-words text-neutral-700 [&_img]:my-2 [&_img]:block [&_img]:h-auto [&_img]:max-w-full [&_img]:rounded-md'
          dangerouslySetInnerHTML={{
            __html: html.replace(/(<img\b[^>]*\bsrc=["'])([^"']+)(["'])/gi, (_all, pre, src, post) => {
              return `${pre}${trainImageUrl(src)}${post}`;
            }),
          }}
        />
      ) : (
        <div className='break-words whitespace-pre-wrap'>{record.answer || '—'}</div>
      )}
      {!htmlHasImg && urls.length ? (
        <Image.PreviewGroup>
          <div className='mt-2 space-y-2'>
            {urls.map((src) => (
              <Image
                key={src}
                src={src}
                alt='培训配图'
                className='block h-auto max-h-40 w-auto max-w-full cursor-zoom-in rounded-md border border-neutral-200'
              />
            ))}
          </div>
        </Image.PreviewGroup>
      ) : null}
    </div>
  );
}

const TrainItemPage = memo(function TrainItemPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [searchParams] = useSearchParams();
  const presetDocId = Number(searchParams.get('docId') || '') || undefined;
  const presetVersionId = Number(searchParams.get('versionId') || '') || undefined;
  const presetFileId = Number(searchParams.get('fileId') || '') || undefined;
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<TrainQaLibraryItem | null>(null);
  const [form] = Form.useForm();

  const openEdit = (record: TrainQaLibraryItem) => {
    setEditing(record);
    form.setFieldsValue({
      question: record.question || '',
      aliases: record.aliases || '',
      answer: record.answer || '',
    });
    setOpen(true);
  };

  const handleSave = async () => {
    if (!editing) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateTrainQaItemApi(editing.id, {
        question: String(values.question).trim(),
        aliases: values.aliases ? String(values.aliases) : '',
        answer: values.answer ? String(values.answer) : undefined,
      });
      message.success('题目已保存，向量会随后更新');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<TrainQaLibraryItem>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'train:doc:edit',
              onClick: () => openEdit(record),
            },
          ]}
        />
      ),
    },
    {
      title: '文档',
      dataIndex: 'docId',
      width: 180,
      valueType: 'select',
      fieldProps: { showSearch: true, allowClear: true, placeholder: '全部文档' },
      request: async () => {
        const docs = await listTrainDocsApi();
        return (docs || []).map((d) => ({ label: d.title, value: d.id }));
      },
      render: (_, record) => record.docTitle || '—',
    },
    {
      title: '版本',
      dataIndex: 'versionId',
      hideInTable: true,
      valueType: 'select',
      dependencies: ['docId'],
      fieldProps: { allowClear: true, placeholder: '先选文档，再选版本' },
      request: async (params) => {
        const docId = Number(params.docId);
        if (!docId) return [];
        const rows = await listTrainVersionsApi(docId);
        return (rows || []).map((v) => ({
          label: `${v.versionLabel || `v${v.versionNo}`}${v.latest ? ' · 最新' : ''}`,
          value: v.id,
        }));
      },
    },
    {
      title: '版本',
      dataIndex: 'versionLabel',
      width: 140,
      search: false,
      render: (_, record) => (
        <span>
          {record.versionLabel || (record.versionNo ? `v${record.versionNo}` : '—')}
          {record.latest ? (
            <Tag
              color='green'
              className='ml-1'
            >
              最新
            </Tag>
          ) : (
            <Tag className='ml-1'>历史</Tag>
          )}
        </span>
      ),
    },
    {
      title: '收银文档',
      dataIndex: 'fileTitle',
      width: 180,
      ellipsis: true,
      search: false,
      render: (_, record) => record.fileTitle || record.fileName || '—',
    },
    {
      title: '范围',
      dataIndex: 'scope',
      hideInTable: true,
      valueType: 'select',
      initialValue: 'latest',
      fieldProps: { allowClear: false },
      valueEnum: {
        latest: { text: '仅最新版' },
        all: { text: '含历史版本' },
      },
    },
    {
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '题干 / 同义问法 / 答文' },
    },
    {
      title: '序号',
      dataIndex: 'sortNo',
      width: 70,
      search: false,
    },
    {
      title: '题干',
      dataIndex: 'question',
      ellipsis: true,
      search: false,
      width: 260,
    },
    {
      title: '同义问法',
      dataIndex: 'aliases',
      ellipsis: true,
      search: false,
      width: 200,
      render: (_, record) => record.aliases || '—',
    },
    {
      title: '答文',
      dataIndex: 'answer',
      search: false,
      width: 360,
      render: (_, record) => <AnswerWithImages record={record} />,
    },
    {
      title: '向量',
      dataIndex: 'embedded',
      width: 100,
      search: false,
      render: (_, record) =>
        record.embedded ? <Tag color='success'>已入库</Tag> : <Tag color='processing'>处理中</Tag>,
    },
  ];

  return (
    <>
      <BaseProTable<TrainQaLibraryItem>
        key={`${presetDocId ?? 'all'}-${presetVersionId ?? ''}-${presetFileId ?? ''}`}
        rowKey='id'
        actionRef={actionRef}
        headerTitle='题目'
        columns={columns}
        scroll={{ x: 1500 }}
        form={{
          initialValues: {
            docId: presetDocId,
            versionId: presetVersionId,
            scope: presetVersionId ? 'all' : 'latest',
          },
        }}
        request={async (params) => {
          const docId = params.docId ? Number(params.docId) : presetDocId;
          const versionId = params.versionId ? Number(params.versionId) : presetVersionId;
          const res = await pageTrainQaItemsApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            docId,
            versionId,
            fileId: presetFileId,
            onlyLatest: versionId ? false : params.scope !== 'all',
            keyword: params.keyword ? String(params.keyword) : undefined,
          });
          return { data: res.rows || [], total: res.total || 0, success: true };
        }}
      />
      <Modal
        title={
          editing
            ? `编辑题目 · ${editing.docTitle || '文档'} / ${editing.versionLabel || (editing.versionNo ? `v${editing.versionNo}` : '版本')}`
            : '编辑题目'
        }
        open={open}
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={() => void handleSave()}
        width={640}
        destroyOnHidden
      >
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='question'
            label='题干'
            rules={[{ required: true, message: '请填写题干' }]}
          >
            <Input.TextArea
              rows={2}
              maxLength={500}
            />
          </Form.Item>
          <Form.Item
            name='aliases'
            label='同义问法'
            extra='一行一条。保存后只重算这一道题的向量。'
          >
            <Input.TextArea
              rows={4}
              placeholder={'找厂子订货\n怎么给厂家下单'}
            />
          </Form.Item>
          <Form.Item
            name='answer'
            label='答文'
            extra='配图跟在答文后面，改文字不会去掉图。'
          >
            <Input.TextArea rows={6} />
          </Form.Item>
          {editing ? (
            <AnswerWithImages
              record={editing}
              imagesOnly
            />
          ) : null}
        </Form>
      </Modal>
    </>
  );
});

export default TrainItemPage;
