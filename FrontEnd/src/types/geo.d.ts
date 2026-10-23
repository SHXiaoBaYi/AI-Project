export interface GeoTopic {
  id: number;
  topicName: string;
  optimizeWeek?: string;
  remark?: string;
}

export interface GeoContentReviewRule {
  id: number;
  writerUserId: number;
  writerName?: string;
  skipStage1: number;
  /** 1=启用 AI 预审 */
  aiPreReview?: number;
  /** assist | skip_stage1_on_pass */
  aiMode?: string;
  /** 1=AI 未完成前禁止通过 */
  aiBlockApprove?: number;
  remark?: string;
  createTime?: string;
}

export interface GeoReviewKbCategory {
  code: string;
  name: string;
  /** atomic=单条规则/实体；doc=成篇文档 */
  shape?: 'atomic' | 'doc' | string;
}

export interface GeoReviewKbStoreAttrs {
  openHours?: string;
  address?: string;
  landline?: string;
  mobile?: string;
  coverage?: string;
}

export interface GeoReviewKbProductAttrs {
  kind?: 'series' | 'sku' | string;
  seriesCode?: string;
  seriesName?: string;
  netWeight?: string;
  grossWeight?: string;
  specJin?: string;
  priceYuan?: string;
  parts?: string;
  /** 落库相对路径 /uploads/...，前端用 resolveUploadUrl 按环境拼接 */
  imageUrl?: string;
  handbookPage?: number;
}

export interface GeoReviewKb {
  id: number;
  category: string;
  code?: string;
  title: string;
  content?: string;
  attrs?: string;
  storeAttrs?: GeoReviewKbStoreAttrs;
  productAttrs?: GeoReviewKbProductAttrs;
  sortOrder?: number;
  enabled?: number;
  sourceFile?: string;
  remark?: string;
  createTime?: string;
}

export interface GeoContentReviewAiIssue {
  quoteText?: string;
  commentText?: string;
  level?: 'high' | 'mid' | 'low' | string;
  quotePrefix?: string;
  quoteSuffix?: string;
}

export interface GeoContentReviewAi {
  id?: number;
  reviewId?: number | null;
  placementId?: number | null;
  /** none/pending/running/done/failed */
  status?: string;
  suggestion?: string;
  score?: number | null;
  summary?: string;
  issues?: GeoContentReviewAiIssue[];
  provider?: string;
  modelName?: string;
  errorMsg?: string;
  adopted?: boolean;
  feedback?: string;
  feedbackRemark?: string;
  startedAt?: string;
  finishedAt?: string;
  aiPreReview?: boolean;
  aiMode?: string;
  aiBlockApprove?: boolean;
}

export interface GeoContentPublisherRule {
  id: number;
  publisherUserId: number;
  publisherName?: string;
  isDefault: number;
  remark?: string;
  createTime?: string;
}

export interface GeoPlatform {
  id: number;
  platformName: string;
  platformType?: string;
  loginUrl?: string;
  /** 官方内容审核规范链接，多条换行 */
  contentAuditUrls?: string;
  /** 官方内容审核规范摘要/补充说明 */
  contentAuditNotes?: string;
  sortOrder?: number;
  remark?: string;
}

export interface GeoPlatformAccount {
  id: number;
  platformId: number;
  platformName?: string;
  account: string;
  passwordMasked?: string;
  hasPassword?: boolean;
  accountNickname?: string;
  managerUserId?: number;
  managerName?: string;
  holderUserId?: number;
  holderName?: string;
  openerUserId?: number;
  openerName?: string;
  recharged?: number;
  openTime?: string;
  expireTime?: string;
  loginMethod?: string;
  verified?: number;
  verifyMethod?: string;
  bindPhone?: string;
  bindEmail?: string;
  accountStatus?: string;
  lastLoginTime?: string;
  sortOrder?: number;
  remark?: string;
}

export interface GeoPlatformAccountDTO {
  id?: number;
  platformId: number;
  account: string;
  password?: string;
  clearPassword?: boolean;
  accountNickname?: string;
  managerUserId?: number | null;
  holderUserId?: number | null;
  openerUserId?: number | null;
  recharged?: number;
  openTime?: string;
  expireTime?: string;
  loginMethod?: string;
  verified?: number;
  verifyMethod?: string;
  bindPhone?: string;
  bindEmail?: string;
  accountStatus?: string;
  lastLoginTime?: string;
  sortOrder?: number;
  remark?: string;
}

