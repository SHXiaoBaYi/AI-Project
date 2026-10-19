import { useEffect, useMemo, useState } from 'react';
import { Button, Checkbox, Drawer, Input, Modal, Segmented, Space, Spin, Tag, message } from 'antd';
import {
  GeoReviewAnnotationReadonly,
  ReviewAnnotationList,
  SelectableReviewBody,
} from '@/components/geo/content-placement/GeoReviewAnnotations';
import { prefetchLakexAssets } from '@/components/lakex/loadLakexAssets';
import {
  addGeoContentReviewAnnotationApi,
  decideGeoContentReviewApi,
  deleteGeoContentReviewAnnotationApi,
  getGeoContentReviewCompareApi,
} from '@/api/geo';
import type { GeoContentReviewAnnotation, GeoContentReviewCompare, GeoContentReviewRound } from '@/types/geo';
import { diffBlocks, lakeToBlocks, titleDiff, type DiffBlock } from '@/utils/articleDiff';

const STATUS_META: Record<string, { text: string; color: string }> = {
  pending: { text: '审核中', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已退回', color: 'error' },
};

const RESULT_META: Record<string, { text: string; color: string }> = {
  pending: { text: '待审', color: 'default' },
  approved: { text: '通过', color: 'success' },
  rejected: { text: '退回', color: 'error' },
};

type VersionKey = `review:${number}`;

type ViewMode = 'diff' | 'side' | 'annotate';

type Props = {
  open: boolean;
  placementId: number | null;
  focusReviewId?: number | null;
  /** 传入则进入审核：可对照历史快照并在本轮通过/退回 */
  reviewId?: number | null;
  onClose: () => void;
};

function versionKeyOf(round: GeoContentReviewRound): VersionKey {
  return `review:${round.reviewId!}`;
}

function stageText(stageNo?: number | null) {
  if (stageNo === 2) return '二审';
  if (stageNo === 1) return '一审';
  return '';
}

function labelOf(round: GeoContentReviewRound, latestRoundNo?: number | null) {
  const stage = stageText(round.stageNo);
  const base = `${stage ? `${stage}·` : ''}第${round.roundNo ?? '-'}轮提审稿`;
  if (latestRoundNo != null && round.roundNo === latestRoundNo) {
    return `${base}（当前稿）`;
  }
  return base;
}

/** 对照排序：轮次小的在左（旧），轮次大的在右（新） */
function sortKeysForCompare(
  a: VersionKey,
  b: VersionKey,
  byKey: Map<VersionKey, GeoContentReviewRound>,
): [VersionKey, VersionKey] {
  const ra = byKey.get(a);
  const rb = byKey.get(b);
  const score = (r?: GeoContentReviewRound) => r?.roundNo ?? r?.reviewId ?? 0;
  return score(ra) <= score(rb) ? [a, b] : [b, a];
}

/** 只列提审轮次；最新一轮无快照时用库内最新正文补齐（二者本就是同一稿） */
function withLatestAsCurrent(
  rounds: GeoContentReviewRound[],
  currentDraft?: GeoContentReviewRound | null,
): GeoContentReviewRound[] {
  if (rounds.length === 0) return [];
  const maxRound = Math.max(...rounds.map((r) => r.roundNo ?? 0));
  return rounds.map((r) => {
    const row: GeoContentReviewRound = { ...r, entryType: 'review' };
    if (r.roundNo !== maxRound || !currentDraft) return row;
    if (row.hasSnapshot) return row;
    return {
      ...row,
      contentTitle: currentDraft.contentTitle || row.contentTitle,
      contentBody: currentDraft.contentBody || row.contentBody,
      contentFormat: currentDraft.contentFormat || row.contentFormat,
      contentVersion: currentDraft.contentVersion ?? row.contentVersion,
      hasSnapshot: !!(currentDraft.contentTitle || currentDraft.contentBody),
    };
  });
}

function DiffBlockCard({ block }: { block: DiffBlock }) {
  if (block.type === 'equal') {
    return (
      <div className='rounded border border-neutral-100 bg-white px-3 py-2 text-sm text-neutral-700'>{block.text}</div>
    );
  }
  if (block.type === 'remove') {
    return (
      <div className='rounded border border-red-200 bg-red-50 px-3 py-2'>
        <div className='mb-1 text-xs font-medium text-red-600'>已删除</div>
        <div className='text-sm text-red-800 line-through'>{block.text}</div>
      </div>
    );
  }
  if (block.type === 'add') {
    return (
      <div className='rounded border border-emerald-200 bg-emerald-50 px-3 py-2'>
        <div className='mb-1 text-xs font-medium text-emerald-700'>新增</div>
        <div className='text-sm text-emerald-900'>{block.text}</div>
      </div>
    );
  }
  return (
    <div className='rounded border border-amber-200 bg-amber-50 px-3 py-2'>
      <div className='mb-1 text-xs font-medium text-amber-700'>有改动</div>
      <div className='mb-2 text-sm text-red-700/90 line-through'>{block.before}</div>
      <div className='text-sm text-emerald-900'>{block.after}</div>
    </div>
  );
}

function ReviewHistoryList({
  versions,
  latestRoundNo,
  focusReviewId,
}: {
  versions: GeoContentReviewRound[];
  latestRoundNo?: number | null;
  focusReviewId?: number | null;
}) {
  if (versions.length === 0) {
    return <div className='flex flex-1 items-center justify-center text-sm text-neutral-400'>暂无审核记录</div>;
  }
  return (
    <div className='min-h-0 flex-1 overflow-auto px-4 py-3'>
      <ul className='m-0 list-none space-y-3 p-0'>
        {versions.map((round) => {
          const meta = round.status ? STATUS_META[round.status] || { text: round.status, color: 'default' } : null;
          const focused = focusReviewId != null && round.reviewId === focusReviewId;
          return (
            <li
              key={versionKeyOf(round)}
              className={`rounded border px-3 py-3 ${focused ? 'border-blue-300 bg-blue-50/60' : 'border-neutral-100 bg-white'}`}
            >
              <div className='mb-2 flex flex-wrap items-center gap-2'>
                <span className='font-medium'>{labelOf(round, latestRoundNo)}</span>
                {focused ? (
                  <Tag
                    color='blue'
                    className='mr-0'
                  >
                    本轮待审
                  </Tag>
                ) : null}
                {meta ? (
                  <Tag
                    color={meta.color}
                    className='mr-0'
                  >
                    {meta.text}
                  </Tag>
                ) : null}
                <span className='text-xs text-neutral-400'>{round.createTime || ''}</span>
              </div>
              {round.submitRemark ? (
                <p className='mb-2 text-xs text-neutral-500'>提审说明：{round.submitRemark}</p>
              ) : null}
              {(round.reviewers ?? []).length === 0 ? (
                <p className='mb-0 text-sm text-neutral-400'>暂无整篇审核意见</p>
              ) : (
                <ul className='m-0 list-none space-y-2 p-0'>
                  {(round.reviewers ?? []).map((item) => {
                    const rm = RESULT_META[item.result || 'pending'] || RESULT_META.pending;
                    return (
                      <li
                        key={`${round.reviewId}-${item.userId}-${item.actedAt || item.result}`}
                        className='rounded border border-neutral-100 bg-white px-3 py-2 text-sm'
                      >
                        <div className='mb-1 flex flex-wrap items-center gap-1'>
                          <span className='font-medium'>{item.userName || item.userId}</span>
                          <Tag
                            color={rm.color}
                            className='mr-0'
                          >
                            {rm.text}
                          </Tag>
                          {item.actedAt ? <span className='text-xs text-neutral-400'>{item.actedAt}</span> : null}
                        </div>
                        <div className='text-neutral-700'>{item.comment?.trim() || '（无文字意见）'}</div>
                      </li>
                    );
                  })}
                </ul>
              )}
              {(round.annotations ?? []).length > 0 ? (
                <div className='mt-2'>
                  <div className='mb-1 text-xs font-medium text-amber-800'>
                    划词批注（{(round.annotations ?? []).length}）· 请在左侧原文点数字查看
                  </div>
                  <GeoReviewAnnotationReadonly
                    annotations={round.annotations ?? []}
                    contentBody={round.contentBody || ''}
                    showOriginal={false}
                  />
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function SidePreview({
  round,
  side,
  latestRoundNo,
  showAnnotations = true,
}: {
  round: GeoContentReviewRound;
  side?: string;
  latestRoundNo?: number | null;
  /** 有划词批注时在原文区直接标数字，不再放到侧栏 */
  showAnnotations?: boolean;
}) {
  const meta = round.status ? STATUS_META[round.status] || { text: round.status, color: 'default' } : null;
  const annotations = showAnnotations ? (round.annotations ?? []) : [];
  return (
    <div className='flex h-full min-h-0 flex-col'>
      <div className='shrink-0 border-b border-neutral-100 px-3 py-2'>
        <div className='mb-1 flex flex-wrap items-center gap-2'>
          {side ? <span className='text-xs text-neutral-400'>{side}</span> : null}
          <span className='font-medium'>{labelOf(round, latestRoundNo)}</span>
          {meta ? (
            <Tag
              color={meta.color}
              className='mr-0'
            >
              {meta.text}
            </Tag>
          ) : null}
          <span className='text-xs text-neutral-400'>v{round.contentVersion ?? 0}</span>
          {annotations.length > 0 ? (
            <Tag
              color='gold'
              className='mr-0'
            >
              批注 {annotations.length}
            </Tag>
          ) : null}
        </div>
        <div className='text-sm font-medium'>{round.contentTitle || '（无标题）'}</div>
      </div>
      <div className='min-h-0 flex-1 overflow-hidden'>
        {!round.hasSnapshot && !(round.contentBody || '').trim() ? (
          <div className='p-4 text-sm text-amber-700'>该轮没有文章快照，无法对照当时正文（重新提审后会保存）</div>
        ) : (
          <SelectableReviewBody
            contentKey={`${versionKeyOf(round)}-${round.contentVersion ?? 0}-preview`}
            contentBody={round.contentBody || ''}
            enabled={false}
            annotations={annotations}
          />
        )}
      </div>
    </div>
  );
}

export function GeoContentReviewCompareDrawer({
  open,
  placementId,
  focusReviewId = null,
  reviewId = null,
  onClose,
}: Props) {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<GeoContentReviewCompare | null>(null);
  const [checked, setChecked] = useState<VersionKey[]>([]);
  const [view, setView] = useState<ViewMode>('diff');
  const [opinion, setOpinion] = useState('');
  const [acting, setActing] = useState(false);
  const [annoDraft, setAnnoDraft] = useState<{
    quoteText: string;
    quotePrefix: string;
    quoteSuffix: string;
    startOffset: number | null;
    endOffset: number | null;
  } | null>(null);
  const [annoComment, setAnnoComment] = useState('');
  const [annoSaving, setAnnoSaving] = useState(false);
  const reviewing = reviewId != null;
  const highlightReviewId = focusReviewId ?? reviewId;

  const versions = useMemo(() => {
    if (!data) return [] as GeoContentReviewRound[];
    return withLatestAsCurrent(data.rounds ?? [], data.currentDraft).filter((r) => r.reviewId != null);
  }, [data]);

  const latestRoundNo = useMemo(() => {
    if (versions.length === 0) return null;
    return Math.max(...versions.map((r) => r.roundNo ?? 0));
  }, [versions]);

  const byKey = useMemo(() => {
    const map = new Map<VersionKey, GeoContentReviewRound>();
    for (const v of versions) map.set(versionKeyOf(v), v);
    return map;
  }, [versions]);

  useEffect(() => {
    if (!open || placementId == null) {
      setData(null);
      setChecked([]);
      setView('diff');
      setOpinion('');
      setActing(false);
      setAnnoDraft(null);
      setAnnoComment('');
      return;
    }
    setLoading(true);
    setOpinion('');
    setAnnoDraft(null);
    setAnnoComment('');
    void prefetchLakexAssets();
    void getGeoContentReviewCompareApi(placementId)
      .then((res) => {
        setData(res);
        const rounds = res.rounds ?? [];
        const focus =
          (highlightReviewId != null && rounds.find((r) => r.reviewId === highlightReviewId)) ||
          (res.currentReviewId != null && rounds.find((r) => r.reviewId === res.currentReviewId)) ||
          rounds[rounds.length - 1] ||
          null;
        // 默认：当前审核轮（最新一轮/当前稿）vs 上一轮
        if (focus?.reviewId != null) {
          const prev = [...rounds]
            .filter((r) => r.reviewId != null && r.reviewId !== focus.reviewId)
            .filter((r) => (r.roundNo ?? 0) < (focus.roundNo ?? 0))
            .sort((a, b) => (a.roundNo ?? 0) - (b.roundNo ?? 0));
          const lastPrev = prev[prev.length - 1];
          if (lastPrev?.reviewId != null) {
            setChecked([`review:${lastPrev.reviewId}`, `review:${focus.reviewId}`]);
          } else {
            setChecked([`review:${focus.reviewId}`]);
          }
        } else if (rounds.length >= 2) {
          const a = rounds[rounds.length - 2];
          const b = rounds[rounds.length - 1];
          setChecked([`review:${a.reviewId!}`, `review:${b.reviewId!}`]);
        } else if (rounds[0]?.reviewId != null) {
          setChecked([`review:${rounds[0].reviewId}`]);
        } else {
          setChecked([]);
        }
        setView(reviewing ? 'annotate' : 'diff');
      })
      .catch((e) => {
        message.error(e instanceof Error ? e.message : '加载对照数据失败');
        setData(null);
      })
      .finally(() => setLoading(false));
  }, [open, placementId, highlightReviewId, reviewing]);

  const pair = useMemo(() => {
    if (checked.length !== 2) return null;
    const [leftKey, rightKey] = sortKeysForCompare(checked[0], checked[1], byKey);
    const left = byKey.get(leftKey) ?? null;
    const right = byKey.get(rightKey) ?? null;
    if (!left || !right) return null;
    return { leftKey, rightKey, left, right };
  }, [checked, byKey]);

  const diffResult = useMemo(() => {
    if (!pair) return null;
    const title = titleDiff(pair.left.contentTitle, pair.right.contentTitle);
    const blocks = diffBlocks(lakeToBlocks(pair.left.contentBody), lakeToBlocks(pair.right.contentBody));
    const changed = blocks.filter((b) => b.type !== 'equal');
    return { title, blocks, changedCount: changed.length };
  }, [pair]);

  const singleRound = useMemo(() => {
    if (pair) return null;
    if (checked.length === 1) return byKey.get(checked[0]) ?? null;
    if (versions.length === 1) return versions[0];
    return null;
  }, [pair, checked, byKey, versions]);

  const onCheck = (key: VersionKey, next: boolean) => {
    setChecked((prev) => {
      if (next) {
        if (prev.includes(key)) return prev;
        if (prev.length >= 2) {
          message.info('对照最多选两轮，已替换先勾选的一轮');
          return [prev[1], key];
        }
        return [...prev, key];
      }
      return prev.filter((k) => k !== key);
    });
  };

  const focusRound = useMemo(() => {
    if (reviewId == null) return null;
    return versions.find((r) => r.reviewId === reviewId) ?? null;
  }, [versions, reviewId]);

  const focusAnnotations = focusRound?.annotations ?? [];

  const patchRoundAnnotations = (reviewKey: number, next: GeoContentReviewAnnotation[]) => {
    setData((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        rounds: (prev.rounds ?? []).map((r) => (r.reviewId === reviewKey ? { ...r, annotations: next } : r)),
      };
    });
  };

  const saveAnnotation = async () => {
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
      patchRoundAnnotations(reviewId, [...focusAnnotations, created]);
      setAnnoDraft(null);
      setAnnoComment('');
      message.success('已添加划词批注');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '添加批注失败');
    } finally {
      setAnnoSaving(false);
    }
  };

  const removeAnnotation = async (id: number) => {
    if (reviewId == null) return;
    try {
      await deleteGeoContentReviewAnnotationApi(id);
      patchRoundAnnotations(
        reviewId,
        focusAnnotations.filter((a) => a.id !== id),
      );
      message.success('已删除');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  const decide = async (action: 'approve' | 'reject') => {
    if (reviewId == null) return;
    if (action === 'reject' && !opinion.trim() && focusAnnotations.length === 0) {
      message.warning('退回修改时请填写意见，或先划词批注');
      return;
    }
    setActing(true);
    try {
      await decideGeoContentReviewApi({
        reviewId,
        action,
        comment: opinion.trim() || undefined,
      });
      message.success(action === 'approve' ? '已通过' : '已退回');
      onClose();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '审核失败');
    } finally {
      setActing(false);
    }
  };

  return (
    <Drawer
      open={open}
      width='96vw'
      placement='right'
      destroyOnHidden
      maskClosable={!reviewing}
      onClose={onClose}
      styles={{
        body: { padding: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' },
        header: { paddingBlock: 12 },
      }}
      extra={
        reviewing ? (
          <Space>
            <Button
              type='primary'
              loading={acting}
              onClick={() => void decide('approve')}
            >
              通过
            </Button>
            <Button
              loading={acting}
              onClick={() => void decide('reject')}
            >
              退回修改
            </Button>
          </Space>
        ) : null
      }
      title={
        <div className='flex min-w-0 flex-wrap items-center gap-3'>
          <div className='flex min-w-0 flex-col'>
            <span className='font-medium'>{reviewing ? '审核文章' : '文章对照'}</span>
            <span className='truncate text-xs font-normal text-neutral-500'>
              {data?.topicName || '话题'} · {data?.targetQuestion || '目标问题'} ·
              左侧勾选两轮看差异，右侧为全部审核记录
            </span>
          </div>
          <Segmented
            size='small'
            value={view}
            onChange={(v) => setView(v as ViewMode)}
            options={[
              ...(reviewing ? [{ label: '划词审核', value: 'annotate' as const }] : []),
              { label: '差异块', value: 'diff' },
              { label: '左右原文', value: 'side' },
            ]}
          />
        </div>
      }
    >
      {loading ? (
        <div className='flex flex-1 items-center justify-center py-24'>
          <Spin tip='加载文章历史…' />
        </div>
      ) : !data ? (
        <div className='flex flex-1 items-center justify-center text-sm text-neutral-400'>对照数据加载失败</div>
      ) : versions.length === 0 ? (
        <div className='flex flex-1 items-center justify-center text-neutral-400'>暂无可对照的提审稿</div>
      ) : (
        <>
          <div className='flex min-h-0 flex-1'>
            <aside className='flex w-64 shrink-0 flex-col border-r border-neutral-100'>
              <div className='border-b border-neutral-100 px-3 py-2 text-xs text-neutral-500'>
                勾选 <b>两轮提审稿</b>看快照差异。默认：当前轮 vs 上一轮。
              </div>
              <div className='min-h-0 flex-1 overflow-auto p-2'>
                {versions.map((round) => {
                  const key = versionKeyOf(round);
                  const checkedNow = checked.includes(key);
                  const meta = round.status ? STATUS_META[round.status] || { text: '-', color: 'default' } : null;
                  const order = checked.indexOf(key);
                  const isLatest = latestRoundNo != null && round.roundNo === latestRoundNo;
                  const focused = highlightReviewId != null && round.reviewId === highlightReviewId;
                  return (
                    <label
                      key={key}
                      className={`mb-1 flex cursor-pointer gap-2 rounded border px-2 py-2 ${
                        checkedNow ? 'border-blue-300 bg-blue-50' : 'border-transparent hover:bg-neutral-50'
                      }`}
                    >
                      <Checkbox
                        checked={checkedNow}
                        onChange={(e) => onCheck(key, e.target.checked)}
                        className='mt-0.5'
                      />
                      <div className='min-w-0 flex-1'>
                        <div className='mb-0.5 flex flex-wrap items-center gap-1'>
                          <span className='text-sm font-medium'>{labelOf(round, latestRoundNo)}</span>
                          {order >= 0 ? (
                            <Tag
                              color='blue'
                              className='mr-0'
                            >
                              {order === 0 ? '已选1' : '已选2'}
                            </Tag>
                          ) : null}
                          {focused ? (
                            <Tag
                              color='blue'
                              className='mr-0'
                            >
                              本轮
                            </Tag>
                          ) : null}
                          {isLatest ? (
                            <Tag
                              color='cyan'
                              className='mr-0'
                            >
                              当前
                            </Tag>
                          ) : null}
                          {meta ? (
                            <Tag
                              color={meta.color}
                              className='mr-0'
                            >
                              {meta.text}
                            </Tag>
                          ) : null}
                        </div>
                        <div className='truncate text-xs text-neutral-500'>
                          {round.contentTitle || '（无标题）'} · v{round.contentVersion ?? 0}
                        </div>
                        {!round.hasSnapshot ? (
                          <div className='text-xs text-amber-600'>无文章快照（需重新提审后才会保存）</div>
                        ) : null}
                      </div>
                    </label>
                  );
                })}
              </div>
            </aside>

            <div className='flex min-h-0 min-w-0 flex-1 flex-col'>
              {view === 'annotate' && reviewing && focusRound ? (
                <div className='grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-[minmax(0,1fr)_260px]'>
                  <section className='flex min-h-0 flex-col border-b border-neutral-100 lg:border-r lg:border-b-0'>
                    <div className='shrink-0 border-b border-neutral-100 px-3 py-2'>
                      <div className='mb-1 flex flex-wrap items-center gap-2'>
                        <span className='font-medium'>{labelOf(focusRound, latestRoundNo)}</span>
                        <Tag
                          color='processing'
                          className='mr-0'
                        >
                          原文划词
                        </Tag>
                        {focusAnnotations.length > 0 ? (
                          <Tag
                            color='gold'
                            className='mr-0'
                          >
                            批注 {focusAnnotations.length}
                          </Tag>
                        ) : null}
                      </div>
                      <div className='text-sm font-medium'>{focusRound.contentTitle || '（无标题）'}</div>
                    </div>
                    <div className='min-h-0 flex-1'>
                      {focusRound.hasSnapshot || focusRound.contentBody || data.currentDraft?.contentBody ? (
                        <SelectableReviewBody
                          contentKey={`review-${focusRound.reviewId}-${focusRound.contentVersion ?? 0}`}
                          contentBody={focusRound.contentBody || data.currentDraft?.contentBody || ''}
                          enabled
                          annotations={focusAnnotations}
                          editableReviewId={reviewId}
                          onAnnotationsChange={(next) => {
                            if (reviewId != null) patchRoundAnnotations(reviewId, next);
                          }}
                          onAdd={(d) => {
                            setAnnoDraft(d);
                            setAnnoComment('');
                          }}
                        />
                      ) : (
                        <div className='p-4 text-sm text-amber-700'>
                          该轮没有文章快照，无法在原文上划词（重新提审后会保存）
                        </div>
                      )}
                    </div>
                  </section>
                  <aside className='flex min-h-0 flex-col overflow-auto px-3 py-2'>
                    <div className='mb-2 text-xs text-neutral-500'>
                      批注列表{focusAnnotations.length > 0 ? `（${focusAnnotations.length}）` : ''}
                      。左侧为撰写排版原文，划选添加；点数字可看批注。
                    </div>
                    <ReviewAnnotationList
                      annotations={focusAnnotations}
                      editableReviewId={reviewId}
                      onDelete={(id) => void removeAnnotation(id)}
                      variant='panel'
                    />
                  </aside>
                </div>
              ) : pair && view === 'diff' ? (
                <div className='min-h-0 flex-1 overflow-auto px-4 py-3'>
                  <div className='mb-3 flex flex-wrap items-center gap-2 text-sm'>
                    <Tag color='default'>{labelOf(pair.left, latestRoundNo)}</Tag>
                    <span className='text-neutral-400'>→</span>
                    <Tag color='processing'>{labelOf(pair.right, latestRoundNo)}</Tag>
                    <span className='text-neutral-500'>
                      {diffResult?.changedCount === 0 ? '正文段落无差异' : `差异块 ${diffResult?.changedCount ?? 0} 处`}
                    </span>
                    <span className='text-xs text-neutral-400'>红=删除 / 绿=新增 / 黄=改动</span>
                  </div>

                  {diffResult?.title && diffResult.title.type !== 'equal' ? (
                    <div className='mb-4'>
                      <div className='mb-1 text-xs font-medium text-neutral-500'>标题</div>
                      <DiffBlockCard block={diffResult.title} />
                    </div>
                  ) : (
                    <div className='mb-4 rounded border border-neutral-100 bg-neutral-50 px-3 py-2 text-sm'>
                      <span className='text-xs text-neutral-400'>标题相同 · </span>
                      {pair.right.contentTitle || '（无标题）'}
                    </div>
                  )}

                  <div className='mb-1 text-xs font-medium text-neutral-500'>正文差异块</div>
                  {(diffResult?.blocks ?? []).length === 0 ? (
                    <div className='text-sm text-neutral-400'>两边都没有可解析正文</div>
                  ) : (
                    <div className='space-y-2'>
                      {(diffResult?.blocks ?? []).map((block, idx) =>
                        block.type === 'equal' ? (
                          <div
                            key={idx}
                            className='rounded border border-dashed border-neutral-100 px-3 py-1.5 text-xs text-neutral-400'
                          >
                            相同段落：{block.text.length > 80 ? `${block.text.slice(0, 80)}…` : block.text}
                          </div>
                        ) : (
                          <DiffBlockCard
                            key={idx}
                            block={block}
                          />
                        ),
                      )}
                    </div>
                  )}
                </div>
              ) : pair && view === 'side' ? (
                <div className='grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-2'>
                  <section className='min-h-0 border-b border-neutral-100 lg:border-r lg:border-b-0'>
                    <SidePreview
                      round={pair.left}
                      side='左'
                      latestRoundNo={latestRoundNo}
                    />
                  </section>
                  <section className='min-h-0'>
                    <SidePreview
                      round={pair.right}
                      side='右'
                      latestRoundNo={latestRoundNo}
                    />
                  </section>
                </div>
              ) : singleRound ? (
                <div className='flex min-h-0 flex-1 flex-col'>
                  <div className='shrink-0 border-b border-neutral-100 px-4 py-2 text-xs text-neutral-500'>
                    {versions.length < 2
                      ? '首次提审，暂无上一轮稿可对照，以下为本轮正文'
                      : '请再勾选一轮提审稿，即可对照快照差异'}
                  </div>
                  <div className='min-h-0 flex-1'>
                    <SidePreview
                      round={singleRound}
                      latestRoundNo={latestRoundNo}
                    />
                  </div>
                </div>
              ) : (
                <div className='flex flex-1 items-center justify-center text-sm text-neutral-400'>
                  请勾选两轮提审稿对照文章
                </div>
              )}
            </div>

            <aside className='flex w-80 shrink-0 flex-col border-l border-neutral-100'>
              <div className='border-b border-neutral-100 px-3 py-2 text-xs text-neutral-500'>历史审核记录</div>
              <ReviewHistoryList
                versions={versions}
                latestRoundNo={latestRoundNo}
                focusReviewId={highlightReviewId}
              />
            </aside>
          </div>
          {reviewing ? (
            <div className='shrink-0 border-t border-neutral-100 bg-white px-4 py-3'>
              <div className='mb-1 text-xs text-neutral-500'>
                本轮总意见
                {opinion.trim() || focusAnnotations.length > 0 ? '' : '（退回时必填；或先划词批注）'}
                {focusAnnotations.length > 0 ? ` · 已批注 ${focusAnnotations.length} 处` : ''}
              </div>
              <Input.TextArea
                value={opinion}
                onChange={(e) => setOpinion(e.target.value)}
                placeholder='可写总意见；退回时也可只靠划词批注'
                autoSize={{ minRows: 2, maxRows: 4 }}
              />
            </div>
          ) : null}
        </>
      )}

      <Modal
        title='添加划词批注'
        open={annoDraft != null}
        confirmLoading={annoSaving}
        onCancel={() => {
          setAnnoDraft(null);
          setAnnoComment('');
        }}
        onOk={() => void saveAnnotation()}
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
    </Drawer>
  );
}
