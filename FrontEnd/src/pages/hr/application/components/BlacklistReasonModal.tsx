import { useEffect, useState } from 'react';
import { Form, Input, Modal } from 'antd';

type Values = { reason: string; remark: string };

type Props = {
  open: boolean;
  title: string;
  okText: string;
  hint?: string;
  onClose: () => void;
  onSubmit: (values: Values) => Promise<void>;
};

export default function BlacklistReasonModal({ open, title, okText, hint, onClose, onSubmit }: Props) {
  const [form] = Form.useForm<Values>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) form.resetFields();
  }, [form, open]);

  return (
    <Modal
      title={title}
      open={open}
      okText={okText}
      cancelText='取消'
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      {hint ? <div className='mb-3 text-sm text-black/45'>{hint}</div> : null}
      <Form
        form={form}
        layout='vertical'
        onFinish={async (values) => {
          setSubmitting(true);
          try {
            await onSubmit({ reason: values.reason.trim(), remark: values.remark.trim() });
            onClose();
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item
          name='reason'
          label='原因'
          rules={[
            { required: true, whitespace: true, message: '请填写原因' },
            { max: 255, message: '原因不能超过255字' },
          ]}
        >
          <Input.TextArea
            rows={3}
            maxLength={255}
            showCount
            placeholder='请填写原因'
          />
        </Form.Item>
        <Form.Item
          name='remark'
          label='备注'
          rules={[
            { required: true, whitespace: true, message: '请填写备注' },
            { max: 500, message: '备注不能超过500字' },
          ]}
        >
          <Input.TextArea
            rows={3}
            maxLength={500}
            showCount
            placeholder='请填写备注'
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
