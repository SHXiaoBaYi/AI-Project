import { useEffect, useState } from 'react';
import { DatePicker, Form, Input, Modal, Select } from 'antd';
import dayjs from 'dayjs';
import { adjustHrApplicationStageApi } from '@/api/hr';

type StageOption = { value: string; label: string };

type Target = {
  id: number;
  displayName?: string;
  jobName?: string;
  currentStage?: string;
};

type Props = {
  open: boolean;
  target: Target | null;
  stages: StageOption[];
  onClose: () => void;
  onSuccess: () => void;
};

export default function StageAdjustModal({ open, target, stages, onClose, onSuccess }: Props) {
  const [form] = Form.useForm<{ stageCode: string; businessAt: dayjs.Dayjs; reason: string }>();
  const [submitting, setSubmitting] = useState(false);
  const stageCode = Form.useWatch('stageCode', form);
  const dateLabel = stageCode === 'ONBOARDED' ? '入职日' : '面试时间';

  useEffect(() => {
    if (!open || !target) return;
    form.setFieldsValue({
      stageCode: target.currentStage,
      businessAt: undefined,
      reason: undefined,
    });
  }, [form, open, target]);

  return (
    <Modal
      title={`调整阶段${target?.displayName ? ` · ${target.displayName}` : ''}`}
      open={open}
      okText='确认调整'
      cancelText='取消'
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout='vertical'
        onFinish={async (values) => {
          if (!target) return;
          setSubmitting(true);
          try {
            await adjustHrApplicationStageApi({
              applicationId: target.id,
              stageCode: values.stageCode,
              businessAt: values.businessAt.format('YYYY-MM-DD HH:mm:ss'),
              reason: values.reason.trim(),
            });
            onSuccess();
            onClose();
          } finally {
            setSubmitting(false);
          }
        }}
      >
        {target?.jobName ? <div className='mb-3 text-sm text-black/45'>岗位：{target.jobName}</div> : null}
        <Form.Item
          name='stageCode'
          label='调整到'
          rules={[{ required: true, message: '请选择阶段' }]}
        >
          <Select
            options={stages}
            showSearch
            optionFilterProp='label'
            placeholder='请选择阶段'
          />
        </Form.Item>
        <Form.Item
          name='businessAt'
          label={dateLabel}
          extra='已入职填入职日，面试阶段填实际面试时间。统计按这个时间计算。'
          rules={[{ required: true, message: `请填写${dateLabel}` }]}
        >
          <DatePicker
            showTime
            className='w-full'
            format='YYYY-MM-DD HH:mm'
          />
        </Form.Item>
        <Form.Item
          name='reason'
          label='调整原因'
          rules={[{ required: true, whitespace: true, message: '请填写调整原因' }]}
        >
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            placeholder='说明为什么要改阶段'
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
