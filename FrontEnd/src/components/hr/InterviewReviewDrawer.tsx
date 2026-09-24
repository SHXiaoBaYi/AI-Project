import { useCallback, useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import { App, Button, Drawer, Form, Input, Modal, Select, Space, Table } from 'antd';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { usePermission } from '@/hooks/usePermission';
import type { RootState } from '@/store';
import {
  getHrFailReasonOptionsApi,
  getHrUsersApi,
  listHrInterviewReviewsApi,
  saveHrInterviewRecordApi,
} from '@/api/hr';

const CONCLUSION_LABEL: Record<string, string> = {
  PASS: '通过',
  FAIL: '未通过',
  PENDING: '待定',
};

const CONCLUSIONS = [
  { label: '通过', value: 'PASS' },
  { label: '未通过', value: 'FAIL' },
  { label: '待定', value: 'PENDING' },
];

const ROUNDS = [
  { label: '一面', value: 1 },
  { label: '二面', value: 2 },
  { label: '三面', value: 3 },
  { label: '四面', value: 4 },
  { label: '五面', value: 5 },
];

type ReviewRow = {
  id?: number;
  applicationId?: number;
  interviewerUserId?: number;
  inviteId?: number;
  kind: string;
  roundNo: number;
  roundName?: string;
  interviewerName?: string;
  conclusion?: string;
  failReason?: string;
  comment?: string;
  interviewedAt?: string;
};

type EditState = { mode: 'add' } | { mode: 'edit'; row: ReviewRow };

export default function InterviewReviewDrawer({
  applicationId,
  candidateName,
  open,
  onClose,
}: {
  applicationId?: number;
  candidateName?: string;
  open: boolean;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const { has } = usePermission();
  const currentUserId = useSelector((state: RootState) => state.user.userInfo?.userId);
  const canManage = has('hr:record:add') || has('hr:record:edit');
  const canMine = has('hr:interview:mine');
  const canAdd = canManage || canMine;

  const [rows, setRows] = useState<ReviewRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [users, setUsers] = useState<{ label: string; value: number }[]>([]);
  const [failReasons, setFailReasons] = useState<{ label: string; value: string }[]>([]);
  const [editing, setEditing] = useState<EditState | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const mineOnly = canMine && !canManage && !has('hr:record:edit');

  const canEditRow = (row: ReviewRow) => {
    if (row.kind !== 'INTERVIEW' || !row.id) return false;
    if (has('hr:record:edit')) return true;
    if (canMine && currentUserId != null && row.interviewerUserId === currentUserId) return true;
    return false;
  };

  const reload = useCallback(async () => {
    if (!applicationId) return;
    setLoading(true);
    try {
      const list = await listHrInterviewReviewsApi(applicationId);
      setRows(list || []);
    } finally {
      setLoading(false);
    }
  }, [applicationId]);

  useEffect(() => {
    if (!open || !applicationId) return;
    void reload();
  }, [open, applicationId, reload]);

  useEffect(() => {
    if (!open) return;
    void getHrUsersApi('interviewer').then((list) => {
      setUsers(
        (list || []).map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      );
    });
    void getHrFailReasonOptionsApi().then((list) => {
      setFailReasons((list ?? []).map((item) => ({ value: item.name, label: item.name })));
    });
  }, [open]);

  useEffect(() => {
    if (!editing) {
      form.resetFields();
      return;
    }
    if (editing.mode === 'edit') {
      form.setFieldsValue({
        roundNo: editing.row.roundNo,
        interviewerUserId: editing.row.interviewerUserId,
        conclusion: editing.row.conclusion,
        failReason: editing.row.failReason,
        comment: editing.row.comment,
      });
      return;
    }
    form.setFieldsValue({
      roundNo: 1,
      interviewerUserId: mineOnly ? currentUserId : undefined,
      conclusion: undefined,
      failReason: undefined,
      comment: undefined,
    });
  }, [editing, form, mineOnly, currentUserId]);

  return (
    <Drawer
      title={candidateName ? `${candidateName}的面试评价` : '面试评价'}
      open={open}
      size='large'
      onClose={onClose}
      extra={
        canAdd ? (
          <Button
            type='primary'
            size='small'
            onClick={() => setEditing({ mode: 'add' })}
          >
            新增评价
          </Button>
        ) : null
      }
    >
      <Table
        rowKey={(row) =>
          row.kind === 'INTERVIEW' && row.id
            ? `INTERVIEW-${row.id}`
            : `${row.kind}-${row.roundNo}-${row.interviewerName}-${row.interviewedAt}`
        }
        loading={loading}
        dataSource={rows}
        pagination={false}
        scroll={{ x: 900 }}
        locale={{ emptyText: '还没有面试评价' }}
        columns={[
          {
            title: '操作',
            key: 'option',
            width: 88,
            fixed: 'left',
            render: (_, row) =>
              canEditRow(row) ? (
                <ActionButtons
                  maxVisible={2}
                  items={[
                    {
                      key: 'edit',
                      label: '修改',
                      onClick: () => setEditing({ mode: 'edit', row }),
                    },
                  ]}
                />
              ) : (
                <span className='text-neutral-300'>—</span>
              ),
          },
          { title: '轮次', dataIndex: 'roundName', width: 80 },
          {
            title: '类型',
            dataIndex: 'kind',
            width: 110,
            render: (value: string) => (value === 'JOINT' ? '联合评价' : '面试官评价'),
          },
          {
            title: '评价人',
            dataIndex: 'interviewerName',
            width: 120,
            render: (value: string) => value || '—',
          },
          {
            title: '结论',
            dataIndex: 'conclusion',
            width: 90,
            render: (value: string) => CONCLUSION_LABEL[value] || value || '—',
          },
          {
            title: '未通过原因',
            dataIndex: 'failReason',
            width: 140,
            render: (value: string, row) => (row.conclusion === 'FAIL' ? value || '—' : '—'),
          },
          {
            title: '评语',
            dataIndex: 'comment',
            ellipsis: true,
            render: (value: string) => value || '—',
          },
          {
            title: '时间',
            dataIndex: 'interviewedAt',
            width: 170,
            render: (value: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—'),
          },
        ]}
      />

      <Modal
        title={editing?.mode === 'edit' ? '修改面试评价' : '新增面试评价'}
        open={!!editing}
        confirmLoading={saving}
        okText='保存'
        destroyOnHidden
        onCancel={() => setEditing(null)}
        onOk={async () => {
          if (!applicationId || !editing) return;
          const values = await form.validateFields();
          setSaving(true);
          try {
            const payload: Record<string, unknown> = {
              applicationId,
              roundNo: values.roundNo,
              interviewerUserId: values.interviewerUserId,
              conclusion: values.conclusion,
              failReason: values.conclusion === 'FAIL' ? values.failReason : undefined,
              comment: values.comment,
            };
            if (editing.mode === 'edit') {
              payload.id = editing.row.id;
              payload.inviteId = editing.row.inviteId;
              if (editing.row.interviewedAt) {
                payload.interviewedAt = String(editing.row.interviewedAt).replace('T', ' ').slice(0, 19);
              }
            }
            const msg = await saveHrInterviewRecordApi(payload);
            message.success(msg || '已保存');
            setEditing(null);
            await reload();
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form
          form={form}
          layout='vertical'
          className='mt-2'
        >
          <Space
            direction='vertical'
            className='w-full'
            size='middle'
          >
            <Form.Item
              name='roundNo'
              label='轮次'
              rules={[{ required: true, message: '请选择轮次' }]}
            >
              <Select
                options={ROUNDS}
                disabled={editing?.mode === 'edit'}
                placeholder='请选择轮次'
              />
            </Form.Item>
            <Form.Item
              name='interviewerUserId'
              label='面试官'
              rules={[{ required: true, message: '请选择面试官' }]}
            >
              <Select
                options={users}
                disabled={editing?.mode === 'edit' || mineOnly}
                showSearch
                optionFilterProp='label'
                placeholder='请选择面试官'
              />
            </Form.Item>
            <Form.Item
              name='conclusion'
              label='结论'
              rules={[{ required: true, message: '请选择结论' }]}
            >
              <Select
                options={CONCLUSIONS}
                placeholder='请选择结论'
                onChange={(value) => {
                  if (value !== 'FAIL') form.setFieldValue('failReason', undefined);
                }}
              />
            </Form.Item>
            <Form.Item
              noStyle
              shouldUpdate={(prev, next) => prev.conclusion !== next.conclusion}
            >
              {() =>
                form.getFieldValue('conclusion') === 'FAIL' ? (
                  <Form.Item
                    name='failReason'
                    label='未通过原因'
                    rules={[{ required: true, message: '未通过必须选择原因' }]}
                  >
                    <Select
                      options={failReasons}
                      placeholder={failReasons.length ? '请选择原因' : '请先在基础数据维护未通过原因'}
                      showSearch
                      optionFilterProp='label'
                    />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
            <Form.Item
              name='comment'
              label='评语'
            >
              <Input.TextArea
                rows={4}
                maxLength={1000}
                placeholder='填写面试评价'
              />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Drawer>
  );
}
