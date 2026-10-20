import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Input, Result, Spin, Typography } from 'antd';
import { dingTalkSsoApi, getDingTalkLoginConfigApi } from '@/api/auth';
import { getLinkMonitorFillContextApi, submitLinkMonitorFillApi } from '@/api/robot';
import { getToken, removeToken, setToken } from '@/utils/auth';

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
      biz?: { navigation?: { close?: () => void } };
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

export default function LinkMonitorFillPage() {
  const { message } = App.useApp();
  const [status, setStatus] = useState<'loading' | 'form' | 'done' | 'error'>('loading');
  const [tip, setTip] = useState('正在免登…');
  const [messageText, setMessageText] = useState('');
  const [ticket, setTicket] = useState('');
  const [urlsText, setUrlsText] = useState('');
  const [draftUrls, setDraftUrls] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const ran = useRef(false);

  const ensureSilentLogin = useCallback(async (tk: string) => {
    if (getToken()) return;
    const params = new URLSearchParams(window.location.search);
    const oauthCode = params.get('authCode') || params.get('code');
    const corpIdFromUrl = (params.get('corpId') || '').trim();
    if (oauthCode) {
      const res = await dingTalkSsoApi(oauthCode, 'oauth');
      setToken(res.token);
      window.history.replaceState(
        {},
        '',
        `${window.location.pathname}?ticket=${encodeURIComponent(tk)}${
          corpIdFromUrl ? `&corpId=${encodeURIComponent(corpIdFromUrl)}` : ''
        }`,
      );
      return;
    }
    const config = await getDingTalkLoginConfigApi();
    const corpId = corpIdFromUrl || (config.corpId || '').trim();
    if (!config?.enabled || !config.clientId) {
      throw new Error(config?.message || '钉钉登录未配置');
    }
    if (!corpId) {
      throw new Error('缺少 CorpId，请从机器人卡片重新进入');
    }
    if (!isDingTalkClient()) {
      throw new Error('请在钉钉客户端内打开此链接');
    }
    await loadDingTalkJsapi();
    removeToken();
    const code = await requestCorpAuthCode(corpId);
    const res = await dingTalkSsoApi(code, 'corp');
    setToken(res.token);
  }, []);

  const loadContext = useCallback(async (tk: string) => {
    const ctx = await getLinkMonitorFillContextApi(tk);
    setDraftUrls((ctx.draftUrls || []).map(String));
    setStatus('form');
  }, []);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    const params = new URLSearchParams(window.location.search);
    const tk = params.get('ticket') || '';
    if (!tk) {
      setStatus('error');
      setMessageText('缺少票据，请从机器人卡片重新进入');
      return;
    }
    setTicket(tk);
    (async () => {
      try {
        setTip('正在免登…');
        await ensureSilentLogin(tk);
        setTip('加载中…');
        await loadContext(tk);
      } catch (err) {
        setStatus('error');
        setMessageText(err instanceof Error ? err.message : '打开失败');
      }
    })();
  }, [ensureSilentLogin, loadContext]);

  const submit = async (action: 'append' | 'commit' | 'cancel') => {
    if (!ticket) return;
    setSubmitting(true);
    try {
      const res = await submitLinkMonitorFillApi({
        ticket,
        urlsText,
        action,
      });
      if (action === 'cancel') {
        setMessageText(res.message || '已取消');
        setStatus('done');
        return;
      }
      if (action === 'append') {
        setDraftUrls((res.draftUrls || []).map(String));
        setUrlsText('');
        message.success(res.message || '已追加');
        return;
      }
      setMessageText(res.message || '已提交');
      setStatus('done');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (status === 'loading') {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 p-6'>
        <Spin tip={tip} />
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 p-6'>
        <Result
          status='error'
          title='无法打开填链页'
          subTitle={messageText}
        />
      </div>
    );
  }

  if (status === 'done') {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 p-6'>
        <Result
          status='success'
          title='完成'
          subTitle={messageText}
          extra={
            <Button
              type='primary'
              onClick={() => window.dd?.biz?.navigation?.close?.()}
            >
              关闭
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className='min-h-screen bg-slate-50 p-4'>
      <div className='mx-auto max-w-lg space-y-4 rounded-xl bg-white p-4 shadow-sm'>
        <Typography.Title
          level={4}
          className='!mb-1'
        >
          填写监控链接
        </Typography.Title>
        <Typography.Paragraph
          type='secondary'
          className='!mb-2'
        >
          可一次填写多条（每行一条）。群内添加的监控，告警会推回发起该监控的群。
        </Typography.Paragraph>
        {draftUrls.length > 0 ? (
          <div className='rounded-lg bg-slate-50 p-3 text-sm'>
            <div className='mb-1 font-medium'>已记下 {draftUrls.length} 条</div>
            <ul className='list-disc space-y-1 pl-5 break-all'>
              {draftUrls.map((u) => (
                <li key={u}>{u}</li>
              ))}
            </ul>
          </div>
        ) : null}
        <Input.TextArea
          rows={5}
          value={urlsText}
          onChange={(e) => setUrlsText(e.target.value)}
          placeholder={'https://example.com/a\nhttps://example.com/b'}
        />
        <div className='flex flex-wrap gap-2'>
          <Button
            type='primary'
            loading={submitting}
            onClick={() => submit('append')}
          >
            追加到草稿
          </Button>
          <Button
            type='primary'
            loading={submitting}
            onClick={() => submit('commit')}
          >
            完成提交
          </Button>
          <Button
            danger
            loading={submitting}
            onClick={() => submit('cancel')}
          >
            取消
          </Button>
        </div>
      </div>
    </div>
  );
}
