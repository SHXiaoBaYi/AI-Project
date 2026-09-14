import type { ModalFormProps } from '@ant-design/pro-components';
import styles from './index.module.css';

/**
 * 修改弹窗默认样式
 */
const defaultModalProps: ModalFormProps['modalProps'] = {
  destroyOnHidden: true,
  className: styles.modal,
  classNames: {
    container: styles.container,
    header: styles.header,
    body: styles.body,
    footer: styles.footer,
  },
};

export default defaultModalProps;
