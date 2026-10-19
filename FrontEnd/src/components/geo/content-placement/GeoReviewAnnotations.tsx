import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Button, Input, Modal, Popconfirm, Popover, Space, message } from 'antd';
import { LakexEditorHost } from '@/components/lakex/LakexEditorHost';
import { addGeoContentReviewAnnotationApi, deleteGeoContentReviewAnnotationApi } from '@/api/geo';
import type { GeoContentReviewAnnotation } from '@/types/geo';
import { lakeToBlocks } from '@/utils/articleDiff';

type AnnoMarker = {
  no: number;
  annotation: GeoContentReviewAnnotation;
  start: number;
  end: number;
};

function resolveAnnotationRanges(plain: string, annotations: GeoContentReviewAnnotation[]): AnnoMarker[] {
  if (!plain || annotations.length === 0) return [];
  const occupied: Array<{ start: number; end: number }> = [];
  const markers: AnnoMarker[] = [];
  annotations.forEach((a, i) => {
    const quote = (a.quoteText || '').trim();
    if (!quote) return;
    let start = a.startOffset ?? -1;
    let end = a.endOffset ?? -1;
    if (start < 0 || end <= start || plain.slice(start, end) !== quote) {
      start = -1;
      end = -1;
      const prefix = (a.quotePrefix || '').trim();
      if (prefix) {
        const needle = prefix + quote;
        const p = plain.indexOf(needle);
        if (p >= 0) {
          start = p + prefix.length;
          end = start + quote.length;
        }
      }
      if (start < 0) {
        let from = 0;
        while (from < plain.length) {
          const p = plain.indexOf(quote, from);
          if (p < 0) break;
          const candidateEnd = p + quote.length;
          const overlap = occupied.some((o) => !(candidateEnd <= o.start || p >= o.end));
          if (!overlap) {
            start = p;
            end = candidateEnd;
            break;
          }
          from = p + 1;
        }
      }
    }
    if (start < 0 || end <= start) return;
    occupied.push({ start, end });
    markers.push({ no: i + 1, annotation: a, start, end });
  });
  return markers.sort((a, b) => a.start - b.start || a.no - b.no);
}

function AnnotationMarkerBadge({
  no,
  annotation,
  active,
  onOpen,
}: {
  no: number;
  annotation: GeoContentReviewAnnotation;
  active?: boolean;
  onOpen?: (no: number) => void;
}) {
  return (
    <Popover
      trigger='click'
      placement='top'
      title={`批注 ${no}`}
      content={
        <div className='max-w-xs space-y-2 text-sm'>
          <blockquote className='mb-0 border-l-2 border-amber-300 pl-2 text-neutral-600'>
            「{annotation.quoteText}」
          </blockquote>
          <div className='text-neutral-800'>{annotation.commentText}</div>
          <div className='text-xs text-neutral-400'>
            {annotation.authorName || '审核人'}
            {annotation.createTime ? ` · ${annotation.createTime}` : ''}
          </div>
        </div>
      }
      onOpenChange={(open) => {
        if (open) onOpen?.(no);
      }}
    >
      <button
        type='button'
        className={`mx-0.5 inline-flex h-5 min-w-5 cursor-pointer items-center justify-center rounded-full border px-1 align-super text-[11px] leading-none font-semibold ${
          active
            ? 'border-amber-600 bg-amber-500 text-white'
            : 'border-amber-400 bg-amber-100 text-amber-900 hover:bg-amber-200'
        }`}
        title={`查看批注 ${no}`}
        onClick={(e) => e.stopPropagation()}
      >
        {no}
      </button>
    </Popover>
  );
}

type SelectionDraft = {
  quoteText: string;
  quotePrefix: string;
  quoteSuffix: string;
  startOffset: number | null;
  endOffset: number | null;
  top: number;
  left: number;
};

/**
 * 原文展示区：正文内嵌批注数字，点击数字弹出批注。
 * 可选 selectable：在原文上划选继续加批注。
 */
