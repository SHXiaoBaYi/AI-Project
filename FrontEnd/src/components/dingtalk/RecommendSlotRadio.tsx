import { Radio } from 'antd';
import dayjs from 'dayjs';

export type RecommendSlotOption = {
  start: string;
  durationMin: number;
  label?: string;
};

export function formatRecommendSlotLabel(slot: RecommendSlotOption) {
  if (slot.label?.trim()) return slot.label.trim();
  const start = dayjs(slot.start);
  const time = start.isValid() ? start.format('M月D日 HH:mm') : slot.start;
  return `${time}（${slot.durationMin || 60} 分）`;
}

type Props = {
  value?: string;
  options: RecommendSlotOption[];
  /** Form.Item 注入 */
  onChange?: (start: string) => void;
  /** 选中时段后同步时长 */
  onDurationChange?: (durationMin: number) => void;
};

/** 机器人推荐时段单选；默认由表单 seed 设为第一项 */
export default function RecommendSlotRadio({ value, options, onChange, onDurationChange }: Props) {
  return (
    <Radio.Group
      className='flex w-full flex-col gap-1.5'
      value={value}
      onChange={(e) => {
        const start = String(e.target.value);
        const hit = options.find((s) => s.start === start);
        onChange?.(start);
        onDurationChange?.(hit?.durationMin || 60);
      }}
    >
      {options.map((slot) => (
        <Radio
          key={slot.start}
          value={slot.start}
          className='!mr-0 rounded-lg border border-neutral-200 bg-neutral-50 px-2 py-1.5'
        >
          <span className='text-sm text-neutral-700'>{formatRecommendSlotLabel(slot)}</span>
        </Radio>
      ))}
    </Radio.Group>
  );
}
