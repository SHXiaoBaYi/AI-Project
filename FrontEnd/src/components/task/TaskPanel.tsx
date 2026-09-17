import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Modal, Select, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  assignTaskApi,
  createTaskApi,
  deleteTaskApi,
  getTaskListApi,
  getTaskTypeOptionsApi,
  updateTaskApi,
  type SysTask,
} from '@/api/task';
import { getGeoOwnerOptionsApi } from '@/api/geo';
import type { GeoOwnerOption } from '@/types/geo';
import { TASK_MINE_STATUSES, TASK_PRIORITIES, TASK_STATUSES } from '@/constants/task';

type Props = {
  mineOnly?: boolean;
  headerTitle?: string;
};

const priorityColor: Record<number, string> = {
  1: 'default',
  2: 'processing',
  3: 'warning',
  4: 'error',
};

const statusColor: Record<string, string> = {
  待分配: 'default',
  未开始: 'processing',
  待处理: 'processing',
  进行中: 'blue',
  已完成: 'success',
  已取消: 'default',
};

const TaskPanel = memo(function TaskPanel({ mineOnly = false, headerTitle }: Props) {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SysTask | null>(null);
  const [assignOpen, setAssignOpen] = useState(false);
  const [assigning, setAssigning] = useState<SysTask | null>(null);
  const [assignForm] = Form.useForm();
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [typeOptions, setTypeOptions] = useState<{ label: string; value: string }[]>([]);
  const defaultType = typeOptions[0]?.value || '日常';

  useEffect(() => {
    void getGeoOwnerOptionsApi().then((list) => setOwners(list ?? []));
    void getTaskTypeOptionsApi().then((list) => {
      const opts = (list ?? []).map((t) => ({ label: t.typeName, value: t.typeName }));
      setTypeOptions(opts);
    });
  }, []);

  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);

  const isGeoAssignTask = (r: SysTask) => r.taskType === 'GEO文章待分配发布人' || r.taskType === 'GEO文章待分配撰写人';

  const statusOptions = mineOnly ? [...TASK_MINE_STATUSES] : [...TASK_STATUSES];
  const defaultStatus = mineOnly ? '未开始' : '未开始';

  const columns: ProColumnType<SysTask>[] = [
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
      formItemProps: { rules: [{ required: true, message: '请输入标题' }] },
      fieldProps: { maxLength: 200 },
    },
    {
      title: '类型',
      dataIndex: 'taskType',
      width: 140,
      valueType: 'select',
      fieldProps: { options: typeOptions, allowClear: true, showSearch: true, optionFilterProp: 'label' },
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 90,
      valueType: 'select',
      fieldProps: { options: [...TASK_PRIORITIES], allowClear: true },
      render: (_, r) => {
        const p = r.priority ?? 2;
        const label = TASK_PRIORITIES.find((x) => x.value === p)?.label || String(p);
        return <Tag color={priorityColor[p] || 'default'}>{label}</Tag>;
      },
    },
    {
      title: '状态',
      dataIndex: 'statuses',
      hideInTable: true,
      hideInForm: true,
      valueType: 'select',
      initialValue: mineOnly ? ['未开始'] : ['待分配'],
      fieldProps: {
        mode: 'multiple',
        options: statusOptions,
        allowClear: true,
        maxTagCount: 'responsive',
        placeholder: '选择状态',
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      hideInSearch: true,
      valueType: 'select',
      fieldProps: { options: statusOptions, allowClear: true },
      render: (_, r) => {
        const s = r.status || '未开始';
        return (
          <span className='inline-flex items-center gap-1'>
            <Tag color={statusColor[s] || 'default'}>{s}</Tag>
            {r.overdue ? <Tag color='error'>逾期</Tag> : null}
            {r.bizType ? <Tag>外源</Tag> : null}
          </span>
        );
      },
    },
    {
      title: '仅逾期',
      dataIndex: 'overdueOnly',
      valueType: 'select',
      hideInTable: true,
      hideInForm: true,
      fieldProps: {
        options: [
          { label: '是', value: true },
          { label: '否', value: false },
        ],
        allowClear: true,
      },
    },
    {
      title: '负责人',
      dataIndex: 'ownerUserId',
      width: 110,
      valueType: 'select',
      fieldProps: {
        options: userOptions,
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: !mineOnly,
      },
      formItemProps: { rules: [{ required: true, message: '请选择负责人' }] },
      render: (_, r) => r.ownerName || '-',
    },
    {
      title: '执行人',
      dataIndex: 'assigneeUserIds',
      width: 160,
      valueType: 'select',
      search: false,
      fieldProps: {
        options: userOptions,
        mode: 'multiple',
        showSearch: true,
        optionFilterProp: 'label',
        maxTagCount: 'responsive',
      },
      formItemProps: { rules: [{ required: true, message: '请至少选择一名执行人' }] },
      render: (_, r) => r.assigneeNames || '-',
    },
    {
      title: '进度',
      dataIndex: 'progress',
      width: 80,
      valueType: 'digit',
      search: false,
      fieldProps: { min: 0, max: 100 },
      render: (_, r) => `${r.progress ?? 0}%`,
    },
    {
      title: '计划截止',
      dataIndex: 'planEndTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '计划开始',
      dataIndex: 'planStartTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
      hideInTable: true,
    },
    {
      title: '说明',
      dataIndex: 'content',
      valueType: 'textarea',
      search: false,
      hideInTable: true,
      fieldProps: { rows: 3 },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      search: false,
      ellipsis: true,
      valueType: 'textarea',
      hideInTable: true,
    },
    {
      title: '创建人',
      dataIndex: 'creatorName',
      width: 100,
      search: false,
      hideInForm: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
      hideInForm: true,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 180,
      render: (_, record) => {
        const items = [
          record.assignable
            ? {
                key: 'assign',
                label: '分配',
                onClick: () => {
                  setAssigning(record);
                  assignForm.setFieldsValue({
                    assigneeUserId: undefined,
                    assigneeUserIds: record.assigneeUserIds || [],
                    ownerUserId: record.ownerUserId,
                  });
                  setAssignOpen(true);
                },
              }
            : null,
          {
            key: 'edit',
            label: '编辑',
            perm: 'task:edit',
            onClick: () => {
              setEditing(record);
              setOpen(true);
            },
          },
          record.deletable !== false
            ? {
                key: 'delete',
                label: '删除',
                perm: 'task:delete',
                confirmTitle: '确定删除该任务？',
                onClick: async () => {
                  await deleteTaskApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              }
            : null,
        ].filter(Boolean) as {
          key: string;
          label: string;
          perm: string;
          confirmTitle?: string;
          onClick: () => void;
        }[];
        return <ActionButtons items={items} />;
      },
    },
  ];

  return (
    <>
      <BaseProTable<SysTask>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle={headerTitle || (mineOnly ? '我的任务' : '任务管理')}
        request={async (params) => {
          const res = await getTaskListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            title: params.title,
            taskType: params.taskType,
            statuses: Array.isArray(params.statuses)
              ? params.statuses
              : params.statuses
                ? [params.statuses]
                : params.status
                  ? [params.status]
                  : undefined,
            priority: params.priority,
            ownerUserId: params.ownerUserId,
            overdueOnly: params.overdueOnly === true || params.overdueOnly === 'true',
            mineOnly,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() =>
          mineOnly
            ? []
            : [
                <PermissionButton
                  key='add'
                  type='primary'
                  perm='task:add'
                  onClick={() => {
                    setEditing(null);
                    setOpen(true);
                  }}
                >
                  新建任务
                </PermissionButton>,
              ]
        }
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑任务' : '新建任务'}
        columns={columns as any}
        open={open}
        onOpenChange={(v) => {
          setOpen(v);
          if (!v) setEditing(null);
        }}
        modalProps={{ width: 800 }}
        initialValues={
          editing
            ? {
                ...editing,
                assigneeUserIds: editing.assigneeUserIds || [],
                taskType: editing.taskType || defaultType,
                priority: editing.priority ?? 2,
                status: editing.status || defaultStatus,
                progress: editing.progress ?? 0,
              }
            : {
                taskType: defaultType,
                priority: 2,
                status: defaultStatus,
                progress: 0,
                assigneeUserIds: [],
              }
        }
        onFinish={async (values) => {
          const assigneeUserIds = (values.assigneeUserIds || []).filter(Boolean);
          if (!assigneeUserIds.length) {
            message.error('请至少选择一名执行人');
            return false;
          }
          const payload = {
            title: values.title,
            content: values.content,
            taskType: values.taskType || defaultType,
            priority: values.priority ?? 2,
            status: values.status || defaultStatus,
            progress: values.progress ?? 0,
            ownerUserId: values.ownerUserId,
            assigneeUserIds,
            planStartTime: values.planStartTime,
            planEndTime: values.planEndTime,
            remark: values.remark,
          };
          if (editing) {
            await updateTaskApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createTaskApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
      <Modal
        title={
          assigning
            ? isGeoAssignTask(assigning)
              ? assigning.taskType === 'GEO文章待分配发布人'
                ? '分配发布人'
                : '分配撰写人'
              : '分配任务'
            : '分配任务'
        }
        open={assignOpen}
        onCancel={() => {
          setAssignOpen(false);
          setAssigning(null);
          assignForm.resetFields();
        }}
        onOk={async () => {
          const values = await assignForm.validateFields();
          if (!assigning) return;
          const geo = isGeoAssignTask(assigning);
          const assigneeUserIds = geo
            ? [values.assigneeUserId].filter(Boolean)
            : (values.assigneeUserIds || []).filter(Boolean);
          if (!assigneeUserIds.length) {
            message.error(geo ? '请选择被分配人' : '请至少选择一名执行人');
            return;
          }
          await assignTaskApi(assigning.id, {
            ownerUserId: geo ? values.assigneeUserId : values.ownerUserId || assigneeUserIds[0],
            assigneeUserIds,
          });
          message.success(geo ? '已分配并回写投放管理' : '已分配');
          setAssignOpen(false);
          setAssigning(null);
          assignForm.resetFields();
          actionRef.current?.reload();
        }}
        destroyOnHidden
      >
        {assigning ? (
          <div className='mb-3 text-sm text-neutral-500'>
            {assigning.title}
            {assigning.bizTitle ? <div className='mt-1'>关联：{assigning.bizTitle}</div> : null}
          </div>
        ) : null}
        <Form
          form={assignForm}
          layout='vertical'
        >
          {assigning && isGeoAssignTask(assigning) ? (
            <Form.Item
              name='assigneeUserId'
              label={assigning.taskType === 'GEO文章待分配发布人' ? '发布人' : '撰写人'}
              rules={[{ required: true, message: '请选择' }]}
            >
              <Select
                options={userOptions}
                showSearch
                optionFilterProp='label'
                placeholder='请选择人员'
              />
            </Form.Item>
          ) : (
            <>
              <Form.Item
                name='ownerUserId'
                label='负责人'
                rules={[{ required: true, message: '请选择负责人' }]}
              >
                <Select
                  options={userOptions}
                  showSearch
                  optionFilterProp='label'
                />
              </Form.Item>
              <Form.Item
                name='assigneeUserIds'
                label='执行人'
                rules={[{ required: true, message: '请至少选择一名执行人' }]}
              >
                <Select
                  mode='multiple'
                  options={userOptions}
                  showSearch
                  optionFilterProp='label'
                  maxTagCount='responsive'
                />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </>
  );
});

export default TaskPanel;
