import { memo, useCallback, useEffect, useState } from 'react';
import { App, DatePicker, Drawer, Form, Input, InputNumber, Modal, Radio, Tag } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import {
  deleteHrRequisitionMonthTaskApi,
  listHrRequisitionMonthTasksApi,
  saveHrRequisitionMonthTaskApi,
  type HrRequisitionMonthTask,
  type HrRequisitionLogEntrySource,
} from '@/api/hr';

export type HrMonthTaskTarget = {
  id: number;
  jobName?: string;
};

type Props = {
  open: boolean;
  onClose: () => void;
  target: HrMonthTaskTarget | null;
  /** 操作入口：写入操作日志 */
  entrySource: HrRequisitionLogEntrySource;
};

const HrRequisitionMonthTaskDrawer = memo(function HrRequisitionMonthTaskDrawer({
  open,
  onClose,
  target,
  entrySource,
}: Props) {
  const { message } = App.useApp();
  const [monthTasks, setMonthTasks] = useState<HrRequisitionMonthTask[]>([]);
  const [taskLoading, setTaskLoading] = useState(false);
  const [taskSaving, setTaskSaving] = useState(false);
  const [taskForm] = Form.useForm<{
    settleMonth?: dayjs.Dayjs;
    priorityCode?: string;
    targetCount?: number;
    resumeTarget?: number;
    score?: number;
    remark?: string;
  }>();
  const taskPriority = Form.useWatch('priorityCode', taskForm);
  const taskOnboard = Form.useWatch('targetCount', taskForm);
  const taskResume = Form.useWatch('resumeTarget', taskForm);
  const taskScore = Form.useWatch('score', taskForm);

  const fillMonthTaskForm = useCallback(
    (rows: HrRequisitionMonthTask[], month = dayjs().format('YYYY-MM')) => {
      const hit = rows.find((t) => t.settleMonth === month);
      taskForm.setFieldsValue({
        settleMonth: dayjs(`${month}-01`).startOf('month'),
        priorityCode: hit?.priorityCode,
        targetCount: hit != null ? hit.targetCount : undefined,
        resumeTarget: hit != null ? hit.resumeTarget : undefined,
        score: hit != null ? hit.score : undefined,
        remark: hit?.remark,
      });
    },
    [taskForm],
  );

  const reloadMonthTasks = useCallback(
    async (requisitionId: number, preferMonth?: string) => {
      setTaskLoading(true);
      try {
        const rows = await listHrRequisitionMonthTasksApi(requisitionId);
        const list = rows || [];
        setMonthTasks(list);
        fillMonthTaskForm(list, preferMonth || dayjs().format('YYYY-MM'));
        return list;
      } finally {
        setTaskLoading(false);
      }
    },
    [fillMonthTaskForm],
  );

  useEffect(() => {
    if (!open || !target?.id) return;
    taskForm.resetFields();
    const month = dayjs().format('YYYY-MM');
    taskForm.setFieldsValue({ settleMonth: dayjs().startOf('month') });
    void reloadMonthTasks(target.id, month);
  }, [open, target?.id, reloadMonthTasks, taskForm]);

  return (
    <Drawer
      title={target ? `月度任务要求 · ${target.jobName || ''}` : '月度任务要求'}
      width={720}
      open={open}
      onClose={() => {
        onClose();
        setMonthTasks([]);
      }}
      destroyOnClose
      styles={{ body: { paddingTop: 12 } }}
    >
      <Form
        form={taskForm}
        layout='vertical'
        requiredMark='optional'
        className='mb-6 !border-0 !shadow-none'
        onFinish={async (values) => {
          if (!target) return;
          const priority = values.priorityCode;
          const month = values.settleMonth!.format('YYYY-MM');
          const onboard = Number(values.targetCount || 0);
          const resume = priority === 'P1' ? Number(values.resumeTarget || 0) : 0;
          if (priority === 'P0' && onboard < 1) {
            message.warning('必须到岗请填写入职人数（至少 1）');
            return;
          }
          if (priority === 'P1' && onboard < 1 && resume < 1) {
            message.warning('灵活到岗请至少填写入职人数或合格简历份数之一');
            return;
          }
          const existed = monthTasks.find((t) => t.settleMonth === month);
          if (existed) {
            const ok = await new Promise<boolean>((resolve) => {
              Modal.confirm({
                title: `覆盖 ${month} 的任务要求？`,
                content: '同一岗位同一月份只能有一条配置，保存将覆盖已有设置。',
                okText: '覆盖保存',
                cancelText: '取消',
                onOk: () => resolve(true),
                onCancel: () => resolve(false),
              });
            });
            if (!ok) return;
          }
          setTaskSaving(true);
          try {
            await saveHrRequisitionMonthTaskApi({
              requisitionId: target.id,
              settleMonth: month,
              priorityCode: priority!,
              targetCount: onboard,
              resumeTarget: resume,
              score: Number(values.score),
              remark: values.remark,
              entrySource,
            });
            message.success(existed ? `已覆盖 ${month} 的任务要求` : '已保存月度任务要求');
            await reloadMonthTasks(target.id, month);
          } finally {
            setTaskSaving(false);
          }
        }}
      >
        <p className='mb-4 text-xs leading-relaxed text-neutral-400'>
          同一岗位同一月份仅一条配置；再次保存将覆盖。必须到岗按入职计分；灵活到岗为入职或合格简历「或」关系，任一达标即得分（每月只记一次）。
        </p>

        <div className='mb-1 grid grid-cols-2 gap-x-4'>
          <Form.Item
            name='settleMonth'
            label='月份'
            rules={[{ required: true, message: '请选择月份' }]}
          >
            <DatePicker
              picker='month'
              className='w-full'
              allowClear={false}
              onChange={(v) => {
                if (!v) return;
                fillMonthTaskForm(monthTasks, v.format('YYYY-MM'));
              }}
            />
          </Form.Item>
          <Form.Item
            name='priorityCode'
            label='到岗类型'
            rules={[{ required: true, message: '请选择到岗类型' }]}
          >
            <Radio.Group
              optionType='button'
              buttonStyle='solid'
              className='flex w-full [&_.ant-radio-button-wrapper]:flex-1 [&_.ant-radio-button-wrapper]:text-center'
              options={[
                { label: '必须到岗', value: 'P0' },
                { label: '灵活到岗', value: 'P1' },
              ]}
              onChange={(e) => {
                if (e.target.value === 'P0') {
                  taskForm.setFieldValue('resumeTarget', undefined);
                }
              }}
            />
          </Form.Item>
        </div>

        <div className='mb-4 rounded-md bg-sky-50/70 px-3 py-2.5 text-sm text-sky-900'>
          {taskPriority === 'P1' ? (
            <>
              灵活到岗：完成 <span className='font-medium'>{taskOnboard ?? '—'}</span> 人入职
              <span className='mx-1 text-sky-600'>或</span>
              <span className='font-medium'>{taskResume ?? '—'}</span> 份合格简历
              <span className='mx-1'>→</span>
              <span className='font-medium'>{taskScore ?? '—'}</span> 分
            </>
          ) : (
            <>
              必须到岗：完成 <span className='font-medium'>{taskOnboard ?? '—'}</span> 人入职
              <span className='mx-1'>→</span>
              <span className='font-medium'>{taskScore ?? '—'}</span> 分
            </>
          )}
        </div>

        <div className={`mb-1 grid gap-x-4 ${taskPriority === 'P1' ? 'grid-cols-3' : 'grid-cols-2'}`}>
          <Form.Item
            name='targetCount'
            label='入职人数'
            rules={taskPriority === 'P0' ? [{ required: true, message: '请填写入职人数' }] : undefined}
          >
            <InputNumber
              min={0}
              max={999}
              precision={0}
              className='w-full'
              suffix='人'
            />
          </Form.Item>
          {taskPriority === 'P1' ? (
            <Form.Item
              name='resumeTarget'
              label='合格简历'
            >
              <InputNumber
                min={0}
                max={999}
                precision={0}
                className='w-full'
                suffix='份'
              />
            </Form.Item>
          ) : null}
          <Form.Item
            name='score'
            label='得分'
            rules={[{ required: true, message: '请填写得分' }]}
          >
            <InputNumber
              min={0}
              max={999}
              precision={0}
              className='w-full'
              suffix='分'
            />
          </Form.Item>
        </div>

        <Form.Item
          name='remark'
          label='备注'
        >
          <Input.TextArea
            rows={2}
            maxLength={255}
            showCount
            placeholder='可选'
            className='!resize-none'
          />
        </Form.Item>

        <PermissionButton
          type='primary'
          htmlType='submit'
          perm='hr:requisition:task'
          loading={taskSaving}
        >
          保存本月配置
        </PermissionButton>
      </Form>

      <div className='mb-2 text-sm font-medium text-neutral-700'>已配置月份</div>
      <BaseProTable<HrRequisitionMonthTask>
        rowKey='id'
        search={false}
        options={false}
        pagination={false}
        loading={taskLoading}
        dataSource={monthTasks}
        cardProps={{ bodyStyle: { padding: 0 } }}
        className='[&_.ant-pro-card]:!border-0 [&_.ant-pro-card]:shadow-none'
        columns={[
          {
            title: '操作',
            valueType: 'option',
            width: 140,
            render: (_, row) => (
              <ActionButtons
                items={[
                  {
                    key: 'edit',
                    label: '载入编辑',
                    perm: 'hr:requisition:task',
                    onClick: () => {
                      taskForm.setFieldsValue({
                        settleMonth: dayjs(`${row.settleMonth}-01`),
                        priorityCode: row.priorityCode,
                        targetCount: row.targetCount ?? 0,
                        resumeTarget: row.resumeTarget ?? 0,
                        score: row.score,
                        remark: row.remark,
                      });
                    },
                  },
                  {
                    key: 'delete',
                    label: '删除',
                    perm: 'hr:requisition:task',
                    confirmTitle: `确认删除 ${row.settleMonth} 的任务要求？`,
                    onClick: async () => {
                      if (!row.id || !target) return;
                      await deleteHrRequisitionMonthTaskApi(row.id, entrySource);
                      message.success('已删除');
                      await reloadMonthTasks(target.id);
                    },
                  },
                ]}
              />
            ),
          },
          { title: '月份', dataIndex: 'settleMonth', width: 100 },
          {
            title: '到岗类型',
            dataIndex: 'priorityCode',
            width: 100,
            render: (_, r) => (
              <Tag color={r.priorityCode === 'P0' ? 'red' : 'blue'}>
                {r.priorityCode === 'P0' ? '必须到岗' : '灵活到岗'}
              </Tag>
            ),
          },
          {
            title: '规则',
            ellipsis: true,
            render: (_, r) => r.achieveModeLabel || `${r.priorityCode} → ${r.score}分`,
          },
          { title: '更新人', dataIndex: 'updateBy', width: 90, ellipsis: true },
          { title: '更新时间', dataIndex: 'updateTime', width: 160, ellipsis: true },
        ]}
      />
    </Drawer>
  );
});

export default HrRequisitionMonthTaskDrawer;
