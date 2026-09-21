import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, InputNumber, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { deleteHrFailReasonApi, getHrFailReasonOptionsApi, saveHrFailReasonApi } from '@/api/hr';

type FailReasonRow = {
  id: number;
  name: string;
  sortNo: number;
};

const FailReasonPage = memo(function FailReasonPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<FailReasonRow | null>(null);
  const [form] = Form.useForm();

  const openEdit = (record?: FailReasonRow) => {
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
      await saveHrFailReasonApi({
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

  const columns: ProColumnType<FailReasonRow>[] = [
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
              perm: 'hr:fail-reason:edit',
              onClick: () => openEdit(record),
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'hr:fail-reason:edit',
              confirmTitle: `确认删除「${record.name}」？已有评价使用该原因时不能删除。`,
              onClick: async () => {
                await deleteHrFailReasonApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    { title: '未通过原因', dataIndex: 'name', ellipsis: true },
    { title: '排序', dataIndex: 'sortNo', width: 90, search: false },
  ];

  return (
    <>
      <BaseProTable<FailReasonRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='未通过原因'
        columns={columns}
        search={false}
        pagination={false}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='hr:fail-reason:edit'
            onClick={() => openEdit()}
          >
            新增
          </PermissionButton>,
        ]}
        request={async () => {
          const list = await getHrFailReasonOptionsApi();
          const rows = (list ?? []).map((row) => ({
            id: row.id,
            name: row.name,
            sortNo: row.sortNo ?? row.sort_no ?? 0,
          }));
          return { data: rows, success: true, total: rows.length };
        }}
      />
      <Modal
        title={editing ? '编辑未通过原因' : '新增未通过原因'}
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
            label='原因'
            rules={[{ required: true, message: '请填写未通过原因' }]}
          >
            <Input
              maxLength={64}
              placeholder='例如：候选人能力不符'
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

export default FailReasonPage;
