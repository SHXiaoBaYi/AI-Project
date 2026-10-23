import { useEffect, useState } from 'react';
import { Button, Input, Space, Tag, message } from 'antd';
import {
  adoptGeoContentReviewAiApi,
  feedbackGeoContentReviewAiApi,
  getGeoContentReviewAiApi,
  rerunGeoContentReviewAiApi,
} from '@/api/geo';
import type { GeoContentReviewAi, GeoContentReviewAnnotation } from '@/types/geo';

const LEVEL_COLOR: Record<string, string> = {
  high: 'error',
  mid: 'warning',
  low: 'default',
};

function suggestionLabel(ai?: GeoContentReviewAi | null) {
  if (!ai || ai.status === 'none') return null;
  if (ai.status === 'pending' || ai.status === 'running') {
    return { text: '预审中', color: 'processing' as const };
  }
  if (ai.status === 'failed') {
    return { text: '预审失败', color: 'error' as const };
  }
  if (ai.suggestion === 'approve') {
    return { text: '建议通过', color: 'success' as const };
  }
  if (ai.suggestion === 'reject') {
    return { text: '建议退回', color: 'warning' as const };
  }
  return { text: '已完成', color: 'default' as const };
}

export function aiStatusTag(ai?: GeoContentReviewAi | null) {
  const meta = suggestionLabel(ai);
  if (!meta) return null;
  return (
    <Tag
      color={meta.color}
      className='mr-0'
    >
      AI·{meta.text}
      {ai?.score != null && ai.status === 'done' ? ` ${ai.score}分` : ''}
    </Tag>
  );
}

type Props = {
  reviewId: number | null;
  /** 对照接口带回的初始 AI；轮询会覆盖 */
  initialAi?: GeoContentReviewAi | null;
  canAct?: boolean;
  onAdopted?: (annotations: GeoContentReviewAnnotation[]) => void;
  onAiChange?: (ai: GeoContentReviewAi) => void;
};