export function AnnotatedOriginalWithMarkers({
  contentBody,
  annotations,
  className,
  /** 作为主原文区时占满高度 */
  fill = false,
  maxHeightClass = 'max-h-80',
  selectable = false,
  onAdd,
  hint,
}: {
  contentBody?: string;
  annotations: GeoContentReviewAnnotation[];
  className?: string;
  fill?: boolean;
  maxHeightClass?: string;
  selectable?: boolean;
  onAdd?: (draft: Omit<SelectionDraft, 'top' | 'left'>) => void;
  hint?: string;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  const [activeNo, setActiveNo] = useState<number | null>(null);
  const [draft, setDraft] = useState<SelectionDraft | null>(null);
  const plain = useMemo(() => {
    const blocks = lakeToBlocks(contentBody);
    if (blocks.length > 0) return blocks.join('\n');
    return (contentBody || '').trim();
  }, [contentBody]);
  const markers = useMemo(() => resolveAnnotationRanges(plain, annotations), [plain, annotations]);

  const clearDraft = useCallback(() => setDraft(null), []);

  useEffect(() => {
    if (!selectable) {
      clearDraft();
      return;
    }
    const onDocClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) clearDraft();
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [selectable, clearDraft]);

  const nodes = useMemo(() => {
    if (!plain) return null;
    if (markers.length === 0) {
      return <span className='whitespace-pre-wrap'>{plain}</span>;
    }
    const out: ReactNode[] = [];
    let cursor = 0;
    markers.forEach((m, i) => {
      if (m.start > cursor) {
        out.push(
          <span
            key={`t-${i}-${cursor}`}
            className='whitespace-pre-wrap'
          >
            {plain.slice(cursor, m.start)}
          </span>,
        );
      }
      const quote = plain.slice(m.start, m.end);
      out.push(
        <Fragment key={`m-${m.no}-${m.start}`}>
          <mark className='rounded bg-amber-100 px-0.5 text-amber-950'>{quote}</mark>
          <AnnotationMarkerBadge
            no={m.no}
            annotation={m.annotation}
            active={activeNo === m.no}
            onOpen={setActiveNo}
          />
        </Fragment>,
      );
      cursor = m.end;
    });
    if (cursor < plain.length) {
      out.push(
        <span
          key='t-tail'
          className='whitespace-pre-wrap'
        >
          {plain.slice(cursor)}
        </span>,
      );
    }
    return out;
  }, [plain, markers, activeNo]);

  if (!plain) {
    return <p className='mb-0 p-4 text-sm text-neutral-400'>暂无原文快照</p>;
  }

  const unmatched = annotations.length - markers.length;
  const bodyClass = fill
    ? 'min-h-0 flex-1 overflow-auto bg-white px-4 py-3 text-[15px] leading-7 text-neutral-800 select-text'
    : `overflow-auto rounded border border-amber-100 bg-white px-3 py-2 text-sm leading-7 text-neutral-800 select-text ${maxHeightClass}`;

  return (
    <div
      ref={rootRef}
      className={`${fill ? 'flex h-full min-h-0 flex-col' : ''} ${className || ''}`}
      onMouseUp={() => {
        if (!selectable || !onAdd) return;
        window.requestAnimationFrame(() => {
          const next = captureSelectionIn(bodyRef.current);
          setDraft(next);
        });
      }}
    >
      {(hint || annotations.length > 0 || selectable) && (
        <div className='shrink-0 border-b border-neutral-100 px-3 py-1.5 text-xs text-neutral-500'>
          {hint ||
            (selectable
              ? '在原文中划选后点「添加批注」；已有批注处点数字查看内容'
              : '高亮处为批注原文，点数字查看具体意见')}
          {unmatched > 0 ? <span className='ml-2 text-amber-700'>有 {unmatched} 处未能精确匹配</span> : null}
        </div>
      )}
      <div className={`relative ${fill ? 'flex min-h-0 flex-1 flex-col' : ''}`}>
        <div
          ref={bodyRef}
          className={bodyClass}
        >
          {nodes}
        </div>
        {selectable && onAdd && draft ? (
          <div
            className='pointer-events-auto absolute z-30'
            style={{ top: Math.max(8, draft.top), left: Math.max(8, draft.left) }}
          >
            <Button
              type='primary'
              size='small'
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                onAdd({
                  quoteText: draft.quoteText,
                  quotePrefix: draft.quotePrefix,
                  quoteSuffix: draft.quoteSuffix,
                  startOffset: draft.startOffset,
                  endOffset: draft.endOffset,
                });
                clearDraft();
                window.getSelection()?.removeAllRanges();
              }}
            >
              添加批注
            </Button>
          </div>
        ) : null}
      </div>
      {unmatched > 0 ? (
        <ul className='m-0 shrink-0 list-none space-y-1 border-t border-neutral-100 px-3 py-2'>
          {annotations.map((a, i) => {
            const hit = markers.some((m) => m.annotation.id === a.id);
            if (hit) return null;
            return (
              <li
                key={a.id}
                className='text-xs text-neutral-600'
              >
                <AnnotationMarkerBadge
                  no={i + 1}
                  annotation={a}
                  onOpen={setActiveNo}
                />
                <span className='ml-1'>未能定位：「{a.quoteText}」</span>
              </li>
            );
          })}
        </ul>
      ) : null}
    </div>
  );
}