export interface GeoOwnerOption {
  userId: number;
  displayName: string;
  username?: string;
  nickname?: string;
}

export interface GeoDailyVO {
  id: number;
  inspectDate: string;
  termType?: string;
  platform: string;
  keyword: string;
  ownerUserId?: number;
  ownerName?: string;
  topicId: number;
  topicName: string;
  mentioned: number;
  rankNo?: number;
  recommendStatus?: string;
  screenshotUrl?: string;
  thirdPartyUrl?: string;
  negativeContent?: string;
  competitors?: string;
  boardLocked?: number;
  createTime?: string;
  updateTime?: string;
}

export interface GeoDailyComboVO {
  topicId: number;
  topicName: string;
  keyword: string;
  platform: string;
  dayCount: number;
  mentionCount: number;
  negativeCount: number;
  avgRank?: number;
  firstDate?: string;
  lastDate?: string;
  latestMentioned?: number;
  latestRankNo?: number;
  latestRecommendStatus?: string;
}

export interface GeoDailyComboPoint {
  inspectDate: string;
  dateLabel: string;
  mentioned: number;
  rankNo?: number;
  hasNegative: number;
  recommendStatus?: string;
}

export interface GeoDailyComboDetail {
  summary?: GeoDailyComboVO;
  days: GeoDailyVO[];
  series: GeoDailyComboPoint[];
}

export interface GeoDailyDTO {
  id?: number;
  inspectDate: string;
  termType?: string;
  platform: string;
  keyword: string;
  ownerUserId?: number;
  ownerName?: string;
  topicId: number;
  mentioned?: number;
  rankNo?: number;
  recommendStatus?: string;
  screenshotUrl?: string;
  thirdPartyUrl?: string;
  negativeContent?: string;
  competitors?: string;
}

export interface GeoDailyPlatformItem {
  id?: number;
  platform: string;
  mentioned?: number;
  rankNo?: number;
  recommendStatus?: string;
  screenshotUrl?: string;
  thirdPartyUrl?: string;
  negativeContent?: string;
  competitors?: string;
}

export interface GeoDailyBatchDTO {
  inspectDate: string;
  termType?: string;
  topicId: number;
  keyword: string;
  ownerUserId?: number;
  ownerName?: string;
  items: GeoDailyPlatformItem[];
}

export interface GeoDailyBulkGroupDTO {
  inspectDate: string;
  termType?: string;
  topicId: number;
  keyword: string;
  ownerUserId?: number;
  ownerName?: string;
  items: GeoDailyPlatformItem[];
}

export interface GeoDailyBulkSaveDTO {
  ignoreLocked?: boolean;
  /** 强制覆盖已统计记录，并重算落库周/月/年看板 */
  forceUpdate?: boolean;
  groups: GeoDailyBulkGroupDTO[];
}

export interface GeoDailyBulkConflict {
  id?: number;
  inspectDate: string;
  platform: string;
  keyword: string;
  topicId?: number;
  topicName?: string;
  reason: string;
}

export interface GeoDailyBulkSaveResult {
  needConfirm: boolean;
  insertCount: number;
  updateCount: number;
  skippedLockedCount: number;
  forcedUpdateCount?: number;
  boardPeriodCount?: number;
  boardSnapshotCount?: number;
  lockedConflicts: GeoDailyBulkConflict[];
}

export interface GeoDailyGroup {
  inspectDate: string;
  termType?: string;
  topicId?: number;
  topicName?: string;
  keyword: string;
  ownerUserId?: number;
  ownerName?: string;
  items: GeoDailyVO[];
}

export interface GeoImportResult {
  totalCount: number;
  insertCount: number;
  updateCount: number;
  failureCount: number;
  errors: { rowIndex: number; field: string; message: string }[];
}

export interface GeoChartPoint {
  axis: string;
  series: string;
  value: number;
  /** 下钻键：话题ID 或目标问题原文 */
  key?: string;
}

export interface GeoWeeklyRow {
  weekLabel: string;
  topicName: string;
  ownerName?: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  mentionRateMom?: number | null;
  mentionRateYoy?: number | null;
  firstMentionRate: number;
  firstMentionRateMom?: number | null;
  firstMentionRateYoy?: number | null;
  recommendCount: number;
  recommendCountMom?: number | null;
  recommendCountYoy?: number | null;
  competitorTop?: string;
  citePlatformTop?: string;
  fromSnapshot?: boolean;
}

