import { type OptionItemType } from './type.ts';
import { CheckCircleOutlined, StopFilled } from '@ant-design/icons';

// 用户管理-用户状态
export default [
  {
    label: '启用',
    value: 0,
    color: '#389e0d',
    iconfont: CheckCircleOutlined,
  },
  {
    label: '停用',
    value: 1,
    color: '#808080',
    iconfont: StopFilled,
  },
] as unknown as OptionItemType[];