type Props = {
  annotations: GeoContentReviewAnnotation[];
  /** 审核进行中且当前用户可批注时传入 reviewId */
  editableReviewId?: number | null;
  /** 可选：用于划选的纯文本来源（Lake 正文） */
  contentBody?: string;
  onChange?: (next: GeoContentReviewAnnotation[]) => void;
  /** compact=历史列表内嵌；panel=独立侧栏 */
  variant?: 'compact' | 'panel';
};

function normalizePlain(text: string) {
  return text.replace(/\s+/g, ' ').trim();
}

function buildPlainFromLake(body?: string) {
  return lakeToBlocks(body).join('\n');
}

function captureSelectionIn(root: HTMLElement | null): SelectionDraft | null {
  if (!root) return null;
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer)) return null;
  const quoteText = normalizePlain(sel.toString());
  if (!quoteText || quoteText.length < 1) return null;

  const plain = normalizePlain(root.innerText || '');
  let startOffset: number | null = null;
  let endOffset: number | null = null;
  const idx = plain.indexOf(quoteText);
  if (idx >= 0) {
    startOffset = idx;
    endOffset = idx + quoteText.length;
  }
  const prefix = startOffset != null ? plain.slice(Math.max(0, startOffset - 40), startOffset) : '';
  const suffix = endOffset != null ? plain.slice(endOffset, Math.min(plain.length, endOffset + 40)) : '';

  // getBoundingClientRect 相对视口；相对 root 可见区域定位即可（子节点滚动无需再加 scrollTop）
  const rect = range.getBoundingClientRect();
  const rootRect = root.getBoundingClientRect();
  return {
    quoteText,
    quotePrefix: prefix,
    quoteSuffix: suffix,
    startOffset,
    endOffset,
    top: rect.bottom - rootRect.top + 8,
    left: Math.min(Math.max(8, rect.left - rootRect.left), Math.max(8, root.clientWidth - 120)),
  };
}

