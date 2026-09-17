import type { ProColumns } from '@ant-design/pro-components';
import { DEMO_CREATE_TIME_RANGE } from '@/constants/demoData';

/** 业务列表通用：创建时间范围筛选项 */
export function createTimeRangeColumn<T extends Record<string, any>>(
  options?: Partial<ProColumns<T>> & { defaultDemoRange?: boolean },
): ProColumns<T> {
  const { defaultDemoRange = false, ...rest } = options ?? {};
  return {
    title: '创建时间',
    dataIndex: 'createTime',
    valueType: 'dateRange',
    hideInTable: true,
    ...(defaultDemoRange ? { initialValue: DEMO_CREATE_TIME_RANGE } : {}),
    search: {
      transform: (value) => ({
        createTimeStart: value?.[0],
        createTimeEnd: value?.[1],
      }),
    },
    ...rest,
  };
}

/** 表格列展示用的创建时间（不可搜，与上面 search 列配对） */
export function createTimeDisplayColumn<T extends Record<string, any>>(
  options?: Partial<ProColumns<T>>,
): ProColumns<T> {
  return {
    title: '创建时间',
    dataIndex: 'createTime',
    valueType: 'dateTime',
    width: 170,
    search: false,
    ...options,
  };
}
