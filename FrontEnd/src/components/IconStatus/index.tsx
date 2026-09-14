import type { ComponentType, CSSProperties } from 'react';
import dictionary from '@/dictionary';
import IconFont from './IconFont';

type Props = {
  type: keyof typeof dictionary;
  value?: string | number;
  style?: CSSProperties;
};

export default function IconStatus({ type, value, style }: Props) {
  const options = dictionary[type];
  const option = options.find((item) => item.value == value);
  if (!option) return '-';

  const icon = option?.iconfont;
  const color = option?.color;

  const renderIcon = () => {
    if (!icon) return null;
    if (typeof icon === 'string') {
      return (
        <IconFont
          type={icon}
          style={{ color, ...style }}
        />
      );
    }
    const IconComp = icon as unknown as ComponentType<{ style?: CSSProperties }>;
    return <IconComp style={{ color, ...style }} />;
  };

  return (
    <div
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1.25 text-xs font-medium`}
      style={{ backgroundColor: color + '20', color }}
      title={option?.label as any}
    >
      {renderIcon()}
      <span>{option?.label}</span>
    </div>
  );
}
