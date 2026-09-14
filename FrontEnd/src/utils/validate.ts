import type { Rule } from 'antd/es/form';

/**
 * 公共表单校验规则，各页面/弹窗可复用。
 *
 * 使用方式：
 *   import validate from '@/utils/validate';
 *   <ProFormText name="username" rules={validate.username} />
 */
const validate = {
  /** 用户名：必填，只允许字母/数字/下划线，不能以数字开头 */
  username: [
    { required: true, message: '请输入用户名' },
    {
      pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/,
      message: '用户名只允许字母、数字、下划线，不能以数字开头',
    },
  ] as Rule[],

  /** 密码：必填，最少6位 */
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, message: '密码长度不能少于6位' },
  ] as Rule[],

  /** 手机号（中国大陆） */
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }] as Rule[],

  /** 邮箱 */
  email: [{ type: 'email', message: '请输入正确的邮箱格式' }] as Rule[],
};

export default validate;