export interface GeoBoardCompareSummary {
  mentionRate?: number;
  mentionRateMom?: number | null;
  mentionRateYoy?: number | null;
  firstMentionRate?: number;
  firstMentionRateMom?: number | null;
  firstMentionRateYoy?: number | null;
  recommendCount?: number;
  recommendCountMom?: number | null;
  recommendCountYoy?: number | null;
  sampleCount?: number;
  compareHint?: string;
}

export interface GeoWeeklyBoard {
  mentionChart: GeoChartPoint[];
  firstMentionChart: GeoChartPoint[];
  recommendChart: GeoChartPoint[];
  rows: GeoWeeklyRow[];
  persistedPeriodCount?: number;
  compareSummary?: GeoBoardCompareSummary;
  ownerMentionChart?: GeoChartPoint[];
  ownerFirstMentionChart?: GeoChartPoint[];
  ownerRecommendChart?: GeoChartPoint[];
  ownerRows?: GeoWeeklyRow[];
  ownerCompareSummary?: GeoBoardCompareSummary;
}

export interface GeoMonthlyRow {
  monthLabel: string;
  topicName: string;
  ownerName?: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  mentionRateMom?: number | null;
  mentionRateYoy?: number | null;
  firstMentionRate: number;
  firstMentionRateMom?: number | null;
  firstMentionRateYoy?: number | null;
  recommendCount: number;
  recommendCountMom?: number | null;
  recommendCountYoy?: number | null;
  competitorTop?: string;
  citePlatformTop?: string;
  fromSnapshot?: boolean;
}

export interface GeoMonthlyBoard {
  mentionChart: GeoChartPoint[];
  firstMentionChart: GeoChartPoint[];
  recommendChart: GeoChartPoint[];
  rows: GeoMonthlyRow[];
  persistedPeriodCount?: number;
  compareSummary?: GeoBoardCompareSummary;
  ownerMentionChart?: GeoChartPoint[];
  ownerFirstMentionChart?: GeoChartPoint[];
  ownerRecommendChart?: GeoChartPoint[];
  ownerRows?: GeoMonthlyRow[];
  ownerCompareSummary?: GeoBoardCompareSummary;
}

export interface GeoDailyRow {
  dateLabel: string;
  topicName: string;
  keyword?: string;
  ownerName?: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  firstMentionRate: number;
  recommendCount: number;
  competitorTop?: string;
  citePlatformTop?: string;
}

export interface GeoDailySummaryPlatform {
  id?: number;
  platform: string;
  topicName?: string;
  keyword?: string;
  mentioned?: number;
  rankNo?: number;
  recommendStatus?: string;
  thirdPartyUrl?: string;
  competitors?: string;
  negativeContent?: string;
  screenshotUrl?: string;
  sampleCount?: number;
  mentionRate?: number;
  firstMentionRate?: number;
  recommendCount?: number;
  competitorTop?: string;
  citePlatformTop?: string;
}

export interface GeoDailySummaryTopic {
  topicId: number;
  topicName: string;
  keyword?: string;
  termType?: string;
  ownerUserId?: number;
  ownerName?: string;
  platforms: GeoDailySummaryPlatform[];
}

export interface GeoDailySummaryDate {
  inspectDate: string;
  topics: GeoDailySummaryTopic[];
}

export interface GeoDailyBoard {
  mentionChart: GeoChartPoint[];
  firstMentionChart: GeoChartPoint[];
  recommendChart: GeoChartPoint[];
  sampleChart?: GeoChartPoint[];
  rankChart?: GeoChartPoint[];
  negativeChart?: GeoChartPoint[];
  compareSummary?: GeoBoardCompareSummary;
  negativeCount?: number;
  negativeRows?: GeoNegativeSummaryRow[];
  rows: GeoDailyRow[];
  summaryGroups?: GeoDailySummaryDate[];
  ownerMentionChart?: GeoChartPoint[];
  ownerFirstMentionChart?: GeoChartPoint[];
  ownerRecommendChart?: GeoChartPoint[];
  ownerRows?: GeoDailyRow[];
  ownerSummaryGroups?: GeoDailySummaryDate[];
}

