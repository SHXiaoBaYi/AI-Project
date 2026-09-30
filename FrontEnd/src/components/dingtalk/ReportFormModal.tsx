import { useEffect, useState } from 'react';
import { App, DatePicker, Form, Input, InputNumber, Modal } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { createDingTalkAssistantReportApi } from '@/api/dingtalk';
import RecommendSlotRadio, { type RecommendSlotOption } from '@/components/dingtalk/RecommendSlotRadio';
import {
  BOOKING_MAX_DAYS,
  bookingDateTimeError,
  bookingPastDisabledTime,
  disabledBookingDate,
} from '@/utils/chinaHoliday';

export type ReportFormSeed = {
  title?: string;
  startTime?: string | Dayjs;
  durationMin?: number;
  location?: string;
  content?: string;
};

type Props = {
  open: boolean;
  targetUserId?: number;
  targetNickname?: string;
  seed?: ReportFormSeed | null;
  /** 有推荐时段时用单选，不再展示日期组件 */
  slotOptions?: RecommendSlotOption[] | null;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (message: string) => void;
};

export default function ReportFormModal({
  open,
  targetUserId,
  targetNickname,
  seed,
  slotOptions,
  onOpenChange,
  onSuccess,
}: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const useSlots = (slotOptions?.length || 0) > 0;

  useEffect(() => {
    if (!open) return;
    const nickname = targetNickname || '';
    const first = useSlots ? slotOptions![0] : null;
    form.setFieldsValue({
      title: seed?.title || (nickname ? `工作汇报 · ${nickname}` : '工作汇报'),
      startTime: first
        ? first.start
        : seed?.startTime
          ? dayjs(seed.startTime)
          : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: first?.durationMin || seed?.durationMin || 60,
      location: seed?.location || '',
      content: seed?.content || '',
    });
  }, [open, seed, targetNickname, form, useSlots, slotOptions]);

  const submit = async () => {
    if (!targetUserId) {
      message.warning('缺少同事信息');
      return;
    }
    const values = await form.validateFields();
    const start = dayjs(values.startTime);
    if (!useSlots) {
      const startErr = bookingDateTimeError(start);
      if (startErr) {
        message.warning(startErr);
        return;
      }
    }
    setSaving(true);
    try {
      const result = await createDingTalkAssistantReportApi({
        targetUserId,
        title: values.title,
        content: values.content,
        location: values.location,
        startTime: start.second(0).format('YYYY-MM-DD HH:mm:ss'),
        durationMin: values.durationMin,
      });
      const tip = result.message || '汇报已安排';
      message.success(tip);
      onOpenChange(false);
      form.resetFields();
      onSuccess?.(tip);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={targetNickname ? `与「${targetNickname}」汇报工作` : '汇报工作'}
      open={open}
      width={520}
      maskClosable={false}
      destroyOnHidden
      confirmLoading={saving}
      okText='确认安排'
      cancelText='取消'
      onOk={() => void submit()}
      onCancel={() => {
        if (saving) return;
        onOpenChange(false);
        form.resetFields();
      }}
    >
      <Form
        form={form}
        layout='vertical'
        className='pt-2'
      >
        <Form.Item
          name='title'
          label='主题'
        >
          <Input placeholder='工作汇报标题' />
        </Form.Item>
        {useSlots ? (
          <>
            <Form.Item
              name='startTime'
              label='推荐时段'
              extra='来自机器人推荐，默认第一项'
              rules={[{ required: true, message: '请选择时段' }]}
            >
              <RecommendSlotRadio
                options={slotOptions!}
                onDurationChange={(durationMin) => form.setFieldsValue({ durationMin })}
              />
            </Form.Item>
            <Form.Item
              name='durationMin'
              hidden
            >
              <InputNumber />
            </Form.Item>
          </>
        ) : (
          <>
            <Form.Item
              name='startTime'
              label='开始时间'
              extra={`不可选过去、法定节假日，最多未来 ${BOOKING_MAX_DAYS} 天`}
              rules={[
                { required: true, message: '请选择开始时间' },
                {
                  validator: async (_, value) => {
                    const err = bookingDateTimeError(value);
                    if (err) return Promise.reject(new Error(err));
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <DatePicker
                showTime={{ minuteStep: 15, format: 'HH:mm', showSecond: false, hideDisabledOptions: true }}
                className='w-full'
                format='YYYY-MM-DD HH:mm'
                disabledDate={disabledBookingDate}
                disabledTime={(date) => bookingPastDisabledTime(date)}
              />
            </Form.Item>
            <Form.Item
              name='durationMin'
              label='时长（分钟）'
              rules={[{ required: true, message: '请填写时长' }]}
            >
              <InputNumber
                className='w-full'
                min={15}
                max={240}
                step={15}
              />
            </Form.Item>
          </>
        )}
        <Form.Item
          name='location'
          label='地点'
        >
          <Input placeholder='可选' />
        </Form.Item>
        <Form.Item
          name='content'
          label='说明'
        >
          <Input.TextArea
            rows={2}
            placeholder='汇报要点'
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
