export const GEO_AUTO_FREQ = [
  { label: '每日', value: '每日' },
  { label: '每周', value: '每周' },
] as const;

export const GEO_AUTO_FREQ_DEFAULT = '每日';

export const GEO_AUTO_SCOPE_TYPES = [
  { label: '按话题圈选', value: 'TOPIC' },
  { label: '按问题圈选', value: 'QUESTION' },
] as const;

export const GEO_AUTO_WEEK_DAYS = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
] as const;

/** 截图存证模式（对标模力指数：禁用 / 提及截图 / 全部截图） */
export const GEO_AUTO_SCREENSHOT_MODES = [
  { label: '禁用截图', value: 'DISABLED' },
  { label: '提及截图', value: 'MENTION' },
  { label: '全部截图', value: 'ALL' },
] as const;

/** 提问位置（浏览器采集地域配置，预留给代理/地理位置） */
export const GEO_AUTO_PROBE_REGIONS = ['全国随机', '北京', '上海', '广州', '深圳', '杭州'] as const;

/** 截图类型标签 */
export const GEO_AUTO_SCREENSHOT_TYPE_MAP: Record<string, string> = {
  BROWSER_SHOT: '浏览器整页存证',
  API_SNAPSHOT: 'API 回答快照',
  MANUAL: '人工补录',
};

/** 推荐状态（与日监测口径一致） */
export const GEO_AUTO_RECOMMEND_OPTIONS = ['未出现', '出现且推荐', '出现未推荐'].map((v) => ({
  label: v,
  value: v,
}));

export const GEO_AUTO_SENTIMENTS = ['正面', '中性', '负面'].map((v) => ({ label: v, value: v }));

export const GEO_AUTO_RESULT_STATUS = [
  { label: '成功', value: 'SUCCESS' },
  { label: '人工优先跳过', value: 'SKIPPED_MANUAL' },
  { label: '判定失败', value: 'JUDGE_FAILED' },
  { label: '调用失败', value: 'CALL_FAILED' },
] as const;

export const GEO_AUTO_RESULT_STATUS_MAP: Record<string, { label: string; color: string }> = {
  SUCCESS: { label: '成功', color: 'green' },
  SKIPPED_MANUAL: { label: '人工优先', color: 'default' },
  JUDGE_FAILED: { label: '判定失败', color: 'orange' },
  CALL_FAILED: { label: '调用失败', color: 'red' },
};

export const GEO_AUTO_RUN_STATUS_MAP: Record<string, { label: string; color: string }> = {
  RUNNING: { label: '运行中', color: 'processing' },
  SUCCESS: { label: '完成', color: 'success' },
  PARTIAL: { label: '部分失败', color: 'warning' },
  FAILED: { label: '失败', color: 'error' },
};

/** 推荐状态 -> 矩阵格子点颜色（浅色主题，绿=推荐位、金=提及、灰=未出现） */
export function geoAutoCellStatus(recommendStatus?: string): 'green' | 'gold' | 'default' {
  if (recommendStatus === '出现且推荐') return 'green';
  if (recommendStatus === '出现未推荐') return 'gold';
  return 'default';
}

export function geoAutoSentimentColor(sentiment?: string): string | undefined {
  if (sentiment === '正面') return 'green';
  if (sentiment === '负面') return 'red';
  if (sentiment === '中性') return 'blue';
  return undefined;
}
