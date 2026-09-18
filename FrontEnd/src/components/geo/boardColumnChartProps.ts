/** 看板分组柱状图：柱宽固定 20px，组内柱紧挨，组间多留白，数值白字常显在柱内；宽度按类目撑开，可横向滚动 */

export const BOARD_COLUMN_WIDTH = 20;

/** 与 scale.x.paddingInner 保持一致：组间留白占比 */
const BOARD_COLUMN_PADDING_INNER = 0.5;

/** 坐标轴 / 边距预留 */
const BOARD_COLUMN_CHART_PAD = 80;

function formatColumnValue(datum: Record<string, unknown>) {
  const raw = datum?.value;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return Number.isInteger(raw) ? String(raw) : String(Math.round(raw * 100) / 100);
  }
  if (raw == null) return '';
  return String(raw);
}

/** 按类目数 × 系列数估算柱图最小宽度，保证柱子不被挤扁 */
export function calcBoardColumnChartWidth(
  data: Array<{ axis?: unknown; series?: unknown }> | undefined | null,
): number {
  const rows = data || [];
  const axes = new Set<string>();
  const series = new Set<string>();
  for (const d of rows) {
    if (d?.axis != null && d.axis !== '') axes.add(String(d.axis));
    if (d?.series != null && d.series !== '') series.add(String(d.series));
  }
  const categoryCount = Math.max(axes.size, 1);
  const seriesCount = Math.max(series.size, 1);
  const groupWidth = seriesCount * BOARD_COLUMN_WIDTH;
  // step 保证组内带宽够放下固定柱宽，组间由 paddingInner 留白
  const step = groupWidth / (1 - BOARD_COLUMN_PADDING_INNER);
  return Math.ceil(categoryCount * step + BOARD_COLUMN_CHART_PAD);
}

/** @ant-design/charts Column 共用扩展项 */
export const boardColumnChartProps = {
  /** 组内柱子尽量贴紧（0~1，越小越紧） */
  group: { padding: 0 },
  /** 组与组之间多留白（配合按数据撑开的宽度 + 横向滚动） */
  scale: {
    x: {
      paddingInner: BOARD_COLUMN_PADDING_INNER,
      paddingOuter: 0.12,
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
