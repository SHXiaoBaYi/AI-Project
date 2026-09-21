import { memo, useEffect, useState } from 'react';
import { App, Form, Input, Modal, Select, Table } from 'antd';
import dayjs from 'dayjs';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import {
  addHrInviteInterviewerApi,
  forwardHrInviteApi,
  getHrUsersApi,
  listMyHrInvitesApi,
  saveHrInterviewRecordApi,
  deleteHrInterviewOwnApi,
} from '@/api/hr';

const ROUNDS = [
  { value: 1, label: '一面' },
  { value: 2, label: '二面' },
  { value: 3, label: '三面' },
  { value: 4, label: '四面' },
  { value: 5, label: '五面' },
];

const CONCLUSIONS = [
  { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '未通过' },
  { value: 'PENDING', label: '待定' },
];

function interviewEnded(value: unknown) {
  if (value == null || value === '') return false;
  return !dayjs(String(value).replace('T', ' ')).isAfter(dayjs());
}

const MinePage = memo(function MinePage() {
  const { message } = App.useApp();
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [reviewing, setReviewing] = useState<Record<string, unknown> | null>(null);
  const [transfer, setTransfer] = useState<{ row: Record<string, unknown>; mode: 'forward' | 'add' } | null>(null);
  const [target, setTarget] = useState<number>();
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const load = () => listMyHrInvitesApi().then(setRows);

  useEffect(() => {
    load();
    getHrUsersApi('interviewer').then((list) =>
      setUsers(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      ),
    );
  }, []);

  return (
    <>
      <div className='rounded bg-white p-4'>
        <h2 className='mb-1 text-base font-medium'>我的面试</h2>
        <p className='mb-3 text-sm text-neutral-500'>
          这里是分配给你、候选人还没进入下一阶段的面试。面试时间到了之后可以填写评价；进入下一阶段之前，可以修改或删除自己的评价。已填写评价的不能再转发或添加面试官。
        </p>
        <Table
          rowKey='id'
          dataSource={rows}
          pagination={false}
          scroll={{ x: 'max-content' }}
          columns={[
            {
              title: '操作',
              fixed: 'left',
              width: 260,
              render: (_, row) => {
                const ended = interviewEnded(row.interview_at);
                const reviewed = Number(row.reviewed) > 0;
                return (
                  <ActionButtons
                    maxVisible={4}
                    items={[
                      reviewed
                        ? {
                            key: 'edit-review',
                            label: '修改评价',
                            perm: 'hr:interview:mine',
                            onClick: () => {
                              form.setFieldsValue({
                                conclusion: row.my_conclusion,
                                comment: row.my_comment || '',
                              });
                              setReviewing(row);
                            },
                          }
                        : {
                            key: 'review',
                            label: '评价',
                            perm: 'hr:interview:mine',
                            disabled: !ended,
                            onClick: () => {
                              form.resetFields();
                              setReviewing(row);
                            },
                          },
                      ...(reviewed
                        ? [
                            {
                              key: 'delete-review',
                              label: '删除评价',
                              perm: 'hr:interview:mine',
                              confirmTitle: '确认删除你对这场面试的评价？',
                              onClick: async () => {
                                await deleteHrInterviewOwnApi(Number(row.record_id));
                                message.success('已删除评价');
                                load();
                              },
                            },
                          ]
                        : [
                            {
                              key: 'forward',
                              label: '转发',
                              perm: 'hr:interview:mine',
                              onClick: () => {
                                setTarget(undefined);
                                setTransfer({ row, mode: 'forward' });
                              },
                            },
                            {
                              key: 'add',
                              label: '添加面试官',
                              perm: 'hr:interview:mine',
                              onClick: () => {
                                setTarget(undefined);
                                setTransfer({ row, mode: 'add' });
                              },
                            },
                          ]),
                    ]}
                  />
                );
              },
            },
            { title: '候选人', dataIndex: 'display_name' },
            {
              title: '简历',
              render: (_, row) => (
                <ResumeViewButton
                  applicationId={Number(row.application_id)}
                  fileName={row.file_name ? String(row.file_name) : undefined}
                />
              ),
            },
            { title: '岗位', dataIndex: 'job_name' },
            {
              title: '轮次',
              dataIndex: 'round_no',
              render: (value: number) => ROUNDS.find((item) => item.value === Number(value))?.label || value,
            },
            {
              title: '时间',
              dataIndex: 'interview_at',
              render: (value: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—'),
            },
            { title: '地点', dataIndex: 'location' },
            {
              title: '我的结论',
              dataIndex: 'my_conclusion',
              render: (value: string) => CONCLUSIONS.find((item) => item.value === value)?.label || '—',
            },
            {
              title: '我的评语',
              dataIndex: 'my_comment',
              ellipsis: true,
              render: (value: string) => value || '—',
            },
          ]}
        />
      </div>
      <Modal
        title={
          reviewing ? `${Number(reviewing.reviewed) > 0 ? '修改评价' : '评价'}：${reviewing.display_name}` : '评价'
        }
        open={!!reviewing}
        confirmLoading={saving}
        okText={reviewing && Number(reviewing.reviewed) > 0 ? '保存修改' : '提交评价'}
        onCancel={() => setReviewing(null)}
        onOk={async () => {
          const values = await form.validateFields();
          if (!reviewing) return;
          setSaving(true);
          try {
            const msg = await saveHrInterviewRecordApi({
              applicationId: reviewing.application_id,
              requisitionId: reviewing.requisition_id,
              inviteId: reviewing.id,
              roundNo: reviewing.round_no,
              interviewerUserId: reviewing.interviewer_user_id,
              ...(reviewing.record_id ? { id: reviewing.record_id } : {}),
              conclusion: values.conclusion,
              comment: values.comment,
              interviewedAt: reviewing.interview_at
                ? String(reviewing.interview_at).replace('T', ' ').slice(0, 19)
                : undefined,
            });
            message.success(msg || '已保存');
            setReviewing(null);
            load();
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
            name='conclusion'
            label='结论'
            rules={[{ required: true, message: '请选择结论' }]}
          >
            <Select
              options={CONCLUSIONS}
              placeholder='请选择结论'
            />
          </Form.Item>
          <Form.Item
            name='comment'
            label='评语'
          >
            <Input.TextArea
              rows={4}
              placeholder='填写面试评价'
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={transfer?.mode === 'add' ? '添加面试官' : '转发面试'}
        open={!!transfer}
        okText='确定'
        onCancel={() => setTransfer(null)}
        onOk={async () => {
          if (!transfer) return;
          if (!target) {
            message.warning('请先选择面试官');
            return;
          }
          const saved =
            transfer.mode === 'add'
              ? await addHrInviteInterviewerApi(Number(transfer.row.id), target)
              : await forwardHrInviteApi(Number(transfer.row.id), target);
          if (saved?.warning) message.warning(saved.warning, 8);
          else message.success(transfer.mode === 'add' ? '已添加面试官并建立日程' : '已转发，原日程已取消');
          setTransfer(null);
          load();
        }}
      >
        <Select
          className='w-full'
          placeholder='选择面试官'
          options={users}
          showSearch
          optionFilterProp='label'
          value={target}
          onChange={setTarget}
        />
      </Modal>
    </>
  );
});

export default MinePage;
