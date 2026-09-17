import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { Key } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Form, Input, Modal, Select, Tag, Upload } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  assignTaskApi,
  batchAssignTaskApi,
  batchCompleteTaskApi,
  completeTaskApi,
  createTaskApi,
  deleteTaskApi,
  getTaskFilesApi,
  getTaskFilesByBizApi,
  getTaskListApi,
  getTaskTypeOptionsApi,
  updateTaskApi,
  uploadTaskFileApi,
  type SysTask,
  type SysTaskFile,
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

function fileOpenUrl(url?: string) {
  if (!url) return '#';
  if (/^https?:\/\//i.test(url)) return url;
  // 开发态优先走 Vite /uploads 代理；无代理时回退直连后端静态资源
  const api = String(import.meta.env.VITE_API_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
  return `${api}${url}`;
}

const TaskPanel = memo(function TaskPanel({ mineOnly = false, headerTitle }: Props) {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SysTask | null>(null);
  const [assignOpen, setAssignOpen] = useState(false);
  const [assigning, setAssigning] = useState<SysTask | null>(null);
  const [batchAssignOpen, setBatchAssignOpen] = useState(false);
  const [batchCompleteOpen, setBatchCompleteOpen] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  const [selectedRows, setSelectedRows] = useState<SysTask[]>([]);
  const [assignForm] = Form.useForm();
  const [batchAssignForm] = Form.useForm();
  const [batchCompleteForm] = Form.useForm();
  const [completeOpen, setCompleteOpen] = useState(false);
  const [completing, setCompleting] = useState<SysTask | null>(null);
  const [completeForm] = Form.useForm();
  const [completeFileList, setCompleteFileList] = useState<UploadFile[]>([]);
  const [filesOpen, setFilesOpen] = useState(false);
  const [filesTitle, setFilesTitle] = useState('附件');
  const [filesLoading, setFilesLoading] = useState(false);
  const [files, setFiles] = useState<SysTaskFile[]>([]);
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

  const batchAllGeo = useMemo(
    () => selectedRows.length > 0 && selectedRows.every((r) => isGeoAssignTask(r)),
    [selectedRows],
  );

  const statusOptions = mineOnly ? [...TASK_MINE_STATUSES] : [...TASK_STATUSES];
  const defaultStatus = mineOnly ? '未开始' : '未开始';

  const openBatchAssign = () => {
    if (!selectedRowKeys.length) {
      message.warning('请先勾选要分配的任务');
      return;
    }
    const assignable = selectedRows.filter((r) => r.assignable);
    if (!assignable.length) {
      message.warning('所选任务均不可分配（请选择待分配等可分配状态）');
      return;
    }
    if (assignable.length < selectedRows.length) {
      message.info(
        `已过滤 ${selectedRows.length - assignable.length} 条不可分配任务，将对 ${assignable.length} 条执行分配`,
      );
    }
    setSelectedRowKeys(assignable.map((r) => r.id));
    setSelectedRows(assignable);
    batchAssignForm.resetFields();
    setBatchAssignOpen(true);
  };

  /** 仅未要求证明附件的任务可批量完成 */
  const canBatchComplete = (r: SysTask) => !!r.completable && !r.requireProof;

  const openBatchComplete = () => {
    if (!selectedRowKeys.length) {
      message.warning('请先勾选要完成的任务');
      return;
    }
    const ready = selectedRows.filter(canBatchComplete);
    if (!ready.length) {
      message.warning('所选任务均不可批量完成（需证明附件的类型请单独「去完成」）');
      return;
    }
    if (ready.length < selectedRows.length) {
      message.info(
        `已过滤 ${selectedRows.length - ready.length} 条需附件/不可完成任务，将对 ${ready.length} 条执行完成`,
      );
    }
    setSelectedRowKeys(ready.map((r) => r.id));
    setSelectedRows(ready);
    batchCompleteForm.resetFields();
    setBatchCompleteOpen(true);
  };

  const openComplete = (record: SysTask) => {
    setCompleting(record);
    completeForm.resetFields();
    setCompleteFileList([]);
    setCompleteOpen(true);
  };

  const openTaskFiles = async (record: SysTask) => {
    setFilesTitle(`任务附件：${record.title}`);
    setFilesOpen(true);
    setFilesLoading(true);
    try {
      const list = await getTaskFilesApi(record.id);
      setFiles(list ?? []);
    } finally {
      setFilesLoading(false);
    }
  };

  const openBizFiles = async (record: SysTask) => {
    if (!record.bizType || !record.bizId) {
      message.warning('该任务未关联业务');
      return;
    }
    setFilesTitle(`业务附件：${record.bizTitle || record.title}`);
    setFilesOpen(true);
    setFilesLoading(true);
    try {
      const list = await getTaskFilesByBizApi(record.bizType, record.bizId);
      setFiles(list ?? []);
    } finally {
      setFilesLoading(false);
    }
  };

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
            {r.requireProof ? <Tag color='orange'>需证明</Tag> : null}
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
      title: '附件',
      dataIndex: 'fileCount',
      width: 70,
      search: false,
      hideInForm: true,
      render: (_, r) =>
        (r.fileCount ?? 0) > 0 ? (
          <a
            onClick={(e) => {
              e.preventDefault();
              void openTaskFiles(r);
            }}
          >
            {r.fileCount}
          </a>
        ) : (
          '-'
        ),
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
      width: mineOnly ? 200 : 180,
      render: (_, record) => {
        const items = [
          mineOnly && record.completable
            ? {
                key: 'complete',
                label: '去完成',
                onClick: () => openComplete(record),
              }
            : null,
          (record.fileCount ?? 0) > 0
            ? {
                key: 'files',
                label: '附件',
                onClick: () => void openTaskFiles(record),
              }
            : null,
          record.bizType && record.bizId
            ? {
                key: 'biz-files',
                label: '业务附件',
                onClick: () => void openBizFiles(record),
              }
            : null,
          !mineOnly && record.assignable
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
          !mineOnly
            ? {
                key: 'edit',
                label: '编辑',
                perm: 'task:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              }
            : null,
          !mineOnly && record.deletable !== false
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
          perm?: string;
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
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, rows) => {
            setSelectedRowKeys(keys);
            setSelectedRows(rows);
          },
          getCheckboxProps: (record) => ({
            disabled: mineOnly ? !canBatchComplete(record) : !record.assignable,
          }),
        }}
        tableAlertRender={({ selectedRowKeys: keys }) =>
          mineOnly ? (
            <span>已选 {keys.length} 条（仅可勾选无需证明附件的可完成任务）</span>
          ) : (
            <span>已选 {keys.length} 条（仅可勾选可分配任务）</span>
          )
        }
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
            ? [
                <Button
                  key='batch-complete'
                  type='primary'
                  disabled={!selectedRowKeys.length}
                  onClick={openBatchComplete}
                >
                  批量完成
                </Button>,
              ]
            : [
                <PermissionButton
                  key='batch-assign'
                  perm='task:edit'
                  disabled={!selectedRowKeys.length}
                  onClick={openBatchAssign}
                >
                  批量分配
                </PermissionButton>,
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
      <Modal
        title={`批量分配（${selectedRowKeys.length} 条）`}
        open={batchAssignOpen}
        onCancel={() => {
          setBatchAssignOpen(false);
          batchAssignForm.resetFields();
        }}
        onOk={async () => {
          const values = await batchAssignForm.validateFields();
          const assigneeUserIds = batchAllGeo
            ? [values.assigneeUserId].filter(Boolean)
            : (values.assigneeUserIds || []).filter(Boolean);
          if (!assigneeUserIds.length) {
            message.error(batchAllGeo ? '请选择被分配人' : '请至少选择一名执行人');
            return;
          }
          const msg = await batchAssignTaskApi({
            taskIds: selectedRowKeys.map((k) => Number(k)),
            ownerUserId: batchAllGeo ? values.assigneeUserId : values.ownerUserId || assigneeUserIds[0],
            assigneeUserIds,
          });
          message.success(typeof msg === 'string' && msg ? msg : '批量分配完成');
          setBatchAssignOpen(false);
          batchAssignForm.resetFields();
          setSelectedRowKeys([]);
          setSelectedRows([]);
          actionRef.current?.reload();
        }}
        destroyOnHidden
      >
        <div className='mb-3 text-sm text-neutral-500'>
          {batchAllGeo
            ? '所选均为 GEO 待分配任务，将统一指定同一人员（并回写投放、派生子任务）'
            : '将把选中任务统一分配给下列负责人/执行人'}
        </div>
        <Form
          form={batchAssignForm}
          layout='vertical'
        >
          {batchAllGeo ? (
            <Form.Item
              name='assigneeUserId'
              label='被分配人'
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
      <Modal
        title='去完成'
        open={completeOpen}
        onCancel={() => {
          setCompleteOpen(false);
          setCompleting(null);
          completeForm.resetFields();
          setCompleteFileList([]);
        }}
        onOk={async () => {
          if (!completing) return;
          const values = await completeForm.validateFields();
          const uploaded = completeFileList
            .filter((f) => f.status === 'done' && f.response)
            .map((f) => {
              const res = f.response as {
                url: string;
                fileName: string;
                fileSize: number;
                contentType: string;
              };
              return {
                fileName: res.fileName || f.name,
                fileUrl: res.url,
                fileSize: res.fileSize ?? f.size,
                contentType: res.contentType || f.type || '',
              };
            });
          if (completing.requireProof && !uploaded.length) {
            message.error('该任务类型必须上传完成证明附件');
            return;
          }
          await completeTaskApi(completing.id, {
            remark: values.remark,
            files: uploaded,
          });
          message.success('已完成');
          setCompleteOpen(false);
          setCompleting(null);
          completeForm.resetFields();
          setCompleteFileList([]);
          actionRef.current?.reload();
        }}
        destroyOnHidden
        okText='确认完成'
      >
        {completing ? (
          <div className='mb-3 text-sm text-neutral-500'>
            <div>{completing.title}</div>
            {completing.bizTitle ? <div className='mt-1'>关联：{completing.bizTitle}</div> : null}
            {completing.requireProof ? (
              <div className='mt-1 text-amber-600'>该类型需上传完成证明，附件将关联业务并带入后续子任务</div>
            ) : null}
          </div>
        ) : null}
        <Form
          form={completeForm}
          layout='vertical'
        >
          <Form.Item
            name='remark'
            label='完成说明'
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder='可选'
            />
          </Form.Item>
          {completing?.requireProof ? (
            <Form.Item
              label='完成证明（必填）'
              required
            >
              <Upload
                multiple
                fileList={completeFileList}
                customRequest={async (opt) => {
                  try {
                    const file = opt.file as File;
                    const res = await uploadTaskFileApi(file);
                    opt.onSuccess?.(res);
                  } catch (e) {
                    opt.onError?.(e as Error);
                  }
                }}
                onChange={({ fileList }) => setCompleteFileList(fileList)}
                onRemove={(file) => {
                  setCompleteFileList((prev) => prev.filter((f) => f.uid !== file.uid));
                }}
              >
                <Button>选择文件</Button>
              </Upload>
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
      <Modal
        title={`批量完成（${selectedRowKeys.length} 条）`}
        open={batchCompleteOpen}
        onCancel={() => {
          setBatchCompleteOpen(false);
          batchCompleteForm.resetFields();
        }}
        onOk={async () => {
          const values = await batchCompleteForm.validateFields();
          const msg = await batchCompleteTaskApi({
            taskIds: selectedRowKeys.map((k) => Number(k)),
            remark: values.remark,
          });
          message.success(typeof msg === 'string' && msg ? msg : '批量完成成功');
          setBatchCompleteOpen(false);
          batchCompleteForm.resetFields();
          setSelectedRowKeys([]);
          setSelectedRows([]);
          actionRef.current?.reload();
        }}
        destroyOnHidden
        okText='确认完成'
      >
        <div className='mb-3 text-sm text-neutral-500'>
          仅完成无需上传证明的任务；需证明的类型请单独「去完成」并上传附件。
        </div>
        <Form
          form={batchCompleteForm}
          layout='vertical'
        >
          <Form.Item
            name='remark'
            label='统一完成说明'
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder='可选'
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={filesTitle}
        open={filesOpen}
        onCancel={() => {
          setFilesOpen(false);
          setFiles([]);
        }}
        footer={null}
        destroyOnHidden
        width={640}
      >
        {filesLoading ? (
          <div className='py-6 text-center text-neutral-400'>加载中…</div>
        ) : files.length === 0 ? (
          <div className='py-6 text-center text-neutral-400'>暂无附件</div>
        ) : (
          <ul className='m-0 list-none space-y-2 p-0'>
            {files.map((f) => (
              <li
                key={f.id}
                className='flex items-center justify-between gap-3 rounded border border-neutral-200 px-3 py-2'
              >
                <div className='min-w-0'>
                  <div className='truncate font-medium'>{f.fileName}</div>
                  <div className='text-xs text-neutral-400'>
                    {f.uploadUserName || '-'}
                    {f.createTime ? ` · ${f.createTime}` : ''}
                    {f.fileSize ? ` · ${Math.max(1, Math.round((f.fileSize || 0) / 1024))} KB` : ''}
                  </div>
                </div>
                <a
                  href={fileOpenUrl(f.fileUrl)}
                  target='_blank'
                  rel='noreferrer'
                  download={f.fileName}
                >
                  查看/下载
                </a>
              </li>
            ))}
          </ul>
        )}
      </Modal>
    </>
  );
});

export default TaskPanel;
