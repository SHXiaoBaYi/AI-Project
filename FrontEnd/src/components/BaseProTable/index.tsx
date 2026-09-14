import { ProTable } from '@ant-design/pro-components';
import type { ParamsType, ProTableProps } from '@ant-design/pro-components';
import type { SizeType } from 'antd/es/config-provider/SizeContext';
import styles from './index.module.css';

/**
 * 扩展ProTable，添加一些公共默认属性。
 * 对于对象类型的属性（scroll、pagination），会与调用方传入的值进行浅合并，
 * 确保默认子属性不会因调用方传入部分属性而丢失。
 * @param props 与原本ProTable保持一致
 * @returns ProTable
 */
export default function BaseProTable<T extends Record<string, any>, U extends ParamsType = any>(
  props: ProTableProps<T, U>,
) {
  const { scroll: callerScroll, pagination: callerPagination, className: callerClassName, ...restProps } = props;

  const scroll = { x: 'max-content' as const, ...callerScroll };
  // pagination：合并默认值，支持 false 禁用分页
  const pagination = (() => {
    if (callerPagination === false) return false;
    const { showSizeChanger: callerSSC, ...rest } = callerPagination ?? {};
    const showSizeChanger =
      callerSSC === false ? false : { variant: 'filled' as const, ...(typeof callerSSC === 'object' ? callerSSC : {}) };
    return { showSizeChanger, size: 'medium' as SizeType, defaultPageSize: 10, ...rest };
  })();

  const className = [styles.table, callerClassName].filter(Boolean).join(' ');

  return (
    <ProTable<T, U>
      options={false}
      scroll={scroll}
      pagination={pagination}
      className={className || undefined}
      {...restProps}
    />
  );
}
