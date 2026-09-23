import { Form, Select } from 'antd';

/** 根据候选人当前阶段，推导下一场可创建的面试轮次；不适合邀约时返回 null */
export function nextInviteRound(stage?: string | null): number | null {
  switch (stage) {
    case 'SCREEN_PASS':
      return 1;
    case 'FIRST_ROUND':
      return 2;
    case 'SECOND_ROUND':
      return 3;
    case 'R3_PASS':
      return 4;
    case 'R4_PASS':
      return 5;
    default:
      return null;
  }
}

export function canCreateInvite(stage?: string | null, maxRound?: number | null): boolean {
  const round = nextInviteRound(stage);
  if (round == null) return false;
  if (maxRound != null && maxRound > 0 && round > maxRound) return false;
  return true;
}

/** 招聘需求配置的最大轮次 */
export function maxRoundOf(plans: Map<number, Map<number, number[]>>, requisitionId?: number | null) {
  if (requisitionId == null) return null;
  const rounds = plans.get(requisitionId);
  if (!rounds || rounds.size === 0) return null;
  return Math.max(...rounds.keys());
}

export type InviteCandidateOption = {
  value: number;
  label: string;
  currentStage?: string;
  requisitionId?: number;
};

/** 从投递列表解析候选人选项（含阶段、需求） */
export function mapApplicationCandidates(rows: Record<string, unknown>[]): InviteCandidateOption[] {
  return rows.map((row) => ({
    value: Number(row.id),
    label: `${row.display_name} · ${row.job_name || '未定岗'}`,
    currentStage: row.current_stage == null || row.current_stage === '' ? undefined : String(row.current_stage),
    requisitionId: row.requisition_id == null ? undefined : Number(row.requisition_id),
  }));
}

/** 仅保留阶段允许邀约的候选人；extraIds 用于编辑时保留当前项 */
export function filterInviteableCandidates(
  candidates: InviteCandidateOption[],
  plans: Map<number, Map<number, number[]>>,
  extraIds?: Iterable<number | undefined | null>,
): InviteCandidateOption[] {
  const keep = new Set<number>();
  for (const id of extraIds ?? []) {
    if (id != null && Number(id) > 0) keep.add(Number(id));
  }
  return candidates.filter((item) => {
    if (keep.has(item.value)) return true;
    return canCreateInvite(item.currentStage, maxRoundOf(plans, item.requisitionId));
  });
}

export function buildAppReqMap(candidates: InviteCandidateOption[]) {
  const next = new Map<number, number>();
  candidates.forEach((item) => {
    if (item.requisitionId != null) next.set(item.value, item.requisitionId);
  });
  return next;
}

export function buildAppStageMap(candidates: InviteCandidateOption[]) {
  const next = new Map<number, string>();
  candidates.forEach((item) => {
    if (item.currentStage) next.set(item.value, item.currentStage);
  });
  return next;
}

type InviteCandidateSelectProps = {
  value?: number;
  onChange?: (value: number) => void;
  options: InviteCandidateOption[];
  plans: Map<number, Map<number, number[]>>;
  disabled?: boolean;
  placeholder?: string;
  /** 选中后按阶段自动写入 roundNo，默认 true */
  autoRound?: boolean;
};

/** 候选人下拉：仅展示传入的 options；选中后自动带出下一轮轮次（面试官/抄送由 RoundPeopleSelect 跟进） */
export function InviteCandidateSelect({
  value,
  onChange,
  options,
  plans,
  disabled,
  placeholder = '请选择候选人（仅可选阶段）',
  autoRound = true,
}: InviteCandidateSelectProps) {
  const form = Form.useFormInstance();

  return (
    <Select
      value={value}
      disabled={disabled}
      options={options}
      showSearch
      optionFilterProp='label'
      placeholder={placeholder}
      style={{ width: '100%' }}
      allowClear={false}
      onChange={(id: number) => {
        onChange?.(id);
        if (!autoRound || id == null) return;
        const opt = options.find((item) => item.value === Number(id));
        const stage = opt?.currentStage;
        const round = nextInviteRound(stage);
        if (round != null && canCreateInvite(stage, maxRoundOf(plans, opt?.requisitionId))) {
          form.setFieldValue('roundNo', round);
        }
      }}
    />
  );
}
