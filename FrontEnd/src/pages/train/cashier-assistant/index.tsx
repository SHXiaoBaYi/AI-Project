import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Avatar, Button, Image, Input, Spin, Typography } from 'antd';
import {
  AudioOutlined,
  CustomerServiceOutlined,
  DislikeOutlined,
  LikeOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { dingTalkSsoApi, getDingTalkLoginConfigApi } from '@/api/auth';
import {
  askTrainAssistantApi,
  feedbackTrainAssistantApi,
  getTrainAssistantMetaApi,
  getTrainJsapiConfigApi,
  type TrainAssistantMeta,
  type TrainQaItem,
} from '@/api/train';
import { getToken, removeToken, setToken } from '@/utils/auth';
import { withBase } from '@/utils/basePath';
import { isLocalhostHost } from '@/utils/localhostAccess';
import { resolveUploadUrl } from '@/utils/uploadUrl';

/** 答疑配图：本机走 /uploads 代理，线上走公网 uploads 根 */
function resolveTrainImageUrl(url?: string) {
  if (!url) return '';
  if (/^(data|blob):/i.test(url)) return url;
  const path = url.startsWith('/uploads/') ? url : url.includes('/uploads/') ? url.slice(url.indexOf('/uploads/')) : '';
  if (path && isLocalhostHost()) return path;
  return resolveUploadUrl(url);
}

const IMAGE_PREVIEW = {
  movable: true,
  minScale: 0.5,
  maxScale: 8,
} as const;

function extractImgSrc(tag: string) {
  const m = tag.match(/\bsrc=["']([^"']+)["']/i);
  return m?.[1] ?? '';
}

function extractImgAlt(tag: string) {
  const m = tag.match(/\balt=["']([^"']*)["']/i);
  return m?.[1] ?? '配图';
}

/** 图文 HTML：配图可点击预览、放大缩小；纯文本段落仍按 HTML 渲染 */
function TrainQaRichHtml({ html }: { html: string }) {
  const parts = html.split(/(<img\b[^>]*>)/gi);
  const hasImg = parts.some((p) => /^<img\b/i.test(p));
  if (!hasImg) {
    return (
      <div
        className='train-qa-rich min-w-0 overflow-hidden break-words text-neutral-700'
        dangerouslySetInnerHTML={{ __html: html }}
      />
    );
  }
  return (
    <div className='train-qa-rich min-w-0 overflow-hidden break-words text-neutral-700 [&_.ant-image]:my-2.5 [&_.ant-image]:block [&_.ant-image]:max-w-full [&_.ant-image_img]:h-auto [&_.ant-image_img]:w-full [&_.ant-image_img]:max-w-full [&_.ant-image_img]:cursor-zoom-in [&_.ant-image_img]:rounded-md [&_.ant-image_img]:border [&_.ant-image_img]:border-neutral-200'>
      <Image.PreviewGroup preview={IMAGE_PREVIEW}>
        {parts.map((part, i) => {
          if (!/^<img\b/i.test(part)) {
            return part ? (
              <span
                key={i}
                dangerouslySetInnerHTML={{ __html: part }}
              />
            ) : null;
          }
          const src = resolveTrainImageUrl(extractImgSrc(part));
          if (!src) return null;
          return (
            <Image
              key={i}
              src={src}
              alt={extractImgAlt(part)}
            />
          );
        })}
      </Image.PreviewGroup>
    </div>
  );
}

function TrainQaImageList({ urls }: { urls: string[] }) {
  const srcs = urls.map((u) => resolveTrainImageUrl(u)).filter(Boolean);
  if (!srcs.length) return null;
  return (
    <Image.PreviewGroup preview={IMAGE_PREVIEW}>
      <div className='mt-2 min-w-0 space-y-3'>
        {srcs.map((src, imgIdx) => (
          <Image
            key={`${src}-${imgIdx}`}
            src={src}
            alt={`培训配图 ${imgIdx + 1}/${srcs.length}`}
            className='block h-auto w-full max-w-full cursor-zoom-in rounded-md border border-neutral-200 bg-white'
          />
        ))}
      </div>
    </Image.PreviewGroup>
  );
}

const DD_JSAPI = 'https://g.alicdn.com/dingding/dingtalk-jsapi/3.0.25/dingtalk.open.js';

type DdAudioApi = {
  startRecord?: (opts: { maxDuration?: number; onSuccess?: () => void; onFail?: (e: unknown) => void }) => void;
  stopRecord?: (opts: {
    onSuccess?: (res: { mediaId?: string; duration?: number }) => void;
    onFail?: (e: unknown) => void;
  }) => void;
  translateVoice?: (opts: {
    mediaId: string;
    duration: number;
    onSuccess?: (res: { content?: string }) => void;
    onFail?: (e: unknown) => void;
  }) => void;
};

declare global {
  interface Window {
    dd?: {
      env?: { platform?: string };
      ready: (fn: () => void) => void;
      error?: (fn: (err: unknown) => void) => void;
      config?: (opts: Record<string, unknown>) => void;
      runtime?: {
        permission?: {
          requestAuthCode: (opts: {
            corpId: string;
            onSuccess: (res: { code?: string }) => void;
            onFail: (err: { errorMessage?: string; message?: string }) => void;
          }) => void;
        };
      };
      getAuthCode?: (opts: { corpId: string }) => Promise<{ code?: string }>;
      device?: { audio?: DdAudioApi };
    };
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
    SpeechRecognition?: new () => SpeechRecognitionLike;
  }
}

type SpeechRecognitionLike = {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((ev: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null;
  onerror: ((ev: { error?: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
};

type ChatRole = 'user' | 'assistant' | 'system';

type ChatMessage = {
  id: string;
  role: ChatRole;
  text: string;
  /** 精确到秒，如 12:44:08 */
  time: string;
  items?: TrainQaItem[];
  needOld?: boolean;
  /** 对应的用户原问题（反馈用） */
  userQuestion?: string;
  docId?: number;
  versionId?: number;
  /** 本条已提交的反馈：accurate / inaccurate */
  feedback?: 'accurate' | 'inaccurate';
};

function nowTime() {
  return dayjs().format('HH:mm:ss');
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
        fail(new Error('当前环境不支持钉钉免登'));
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
      if (window.dd?.getAuthCode) {
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
    if (window.dd?.ready) window.dd.ready(run);
    else run();
  });
}

function uid() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/** 安卓/钉钉 WebView 常不支持 dvh，100dvh 失效后页面只跟内容等高，输入框无法贴底。用 visualViewport 锁全屏。 */
function useCashierViewportBox() {
  const [box, setBox] = useState({ top: 0, left: 0, width: '100%', height: '100%' });

  useEffect(() => {
    const sync = () => {
      const vv = window.visualViewport;
      if (vv) {
        setBox({
          top: vv.offsetTop,
          left: vv.offsetLeft,
          width: `${Math.round(vv.width)}px`,
          height: `${Math.round(vv.height)}px`,
        });
        return;
      }
      setBox({
        top: 0,
        left: 0,
        width: `${window.innerWidth}px`,
        height: `${window.innerHeight}px`,
      });
    };
    sync();
    const vv = window.visualViewport;
    vv?.addEventListener('resize', sync);
    vv?.addEventListener('scroll', sync);
    window.addEventListener('resize', sync);
    window.addEventListener('orientationchange', sync);
    return () => {
      vv?.removeEventListener('resize', sync);
      vv?.removeEventListener('scroll', sync);
      window.removeEventListener('resize', sync);
      window.removeEventListener('orientationchange', sync);
    };
  }, []);

  return box;
}

function lockCashierDocument() {
  const html = document.documentElement;
  const body = document.body;
  const root = document.getElementById('root');
  const meta = document.querySelector('meta[name="viewport"]');
  const prev = {
    htmlOverflow: html.style.overflow,
    htmlHeight: html.style.height,
    bodyOverflow: body.style.overflow,
    bodyHeight: body.style.height,
    bodyOverscroll: body.style.overscrollBehavior,
    rootHeight: root?.style.height ?? '',
    rootOverflow: root?.style.overflow ?? '',
    viewport: meta?.getAttribute('content') ?? '',
  };
  html.style.overflow = 'hidden';
  html.style.height = '100%';
  body.style.overflow = 'hidden';
  body.style.height = '100%';
  body.style.overscrollBehavior = 'none';
  if (root) {
    root.style.height = '100%';
    root.style.overflow = 'hidden';
  }
  meta?.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover');
  return () => {
    html.style.overflow = prev.htmlOverflow;
    html.style.height = prev.htmlHeight;
    body.style.overflow = prev.bodyOverflow;
    body.style.height = prev.bodyHeight;
    body.style.overscrollBehavior = prev.bodyOverscroll;
    if (root) {
      root.style.height = prev.rootHeight;
      root.style.overflow = prev.rootOverflow;
    }
    if (prev.viewport) meta?.setAttribute('content', prev.viewport);
  };
}

function configDingTalkJsapi(cfg: {
  agentId: string;
  corpId: string;
  timeStamp: string;
  nonceStr: string;
  signature: string;
}) {
  return new Promise<void>((resolve, reject) => {
    if (!window.dd?.config) {
      reject(new Error('钉钉 JSAPI config 不可用'));
      return;
    }
    window.dd.error?.((err) => reject(err instanceof Error ? err : new Error(String(err))));
    window.dd.config({
      agentId: cfg.agentId,
      corpId: cfg.corpId,
      timeStamp: cfg.timeStamp,
      nonceStr: cfg.nonceStr,
      signature: cfg.signature,
      type: 0,
      jsApiList: ['device.audio.startRecord', 'device.audio.stopRecord', 'device.audio.translateVoice'],
    });
    window.dd.ready(() => resolve());
  });
}

export default function CashierAssistantPage() {
  const { message } = App.useApp();
  const [boot, setBoot] = useState<'loading' | 'ready' | 'error'>('loading');
  const [bootTip, setBootTip] = useState('请使用钉钉扫码进入…');
  const [meta, setMeta] = useState<TrainAssistantMeta | null>(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [recording, setRecording] = useState(false);
  const [voiceReady, setVoiceReady] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const listRef = useRef<HTMLDivElement>(null);
  const lastQuestionRef = useRef('');
  const recordStartedAt = useRef(0);
  const webkitRecRef = useRef<SpeechRecognitionLike | null>(null);
  const ran = useRef(false);
  const viewportBox = useCashierViewportBox();
  const shellStyle = {
    top: viewportBox.top,
    left: viewportBox.left,
    width: viewportBox.width,
    height: viewportBox.height,
  };

  const push = useCallback((msg: Omit<ChatMessage, 'id' | 'time'> & { time?: string }) => {
    setMessages((prev) => [...prev, { ...msg, id: uid(), time: msg.time || nowTime() }]);
  }, []);

  useEffect(() => {
    const prev = document.title;
    document.title = '小巴依收银答疑小助手';
    const unlock = lockCashierDocument();
    return () => {
      document.title = prev;
      unlock();
    };
  }, []);

  useEffect(() => {
    requestAnimationFrame(() => {
      const el = listRef.current;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }, [messages, recording, sending]);

  /**
   * 登录策略：
   * - localhost / 127.0.0.1：本机联调，复用后台已登录 token，不强制钉钉
   * - 钉钉客户端：企业内免登（corp authCode）
   * - 其他环境：拒绝
   */
  const ensureCorpEmployeeLogin = useCallback(async () => {
    if (isLocalhostHost()) {
      setBootTip('本机联调模式：检查登录状态…');
      if (!getToken()) {
        throw new Error('本机联调请先打开 /login 登录后台，再访问本页（可带 corpId 参数，仅作展示）。');
      }
      const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
      setVoiceReady(!!SR);
      return;
    }

    if (!isDingTalkClient()) {
      throw new Error('请使用钉钉扫描「收银答疑」二维码进入。浏览器或其他 App 打开无法校验企业员工身份。');
    }
    setBootTip('正在校验企业员工身份…');
    await loadDingTalkJsapi();
    const params = new URLSearchParams(window.location.search);
    const corpIdFromUrl = (params.get('corpId') || '').trim();
    const config = await getDingTalkLoginConfigApi();
    const corpId = corpIdFromUrl || (config.corpId || '').trim();
    if (!config?.enabled || !config.clientId) {
      throw new Error(config?.message || '钉钉登录未配置，请联系管理员');
    }
    if (!corpId) {
      throw new Error('缺少企业 CorpId，无法确认是否为本企业员工');
    }
    // 每次进入都重新免登，避免沿用外部扫码/后台 token 绕过企业校验
    removeToken();
    const code = await requestCorpAuthCode(corpId);
    const res = await dingTalkSsoApi(code, 'corp', true);
    setToken(res.token);

    // 语音 JSAPI 鉴权（失败不阻断答疑，仅禁用语音）
    try {
      const signUrl = window.location.href.split('#')[0];
      const jsapi = await getTrainJsapiConfigApi(signUrl);
      await configDingTalkJsapi(jsapi);
      setVoiceReady(true);
    } catch {
      const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
      setVoiceReady(!!SR);
    }
  }, []);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    (async () => {
      try {
        await ensureCorpEmployeeLogin();
        setBootTip('加载培训资料…');
        const info = await getTrainAssistantMetaApi('cashier');
        setMeta(info);
        const localHint = isLocalhostHost() ? '（本机联调）' : '（仅本企业员工可用）';
        const welcome = info.hasDocument
          ? `你好，我是收银操作答疑小助手${localHint}。当前「${info.docTitle || '培训手册'}」最新版 ${info.latestVersionLabel || '—'}。可文字或语音提问。`
          : `你好，我是收银操作答疑小助手${localHint}。当前还没有上传培训文档，请联系管理员上传后再提问。`;
        setMessages([{ id: uid(), role: 'assistant', text: welcome, time: nowTime() }]);
        setBoot('ready');
      } catch (err) {
        setBoot('error');
        setBootTip(err instanceof Error ? err.message : '进入失败');
      }
    })();
  }, [ensureCorpEmployeeLogin]);

  const ask = async (question: string, searchOld = false) => {
    const q = question.trim();
    if (!q || sending) return;
    if (!getToken()) {
      message.error(isLocalhostHost() ? '登录已失效，请先到 /login 登录后再试' : '登录已失效，请重新用钉钉扫码进入');
      return;
    }
    setSending(true);
    lastQuestionRef.current = q;
    if (!searchOld) {
      push({ role: 'user', text: q });
      setInput('');
    }
    try {
      const res = await askTrainAssistantApi({ question: q, searchOld, category: 'cashier' });
      if (res.status === 'HIT') {
        push({
          role: 'assistant',
          text: res.message || '已找到相关问答',
          items: res.items || [],
          userQuestion: q,
          docId: res.docId,
          versionId: res.versionId,
        });
      } else if (res.status === 'NEED_OLD') {
        push({
          role: 'assistant',
          text: res.message || '暂时没有找到答案，是否检索旧版？',
          needOld: true,
          userQuestion: q,
          docId: res.docId,
          versionId: res.versionId,
        });
      } else {
        push({ role: 'assistant', text: res.message || '没有找到答案' });
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提问失败');
      push({ role: 'assistant', text: err instanceof Error ? err.message : '提问失败，请稍后再试' });
    } finally {
      setSending(false);
    }
  };

  const submitFeedback = async (msg: ChatMessage, accurate: boolean) => {
    if (!msg.items?.length || !msg.userQuestion || msg.feedback) return;
    const item = msg.items[0];
    try {
      const id = await feedbackTrainAssistantApi({
        userQuestion: msg.userQuestion,
        accurate,
        docId: msg.docId,
        versionId: msg.versionId,
        calibrateId: item.calibrateId,
        topicTitle: item.question,
        answer: item.answer,
        answerHtml: item.answerHtml,
        images: item.images,
      });
      setMessages((prev) =>
        prev.map((m) =>
          m.id === msg.id
            ? {
                ...m,
                feedback: accurate ? 'accurate' : 'inaccurate',
                items: m.items?.map((it, i) => (i === 0 ? { ...it, calibrateId: id || it.calibrateId } : it)),
              }
            : m,
        ),
      );
      message.success(accurate ? '已标记为准确，下次同问题将优先用此答案' : '已标记为不准，可在后台校准或重新识别');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '反馈失败');
    }
  };

  const stopDingTalkRecord = () =>
    new Promise<{ mediaId: string; duration: number }>((resolve, reject) => {
      const audio = window.dd?.device?.audio;
      if (!audio?.stopRecord) {
        reject(new Error('停止录音不可用'));
        return;
      }
      audio.stopRecord({
        onSuccess: (res) => {
          if (!res?.mediaId) {
            reject(new Error('未获取到录音'));
            return;
          }
          resolve({
            mediaId: res.mediaId,
            duration: Math.max(1, Number(res.duration) || Math.ceil((Date.now() - recordStartedAt.current) / 1000)),
          });
        },
        onFail: (e) => reject(e instanceof Error ? e : new Error('停止录音失败')),
      });
    });

  const translateDingTalkVoice = (mediaId: string, duration: number) =>
    new Promise<string>((resolve, reject) => {
      const audio = window.dd?.device?.audio;
      if (!audio?.translateVoice) {
        reject(new Error('语音转文字不可用'));
        return;
      }
      audio.translateVoice({
        mediaId,
        duration,
        onSuccess: (res) => resolve((res?.content || '').trim()),
        onFail: (e) => reject(e instanceof Error ? e : new Error('语音识别失败')),
      });
    });

  const startWebSpeech = () => {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      message.warning('当前环境不支持语音输入');
      return;
    }
    const rec = new SR();
    webkitRecRef.current = rec;
    rec.lang = 'zh-CN';
    rec.interimResults = false;
    rec.continuous = false;
    rec.onresult = (ev) => {
      const text = ev.results?.[0]?.[0]?.transcript || '';
      if (text.trim()) {
        setInput((prev) => (prev ? `${prev}${text}` : text));
        message.success('已识别语音');
      }
    };
    rec.onerror = () => {
      setRecording(false);
      message.error('语音识别失败');
    };
    rec.onend = () => setRecording(false);
    rec.start();
    setRecording(true);
  };

  const startVoice = async () => {
    if (sending || recording) return;
    const audio = window.dd?.device?.audio;
    if (voiceReady && audio?.startRecord) {
      try {
        await new Promise<void>((resolve, reject) => {
          audio.startRecord!({
            maxDuration: 60,
            onSuccess: () => resolve(),
            onFail: (e) => reject(e instanceof Error ? e : new Error('无法开始录音')),
          });
        });
        recordStartedAt.current = Date.now();
        setRecording(true);
        message.info('正在录音，再次点击结束');
        return;
      } catch {
        // fallthrough web speech
      }
    }
    startWebSpeech();
  };

  const stopVoice = async () => {
    if (!recording) return;
    if (webkitRecRef.current) {
      try {
        webkitRecRef.current.stop();
      } catch {
        // ignore
      }
      webkitRecRef.current = null;
      setRecording(false);
      return;
    }
    setRecording(false);
    try {
      const { mediaId, duration } = await stopDingTalkRecord();
      const text = await translateDingTalkVoice(mediaId, duration);
      if (!text) {
        message.warning('没有识别到内容，请再说一次或改用文字');
        return;
      }
      setInput((prev) => (prev ? `${prev}${text}` : text));
      message.success('已识别语音');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '语音识别失败');
    }
  };

  if (boot === 'loading') {
    return (
      <div
        className='fixed z-10 flex w-full max-w-full flex-col items-center justify-center gap-3 overflow-hidden bg-[#f0f4f8] px-6'
        style={shellStyle}
      >
        <Spin size='large' />
        <Typography.Text type='secondary'>{bootTip}</Typography.Text>
      </div>
    );
  }

  if (boot === 'error') {
    const local = isLocalhostHost();
    return (
      <div
        className='fixed z-10 flex w-full max-w-full flex-col items-center justify-center gap-3 overflow-hidden bg-[#f0f4f8] px-6 text-center'
        style={shellStyle}
      >
        <Typography.Title
          level={4}
          className='!mb-0'
        >
          无法进入答疑助手
        </Typography.Title>
        <Typography.Text type='secondary'>{bootTip}</Typography.Text>
        {local ? (
          <Button
            type='primary'
            onClick={() => {
              window.location.href = withBase('/login');
            }}
          >
            去登录后台
          </Button>
        ) : (
          <Typography.Paragraph
            type='secondary'
            className='!mb-0 max-w-sm text-xs'
          >
            请使用钉钉扫描管理后台「收银培训 → 培训文档」中的长期二维码进入；仅本企业员工可用。
          </Typography.Paragraph>
        )}
      </div>
    );
  }

  return (
    <div
      className='fixed z-10 flex w-full max-w-full flex-col overflow-hidden bg-[#eef2f6]'
      style={shellStyle}
    >
      <header className='flex w-full min-w-0 shrink-0 items-center justify-between border-b border-neutral-200 bg-white px-4 py-3'>
        <div className='min-w-0'>
          <div className='truncate text-base font-semibold text-neutral-900'>收银答疑小助手</div>
          <div className='truncate text-xs text-neutral-500'>
            {isLocalhostHost() ? '本机联调' : '本企业员工专用'}
            {meta?.hasDocument
              ? ` · 最新版 ${meta.latestVersionLabel || '—'}${meta.hasOlderVersions ? ' · 可检索旧版' : ''}`
              : ' · 暂无文档'}
          </div>
        </div>
      </header>

      <div
        ref={listRef}
        className='min-h-0 w-full min-w-0 flex-1 space-y-3 overflow-x-hidden overflow-y-auto px-3 py-4'
      >
        {messages.map((msg) => {
          const isUser = msg.role === 'user';
          return (
            <div
              key={msg.id}
              className={`flex w-full min-w-0 items-start gap-2 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
            >
              <Avatar
                size={36}
                className={`mt-0.5 shrink-0 ${isUser ? '!bg-[#1677ff]' : '!bg-[#13c2c2]'}`}
                icon={isUser ? <UserOutlined /> : <CustomerServiceOutlined />}
              />
              <div
                className={`flex max-w-[calc(100%-2.75rem)] min-w-0 flex-col ${isUser ? 'items-end' : 'items-start'}`}
              >
                <div
                  className={`max-w-full min-w-0 overflow-hidden rounded-2xl px-3.5 py-2.5 text-[15px] leading-relaxed break-words shadow-sm ${
                    isUser
                      ? 'rounded-br-md bg-[#1677ff] text-white'
                      : 'rounded-bl-md border border-neutral-100 bg-white text-neutral-800'
                  }`}
                >
                  <div className='[overflow-wrap:anywhere] break-words whitespace-pre-wrap'>{msg.text}</div>
                  {msg.items?.length ? (
                    <div className='mt-2 min-w-0 space-y-2'>
                      {msg.items.map((item, idx) => (
                        <div
                          key={`${msg.id}-${idx}`}
                          className='min-w-0 overflow-hidden rounded-xl border border-blue-100 bg-blue-50/70 px-3 py-2 text-sm text-neutral-800'
                        >
                          <div className='mb-1 font-medium break-words text-blue-700'>
                            Q{idx + 1}. {item.question}
                            {item.versionLabel ? (
                              <span className='ml-2 text-xs font-normal text-neutral-400'>{item.versionLabel}</span>
                            ) : null}
                          </div>
                          {item.answerHtml ? (
                            <div className='min-w-0'>
                              <div className='mb-0.5 text-neutral-500'>A.</div>
                              <TrainQaRichHtml html={item.answerHtml} />
                            </div>
                          ) : (
                            <>
                              <div className='[overflow-wrap:anywhere] break-words whitespace-pre-wrap text-neutral-700'>
                                A. {item.answer}
                              </div>
                              {item.images?.length ? <TrainQaImageList urls={item.images} /> : null}
                            </>
                          )}
                          {item.fromCalibrate ? (
                            <div className='mt-1 text-xs text-emerald-600'>来自人工校准库</div>
                          ) : null}
                        </div>
                      ))}
                      {msg.userQuestion && msg.items?.length ? (
                        <div className='mt-2 flex flex-wrap items-center gap-2'>
                          {msg.feedback ? (
                            <span className='text-xs text-neutral-400'>
                              已反馈：{msg.feedback === 'accurate' ? '准确' : '不准'}
                            </span>
                          ) : (
                            <>
                              <Button
                                size='small'
                                icon={<LikeOutlined />}
                                disabled={sending}
                                onClick={() => void submitFeedback(msg, true)}
                              >
                                准确
                              </Button>
                              <Button
                                size='small'
                                icon={<DislikeOutlined />}
                                disabled={sending}
                                onClick={() => void submitFeedback(msg, false)}
                              >
                                不准
                              </Button>
                            </>
                          )}
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                  {msg.needOld ? (
                    <div className='mt-2 flex flex-wrap gap-2'>
                      <Button
                        size='small'
                        type='primary'
                        loading={sending}
                        onClick={() => void ask(lastQuestionRef.current, true)}
                      >
                        检索旧版
                      </Button>
                      <Button
                        size='small'
                        disabled={sending}
                        onClick={() =>
                          push({ role: 'assistant', text: '好的，已取消旧版检索。你可以换个问法再试试。' })
                        }
                      >
                        不用了
                      </Button>
                    </div>
                  ) : null}
                </div>
                <div className='mt-1 px-1 text-[11px] text-neutral-400 tabular-nums'>{msg.time}</div>
              </div>
            </div>
          );
        })}
        {sending ? (
          <div className='flex w-full min-w-0 items-start gap-2'>
            <Avatar
              size={36}
              className='mt-0.5 shrink-0 !bg-[#13c2c2]'
              icon={<CustomerServiceOutlined />}
            />
            <div className='max-w-[calc(100%-2.75rem)] min-w-0 rounded-2xl rounded-bl-md border border-neutral-100 bg-white px-3 py-2 text-sm text-neutral-400 shadow-sm'>
              正在从培训文档检索…
            </div>
          </div>
        ) : null}
      </div>

      <div className='w-full min-w-0 shrink-0 border-t border-neutral-200 bg-white px-3 pt-2 pb-[max(8px,env(safe-area-inset-bottom,0px))]'>
        <div className='mx-auto flex w-full max-w-3xl min-w-0 items-end gap-2'>
          <Button
            shape='circle'
            size='large'
            className='shrink-0'
            type={recording ? 'primary' : 'default'}
            danger={recording}
            icon={<AudioOutlined />}
            disabled={sending || !meta?.hasDocument}
            onClick={() => void (recording ? stopVoice() : startVoice())}
            aria-label={recording ? '结束录音' : '语音输入'}
          />
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={recording ? '正在聆听…' : '输入或语音提问…'}
            autoSize={{ minRows: 1, maxRows: 4 }}
            disabled={sending || !meta?.hasDocument || recording}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                void ask(input);
              }
            }}
            className='!min-w-0 !flex-1 !rounded-xl'
          />
          <Button
            type='primary'
            shape='circle'
            size='large'
            className='shrink-0'
            icon={<SendOutlined />}
            loading={sending}
            disabled={!input.trim() || !meta?.hasDocument || recording}
            onClick={() => void ask(input)}
          />
        </div>
        <div className='pt-1 text-center text-[11px] text-neutral-400'>
          {recording ? '再次点击麦克风结束并转文字' : '钉钉企业员工专用 · 点麦克风可语音输入'}
        </div>
      </div>
    </div>
  );
}
