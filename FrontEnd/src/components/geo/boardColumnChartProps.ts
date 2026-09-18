/** 看板分组柱状图：柱宽固定 20px，组内柱紧挨，组间多留白，数值白字常显在柱内 */

export const BOARD_COLUMN_WIDTH = 20;

function formatColumnValue(datum: Record<string, unknown>) {
  const raw = datum?.value;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return Number.isInteger(raw) ? String(raw) : String(Math.round(raw * 100) / 100);
  }
  if (raw == null) return '';
  return String(raw);
}

/** @ant-design/charts Column 共用扩展项 */
export const boardColumnChartProps = {
  /** 组内柱子尽量贴紧（0~1，越小越紧） */
  group: { padding: 0 },
  /** 组与组之间多留白 */
  scale: {
    x: {
      paddingInner: 0.42,
      paddingOuter: 0.1,
    },
  },
  /** 固定柱宽 20px，组内不再额外 inset 拉开 */
  style: {
    maxWidth: BOARD_COLUMN_WIDTH,
    minWidth: BOARD_COLUMN_WIDTH,
    inset: 0,
  },
  /** 白字显示在柱体内，更醒目 */
  label: {
    text: formatColumnValue,
    position: 'inside' as const,
    fontSize: 11,
    fontWeight: 600,
    fill: '#ffffff',
    textAlign: 'center' as const,
    transform: [{ type: 'overlapHide' }],
  },
};