export interface GeoTopicPlatformCharts {
  level: 'topic' | 'platform' | 'keyword' | string;
  seriesField?: 'topic' | 'platform' | 'keyword' | string;
  grain?: string;
  topicId?: number;
  topicName?: string;
  keyword?: string;
  platform?: string;
  rankChart?: GeoChartPoint[];
  sampleChart?: GeoChartPoint[];
  negativeChart?: GeoChartPoint[];
  firstRecommendChart?: GeoChartPoint[];
  top3RecommendChart?: GeoChartPoint[];
}

export interface GeoNegativeSummaryRow {
  inspectDate?: string;
  topicId?: number;
  topicName?: string;
  platform?: string;
  termType?: string;
  negativeCount?: number;
}

export interface GeoYearlyRow {
  periodLabel: string;
  topicName: string;
  ownerName?: string;
  platform: string;
  targetRate?: number;
  actualRate: number;
  actualRateMom?: number | null;
  actualRateYoy?: number | null;
  achieveRate: number;
  achieveRateMom?: number | null;
  achieveRateYoy?: number | null;
  sampleCount: number;
  fromSnapshot?: boolean;
}

export interface GeoYearlyPlatformOverall {
  platform: string;
  achieveRate: number;
  filledCount?: number;
  totalCount?: number;
}

export interface GeoYearlyBoard {
  platforms?: string[];
  overallAchieveRates?: GeoYearlyPlatformOverall[];
  actualChart: GeoChartPoint[];
  achieveChart: GeoChartPoint[];
  rows: GeoYearlyRow[];
  persistedPeriodCount?: number;
  compareSummary?: GeoBoardCompareSummary;
  ownerActualChart?: GeoChartPoint[];
  ownerAchieveChart?: GeoChartPoint[];
  ownerRows?: GeoYearlyRow[];
  ownerCompareSummary?: GeoBoardCompareSummary;
}

export interface GeoPersistResult {
  snapshotCount: number;
  lockedDailyCount: number;
  periodCount: number;
}

export interface GeoYearTarget {
  id: number;
  periodLabel: string;
  periodStart?: string;
  periodEnd?: string;
  topicId: number;
  targetRate: number;
  sortOrder?: number;
  remark?: string;
}

export interface GeoBoardQuery {
  startDate?: string;
  endDate?: string;
  topicId?: number;
  keyword?: string;
  termType?: string;
  platforms?: string[];
  /** 露出排名下钻选中的单个平台 */
  platform?: string;
  /** 豆腐块指标：rank / sample / negative / firstRecommend / top3Recommend */
  chartMetric?: 'rank' | 'sample' | 'negative' | 'firstRecommend' | 'top3Recommend' | string;
  grain?: 'day' | 'week' | 'month' | 'year' | string;
}

export interface GeoContentPlacementListItem {
  id: number;
  publisherUserId?: number;
  publisherName?: string;
  ownerUserId?: number;
  ownerName?: string;
  topicId?: number;
  topicName?: string;
  targetQuestion?: string;
  title?: string;
  source?: string;
  sourcePlacementId?: number;
  sourceTargetQuestion?: string;
  sourceAiModel?: string;
  aggregateStatus?: string;
  publishProgress?: number | null;
  platformCount?: number;
  successCount?: number;
  citeCount?: number;
  proofFileCount?: number;
  /** drafting / pending_review / revising / approved / queue */
  contentStatus?: string;
  /** 在线撰写正文标题 */
  contentTitle?: string;
  remark?: string;
  createTime?: string;
}

export interface GeoContentDraft {
  placementId: number;
  targetQuestion?: string;
  contentTitle?: string;
  contentStatus: string;
  contentFormat: string;
  contentBody?: string;
  contentVersion: number;
  editable: boolean;
  contentUpdatedBy?: string;
  contentUpdatedAt?: string;
}

export interface GeoContentOpinion {
  id: number;
  itemId: number;
  opinion: string;
  authorName?: string;
  createTime?: string;
}

export interface GeoContentArticle {
  itemId: number;
  placementId: number;
  platformName?: string;
  contentForm?: string;
  /** shared=沿用统一稿 custom=独立稿 */
  contentMode: 'shared' | 'custom' | string;
  contentStatus: string;
  contentTitle?: string;
  contentBody?: string;
  contentVersion: number;
  editable: boolean;
  publishStatus?: string;
  opinions?: GeoContentOpinion[];
}

