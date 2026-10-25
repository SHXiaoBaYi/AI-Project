import { memo, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Drawer, Form, Input, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  listTrainDocsApi,
  listTrainVersionsApi,
  pageTrainQaItemsApi,
  updateTrainQaItemApi,
  type TrainQaLibraryItem,
} from '@/api/train';
import { resolveUploadUrl } from '@/utils/uploadUrl';

function trainImageUrl(url?: string) {
  if (!url) return '';
  if (/^(data|blob):/i.test(url)) return url;
  return resolveUploadUrl(url);
}

function rewriteAnswerHtml(record: TrainQaLibraryItem) {
  const source =
    record.answerHtml && record.answerHtml.trim()
      ? record.answerHtml
      : `<div class="train-qa-answer">${(record.answer || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br/>')}</div>`;
  let html = source.replace(/(<img\b[^>]*\bsrc=["'])([^"']+)(["'])/gi, (_all, pre, src, post) => {
    return `${pre}${trainImageUrl(src)}${post}`;
  });
  if (!/<img\b/i.test(html) && record.images?.length) {
    html += record.images
      .map(
        (src) =>
          `<img src="${trainImageUrl(src)}" alt="培训配图" style="max-width:100%;height:auto;display:block;margin:10px 0;" />`,
      )
      .join('');
  }
  return html;
}

function htmlToPlain(html: string) {
  const el = document.createElement('div');
  el.innerHTML = html;
  return (el.innerText || '').replace(/\u00a0/g, ' ').trim();
}

function RichAnswer({ initialHtml, onChange }: { initialHtml: string; onChange: (html: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.innerHTML = initialHtml;
  }, [initialHtml]);
  return (
    <div
      ref={ref}
      contentEditable
      suppressContentEditableWarning
      className='min-h-72 rounded-md border border-neutral-200 bg-white p-3 text-sm leading-7 break-words outline-none [&_img]:my-2.5 [&_img]:block [&_img]:h-auto [&_img]:max-w-full [&_img]:rounded-md [&_img]:border [&_img]:border-neutral-200'
      onInput={() => onChange(ref.current?.innerHTML || '')}
    />
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
  const [answerHtml, setAnswerHtml] = useState('');
  const [form] = Form.useForm();

  const openDetail = (record: TrainQaLibraryItem) => {
    setEditing(record);
    setAnswerHtml(rewriteAnswerHtml(record));
    form.setFieldsValue({
      question: record.question || '',
      aliases: record.aliases || '',
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
        answer: htmlToPlain(answerHtml),
        answerHtml,
      });
      message.success('题目已保存');
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
              label: '详情',
              perm: 'train:doc:edit',
              onClick: () => openDetail(record),
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
        scroll={{ x: 1100 }}
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
      <Drawer
        title={
          editing ? `题目详情 · ${editing.fileTitle || editing.fileName || editing.docTitle || '文档'}` : '题目详情'
        }
        open={open}
        onClose={() => setOpen(false)}
        width={760}
        destroyOnHidden
        extra={
          <Button
            type='primary'
            loading={saving}
            onClick={() => void handleSave()}
          >
            保存
          </Button>
        }
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
            extra='一行一条。'
          >
            <Input.TextArea
              rows={3}
              placeholder={'找厂子订货\n怎么给厂家下单'}
            />
          </Form.Item>
          <Form.Item label='答文'>
            {editing ? (
              <RichAnswer
                key={editing.id}
                initialHtml={rewriteAnswerHtml(editing)}
                onChange={setAnswerHtml}
              />
            ) : null}
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
});

export default TrainItemPage;
