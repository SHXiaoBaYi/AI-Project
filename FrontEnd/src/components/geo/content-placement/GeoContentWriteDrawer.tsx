import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Drawer, Input, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { LakexEditorHost, type LakexEditorHostHandle } from '@/components/lakex/LakexEditorHost';
import {
  addGeoContentArticleOpinionApi,
  addGeoContentPlatformArticleApi,
  aiGenerateGeoContentArticleApi,
  getGeoAiProvidersApi,
  getGeoContentArticleBundleApi,
  getGeoContentReviewCompareApi,
  getGeoPlatformOptionsApi,
  decideGeoContentReviewApi,
  saveGeoContentDraftApi,
  saveGeoContentItemDraftApi,
  uploadGeoScreenshotApi,
} from '@/api/geo';
import { GeoReviewAnnotationReadonly } from '@/components/geo/content-placement/GeoReviewAnnotations';
import { GEO_PLATFORM_TYPE_CONTENT } from '@/constants/geo';
import type {
  GeoContentArticle,
  GeoContentArticleBundle,
  GeoContentReviewAnnotation,
  GeoContentReviewRound,
  GeoPlatform,
} from '@/types/geo';

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
  const [aiProvider, setAiProvider] = useState<string>();
  const [aiUserPrompt, setAiUserPrompt] = useState('');
  const [aiProviders, setAiProviders] = useState<
    { provider: string; label: string; model: string; available: boolean; hint?: string }[]
  >([]);
  const [editorEpoch, setEditorEpoch] = useState(0);
  const [reviewHints, setReviewHints] = useState<{
    roundLabel: string;
    contentBody: string;
    annotations: GeoContentReviewAnnotation[];
    reviewers: GeoContentReviewRound['reviewers'];
  } | null>(null);
  const versionRef = useRef(0);
  const timerRef = useRef<number | null>(null);
  const bodyRef = useRef('');
  const titleRef = useRef('');
  const flushingRef = useRef(false);
  const pendingRetryRef = useRef(false);
  const activeRef = useRef<ActiveKey>('shared');
  const editorHostRef = useRef<LakexEditorHostHandle | null>(null);

  bodyRef.current = body;
  titleRef.current = contentTitle;
  activeRef.current = active;

  const article = useMemo(
    () => (typeof active === 'number' ? bundle?.articles.find((row) => row.itemId === active) : undefined),
    [active, bundle],
  );
  const usingShared = active === 'shared' || article?.contentMode !== 'custom';
  const readOnly =
    mode === 'review' ||
    (active !== 'shared' && article != null && !article.editable && article.contentMode === 'custom');
  const sharedEditable = mode === 'write' && (active === 'shared' || article?.contentMode !== 'custom');

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
      setBundle(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!open || placementId == null) return;
    setOpinion('');
    setReviewHints(null);
    void load(placementId, 'shared');
    if (mode === 'write') {
      void getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_CONTENT)
        .then(setPlatforms)
        .catch(() => setPlatforms([]));
    }
    void getGeoContentReviewCompareApi(placementId)
      .then((res) => {
        const rounds = res.rounds ?? [];
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
          return;
        }
        const stage = focus.stageNo === 2 ? '二审' : focus.stageNo === 1 ? '一审' : '';
        setReviewHints({
          roundLabel: `${stage ? `${stage}·` : ''}第${focus.roundNo ?? '-'}轮`,
          contentBody: focus.contentBody || res.currentDraft?.contentBody || '',
          annotations: focus.annotations ?? [],
          reviewers: focus.reviewers ?? [],
        });
      })
      .catch(() => setReviewHints(null));
    return () => clearTimer();
  }, [open, placementId, mode, load, reviewId]);

  const persist = useCallback(
    async (opts?: { silent?: boolean }) => {
      if (placementId == null || !bundle || mode !== 'write') return null;
      if (flushingRef.current) {
        pendingRetryRef.current = true;
        return null;
      }
      const current = activeRef.current;
      const row = typeof current === 'number' ? bundle.articles.find((item) => item.itemId === current) : undefined;
      const editingShared = current === 'shared' || row?.contentMode !== 'custom';
      if (!editingShared && row && !row.editable) return null;
      const lake = editorHostRef.current?.getLake();
      if (lake != null) {
        bodyRef.current = lake;
      }
      flushingRef.current = true;
      setSaveState('saving');
      setSaveError('');
      try {
        if (editingShared) {
          const next = await saveGeoContentDraftApi({
            placementId,
            contentTitle: titleRef.current,
            contentBody: bodyRef.current,
            contentVersion: versionRef.current,
          });
          versionRef.current = next.contentVersion ?? versionRef.current + 1;
          setBundle((prev) => (prev ? { ...prev, shared: next } : prev));
        } else if (row) {
          const next = await saveGeoContentItemDraftApi({
            itemId: row.itemId,
            contentMode: 'custom',
            contentTitle: titleRef.current,
            contentBody: bodyRef.current,
            contentVersion: versionRef.current,
          });
          versionRef.current = next.contentVersion ?? versionRef.current + 1;
          setBundle((prev) =>
            prev
              ? { ...prev, articles: prev.articles.map((item) => (item.itemId === next.itemId ? next : item)) }
              : prev,
          );
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
    if (!bundle) return;
    if (next === 'shared') applyShared(bundle);
    else {
      const row = bundle.articles.find((item) => item.itemId === next);
      if (row) applyArticle(row, bundle);
    }
    setSaveState('idle');
  };

  const handleClose = async () => {
    clearTimer();
    if (mode === 'write') await persist({ silent: true });
    onClose();
  };

  const replaceArticle = (next: GeoContentArticle) => {
    setBundle((prev) =>
      prev ? { ...prev, articles: prev.articles.map((item) => (item.itemId === next.itemId ? next : item)) } : prev,
    );
    if (active === next.itemId) {
      if (next.contentMode === 'custom') {
        setContentTitle(next.contentTitle || '');
        setBody(next.contentBody || '');
        versionRef.current = next.contentVersion ?? 0;
      } else if (bundle) {
        setContentTitle(bundle.shared.contentTitle || '');
        setBody(bundle.shared.contentBody || '');
        versionRef.current = bundle.shared.contentVersion ?? 0;
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
        contentVersion: article.contentVersion,
      });
      replaceArticle(next);
      message.success(contentMode === 'custom' ? '已改为该平台独立稿' : '已改回统一稿');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '切换失败');
    } finally {
      setActing(false);
    }
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
          content: 'AI 生成会写入当前编辑中的统一稿或独立稿，是否继续？',
          okText: '继续生成',
          cancelText: '取消',
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!ok) return;
    }
    clearTimer();
    setAiGenerating(true);
    try {
      const itemId = active !== 'shared' && article?.contentMode === 'custom' ? article.itemId : undefined;
      const res = await aiGenerateGeoContentArticleApi({
        placementId,
        itemId,
        provider: aiProvider,
        userPrompt: aiUserPrompt.trim() || undefined,
      });
      setContentTitle(res.contentTitle || '');
      titleRef.current = res.contentTitle || '';
      setBody(res.contentBody || '');
      bodyRef.current = res.contentBody || '';
      versionRef.current = res.contentVersion ?? versionRef.current + 1;
      setEditorEpoch((n) => n + 1);
      setEditorReady(false);
      setSaveState('saved');
      setAiOpen(false);
      message.success(
        `已生成并写入${itemId ? '独立稿' : '统一稿'}${res.sourceAiModel ? `（${res.sourceAiModel}）` : ''}`,
      );
      if (placementId != null) {
        void load(placementId, active);
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : 'AI 生成失败');
    } finally {
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
        <div className='flex min-w-0 flex-col'>
          <span className='font-medium'>{mode === 'review' ? '审核文章' : '撰写文章'}</span>
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
                    if (!opinion.trim()) {
                      message.warning('退回修改时请填写意见');
                      return;
                    }
                    setActing(true);
                    try {
                      await decideGeoContentReviewApi({
                        reviewId,
                        action: 'reject',
                        comment: opinion.trim(),
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
                        setBundle((prev) => (prev ? { ...prev, articles: [...prev.articles, created] } : prev));
                        message.success(`已添加 ${platformName}`);
                        setActive(created.itemId);
                        applyArticle(created, bundle);
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
                <Tag>{article.contentMode === 'custom' ? '独立稿' : '沿用统一稿'}</Tag>
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
            </div>
            <div className='border-b border-neutral-100 px-4 py-2'>
              <Input
                placeholder='文章标题'
                autoComplete='off'
                value={contentTitle}
                disabled={readOnly && !sharedEditable}
                onChange={(e) => {
                  setContentTitle(e.target.value);
                  titleRef.current = e.target.value;
                  scheduleSave();
                }}
              />
            </div>
            <div className='relative min-h-0 flex-1'>
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
            {reviewHints &&
            ((reviewHints.annotations?.length ?? 0) > 0 ||
              (reviewHints.reviewers ?? []).some((r) => r.comment?.trim())) ? (
              <div className='max-h-56 overflow-auto border-t border-amber-100 bg-amber-50/40 px-4 py-2'>
                <div className='mb-1 text-xs font-medium text-amber-800'>
                  {reviewHints.roundLabel} 审核反馈
                  {mode === 'write' ? '（改稿时请对照）' : ''}
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
                  <>
                    <div className='mb-1 text-xs font-medium text-amber-800'>划词批注 · 点原文数字查看</div>
                    <GeoReviewAnnotationReadonly
                      annotations={reviewHints.annotations}
                      contentBody={reviewHints.contentBody}
                    />
                  </>
                ) : null}
              </div>
            ) : null}
            {mode === 'review' && reviewId != null ? (
              <div className='border-t border-neutral-100 px-4 py-2'>
                <div className='mb-1 text-xs font-medium text-neutral-500'>
                  总意见（退回时必填；划词批注请到「文章对照 / 划词审核」）
                </div>
                <Input.TextArea
                  rows={2}
                  placeholder='填写审核意见'
                  value={opinion}
                  onChange={(e) => setOpinion(e.target.value)}
                />
              </div>
            ) : article ? (
              <div className='max-h-48 overflow-auto border-t border-neutral-100 px-4 py-2'>
                <div className='mb-1 text-xs font-medium text-neutral-500'>内部意见</div>
                {(article.opinions ?? []).length === 0 ? (
                  <p className='mb-2 text-xs text-neutral-400'>还没有意见</p>
                ) : (
                  <ul className='mb-2 space-y-1'>
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
                          const next = await addGeoContentArticleOpinionApi({ itemId: article.itemId, opinion: text });
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
          </section>
        </div>
      )}
      <Modal
        title='AI 生成文章'
        open={aiOpen}
        onCancel={() => !aiGenerating && setAiOpen(false)}
        onOk={() => void runAiGenerate()}
        okText='开始生成'
        confirmLoading={aiGenerating}
        destroyOnHidden
        width={560}
      >
        <p className='mb-3 text-sm text-neutral-500'>
          按「提示词 + 话题 +
          目标问题」召回卖点，生成可直接粘贴到公众号/知乎等平台的正式排版（导语、小标题、短段落、要点），并写入
          {active !== 'shared' && article?.contentMode === 'custom' ? '当前独立稿' : '统一稿'}。
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
        <div className='mb-1 text-xs text-neutral-500'>选择模型</div>
        <Select
          className='mb-3 w-full'
          placeholder='请选择已配置的 AI 模型'
          value={aiProvider}
          options={aiProviders.map((p) => ({
            value: p.provider,
            label: p.model ? `${p.label}（${p.model}）` : p.label,
          }))}
          onChange={setAiProvider}
        />
        {aiProviders.length === 0 ? (
          <p className='mb-3 text-xs text-amber-600'>暂无可用云厂商，请到「系统管理 → AI模型配置」填写 API Key。</p>
        ) : null}
        <div className='mb-1 text-xs text-neutral-500'>写作提示词（可选）</div>
        <Input.TextArea
          value={aiUserPrompt}
          onChange={(e) => setAiUserPrompt(e.target.value)}
          placeholder='例如：偏科普口吻，突出有机双认证与牧场自有；约 1000 字；开头先回答目标问题'
          autoSize={{ minRows: 3, maxRows: 8 }}
          maxLength={2000}
          showCount
          disabled={aiGenerating}
        />
      </Modal>
    </Drawer>
  );
}
