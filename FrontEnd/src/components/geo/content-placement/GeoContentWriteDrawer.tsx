import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Drawer, Input, Modal, Segmented, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { LakexEditorHost, type LakexEditorHostHandle } from '@/components/lakex/LakexEditorHost';
import { ensureLakexAntdCss, prefetchLakexAssets, removeLakexAntdCss } from '@/components/lakex/loadLakexAssets';
import {
  addGeoContentArticleOpinionApi,
  addGeoContentPlatformArticleApi,
  addGeoContentReviewAnnotationApi,
  startAiGenerateGeoContentArticleApi,
  streamAiGenerateJobApi,
  deleteGeoContentReviewAnnotationApi,
  getGeoAiProvidersApi,
  getGeoContentArticleBundleApi,
  getGeoContentReviewCompareApi,
  getGeoPlatformOptionsApi,
  decideGeoContentReviewApi,
  precheckGeoContentDraftApi,
  saveGeoContentDraftApi,
  saveGeoContentItemDraftApi,
  uploadGeoScreenshotApi,
} from '@/api/geo';
import {
  GeoReviewAnnotationReadonly,
  ReviewAnnotationList,
  SelectableReviewBody,
} from '@/components/geo/content-placement/GeoReviewAnnotations';
import { GeoArticleVersionDiff } from '@/components/geo/content-placement/GeoArticleVersionDiff';
import { GeoContentReviewCompareDrawer } from '@/components/geo/content-placement/GeoContentReviewCompareDrawer';
import { GEO_PLATFORM_TYPE_CONTENT } from '@/constants/geo';
import type {
  GeoContentArticle,
  GeoContentArticleBundle,
  GeoContentReviewAi,
  GeoContentReviewAnnotation,
  GeoContentReviewRound,
  GeoPipelineStage,
  GeoPlatform,
} from '@/types/geo';
import './geo-ai-generate-modal.css';

type ReviewPane = 'annotate' | 'body';

function roundLabel(round: GeoContentReviewRound, latestRoundNo?: number | null) {
  const stage = round.stageNo === 2 ? '二审' : round.stageNo === 1 ? '一审' : '';
  const base = `${stage ? `${stage}·` : ''}第${round.roundNo ?? '-'}轮`;
  if (latestRoundNo != null && round.roundNo === latestRoundNo) return `${base}（当前稿）`;
  return base;
}

/** 前端一键生成进度（与后台外网一次成稿流水线对齐） */
const AI_PIPELINE_STEPS = [
  { key: 'style', title: '加载风格' },
  { key: 'sell', title: '召回卖点' },
  { key: 'media', title: '召回素材' },
  { key: 'guard', title: '加载审稿口径' },
  { key: 'write', title: '撰写正文' },
  { key: 'brand', title: '品牌轻补' },
  { key: 'illustrate', title: '智能插图' },
  { key: 'save', title: '写入草稿' },
] as const;

const STATUS_LABEL: Record<string, { text: string; color: string }> = {
  drafting: { text: '草稿', color: 'default' },
  pending_review: { text: '待审核', color: 'processing' },
  revising: { text: '修改中', color: 'warning' },
  approved: { text: '已通过', color: 'success' },
  stage1_passed: { text: '一审已通过', color: 'success' },
  queue: { text: '待投放', color: 'cyan' },
  published: { text: '已发布', color: 'green' },
};

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';
type ActiveKey = 'shared' | number;

type Props = {
  open: boolean;
  placementId: number | null;
  /** write=撰写改稿；review=只读查看（待审核/审核记录） */
  mode: 'write' | 'review';
  /** 待审核时传入，用于在抽屉内通过/退回 */
  reviewId?: number | null;
  onClose: () => void;
};

function statusMeta(status?: string) {
  return STATUS_LABEL[status || 'drafting'] || STATUS_LABEL.drafting;
}

