import type { PageQuery, PageResult } from './api';
import type { GeoTopic } from './geo';

/** 监控问题（目标问题） */
export interface GeoAutoQuestion {
  id: number;
  questionText: string;
  topicId: number;
  topicName?: string;
  brandWords?: string;
  competitorWords?: string;
  /** 每日 / 每周 */
  freq?: string;
  ownerUserId?: number;
  ownerName?: string;
  /** 1=启用 0=停用 */
  enabled?: number;
  sortOrder?: number;
  remark?: string;
  createTime?: string;
}

export interface GeoAutoQuestionOption {
  id: number;
  questionText: string;
  topicId: number;
  topicName?: string;
  freq?: string;
  brandWords?: string;
  competitorWords?: string;
}

export interface GeoAutoQuestionQuery extends PageQuery {
  questionText?: string;
  topicId?: number;
  enabled?: number;
  freq?: string;
}

export interface GeoAutoQuestionDTO {
  id?: number;
  questionText: string;
  topicId: number;
  brandWords?: string;
  competitorWords?: string;
  freq?: string;
  ownerUserId?: number;
  ownerName?: string;
  enabled?: number;
  sortOrder?: number;
  remark?: string;
}

/** 可监测平台（AI平台 + 厂商就绪状态） */
export interface GeoAutoPlatformOption {
  id: number;
  platformName: string;
  providerCode?: string;
  providerReady: boolean;
  hint?: string;
}

/** 监测任务 */
export interface GeoAutoTask {
  id: number;
  taskName: string;
  /** TOPIC=按话题 / QUESTION=按问题 */
  scopeType: string;
  scopeIds?: string;
  scopeIdList?: number[];
  platforms?: string;
  platformList?: string[];
  /** 每日 / 每周 */
  freq?: string;
  /** HH:mm:ss */
  runTime?: string;
  weekDay?: number;
  /** 1=同日有人工记录也覆盖 */
  overwriteManual?: number;
  notifyEnabled?: number;
  enabled?: number;
  lastRunId?: number;
  nextRunTime?: string;
  sortOrder?: number;
  remark?: string;
  createTime?: string;
  questionCount?: number;
  estimatedDailyCalls?: number;
  lastRunStatus?: string;
  lastRunStartTime?: string;
  lastRunProgress?: string;
}

export interface GeoAutoTaskQuery extends PageQuery {
  taskName?: string;
  enabled?: number;
}

export interface GeoAutoTaskDTO {
  id?: number;
  taskName: string;
  scopeType: string;
  scopeIds: number[];
  platforms: string[];
  freq?: string;
  runTime?: string;
  weekDay?: number;
  overwriteManual?: number;
  notifyEnabled?: number;
  enabled?: number;
  remark?: string;
}

/** 运行批次 */
export interface GeoAutoRun {
  id: number;
  taskId: number;
  taskName: string;
  runDate: string;
  /** SCHEDULED / MANUAL */
  triggerType: string;
  /** RUNNING / SUCCESS / PARTIAL / FAILED */
  status: string;
  totalCount: number;
  doneCount: number;
  successCount: number;
  failedCount: number;
  startTime?: string;
  endTime?: string;
  errorMsg?: string;
}

/** 监测结果（列表行） */
export interface GeoAutoResult {
  id: number;
  runId: number;
  questionId: number;
  questionText: string;
  topicId: number;
  topicName?: string;
  platform: string;
  channel?: string;
  answerModel?: string;
  runDate?: string;
  mentioned?: number;
  rankNo?: number | null;
  recommendStatus?: string;
  sentiment?: string;
  mentionPosition?: number | null;
  negativeContent?: string;
  competitors?: string;
  /** SUCCESS / SKIPPED_MANUAL / JUDGE_FAILED / CALL_FAILED */
  status: string;
  errorMsg?: string;
  latencyMs?: number;
  dailyId?: number;
  createTime?: string;
}

export interface GeoAutoResultQuery extends PageQuery {
  dateStart?: string;
  dateEnd?: string;
  platform?: string;
  topicId?: number;
  questionText?: string;
  mentioned?: number;
  sentiment?: string;
  status?: string;
  runId?: number;
}

export interface GeoAutoCitation {
  title?: string;
  site?: string;
  context?: string;
}

export interface GeoAutoResultDiff {
  field: string;
  label: string;
  before: string;
  after: string;
  /** up / down / flat */
  direction: string;
}

/** 监测结果详情（含回答原文与对比） */
export interface GeoAutoResultDetail extends GeoAutoResult {
  answerText?: string;
  citations?: GeoAutoCitation[];
  judgeRaw?: string;
  diffs?: GeoAutoResultDiff[];
}

/** 问题×平台矩阵 */
export interface GeoAutoMatrixCell {
  resultId: number;
  runDate: string;
  mentioned?: number;
  rankNo?: number | null;
  recommendStatus?: string;
  sentiment?: string;
  status: string;
}

export interface GeoAutoMatrixRow {
  questionId: number;
  questionText: string;
  topicId: number;
  topicName?: string;
  /** 提及平台数/参与平台数 */
  mentionSummary?: string;
  cells: Record<string, GeoAutoMatrixCell>;
}

export interface GeoAutoResultMatrix {
  platforms: string[];
  rows: GeoAutoMatrixRow[];
}

/** 从历史数据洗入问题库结果 */
export interface GeoAutoSeedResult {
  scanned: number;
  imported: number;
  skipped: number;
}

/** 话题下拉（问题库/结果页筛选用，复用 geo 话题） */
export type GeoAutoTopicOption = GeoTopic;
