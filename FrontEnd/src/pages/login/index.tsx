import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Card, Modal, Spin } from 'antd';
import { ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { getDingTalkLoginConfigApi, type DingTalkLoginConfig } from '@/api/auth';
import { CODE_LOGIN_CONFLICT } from '@/api/request';
import { getInfo, loginByDingTalk } from '@/store/slices/userSlice';
import { message } from '@/store/slices/staticFunctionSlice';
import type { AppDispatch } from '@/store';
import loginBg from '@/assets/login-bg.png';

const DD_LOGIN_SCRIPT = 'https://g.alicdn.com/dingding/h5-dingtalk-login/0.21.0/ddlogin.js';
const QR_BOX_ID = 'dingtalk-login-qr';

declare global {
  interface Window {
    DTFrameLogin?: (
      frameParams: { id: string; width: number; height: number },
      loginParams: Record<string, string>,
      success: (result: { redirectUrl?: string; authCode?: string; state?: string }) => void,
      error: (errorMsg: string) => void,
    ) => void;
  }
}

function loginRedirectUri() {
  const base = import.meta.env.BASE_URL || '/';
  const joined = `${base.endsWith('/') ? base : `${base}/`}login`;
  const path = joined.startsWith('/') ? joined : `/${joined}`;
  return `${window.location.origin}${path}`;
}

function loadDingTalkScript() {
  return new Promise<void>((resolve, reject) => {
    if (window.DTFrameLogin) {
      resolve();
      return;
    }
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${DD_LOGIN_SCRIPT}"]`);
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('钉钉登录脚本加载失败')));
      return;
    }
    const script = document.createElement('script');
    script.src = DD_LOGIN_SCRIPT;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('钉钉登录脚本加载失败'));
    document.head.appendChild(script);
  });
}

