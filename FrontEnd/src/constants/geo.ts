export const GEO_TERM_TYPES = [
  { label: '日巡查', value: '日巡查' },
  { label: '周巡查', value: '周巡查' },
] as const;

export const GEO_TERM_TYPE_DEFAULT = '日巡查';

export const GEO_PLATFORM_TYPES = [
  { label: 'AI平台', value: 'AI平台' },
  { label: '内容发布平台', value: '内容发布平台' },
] as const;

export const GEO_PLATFORM_TYPE_AI = 'AI平台';
export const GEO_PLATFORM_TYPE_CONTENT = '内容发布平台';
export const GEO_PLATFORM_TYPE_DEFAULT = 'AI平台';

export const GEO_CONTENT_AGG_STATUS = [
  { label: '投放完成', value: '投放完成' },
  { label: '部分投放', value: '部分投放' },
  { label: '未投放', value: '未投放' },
] as const;

export const GEO_CONTENT_PUBLISH_STATUS = [
  { label: '投放成功', value: '投放成功' },
  { label: '审核未通过', value: '审核未通过' },
  { label: '未投放', value: '未投放' },
] as const;

export const GEO_CONTENT_SOURCES = [
  { label: '导入', value: '导入' },
  { label: '手动新增', value: '手动新增' },
  { label: 'AI生成', value: 'AI生成' },
] as const;

export const GEO_CONTENT_FORMS = [
  { label: '图文', value: '图文' },
  { label: '视频', value: '视频' },
] as const;

export const GEO_CONTENT_WEEK_METRICS = [
  { key: 'produced', label: '产出篇数', tip: '本周图文已提交（投放成功+审核未通过）' },
  { key: 'pendingReview', label: '待审', tip: '当前图文审核未通过' },
  { key: 'published', label: '已发布', tip: '本周图文投放成功' },
  { key: 'pendingProduce', label: '待产出', tip: '当前未投放（待产出 backlog）' },
  { key: 'videoPublished', label: '视频已发布', tip: '本周视频投放成功' },
  { key: 'videoPendingReview', label: '视频待审', tip: '当前视频审核未通过' },
] as const;
