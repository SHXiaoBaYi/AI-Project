import { memo, useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Form, Input, Switch, Table, Tag } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { usePermission } from '@/hooks/usePermission';
import {
  getDingTalkAppApi,
  getDingTalkDirectoryApi,
  saveDingTalkAppApi,
  testDingTalkAppApi,
  type DingTalkAppVO,
  type DingTalkDirectoryUser,
} from '@/api/dingtalk';

const DingTalkConfigPage = memo(function DingTalkConfigPage() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const canEdit = has('system:dingtalk:edit');
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [saved, setSaved] = useState<DingTalkAppVO | null>(null);
  const [directory, setDirectory] = useState<DingTalkDirectoryUser[]>([]);
  const [loadingDirectory, setLoadingDirectory] = useState(false);
  const [keyword, setKeyword] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const data = await getDingTalkAppApi();
      setSaved(data);
      form.setFieldsValue({
        appId: data?.appId || '',
        agentId: data?.agentId || '',
        clientId: data?.clientId || '',
        corpId: data?.corpId || '',
        clientSecret: '',
        enabled: data?.enabled === 1,
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

  const loadDirectory = async () => {
    setLoadingDirectory(true);
    try {
      const rows = await getDingTalkDirectoryApi();
      setDirectory(rows ?? []);
      setKeyword('');
    } finally {
      setLoadingDirectory(false);
    }
  };

  const filteredDirectory = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) return directory;
    return directory.filter((row) => {
      const blob = `${row.name} ${row.mobile} ${row.telephone || ''} ${row.userid} ${row.unionId || ''}`.toLowerCase();
      return blob.includes(text);
    });
  }, [directory, keyword]);

  return (
    <div className='flex flex-col gap-4'>
      <Card
        title='钉钉应用配置'
        loading={loading}
        className='max-w-3xl'
      >
        <p className='mb-4 text-sm text-neutral-500'>
          用于钉钉扫码登录、按手机号绑定钉钉身份，以及创建/取消日程。Client Secret
          只保存到数据库，页面只显示脱敏结果。扫码登录需在钉钉开放平台配置回调域名，并与本系统登录页同源。
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
          initialValues={{ enabled: true }}
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
            extra='填写后扫码仅允许本企业专属账号，避免扫到个人钉钉'
          >
            <Input placeholder='钉钉企业 CorpId（可选，建议填写）' />
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
      <Card
        title='应用读到的通讯录'
        extra={
          <Button
            loading={loadingDirectory}
            onClick={() => void loadDirectory()}
          >
            读取
          </Button>
        }
      >
        <p className='mb-3 text-sm text-neutral-500'>
          这里是钉钉接口返回的已加入成员，绑定用的就是这份数据。可按姓名或手机号检索，对照 15102131256、13876985672
          是否出现、手机号是否为空、是不是企业账号。
        </p>
        {directory.length > 0 ? (
          <>
            <Input.Search
              allowClear
              className='mb-3 max-w-md'
              placeholder='检索姓名、手机号、userid'
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
            <div className='mb-2 text-sm text-neutral-500'>
              共 {directory.length} 人{keyword.trim() ? `，匹配 ${filteredDirectory.length} 人` : ''}
            </div>
            <Table<DingTalkDirectoryUser>
              size='small'
              rowKey='userid'
              pagination={false}
              scroll={{ x: 1100, y: 480 }}
              dataSource={filteredDirectory}
              columns={[
                { title: '姓名', dataIndex: 'name', width: 120, fixed: 'left' },
                { title: '手机号', dataIndex: 'mobile', width: 140, render: (value: string) => value || '—' },
                { title: '国家码', dataIndex: 'stateCode', width: 90, render: (value: string) => value || '—' },
                { title: '分机', dataIndex: 'telephone', width: 120, render: (value: string) => value || '—' },
                {
                  title: '企业账号',
                  dataIndex: 'exclusiveAccount',
                  width: 100,
                  render: (value: boolean) => (value ? <Tag color='blue'>是</Tag> : <Tag>否</Tag>),
                },
                {
                  title: '隐藏手机号',
                  dataIndex: 'hideMobile',
                  width: 110,
                  render: (value: boolean) => (value ? <Tag color='orange'>是</Tag> : <Tag>否</Tag>),
                },
                {
                  title: '已激活',
                  dataIndex: 'active',
                  width: 90,
                  render: (value: boolean) => (value ? <Tag color='success'>是</Tag> : <Tag>否</Tag>),
                },
                { title: 'userid', dataIndex: 'userid', width: 180 },
                { title: 'unionId', dataIndex: 'unionId', width: 220, render: (value: string) => value || '—' },
              ]}
            />
          </>
        ) : (
          <div className='py-6 text-sm text-neutral-400'>{loadingDirectory ? '正在读取钉钉通讯录' : '还没有读取'}</div>
        )}
      </Card>
    </div>
  );
});

export default DingTalkConfigPage;
