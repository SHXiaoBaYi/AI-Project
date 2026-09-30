import { useEffect, useState } from 'react';
import { App, DatePicker, Form, Input, InputNumber, Modal, Switch } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { createDingTalkAssistantMeetingApi } from '@/api/dingtalk';
import RecommendSlotRadio, { type RecommendSlotOption } from '@/components/dingtalk/RecommendSlotRadio';
import {
  BOOKING_MAX_DAYS,
  bookingDateTimeError,
  bookingPastDisabledTime,
  disabledBookingDate,
} from '@/utils/chinaHoliday';

export type MeetingFormSeed = {
  title?: string;
  startTime?: string | Dayjs;
  durationMin?: number;
  location?: string;
  description?: string;
  onlineMeeting?: boolean;
};

type Props = {
  open: boolean;
  targetUserId?: number;
  /** 多人开会：含首位在内的全部参会人 */
  targetUserIds?: number[] | null;
  targetNickname?: string;
  seed?: MeetingFormSeed | null;
  /** 有推荐时段时用单选，不再展示日期组件 */
  slotOptions?: RecommendSlotOption[] | null;
  onOpenChange: (open: boolean) => void;
  onSuccess?: (message: string) => void;
};

export default function MeetingFormModal({
  open,
  targetUserId,
  targetUserIds,
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
  const allTargetIds = (targetUserIds?.length ? targetUserIds : targetUserId ? [targetUserId] : []).filter(
    (id): id is number => typeof id === 'number' && Number.isFinite(id),
  );

  useEffect(() => {
    if (!open) return;
    const nickname = targetNickname || '';
    const first = useSlots ? slotOptions![0] : null;
    form.setFieldsValue({
      title: seed?.title || (nickname ? `与${nickname}的会议` : '会议'),
      startTime: first
        ? first.start
        : seed?.startTime
          ? dayjs(seed.startTime)
          : dayjs().add(1, 'hour').minute(0).second(0),
      durationMin: first?.durationMin || seed?.durationMin || 60,
      location: seed?.location || '',
      description: seed?.description || '',
      onlineMeeting: seed?.onlineMeeting ?? true,
    });
  }, [open, seed, targetNickname, form, useSlots, slotOptions]);

  const submit = async () => {
    if (!allTargetIds.length) {
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
      const result = await createDingTalkAssistantMeetingApi({
        targetUserId: allTargetIds[0],
        attendeeUserIds: allTargetIds.slice(1),
        title: values.title,
        startTime: start.second(0).format('YYYY-MM-DD HH:mm:ss'),
        durationMin: values.durationMin,
        location: values.location,
        description: values.description,
        onlineMeeting: !!values.onlineMeeting,
      });
      const tip = result.message || '会议已创建';
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
      title={targetNickname ? `邀请「${targetNickname}」开会` : '邀请开会'}
      open={open}
      width={520}
      maskClosable={false}
      destroyOnHidden
      confirmLoading={saving}
      okText='创建会议日程'
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
          rules={[{ required: true, message: '请填写会议主题' }]}
        >
          <Input placeholder='与钉钉日程「主题」一致' />
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
          <Input placeholder='会议室 / 线上地址' />
        </Form.Item>
        <Form.Item
          name='description'
          label='描述'
        >
          <Input.TextArea
            rows={2}
            placeholder='会议说明'
          />
        </Form.Item>
        <Form.Item
          name='onlineMeeting'
          label='钉钉视频会议'
          valuePropName='checked'
        >
          <Switch
            checkedChildren='开'
            unCheckedChildren='关'
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
