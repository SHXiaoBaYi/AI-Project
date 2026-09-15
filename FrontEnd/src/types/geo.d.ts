export interface GeoTopic {
  id: number;
  topicName: string;
  optimizeWeek?: string;
  remark?: string;
}

export interface GeoPlatform {
  id: number;
  platformName: string;
  sortOrder?: number;
  remark?: string;
}

export interface GeoDailyVO {
  id: number;
  inspectDate: string;
  platform: string;
  keyword: string;
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
  platform: string;
  keyword: string;
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
  topicId: number;
  keyword: string;
  items: GeoDailyPlatformItem[];
}

export interface GeoDailyBulkGroupDTO {
  inspectDate: string;
  topicId: number;
  keyword: string;
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
  topicId?: number;
  topicName?: string;
  keyword: string;
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
}

export interface GeoWeeklyRow {
  weekLabel: string;
  topicName: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  firstMentionRate: number;
  recommendCount: number;
  competitorTop?: string;
  citePlatformTop?: string;
  fromSnapshot?: boolean;
}

export interface GeoWeeklyBoard {
  mentionChart: GeoChartPoint[];
  firstMentionChart: GeoChartPoint[];
  recommendChart: GeoChartPoint[];
  rows: GeoWeeklyRow[];
  persistedPeriodCount?: number;
}

export interface GeoMonthlyRow {
  monthLabel: string;
  topicName: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  firstMentionRate: number;
  recommendCount: number;
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
}

export interface GeoDailyRow {
  dateLabel: string;
  topicName: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  firstMentionRate: number;
  recommendCount: number;
  competitorTop?: string;
  citePlatformTop?: string;
}

export interface GeoDailyBoard {
  mentionChart: GeoChartPoint[];
  firstMentionChart: GeoChartPoint[];
  recommendChart: GeoChartPoint[];
  rows: GeoDailyRow[];
}

export interface GeoYearlyRow {
  periodLabel: string;
  topicName: string;
  platform: string;
  targetRate?: number;
  actualRate: number;
  achieveRate: number;
  sampleCount: number;
  fromSnapshot?: boolean;
}

export interface GeoYearlyBoard {
  actualChart: GeoChartPoint[];
  achieveChart: GeoChartPoint[];
  rows: GeoYearlyRow[];
  persistedPeriodCount?: number;
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
  platforms?: string[];
}
