import { BetaSchemaForm } from '@ant-design/pro-components';
import type { FormSchema } from '@ant-design/pro-components/es/form/components/SchemaForm';
import defaultModalProps from '@/components/BaseModalForm/defaultModalProps';
import styles from './index.module.css';
import { useMemo } from 'react';

type TableModalProps<T = Record<string, any>, ValueType = 'text'> = FormSchema<T, ValueType> & {
  /** 是否只读，默认 true（详情模式）。设为 false 则为新增/编辑模式 */
  readonly?: boolean;
  /** 提交回调，仅 readonly=false 时生效 */
  onFinish?: (values: T) => Promise<boolean | void>;
};

export default function TableModal<T = Record<string, any>, ValueType = 'text'>(props: TableModalProps<T, ValueType>) {
  const {
    modalProps: callerModalProps,
    className: callerClassName,
    columns: callerColumns,
    readonly = true,
    onFinish,
    ...rest
  } = props as any;

  const adaptedColumns = useMemo(() => {
    if (!Array.isArray(callerColumns)) return callerColumns;
    return callerColumns
      .filter((col: any) => col.valueType !== 'option')
      .map(({ render: _, ...restCol }: any) => restCol);
  }, [callerColumns]);

  const mergedModalProps = {
    ...defaultModalProps,
    ...callerModalProps,
    className: [defaultModalProps?.className, callerModalProps?.className].filter(Boolean).join(' ') || undefined,
    classNames: {
      ...defaultModalProps?.classNames,
      ...callerModalProps?.classNames,
    },
  };

  const isDetail = readonly;

  const mergedProps = {
    readonly,
    submitter: isDetail ? false : undefined,
    grid: true,
    rowProps: { gutter: 32 },
    colProps: { span: 12 },
    layoutType: 'ModalForm' as const,
    requiredMark: !isDetail,
    modalProps: mergedModalProps,
    fieldProps: isDetail ? undefined : { style: { width: '100%' } },
    className: isDetail ? [styles.detailForm, callerClassName].filter(Boolean).join(' ') : callerClassName || undefined,
    columns: adaptedColumns,
    onFinish,
    ...rest,
  } as FormSchema<T, ValueType>;

  return <BetaSchemaForm<T, ValueType> {...mergedProps} />;
}
