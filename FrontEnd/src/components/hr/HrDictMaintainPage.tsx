import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, InputNumber, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { deleteHrDictApi, getHrDictOptionsApi, saveHrDictApi, type HrDictType } from '@/api/hr';

type DictRow = {
  id: number;
  code: string;
  category: string;
  name: string;
  sortNo: number;
};

type Props = {
  dictType: HrDictType;
  title: string;
  nameLabel: string;
  namePlaceholder?: string;
  permEdit: string;
  extraHint?: string;
  showCode?: boolean;
  codeLabel?: string;
  codePlaceholder?: string;
  showCategory?: boolean;
  categoryLabel?: string;
  categoryPlaceholder?: string;
  deleteHint?: string;
};

const HrDictMaintainPage = memo(function HrDictMaintainPage({
  dictType,
  title,
  nameLabel,
  namePlaceholder,
  permEdit,
  extraHint,
  showCode = false,
  codeLabel = '编码',
  codePlaceholder,
  showCategory = false,
  categoryLabel = '地址类别',
  categoryPlaceholder,
  deleteHint,
}: Props) {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<DictRow | null>(null);
  const [form] = Form.useForm();

  const openEdit = (record?: DictRow) => {
    setEditing(record ?? null);
    form.setFieldsValue({
      code: record?.code || '',
      category: record?.category || '',
      name: record?.name || '',
      sortNo: record?.sortNo ?? 0,
    });
    setOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await saveHrDictApi(dictType, {
        id: editing?.id,
        code: showCode ? String(values.code ?? '').trim() : undefined,
        category: showCategory ? String(values.category ?? '').trim() : undefined,
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

  const columns: ProColumnType<DictRow>[] = [
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
              perm: permEdit,
              onClick: () => openEdit(record),
            },
            {
              key: 'delete',
              label: '删除',
              perm: permEdit,
              confirmTitle: `确认删除「${showCode && record.code ? `${record.code} ${record.name}` : record.name}」？${deleteHint || ''}`,
              onClick: async () => {
                await deleteHrDictApi(dictType, record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
    ...(showCode
      ? [
          {
            title: codeLabel,
            dataIndex: 'code',
            width: 120,
            ellipsis: true,
          } as ProColumnType<DictRow>,
        ]
      : []),
    ...(showCategory
      ? [
          {
            title: categoryLabel,
            dataIndex: 'category',
            width: 140,
            ellipsis: true,
          } as ProColumnType<DictRow>,
        ]
      : []),
    { title: nameLabel, dataIndex: 'name', ellipsis: true },
    { title: '排序', dataIndex: 'sortNo', width: 90, search: false },
  ];

  return (
    <>
      <BaseProTable<DictRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle={title}
        columns={columns}
        search={false}
        pagination={false}
        toolBarRender={() => [
          extraHint ? <span className='mr-3 text-xs text-neutral-500'>{extraHint}</span> : null,
          <PermissionButton
            key='add'
            type='primary'
            perm={permEdit}
            onClick={() => openEdit()}
          >
            新增
          </PermissionButton>,
        ]}
        request={async () => {
          const list = await getHrDictOptionsApi(dictType);
          const rows = (list ?? []).map((row) => ({
            id: row.id,
            code: row.code || '',
            category: row.category || '',
            name: row.name,
            sortNo: row.sortNo ?? row.sort_no ?? 0,
          }));
          return { data: rows, success: true, total: rows.length };
        }}
      />
      <Modal
        title={editing ? `编辑${title}` : `新增${title}`}
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
          {showCode ? (
            <Form.Item
              name='code'
              label={codeLabel}
              rules={[{ required: true, message: `请填写${codeLabel}` }]}
            >
              <Input
                maxLength={32}
                placeholder={codePlaceholder || '例如：P0'}
              />
            </Form.Item>
          ) : null}
          {showCategory ? (
            <Form.Item
              name='category'
              label={categoryLabel}
              rules={[{ required: true, message: `请填写${categoryLabel}` }]}
            >
              <Input
                maxLength={64}
                placeholder={categoryPlaceholder || '例如：办公室'}
              />
            </Form.Item>
          ) : null}
          <Form.Item
            name='name'
            label={nameLabel}
            rules={[{ required: true, message: `请填写${nameLabel}` }]}
          >
            <Input
              maxLength={128}
              placeholder={namePlaceholder}
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

export default HrDictMaintainPage;
