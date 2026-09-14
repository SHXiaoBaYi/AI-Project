import { useRef } from 'react';
import { ModalForm, type ModalFormProps } from '@ant-design/pro-components';
import defaultModalProps from './defaultModalProps';

/**
 * 扩展 ModalForm：统一弹窗样式，并在每次打开时重建表单，
 * 避免 initialValues 只在首次挂载生效导致编辑弹窗残留上一次数据。
 */
export default function BaseModalForm<T = Record<string, any>, U = Record<string, any>>(props: ModalFormProps<T, U>) {
  const callerModalProps = props.modalProps ?? {};
  const wasOpenRef = useRef(!!props.open);
  const instanceKeyRef = useRef(0);

  if (props.open && !wasOpenRef.current) {
    instanceKeyRef.current += 1;
  }
  wasOpenRef.current = !!props.open;

  const mergedModalProps: ModalFormProps['modalProps'] = {
    ...defaultModalProps,
    ...callerModalProps,
    className: [defaultModalProps?.className, callerModalProps?.className].filter(Boolean).join(' ') || undefined,
    classNames: {
      ...defaultModalProps?.classNames,
      ...callerModalProps?.classNames,
    },
  };

  return (
    <ModalForm<T, U>
      key={instanceKeyRef.current}
      {...props}
      modalProps={mergedModalProps}
    />
  );
}
