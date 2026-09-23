import { useMemo, useRef } from 'react';
import { BetaSchemaForm } from '@ant-design/pro-components';
import type { FormSchema } from '@ant-design/pro-components/es/form/components/SchemaForm';
import defaultModalProps from '@/components/BaseModalForm/defaultModalProps';
import styles from './index.module.css';

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
    open,
    ...rest
  } = props as any;

  // 每次打开弹窗重建表单，避免编辑时残留上一次 initialValues
  const wasOpenRef = useRef(!!open);
  const instanceKeyRef = useRef(0);
  if (open && !wasOpenRef.current) {
    instanceKeyRef.current += 1;
  }
  wasOpenRef.current = !!open;

  const adaptedColumns = useMemo(() => {
    if (!Array.isArray(callerColumns)) return callerColumns;
    return callerColumns
      .filter((col: any) => col.valueType !== 'option' && col.hideInForm !== true)
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
    open,
    ...rest,
  } as FormSchema<T, ValueType>;

  return (
    <BetaSchemaForm<T, ValueType>
      key={instanceKeyRef.current}
      {...mergedProps}
    />
  );
}