export function GeoContentWriteDrawer({ open, placementId, mode, reviewId = null, onClose }: Props) {
  const [loading, setLoading] = useState(false);
  const [editorReady, setEditorReady] = useState(false);
  const [bundle, setBundle] = useState<GeoContentArticleBundle | null>(null);
  const [active, setActive] = useState<ActiveKey>('shared');
  const [body, setBody] = useState('');
  const [contentTitle, setContentTitle] = useState('');
  const [saveState, setSaveState] = useState<SaveState>('idle');
  const [saveError, setSaveError] = useState('');
  const [opinion, setOpinion] = useState('');
  const [acting, setActing] = useState(false);
  const [platforms, setPlatforms] = useState<GeoPlatform[]>([]);
  const [aiOpen, setAiOpen] = useState(false);
  const [aiGenerating, setAiGenerating] = useState(false);
  const [aiStepIndex, setAiStepIndex] = useState(0);
  const [aiStages, setAiStages] = useState<GeoPipelineStage[]>([]);
  const [aiCurrentMessage, setAiCurrentMessage] = useState('');
  const [aiProvider, setAiProvider] = useState<string>();
  const [aiUserPrompt, setAiUserPrompt] = useState('');
  const [aiProviders, setAiProviders] = useState<
    { provider: string; label: string; model: string; available: boolean; hint?: string }[]
  >([]);
  /** 撰写正文阶段：弹窗内打字机预览（不写 Lake，成文后再覆盖编辑器） */
  const [aiDraftTitle, setAiDraftTitle] = useState('');
  const [aiDraftHtml, setAiDraftHtml] = useState('');
  const [aiElapsedSec, setAiElapsedSec] = useState(0);
  const aiDraftScrollRef = useRef<HTMLDivElement>(null);
  const aiStartedAtRef = useRef<number>(0);
  /** 成文后对照审稿库的草稿预检结果 */
  const [precheckOpen, setPrecheckOpen] = useState(false);
  const [precheckLoading, setPrecheckLoading] = useState(false);
  const [precheckResult, setPrecheckResult] = useState<GeoContentReviewAi | null>(null);
  const aiStreamAbortRef = useRef<AbortController | null>(null);
  const [editorEpoch, setEditorEpoch] = useState(0);
  const [reviewHints, setReviewHints] = useState<{
    roundLabel: string;
    contentBody: string;
    annotations: GeoContentReviewAnnotation[];
    reviewers: GeoContentReviewRound['reviewers'];
  } | null>(null);
  /** 审核进行中：可在原文上划词的批注列表 */
  const [liveAnnotations, setLiveAnnotations] = useState<GeoContentReviewAnnotation[]>([]);
  /** 审核主区：划词 / 版本对照（任务视角内嵌，不必再点顶栏） */
  const [reviewPane, setReviewPane] = useState<ReviewPane>('annotate');
  const [versionPair, setVersionPair] = useState<{
    left: GeoContentReviewRound;
    right: GeoContentReviewRound;
    latestRoundNo: number | null;
  } | null>(null);
  /** 完整多轮勾选对照（次要入口） */
  const [versionCompareOpen, setVersionCompareOpen] = useState(false);
  const [annoDraft, setAnnoDraft] = useState<{
    quoteText: string;
    quotePrefix: string;
    quoteSuffix: string;
    startOffset: number | null;
    endOffset: number | null;
  } | null>(null);
  const [annoComment, setAnnoComment] = useState('');
  const [annoSaving, setAnnoSaving] = useState(false);
  const versionRef = useRef(0);
  const timerRef = useRef<number | null>(null);
  const bodyRef = useRef('');
  const titleRef = useRef('');
  const flushingRef = useRef(false);
  const pendingRetryRef = useRef(false);
  const activeRef = useRef<ActiveKey>('shared');
  const editorHostRef = useRef<LakexEditorHostHandle | null>(null);
  const bundleRef = useRef<GeoContentArticleBundle | null>(null);

  bodyRef.current = body;
  titleRef.current = contentTitle;
  activeRef.current = active;
  bundleRef.current = bundle;

  const article = useMemo(
    () => (typeof active === 'number' ? bundle?.articles.find((row) => row.itemId === active) : undefined),
    [active, bundle],
  );
  const usingShared = active === 'shared' || article?.contentMode !== 'custom';
  const reviewingLive = mode === 'review' && reviewId != null;
  const readOnly =
    mode === 'review' ||
    (active !== 'shared' && article != null && !article.editable && article.contentMode === 'custom');
  const sharedEditable = mode === 'write' && (active === 'shared' || article?.contentMode !== 'custom');
  /** 审核划词优先用提审快照，否则用当前打开的正文 */
  const annotateBody = (reviewHints?.contentBody || body || '').trim();

  const clearTimer = () => {
    if (timerRef.current != null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  };

  const applyShared = (data: GeoContentArticleBundle) => {
    setContentTitle(data.shared.contentTitle || '');
    setBody(data.shared.contentBody || '');
    versionRef.current = data.shared.contentVersion ?? 0;
  };

  const applyArticle = (row: GeoContentArticle, data: GeoContentArticleBundle) => {
    if (row.contentMode === 'custom') {
      setContentTitle(row.contentTitle || '');
      setBody(row.contentBody || '');
      versionRef.current = row.contentVersion ?? 0;
      return;
    }
    setContentTitle(data.shared.contentTitle || '');
    setBody(data.shared.contentBody || '');
    versionRef.current = data.shared.contentVersion ?? 0;
  };

  const load = useCallback(async (id: number, prefer?: ActiveKey) => {
    setLoading(true);
    setEditorReady(false);
    setSaveState('idle');
    setSaveError('');
    try {
      const data = await getGeoContentArticleBundleApi(id);
      bundleRef.current = data;
      setBundle(data);
      const next = prefer ?? 'shared';
      setActive(next);
      if (next === 'shared') {
        applyShared(data);
      } else {
        const row = data.articles.find((item) => item.itemId === next);
        if (row) applyArticle(row, data);
        else applyShared(data);
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载文章失败');
      bundleRef.current = null;
      setBundle(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open || placementId == null) return;
    setOpinion('');
    setReviewHints(null);
    setLiveAnnotations([]);
    setVersionPair(null);
    setReviewPane('annotate');
    setAnnoDraft(null);
    setAnnoComment('');
    setEditorReady(false);
    void prefetchLakexAssets();
    void load(placementId, 'shared');
    if (mode === 'write') {
      void getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_CONTENT)
        .then(setPlatforms)
        .catch(() => setPlatforms([]));
    }
    void getGeoContentReviewCompareApi(placementId)
      .then((res) => {
        const rounds = res.rounds ?? [];
        const latestRoundNo = rounds.reduce((m, r) => Math.max(m, r.roundNo ?? 0), 0) || null;
        const focus =
          (reviewId != null && rounds.find((r) => r.reviewId === reviewId)) ||
          (res.currentReviewId != null && rounds.find((r) => r.reviewId === res.currentReviewId)) ||
          [...rounds]
            .reverse()
            .find((r) => (r.annotations ?? []).length > 0 || (r.reviewers ?? []).some((x) => x.comment)) ||
          rounds[rounds.length - 1] ||
          null;
        if (!focus) {
          setReviewHints(null);
          setLiveAnnotations([]);
          setVersionPair(null);
          return;
        }
        // 补齐最新一轮快照（与对照抽屉一致）
        const focusFilled: GeoContentReviewRound =
          focus.roundNo === latestRoundNo && res.currentDraft && !focus.hasSnapshot
            ? {
                ...focus,
                contentTitle: res.currentDraft.contentTitle || focus.contentTitle,
                contentBody: res.currentDraft.contentBody || focus.contentBody,
                contentFormat: res.currentDraft.contentFormat || focus.contentFormat,
                contentVersion: res.currentDraft.contentVersion ?? focus.contentVersion,
                hasSnapshot: !!(res.currentDraft.contentTitle || res.currentDraft.contentBody),
              }
            : focus;
        const prev = [...rounds]
          .filter((r) => r.reviewId != null && r.reviewId !== focus.reviewId)
          .filter((r) => (r.roundNo ?? 0) < (focus.roundNo ?? 0))
          .sort((a, b) => (a.roundNo ?? 0) - (b.roundNo ?? 0))
          .at(-1);
        if (prev) {
          setVersionPair({ left: prev, right: focusFilled, latestRoundNo });
          // 有上一轮改稿时，审核默认先看版本对照（任务视角最需要）
          if (mode === 'review') setReviewPane('body');
        } else {
          setVersionPair(null);
          setReviewPane('annotate');
        }
        const stage = focusFilled.stageNo === 2 ? '二审' : focusFilled.stageNo === 1 ? '一审' : '';
        const annos = focusFilled.annotations ?? [];
        setReviewHints({
          roundLabel: `${stage ? `${stage}·` : ''}第${focusFilled.roundNo ?? '-'}轮`,
          contentBody: focusFilled.contentBody || res.currentDraft?.contentBody || '',
          annotations: annos,
          reviewers: focusFilled.reviewers ?? [],
        });
        if (reviewId != null && focusFilled.reviewId === reviewId) {
          setLiveAnnotations(annos);
        } else if (reviewId != null) {
          const mine = rounds.find((r) => r.reviewId === reviewId);
          setLiveAnnotations(mine?.annotations ?? []);
        } else {
          setLiveAnnotations([]);
        }
      })
      .catch(() => {
        setReviewHints(null);
        setLiveAnnotations([]);
        setVersionPair(null);
      });
    return () => clearTimer();
  }, [open, placementId, mode, load, reviewId]);

  const persist = useCallback(
    async (opts?: { silent?: boolean }) => {
      if (placementId == null || !bundle || mode !== 'write') return null;
      if (flushingRef.current) {
        pendingRetryRef.current = true;
        return null;
      }
      const latest = bundleRef.current;
      const current = activeRef.current;
      const row = typeof current === 'number' ? latest?.articles.find((item) => item.itemId === current) : undefined;
      // 打开的是某个内容平台上的文章：只保存这篇，不写回统一稿。不看投放/发布状态。
      const onPlatform = row != null;
      if (onPlatform && row.contentMode === 'custom' && !row.editable) return null;
      const lake = editorHostRef.current?.getLake();
      if (lake != null) {
        bodyRef.current = lake;
      }
      if (onPlatform && row.contentMode !== 'custom') {
        const shared = latest?.shared;
        const sameTitle = (titleRef.current || '') === (shared?.contentTitle || '');
        const sameBody = (bodyRef.current || '') === (shared?.contentBody || '');
        if (sameTitle && sameBody) return null;
      }
      flushingRef.current = true;
      setSaveState('saving');
      setSaveError('');
      try {
        if (!onPlatform) {
          const next = await saveGeoContentDraftApi({
            placementId,
            contentTitle: titleRef.current,
            contentBody: bodyRef.current,
            contentVersion: versionRef.current,
          });
          versionRef.current = next.contentVersion ?? versionRef.current + 1;
          setBundle((prev) => {
            const nextBundle = prev ? { ...prev, shared: next } : prev;
            bundleRef.current = nextBundle;
            return nextBundle;
          });
        } else if (row) {
          const next = await saveGeoContentItemDraftApi({
            itemId: row.itemId,
            contentMode: 'custom',
            contentTitle: titleRef.current,
            contentBody: bodyRef.current,
            contentVersion: row.contentMode === 'custom' ? versionRef.current : (row.contentVersion ?? 0),
          });
          versionRef.current = next.contentVersion ?? versionRef.current + 1;
          setBundle((prev) => {
            const nextBundle = prev
              ? { ...prev, articles: prev.articles.map((item) => (item.itemId === next.itemId ? next : item)) }
              : prev;
            bundleRef.current = nextBundle;
            return nextBundle;
          });
        }
        setSaveState('saved');
        if (!opts?.silent) message.success('已保存');
        return true;
      } catch (e) {
        const msg = e instanceof Error ? e.message : '保存失败';
        setSaveState('error');
        setSaveError(msg);
        if (!opts?.silent) message.error(msg);
        return null;
      } finally {
        flushingRef.current = false;
        if (pendingRetryRef.current) {
          pendingRetryRef.current = false;
          window.setTimeout(() => {
            void persist({ silent: true });
          }, 0);
        }
      }
    },
    [bundle, mode, placementId],
  );

  const scheduleSave = useCallback(() => {
    if (mode !== 'write') return;
    if (active !== 'shared' && article?.contentMode === 'custom' && !article.editable) return;
    setSaveState('dirty');
    clearTimer();
    timerRef.current = window.setTimeout(() => {
      void persist({ silent: true });
    }, 1200);
  }, [active, article?.contentMode, article?.editable, mode, persist]);

  const selectTarget = async (next: ActiveKey) => {
    if (next === active) return;
    clearTimer();
    if (mode === 'write') await persist({ silent: true });
    setEditorReady(false);
    setActive(next);
    const latest = bundleRef.current;
    if (!latest) return;
    if (next === 'shared') applyShared(latest);
    else {
      const row = latest.articles.find((item) => item.itemId === next);
      if (row) applyArticle(row, latest);
    }
    setSaveState('idle');
  };

  const handleClose = async () => {
    clearTimer();
    if (mode === 'write') await persist({ silent: true });
    onClose();
  };

  const replaceArticle = (next: GeoContentArticle) => {
    setBundle((prev) => {
      const nextBundle = prev
        ? { ...prev, articles: prev.articles.map((item) => (item.itemId === next.itemId ? next : item)) }
        : prev;
      bundleRef.current = nextBundle;
      return nextBundle;
    });
    if (active === next.itemId) {
      if (next.contentMode === 'custom') {
        setContentTitle(next.contentTitle || '');
        setBody(next.contentBody || '');
        versionRef.current = next.contentVersion ?? 0;
      } else if (bundleRef.current) {
        setContentTitle(bundleRef.current.shared.contentTitle || '');
        setBody(bundleRef.current.shared.contentBody || '');
        versionRef.current = bundleRef.current.shared.contentVersion ?? 0;
      }
    }
  };

  const switchMode = async (contentMode: 'shared' | 'custom') => {
    if (!article) return;
    const lake = editorHostRef.current?.getLake();
    if (lake != null) {
      bodyRef.current = lake;
    }
    setActing(true);
    try {
      const next = await saveGeoContentItemDraftApi({
        itemId: article.itemId,
        contentMode,
        contentTitle: contentMode === 'custom' ? titleRef.current : undefined,
        contentBody: contentMode === 'custom' ? bodyRef.current : undefined,
        contentVersion: article.contentMode === 'custom' ? versionRef.current : article.contentVersion,
      });
      replaceArticle(next);
      message.success(contentMode === 'custom' ? '已改为该平台独立稿' : '已改回统一稿');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '切换失败');
    } finally {
      setActing(false);
    }
  };

  const stopAiStream = () => {
    aiStreamAbortRef.current?.abort();
    aiStreamAbortRef.current = null;
  };

  useEffect(() => {
    if (!aiGenerating || (!aiDraftTitle && !aiDraftHtml)) return;
    const el = aiDraftScrollRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, [aiGenerating, aiDraftTitle, aiDraftHtml]);

  useEffect(() => {
    if (!aiGenerating) {
      setAiElapsedSec(0);
      return;
    }
    aiStartedAtRef.current = Date.now();
    setAiElapsedSec(0);
    const timer = window.setInterval(() => {
      setAiElapsedSec(Math.floor((Date.now() - aiStartedAtRef.current) / 1000));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [aiGenerating]);

  const applyAiJobProgress = (job: {
    currentCode?: string;
    currentMessage?: string;
    stages?: GeoPipelineStage[];
    draftTitle?: string;
    draftHtml?: string;
  }) => {
    if (job.stages?.length) {
      setAiStages(job.stages);
    }
    if (job.currentMessage) {
      setAiCurrentMessage(job.currentMessage);
    }
    const code = job.currentCode;
    if (code) {
      const idx = AI_PIPELINE_STEPS.findIndex((s) => s.key === code);
      if (idx >= 0) setAiStepIndex(idx);
    }
    // 撰写阶段只更新弹窗打字机预览，不碰 Lake；成文后再一次性覆盖编辑器
    if (job.draftTitle) {
      setAiDraftTitle(job.draftTitle);
    }
    if (job.draftHtml) {
      setAiDraftHtml(job.draftHtml);
    }
  };

  const closeAiGenerateModal = () => {
    stopAiStream();
    setAiOpen(false);
    // 弹窗关掉后恢复 Lake 工具栏所需的 antd v4 样式
    void ensureLakexAntdCss();
  };

  const openAiGenerate = () => {
    if (mode !== 'write' || placementId == null) return;
    if (readOnly && !sharedEditable) {
      message.warning('当前稿件不可编辑');
      return;
    }
    void getGeoAiProvidersApi()
      .then((list) => {
        const ready = (list ?? []).filter((p) => p.available && p.provider !== 'local');
        setAiProviders(ready);
        setAiProvider(ready.find((p) => p.provider === 'tongyi')?.provider || ready[0]?.provider);
        setAiStepIndex(0);
        setAiStages([]);
        setAiCurrentMessage('');
        setAiDraftTitle('');
        setAiDraftHtml('');
        // 卸掉 Lake 注入的 antd v4，避免污染弹窗内 Ant Design 6 控件
        removeLakexAntdCss();
        setAiOpen(true);
      })
      .catch((e) => message.error(e instanceof Error ? e.message : '加载 AI 模型失败'));
  };

  const runAiGenerate = async () => {
    if (placementId == null || !aiProvider) {
      message.warning('请选择 AI 模型');
      return;
    }
    const hasContent = Boolean(titleRef.current?.trim() || bodyRef.current?.trim());
    if (hasContent) {
      const ok = await new Promise<boolean>((resolve) => {
        Modal.confirm({
          title: '将覆盖当前标题与正文',
          content: '生成完成后会一次性写入当前统一稿或独立稿，是否继续？',
          okText: '继续生成',
          cancelText: '取消',
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!ok) return;
    }
    clearTimer();
    stopAiStream();
    const abort = new AbortController();
    aiStreamAbortRef.current = abort;
    setAiGenerating(true);
    setAiStepIndex(0);
    setAiStages([]);
    setAiCurrentMessage('已提交任务，等待开始…');
    setAiDraftTitle('');
    setAiDraftHtml('');
    setPrecheckResult(null);
    // 弹窗保持打开：撰写正文阶段 SSE 打字机预览；Lake 等成文后再覆盖
    try {
      const itemId = active !== 'shared' && article ? article.itemId : undefined;
      const started = await startAiGenerateGeoContentArticleApi({
        placementId,
        itemId,
        provider: aiProvider,
        userPrompt: aiUserPrompt.trim() || undefined,
      });
      applyAiJobProgress(started);
      const job = await streamAiGenerateJobApi(
        started.jobId,
        {
          onProgress: (j) => applyAiJobProgress(j),
          onDone: (j) => applyAiJobProgress(j),
          onError: (msg) => {
            setAiCurrentMessage(msg);
          },
        },
        abort.signal,
      );
      if (abort.signal.aborted) {
        return;
      }
      if (job.status === 'FAILED') {
        throw new Error(job.currentMessage || 'AI 生成失败');
      }
      const res = job.result;
      if (!res) {
        throw new Error('生成完成但未返回结果');
      }
      setAiStepIndex(AI_PIPELINE_STEPS.length - 1);
      if (res.stages?.length) {
        setAiStages(res.stages);
      }
      // 成文：一次性覆盖 Lake 编辑器
      setContentTitle(res.contentTitle || '');
      titleRef.current = res.contentTitle || '';
      setBody(res.contentBody || '');
      bodyRef.current = res.contentBody || '';
      versionRef.current = res.contentVersion ?? versionRef.current + 1;
      editorHostRef.current?.setLake(res.contentBody || '');
      setEditorEpoch((n) => n + 1);
      setEditorReady(false);
      setSaveState('saved');
      closeAiGenerateModal();
      setAiDraftTitle('');
      setAiDraftHtml('');
      const extra = [
        res.usedImageCount != null ? `插图 ${res.usedImageCount}` : null,
        res.qaRounds != null && res.qaRounds > 0 ? `品牌轻补 ${res.qaRounds} 次` : null,
      ]
        .filter(Boolean)
        .join(' · ');
      message.success(
        `已生成并写入${itemId ? '独立稿' : '统一稿'}${res.sourceAiModel ? `（${res.sourceAiModel}）` : ''}${extra ? ` · ${extra}` : ''}`,
      );

      // 成文后立刻对照审稿知识库预检（不落审核轮次）；提审后仍会再跑正式 AI 预审
      if (placementId != null && (res.contentBody || '').trim()) {
        setAiCurrentMessage('生成完成，正在对照审稿库预检…');
        setPrecheckLoading(true);
        setPrecheckOpen(true);
        try {
          const check = await precheckGeoContentDraftApi({
            placementId,
            title: res.contentTitle || undefined,
            body: res.contentBody || '',
          });
          setPrecheckResult(check);
          if (check.status === 'done' && check.suggestion === 'reject') {
            message.warning(check.summary || '预检发现风险点，请对照下方列表改稿');
          } else if (check.status === 'done' && check.suggestion === 'approve') {
            message.success(check.summary || '预检通过');
          } else if (check.status === 'failed') {
            message.warning(check.errorMsg || '草稿预检失败，可稍后提审时再跑 AI 预审');
          }
        } catch (e) {
          message.warning(e instanceof Error ? e.message : '草稿预检失败，可稍后提审时再跑 AI 预审');
          setPrecheckOpen(false);
        } finally {
          setPrecheckLoading(false);
          setAiCurrentMessage('生成完成');
        }
      }

      if (placementId != null) {
        void load(placementId, active);
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        return;
      }
      message.error(e instanceof Error ? e.message : 'AI 生成失败');
    } finally {
      stopAiStream();
      setAiGenerating(false);
    }
  };

  const usedPlatforms = new Set((bundle?.articles ?? []).map((row) => row.platformName));
  const platformOptions = platforms
    .filter((item) => item.platformName && !usedPlatforms.has(item.platformName))
    .map((item) => ({ label: item.platformName, value: item.platformName }));

  const headerStatus = article ? statusMeta(article.contentStatus) : null;
  const saveHint =
    saveState === 'saving'
      ? '保存中…'
      : saveState === 'saved'
        ? '已自动保存'
        : saveState === 'dirty'
          ? '有未保存更改'
          : saveState === 'error'
            ? `保存失败：${saveError}`
            : '';

  return (
    <>
      <Drawer
        open={open}
        width='95vw'
        placement='right'
        maskClosable={false}
        keyboard={false}
        destroyOnHidden
        onClose={() => {
          void handleClose();
        }}
        styles={{
          body: { padding: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' },
          header: { paddingBlock: 12 },
        }}
        title={
          <div className='flex min-w-0 flex-col gap-0.5'>
            <span className='font-medium'>{mode === 'review' ? '审核文章' : '撰写文章'}</span>
            {contentTitle ? (
              <Typography.Paragraph
                className='!mb-0 max-w-[min(720px,70vw)] text-sm font-semibold text-neutral-800'
                copyable={{ text: contentTitle, tooltips: ['复制标题', '已复制'] }}
                ellipsis={{ rows: 1, tooltip: contentTitle }}
              >
                {contentTitle}
              </Typography.Paragraph>
            ) : null}
            <span className='truncate text-xs font-normal text-neutral-500'>
              {bundle?.topicName || '话题'} · {bundle?.targetQuestion || '目标问题'}
            </span>
          </div>
        }
        extra={
          <Space>
            <Typography.Text
              type={saveState === 'error' ? 'danger' : 'secondary'}
              className='text-xs'
            >
              {saveHint}
            </Typography.Text>
            {mode === 'write' ? (
              <>
                <Button
                  loading={aiGenerating}
                  disabled={readOnly && !sharedEditable}
                  onClick={openAiGenerate}
                >
                  AI 生成
                </Button>
                <Button
                  type='primary'
                  loading={saveState === 'saving'}
                  onClick={() => void persist()}
                >
                  保存
                </Button>
              </>
            ) : null}
            {mode === 'review' && reviewId != null ? (
              <>
                <Button
                  type='primary'
                  loading={acting}
                  onClick={() => {
                    void (async () => {
                      setActing(true);
                      try {
                        await decideGeoContentReviewApi({
                          reviewId,
                          action: 'approve',
                          comment: opinion.trim() || undefined,
                        });
                        message.success('已通过');
                        onClose();
                      } catch (e) {
                        message.error(e instanceof Error ? e.message : '审核失败');
                      } finally {
                        setActing(false);
                      }
                    })();
                  }}
                >
                  通过
                </Button>
                <Button
                  loading={acting}
                  onClick={() => {
                    void (async () => {
                      if (!opinion.trim() && liveAnnotations.length === 0) {
                        message.warning('退回修改时请填写意见，或先在原文划词批注');
                        return;
                      }
                      setActing(true);
                      try {
                        await decideGeoContentReviewApi({
                          reviewId,
                          action: 'reject',
                          comment: opinion.trim() || undefined,
                        });
                        message.success('已退回');
                        onClose();
                      } catch (e) {
                        message.error(e instanceof Error ? e.message : '退回失败');
                      } finally {
                        setActing(false);
                      }
                    })();
                  }}
                >
                  退回修改
                </Button>
              </>
            ) : null}
            <Button onClick={() => void handleClose()}>关闭</Button>
          </Space>
        }
      >
        {loading || !bundle ? (
          <div className='flex flex-1 items-center justify-center py-24'>
            <Spin tip='加载文章…' />
          </div>
        ) : (
          <div className='flex min-h-0 flex-1'>
            <aside className='flex w-64 shrink-0 flex-col border-r border-neutral-100'>
              <div className='border-b border-neutral-100 px-3 py-2 text-xs text-neutral-500'>
                同一问题在各内容平台默认发同一篇稿，需要时可以改成独立稿。每篇文章单独审核。
              </div>
              <button
                type='button'
                className={`border-b border-neutral-100 px-3 py-2 text-left ${active === 'shared' ? 'bg-blue-50' : 'bg-white'}`}
                onClick={() => void selectTarget('shared')}
              >
                <div className='text-sm font-medium'>统一稿</div>
                <div className='text-xs text-neutral-400'>各平台默认正文</div>
              </button>
              <div className='min-h-0 flex-1 overflow-auto'>
                {bundle.articles.length === 0 ? (
                  <p className='px-3 py-4 text-xs text-neutral-400'>
                    {mode === 'write'
                      ? '还没有内容平台。先在下方添加要发布的平台。'
                      : '还没有平台文章，撰写人添加发布平台后才能审核。'}
                  </p>
                ) : (
                  bundle.articles.map((row) => {
                    const meta = statusMeta(row.contentStatus);
                    return (
                      <button
                        key={row.itemId}
                        type='button'
                        className={`block w-full border-b border-neutral-100 px-3 py-2 text-left ${
                          active === row.itemId ? 'bg-blue-50' : 'bg-white'
                        }`}
                        onClick={() => void selectTarget(row.itemId)}
                      >
                        <div className='flex items-center justify-between gap-2'>
                          <span className='truncate text-sm'>{row.platformName}</span>
                          <Tag
                            color={meta.color}
                            className='mr-0'
                          >
                            {meta.text}
                          </Tag>
                        </div>
                        <div className='text-xs text-neutral-400'>
                          {row.contentMode === 'custom' ? '独立稿' : '沿用统一稿'}
                          {row.contentForm ? ` · ${row.contentForm}` : ''}
                        </div>
                      </button>
                    );
                  })
                )}
              </div>
              {mode === 'write' ? (
                <div className='border-t border-neutral-100 p-2'>
                  <Select
                    key={bundle.articles.length}
                    className='w-full'
                    placeholder='添加发布平台'
                    options={platformOptions}
                    showSearch
                    optionFilterProp='label'
                    onChange={(platformName: string) => {
                      if (!placementId || !platformName) return;
                      void (async () => {
                        try {
                          const created = await addGeoContentPlatformArticleApi({ placementId, platformName });
                          setBundle((prev) => {
                            const nextBundle = prev ? { ...prev, articles: [...prev.articles, created] } : prev;
                            bundleRef.current = nextBundle;
                            return nextBundle;
                          });
                          message.success(`已添加 ${platformName}`);
                          setActive(created.itemId);
                          if (bundleRef.current) applyArticle(created, bundleRef.current);
                        } catch (e) {
                          message.error(e instanceof Error ? e.message : '添加平台失败');
                        }
                      })();
                    }}
                  />
                </div>
              ) : null}
            </aside>
            <section className='flex min-w-0 flex-1 flex-col'>
              <div className='flex flex-wrap items-center gap-2 border-b border-neutral-100 px-4 py-2'>
                {headerStatus ? <Tag color={headerStatus.color}>{headerStatus.text}</Tag> : <Tag>统一稿</Tag>}
                {article ? (
                  <>
                    <Tag>{article.contentMode === 'custom' ? '独立稿' : '沿用统一稿'}</Tag>
                    <span className='text-xs text-neutral-400'>本平台的文章只保存在这里，不会写进统一稿</span>
                  </>
                ) : (
                  <span className='text-xs text-neutral-400'>改这里会同步到所有「沿用统一稿」的平台</span>
                )}
                {mode === 'write' && article?.editable ? (
                  article.contentMode === 'custom' ? (
                    <Button
                      size='small'
                      loading={acting}
                      onClick={() => void switchMode('shared')}
                    >
                      改回统一稿
                    </Button>
                  ) : (
                    <Button
                      size='small'
                      loading={acting}
                      onClick={() => void switchMode('custom')}
                    >
                      改为独立稿
                    </Button>
                  )
                ) : null}
                {mode === 'write' ? (
                  <span className='text-xs text-neutral-400'>
                    保存后在「我的撰写记录」发起一审/二审（串行，每阶段 1 人）
                  </span>
                ) : null}
                {reviewingLive ? (
                  <>
                    <Segmented
                      size='small'
                      value={reviewPane}
                      onChange={(v) => setReviewPane(v as ReviewPane)}
                      options={[
                        {
                          label: versionPair ? '版本对照' : '版本对照（无上一轮）',
                          value: 'body',
                          disabled: !versionPair,
                        },
                        {
                          label: `划词审核${liveAnnotations.length > 0 ? ` · ${liveAnnotations.length}` : ''}`,
                          value: 'annotate',
                        },
                      ]}
                    />
                    {versionPair ? (
                      <Button
                        size='small'
                        type='link'
                        className='!px-1'
                        onClick={() => setVersionCompareOpen(true)}
                      >
                        选其他轮次
                      </Button>
                    ) : null}
                  </>
                ) : null}
              </div>
              <div className='border-b border-neutral-100 bg-neutral-50/80 px-4 py-3'>
                <div className='mb-1 text-xs font-medium tracking-wide text-neutral-500'>文章标题</div>
                {mode === 'review' || (readOnly && !sharedEditable) ? (
                  <Typography.Paragraph
                    className='!mb-0 text-base font-semibold text-neutral-900'
                    copyable={contentTitle ? { text: contentTitle, tooltips: ['复制标题', '已复制'] } : false}
                    ellipsis={{ rows: 2, tooltip: contentTitle || undefined }}
                  >
                    {contentTitle || '（无标题）'}
                  </Typography.Paragraph>
                ) : (
                  <Input
                    placeholder='请输入文章标题'
                    autoComplete='off'
                    size='large'
                    className='!font-semibold'
                    value={contentTitle}
                    onChange={(e) => {
                      setContentTitle(e.target.value);
                      titleRef.current = e.target.value;
                      scheduleSave();
                    }}
                  />
                )}
              </div>
              {reviewingLive && reviewPane === 'body' && versionPair ? (
                <GeoArticleVersionDiff
                  leftLabel={roundLabel(versionPair.left, versionPair.latestRoundNo)}
                  rightLabel={roundLabel(versionPair.right, versionPair.latestRoundNo)}
                  leftTitle={versionPair.left.contentTitle}
                  rightTitle={versionPair.right.contentTitle}
                  leftBody={versionPair.left.contentBody}
                  rightBody={versionPair.right.contentBody}
                />
              ) : reviewingLive ? (
                <div className='grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(0,1fr)_260px]'>
                  <div className='min-h-0 border-b border-neutral-100 lg:border-r lg:border-b-0'>
                    {annotateBody ? (
                      <SelectableReviewBody
                        contentKey={`review-${reviewId}-${annotateBody.length}`}
                        contentBody={annotateBody}
                        enabled
                        annotations={liveAnnotations}
                        editableReviewId={reviewId}
                        onAnnotationsChange={setLiveAnnotations}
                        onAdd={(d) => {
                          setAnnoDraft(d);
                          setAnnoComment('');
                        }}
                      />
                    ) : (
                      <div className='p-4 text-sm text-amber-700'>暂无原文快照，无法划词（请确认已提审并保存正文）</div>
                    )}
                  </div>
                  <aside className='flex min-h-0 flex-col overflow-auto px-3 py-2'>
                    <div className='mb-2 text-xs text-neutral-500'>
                      批注列表{liveAnnotations.length > 0 ? `（${liveAnnotations.length}）` : ''}
                      ；左侧 Lake 原文可划选，高亮处点数字查看
                      {!versionPair ? ' · 首次提审暂无上一轮可对照' : ''}
                    </div>
                    <ReviewAnnotationList
                      annotations={liveAnnotations}
                      editableReviewId={reviewId}
                      onDelete={(id) => {
                        void (async () => {
                          try {
                            await deleteGeoContentReviewAnnotationApi(id);
                            setLiveAnnotations((prev) => prev.filter((a) => a.id !== id));
                            message.success('已删除');
                          } catch (e) {
                            message.error(e instanceof Error ? e.message : '删除失败');
                          }
                        })();
                      }}
                      variant='panel'
                    />
                  </aside>
                </div>
              ) : (
                <div className='grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(0,1fr)_280px]'>
                  <div className='relative min-h-0 border-b border-neutral-100 lg:border-r lg:border-b-0'>
                    {!editorReady && (
                      <div className='absolute inset-0 z-10 flex items-center justify-center bg-white/70'>
                        <Spin tip='加载编辑器…' />
                      </div>
                    )}
                    <LakexEditorHost
                      ref={editorHostRef}
                      key={`${placementId}-${active}-${usingShared ? 'shared' : 'custom'}-${readOnly && !sharedEditable ? 'ro' : 'rw'}-${editorEpoch}`}
                      value={body}
                      readOnly={readOnly && !sharedEditable}
                      onChange={(lake) => {
                        setBody(lake);
                        bodyRef.current = lake;
                        scheduleSave();
                      }}
                      onReady={() => setEditorReady(true)}
                      onError={(err) => message.error(err.message)}
                      uploadImage={async (file) => {
                        const res = await uploadGeoScreenshotApi(file);
                        return res.url;
                      }}
                    />
                  </div>
                  <aside className='flex min-h-0 flex-col overflow-auto bg-neutral-50/60 px-3 py-2'>
                    {reviewHints &&
                    ((reviewHints.annotations?.length ?? 0) > 0 ||
                      (reviewHints.reviewers ?? []).some((r) => r.comment?.trim())) ? (
                      <div className='mb-3'>
                        <div className='mb-2 text-xs font-medium text-amber-800'>
                          {reviewHints.roundLabel} 审核反馈
                          {mode === 'write' ? '（改稿请对照）' : ''}
                        </div>
                        {(reviewHints.reviewers ?? [])
                          .filter((r) => r.comment?.trim())
                          .map((r) => (
                            <div
                              key={`${r.userId}-${r.actedAt || r.comment}`}
                              className='mb-2 rounded border border-neutral-100 bg-white px-3 py-2 text-sm'
                            >
                              <span className='text-xs text-neutral-400'>{r.userName || r.userId} · 总意见</span>
                              <div className='text-neutral-800'>{r.comment}</div>
                            </div>
                          ))}
                        {(reviewHints.annotations?.length ?? 0) > 0 ? (
                          <div className='mt-1'>
                            <div className='mb-1 text-xs font-medium text-amber-800'>划词批注</div>
                            <GeoReviewAnnotationReadonly
                              annotations={reviewHints.annotations}
                              contentBody={reviewHints.contentBody}
                              showOriginal={false}
                            />
                          </div>
                        ) : null}
                      </div>
                    ) : (
                      <p className='mb-3 text-xs text-neutral-400'>暂无审核/批注记录</p>
                    )}
                    {article ? (
                      <div className='mt-auto border-t border-neutral-100 pt-2'>
                        <div className='mb-1 text-xs font-medium text-neutral-500'>内部意见</div>
                        {(article.opinions ?? []).length === 0 ? (
                          <p className='mb-2 text-xs text-neutral-400'>还没有意见</p>
                        ) : (
                          <ul className='mb-2 max-h-40 space-y-1 overflow-auto'>
                            {(article.opinions ?? []).map((note) => (
                              <li
                                key={note.id}
                                className='text-xs text-neutral-700'
                              >
                                <span className='text-neutral-400'>
                                  {note.authorName || '内部'} {note.createTime || ''}
                                </span>
                                <span className='ml-2'>{note.opinion}</span>
                              </li>
                            ))}
                          </ul>
                        )}
                        <Space.Compact className='w-full'>
                          <Input
                            placeholder='填写内部意见'
                            value={opinion}
                            onChange={(e) => setOpinion(e.target.value)}
                          />
                          <Button
                            loading={acting}
                            onClick={() => {
                              const text = opinion.trim();
                              if (!text) {
                                message.warning('请填写意见');
                                return;
                              }
                              void (async () => {
                                setActing(true);
                                try {
                                  const next = await addGeoContentArticleOpinionApi({
                                    itemId: article.itemId,
                                    opinion: text,
                                  });
                                  replaceArticle(next);
                                  setOpinion('');
                                  message.success('已记录意见');
                                } catch (e) {
                                  message.error(e instanceof Error ? e.message : '保存意见失败');
                                } finally {
                                  setActing(false);
                                }
                              })();
                            }}
                          >
                            提交意见
                          </Button>
                        </Space.Compact>
                      </div>
                    ) : null}
                  </aside>
                </div>
              )}
              {reviewingLive ? (
                <div className='border-t border-neutral-100 px-4 py-2'>
                  <div className='mb-1 text-xs text-neutral-500'>
                    总意见
                    {opinion.trim() || liveAnnotations.length > 0 ? '' : '（退回时必填；或先在原文划词批注）'}
                    {liveAnnotations.length > 0 ? ` · 已批注 ${liveAnnotations.length} 处` : ''}
                  </div>
                  <Input.TextArea
                    rows={2}
                    placeholder='可写总意见；退回时也可只靠划词批注'
                    value={opinion}
                    onChange={(e) => setOpinion(e.target.value)}
                  />
                </div>
              ) : null}
            </section>
          </div>
        )}
        <Modal
          title='添加划词批注'
          open={annoDraft != null}
          confirmLoading={annoSaving}
          onCancel={() => {
            setAnnoDraft(null);
            setAnnoComment('');
          }}
          onOk={() => {
            void (async () => {
              if (reviewId == null || !annoDraft) return;
              const text = annoComment.trim();
              if (!text) {
                message.warning('请填写批注内容');
                return;
              }
              setAnnoSaving(true);
              try {
                const created = await addGeoContentReviewAnnotationApi({
                  reviewId,
                  quoteText: annoDraft.quoteText,
                  commentText: text,
                  quotePrefix: annoDraft.quotePrefix,
                  quoteSuffix: annoDraft.quoteSuffix,
                  startOffset: annoDraft.startOffset,
                  endOffset: annoDraft.endOffset,
                });
                setLiveAnnotations((prev) => [...prev, created]);
                setAnnoDraft(null);
                setAnnoComment('');
                message.success('已添加划词批注');
              } catch (e) {
                message.error(e instanceof Error ? e.message : '添加批注失败');
              } finally {
                setAnnoSaving(false);
              }
            })();
          }}
          okText='保存批注'
          destroyOnHidden
        >
          {annoDraft ? (
            <Space
              direction='vertical'
              className='w-full'
              size={12}
            >
              <blockquote className='mb-0 border-l-2 border-amber-300 bg-amber-50/60 px-3 py-2 text-sm text-neutral-700'>
                「{annoDraft.quoteText}」
              </blockquote>
              <Input.TextArea
                rows={3}
                value={annoComment}
                placeholder='说明这里要怎么改'
                onChange={(e) => setAnnoComment(e.target.value)}
                maxLength={2000}
                showCount
              />
            </Space>
          ) : null}
        </Modal>
        <Modal
          title='草稿 AI 预检（审稿知识库）'
          open={precheckOpen}
          onCancel={() => setPrecheckOpen(false)}
          onOk={() => setPrecheckOpen(false)}
          okText='知道了'
          cancelButtonProps={{ style: { display: 'none' } }}
          destroyOnHidden
          width={560}
        >
          {precheckLoading ? (
            <div className='flex items-center gap-2 py-6 text-sm text-neutral-600'>
              <Spin size='small' />
              对照审稿知识库检查杜撰/口径中…
            </div>
          ) : precheckResult?.status === 'done' ? (
            <div className='flex flex-col gap-2'>
              <div className='flex flex-wrap items-center gap-2'>
                <Tag color={precheckResult.suggestion === 'approve' ? 'success' : 'warning'}>
                  {precheckResult.suggestion === 'approve' ? '建议可通过' : '建议改后再提审'}
                </Tag>
                {precheckResult.score != null ? (
                  <span className='text-xs text-neutral-500'>{precheckResult.score} 分</span>
                ) : null}
              </div>
              {precheckResult.summary ? (
                <p className='mb-0 text-sm text-neutral-700'>{precheckResult.summary}</p>
              ) : null}
              {(precheckResult.issues?.length ?? 0) > 0 ? (
                <ul className='m-0 max-h-64 list-none space-y-1.5 overflow-auto p-0'>
                  {precheckResult.issues!.map((issue, idx) => (
                    <li
                      key={`${issue.quoteText}-${idx}`}
                      className='rounded border border-neutral-100 bg-neutral-50 px-2 py-1.5 text-xs'
                    >
                      <div className='mb-0.5 flex items-center gap-1'>
                        <Tag
                          color={issue.level === 'high' ? 'error' : issue.level === 'low' ? 'default' : 'warning'}
                          className='mr-0'
                        >
                          {issue.level || 'mid'}
                        </Tag>
                        <span className='truncate text-neutral-500'>「{issue.quoteText}」</span>
                      </div>
                      <div className='text-neutral-800'>{issue.commentText}</div>
                    </li>
                  ))}
                </ul>
              ) : (
                <div className='text-xs text-neutral-400'>未检出需划词的问题</div>
              )}
              <p className='mb-0 text-xs text-neutral-400'>
                正式提审后仍会再跑一轮 AI 预审；本结果不落库、不代替人工审核。
              </p>
            </div>
          ) : (
            <div className='text-sm text-neutral-600'>
              {precheckResult?.errorMsg || precheckResult?.summary || '暂无预检结果'}
            </div>
          )}
        </Modal>
        <Modal
          title='AI 一键生成'
          open={aiOpen}
          rootClassName='geo-ai-generate-modal'
          onCancel={() => {
            if (aiGenerating) {
              stopAiStream();
              setAiGenerating(false);
              setAiCurrentMessage('已停止预览（后台任务可能仍在跑完）');
              return;
            }
            closeAiGenerateModal();
          }}
          onOk={() => void runAiGenerate()}
          okText={aiGenerating ? '生成中…' : '开始生成'}
          cancelText={aiGenerating ? '停止预览' : '取消'}
          confirmLoading={aiGenerating}
          okButtonProps={{ disabled: aiGenerating }}
          destroyOnHidden
          width={aiGenerating ? 780 : 640}
        >
          <p className='mb-3 text-sm text-neutral-500'>
            「撰写正文」阶段经 SSE 在本弹窗打字机预览；成文后一次性写入
            {active !== 'shared' && article?.contentMode === 'custom' ? '当前独立稿' : '统一稿'}的 Lake
            编辑器，并自动对照审稿知识库预检。
          </p>
          <div className='mb-3 rounded bg-neutral-50 px-3 py-2 text-xs text-neutral-600'>
            <div>
              <span className='text-neutral-400'>话题：</span>
              {bundle?.topicName || '-'}
            </div>
            <div className='mt-1'>
              <span className='text-neutral-400'>目标问题：</span>
              {bundle?.targetQuestion || '-'}
            </div>
          </div>
          {!aiGenerating ? (
            <>
              <div className='geo-ai-generate-field'>
                <div className='geo-ai-generate-label'>选择模型</div>
                {/* 不用 antd Select：Lake 的 antd v4 会把箭头挤到左侧；选项不多，用自绘列表更稳 */}
                <div className='flex flex-col gap-2'>
                  {aiProviders.map((p) => {
                    const label = p.model ? `${p.label}（${p.model}）` : p.label;
                    const active = aiProvider === p.provider;
                    return (
                      <button
                        key={p.provider}
                        type='button'
                        className={`w-full rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                          active
                            ? 'border-blue-500 bg-blue-50 text-blue-900'
                            : 'border-neutral-200 bg-white text-neutral-800 hover:border-neutral-300'
                        }`}
                        onClick={() => setAiProvider(p.provider)}
                      >
                        <span className='flex items-center justify-between gap-2'>
                          <span className='min-w-0 truncate'>{label}</span>
                          {active ? <span className='shrink-0 text-xs text-blue-600'>已选</span> : null}
                        </span>
                      </button>
                    );
                  })}
                </div>
                {aiProviders.length === 0 ? (
                  <p className='mt-2 mb-0 text-xs text-amber-600'>
                    暂无可用云厂商，请到「系统管理 → AI模型配置」填写 API Key。
                  </p>
                ) : null}
              </div>
              <div className='geo-ai-generate-field'>
                <div className='geo-ai-generate-label'>写作提示词（可选）</div>
                <Input.TextArea
                  value={aiUserPrompt}
                  onChange={(e) => setAiUserPrompt(e.target.value)}
                  placeholder='例如：偏科普口吻，突出有机双认证与牧场自有；约 1000 字；开头先回答目标问题'
                  autoSize={{ minRows: 3, maxRows: 8 }}
                  maxLength={2000}
                />
                <div className='mt-1 text-right text-xs text-neutral-400'>{aiUserPrompt.length} / 2000</div>
              </div>
            </>
          ) : (
            <div className='flex flex-col gap-3'>
              <div className='rounded-lg border border-sky-100 bg-sky-50/70 px-3 py-2.5'>
                <div className='mb-2 flex items-start justify-between gap-2'>
                  <div className='flex min-w-0 items-start gap-2 text-sm text-sky-900'>
                    <Spin
                      size='small'
                      className='mt-0.5'
                    />
                    <span className='min-w-0 leading-5'>
                      {aiCurrentMessage || AI_PIPELINE_STEPS[aiStepIndex]?.title || '进行中…'}
                    </span>
                  </div>
                  <span className='shrink-0 text-xs text-sky-600/80'>{aiElapsedSec}s</span>
                </div>
                <div className='flex flex-wrap gap-1.5'>
                  {AI_PIPELINE_STEPS.map((s, idx) => {
                    const real = aiStages.find((x) => x.code === s.key);
                    const done = idx < aiStepIndex || real?.status === 'done';
                    const current = idx === aiStepIndex;
                    return (
                      <Tag
                        key={s.key}
                        color={done ? 'success' : current ? 'processing' : 'default'}
                        className='mr-0'
                      >
                        {s.title}
                      </Tag>
                    );
                  })}
                </div>
              </div>
              <div>
                <div className='mb-1.5 flex items-center justify-between gap-2'>
                  <span className='text-xs font-medium text-neutral-600'>撰写正文 · 打字机预览</span>
                  <span className='text-xs text-neutral-400'>成文后写入编辑器</span>
                </div>
                <div
                  ref={aiDraftScrollRef}
                  className='max-h-[min(420px,50vh)] min-h-[180px] overflow-auto rounded-lg border border-neutral-200 bg-white px-3 py-3'
                >
                  {aiDraftTitle || aiDraftHtml ? (
                    <>
                      {aiDraftTitle ? (
                        <h3 className='mt-0 mb-2 text-base font-semibold text-neutral-900'>{aiDraftTitle}</h3>
                      ) : null}
                      {aiDraftHtml ? (
                        <div
                          className='prose prose-sm prose-headings:text-neutral-900 prose-p:my-2 max-w-none text-neutral-800'
                          dangerouslySetInnerHTML={{ __html: aiDraftHtml }}
                        />
                      ) : (
                        <p className='mb-0 text-sm text-neutral-400'>标题已出，正文生成中…</p>
                      )}
                    </>
                  ) : (
                    <div className='flex h-full min-h-[140px] flex-col items-center justify-center gap-2 px-4 text-center'>
                      <Spin size='small' />
                      <p className='mb-0 text-sm text-neutral-600'>
                        {aiStepIndex < AI_PIPELINE_STEPS.findIndex((s) => s.key === 'write')
                          ? '准备中：加载风格 / 卖点 / 审稿口径…'
                          : aiCurrentMessage?.includes('深度思考') || aiCurrentMessage?.includes('已请求模型')
                            ? '模型正在深度思考，正文会在思考结束后开始逐字出现'
                            : '等待模型输出正文…'}
                      </p>
                      <p className='mb-0 text-xs text-neutral-400'>
                        已等待 {aiElapsedSec} 秒{aiElapsedSec >= 15 ? ' · 深度思考通常需要数十秒，请稍候' : ''}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}
        </Modal>
      </Drawer>

      <GeoContentReviewCompareDrawer
        open={versionCompareOpen}
        placementId={placementId}
        focusReviewId={reviewId}
        onClose={() => setVersionCompareOpen(false)}
      />
    </>
  );
}
