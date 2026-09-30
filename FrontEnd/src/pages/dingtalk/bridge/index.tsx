import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Result, Spin, Typography } from 'antd';
import { getDingTalkLoginConfigApi, dingTalkSsoApi } from '@/api/auth';
import { executeDingTalkIntentApi, type DingTalkIntentExecuteResult } from '@/api/dingtalk';
import { CODE_LOGIN_CONFLICT } from '@/api/request';
import { setToken, getToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';

const DD_JSAPI = 'https://g.alicdn.com/dingding/dingtalk-jsapi/3.0.25/dingtalk.open.js';

declare global {
  interface Window {
    dd?: {
      ready: (fn: () => void) => void;
      error?: (fn: (err: unknown) => void) => void;
      runtime?: {
        permission?: {
          requestAuthCode: (opts: {
            corpId: string;
            onSuccess: (res: { code?: string }) => void;
            onFail: (err: { errorMessage?: string; message?: string }) => void;
          }) => void;
        };
      };
      biz?: {
        navigation?: {
          close?: () => void;
        };
      };
    };
  }
}

function isDingTalkUa() {
  return /DingTalk/i.test(navigator.userAgent || '');
}

function loadDingTalkJsapi() {
  return new Promise<void>((resolve, reject) => {
    if (window.dd?.ready) {
      resolve();
      return;
    }
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${DD_JSAPI}"]`);
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('钉钉 JSAPI 加载失败')));
      return;
    }
    const script = document.createElement('script');
    script.src = DD_JSAPI;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('钉钉 JSAPI 加载失败'));
    document.head.appendChild(script);
  });
}

function requestCorpAuthCode(corpId: string) {
  return new Promise<string>((resolve, reject) => {
    if (!window.dd?.ready || !window.dd.runtime?.permission?.requestAuthCode) {
      reject(new Error('当前环境不支持钉钉免登'));
      return;
    }
    window.dd.ready(() => {
      window.dd!.runtime!.permission!.requestAuthCode({
        corpId,
        onSuccess: (res) => {
          if (res?.code) {
            resolve(res.code);
          } else {
            reject(new Error('未获取到免登授权码'));
          }
        },
        onFail: (err) => {
          reject(new Error(err?.errorMessage || err?.message || '免登授权失败'));
        },
      });
    });
  });
}

function oauthAuthorizeUrl(clientId: string, corpId: string | undefined, ticket: string) {
  const redirect = `${window.location.origin}${withBase('/dingtalk/bridge')}`;
  const params = new URLSearchParams({
    redirect_uri: redirect,
    response_type: 'code',
    client_id: clientId,
    scope: 'openid',
    prompt: 'consent',
    state: ticket,
  });
  if (corpId) {
    params.set('exclusiveLogin', 'true');
    params.set('exclusiveCorpId', corpId);
  }
  return `https://login.dingtalk.com/oauth2/auth?${params.toString()}`;
}

export default function DingTalkBridgePage() {
  const [status, setStatus] = useState<'loading' | 'pick' | 'done' | 'error'>('loading');
  const [tip, setTip] = useState('正在连接钉钉…');
  const [message, setMessage] = useState('');
  const [candidates, setCandidates] = useState<DingTalkIntentExecuteResult['candidates']>([]);
  const ran = useRef(false);

  const finishLogin = useCallback(async (jwt: string) => {
    setToken(jwt);
  }, []);

  const ensureLogin = useCallback(
    async (ticket: string) => {
      if (getToken()) {
        return;
      }
      const config = await getDingTalkLoginConfigApi();
      if (!config?.enabled || !config.clientId) {
        throw new Error(config?.message || '钉钉登录未配置');
      }

      const params = new URLSearchParams(window.location.search);
      const oauthCode = params.get('authCode') || params.get('code');
      if (oauthCode) {
        const res = await dingTalkSsoApi(oauthCode, 'oauth', true);
        await finishLogin(res.token);
        window.history.replaceState({}, '', `${window.location.pathname}?ticket=${encodeURIComponent(ticket)}`);
        return;
      }

      if (isDingTalkUa() && config.corpId) {
        setTip('正在免登…');
        await loadDingTalkJsapi();
        const code = await requestCorpAuthCode(config.corpId);
        try {
          const res = await dingTalkSsoApi(code, 'corp', true);
          await finishLogin(res.token);
          return;
        } catch (err) {
          const e = err as Error & { code?: number; data?: { forceTicket?: string } };
          if (e.code === CODE_LOGIN_CONFLICT) {
            const forceTicket =
              e.data && typeof e.data === 'object' && 'forceTicket' in e.data
                ? String((e.data as { forceTicket?: string }).forceTicket || '')
                : '';
            const res = await dingTalkSsoApi(code, 'corp', true, forceTicket || undefined);
            await finishLogin(res.token);
            return;
          }
          throw err;
        }
      }

      setTip('跳转钉钉授权…');
      window.location.href = oauthAuthorizeUrl(config.clientId, config.corpId, ticket);
      throw new Error('__redirect__');
    },
    [finishLogin],
  );

  const runIntent = useCallback(async (ticket: string) => {
    setTip('正在执行…');
    const result = await executeDingTalkIntentApi(ticket);
    if (result.status === 'need_candidate' && result.candidates?.length) {
      setCandidates(result.candidates);
      setMessage(result.message || '请选择候选人');
      setStatus('pick');
      return;
    }
    setMessage(result.message || '已完成');
    setStatus('done');
  }, []);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    const params = new URLSearchParams(window.location.search);
    const ticket = params.get('ticket') || params.get('state') || '';
    if (!ticket) {
      setStatus('error');
      setMessage('缺少意图参数，请从钉钉机器人卡片重新进入');
      return;
    }
    (async () => {
      try {
        await ensureLogin(ticket);
        await runIntent(ticket);
      } catch (err) {
        if (err instanceof Error && err.message === '__redirect__') {
          return;
        }
        setStatus('error');
        setMessage(err instanceof Error ? err.message : '处理失败');
      }
    })();
  }, [ensureLogin, runIntent]);

  const onPick = async (inviteTicket: string) => {
    setStatus('loading');
    setTip('正在创建面试日程…');
    try {
      await runIntent(inviteTicket);
    } catch (err) {
      setStatus('error');
      setMessage(err instanceof Error ? err.message : '创建失败');
    }
  };

  if (status === 'loading') {
    return (
      <div className='flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 px-6'>
        <Spin size='large' />
        <Typography.Text type='secondary'>{tip}</Typography.Text>
      </div>
    );
  }

  if (status === 'pick') {
    return (
      <div className='mx-auto flex min-h-screen max-w-md flex-col gap-4 bg-slate-50 px-4 py-8'>
        <Typography.Title level={4}>{message}</Typography.Title>
        <div className='flex flex-col gap-2'>
          {(candidates || []).map((c) => (
            <Button
              key={c.ticket}
              type='primary'
              block
              className='h-auto py-3 text-left whitespace-normal'
              onClick={() => void onPick(c.ticket)}
            >
              {c.name}
              {c.jobName ? ` · ${c.jobName}` : ''}
            </Button>
          ))}
        </div>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
        <Result
          status='error'
          title='无法完成'
          subTitle={message}
        />
      </div>
    );
  }

  return (
    <div className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
      <Result
        status='success'
        title='已处理'
        subTitle={<span className='text-left whitespace-pre-wrap'>{message}</span>}
        extra={
          isDingTalkUa() ? (
            <Button
              type='primary'
              onClick={() => window.dd?.biz?.navigation?.close?.()}
            >
              关闭
            </Button>
          ) : null
        }
      />
    </div>
  );
}
