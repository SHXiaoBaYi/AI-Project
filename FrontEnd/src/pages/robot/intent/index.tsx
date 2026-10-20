import { useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Switch } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { listRobotIntentsApi, updateRobotIntentApi, type RobotIntent } from '@/api/robot';

export default function RobotIntentPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [editing, setEditing] = useState<RobotIntent | null>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const columns: ProColumnType<RobotIntent>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, row) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'robot:intent:edit',
              onClick: () => {
                setEditing(row);
                form.setFieldsValue({
                  intentName: row.intentName,
                  sampleUtterances: row.sampleUtterances,
                  remark: row.remark,
                  enabled: row.enabled === 1,
                  allowGroup: row.allowGroup !== 0,
                  allowDm: row.allowDm !== 0,
                });
                setOpen(true);
              },
            },
          ]}
        />
      ),
    },
    { title: '编码', dataIndex: 'intentCode', width: 180, search: false, copyable: true },
    { title: '名称', dataIndex: 'intentName', width: 160 },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      search: false,
      render: (_, r) => (r.enabled === 1 ? '是' : '否'),
    },
    { title: '示例问法', dataIndex: 'sampleUtterances', ellipsis: true, search: false },
    {
      title: '群/单聊',
      search: false,
      width: 100,
      render: (_, r) => `${r.allowGroup === 1 ? '群' : '-'}/${r.allowDm === 1 ? '单' : '-'}`,
    },
  ];

  return (
    <>
      <BaseProTable<RobotIntent>
        actionRef={actionRef}
        rowKey='id'
        columns={columns}
        headerTitle='机器人意图管理'
        search={false}
        request={async () => {
          const data = await listRobotIntentsApi();
          const rows = (data || []).map((r) => ({
            ...r,
            intentCode: (r as unknown as { intent_code?: string }).intent_code || r.intentCode,
            intentName: (r as unknown as { intent_name?: string }).intent_name || r.intentName,
            sampleUtterances: (r as unknown as { sample_utterances?: string }).sample_utterances || r.sampleUtterances,
            allowGroup: (r as unknown as { allow_group?: number }).allow_group ?? r.allowGroup,
            allowDm: (r as unknown as { allow_dm?: number }).allow_dm ?? r.allowDm,
          }));
          return { data: rows, success: true, total: rows.length };
        }}
      />
      <Modal
        title='编辑意图'
        open={open}
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={async () => {
          if (!editing) return;
          const v = await form.validateFields();
          setSaving(true);
          try {
            await updateRobotIntentApi(editing.id, {
              intentName: v.intentName,
              sampleUtterances: v.sampleUtterances,
              remark: v.remark,
              enabled: v.enabled ? 1 : 0,
              allowGroup: v.allowGroup ? 1 : 0,
              allowDm: v.allowDm ? 1 : 0,
            });
            message.success('已保存');
            setOpen(false);
            actionRef.current?.reload();
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='intentName'
            label='名称'
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='sampleUtterances'
            label='示例问法（分号分隔）'
          >
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item
            name='enabled'
            label='启用'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name='allowGroup'
            label='允许群聊'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name='allowDm'
            label='允许单聊'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
