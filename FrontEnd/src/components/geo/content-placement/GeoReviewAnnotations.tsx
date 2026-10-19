import { Fragment, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Button, Input, Modal, Popconfirm, Popover, Space, message } from 'antd';
import { LakexEditorHost } from '@/components/lakex/LakexEditorHost';
import { prefetchLakexAssets } from '@/components/lakex/loadLakexAssets';
import {
  addGeoContentReviewAnnotationApi,
  deleteGeoContentReviewAnnotationApi,
  updateGeoContentReviewAnnotationApi,
} from '@/api/geo';
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
    let hit = findQuoteOffsets(plain, quote, a.startOffset, a.endOffset, occupied);
    if (!hit && (a.quotePrefix || '').trim()) {
      const prefix = (a.quotePrefix || '').trim();
      const withPrefix = findQuoteOffsets(plain, `${prefix}${quote}`, null, null, occupied);
      if (withPrefix) {
        const prefHit = findQuoteOffsets(plain.slice(withPrefix.start, withPrefix.end), prefix, null, null);
        const quoteStart = withPrefix.start + (prefHit ? prefHit.end : prefix.length);
        hit = { start: quoteStart, end: withPrefix.end };
        if (occupied.some((o) => !(hit!.end <= o.start || hit!.start >= o.end))) hit = null;
      }
    }
    if (!hit) return;
    occupied.push(hit);
    markers.push({ no: i + 1, annotation: a, start: hit.start, end: hit.end });
  });
  return markers.sort((a, b) => a.start - b.start || a.no - b.no);
}

/** 批注数字旁的浮层：查看 / 修改 / 删除 */
function AnnotationTipContent({
  no,
  annotation,
  canEdit,
  onUpdated,
  onDeleted,
  onClose,
}: {
  no: number;
  annotation: GeoContentReviewAnnotation;
  canEdit?: boolean;
  onUpdated?: (next: GeoContentReviewAnnotation) => void;
  onDeleted?: (id: number) => void;
  onClose?: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(annotation.commentText || '');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setText(annotation.commentText || '');
    setEditing(false);
  }, [annotation.id, annotation.commentText]);

  const saveEdit = async () => {
    const next = text.trim();
    if (!next) {
      message.warning('请填写批注内容');
      return;
    }
    setSaving(true);
    try {
      const updated = await updateGeoContentReviewAnnotationApi({ id: annotation.id, commentText: next });
      onUpdated?.(updated);
      setEditing(false);
      message.success('已修改');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '修改失败');
    } finally {
      setSaving(false);
    }
  };

  const [confirmDelete, setConfirmDelete] = useState(false);

  const remove = async () => {
    setSaving(true);
    try {
      await deleteGeoContentReviewAnnotationApi(annotation.id);
      onDeleted?.(annotation.id);
      onClose?.();
      message.success('已删除');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败');
    } finally {
      setSaving(false);
      setConfirmDelete(false);
    }
  };

  return (
    <div className='w-72 space-y-2 text-sm'>
      <div className='flex items-center gap-2 text-xs text-neutral-500'>
        <span className='font-semibold text-amber-800'>批注 {no}</span>
        <span>{annotation.authorName || '审核人'}</span>
        {annotation.createTime ? <span>{annotation.createTime}</span> : null}
      </div>
      {editing ? (
        <Input.TextArea
          rows={3}
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={2000}
          showCount
          autoFocus
        />
      ) : (
        <div className='whitespace-pre-wrap text-neutral-800'>{annotation.commentText}</div>
      )}
      {canEdit ? (
        <div className='flex flex-wrap items-center gap-1 border-t border-neutral-100 pt-2'>
          {editing ? (
            <>
              <Button
                type='primary'
                size='small'
                loading={saving}
                onClick={() => void saveEdit()}
              >
                保存
              </Button>
              <Button
                size='small'
                disabled={saving}
                onClick={() => {
                  setEditing(false);
                  setText(annotation.commentText || '');
                }}
              >
                取消
              </Button>
            </>
          ) : confirmDelete ? (
            <>
              <span className='mr-1 text-xs text-red-500'>确认删除？</span>
              <Button
                type='primary'
                danger
                size='small'
                loading={saving}
                onClick={() => void remove()}
              >
                确认
              </Button>
              <Button
                size='small'
                disabled={saving}
                onClick={() => setConfirmDelete(false)}
              >
                取消
              </Button>
            </>
          ) : (
            <>
              <Button
                type='link'
                size='small'
                className='h-auto px-0'
                onClick={() => setEditing(true)}
              >
                修改
              </Button>
              <Button
                type='link'
                size='small'
                danger
                className='h-auto px-0'
                onClick={() => setConfirmDelete(true)}
              >
                删除
              </Button>
            </>
          )}
        </div>
      ) : null}
    </div>
  );
}

