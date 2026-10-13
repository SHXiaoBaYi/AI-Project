export const AGG_COLOR: Record<string, string> = {
  投放完成: 'success',
  部分投放: 'warning',
  待投放: 'error',
  未投放: 'default',
};

export const STATUS_COLOR: Record<string, string> = {
  投放成功: 'success',
  审核未通过: 'error',
  待投放: 'warning',
  未投放: 'default',
};

export const SOURCE_COLOR: Record<string, string> = {
  导入: 'blue',
  手动新增: 'default',
  AI生成: 'purple',
};

export { isHttpUrl, canOpenExternalInApp, resolveExternalUrl } from '@/utils/externalUrl';

/** 投放管理里是否能打开在线撰写稿（审核通过的待投放也算） */
export function hasOnlineArticle(record: { contentStatus?: string; contentTitle?: string }) {
  const status = record.contentStatus || '';
  return (
    Boolean(record.contentTitle) ||
    status === 'pending_review' ||
    status === 'revising' ||
    status === 'approved' ||
    status === 'stage1_passed' ||
    status === 'queue' ||
    status === 'published'
  );
}

export const derivePlacementProgress = (list: { publishStatus?: string }[]) => {
  const platformCount = list.length;
  const successCount = list.filter((i) => i.publishStatus === '投放成功').length;
  let aggregateStatus = '未投放';
  if (platformCount > 0) {
    if (successCount === platformCount) aggregateStatus = '投放完成';
    else if (successCount > 0) aggregateStatus = '部分投放';
  }
  return {
    aggregateStatus,
    publishProgress: platformCount > 0 ? Math.round((successCount * 100) / platformCount) : null,
    platformCount,
    successCount,
  };
};
