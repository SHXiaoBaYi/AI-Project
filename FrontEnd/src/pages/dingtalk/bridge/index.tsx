import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Result, Spin, Typography } from 'antd';
import { getDingTalkLoginConfigApi, dingTalkSsoApi } from '@/api/auth';
import { executeDingTalkIntentApi, type DingTalkIntentExecuteResult } from '@/api/dingtalk';
import { CODE_LOGIN_CONFLICT } from '@/api/request';
import MeetingFormModal from '@/components/dingtalk/MeetingFormModal';
import ReportFormModal from '@/components/dingtalk/ReportFormModal';
import InviteFormModal, { type InviteFormValues } from '@/components/hr/InviteFormModal';
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

type FormKind = 'invite' | 'meeting' | 'report';

function formTitle(kind: FormKind) {
  if (kind === 'meeting') return '邀请开会';
  if (kind === 'report') return '安排工作汇报';
  return '发起面试邀约';
}

export default function DingTalkBridgePage() {
  const { message } = App.useApp();
  const [status, setStatus] = useState<'loading' | 'form' | 'done' | 'error'>('loading');
  const [tip, setTip] = useState('正在免登…');
  const [messageText, setMessageText] = useState('');
  const [formKind, setFormKind] = useState<FormKind>('invite');
  const [formHint, setFormHint] = useState('');
  const [targetUserId, setTargetUserId] = useState<number | undefined>();
  const [targetNickname, setTargetNickname] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  const [meetingOpen, setMeetingOpen] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const [inviteSeed, setInviteSeed] = useState<InviteFormValues | null>(null);
  const [slotSeed, setSlotSeed] = useState<{ startTime?: string; durationMin: number } | null>(null);
  const ran = useRef(false);

  const markDone = useCallback((text: string) => {
    setInviteOpen(false);
    setMeetingOpen(false);
    setReportOpen(false);
    setMessageText(text);
    setStatus('done');
  }, []);

  const openForms = useCallback((kind: FormKind, result: DingTalkIntentExecuteResult) => {
    const userId = result.targetUserId ? Number(result.targetUserId) : undefined;
    const who = result.targetNickname || '对方';
    const start = result.startTime || '';
    const duration = result.durationMin || 60;
    setFormKind(kind);
    setTargetUserId(userId);
    setTargetNickname(who);
    setSlotSeed({ startTime: start || undefined, durationMin: duration });
    setFormHint(`预填对方「${who}」、时间 ${start || '待选'}（${duration} 分）；请确认后提交。`);
    setStatus('form');
    if (kind === 'invite') {
      setInviteSeed({
        interviewerUserIds: userId ? [userId] : [],
        interviewAt: start || undefined,
        durationMin: duration,
        roundNo: 1,
      });
      setInviteOpen(true);
    } else if (kind === 'meeting') {
      setMeetingOpen(true);
    } else {
      setReportOpen(true);
    }
  }, []);

  const runIntent = useCallback(
    async (ticket: string) => {
      setTip('正在打开…');
      const result = await executeDingTalkIntentApi(ticket);
      if (result.status === 'open_invite_form') {
        openForms('invite', result);
        return;
      }
      if (result.status === 'open_meeting_form') {
        openForms('meeting', result);
        return;
      }
      if (result.status === 'open_report_form') {
        openForms('report', result);
        return;
      }
      setMessageText(result.message || '已完成');
      setStatus('done');
    },
    [openForms],
  );

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
      throw new Error(
        '当前读到的 CorpId 为空。请到「系统管理 → 钉钉应用配置」填写并保存企业 CorpId（开放平台企业信息里的，不是 ClientId），然后重新点机器人卡片',
      );
    }
    if (!isDingTalkClient()) {
      throw new Error('请在钉钉客户端内打开此链接（当前非钉钉环境，无法静默免登）');
    }

    await loadDingTalkJsapi();
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
      setMessageText('缺少意图参数，请从钉钉机器人卡片重新进入');
      return;
    }
    (async () => {
      try {
        await ensureSilentLogin();
        await runIntent(ticket);
      } catch (err) {
        setStatus('error');
        setMessageText(err instanceof Error ? err.message : '处理失败');
      }
    })();
  }, [ensureSilentLogin, runIntent]);

  if (status === 'loading') {
    return (
      <div className='flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 px-6'>
        <Spin size='large' />
        <Typography.Text type='secondary'>{tip}</Typography.Text>
      </div>
    );
  }

  if (status === 'form') {
    return (
      <div className='min-h-screen bg-slate-50 px-4 py-6'>
        <div className='mx-auto max-w-lg'>
          <Typography.Title
            level={4}
            className='!mb-2'
          >
            {formTitle(formKind)}
          </Typography.Title>
          {formHint ? <Typography.Paragraph type='secondary'>{formHint}</Typography.Paragraph> : null}
          <Button
            type='primary'
            block
            className='mb-4'
            onClick={() => {
              if (formKind === 'invite') setInviteOpen(true);
              else if (formKind === 'meeting') setMeetingOpen(true);
              else setReportOpen(true);
            }}
          >
            打开表单
          </Button>
        </div>

        <InviteFormModal
          open={inviteOpen}
          seed={inviteSeed}
          createDingTalkCalendar
          sendDingTalkWorkNotice={false}
          onOpenChange={setInviteOpen}
          onSuccess={() => {
            message.success('面试邀约已提交');
            markDone('面试邀约已提交');
          }}
        />
        <MeetingFormModal
          open={meetingOpen}
          targetUserId={targetUserId}
          targetNickname={targetNickname}
          seed={slotSeed}
          onOpenChange={setMeetingOpen}
          onSuccess={(tipText) => markDone(tipText)}
        />
        <ReportFormModal
          open={reportOpen}
          targetUserId={targetUserId}
          targetNickname={targetNickname}
          seed={slotSeed}
          onOpenChange={setReportOpen}
          onSuccess={(tipText) => markDone(tipText)}
        />
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
        <Result
          status='error'
          title='无法完成'
          subTitle={messageText}
        />
      </div>
    );
  }

  return (
    <div className='flex min-h-screen items-center justify-center bg-slate-50 px-4'>
      <Result
        status='success'
        title='已处理'
        subTitle={<span className='text-left whitespace-pre-wrap'>{messageText}</span>}
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