function AnnotationMarkerBadge({
  no,
  annotation,
  active,
  onOpen,
  canEdit,
  onUpdated,
  onDeleted,
}: {
  no: number;
  annotation: GeoContentReviewAnnotation;
  active?: boolean;
  onOpen?: (no: number) => void;
  canEdit?: boolean;
  onUpdated?: (next: GeoContentReviewAnnotation) => void;
  onDeleted?: (id: number) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Popover
      trigger='click'
      placement='top'
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) onOpen?.(no);
      }}
      content={
        <AnnotationTipContent
          no={no}
          annotation={annotation}
          canEdit={canEdit}
          onUpdated={(next) => {
            onUpdated?.(next);
          }}
          onDeleted={(id) => {
            onDeleted?.(id);
            setOpen(false);
          }}
          onClose={() => setOpen(false)}
        />
      }
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
  editableReviewId = null,
  onAnnotationsChange,
}: {
  contentBody?: string;
  annotations: GeoContentReviewAnnotation[];
  className?: string;
  fill?: boolean;
  maxHeightClass?: string;
  selectable?: boolean;
  onAdd?: (draft: Omit<SelectionDraft, 'top' | 'left'>) => void;
  hint?: string;
  editableReviewId?: number | null;
  onAnnotationsChange?: (next: GeoContentReviewAnnotation[]) => void;
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
    const onDocDown = (e: MouseEvent) => {
      const el = e.target instanceof Element ? e.target : (e.target as Node | null)?.parentElement;
      if (el?.closest?.('[data-geo-anno-add], [data-geo-anno-tip], [data-geo-anno-badge]')) return;
      if (!rootRef.current?.contains(e.target as Node)) clearDraft();
    };
    document.addEventListener('mousedown', onDocDown);
    return () => document.removeEventListener('mousedown', onDocDown);
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
            canEdit={editableReviewId != null && m.annotation.reviewId === editableReviewId}
            onUpdated={(next) => {
              onAnnotationsChange?.(annotations.map((a) => (a.id === next.id ? next : a)));
            }}
            onDeleted={(id) => {
              onAnnotationsChange?.(annotations.filter((a) => a.id !== id));
            }}
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
  }, [plain, markers, activeNo, annotations, editableReviewId, onAnnotationsChange]);

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
      onMouseUp={(e) => {
        if (!selectable || !onAdd) return;
        const t = e.target as Element | null;
        if (t?.closest?.('[data-geo-anno-add], [data-geo-anno-tip], [data-geo-anno-badge]')) return;
        window.requestAnimationFrame(() => {
          const next = captureSelectionIn(bodyRef.current);
          if (!next) {
            setDraft(null);
            return;
          }
          const sel = window.getSelection();
          if (sel && sel.rangeCount > 0) {
            const rect = sel.getRangeAt(0).getBoundingClientRect();
            if (rect.width > 0 || rect.height > 0) {
              next.top = rect.bottom + 8;
              next.left = Math.min(Math.max(8, rect.left), window.innerWidth - 120);
            }
          }
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
        {selectable && onAdd && draft
          ? createPortal(
              <div
                data-geo-anno-add
                className='pointer-events-auto'
                style={{
                  position: 'fixed',
                  top: Math.min(Math.max(8, draft.top), window.innerHeight - 48),
                  left: Math.min(Math.max(8, draft.left), window.innerWidth - 120),
                  zIndex: 11000,
                }}
                onMouseDown={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                }}
              >
                <Button
                  type='primary'
                  size='small'
                  onMouseDown={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                  }}
                  onClick={(e) => {
                    e.stopPropagation();
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
              </div>,
              document.body,
            )
          : null}
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

function escapeRegExp(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** 是否为批注角标等非正文节点（计入 plain 会打乱偏移） */
function isAnnoChromeNode(node: Node): boolean {
  const el = node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node.parentElement;
  return !!el?.closest?.('[data-geo-anno-badge]');
}

/** 与 rangeFromTextOffsets 同一套：拼接可见正文 Text，跳过角标 */
function collectDomPlain(root: HTMLElement): string {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let plain = '';
  let node = walker.nextNode() as Text | null;
  while (node) {
    if (!isAnnoChromeNode(node)) plain += node.data;
    node = walker.nextNode() as Text | null;
  }
  return plain;
}

/** 将选区映射到 collectDomPlain 偏移（支持起止在元素节点上的跨行选区） */
function offsetsFromRange(root: HTMLElement, range: Range): { start: number; end: number } | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let pos = 0;
  let start = -1;
  let end = -1;
  let node = walker.nextNode() as Text | null;
  while (node) {
    if (isAnnoChromeNode(node)) {
      node = walker.nextNode() as Text | null;
      continue;
    }
    const len = node.data.length;
    const nodeRange = document.createRange();
    nodeRange.selectNodeContents(node);
    // 与选区无交集则跳过
    const startsAfterNode = range.compareBoundaryPoints(Range.START_TO_END, nodeRange) >= 0;
    const endsBeforeNode = range.compareBoundaryPoints(Range.END_TO_START, nodeRange) <= 0;
    if (!startsAfterNode && !endsBeforeNode) {
      let localStart = 0;
      let localEnd = len;
      if (range.startContainer === node) {
        localStart = Math.min(range.startOffset, len);
      } else if (range.compareBoundaryPoints(Range.START_TO_START, nodeRange) > 0) {
        // 起点在本节点之后：不应相交
        localStart = len;
      }
      if (range.endContainer === node) {
        localEnd = Math.min(range.endOffset, len);
      } else if (range.compareBoundaryPoints(Range.END_TO_END, nodeRange) < 0) {
        localEnd = 0;
      }
      if (start < 0) start = pos + localStart;
      end = pos + localEnd;
    }
    pos += len;
    node = walker.nextNode() as Text | null;
  }
  if (start < 0 || end <= start) return null;
  return { start, end };
}

function captureSelectionIn(root: HTMLElement | null): SelectionDraft | null {
  if (!root) return null;
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || sel.rangeCount === 0) return null;
  const range = sel.getRangeAt(0);
  if (!root.contains(range.commonAncestorContainer)) return null;
  const rawQuote = (sel.toString() || '').replace(/\u00a0/g, ' ');
  const quoteText = normalizePlain(rawQuote);
  if (!quoteText || quoteText.length < 1) return null;

  const plain = collectDomPlain(root);
  let startOffset: number | null = null;
  let endOffset: number | null = null;
  const fromRange = offsetsFromRange(root, range);
  if (fromRange) {
    startOffset = fromRange.start;
    endOffset = fromRange.end;
  } else {
    const hit = findQuoteOffsets(plain, quoteText, null, null);
    if (hit) {
      startOffset = hit.start;
      endOffset = hit.end;
    }
  }
  const prefix = startOffset != null ? plain.slice(Math.max(0, startOffset - 40), startOffset) : '';
  const suffix = endOffset != null ? plain.slice(endOffset, Math.min(plain.length, endOffset + 40)) : '';

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

/** 在 DOM plain 中定位引用（支持跨行空白差异；优先用存储的偏移） */
function findQuoteOffsets(
  plain: string,
  quote: string,
  preferStart: number | null | undefined,
  preferEnd: number | null | undefined,
  occupied: Array<{ start: number; end: number }> = [],
): { start: number; end: number } | null {
  const q = (quote || '').trim();
  if (!q || !plain) return null;

  const overlaps = (s: number, e: number) => occupied.some((o) => !(e <= o.start || s >= o.end));

  if (
    preferStart != null &&
    preferEnd != null &&
    preferStart >= 0 &&
    preferEnd > preferStart &&
    preferEnd <= plain.length
  ) {
    const slice = plain.slice(preferStart, preferEnd);
    if (normalizePlain(slice) === normalizePlain(q) && !overlaps(preferStart, preferEnd)) {
      return { start: preferStart, end: preferEnd };
    }
    // 偏移附近容错：前后各扩 80 字再搜
    const pad = 80;
    const from = Math.max(0, preferStart - pad);
    const to = Math.min(plain.length, preferEnd + pad);
    const windowHit = findQuoteOffsets(plain.slice(from, to), q, null, null, []);
    if (windowHit && !overlaps(from + windowHit.start, from + windowHit.end)) {
      return { start: from + windowHit.start, end: from + windowHit.end };
    }
  }

  const exact = plain.indexOf(q);
  if (exact >= 0 && !overlaps(exact, exact + q.length)) {
    return { start: exact, end: exact + q.length };
  }

  // 跨行：空白任意；中文之间也可能只有换行无空格 → 字符间允许 \s*
  const parts = q.split(/\s+/).filter(Boolean).map(escapeRegExp);
  if (parts.length === 0) return null;
  const re =
    parts.length === 1
      ? new RegExp(parts[0])
      : new RegExp(parts.join('\\s+'));
  let from = 0;
  while (from < plain.length) {
    const slice = plain.slice(from);
    const m = re.exec(slice);
    if (!m || m.index < 0) break;
    const start = from + m.index;
    const end = start + m[0].length;
    if (!overlaps(start, end)) return { start, end };
    from = start + 1;
  }

  // 再退一步：去掉 quote 内所有空白后，在 plain 上去空白映射回原偏移
  const compactQ = q.replace(/\s+/g, '');
  if (compactQ.length >= 2) {
    const map: number[] = [];
    let compactPlain = '';
    for (let i = 0; i < plain.length; i++) {
      if (/\s/.test(plain[i])) continue;
      map.push(i);
      compactPlain += plain[i];
    }
    const idx = compactPlain.indexOf(compactQ);
    if (idx >= 0) {
      const start = map[idx];
      const endExclusive = map[idx + compactQ.length - 1] + 1;
      if (start != null && endExclusive > start && !overlaps(start, endExclusive)) {
        return { start, end: endExclusive };
      }
    }
  }
  return null;
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

/** 在 Lake 渲染结果中按纯文本偏移定位 Range（跳过角标文字） */
function rangeFromTextOffsets(root: HTMLElement, start: number, end: number): Range | null {
  if (start < 0 || end <= start) return null;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let pos = 0;
  let startNode: Text | null = null;
  let startOff = 0;
  let endNode: Text | null = null;
  let endOff = 0;
  let node = walker.nextNode() as Text | null;
  while (node) {
    if (isAnnoChromeNode(node)) {
      node = walker.nextNode() as Text | null;
      continue;
    }
    const len = node.data.length;
    if (!startNode && pos + len > start) {
      startNode = node;
      startOff = start - pos;
    }
    if (pos + len >= end) {
      endNode = node;
      endOff = end - pos;
      break;
    }
    pos += len;
    node = walker.nextNode() as Text | null;
  }
  if (!startNode || !endNode) return null;
  try {
    const range = document.createRange();
    range.setStart(startNode, Math.max(0, Math.min(startOff, startNode.data.length)));
    range.setEnd(endNode, Math.max(0, Math.min(endOff, endNode.data.length)));
    return range;
  } catch {
    return null;
  }
}

function clearLakeAnnoMarks(root: HTMLElement) {
  root.querySelectorAll('mark[data-geo-anno]').forEach((el) => {
    const parent = el.parentNode;
    if (!parent) return;
    while (el.firstChild) parent.insertBefore(el.firstChild, el);
    parent.removeChild(el);
    parent.normalize();
  });
  root.querySelectorAll('[data-geo-anno-badge]').forEach((el) => el.remove());
}

function makeAnnoBadge(no: number, onOpen?: (no: number, anchor: HTMLElement) => void) {
  const badge = document.createElement('button');
  badge.type = 'button';
  badge.dataset.geoAnnoBadge = String(no);
  badge.className =
    'geo-lake-anno-badge mx-0.5 inline-flex h-5 min-w-5 cursor-pointer items-center justify-center rounded-full border border-amber-400 bg-amber-100 px-1 align-super text-[11px] font-semibold leading-none text-amber-900 hover:bg-amber-200';
  badge.textContent = String(no);
  badge.title = `查看批注 ${no}`;
  badge.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    onOpen?.(no, badge);
  });
  return badge;
}

/** 跨行/跨块：按文本节点分段包 mark，并在末段后插数字（比 surroundContents 稳） */
function applyLakeHighlight(
  root: HTMLElement,
  range: Range,
  no: number,
  onOpen?: (no: number, anchor: HTMLElement) => void,
) {
  const badge = makeAnnoBadge(no, onOpen);
  const segments: Array<{ node: Text; start: number; end: number }> = [];
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let node = walker.nextNode() as Text | null;
  while (node) {
    if (!isAnnoChromeNode(node) && node.data.length > 0) {
      try {
        if (range.intersectsNode(node)) {
          let start = 0;
          let end = node.data.length;
          if (range.startContainer === node) start = Math.min(range.startOffset, end);
          if (range.endContainer === node) end = Math.min(range.endOffset, end);
          if (start < end) segments.push({ node, start, end });
        }
      } catch {
        /* intersectsNode 在部分游离节点上会抛 */
      }
    }
    node = walker.nextNode() as Text | null;
  }

  if (segments.length === 0) {
    try {
      const tip = range.cloneRange();
      tip.collapse(false);
      tip.insertNode(badge);
    } catch {
      /* ignore */
    }
    return;
  }

  // 从后往前拆分包装，避免前面改 DOM 影响后面节点引用
  let lastMark: HTMLElement | null = null;
  for (let i = segments.length - 1; i >= 0; i--) {
    const seg = segments[i];
    try {
      const text = seg.node;
      const after = text.splitText(seg.end);
      const mid = text.splitText(seg.start);
      const mark = document.createElement('mark');
      mark.dataset.geoAnno = String(no);
      mark.className = 'geo-lake-anno-mark rounded bg-amber-100 px-0.5 text-amber-950';
      mid.parentNode?.insertBefore(mark, mid);
      mark.appendChild(mid);
      if (i === segments.length - 1) lastMark = mark;
      void after;
    } catch {
      /* 单段失败则继续其它段 */
    }
  }
  if (lastMark) {
    lastMark.insertAdjacentElement('afterend', badge);
  } else {
    try {
      const tip = range.cloneRange();
      tip.collapse(false);
      tip.insertNode(badge);
    } catch {
      /* ignore */
    }
  }
}

/** 在已渲染的 Lake 原文上叠批注高亮 + 数字角标（不破坏撰写排版） */
function paintLakeAnnotations(
  root: HTMLElement,
  annotations: GeoContentReviewAnnotation[],
  onOpen?: (no: number, anchor: HTMLElement) => void,
) {
  clearLakeAnnoMarks(root);
  if (annotations.length === 0) return;
  const plain = collectDomPlain(root);
  const markers = resolveAnnotationRanges(plain, annotations);
  // 先算好 Range，再从后往前插入，避免前面包装打乱后面偏移
  const planned = markers
    .map((m) => ({ m, range: rangeFromTextOffsets(root, m.start, m.end) }))
    .filter((p): p is { m: AnnoMarker; range: Range } => p.range != null)
    .sort((a, b) => b.m.start - a.m.start || b.m.no - a.m.no);
  planned.forEach(({ m, range }) => applyLakeHighlight(root, range, m.no, onOpen));

  // 未能定位 Range 的批注：仍在文首附近插数字，避免「有批注无角标」
  const painted = new Set(planned.map((p) => p.m.no));
  markers
    .filter((m) => !painted.has(m.no))
    .forEach((m) => {
      const badge = makeAnnoBadge(m.no, onOpen);
      badge.title = `批注 ${m.no}（未能精确高亮）：「${m.annotation.quoteText}」`;
      badge.className =
        'geo-lake-anno-badge mx-0.5 inline-flex h-5 min-w-5 cursor-pointer items-center justify-center rounded-full border border-amber-500 bg-amber-200 px-1 align-super text-[11px] font-semibold text-amber-950';
      const first = root.querySelector('p, div, h1, h2, h3, li, span') || root;
      first.insertAdjacentElement('afterbegin', badge);
    });
}

function resolvePaintRoot(wrap: HTMLElement | null): HTMLElement | null {
  if (!wrap) return null;
  const host = wrap.querySelector('.lakex-host') as HTMLElement | null;
  return (
    (host?.querySelector('.ne-viewer-body, .ne-editor, .lake-content, [contenteditable]') as HTMLElement | null) ||
    host ||
    wrap
  );
}

/** 在 Lake 原文只读预览上划选 + 浮动「添加批注」；已有批注直接叠在 Lake 排版上 */
export function SelectableReviewBody({
  contentBody,
  enabled,
  onAdd,
  contentKey,
  annotations = [],
  onOpenAnnotation,
  editableReviewId = null,
  onAnnotationsChange,
}: {
  contentBody?: string;
  enabled: boolean;
  onAdd?: (draft: Omit<SelectionDraft, 'top' | 'left'>) => void;
  /** 换稿时重挂载编辑器 */
  contentKey?: string | number;
  /** 已有划词批注：叠在 Lake 原文上显示高亮与数字 */
  annotations?: GeoContentReviewAnnotation[];
  onOpenAnnotation?: (no: number) => void;
  /** 本轮可改删时传入 reviewId */
  editableReviewId?: number | null;
  onAnnotationsChange?: (next: GeoContentReviewAnnotation[]) => void;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const hostWrapRef = useRef<HTMLDivElement>(null);
  const [draft, setDraft] = useState<SelectionDraft | null>(null);
  const [editorReady, setEditorReady] = useState(false);
  const [tip, setTip] = useState<{ no: number; top: number; left: number } | null>(null);
  const body = contentBody || '';
  const hasBody = body.trim().length > 0;
  const annoKey = useMemo(
    () => annotations.map((a) => `${a.id}:${a.quoteText}:${a.commentText}`).join('|'),
    [annotations],
  );

  const clearDraft = useCallback(() => setDraft(null), []);
  const closeTip = useCallback(() => setTip(null), []);

  useEffect(() => {
    void prefetchLakexAssets();
  }, []);

  useEffect(() => {
    setEditorReady(false);
    setTip(null);
  }, [contentKey, body]);

  useEffect(() => {
    const onDocDown = (e: MouseEvent) => {
      const t = e.target as Node;
      const el = t instanceof Element ? t : t.parentElement;
      // 点「添加批注」/批注浮层/数字角标/antd 弹层时不要清，否则删除确认点不中
      if (
        el?.closest?.(
          '[data-geo-anno-add], [data-geo-anno-tip], [data-geo-anno-badge], .ant-popover, .ant-popconfirm, .ant-modal-root, .ant-modal-wrap',
        )
      ) {
        return;
      }
      if (!rootRef.current?.contains(t)) {
        clearDraft();
        closeTip();
        return;
      }
      if (enabled) clearDraft();
      closeTip();
    };
    document.addEventListener('mousedown', onDocDown);
    return () => document.removeEventListener('mousedown', onDocDown);
  }, [enabled, clearDraft, closeTip]);

  useEffect(() => {
    if (!editorReady || !hostWrapRef.current) return;
    const paintRoot = resolvePaintRoot(hostWrapRef.current);
    if (!paintRoot) return;
    const open = (no: number, anchor: HTMLElement) => {
      const badgeRect = anchor.getBoundingClientRect();
      setTip({
        no,
        top: badgeRect.bottom + 6,
        left: Math.min(Math.max(8, badgeRect.left - 20), window.innerWidth - 300),
      });
      setDraft(null);
      onOpenAnnotation?.(no);
    };
    paintLakeAnnotations(paintRoot, annotations, open);
    // Lake 异步排版 / 虚拟滚动会冲掉 mark，多刷几轮保证批注保存后高亮仍在
    const timers = [80, 280, 600, 1200].map((ms) =>
      window.setTimeout(() => paintLakeAnnotations(paintRoot, annotations, open), ms),
    );
    return () => {
      timers.forEach((t) => window.clearTimeout(t));
      clearLakeAnnoMarks(paintRoot);
    };
  }, [editorReady, annoKey, annotations, onOpenAnnotation]);

  if (!hasBody) {
    return <div className='p-4 text-sm text-neutral-400'>暂无提审原文，无法划词</div>;
  }

  const tipAnno = tip != null ? annotations[tip.no - 1] : null;
  // 本轮审核中可改删：reviewId 缺失时也放开（接口偶发不回带 reviewId）
  const tipCanEdit =
    tipAnno != null &&
    editableReviewId != null &&
    (tipAnno.reviewId == null || Number(tipAnno.reviewId) === Number(editableReviewId));

  return (
    <div
      ref={rootRef}
      className='relative flex h-full min-h-0 flex-col bg-white'
      onMouseUp={(e) => {
        if (!enabled || !onAdd) return;
        const t = e.target as Element | null;
        if (t?.closest?.('[data-geo-anno-add], [data-geo-anno-tip], [data-geo-anno-badge]')) return;
        window.requestAnimationFrame(() => {
          const paintRoot = resolvePaintRoot(hostWrapRef.current);
          const next = captureSelectionIn(paintRoot);
          if (next) {
            // 用视口坐标，按钮 Portal 到 body，避开 Lake 内部 stacking
            const sel = window.getSelection();
            if (sel && sel.rangeCount > 0) {
              const rect = sel.getRangeAt(0).getBoundingClientRect();
              if (rect.width > 0 || rect.height > 0) {
                next.top = rect.bottom + 8;
                next.left = Math.min(Math.max(8, rect.left), window.innerWidth - 120);
              }
            }
            setTip(null);
            setDraft(next);
          }
        });
      }}
    >
      <div className='shrink-0 border-b border-neutral-100 px-3 py-1.5 text-xs text-neutral-500'>
        {enabled
          ? '划选原文后点「添加批注」；点高亮旁数字可查看/修改/删除'
          : annotations.length > 0
            ? '点高亮旁数字查看批注'
            : 'Lake 原文预览'}
        {annotations.length > 0 ? ` · 批注 ${annotations.length}` : ''}
      </div>
      <div
        ref={hostWrapRef}
        className='relative min-h-0 flex-1 overflow-auto select-text'
      >
        {!editorReady ? (
          <div className='absolute inset-0 z-10 flex items-center justify-center bg-white/70 text-sm text-neutral-400'>
            加载原文…
          </div>
        ) : null}
        <LakexEditorHost
          key={contentKey ?? `lakex-anno-${body.length}`}
          value={body}
          readOnly
          onReady={() => setEditorReady(true)}
        />
        {enabled && onAdd && draft
          ? createPortal(
              <div
                data-geo-anno-add
                className='pointer-events-auto'
                style={{
                  position: 'fixed',
                  top: Math.min(Math.max(8, draft.top), window.innerHeight - 48),
                  left: Math.min(Math.max(8, draft.left), window.innerWidth - 120),
                  zIndex: 11000,
                }}
                onMouseDown={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                }}
              >
                <Button
                  type='primary'
                  size='small'
                  onMouseDown={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                  }}
                  onClick={(e) => {
                    e.stopPropagation();
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
              </div>,
              document.body,
            )
          : null}
        {tip && tipAnno
          ? createPortal(
              <div
                data-geo-anno-tip
                className='pointer-events-auto max-w-xs rounded-lg border border-amber-200 bg-white p-3 shadow-lg'
                style={{
                  position: 'fixed',
                  top: Math.min(Math.max(8, tip.top), window.innerHeight - 200),
                  left: Math.min(Math.max(8, tip.left), window.innerWidth - 300),
                  zIndex: 11010,
                }}
                onMouseDown={(e) => e.stopPropagation()}
              >
                <AnnotationTipContent
                  no={tip.no}
                  annotation={tipAnno}
                  canEdit={tipCanEdit}
                  onUpdated={(next) => {
                    onAnnotationsChange?.(annotations.map((a) => (a.id === next.id ? next : a)));
                  }}
                  onDeleted={(id) => {
                    onAnnotationsChange?.(annotations.filter((a) => a.id !== id));
                    closeTip();
                  }}
                  onClose={closeTip}
                />
              </div>,
              document.body,
            )
          : null}
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
            editableReviewId={editableReviewId}
            onAnnotationsChange={sync}
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
