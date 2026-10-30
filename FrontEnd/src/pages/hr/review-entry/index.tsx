import { useEffect, useRef, useState } from 'react';
import { Result, Spin, Typography } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { dingTalkSsoApi, getDingTalkLoginConfigApi } from '@/api/auth';
import { removeToken, setToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';

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

/**
 * 钉钉催评落地：每次都走企业免登（不沿用扫码/密码 token，不参与账号互踢），再打开「我的面试」评价。
 * 链接形如 /hr/review-entry?inviteId=123&corpId=xxx
 */
export default function HrReviewEntryPage() {
  const [searchParams] = useSearchParams();
  const [tip, setTip] = useState('正在免登…');
  const [error, setError] = useState('');
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    const inviteId = (searchParams.get('inviteId') || '').trim();
    if (!inviteId) {
      setError('缺少邀约参数，请从钉钉通知重新进入');
      return;
    }
    (async () => {
      try {
        const corpIdFromUrl = (searchParams.get('corpId') || '').trim();
        const oauthCode = searchParams.get('authCode') || searchParams.get('code');
        // 丢掉已有登录态，避免把扫码会话带进评价页后触发互踢
        removeToken();
        let token = '';
        if (oauthCode) {
          setTip('正在登录…');
          const res = await dingTalkSsoApi(oauthCode, 'oauth');
          token = res.token;
        } else {
          setTip('正在免登…');
          const config = await getDingTalkLoginConfigApi();
          const corpId = corpIdFromUrl || (config.corpId || '').trim();
          if (!config?.enabled || !config.clientId) {
            throw new Error(config?.message || '钉钉登录未配置');
          }
          if (!corpId) {
            throw new Error('缺少 CorpId，请在「钉钉应用配置」填写企业 CorpId 后重发通知');
          }
          if (!isDingTalkClient()) {
            throw new Error('请在钉钉客户端内打开此链接');
          }
          await loadDingTalkJsapi();
          const code = await requestCorpAuthCode(corpId);
          const res = await dingTalkSsoApi(code, 'corp');
          token = res.token;
        }
        if (!token) {
          throw new Error('免登未返回登录凭证');
        }
        setToken(token);
        setTip('正在打开评价…');
        window.location.replace(withBase(`/hr/mine?inviteId=${encodeURIComponent(inviteId)}`));
      } catch (err) {
        setError(err instanceof Error ? err.message : '打开失败');
      }
    })();
  }, [searchParams]);

  if (error) {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
        <Result
          status='error'
          title='无法打开评价'
          subTitle={error}
        />
      </div>
    );
  }

  return (
    <div className='flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 px-6'>
      <Spin size='large' />
      <Typography.Text type='secondary'>{tip}</Typography.Text>
    </div>
  );
}
