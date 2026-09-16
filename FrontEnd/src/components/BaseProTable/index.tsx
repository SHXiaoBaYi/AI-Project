import { useMemo } from 'react';
import { ProTable } from '@ant-design/pro-components';
import type { ParamsType, ProColumns, ProTableProps } from '@ant-design/pro-components';
import type { SizeType } from 'antd/es/config-provider/SizeContext';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { DATE_TIME_FORMAT, formatDateTime } from '@/utils/datetime';
import styles from './index.module.css';

function isActionColumn<T extends Record<string, any>>(col: ProColumns<T>): boolean {
  if (col.valueType === 'option') return true;
  if (col.title === '操作' || col.title === 'Action') return true;
  if (col.key === 'option' || col.key === 'action') return true;
  return false;
}

/** 操作列统一置顶并左固定，横向滚动时始终可见 */
function withActionColumnFirst<T extends Record<string, any>>(columns?: ProColumns<T>[]): ProColumns<T>[] | undefined {
  if (!columns?.length) return columns;
  const actions: ProColumns<T>[] = [];
  const rest: ProColumns<T>[] = [];
  for (const col of columns) {
    if (isActionColumn(col)) {
      actions.push({ ...col, fixed: 'left' });
    } else {
      rest.push(col);
    }
  }
  if (!actions.length) return columns;
  return [...actions, ...rest];
}

function withDateTimeFormat<T extends Record<string, any>>(columns?: ProColumns<T>[]): ProColumns<T>[] | undefined {
  if (!columns) return columns;
  return columns.map((col) => {
    if (col.valueType === 'dateTime') {
      return {
        ...col,
        fieldProps: { format: DATE_TIME_FORMAT, showTime: true, ...(col.fieldProps as object) },
        render: col.render
          ? col.render
          : (_, record) => {
              const key = col.dataIndex;
              const raw = typeof key === 'string' || typeof key === 'number' ? record[key] : undefined;
              return formatDateTime(raw);
            },
      };
    }
    if (col.valueType === 'dateTimeRange') {
      return {
        ...col,
        fieldProps: { format: DATE_TIME_FORMAT, showTime: true, ...(col.fieldProps as object) },
      };
    }
    return col;
  });
}

/**
 * 扩展ProTable，添加一些公共默认属性。
 * 对于对象类型的属性（scroll、pagination、search），会与调用方传入的值进行浅合并，
 * 确保默认子属性不会因调用方传入部分属性而丢失。
 * 操作列（valueType=option / 标题「操作」）会自动移到第一列并 left 固定。
 */
export default function BaseProTable<T extends Record<string, any>, U extends ParamsType = any>(
  props: ProTableProps<T, U>,
) {
  const {
    scroll: callerScroll,
    pagination: callerPagination,
    className: callerClassName,
    columns,
    search: callerSearch,
    ...restProps
  } = props;

  const scroll = { x: 'max-content' as const, ...callerScroll };
  const pagination = (() => {
    if (callerPagination === false) return false;
    const { showSizeChanger: callerSSC, ...rest } = callerPagination ?? {};
    const showSizeChanger =
      callerSSC === false ? false : { variant: 'filled' as const, ...(typeof callerSSC === 'object' ? callerSSC : {}) };
    return { showSizeChanger, size: 'medium' as SizeType, defaultPageSize: 10, ...rest };
  })();

  const search = (() => {
    if (callerSearch === false) return false;
    return {
      ...BUTTERFLY_SEARCH,
      ...(typeof callerSearch === 'object' ? callerSearch : {}),
    };
  })();

  const className = [styles.table, callerClassName].filter(Boolean).join(' ');
  const mergedColumns = useMemo(
    () => withActionColumnFirst(withDateTimeFormat(columns as ProColumns<T>[] | undefined)),
    [columns],
  );

  return (
    <ProTable<T, U>
      options={false}
      scroll={scroll}
      pagination={pagination}
      search={search}
      className={className || undefined}
      columns={mergedColumns}
      {...restProps}
    />
  );
}