export function ReviewAnnotationList({
  annotations,
  editableReviewId,
  onDelete,
  variant = 'compact',
  activeNo,
  onSelectNo,
}: {
  annotations: GeoContentReviewAnnotation[];
  editableReviewId?: number | null;
  onDelete?: (id: number) => void;
  variant?: 'compact' | 'panel';
  activeNo?: number | null;
  onSelectNo?: (no: number) => void;
}) {
  if (annotations.length === 0) {
    return variant === 'panel' ? (
      <p className='mb-0 px-1 text-sm text-neutral-400'>暂无划词批注。审核时可在正文中划选文字添加。</p>
    ) : null;
  }
  return (
    <ul className={`m-0 list-none space-y-2 p-0 ${variant === 'panel' ? '' : 'mt-2'}`}>
      {annotations.map((a, idx) => {
        const no = idx + 1;
        const canDelete = editableReviewId != null && a.reviewId === editableReviewId && onDelete;
        const active = activeNo === no;
        return (
          <li
            key={a.id}
            id={`geo-anno-${a.id}`}
            className={`rounded border px-3 py-2 text-sm ${
              active ? 'border-amber-400 bg-amber-100/80' : 'border-amber-100 bg-amber-50/70'
            }`}
          >
            <div className='mb-1 flex flex-wrap items-center gap-2 text-xs text-neutral-500'>
              <button
                type='button'
                className='inline-flex h-5 min-w-5 cursor-pointer items-center justify-center rounded-full border border-amber-400 bg-amber-100 px-1 text-[11px] font-semibold text-amber-900'
                onClick={() => onSelectNo?.(no)}
                title='定位原文'
              >
                {no}
              </button>
              <span className='font-medium text-amber-800'>批注 {no}</span>
              <span>{a.authorName || '审核人'}</span>
              {a.createTime ? <span>{a.createTime}</span> : null}
              {canDelete ? (
                <Popconfirm
                  title='删除这条批注？'
                  onConfirm={() => onDelete?.(a.id)}
                >
                  <Button
                    type='link'
                    size='small'
                    danger
                    className='ml-auto h-auto p-0'
                  >
                    删除
                  </Button>
                </Popconfirm>
              ) : null}
            </div>
            <blockquote className='mb-1 border-l-2 border-amber-300 pl-2 text-neutral-700'>
              「{a.quoteText}」
            </blockquote>
            <div className='text-neutral-800'>{a.commentText}</div>
          </li>
        );
      })}
    </ul>
  );
}