export interface GeoContentArticleBundle {
  placementId: number;
  topicName?: string;
  targetQuestion?: string;
  shared: GeoContentDraft;
  articles: GeoContentArticle[];
}

export interface GeoContentWritingRow {
  placementId: number;
  topicName?: string;
  targetQuestion?: string;
  contentTitle?: string;
  contentStatus?: string;
  ownerName?: string;
  contentVersion?: number;
  currentReviewId?: number | null;
  currentRoundNo?: number | null;
  currentStageNo?: number | null;
  nextStageNo?: number | null;
  skipStage1?: number | null;
  lockedReviewerUserId?: number | null;
  lockedReviewerName?: string;
  stage1ReviewerUserId?: number | null;
  stage1ReviewerName?: string;
  latestReviewId?: number | null;
  latestReviewStatus?: string | null;
  latestRoundNo?: number | null;
  latestStageNo?: number | null;
  reviewerSummary?: string;
  rejectSummary?: string;
  reviewers?: GeoContentReviewerItem[];
  contentUpdatedAt?: string;
  createTime?: string;
  ai?: GeoContentReviewAi | null;
}

export interface GeoContentReviewerItem {
  userId: number;
  userName?: string;
  result?: string;
  comment?: string;
  actedAt?: string;
}

export interface GeoContentReviewAnnotation {
  id: number;
  reviewId?: number;
  placementId?: number;
  quoteText: string;
  quotePrefix?: string;
  quoteSuffix?: string;
  startOffset?: number | null;
  endOffset?: number | null;
  commentText: string;
  authorUserId?: number;
  authorName?: string;
  createTime?: string;
}

export interface GeoContentReviewRow {
  reviewId: number;
  placementId: number;
  roundNo?: number;
  stageNo?: number;
  status?: string;
  topicName?: string;
  targetQuestion?: string;
  contentTitle?: string;
  submitterName?: string;
  submitRemark?: string;
  myResult?: string;
  reviewerSummary?: string;
  reviewers?: GeoContentReviewerItem[];
  createTime?: string;
  ai?: GeoContentReviewAi | null;
}

export interface GeoContentReviewRound {
  reviewId?: number | null;
  /** draft=当前稿 review=某轮提审快照 */
  entryType?: 'draft' | 'review' | string;
  roundNo?: number | null;
  stageNo?: number | null;
  status?: string | null;
  submitterName?: string;
  submitRemark?: string;
  contentVersion?: number;
  contentTitle?: string;
  contentFormat?: string;
  contentBody?: string;
  hasSnapshot?: boolean;
  reviewers?: GeoContentReviewerItem[];
  reviewerSummary?: string;
  annotations?: GeoContentReviewAnnotation[];
  createTime?: string;
  ai?: GeoContentReviewAi | null;
}

export interface GeoContentReviewCompare {
  placementId: number;
  topicName?: string;
  targetQuestion?: string;
  currentReviewId?: number | null;
  currentDraft?: GeoContentReviewRound | null;
  rounds: GeoContentReviewRound[];
}

export interface GeoContentPlacementArticle {
  id: number;
  placementId: number;
  publisherUserId?: number;
  publisherName?: string;
  ownerUserId?: number;
  ownerName?: string;
  topicId?: number;
  topicName?: string;
  targetQuestion?: string;
  title?: string;
  platformName?: string;
  contentForm?: string;
  publishStatus?: string;
  publishUrl?: string;
  publishTime?: string;
  remark?: string;
  citeCount?: number;
  createTime?: string;
}

export interface GeoTargetQuestionOption {
  placementId: number;
  targetQuestion?: string;
}

export interface GeoContentPlacementItem {
  id: number;
  placementId: number;
  title?: string;
  platformName?: string;
  contentForm?: string;
  publishStatus?: string;
  publishUrl?: string;
  publishTime?: string;
  citeCount?: number;
  remark?: string;
}

export interface GeoContentPlacementCite {
  id: number;
  placementId: number;
  itemId?: number;
  askQuestion?: string;
  aiPlatform?: string;
  citeUrl?: string;
  screenshotUrl?: string;
  remark?: string;
}

