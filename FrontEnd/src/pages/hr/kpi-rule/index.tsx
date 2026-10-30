import { memo, useEffect, useState } from 'react';
import { App, Button, Card, Form, InputNumber, Space, Typography } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getHrKpiScoreRuleApi, saveHrKpiScoreRuleApi, type HrKpiScoreRule } from '@/api/hr';

const KpiRulePage = memo(function KpiRulePage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<HrKpiScoreRule>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [updateTime, setUpdateTime] = useState<string>();

  const load = async () => {
    setLoading(true);
    try {
      const rule = await getHrKpiScoreRuleApi();
      form.setFieldsValue(rule);
      setUpdateTime(rule.updateTime);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div className='p-1'>
      <Card
        loading={loading}
        title='考核计分规则'
        extra={
          updateTime ? (
            <Typography.Text type='secondary'>
              最近更新：{String(updateTime).replace('T', ' ').slice(0, 19)}
            </Typography.Text>
          ) : null
        }
      >
        <Typography.Paragraph
          type='secondary'
          className='mb-4'
        >
          完成门槛用于判定任务何时算「完成」。下方「岗位权重」同步用于考核得分面板：得分=整体完成率×30；P0/P1
          权重取本页配置；完成计权重、未完成计
          0；无额外加减分。修改门槛后仅影响之后新完成的任务，权重变更立即影响得分展示。
        </Typography.Paragraph>
        <Form
          form={form}
          layout='vertical'
          className='max-w-xl'
          initialValues={{
            p0OnboardCount: 1,
            p0Score: 2,
            p1QualifiedResumeCount: 3,
            p1OnboardCount: 1,
            p1Score: 1,
            dueMonths: 1,
          }}
        >
          <Card
            type='inner'
            size='small'
            title='P0'
            className='mb-4'
          >
            <Space
              align='start'
              wrap
              size='large'
            >
              <Form.Item
                name='p0OnboardCount'
                label='完成多少人入职'
                rules={[{ required: true, message: '必填' }]}
              >
                <InputNumber
                  min={1}
                  max={99}
                  precision={0}
                  addonAfter='人'
                  className='w-36'
                />
              </Form.Item>
              <Form.Item
                name='p0Score'
                label='岗位权重（考核得分）'
                rules={[{ required: true, message: '必填' }]}
                extra='用于完成率加权，如 2'
              >
                <InputNumber
                  min={0}
                  max={100}
                  precision={0}
                  className='w-36'
                />
              </Form.Item>
            </Space>
          </Card>

          <Card
            type='inner'
            size='small'
            title='P1（周期内二选一）'
            className='mb-4'
          >
            <Space
              align='start'
              wrap
              size='large'
            >
              <Form.Item
                name='p1QualifiedResumeCount'
                label='合格简历份数'
                rules={[{ required: true, message: '必填' }]}
              >
                <InputNumber
                  min={1}
                  max={99}
                  precision={0}
                  addonAfter='份'
                  className='w-36'
                />
              </Form.Item>
              <Form.Item
                name='p1OnboardCount'
                label='或入职人数'
                rules={[{ required: true, message: '必填' }]}
              >
                <InputNumber
                  min={1}
                  max={99}
                  precision={0}
                  addonAfter='人'
                  className='w-36'
                />
              </Form.Item>
              <Form.Item
                name='p1Score'
                label='岗位权重（考核得分）'
                rules={[{ required: true, message: '必填' }]}
                extra='用于完成率加权，如 1'
              >
                <InputNumber
                  min={0}
                  max={100}
                  precision={0}
                  className='w-36'
                />
              </Form.Item>
            </Space>
          </Card>

          <Form.Item
            name='dueMonths'
            label='任务默认周期（发布后几个月到期）'
            rules={[{ required: true, message: '必填' }]}
            extra='仅对新生成的考核任务槽位生效'
          >
            <InputNumber
              min={1}
              max={24}
              precision={0}
              addonAfter='个月'
              className='w-40'
            />
          </Form.Item>

          <Space>
            <PermissionButton
              perm='hr:kpi-rule:edit'
              type='primary'
              loading={saving}
              onClick={async () => {
                const values = await form.validateFields();
                setSaving(true);
                try {
                  await saveHrKpiScoreRuleApi(values);
                  message.success('已保存');
                  load();
                } finally {
                  setSaving(false);
                }
              }}
            >
              保存
            </PermissionButton>
            <Button onClick={load}>刷新</Button>
          </Space>
        </Form>
      </Card>
    </div>
  );
});

export default KpiRulePage;
