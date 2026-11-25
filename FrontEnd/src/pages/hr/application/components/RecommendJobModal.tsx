import { useEffect, useState } from 'react';
import { App, DatePicker, Drawer, Form, Select } from 'antd';
import dayjs from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { recommendHrTalentApi } from '@/api/hr';

type Option = { value: number | string; label: string };

export default function RecommendJobModal({
  open,
  candidateId,
  candidateName,
  jobs,
  channels,
  users,
  onClose,
  onSuccess,
}: {
  open: boolean;
  candidateId?: number;
  candidateName?: string;
  jobs: Option[];
  channels: Option[];
  users: Option[];
  onClose: () => void;
  onSuccess: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{
    requisitionId: number;
    channelCode?: string;
    submitterUserId?: number;
    submittedAt: dayjs.Dayjs;
  }>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      submittedAt: dayjs(),
      channelCode: undefined,
      submitterUserId: undefined,
      requisitionId: undefined,
    });
  }, [form, open]);

  return (
    <Drawer
      title={`推荐到岗位${candidateName ? ` · ${candidateName}` : ''}`}
      open={open}
      width={420}
      destroyOnHidden
      onClose={onClose}
      extra={
        <PermissionButton
          perm='hr:application:add'
          type='primary'
          loading={submitting}
          onClick={() => form.submit()}
        >
          确认推荐
        </PermissionButton>
      }
    >
      <Form
        form={form}
        layout='vertical'
        onFinish={async (values) => {
          if (!candidateId) return;
          setSubmitting(true);
          try {
            await recommendHrTalentApi({
              candidateId,
              requisitionId: values.requisitionId,
              channelCode: values.channelCode,
              submitterUserId: values.submitterUserId,
              submittedAt: values.submittedAt.format('YYYY-MM-DD'),
            });
            message.success('已推荐到岗位，从待初筛开始');
            onSuccess();
            onClose();
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item
          name='requisitionId'
          label='岗位'
          rules={[{ required: true, message: '请选择岗位' }]}
        >
          <Select
            options={jobs}
            showSearch
            optionFilterProp='label'
            placeholder='请选择招聘需求'
          />
        </Form.Item>
        <Form.Item
          name='channelCode'
          label='渠道'
        >
          <Select
            options={channels}
            allowClear
            placeholder='可选'
          />
        </Form.Item>
        <Form.Item
          name='submitterUserId'
          label='提交人'
        >
          <Select
            options={users}
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='可选'
          />
        </Form.Item>
        <Form.Item
          name='submittedAt'
          label='推荐日期'
          rules={[{ required: true, message: '请选择日期' }]}
        >
          <DatePicker className='w-full' />
        </Form.Item>
        <p className='text-sm text-black/45'>
          这个人已有未挂岗的记录时，会直接挂到所选岗位；否则新建一条待初筛投递，并带上最近一份简历。
        </p>
      </Form>
    </Drawer>
  );
}
