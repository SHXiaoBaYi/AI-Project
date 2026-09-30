import { memo, useEffect, useState } from 'react';
import { App, Button, Card, Form, Input, Switch, Tag } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { usePermission } from '@/hooks/usePermission';
import { getDingTalkAppApi, saveDingTalkAppApi, testDingTalkAppApi, type DingTalkAppVO } from '@/api/dingtalk';

const DingTalkConfigPage = memo(function DingTalkConfigPage() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const canEdit = has('system:dingtalk:edit');
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [saved, setSaved] = useState<DingTalkAppVO | null>(null);
  const [canEditRobotRoute, setCanEditRobotRoute] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const data = await getDingTalkAppApi();
      setSaved(data);
      setCanEditRobotRoute(!!data?.canEditRobotRoute);
      form.setFieldsValue({
        appId: data?.appId || '',
        agentId: data?.agentId || '',
        clientId: data?.clientId || '',
        corpId: data?.corpId || '',
        clientSecret: '',
        enabled: data?.enabled === 1,
        robotRouteLocal: (data?.robotRoute || 'local') === 'local',
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const payload = async () => {
    const values = await form.validateFields();
    return {
      appId: String(values.appId).trim(),
      agentId: String(values.agentId).trim(),
      clientId: String(values.clientId).trim(),
      corpId: values.corpId ? String(values.corpId).trim() : '',
      clientSecret: values.clientSecret ? String(values.clientSecret).trim() : undefined,
      enabled: values.enabled ? 1 : 0,
      robotRoute: values.robotRouteLocal ? 'local' : 'online',
    };
  };

  const handleSave = async () => {
    const data = await payload();
    setSaving(true);
    try {
      await saveDingTalkAppApi(data);
      message.success('已保存');
      await load();
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    const data = await payload();
    setTesting(true);
    try {
      await testDingTalkAppApi(data);
      message.success('已连通钉钉');
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className='flex flex-col gap-4'>
      <Card
        title='钉钉应用配置'
        loading={loading}
        className='max-w-3xl'
      >
        <p className='mb-4 text-sm text-neutral-500'>
          用于钉钉扫码登录、按手机号绑定钉钉身份，以及创建/取消日程、简历上传钉盘。Client Secret
          只保存到数据库，页面只显示脱敏结果。扫码登录需在钉钉开放平台配置回调域名，并与本系统登录页同源。机器人
          ActionCard 会打开 H5（/dingtalk/bridge）并走企业免登；CorpId 必填。简历默认上传应用存储空间。
        </p>
        {saved?.hasClientSecret ? (
          <div className='mb-4 text-sm text-neutral-600'>
            当前密钥：<span className='font-mono'>{saved.clientSecretMasked}</span>
            （留空表示不修改）
          </div>
        ) : null}
        <Form
          form={form}
          layout='vertical'
          disabled={!canEdit}
          initialValues={{ enabled: true, robotRouteLocal: true }}
        >
          <Form.Item
            name='appId'
            label='AppId'
            rules={[{ required: true, message: '请填写 AppId' }]}
          >
            <Input placeholder='钉钉应用 AppId' />
          </Form.Item>
          <Form.Item
            name='agentId'
            label='AgentId'
            rules={[{ required: true, message: '请填写 AgentId' }]}
          >
            <Input placeholder='钉钉应用 AgentId' />
          </Form.Item>
          <Form.Item
            name='clientId'
            label='Client ID'
            rules={[{ required: true, message: '请填写 Client ID' }]}
          >
            <Input placeholder='Client ID / AppKey' />
          </Form.Item>
          <Form.Item
            name='corpId'
            label='CorpId'
            extra='填写后扫码仅允许本企业专属账号，免登也依赖此字段'
          >
            <Input placeholder='钉钉企业 CorpId（建议填写）' />
          </Form.Item>
          <Form.Item
            name='clientSecret'
            label='Client Secret'
            extra={saved?.hasClientSecret ? '填写后覆盖原密钥；留空则保持不变' : '首次保存必填'}
          >
            <Input.Password
              placeholder={saved?.hasClientSecret ? '留空则不修改' : 'Client Secret'}
              autoComplete='new-password'
            />
          </Form.Item>
          <Form.Item
            name='enabled'
            label='启用'
            valuePropName='checked'
          >
            <Switch />
          </Form.Item>

          <div className='mb-4 rounded border border-neutral-200 bg-neutral-50 px-4 py-3'>
            <div className='mb-2 flex flex-wrap items-center gap-2'>
              <span className='font-medium'>机器人联调环境</span>
              {canEditRobotRoute ? <Tag color='blue'>wangfangyang / thh / tbb 可切换</Tag> : <Tag>其他人固定线上</Tag>}
            </div>
            <p className='mb-3 text-sm text-neutral-500'>
              账号 wangfangyang、thh、tbb 发起的机器人对话共用此开关：开=本机 H5（默认），关=线上
              H5。其他用户一律走线上，不可改。
            </p>
            <Form.Item
              name='robotRouteLocal'
              label='联调账号 → 本机'
              valuePropName='checked'
              className='mb-0'
              extra={
                canEditRobotRoute
                  ? '打开：卡片打开本机 Vite（local-h5-base-url）；关闭：打开线上 /shxby'
                  : '当前登录人不在联调白名单，保存时不会改此开关'
              }
            >
              <Switch disabled={!canEdit || !canEditRobotRoute} />
            </Form.Item>
          </div>
        </Form>
        <div className='flex gap-2'>
          <PermissionButton
            perm='system:dingtalk:edit'
            type='primary'
            loading={saving}
            onClick={() => void handleSave()}
          >
            保存
          </PermissionButton>
          <Button
            loading={testing}
            disabled={!canEdit}
            onClick={() => void handleTest()}
          >
            测试连接
          </Button>
        </div>
      </Card>
    </div>
  );
});

export default DingTalkConfigPage;
