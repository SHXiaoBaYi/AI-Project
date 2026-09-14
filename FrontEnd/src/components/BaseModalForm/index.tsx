import { ModalForm, type ModalFormProps } from '@ant-design/pro-components';
import defaultModalProps from './defaultModalProps';

/**
 * 扩展ModalForm组件，修改了modalProps的默认样式
 * @param props 和ModalForm保持一致
 * @returns ModalForm
 */
export default function BaseModalForm<T = Record<string, any>, U = Record<string, any>>(props: ModalFormProps<T, U>) {
  const callerModalProps = props.modalProps ?? {};

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
      {...props}
      modalProps={mergedModalProps}
    />
  );
}
