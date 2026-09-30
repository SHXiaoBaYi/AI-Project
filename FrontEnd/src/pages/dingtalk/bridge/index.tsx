import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Result, Spin, Typography } from 'antd';
import { getDingTalkLoginConfigApi, dingTalkSsoApi } from '@/api/auth';
import { executeDingTalkIntentApi, type DingTalkIntentExecuteResult } from '@/api/dingtalk';
import { CODE_LOGIN_CONFLICT } from '@/api/request';
import { setToken, getToken, removeToken } from '@/utils/auth';

const DD_JSAPI = 'https://g.alicdn.com/dingding/dingtalk-jsapi/3.0.25/dingtalk.open.js';

declare global {
  interface Window {
    dd?: {
      env?: { platform?: string };
      ready: (fn: () => void) => void;
      error?: (fn: (err: unknown) => void) => void;
      runtime?: {
        permission?: {
          requestAuthCode: (opts: {
            corpId: string;
            onSuccess: (res: { code?: string }) => void;
            onFail: (err: { errorMessage?: string; message?: string; errorCode?: string }) => void;
          }) => void;
        };
      };
      getAuthCode?: (opts: { corpId: string }) => Promise<{ code?: string }>;
      biz?: {
        navigation?: {
          close?: () => void;
        };
      };
    };
  }
}

function isDingTalkClient() {
  const ua = navigator.userAgent || '';
  if (/DingTalk/i.test(ua)) return true;
  try {
    return !!window.dd?.env?.platform;
  } catch {
    return false;
  }
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

/** 静默免登：requestAuthCode / getAuthCode，无需用户点击 */
function requestCorpAuthCode(corpId: string) {
  return new Promise<string>((resolve, reject) => {
    let settled = false;
    const ok = (code: string) => {
      if (settled) return;
      settled = true;
      resolve(code);
    };
    const fail = (err: unknown) => {
      if (settled) return;
      settled = true;
      const e = err as { errorMessage?: string; message?: string };
      reject(new Error(e?.errorMessage || e?.message || '免登授权失败'));
    };

    const tryLegacy = () => {
      if (!window.dd?.runtime?.permission?.requestAuthCode) {
        fail(new Error('当前环境不支持钉钉免登 JSAPI'));
        return;
      }
      window.dd.runtime.permission.requestAuthCode({
        corpId,
        onSuccess: (res) => {
          if (res?.code) ok(res.code);
          else fail(new Error('未获取到免登授权码'));
        },
        onFail: fail,
      });
    };

    const run = () => {
      if (settled) return;
      if (typeof window.dd?.getAuthCode === 'function') {
        window.dd
          .getAuthCode({ corpId })
          .then((res) => {
            if (res?.code) ok(res.code);
            else tryLegacy();
          })
          .catch(() => tryLegacy());
        return;
      }
      tryLegacy();
    };

    if (!window.dd?.ready) {
      fail(new Error('钉钉 JSAPI 未就绪'));
      return;
    }
    window.dd.ready(run);
  });
}

async function ssoWithForce(authCode: string, mode: 'corp' | 'oauth') {
  try {
    return await dingTalkSsoApi(authCode, mode, true);
  } catch (err) {
    const e = err as Error & { code?: number; data?: { forceTicket?: string } };
    if (e.code === CODE_LOGIN_CONFLICT) {
      const forceTicket =
        e.data && typeof e.data === 'object' && 'forceTicket' in e.data
          ? String((e.data as { forceTicket?: string }).forceTicket || '')
          : '';
      return dingTalkSsoApi(authCode, mode, true, forceTicket || undefined);
    }
    throw err;
  }
}

export default function DingTalkBridgePage() {
  const [status, setStatus] = useState<'loading' | 'pick' | 'done' | 'error'>('loading');
  const [tip, setTip] = useState('正在免登…');
  const [message, setMessage] = useState('');
  const [candidates, setCandidates] = useState<DingTalkIntentExecuteResult['candidates']>([]);
  const ran = useRef(false);

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

  const ensureSilentLogin = useCallback(async () => {
    if (getToken()) {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const oauthCode = params.get('authCode') || params.get('code');
    const ticket = params.get('ticket') || params.get('state') || '';
    const corpIdFromUrl = (params.get('corpId') || '').trim();

    if (oauthCode) {
      setTip('正在登录…');
      const res = await ssoWithForce(oauthCode, 'oauth');
      setToken(res.token);
      if (ticket) {
        window.history.replaceState(
          {},
          '',
          `${window.location.pathname}?ticket=${encodeURIComponent(ticket)}${
            corpIdFromUrl ? `&corpId=${encodeURIComponent(corpIdFromUrl)}` : ''
          }`,
        );
      }
      return;
    }

    setTip('正在免登…');
    const config = await getDingTalkLoginConfigApi();
    const corpId = corpIdFromUrl || (config.corpId || '').trim();
    if (!config?.enabled || !config.clientId) {
      throw new Error(config?.message || '钉钉登录未配置');
    }
    if (!corpId) {
      throw new Error('未配置企业 CorpId，无法免登。请在「钉钉应用配置」填写 CorpId');
    }
    if (!isDingTalkClient()) {
      throw new Error('请在钉钉客户端内打开此链接（当前非钉钉环境，无法静默免登）');
    }

    await loadDingTalkJsapi();
    // 清理可能残留的无效 token，避免干扰
    removeToken();
    const code = await requestCorpAuthCode(corpId);
    const res = await ssoWithForce(code, 'corp');
    setToken(res.token);
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
        await ensureSilentLogin();
        await runIntent(ticket);
      } catch (err) {
        setStatus('error');
        setMessage(err instanceof Error ? err.message : '处理失败');
      }
    })();
  }, [ensureSilentLogin, runIntent]);

  const onPick = async (inviteTicket: string) => {
    setStatus('loading');
    setTip('正在创建面试日程…');
    try {
      if (!getToken()) {
        await ensureSilentLogin();
      }
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
          isDingTalkClient() ? (
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