export default function Login() {
  const [loading, setLoading] = useState(false);
  const [configLoading, setConfigLoading] = useState(true);
  const [config, setConfig] = useState<DingTalkLoginConfig | null>(null);
  const [qrError, setQrError] = useState('');
  const [qrKey, setQrKey] = useState(0);
  const loggingRef = useRef(false);
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  useEffect(() => {
    const authError = sessionStorage.getItem('authError');
    if (authError) {
      sessionStorage.removeItem('authError');
      message.error(authError);
    }
  }, []);

  const completeLogin = useCallback(
    async (authCode: string, force = false) => {
      if (loggingRef.current) return;
      loggingRef.current = true;
      setLoading(true);
      try {
        await dispatch(loginByDingTalk({ authCode, force })).unwrap();
        await dispatch(getInfo()).unwrap();
        message.success('登录成功!');
        navigate('/', { replace: true });
      } catch (err) {
        const code = Number((err as { code?: number | string })?.code);
        if (code === CODE_LOGIN_CONFLICT) {
          Modal.confirm({
            title: '账号登录提示',
            icon: <ExclamationCircleOutlined />,
            content: '该账号已在其他设备登录，是否强制对方下线？',
            okText: '强制下线',
            cancelText: '取消',
            centered: true,
            onOk: async () => {
              loggingRef.current = false;
              await completeLogin(authCode, true);
            },
            onCancel: () => {
              loggingRef.current = false;
              setQrKey((k) => k + 1);
            },
          });
          return;
        }
        loggingRef.current = false;
        setQrKey((k) => k + 1);
      } finally {
        setLoading(false);
      }
    },
    [dispatch, navigate],
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setConfigLoading(true);
      try {
        const data = await getDingTalkLoginConfigApi();
        if (!cancelled) setConfig(data);
      } catch {
        if (!cancelled) {
          setConfig({
            enabled: 0,
            clientId: '',
            message: '无法获取钉钉登录配置',
          });
        }
      } finally {
        if (!cancelled) setConfigLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const authCode = params.get('authCode') || params.get('code');
    if (authCode) {
      window.history.replaceState({}, '', window.location.pathname);
      void completeLogin(authCode);
    }
  }, [completeLogin]);

  useEffect(() => {
    if (!config || config.enabled !== 1 || !config.clientId || loading) return;
    let cancelled = false;
    (async () => {
      setQrError('');
      try {
        await loadDingTalkScript();
        if (cancelled || !window.DTFrameLogin) return;
        const box = document.getElementById(QR_BOX_ID);
        if (box) box.innerHTML = '';
        const loginParams: Record<string, string> = {
          redirect_uri: encodeURIComponent(loginRedirectUri()),
          client_id: config.clientId,
          scope: 'openid',
          response_type: 'code',
          prompt: 'consent',
          state: `xby_${Date.now()}`,
        };
        if (config.exclusiveLogin && config.corpId) {
          loginParams.exclusiveLogin = 'true';
          loginParams.exclusiveCorpId = config.corpId;
        }
        window.DTFrameLogin(
          { id: QR_BOX_ID, width: 300, height: 300 },
          loginParams,
          (result) => {
            if (result?.authCode) {
              void completeLogin(result.authCode);
            } else {
              setQrError('未获取到授权码，请刷新二维码重试');
            }
          },
          (errorMsg) => {
            setQrError(errorMsg || '二维码加载失败');
          },
        );
      } catch (err) {
        setQrError(err instanceof Error ? err.message : '二维码加载失败');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [completeLogin, config, loading, qrKey]);

  const enabled = config?.enabled === 1 && !!config.clientId;

  return (
    <div
      className='relative flex min-h-screen items-center justify-center overflow-hidden md:justify-start'
      style={{
        background: `url(${loginBg}) center/cover no-repeat`,
      }}
    >
      <div
        className='absolute rounded-full'
        style={{
          width: 520,
          height: 520,
          background: 'radial-gradient(circle, rgba(59,130,246,0.35) 70%, transparent 70%)',
          filter: 'blur(60px)',
          top: -160,
          right: -120,
        }}
      />
      <div
        className='absolute rounded-full'
        style={{
          width: 400,
          height: 400,
          background: 'radial-gradient(circle, rgba(99,102,241,0.3) 70%, transparent 70%)',
          filter: 'blur(60px)',
          bottom: -120,
          left: -100,
        }}
      />

      <Card
        className='relative w-100 rounded-2xl! shadow-xl backdrop-blur-sm md:mr-40! md:ml-auto!'
        variant='borderless'
        style={{ background: 'rgba(255, 255, 255, 0.75)' }}
        styles={{ body: { padding: '40px 36px 32px' } }}
      >
        <div className='mb-6 text-center'>
          <div
            className='mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl'
            style={{ background: 'linear-gradient(135deg, var(--ant-color-primary), var(--ant-color-primary-hover))' }}
          >
            <span className='text-2xl font-bold text-white'>小</span>
          </div>
          <h1 className='text-2xl font-bold tracking-tight text-gray-800'>小巴依(上海)</h1>
          <p className='mt-1.5 text-sm text-gray-400'>请使用钉钉扫码登录</p>
        </div>

        <Spin spinning={configLoading || loading}>
          {enabled ? (
            <div className='flex flex-col items-center gap-3'>
              <div
                id={QR_BOX_ID}
                key={qrKey}
                className='flex h-[300px] w-[300px] items-center justify-center overflow-hidden rounded-lg bg-white'
              />
              {qrError ? <p className='text-center text-sm text-red-500'>{qrError}</p> : null}
              <Button
                type='link'
                icon={<ReloadOutlined />}
                onClick={() => {
                  loggingRef.current = false;
                  setQrKey((k) => k + 1);
                }}
              >
                刷新二维码
              </Button>
              <p className='text-center text-xs text-gray-400'>使用企业钉钉扫码；未绑定系统账号将无法登录</p>
            </div>
          ) : (
            <div className='rounded-lg bg-amber-50 px-4 py-6 text-center text-sm text-amber-800'>
              {config?.message || '钉钉扫码登录未配置，请联系管理员'}
            </div>
          )}
        </Spin>
      </Card>
    </div>
  );
}
