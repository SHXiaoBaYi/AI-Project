/** 招聘候选人阶段中央目录（与后端 HrStageCatalog 对齐） */

export type HrStagePhaseCode = 'SCREEN' | 'PHONE' | 'INTERVIEW' | 'ONBOARD';

export interface HrStageCatalogStage {
  code: string;
  name: string;
  sortNo?: number | null;
  terminal?: boolean | null;
  phase?: HrStagePhaseCode | string | null;
  funnelBucket?: string | null;
}

export interface HrStageCatalogPhase {
  code: string;
  name: string;
  order?: number;
}

export interface HrStageCatalogFunnel {
  code: string;
  name: string;
  order?: number;
  conversionLabel?: string | null;
  memberStageCodes?: string[];
}

export interface HrStageCatalog {
  stages: HrStageCatalogStage[];
  phases: HrStageCatalogPhase[];
  funnel: HrStageCatalogFunnel[];
  aliases?: Record<string, string>;
}

/** 库未就绪时的展示名兜底 */
export const HR_STAGE_ALIAS: Record<string, string> = {
  ENTERED: '待初筛',
  SCREEN_PASS: '初筛通过（待电话）',
  SCREEN_FAIL: '初筛不合适',
  PHONE_PASS: '电话通过（待邀约）',
  PHONE_FAIL: '电话不合适',
  INVITED: '已邀约（待到面）',
  SHOW_UP: '已到面（待出评价）',
  FIRST_PENDING: '一面待定',
  R1_DISPUTE: '一面待商榷',
  FIRST_ROUND: '一面通过',
  FIRST_FAIL: '一面未通过',
  R2_PENDING: '二面待定',
  R2_DISPUTE: '二面待商榷',
  R2_FAIL: '二面未通过',
  SECOND_ROUND: '二面通过',
  R3_PASS: '三面通过',
  R3_PENDING: '三面待定',
  R3_DISPUTE: '三面待商榷',
  R3_FAIL: '三面未通过',
  R4_PASS: '四面通过',
  R4_PENDING: '四面待定',
  R4_DISPUTE: '四面待商榷',
  R4_FAIL: '四面未通过',
  R5_PASS: '五面通过',
  R5_PENDING: '五面待定',
  R5_DISPUTE: '五面待商榷',
  R5_FAIL: '五面未通过',
  FINAL: '终面通过',
  SALARY: '薪资沟通中',
  BG_COLLECT: '背调资料收集中',
  BG_CHECK: '背调中',
  MEDICAL: '体检中',
  OFFER_PENDING: '待发offer',
  PENDING_ONBOARD: '待入职',
  OFFER_SENT: '发放Offer',
  OFFER_ACCEPTED: 'Offer接受',
  ACCEPTED: 'Offer接受',
  CANDIDATE_REJECT: '候选人拒绝',
  ONBOARDED: '已入职',
};

export function hrStageDisplayName(
  code?: string | null,
  catalog?: HrStageCatalog | null,
  fallback?: string | null,
): string {
  if (!code) {
    return fallback || '—';
  }
  const key = code.trim().toUpperCase();
  const fromCatalog = catalog?.stages?.find((s) => s.code === key)?.name;
  if (fromCatalog) {
    return fromCatalog;
  }
  if (catalog?.aliases?.[key]) {
    return catalog.aliases[key];
  }
  if (HR_STAGE_ALIAS[key]) {
    return HR_STAGE_ALIAS[key];
  }
  return fallback || code;
}

export function hrStageSelectOptions(catalog?: HrStageCatalog | null) {
  if (catalog?.stages?.length) {
    return catalog.stages.map((s) => ({ label: s.name, value: s.code }));
  }
  return Object.entries(HR_STAGE_ALIAS).map(([value, label]) => ({ label, value }));
}