export interface GeoContentPlacementDetail {
  id: number;
  publisherName?: string;
  ownerName?: string;
  topicName?: string;
  targetQuestion?: string;
  title?: string;
  aggregateStatus?: string;
  publishProgress?: number | null;
  remark?: string;
  items: GeoContentPlacementItem[];
  cites: GeoContentPlacementCite[];
  proofFiles?: GeoContentPlacementProofFile[];
}

export interface GeoContentPlacementProofFile {
  id: number;
  taskId?: number;
  taskTitle?: string;
  taskType?: string;
  bizType?: string;
  bizId?: number;
  fileName: string;
  fileUrl: string;
  fileSize?: number;
  contentType?: string;
  uploadUserName?: string;
  createTime?: string;
}

export interface GeoContentPlacementDTO {
  id?: number;
  publisherUserId?: number | null;
  ownerUserId?: number | null;
  topicId?: number;
  topicName?: string;
  targetQuestion: string;
  remark?: string;
}

export interface GeoContentPlacementItemDTO {
  id?: number;
  placementId: number;
  title: string;
  platformName: string;
  contentForm?: string;
  publishStatus: string;
  publishUrl?: string;
  publishTime?: string;
  sortOrder?: number;
  remark?: string;
}

export interface GeoContentPublisherWeekRow {
  weekLabel?: string;
  weekStart?: string;
  weekEnd?: string;
  publisherUserId?: number;
  publisherName?: string;
  producedCount?: number;
  pendingReviewCount?: number;
  hasPendingReview?: boolean;
  publishedCount?: number;
  pendingProduceCount?: number;
  videoPublishedCount?: number;
  videoPendingReviewCount?: number;
  hasVideoPendingReview?: boolean;
}

export interface GeoContentPublisherWeekBoard {
  startDate?: string;
  endDate?: string;
  rows: GeoContentPublisherWeekRow[];
}

export interface GeoContentPublisherWeekDetail {
  itemId: number;
  placementId: number;
  publisherName?: string;
  topicName?: string;
  targetQuestion?: string;
  title?: string;
  platformName?: string;
  contentForm?: string;
  publishStatus?: string;
  publishUrl?: string;
  publishTime?: string;
}

export type GeoContentWeekMetric =
  'produced' | 'pendingReview' | 'published' | 'pendingProduce' | 'videoPublished' | 'videoPendingReview';

export interface GeoContentArticleBoardQuery {
  startDate?: string;
  endDate?: string;
  topicId?: number;
  publisherUserId?: number;
  publisherUserIds?: number[];
  publishPlatforms?: string[];
  aiPlatforms?: string[];
  contentForm?: string;
}

export interface GeoRankItem {
  name: string;
  value: number;
  extra?: string;
}

export interface GeoContentPublishAggRow {
  dateLabel?: string;
  topicName?: string;
  publisherName?: string;
  publisherUserId?: number;
  publishPlatform?: string;
  contentForm?: string;
  publishCount?: number;
}

export interface GeoContentCiteAggRow {
  dateLabel?: string;
  topicName?: string;
  publisherName?: string;
  publishPlatform?: string;
  aiPlatform?: string;
  successCount?: number;
  citedCount?: number;
  citeRate?: number;
}

export interface GeoContentPublisherCiteRow {
  publisherUserId?: number;
  publisherName?: string;
  successCount?: number;
  citedCount?: number;
  citeHitCount?: number;
  citeRate?: number;
  citeRateMom?: number | null;
  citeRateYoy?: number | null;
  citeHitCountMom?: number | null;
  citeHitCountYoy?: number | null;
}

export interface GeoContentArticleBoard {
  publishCountChart: GeoChartPoint[];
  citeRateChart: GeoChartPoint[];
  publisherCiteCompareChart: GeoChartPoint[];
  publishPlatformCiteRank: GeoRankItem[];
  articleCiteRank: GeoRankItem[];
  publishRows: GeoContentPublishAggRow[];
  citeRows: GeoContentCiteAggRow[];
  publisherCiteRows: GeoContentPublisherCiteRow[];
  publisherCompareHint?: string;
}

export interface GeoContentArticleDetailRow {
  placementId?: number;
  itemId?: number;
  targetQuestion?: string;
  title?: string;
  topicName?: string;
  publisherName?: string;
  publishPlatform?: string;
  contentForm?: string;
  publishStatus?: string;
  publishTime?: string;
  publishUrl?: string;
  aiPlatform?: string;
  citeUrl?: string;
  askQuestion?: string;
  citeCount?: number;
}

