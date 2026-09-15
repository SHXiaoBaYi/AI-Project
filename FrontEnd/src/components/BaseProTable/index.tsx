import { useMemo } from 'react';
import { ProTable } from '@ant-design/pro-components';
import type { ParamsType, ProColumns, ProTableProps } from '@ant-design/pro-components';
import type { SizeType } from 'antd/es/config-provider/SizeContext';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { DATE_TIME_FORMAT, formatDateTime } from '@/utils/datetime';
import styles from './index.module.css';

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
  const mergedColumns = useMemo(() => withDateTimeFormat(columns as ProColumns<T>[] | undefined), [columns]);

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
