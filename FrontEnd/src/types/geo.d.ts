export interface GeoTopic {
  id: number;
  topicName: string;
  optimizeWeek?: string;
  remark?: string;
}

export interface GeoPlatform {
  id: number;
  platformName: string;
  platformType?: string;
  loginUrl?: string;
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
  level: 'topic' | 'question' | 'platform' | string;
  seriesField?: 'topic' | 'question' | 'platform' | string;
  grain?: string;
  topicId?: number;
  topicName?: string;
  keyword?: string;
  rankChart?: GeoChartPoint[];
  sampleChart?: GeoChartPoint[];
  negativeChart?: GeoChartPoint[];
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
  remark?: string;
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
  sortOrder?: number;
  remark?: string;
}
