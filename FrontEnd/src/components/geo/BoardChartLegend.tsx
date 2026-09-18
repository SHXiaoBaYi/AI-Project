import { useCallback, useEffect, useState } from 'react';
import { Tooltip } from 'antd';

/** 与 @antv/g2 category10 一致，保证色块和柱子对得上 */
export const BOARD_SERIES_COLORS = [
  '#5B8FF9',
  '#5AD8A6',
  '#5D7092',
  '#F6BD16',
  '#6F5EF9',
  '#6DC8EC',
  '#945FB9',
  '#FF9845',
  '#1E9493',
  '#FF99C3',
];

export function boardSeriesColor(index: number) {
  return BOARD_SERIES_COLORS[index % BOARD_SERIES_COLORS.length];
}

export function uniqueBoardSeries(data: Array<{ series?: unknown }> | undefined | null): string[] {
  const seen = new Set<string>();
  const names: string[] = [];
  for (const row of data || []) {
    if (row?.series == null || row.series === '') continue;
    const name = String(row.series);
    if (seen.has(name)) continue;
    seen.add(name);
    names.push(name);
  }
  return names;
}

export function boardColorScale(series: string[]) {
  return {
    domain: series,
    range: series.map((_, index) => boardSeriesColor(index)),
  };
}

export function filterBoardSeries<T extends { series?: unknown }>(
  data: T[] | undefined | null,
  hidden: ReadonlySet<string>,
): T[] {
  const rows = data || [];
  if (!hidden.size) return rows;
  return rows.filter((row) => !hidden.has(String(row.series ?? '')));
}

/** 点图例隐藏/显示系列；系列名单变化时恢复全显 */
export function useHiddenBoardSeries(series: string[]) {
  const seriesKey = series.join('\0');
  const [hidden, setHidden] = useState<Set<string>>(() => new Set());

  useEffect(() => {
    setHidden(new Set());
  }, [seriesKey]);

  const toggle = useCallback((name: string) => {
    setHidden((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  }, []);

  return { hidden, toggle };
}

/** 图例在图表外：每项 100px，超出省略，悬停看全名；点击切换对应系列 */
export function BoardChartLegend({
  series,
  hidden,
  onToggle,
}: {
  series: string[];
  hidden?: ReadonlySet<string>;
  onToggle?: (name: string) => void;
}) {
  if (!series.length) return null;
  return (
    <div className='mb-2 flex flex-wrap gap-x-2 gap-y-1'>
      {series.map((name, index) => {
        const off = hidden?.has(name) ?? false;
        return (
          <Tooltip
            key={name}
            title={name}
          >
            <button
              type='button'
              aria-pressed={!off}
              onClick={() => onToggle?.(name)}
              className={`inline-flex h-5 w-[100px] cursor-pointer items-center gap-1 border-0 bg-transparent p-0 text-left ${off ? 'opacity-35' : ''}`}
            >
              <span
                className='size-2 shrink-0 rounded-sm'
                style={{ backgroundColor: off ? '#d9d9d9' : boardSeriesColor(index) }}
              />
              <span className='min-w-0 flex-1 truncate text-xs text-neutral-600'>{name}</span>
            </button>
          </Tooltip>
        );
      })}
    </div>
  );
}
