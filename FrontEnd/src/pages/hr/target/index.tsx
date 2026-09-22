import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, InputNumber, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { deleteHrTargetApi, getHrTargetOptionsApi, saveHrTargetApi } from '@/api/hr';

type TargetRow = {
  id: number;
  name: string;
  sortNo: number;
};

const TargetPage = memo(function TargetPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<TargetRow | null>(null);
  const [form] = Form.useForm();

  const openEdit = (record?: TargetRow) => {
    setEditing(record ?? null);
    form.setFieldsValue({
      name: record?.name || '',
      sortNo: record?.sortNo ?? 0,
    });
    setOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await saveHrTargetApi({
        id: editing?.id,
        name: String(values.name).trim(),
        sortNo: Number(values.sortNo) || 0,
      });
      message.success('已保存');
      setOpen(false);
      actionRef.current?.reload();
    } finally {
      setSaving(false);
    }
  };

  const columns: ProColumnType<TargetRow>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'hr:target:edit',
              onClick: () => openEdit(record),
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'hr:target:edit',
              confirmTitle: `确认删除「${record.name}」？已有招聘需求使用时不能删除。`,
              onClick: async () => {
                await deleteHrTargetApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '目标到岗', dataIndex: 'name', ellipsis: true },
    { title: '排序', dataIndex: 'sortNo', width: 90, search: false },
  ];

  return (
    <>
      <BaseProTable<TargetRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='目标到岗'
        columns={columns}
        search={false}
        pagination={false}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='hr:target:edit'
            onClick={() => openEdit()}
          >
            新增
          </PermissionButton>,
        ]}
        request={async () => {
          const list = await getHrTargetOptionsApi();
          const rows = (list ?? []).map((row) => ({
            id: row.id,
            name: row.name,
            sortNo: row.sortNo ?? row.sort_no ?? 0,
          }));
          return { data: rows, success: true, total: rows.length };
        }}
      />
      <Modal
        title={editing ? '编辑目标到岗' : '新增目标到岗'}
        open={open}
        confirmLoading={saving}
        destroyOnHidden
        onCancel={() => {
          if (saving) return;
          setOpen(false);
        }}
        onOk={() => void handleSave()}
      >
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='name'
            label='目标到岗'
            rules={[{ required: true, message: '请填写目标到岗' }]}
          >
            <Input
              maxLength={64}
              placeholder='例如：尽快'
            />
          </Form.Item>
          <Form.Item
            name='sortNo'
            label='排序'
            extra='数字越小越靠前'
          >
            <InputNumber
              className='w-full'
              min={0}
              precision={0}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default TargetPage;
