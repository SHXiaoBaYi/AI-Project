import type { TrainQaHandleStatus } from '@/api/train';

export const HANDLE_STATUS_OPTIONS: { label: string; value: TrainQaHandleStatus }[] = [
  { label: '未处理', value: 'OPEN' },
  { label: '用户意图不准', value: 'INTENT_BAD' },
  { label: '忽略这样的问题', value: 'IGNORE' },
  { label: '培训文档缺失', value: 'DOC_MISSING' },
  { label: '培训文档处理完毕', value: 'DOC_DONE' },
  { label: 'AI意图处理完毕', value: 'AI_INTENT_DONE' },
];

export const HANDLE_REASON_OPTIONS = HANDLE_STATUS_OPTIONS.filter((item) => item.value !== 'OPEN');

export const HANDLED_FILTER_OPTIONS = [
  { label: '未处理', value: 'false' },
  { label: '已处理', value: 'true' },
];

export function handleStatusLabel(status?: string) {
  return HANDLE_STATUS_OPTIONS.find((item) => item.value === status)?.label || status || '未处理';
}

export function handleStatusColor(status?: string): string {
  switch (status) {
    case 'INTENT_BAD':
      return 'warning';
    case 'IGNORE':
      return 'default';
    case 'DOC_MISSING':
      return 'error';
    case 'DOC_DONE':
      return 'success';
    case 'AI_INTENT_DONE':
      return 'blue';
    case 'OPEN':
    default:
      return 'processing';
  }
}

export function parseHandledFilter(value: unknown): boolean | undefined {
  if (value === true || value === 'true') return true;
  if (value === false || value === 'false') return false;
  return undefined;
}