export function GeoReviewAiPanel({ reviewId, initialAi, canAct = false, onAdopted, onAiChange }: Props) {
  const [ai, setAi] = useState<GeoContentReviewAi | null>(initialAi ?? null);
  const [busy, setBusy] = useState(false);
  const [feedbackRemark, setFeedbackRemark] = useState('');

  useEffect(() => {
    setAi(initialAi ?? null);
  }, [initialAi, reviewId]);

  useEffect(() => {
    if (reviewId == null) return;
    let stopped = false;
    let timer: number | null = null;
    const poll = async () => {
      try {
        const next = await getGeoContentReviewAiApi(reviewId);
        if (stopped) return;
        setAi(next);
        onAiChange?.(next);
        if (next.status === 'done' || next.status === 'failed') {
          if (timer != null) window.clearInterval(timer);
          timer = null;
        }
      } catch {
        /* 轮询失败忽略 */
      }
    };
    void poll();
    timer = window.setInterval(() => void poll(), 2500);
    return () => {
      stopped = true;
      if (timer != null) window.clearInterval(timer);
    };
  }, [reviewId]); // eslint-disable-line react-hooks/exhaustive-deps -- 仅随 reviewId 启停轮询

  if (reviewId == null) return null;
  if (ai?.aiPreReview === false) {
    return <div className='text-xs text-neutral-400'>该撰写人未开启 AI 预审</div>;
  }

  const meta = suggestionLabel(ai);
  const issues = ai?.issues ?? [];
  const done = ai?.status === 'done';
  const running = ai?.status === 'pending' || ai?.status === 'running';

  const rerun = async () => {
    setBusy(true);
    try {
      const next = await rerunGeoContentReviewAiApi(reviewId);
      setAi(next);
      onAiChange?.(next);
      message.success('已重新排队 AI 预审');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '重跑失败');
    } finally {
      setBusy(false);
    }
  };

  const adopt = async () => {
    setBusy(true);
    try {
      const created = await adoptGeoContentReviewAiApi(reviewId);
      onAdopted?.(created);
      setAi((prev) => (prev ? { ...prev, adopted: true } : prev));
      message.success(`已采纳 ${created.length} 条划词`);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '采纳失败');
    } finally {
      setBusy(false);
    }
  };

  const feedback = async (value: 'accurate' | 'inaccurate') => {
    setBusy(true);
    try {
      await feedbackGeoContentReviewAiApi({
        reviewId,
        feedback: value,
        remark: feedbackRemark.trim() || undefined,
      });
      setAi((prev) =>
        prev ? { ...prev, feedback: value, feedbackRemark: feedbackRemark.trim() || prev.feedbackRemark } : prev,
      );
      message.success(value === 'accurate' ? '已标记为准' : '已标记为不准');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '反馈失败');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className='flex flex-col gap-2'>
      <div className='flex flex-wrap items-center gap-2'>
        <span className='text-sm font-medium'>AI 预审</span>
        {meta ? (
          <Tag
            color={meta.color}
            className='mr-0'
          >
            {meta.text}
          </Tag>
        ) : (
          <Tag className='mr-0'>未开始</Tag>
        )}
        {done && ai?.score != null ? <span className='text-xs text-neutral-500'>{ai.score} 分</span> : null}
      </div>
      {running ? <div className='text-xs text-neutral-500'>预审中，通过/退回仍可用（除非规则禁止通过）…</div> : null}
      {ai?.status === 'failed' ? (
        <div className='rounded border border-red-100 bg-red-50 px-2 py-1.5 text-xs text-red-700'>
          {ai.errorMsg || '预审失败'}
        </div>
      ) : null}
      {done && ai?.summary ? <p className='mb-0 text-sm text-neutral-700'>{ai.summary}</p> : null}
      {done && issues.length > 0 ? (
        <ul className='m-0 max-h-40 list-none space-y-1.5 overflow-auto p-0'>
          {issues.map((issue, idx) => (
            <li
              key={`${issue.quoteText}-${idx}`}
              className='rounded border border-neutral-100 bg-neutral-50 px-2 py-1.5 text-xs'
            >
              <div className='mb-0.5 flex items-center gap-1'>
                <Tag
                  color={LEVEL_COLOR[issue.level || 'mid'] || 'default'}
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
      ) : null}
      {done && issues.length === 0 ? <div className='text-xs text-neutral-400'>无划词建议</div> : null}
      {canAct ? (
        <Space
          wrap
          size={8}
        >
          {done && issues.length > 0 && !ai?.adopted ? (
            <Button
              size='small'
              type='primary'
              loading={busy}
              onClick={() => void adopt()}
            >
              一键采纳划词
            </Button>
          ) : null}
          {ai?.adopted ? <Tag color='success'>已采纳划词</Tag> : null}
          <Button
            size='small'
            loading={busy}
            disabled={running}
            onClick={() => void rerun()}
          >
            重新跑 AI
          </Button>
        </Space>
      ) : null}
      {canAct && done ? (
        <div className='mt-1 border-t border-neutral-100 pt-2'>
          <div className='mb-1 text-xs text-neutral-500'>预审准不准？（用于校准）</div>
          {ai?.feedback ? (
            <Tag color={ai.feedback === 'accurate' ? 'success' : 'warning'}>
              已反馈：{ai.feedback === 'accurate' ? '准' : '不准'}
            </Tag>
          ) : (
            <Space
              direction='vertical'
              className='w-full'
              size={6}
            >
              <Input.TextArea
                size='small'
                rows={2}
                value={feedbackRemark}
                onChange={(e) => setFeedbackRemark(e.target.value)}
                placeholder='可选说明'
                maxLength={200}
              />
              <Space size={8}>
                <Button
                  size='small'
                  loading={busy}
                  onClick={() => void feedback('accurate')}
                >
                  准
                </Button>
                <Button
                  size='small'
                  loading={busy}
                  onClick={() => void feedback('inaccurate')}
                >
                  不准
                </Button>
              </Space>
            </Space>
          )}
        </div>
      ) : null}
      {ai?.modelName || ai?.provider ? (
        <div className='text-[11px] text-neutral-400'>
          {ai.provider}
          {ai.modelName ? ` · ${ai.modelName}` : ''}
        </div>
      ) : null}
    </div>
  );
}

/** 相对上一轮：AI 风险原文是否仍出现在新稿中 */
export function aiRiskClearedLabel(prevAi?: GeoContentReviewAi | null, nextBody?: string) {
  if (!prevAi || prevAi.status !== 'done') return null;
  const quotes = (prevAi.issues ?? []).map((i) => (i.quoteText || '').trim()).filter(Boolean);
  if (quotes.length === 0) {
    if (prevAi.suggestion === 'reject') return { text: '上轮 AI 风险：无划词可核', color: 'default' as const };
    return { text: '上轮 AI 建议通过', color: 'success' as const };
  }
  const body = nextBody || '';
  const still = quotes.filter((q) => body.includes(q)).length;
  if (still === 0) return { text: '上轮 AI 风险已改完', color: 'success' as const };
  if (still === quotes.length) return { text: `上轮 AI 风险仍在 ${still}`, color: 'error' as const };
  return { text: `上轮 AI 风险剩 ${still}/${quotes.length}`, color: 'warning' as const };
}
