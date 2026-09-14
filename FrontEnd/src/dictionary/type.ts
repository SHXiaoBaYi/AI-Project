import type { ReactNode } from 'react';
import type { DefaultOptionType } from 'antd/lib/select';

/**
 * 继承至 import type { DefaultOptionType } from 'antd/lib/select'
 *
 * OptionItemType 扩展一些自定义属性
 */
export type OptionItemType = DefaultOptionType & {
  /**
   * color 文字主色，背景色会根据主色自动生成
   */
  color?: string;
  /**
   * iconfont 传入一个图标组件或者iconfont项目中自定义图标名称
   */
  iconfont?: ReactNode | string;
};
