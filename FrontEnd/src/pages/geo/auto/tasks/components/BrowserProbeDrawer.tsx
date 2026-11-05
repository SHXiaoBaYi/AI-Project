import { useEffect, useMemo, useState } from 'react';
import {
  App,
  Alert,
  Button,
  Drawer,
  Form,
  Image,
  Input,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { CopyOutlined, ExperimentOutlined, UploadOutlined } from '@ant-design/icons';
import {
  browserLoginGeoAutoApi,
  getGeoAutoBrowserAccountsApi,
  getGeoAutoPlatformOptionsApi,
  probeGeoAutoBrowserApi,
  uploadGeoAutoBrowserStateApi,
} from '@/api/geoAuto';
import { resolveUploadUrl } from '@/utils/uploadUrl';
import { GEO_AUTO_PROBE_REGIONS } from '@/constants/geoAuto';
import type { GeoAutoPlatformOption, GeoBrowserAccountVO, GeoBrowserProbeVO } from '@/types/geoAuto';

interface BrowserProbeDrawerProps {
  open: boolean;
  onClose: () => void;
}

/**
 * 采集诊断：单题驱动真实网页版，核对选择器 / 登录态 / 官方分享链接 / 整页截图。
 * 上线新平台或 C 端改版后，先在这里试跑一次，再启用定时任务。
 */
export default function BrowserProbeDrawer({ open, onClose }: BrowserProbeDrawerProps) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [platforms, setPlatforms] = useState<GeoAutoPlatformOption[]>([]);
  const [probing, setProbing] = useState(false);
  const [result, setResult] = useState<GeoBrowserProbeVO | null>(null);
  const [statePlatform, setStatePlatform] = useState<string>('');
  const [uploadingState, setUploadingState] = useState(false);
  const [accounts, setAccounts] = useState<GeoBrowserAccountVO[]>([]);
  const [loggingIn, setLoggingIn] = useState(false);
  const selectedPlatform = Form.useWatch('platform', form) as string | undefined;

  const loadAccounts = () =>
    getGeoAutoBrowserAccountsApi()
      .then(setAccounts)
      .catch(() => setAccounts([]));

  useEffect(() => {
    if (!open) return;
    getGeoAutoPlatformOptionsApi()
      .then(setPlatforms)
      .catch(() => setPlatforms([]));
    void loadAccounts();
    form.setFieldsValue({
      deepThink: false,
      screenshot: true,
      region: '全国随机',
    });
  }, [open, form]);

  const currentAccount = accounts.find((a) => a.platform === selectedPlatform);

  /** 用系统维护的平台账号在服务器无头浏览器模拟登录，登录后可直接试跑 */
  const doBrowserLogin = async () => {
    if (!selectedPlatform) {
      message.warning('请先选择平台');
      return;
    }
    setLoggingIn(true);
    try {
      const res = await browserLoginGeoAutoApi({ platform: selectedPlatform, forceRefresh: true });
      if (res.success) {
        message.success(`登录态已刷新（${res.source || 'READY'}）`);
      } else {
        message.warning(res.error || '模拟登录未完成，请查看平台账号诊断');
      }
      await loadAccounts();
    } catch {
      message.error('模拟登录请求失败');
    } finally {
      setLoggingIn(false);
    }
  };

  const diagnosticText = useMemo(() => {
    if (!result?.diagnostic) return '';
    try {
      return JSON.stringify(JSON.parse(result.diagnostic), null, 2);
    } catch {
      return result.diagnostic;
    }
  }, [result?.diagnostic]);

  const runProbe = async () => {
    const values = await form.validateFields();
    setProbing(true);
    setResult(null);
    try {
      const res = await probeGeoAutoBrowserApi({
        platform: values.platform,
        question: values.question.trim(),
        deepThink: !!values.deepThink,
        screenshot: !!values.screenshot,
        region: values.region,
      });
      setResult(res);
      message[res.success ? 'success' : 'warning'](
        res.success ? '采集成功，请核对下方链接与截图' : '采集未完成，请查看诊断信息',
      );
    } catch {
      message.error('试跑请求失败');
    } finally {
      setProbing(false);
    }
  };

  const uploadState = async (file: File) => {
    const platform = statePlatform || form.getFieldValue('platform');
    if (!platform) {
      message.warning('请先选择要上传登录态的平台');
      return;
    }
    setUploadingState(true);
    try {
      await uploadGeoAutoBrowserStateApi(platform, file);
      message.success(`已更新「${platform}」登录态，下次采集自动复用`);
    } catch {
      message.error('登录态上传失败（需为 Playwright storageState JSON）');
    } finally {
      setUploadingState(false);
    }
  };

  const copy = (text?: string) => {
    if (!text) return;
    void navigator.clipboard
      .writeText(text)
      .then(() => message.success('已复制'))
      .catch(() => message.error('复制失败'));
  };

  const platformOptions = platforms.map((p) => ({
    label: p.providerReady ? p.platformName : `${p.platformName}（未配置适配器）`,
    value: p.platformName,
    disabled: !p.providerReady,
  }));

  return (
    <Drawer
      title='采集诊断'
      open={open}
      onClose={onClose}
      width={760}
      extra={
        <Button
          type='primary'
          icon={<ExperimentOutlined />}
          loading={probing}
          onClick={() => void runProbe()}
        >
          开始试跑
        </Button>
      }
    >
      <div className='flex flex-col gap-4'>
        <Alert
          type='info'
          showIcon
          message='浏览器通道直接驱动各模型网页版，产出官方分享链接、整页截图、思考过程与页面引用信源。'
          description='试跑不写入业务库；若未取到回答或分享链接，多半是登录态缺失或平台改版，可先上传 storageState 或调整 application.yml 里的选择器。'
        />

        <Alert
          type={currentAccount?.ready ? 'success' : 'warning'}
          showIcon
          message={
            currentAccount
              ? `平台账号 ${currentAccount.account} · 登录态 ${currentAccount.loginStatus} · ${currentAccount.loginType}`
              : '当前平台尚未配置「用于浏览器监测」的平台账号'
          }
          description={
            currentAccount?.error ||
            '采集前会用系统维护的平台账号在服务器无头浏览器模拟登录；登录态按平台缓存，一批 3 题内只登录一次。只有扫码/验证码登录的平台请改用人工导出登录态。'
          }
          action={
            <Button
              size='small'
              loading={loggingIn}
              onClick={() => void doBrowserLogin()}
            >
              模拟登录 / 刷新
            </Button>
          }
        />

        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='platform'
            label='监测平台'
            rules={[{ required: true, message: '请选择平台' }]}
          >
            <Select
              options={platformOptions}
              placeholder='选择已配置适配器的网页版模型'
            />
          </Form.Item>
          <Form.Item
            name='question'
            label='监控问题'
            rules={[{ required: true, message: '请输入监控问题' }]}
          >
            <Input.TextArea
              rows={2}
              maxLength={500}
              showCount
              placeholder='输入一条真实用户问题，如：新疆适合寄内地的礼品有哪些？'
            />
          </Form.Item>
          <div className='flex gap-6'>
            <Form.Item
              name='deepThink'
              label='深度思考'
              valuePropName='checked'
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name='screenshot'
              label='整页截图'
              valuePropName='checked'
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name='region'
              label='提问位置'
            >
              <Select
                style={{ width: 140 }}
                options={GEO_AUTO_PROBE_REGIONS.map((r) => ({ label: r, value: r }))}
              />
            </Form.Item>
          </div>
        </Form>

        <div className='rounded-lg border border-neutral-200 p-3'>
          <div className='mb-2 flex items-center justify-between'>
            <Typography.Text strong>平台登录态（storageState）</Typography.Text>
            <Space>
              <Select
                style={{ width: 150 }}
                placeholder='选择平台'
                value={statePlatform || undefined}
                onChange={setStatePlatform}
                options={platforms.map((p) => ({ label: p.platformName, value: p.platformName }))}
                allowClear
              />
              <Upload
                accept='.json,application/json'
                showUploadList={false}
                beforeUpload={(file) => {
                  void uploadState(file);
                  return false;
                }}
              >
                <Button
                  size='small'
                  icon={<UploadOutlined />}
                  loading={uploadingState}
                >
                  上传 JSON
                </Button>
              </Upload>
            </Space>
          </div>
          <div className='text-xs text-neutral-400'>
            用 Playwright 登录后导出 storageState（含
            cookies/origins），文件名会自动按平台保存；未登录时部分平台无法提问或生成分享链接。
          </div>
        </div>

        <Spin spinning={probing}>
          {result && (
            <div className='flex flex-col gap-3'>
              <Alert
                type={result.success ? 'success' : 'warning'}
                showIcon
                message={result.success ? '采集成功' : '采集未完成'}
                description={result.error}
              />

              <div className='rounded-lg border border-neutral-200 p-3'>
                <Typography.Text strong>回答链接</Typography.Text>
                {result.probeUrl ? (
                  <div className='mt-2'>
                    <Space.Compact className='w-full'>
                      <Input
                        readOnly
                        size='small'
                        value={result.probeUrl}
                      />
                      <Button
                        size='small'
                        icon={<CopyOutlined />}
                        onClick={() => copy(result.probeUrl)}
                      />
                      <Button
                        size='small'
                        type='primary'
                        href={result.probeUrl}
                        target='_blank'
                        rel='noreferrer'
                      >
                        打开
                      </Button>
                    </Space.Compact>
                    <div className='mt-1.5'>
                      <Tag color={result.probeUrlSource === 'OFFICIAL_SHARE' ? 'green' : 'default'}>
                        {result.probeUrlSource === 'OFFICIAL_SHARE' ? '官方分享链接' : '会话页面地址'}
                      </Tag>
                      <span className='text-xs text-neutral-400'>{result.modelLabel}</span>
                    </div>
                  </div>
                ) : (
                  <div className='mt-1 text-sm text-neutral-400'>未取到分享链接</div>
                )}
              </div>

              {result.screenshotUrl && (
                <div className='rounded-lg border border-neutral-200 p-3'>
                  <Typography.Text strong>整页截图存证</Typography.Text>
                  <div className='mt-2'>
                    <Image
                      width='100%'
                      src={resolveUploadUrl(result.screenshotUrl)}
                      alt='试跑整页截图'
                    />
                  </div>
                </div>
              )}

              {result.reasoningText && (
                <details className='rounded-lg border border-neutral-200 px-3 py-2'>
                  <summary className='cursor-pointer text-sm'>
                    <Typography.Text strong>思考过程</Typography.Text>
                    <span className='ml-2 text-xs text-neutral-400'>{result.reasoningText.length} 字</span>
                  </summary>
                  <div className='mt-2 max-h-60 overflow-auto rounded-md bg-neutral-50 p-3 text-sm whitespace-pre-wrap'>
                    {result.reasoningText}
                  </div>
                </details>
              )}

              {result.answerText && (
                <div className='rounded-lg border border-neutral-200 p-3'>
                  <Typography.Text strong>AI 回答原文</Typography.Text>
                  <div className='mt-2 max-h-72 overflow-auto rounded-md bg-neutral-50 p-3 text-sm whitespace-pre-wrap'>
                    {result.answerText}
                  </div>
                </div>
              )}

              {result.citations && result.citations.length > 0 && (
                <div className='rounded-lg border border-neutral-200 p-3'>
                  <Typography.Text strong>引用信源（{result.citations.length}）</Typography.Text>
                  <div className='mt-2 flex flex-col gap-1'>
                    {result.citations.map((c, i) => (
                      <div
                        key={i}
                        className='truncate text-sm'
                      >
                        <span className='mr-2 text-xs text-neutral-400'>{i + 1}</span>
                        {c.url ? (
                          <a
                            href={c.url}
                            target='_blank'
                            rel='noreferrer'
                          >
                            {c.title || c.url}
                          </a>
                        ) : (
                          c.title
                        )}
                        {c.site && <Tag className='ml-2'>{c.site}</Tag>}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {diagnosticText && (
                <details className='rounded-md bg-neutral-50 px-3 py-2 text-sm'>
                  <summary className='cursor-pointer text-neutral-500'>
                    采集诊断（步骤耗时 / 选择器命中 / 登录态）
                  </summary>
                  <pre className='mt-2 max-h-60 overflow-auto text-xs whitespace-pre-wrap'>{diagnosticText}</pre>
                </details>
              )}
            </div>
          )}
        </Spin>
      </div>
    </Drawer>
  );
}
