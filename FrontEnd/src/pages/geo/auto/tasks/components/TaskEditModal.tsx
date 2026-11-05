import { useEffect, useMemo, useState } from 'react';
import { App, Form, Input, Modal, Radio, Select, Switch, TimePicker, Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getGeoTopicOptionsApi } from '@/api/geo';
import {
  createGeoAutoTaskApi,
  getGeoAutoPlatformOptionsApi,
  getGeoAutoQuestionOptionsApi,
  updateGeoAutoTaskApi,
} from '@/api/geoAuto';
import type { GeoAutoPlatformOption, GeoAutoQuestionOption, GeoAutoTask, GeoAutoTaskDTO } from '@/types/geoAuto';
import { GEO_AUTO_SCOPE_TYPES, GEO_AUTO_WEEK_DAYS } from '@/constants/geoAuto';

interface TaskEditModalProps {
  open: boolean;
  editing: GeoAutoTask | null;
  onClose: () => void;
  onSaved: () => void;
}

/** 监测任务编辑：圈选话题/问题 × 平台 × 频率 */
export default function TaskEditModal({ open, editing, onClose, onSaved }: TaskEditModalProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [platforms, setPlatforms] = useState<GeoAutoPlatformOption[]>([]);
  const [topics, setTopics] = useState<{ label: string; value: number }[]>([]);
  const [questions, setQuestions] = useState<GeoAutoQuestionOption[]>([]);
  const [submitting, setSubmitting] = useState(false);

  const scopeType = Form.useWatch('scopeType', form) || 'TOPIC';
  const scopeIds: number[] = Form.useWatch('scopeIds', form) || [];
  const platformNames: string[] = Form.useWatch('platforms', form) || [];
  const freq = Form.useWatch('freq', form) || '每日';

  useEffect(() => {
    if (!open) {
      return;
    }
    getGeoAutoPlatformOptionsApi()
      .then(setPlatforms)
      .catch(() => setPlatforms([]));
    getGeoTopicOptionsApi()
      .then((list) => setTopics(list.map((t) => ({ label: t.topicName, value: t.id }))))
      .catch(() => setTopics([]));
    getGeoAutoQuestionOptionsApi()
      .then(setQuestions)
      .catch(() => setQuestions([]));
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (editing) {
      form.setFieldsValue({
        taskName: editing.taskName,
        scopeType: editing.scopeType || 'TOPIC',
        scopeIds: editing.scopeIdList ?? [],
        platforms: editing.platformList ?? [],
        freq: editing.freq || '每日',
        weekDay: editing.weekDay ?? 1,
        runTime: editing.runTime ? dayjs(editing.runTime, 'HH:mm:ss') : dayjs('08:30:00', 'HH:mm:ss'),
        overwriteManual: editing.overwriteManual === 1,
        notifyEnabled: editing.notifyEnabled !== 0,
        remark: editing.remark || '',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        scopeType: 'TOPIC',
        freq: '每日',
        weekDay: 1,
        runTime: dayjs('08:30:00', 'HH:mm:ss'),
        overwriteManual: false,
        notifyEnabled: true,
      });
    }
  }, [open, editing, form]);

  // 切换圈选方式时清空已选，避免混用
  useEffect(() => {
    if (open) {
      form.setFieldValue('scopeIds', []);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeType]);

  const questionCount = useMemo(() => {
    if (scopeType === 'QUESTION') {
      return scopeIds.length;
    }
    return questions.filter((q) => scopeIds.includes(q.topicId)).length;
  }, [scopeType, scopeIds, questions]);

  const estimatedDailyCalls = useMemo(() => {
    const factor = freq === '每周' ? 1 / 7 : 1;
    return Math.round(questionCount * platformNames.length * factor);
  }, [questionCount, platformNames.length, freq]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: GeoAutoTaskDTO = {
      id: editing?.id,
      taskName: values.taskName.trim(),
      scopeType: values.scopeType,
      scopeIds: values.scopeIds,
      platforms: values.platforms,
      freq: values.freq,
      weekDay: values.freq === '每周' ? values.weekDay : undefined,
      runTime: values.runTime ? values.runTime.format('HH:mm:ss') : undefined,
      overwriteManual: values.overwriteManual ? 1 : 0,
      notifyEnabled: values.notifyEnabled ? 1 : 0,
      remark: values.remark || '',
    };
    setSubmitting(true);
    try {
      if (editing) {
        await updateGeoAutoTaskApi(payload);
        message.success('任务已更新');
      } else {
        await createGeoAutoTaskApi(payload);
        message.success('任务已创建');
      }
      onSaved();
      onClose();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={editing ? '编辑监测任务' : '新增监测任务'}
      open={open}
      onCancel={onClose}
      onOk={() => {
        void handleOk();
      }}
      confirmLoading={submitting}
      width={640}
      destroyOnHidden
    >
      <Form
        form={form}
        layout='vertical'
        className='pt-2'
      >
        <Form.Item
          name='taskName'
          label='任务名'
          rules={[{ required: true, message: '请输入任务名' }]}
        >
          <Input
            placeholder='如：核心问题每日监测'
            maxLength={128}
            showCount
          />
        </Form.Item>

        <Form.Item
          name='scopeType'
          label='圈选方式'
          rules={[{ required: true }]}
        >
          <Radio.Group
            options={[...GEO_AUTO_SCOPE_TYPES]}
            optionType='button'
            buttonStyle='solid'
          />
        </Form.Item>

        <Form.Item
          name='scopeIds'
          label={scopeType === 'TOPIC' ? '圈选话题' : '圈选问题'}
          rules={[{ required: true, message: '请至少选择一个' }]}
          tooltip={scopeType === 'TOPIC' ? '按话题圈选该话题下所有启用的问题' : '精确圈选启用的问题'}
        >
          {scopeType === 'TOPIC' ? (
            <Select
              mode='multiple'
              options={topics}
              placeholder='选择话题（可多选）'
              showSearch
              optionFilterProp='label'
            />
          ) : (
            <Select
              mode='multiple'
              options={questions.map((q) => ({
                label: `${q.topicName || '-'} · ${q.questionText}`,
                value: q.id,
              }))}
              placeholder='选择问题（可多选，仅启用的问题）'
              showSearch
              optionFilterProp='label'
              maxTagCount={4}
            />
          )}
        </Form.Item>

        <Form.Item
          name='platforms'
          label='监测平台'
          rules={[{ required: true, message: '请至少选择一个平台' }]}
        >
          <Select
            mode='multiple'
            placeholder='选择AI平台（未配置厂商的平台不可选）'
            options={platforms.map((p) => ({
              label: p.providerReady ? p.platformName : `${p.platformName}（${p.hint || '未就绪'}）`,
              value: p.platformName,
              disabled: !p.providerReady,
            }))}
          />
        </Form.Item>

        <div className='flex gap-4'>
          <Form.Item
            name='freq'
            label='监测频率'
            rules={[{ required: true }]}
          >
            <Radio.Group>
              <Radio.Button value='每日'>每日</Radio.Button>
              <Radio.Button value='每周'>每周</Radio.Button>
            </Radio.Group>
          </Form.Item>
          {freq === '每周' && (
            <Form.Item
              name='weekDay'
              label='每周'
              rules={[{ required: true }]}
            >
              <Select
                style={{ width: 110 }}
                options={[...GEO_AUTO_WEEK_DAYS]}
              />
            </Form.Item>
          )}
          <Form.Item
            name='runTime'
            label='运行时刻'
            rules={[{ required: true, message: '请选择运行时刻' }]}
          >
            <TimePicker
              format='HH:mm'
              allowClear={false}
            />
          </Form.Item>
        </div>

        <Form.Item
          name='overwriteManual'
          label={
            <span>
              同日有人工记录时覆盖{' '}
              <Tooltip title='默认人工优先：同日同问题同平台已有人工巡查记录时跳过回写；开启后自动结果会覆盖人工记录'>
                <QuestionCircleOutlined className='text-neutral-400' />
              </Tooltip>
            </span>
          }
          valuePropName='checked'
        >
          <Switch />
        </Form.Item>

        <Form.Item
          name='notifyEnabled'
          label={
            <span>
              变化提醒{' '}
              <Tooltip title='排名下滑/失去提及/新增负面时提醒（后续版本接入钉钉）'>
                <QuestionCircleOutlined className='text-neutral-400' />
              </Tooltip>
            </span>
          }
          valuePropName='checked'
        >
          <Switch />
        </Form.Item>

        <Form.Item
          name='remark'
          label='备注'
        >
          <Input.TextArea
            rows={2}
            maxLength={500}
            showCount
            placeholder='选填'
          />
        </Form.Item>

        {questionCount > 0 && platformNames.length > 0 && (
          <div className='rounded-md bg-neutral-50 px-3 py-2 text-sm text-neutral-600'>
            预估规模：{questionCount} 个问题 × {platformNames.length} 个平台 ≈ 每日 {estimatedDailyCalls} 次 AI 调用
            {freq === '每周' && '（每周一次）'}
          </div>
        )}
      </Form>
    </Modal>
  );
}
