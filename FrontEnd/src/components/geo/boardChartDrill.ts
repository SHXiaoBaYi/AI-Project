/**
 * 看板分组柱图下钻：统一解析 G2 点击事件。
 * G2 常把 series 收成颜色域下标，而 color 通道反而仍是真实系列名（平台名/话题名）。
 */

export type BoardDrillHit = {
  series: string;
  drillKey?: string;
};

/** 从图表点列表构建 series → drillKey 回查表（勿把 drillKey 写成数据字段 key，G2 会当成系列） */
export function buildBoardDrillLookup(
  points: Array<{ series?: string; drillKey?: string; key?: string; fullSeries?: string }> | undefined | null,
): Map<string, string> {
  const map = new Map<string, string>();
  for (const p of points || []) {
    const series = p.series != null ? String(p.series) : '';
    const full = p.fullSeries != null ? String(p.fullSeries) : series;
    const key =
      p.drillKey != null && String(p.drillKey) !== ''
        ? String(p.drillKey)
        : p.key != null && String(p.key) !== ''
          ? String(p.key)
          : '';
    if (series) {
      if (key) map.set(series, key);
      else if (!map.has(series)) map.set(series, series);
    }
    if (full && full !== series) {
      if (key) map.set(full, key);
      else if (!map.has(full)) map.set(full, full);
    }
  }
  return map;
}

function isColorToken(raw: string) {
  return /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(raw.trim()) || /^(rgb|hsl)a?\(/i.test(raw.trim());
}

function isPureIndex(raw: string) {
  return /^\d+$/.test(raw.trim());
}

function uniqueNames(points: Array<{ series?: string }> | undefined | null): string[] {
  const names: string[] = [];
  const seen = new Set<string>();
  for (const p of points || []) {
    const name = p.series != null ? String(p.series) : '';
    if (!name || seen.has(name)) continue;
    seen.add(name);
    names.push(name);
  }
  return names;
}

/**
 * 把点击得到的 series/drillKey 还原成图表里的真实系列名。
 * seriesOrder 应与图例/颜色域顺序一致（用于下标还原）。
 */
export function resolveBoardSeriesName(
  rawSeries: string | undefined,
  rawDrillKey: string | undefined,
  points: Array<{ series?: string; key?: string; drillKey?: string }> | undefined | null,
  seriesOrder?: string[],
): string | undefined {
  const rows = points || [];
  const names = seriesOrder?.length ? [...seriesOrder] : uniqueNames(rows);
  const nameSet = new Set(names);

  const match = (raw?: string): string | undefined => {
    if (!raw || isColorToken(raw)) return undefined;
    if (nameSet.has(raw)) return raw;
    const hit = rows.find(
      (p) => p.series === raw || p.key === raw || p.drillKey === raw || String(p.key ?? '') === raw,
    );
    if (hit?.series && nameSet.has(String(hit.series))) return String(hit.series);
    if (hit?.series) return String(hit.series);
    if (isPureIndex(raw)) {
      const idx = Number(raw);
      if (Number.isInteger(idx) && idx >= 0 && idx < names.length) return names[idx];
    }
    return undefined;
  };

  return match(rawSeries) || match(rawDrillKey);
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

/** 在事件树里深搜：命中已知系列名则返回（比通道字段更稳） */
function deepFindKnownSeries(node: unknown, known: Set<string>, depth = 0): string | undefined {
  if (depth > 8 || node == null) return undefined;
  if (typeof node === 'string') {
    return known.has(node) ? node : undefined;
  }
  if (typeof node !== 'object') return undefined;
  if (Array.isArray(node)) {
    for (const item of node) {
      const hit = deepFindKnownSeries(item, known, depth + 1);
      if (hit) return hit;
    }
    return undefined;
  }
  const rec = node as Record<string, unknown>;
  for (const k of ['fullSeries', 'series', 'drillKey', 'seriesKey', 'color', 'name', 'label', 'title']) {
    const v = rec[k];
    if (typeof v === 'string' && known.has(v)) return v;
  }
  for (const v of Object.values(rec)) {
    const hit = deepFindKnownSeries(v, known, depth + 1);
    if (hit) return hit;
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
        return asRecord(first.data) || asRecord(first.origin) || asRecord(first.datum) || first;
      }
    }
    const rec = asRecord(c);
    if (rec) return rec;
  }
  if (pickString(data, 'series', 'fullSeries', 'drillKey', 'seriesKey', 'color', 'name', 'label')) {
    return data;
  }
  return undefined;
}

export function resolveBoardDrillHit(
  evt: unknown,
  lookup?: Map<string, string>,
  seriesOrder?: string[],
): BoardDrillHit | undefined {
  const known = new Set(seriesOrder || []);
  // 优先：事件树里直接出现的真实系列名（平台名等）
  const fromTree = known.size ? deepFindKnownSeries(evt, known) : undefined;

  const datum = extractBoardClickDatum(evt);
  let series = fromTree || '';
  let drillKey: string | undefined;

  if (datum) {
    const fullSeries = pickString(datum, 'fullSeries');
    const rawSeries = pickString(datum, 'series', 'seriesKey', 'name', 'label');
    // G2 分组柱：color 通道经常仍是类别名（如「小红书」），而 series 可能是下标
    const colorVal = pickString(datum, 'color');
    const colorAsSeries = colorVal && !isColorToken(colorVal) ? colorVal : undefined;

    if (!series) {
      if (fullSeries && (!known.size || known.has(fullSeries))) series = fullSeries;
      else if (colorAsSeries && (!known.size || known.has(colorAsSeries))) series = colorAsSeries;
      else if (rawSeries && (!known.size || known.has(rawSeries) || !isPureIndex(rawSeries))) series = rawSeries;
      else if (colorAsSeries) series = colorAsSeries;
      else if (rawSeries) series = rawSeries;
    }

    drillKey = pickString(datum, 'drillKey', 'seriesKey');
  }

  // 下标 → 图例顺序系列名
  if (seriesOrder?.length && series && isPureIndex(series)) {
    const idx = Number(series);
    if (idx >= 0 && idx < seriesOrder.length) series = seriesOrder[idx];
  }
  if (series && isColorToken(series)) series = '';

  if (series && lookup?.has(series)) {
    drillKey = lookup.get(series);
  } else if (!drillKey && series) {
    drillKey = series;
  }

  // 仍没有系列名时，再用下标猜 drillKey
  if (!series && drillKey && seriesOrder?.length && isPureIndex(drillKey)) {
    const idx = Number(drillKey);
    if (idx >= 0 && idx < seriesOrder.length) {
      series = seriesOrder[idx];
      drillKey = lookup?.get(series) || series;
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
 * 绑定柱图下钻。lookupRef / seriesOrderRef 在每次数据更新时指向最新值。
 */
export function bindBoardColumnDrill(
  plot: { chart?: ChartLike } | undefined,
  onHit: (hit: BoardDrillHit) => void,
  lookupRef?: { current: Map<string, string> },
  seriesOrderRef?: { current: string[] },
) {
  const chart = plot?.chart;
  if (!chart?.on) return;

  const handler = (evt: unknown) => {
    const hit = resolveBoardDrillHit(evt, lookupRef?.current, seriesOrderRef?.current);
    if (!hit) return;
    onHit(hit);
  };

  chart.on('element:click', handler);
  chart.on('label:click', handler);
  chart.on('interval:click', handler);
}