export interface GeoContentPlacementCiteDTO {
  id?: number;
  placementId: number;
  itemId?: number;
  askQuestion: string;
  aiPlatform: string;
  citeUrl?: string;
  screenshotUrl?: string;
  sortOrder?: number;
  remark?: string;
}

/** 卖点结构类型：与 Excel sheet 一一对应，勿混用字段 */
export type GeoSellKind = 'simple' | 'quality' | 'social' | 'authority';

export interface GeoSellDomain {
  id: number;
  parentId?: number;
  name: string;
  code?: string;
  /** 根域结构类型，子域继承 */
  rootKind?: GeoSellKind | string;
  sortOrder?: number;
  enabled?: number;
  remark?: string;
  /** 本域及子域卖点条数 */
  pointCount?: number;
  children?: GeoSellDomain[];
}

export interface GeoSellPoint {
  id: number;
  domainId: number;
  domainName?: string;
  kind?: GeoSellKind | string;
  title: string;
  claim?: string;
  evidence?: string;
  tags?: string[];
  keywords?: string[];
  evidenceLevel?: string;
  /** quality */
  moduleName?: string;
  sectionName?: string;
  itemName?: string;
  standardText?: string;
  /** social */
  dimension?: string;
  eventName?: string;
  actionContent?: string;
  eventRemark?: string;
  /** authority */
  channel?: string;
  admission?: string;
  processText?: string;
  meaning?: string;
  extraNote?: string;
  status?: string;
  sortOrder?: number;
  sourceSheet?: string;
  sourceRow?: number;
  /** 正文/论据字数，列表展示用 */
  evidenceLen?: number;
  createTime?: string;
  updateTime?: string;
}

export interface GeoSellPointContext {
  topic?: string;
  question?: string;
  points?: {
    id?: number;
    domain?: string;
    title?: string;
    claim?: string;
    evidence?: string;
    level?: string;
    tags?: string[];
    score?: number;
  }[];
  hardFacts?: string[];
  forbiddenHints?: string[];
}

export interface GeoPipelineStage {
  code: string;
  name: string;
  status: 'pending' | 'running' | 'done' | 'skipped' | 'failed' | string;
  message?: string;
}

export interface GeoContentAiGenerateResult {
  contentTitle?: string;
  contentBody?: string;
  sourceAiModel?: string;
  usedPointCount?: number;
  usedImageCount?: number;
  qaRounds?: number;
  styleCode?: string;
  skeletonCode?: string;
  stages?: GeoPipelineStage[];
  applied?: boolean;
  contentVersion?: number;
}

export interface GeoAiGenerateJob {
  jobId: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string;
  currentCode?: string;
  currentMessage?: string;
  stages?: GeoPipelineStage[];
  /** 流式撰写中的标题草稿 */
  draftTitle?: string;
  /** 流式撰写中的 HTML 草稿（打字机预览） */
  draftHtml?: string;
  result?: GeoContentAiGenerateResult;
}

export interface GeoMediaAsset {
  id: number;
  title: string;
  fileUrl: string;
  publicUrl?: string;
  thumbUrl?: string;
  sourceType?: 'own' | 'stock_licensed' | string;
  licenseNote?: string;
  brandVisible?: number;
  caption?: string;
  altText?: string;
  tags?: string[];
  /** 可用于哪里 / 适用段落场景 */
  usageHint?: string;
  sceneTags?: string[];
  widthPx?: number;
  heightPx?: number;
  status?: string;
  remark?: string;
  /** 可选软优先 */
  topicIds?: number[];
  topicNames?: string[];
  createTime?: string;
  updateTime?: string;
}

export interface GeoArticleStylePack {
  id: number;
  code: string;
  name: string;
  platformHint?: string;
  toneGuide?: string;
  structureGuide?: string;
  samplePhrases?: string;
  brandGuide?: string;
  enabled?: number;
  sortOrder?: number;
  createTime?: string;
  updateTime?: string;
}

export interface GeoArticleSkeleton {
  id: number;
  code: string;
  name: string;
  sectionSchema?: string;
  enabled?: number;
  sortOrder?: number;
  createTime?: string;
  updateTime?: string;
}