/** 在 Lake 原文只读预览上划选 + 浮动「添加批注」 */
export function SelectableReviewBody({
  contentBody,
  enabled,
  onAdd,
  contentKey,
}: {
  contentBody?: string;
  enabled: boolean;
  onAdd: (draft: Omit<SelectionDraft, 'top' | 'left'>) => void;
  /** 换稿时重挂载编辑器 */
  contentKey?: string | number;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const [draft, setDraft] = useState<SelectionDraft | null>(null);
  const body = contentBody || '';
  const hasBody = body.trim().length > 0;

  const clearDraft = useCallback(() => setDraft(null), []);

  useEffect(() => {
    if (!enabled) {
      clearDraft();
      return;
    }
    const onDocClick = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) {
        clearDraft();
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [enabled, clearDraft]);

  if (!hasBody) {
    return <div className='p-4 text-sm text-neutral-400'>暂无提审原文，无法划词</div>;
  }

  return (
    <div
      ref={rootRef}
      className='relative flex h-full min-h-0 flex-col bg-white'
      onMouseUp={() => {
        if (!enabled) return;
        // 等浏览器选区稳定后再取
        window.requestAnimationFrame(() => {
          const next = captureSelectionIn(rootRef.current);
          setDraft(next);
        });
      }}
    >
      {enabled ? (
        <div className='shrink-0 border-b border-neutral-100 px-3 py-1.5 text-xs text-neutral-500'>
          在下方原文中划选文字后点「添加批注」；可划词、划句或整段。
        </div>
      ) : null}
      <div className='relative min-h-0 flex-1 overflow-hidden select-text'>
        <LakexEditorHost
          key={contentKey ?? `lakex-anno-${body.length}`}
          value={body}
          readOnly
        />
        {enabled && draft ? (
          <div
            className='pointer-events-auto absolute z-30'
            style={{ top: Math.max(8, draft.top), left: Math.max(8, draft.left) }}
          >
            <Button
              type='primary'
              size='small'
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                onAdd({
                  quoteText: draft.quoteText,
                  quotePrefix: draft.quotePrefix,
                  quoteSuffix: draft.quoteSuffix,
                  startOffset: draft.startOffset,
                  endOffset: draft.endOffset,
                });
                clearDraft();
                window.getSelection()?.removeAllRanges();
              }}
            >
              添加批注
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}

export function GeoReviewAnnotationPanel({
  annotations,
  editableReviewId = null,
  contentBody,
  onChange,
  variant = 'panel',
}: Props) {
  const [list, setList] = useState(annotations);
  const [saving, setSaving] = useState(false);
  const [pending, setPending] = useState<Omit<SelectionDraft, 'top' | 'left'> | null>(null);
  const [comment, setComment] = useState('');

  useEffect(() => {
    setList(annotations);
  }, [annotations]);

  const sync = (next: GeoContentReviewAnnotation[]) => {
    setList(next);
    onChange?.(next);
  };

  const submit = async () => {
    if (editableReviewId == null || !pending) return;
    const text = comment.trim();
    if (!text) {
      message.warning('请填写批注内容');
      return;
    }
    setSaving(true);
    try {
      const created = await addGeoContentReviewAnnotationApi({
        reviewId: editableReviewId,
        quoteText: pending.quoteText,
        commentText: text,
        quotePrefix: pending.quotePrefix,
        quoteSuffix: pending.quoteSuffix,
        startOffset: pending.startOffset,
        endOffset: pending.endOffset,
      });
      sync([...list, created]);
      setPending(null);
      setComment('');
      message.success('已添加划词批注');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '添加批注失败');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: number) => {
    try {
      await deleteGeoContentReviewAnnotationApi(id);
      sync(list.filter((a) => a.id !== id));
      message.success('已删除');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  return (
    <div className={variant === 'panel' ? 'flex min-h-0 flex-1 flex-col' : ''}>
      {editableReviewId != null && contentBody != null ? (
        <div className='min-h-0 flex-1 border-b border-neutral-100'>
          <AnnotatedOriginalWithMarkers
            contentBody={contentBody}
            annotations={list}
            fill
            selectable
            onAdd={(d) => {
              setPending(d);
              setComment('');
            }}
          />
        </div>
      ) : null}
      <div className={`overflow-auto ${variant === 'panel' ? 'max-h-64 shrink-0 px-3 py-2' : ''}`}>
        <div className='mb-1 text-xs font-medium text-neutral-500'>
          批注列表{list.length > 0 ? `（${list.length}）` : ''}
        </div>
        <ReviewAnnotationList
          annotations={list}
          editableReviewId={editableReviewId}
          onDelete={editableReviewId != null ? (id) => void remove(id) : undefined}
          variant={variant}
        />
      </div>

      <Modal
        title='添加划词批注'
        open={pending != null}
        confirmLoading={saving}
        onCancel={() => {
          setPending(null);
          setComment('');
        }}
        onOk={() => void submit()}
        okText='保存批注'
        destroyOnHidden
      >
        {pending ? (
          <Space
            direction='vertical'
            className='w-full'
            size={12}
          >
            <blockquote className='mb-0 border-l-2 border-amber-300 bg-amber-50/60 px-3 py-2 text-sm text-neutral-700'>
              「{pending.quoteText}」
            </blockquote>
            <Input.TextArea
              rows={3}
              value={comment}
              placeholder='说明这里要怎么改'
              onChange={(e) => setComment(e.target.value)}
              maxLength={2000}
              showCount
            />
          </Space>
        ) : null}
      </Modal>
    </div>
  );
}

/** 审核意见/对照：原文数字定位 + 批注列表 */
export function GeoReviewAnnotationReadonly({
  annotations,
  contentBody,
  showOriginal = true,
}: {
  annotations: GeoContentReviewAnnotation[];
  contentBody?: string;
  showOriginal?: boolean;
}) {
  const [activeNo, setActiveNo] = useState<number | null>(null);
  if (annotations.length === 0) return null;
  return (
    <div className='space-y-2'>
      {showOriginal && contentBody ? (
        <AnnotatedOriginalWithMarkers
          contentBody={contentBody}
          annotations={annotations}
          maxHeightClass='max-h-64'
        />
      ) : null}
      <ReviewAnnotationList
        annotations={annotations}
        variant='compact'
        activeNo={activeNo}
        onSelectNo={setActiveNo}
      />
    </div>
  );
}

export function hasSelectablePlain(contentBody?: string) {
  return buildPlainFromLake(contentBody).length > 0;
}
