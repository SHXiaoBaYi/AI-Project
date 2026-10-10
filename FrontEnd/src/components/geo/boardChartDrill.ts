/**
 * 看板分组柱图下钻：统一解析 G2 点击事件，并在自定义字段丢失时用系列名回查 key。
 * 同层所有柱应都能下钻；柱内 label 需 pointer-events:none，避免点到文字无数据。
 */

export type BoardDrillHit = {
  series: string;
  drillKey?: string;
};

/** 从图表点列表构建 series/key → drillKey 回查表 */
export function buildBoardDrillLookup(
  points: Array<{ series?: string; key?: string; fullSeries?: string }> | undefined | null,
): Map<string, string> {
  const map = new Map<string, string>();
  for (const p of points || []) {
    const series = p.series != null ? String(p.series) : '';
    const full = p.fullSeries != null ? String(p.fullSeries) : series;
    const key = p.key != null && p.key !== '' ? String(p.key) : '';
    if (series) {
      if (key) map.set(series, key);
      else if (!map.has(series)) map.set(series, series);
    }
    if (full && full !== series) {
      if (key) map.set(full, key);
      else if (!map.has(full)) map.set(full, full);
    }
    if (key) {
      map.set(key, key);
    }
  }
  return map;
}

function asRecord(v: unknown): Record<string, unknown> | undefined {
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    return v as Record<string, unknown>;
  }
  return undefined;
}

function pickString(obj: Record<string, unknown> | undefined, ...keys: string[]): string | undefined {
  if (!obj) return undefined;
  for (const k of keys) {
    const v = obj[k];
    if (v != null && v !== '') return String(v);
  }
  return undefined;
}

/** 兼容 element:click / label 残留 / 嵌套 data 多种形态 */
export function extractBoardClickDatum(evt: unknown): Record<string, unknown> | undefined {
  const e = asRecord(evt);
  const data = asRecord(e?.data) ?? e;
  if (!data) return undefined;

  const candidates: unknown[] = [
    data.data,
    data.datum,
    asRecord(data.data)?.data,
    asRecord(data.data)?.datum,
    data.origin,
    asRecord(data.data)?.origin,
    data.items,
  ];

  for (const c of candidates) {
    if (Array.isArray(c) && c.length) {
      const first = asRecord(c[0]);
      if (first) {
        // tooltip items: { data / origin / value }
        return asRecord(first.data) || asRecord(first.origin) || asRecord(first.datum) || first;
      }
    }
    const rec = asRecord(c);
    if (rec) return rec;
  }
  // 有的事件直接把系列挂在 data 上
  if (pickString(data, 'series', 'fullSeries', 'drillKey', 'seriesKey', 'key', 'color')) {
    return data;
  }
  return undefined;
}

export function resolveBoardDrillHit(evt: unknown, lookup?: Map<string, string>): BoardDrillHit | undefined {
  const datum = extractBoardClickDatum(evt);
  if (!datum) return undefined;

  const series = pickString(datum, 'fullSeries', 'series', 'color', 'seriesKey', 'name', 'label') || '';
  let drillKey = pickString(datum, 'drillKey', 'seriesKey', 'key');

  if ((!drillKey || drillKey === 'undefined') && lookup) {
    const bySeries = series ? lookup.get(series) : undefined;
    if (bySeries) drillKey = bySeries;
  }
  if ((!drillKey || drillKey === 'undefined') && lookup) {
    for (const k of ['color', 'name', 'label'] as const) {
      const alt = pickString(datum, k);
      if (alt && lookup.has(alt)) {
        drillKey = lookup.get(alt);
        break;
      }
    }
  }

  if (!series && !drillKey) return undefined;
  return {
    series: series || drillKey || '',
    drillKey: drillKey || undefined,
  };
}

type ChartLike = {
  on?: (event: string, handler: (evt: unknown) => void) => void;
  off?: (event: string, handler: (evt: unknown) => void) => void;
};

/**
 * 绑定柱图下钻。lookupRef 在每次数据更新时指向最新回查表。
 * 同时监听 element:click，避免只绑一次后数据字段变化失效。
 */
export function bindBoardColumnDrill(
  plot: { chart?: ChartLike } | undefined,
  onHit: (hit: BoardDrillHit) => void,
  lookupRef?: { current: Map<string, string> },
) {
  const chart = plot?.chart;
  if (!chart?.on) return;

  const handler = (evt: unknown) => {
    const hit = resolveBoardDrillHit(evt, lookupRef?.current);
    if (!hit) return;
    onHit(hit);
  };

  chart.on('element:click', handler);
  // 部分版本点中 interval 内文本会走 label 相关事件
  chart.on('label:click', handler);
  chart.on('interval:click', handler);
}
