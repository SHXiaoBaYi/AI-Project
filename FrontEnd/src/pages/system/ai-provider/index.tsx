import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Switch, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { getAiProviderListApi, updateAiProviderApi, type AiProviderVO } from '@/api/aiProvider';

const AiProviderPage = memo(function AiProviderPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [editing, setEditing] = useState<AiProviderVO | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const openEdit = (record: AiProviderVO) => {
    setEditing(record);
    form.setFieldsValue({
      apiKey: '',
      model: record.model,
      baseUrl: record.baseUrl || '',
      enabled: record.enabled === 1,
      remark: record.remark || '',
      clearApiKey: false,
    });
    setOpen(true);
  };

  const handleSave = async () => {
    if (!editing) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateAiProviderApi({
        id: editing.id,
        apiKey: values.apiKey || undefined,
        clearApiKey: !!values.clearApiKey,
        model: values.model,
        baseUrl: values.baseUrl || '',
        enabled: values.enabled ? 1 : 0,
        remark: values.remark || '',
      });
      message.success('已保存');
      setOpen(false);
      setEditing(null);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<AiProviderVO>[] = [
    {
      title: '厂商',
      dataIndex: 'providerName',
      width: 160,
      search: false,
      render: (_, r) => (
        <span>
          {r.providerName}
          <span className='ml-2 text-xs text-neutral-400'>{r.provider}</span>
        </span>
      ),
    },
    {
      title: 'API Key',
      dataIndex: 'apiKeyMasked',
      search: false,
      ellipsis: true,
      render: (_, r) =>
        r.hasApiKey ? <span className='font-mono text-sm'>{r.apiKeyMasked}</span> : <Tag color='warning'>未配置</Tag>,
    },
    { title: '模型', dataIndex: 'model', search: false, width: 160, ellipsis: true },
    {
      title: 'Base URL',
      dataIndex: 'baseUrl',
      search: false,
      ellipsis: true,
      render: (_, r) => r.baseUrl || <span className='text-neutral-400'>内置预设</span>,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      search: false,
      render: (_, r) => (r.enabled === 1 ? <Tag color='success'>启用</Tag> : <Tag color='default'>停用</Tag>),
    },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '配置',
              perm: 'system:ai:edit',
              onClick: () => openEdit(record),
            },
          ]}
        />
      ),
    },
  ];

  return (
    <>
      <BaseProTable<AiProviderVO>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='AI 模型配置'
        columns={columns}
        search={false}
        pagination={false}
        request={async () => {
          const list = await getAiProviderListApi();
          return { data: list ?? [], success: true, total: list?.length ?? 0 };
        }}
      />

      <Modal
        title={editing ? `配置 ${editing.providerName}` : '配置'}
        open={open}
        onCancel={() => {
          if (saving) return;
          setOpen(false);
          setEditing(null);
        }}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        destroyOnHidden
        width={560}
      >
        {editing?.hasApiKey && (
          <div className='mb-3 text-sm text-neutral-500'>
            当前 Key：<span className='font-mono'>{editing.apiKeyMasked}</span>
            （留空「新 API Key」表示不修改）
          </div>
        )}
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='apiKey'
            label='新 API Key'
            extra='填写后覆盖原 Key；留空则保持不变'
          >
            <Input.Password
              placeholder='sk-...'
              autoComplete='off'
            />
          </Form.Item>
          <Form.Item
            name='clearApiKey'
            label='清空 Key'
            valuePropName='checked'
            extra='勾选后删除已保存的 Key，生成相似问题时不再可选此模型'
          >
            <Switch
              checkedChildren='清空'
              unCheckedChildren='保留'
            />
          </Form.Item>
          <Form.Item
            name='model'
            label='模型名'
            rules={[{ required: true, message: '请输入模型名' }]}
          >
            <Input placeholder='如 qwen-turbo / deepseek-chat' />
          </Form.Item>
          <Form.Item
            name='baseUrl'
            label='Base URL'
            extra='一般留空使用内置兼容地址'
          >
            <Input placeholder='可选，OpenAI 兼容 /v1 根路径' />
          </Form.Item>
          <Form.Item
            name='enabled'
            label='启用'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea
              rows={2}
              placeholder='可选'
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default AiProviderPage;
