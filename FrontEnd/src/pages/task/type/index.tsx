import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createTaskTypeApi,
  deleteTaskTypeApi,
  getTaskTypeListApi,
  getTaskTypeOptionsApi,
  updateTaskTypeApi,
  type SysTaskType,
} from '@/api/task';

/** 业务类型选项；新增业务回写时前后端同步扩展 */
const BIZ_TYPE_OPTIONS = [
  { label: '无（普通任务）', value: '' },
  { label: 'GEO内容投放', value: 'geo_content_placement' },
];

/** 回写字段编码；与后端 TaskBizFieldWriter 约定一致 */
const ASSIGN_FIELD_OPTIONS = [
  { label: '无', value: '' },
  { label: '发布人 (publisher)', value: 'publisher' },
  { label: '撰写人 (writer)', value: 'writer' },
];

type TypeOption = { label: string; value: string };

const TaskTypePage = memo(function TaskTypePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SysTaskType | null>(null);
  const [typeOptions, setTypeOptions] = useState<TypeOption[]>([]);

  const loadTypeOptions = useCallback(async () => {
    const list = await getTaskTypeOptionsApi();
    setTypeOptions((list || []).map((t) => ({ label: t.typeName, value: t.typeName })));
  }, []);

  useEffect(() => {
    void loadTypeOptions();
  }, [loadTypeOptions]);

  const columns: ProColumnType<SysTaskType>[] = useMemo(
    () => [
      {
        title: '类型名称',
        dataIndex: 'typeName',
        formItemProps: { rules: [{ required: true, message: '请输入类型名称' }] },
        fieldProps: { maxLength: 64 },
      },
      {
        title: '关联业务',
        dataIndex: 'bizType',
        width: 160,
        valueType: 'select',
        fieldProps: { options: BIZ_TYPE_OPTIONS, allowClear: true },
        render: (_, r) => BIZ_TYPE_OPTIONS.find((x) => x.value === (r.bizType || ''))?.label || r.bizType || '-',
      },
      {
        title: '回写字段',
        dataIndex: 'assignField',
        width: 150,
        valueType: 'select',
        search: false,
        fieldProps: { options: ASSIGN_FIELD_OPTIONS, allowClear: true },
        render: (_, r) =>
          ASSIGN_FIELD_OPTIONS.find((x) => x.value === (r.assignField || ''))?.label || r.assignField || '-',
      },
      {
        title: '派发下一任务',
        dataIndex: 'spawnTaskType',
        width: 140,
        valueType: 'select',
        search: false,
        dependencies: ['typeName'],
        fieldProps: (form: { getFieldValue: (name: string) => unknown }) => {
          const selfName = String(form?.getFieldValue?.('typeName') || '').trim();
          return {
            options: typeOptions.filter((o) => o.value !== selfName),
            allowClear: true,
            showSearch: true,
            optionFilterProp: 'label',
            placeholder: '选择下一任务类型',
          };
        },
        render: (_, r) => r.spawnTaskType || '-',
      },
      {
        title: '完成需证明',
        dataIndex: 'requireProof',
        width: 110,
        valueType: 'switch',
        search: false,
        fieldProps: { checkedChildren: '是', unCheckedChildren: '否' },
        render: (_, r) => (Number(r.requireProof) === 1 || r.requireProof === true ? '是' : '否'),
      },
      {
        title: '排序',
        dataIndex: 'sortOrder',
        width: 80,
        search: false,
        valueType: 'digit',
        fieldProps: { precision: 0, min: 0 },
      },
      {
        title: '备注',
        dataIndex: 'remark',
        search: false,
        ellipsis: true,
        fieldProps: { maxLength: 500 },
      },
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
                perm: 'task:type:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'delete',
                label: '删除',
                perm: 'task:type:delete',
                confirmTitle: '确定删除该任务类型？',
                onClick: async () => {
                  await deleteTaskTypeApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                  void loadTypeOptions();
                },
              },
            ]}
          />
        ),
      },
    ],
    [loadTypeOptions, message, typeOptions],
  );

  return (
    <>
      <BaseProTable<SysTaskType>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='任务类型'
        request={async (params) => {
          const res = await getTaskTypeListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            typeName: params.typeName,
          });
          return { data: res.rows ?? [], total: res.total ?? 0, success: true };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='task:type:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新建类型
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑任务类型' : '新建任务类型'}
        columns={columns as any}
        open={open}
        onOpenChange={(v) => {
          setOpen(v);
          if (!v) setEditing(null);
        }}
        initialValues={
          editing
            ? {
                typeName: editing.typeName,
                sortOrder: editing.sortOrder ?? 0,
                remark: editing.remark,
                bizType: editing.bizType || '',
                assignField: editing.assignField || '',
                spawnTaskType: editing.spawnTaskType || '',
                requireProof: Number(editing.requireProof) === 1 || editing.requireProof === true,
              }
            : { sortOrder: 0, bizType: '', assignField: '', spawnTaskType: '', requireProof: false }
        }
        onFinish={async (values) => {
          const typeName = String(values.typeName || '').trim();
          const spawnTaskType = String(values.spawnTaskType || '').trim();
          if (spawnTaskType && spawnTaskType === typeName) {
            message.error('派发下一任务不能选择自身类型');
            return false;
          }
          const payload = {
            typeName,
            sortOrder: values.sortOrder ?? 0,
            remark: values.remark,
            bizType: values.bizType || '',
            assignField: values.assignField || '',
            spawnTaskType,
            requireProof: !!values.requireProof,
          };
          if (editing) {
            await updateTaskTypeApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createTaskTypeApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          void loadTypeOptions();
          return true;
        }}
      />
    </>
  );
});

export default TaskTypePage;
