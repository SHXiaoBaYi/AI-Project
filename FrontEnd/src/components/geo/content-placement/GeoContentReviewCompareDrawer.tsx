import { useEffect, useMemo, useState } from 'react';
import { Checkbox, Drawer, Segmented, Spin, Tag, message } from 'antd';
import { LakexEditorHost } from '@/components/lakex/LakexEditorHost';
import { getGeoContentReviewCompareApi } from '@/api/geo';
import type { GeoContentReviewCompare, GeoContentReviewRound } from '@/types/geo';
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

type Props = {
  open: boolean;
  placementId: number | null;
  focusReviewId?: number | null;
  onClose: () => void;
};

function versionKeyOf(round: GeoContentReviewRound): VersionKey {
  return `review:${round.reviewId!}`;
}

function labelOf(round: GeoContentReviewRound, latestRoundNo?: number | null) {
  const base = `第${round.roundNo ?? '-'}轮提审稿`;
  // 最新一轮提审稿就是当前稿
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

function SidePreview({
  round,
  side,
  latestRoundNo,
}: {
  round: GeoContentReviewRound;
  side: '左' | '右';
  latestRoundNo?: number | null;
}) {
  const meta = round.status ? STATUS_META[round.status] || { text: round.status, color: 'default' } : null;
  return (
    <div className='flex h-full min-h-0 flex-col'>
      <div className='shrink-0 border-b border-neutral-100 px-3 py-2'>
        <div className='mb-1 flex flex-wrap items-center gap-2'>
          <span className='text-xs text-neutral-400'>{side}</span>
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
        </div>
        <div className='text-sm font-medium'>{round.contentTitle || '（无标题）'}</div>
      </div>
      <div className='min-h-0 flex-1 overflow-hidden'>
        {round.hasSnapshot ? (
          <LakexEditorHost
            key={`${versionKeyOf(round)}-${round.contentVersion ?? 0}-preview`}
            value={round.contentBody || ''}
            readOnly
          />
        ) : (
          <div className='p-4 text-sm text-amber-700'>该轮没有文章快照，无法对照当时正文（重新提审后会保存）</div>
        )}
      </div>
    </div>
  );
}

export function GeoContentReviewCompareDrawer({ open, placementId, focusReviewId = null, onClose }: Props) {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<GeoContentReviewCompare | null>(null);
  const [checked, setChecked] = useState<VersionKey[]>([]);
  const [view, setView] = useState<'diff' | 'side' | 'opinions'>('diff');

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
      return;
    }
    setLoading(true);
    void getGeoContentReviewCompareApi(placementId)
      .then((res) => {
        setData(res);
        const rounds = res.rounds ?? [];
        const focus =
          (focusReviewId != null && rounds.find((r) => r.reviewId === focusReviewId)) ||
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
        setView('diff');
      })
      .catch((e) => {
        message.error(e instanceof Error ? e.message : '加载对照数据失败');
        setData(null);
      })
      .finally(() => setLoading(false));
  }, [open, placementId, focusReviewId]);

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

  return (
    <Drawer
      open={open}
      width='96vw'
      placement='right'
      destroyOnHidden
      onClose={onClose}
      styles={{
        body: { padding: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' },
        header: { paddingBlock: 12 },
      }}
      title={
        <div className='flex min-w-0 flex-wrap items-center gap-3'>
          <div className='flex min-w-0 flex-col'>
            <span className='font-medium'>文章对照</span>
            <span className='truncate text-xs font-normal text-neutral-500'>
              {data?.topicName || '话题'} · {data?.targetQuestion || '目标问题'} · 最新一轮提审稿即当前稿
            </span>
          </div>
          <Segmented
            size='small'
            value={view}
            onChange={(v) => setView(v as 'diff' | 'side' | 'opinions')}
            options={[
              { label: '差异块', value: 'diff' },
              { label: '左右原文', value: 'side' },
              { label: '审核意见', value: 'opinions' },
            ]}
          />
        </div>
      }
    >
      {loading || !data ? (
        <div className='flex flex-1 items-center justify-center py-24'>
          <Spin tip='加载文章历史…' />
        </div>
      ) : versions.length === 0 ? (
        <div className='flex flex-1 items-center justify-center text-neutral-400'>暂无可对照的提审稿</div>
      ) : (
        <div className='flex min-h-0 flex-1'>
          <aside className='flex w-64 shrink-0 flex-col border-r border-neutral-100'>
            <div className='border-b border-neutral-100 px-3 py-2 text-xs text-neutral-500'>
              勾选 <b>两轮提审稿</b>。默认：当前轮 vs 上一轮。旧在左、新在右。
            </div>
            <div className='min-h-0 flex-1 overflow-auto p-2'>
              {versions.map((round) => {
                const key = versionKeyOf(round);
                const checkedNow = checked.includes(key);
                const meta = round.status ? STATUS_META[round.status] || { text: '-', color: 'default' } : null;
                const order = checked.indexOf(key);
                const isLatest = latestRoundNo != null && round.roundNo === latestRoundNo;
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
            {checked.length < 2 ? (
              <div className='flex flex-1 items-center justify-center text-sm text-neutral-400'>
                请再勾选 {2 - checked.length} 轮，才能对照文章
              </div>
            ) : !pair ? (
              <div className='flex flex-1 items-center justify-center text-sm text-neutral-400'>选中的版本无效</div>
            ) : view === 'diff' ? (
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
            ) : view === 'side' ? (
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
            ) : (
              <div className='grid min-h-0 flex-1 grid-cols-1 gap-0 overflow-auto lg:grid-cols-2'>
                {[pair.left, pair.right].map((round, idx) => (
                  <div
                    key={versionKeyOf(round)}
                    className={`border-b border-neutral-100 p-4 lg:border-b-0 ${idx === 0 ? 'lg:border-r' : ''}`}
                  >
                    <div className='mb-2 font-medium'>
                      {idx === 0 ? '左 · ' : '右 · '}
                      {labelOf(round, latestRoundNo)}
                    </div>
                    {(round.reviewers ?? []).length === 0 ? (
                      <p className='text-sm text-neutral-400'>暂无意见</p>
                    ) : (
                      <ul className='m-0 list-none space-y-2 p-0'>
                        {(round.reviewers ?? []).map((item) => {
                          const rm = RESULT_META[item.result || 'pending'] || RESULT_META.pending;
                          return (
                            <li
                              key={`${item.userId}-${item.actedAt || item.result}`}
                              className='rounded border border-neutral-100 px-3 py-2 text-sm'
                            >
                              <div className='mb-1 flex flex-wrap items-center gap-1'>
                                <span className='font-medium'>{item.userName || item.userId}</span>
                                <Tag
                                  color={rm.color}
                                  className='mr-0'
                                >
                                  {rm.text}
                                </Tag>
                              </div>
                              <div>{item.comment?.trim() || '（无文字意见）'}</div>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </Drawer>
  );
}
