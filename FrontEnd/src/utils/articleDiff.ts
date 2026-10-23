/** 从 Lake / HTML 正文抽出可读段落，供对照差异 */

export function lakeToBlocks(raw?: string | null): string[] {
  if (!raw) return [];
  let text = raw.trim();
  // Lake JSON（含 text 字段）时尽量抽出纯文本
  if ((text.startsWith('{') || text.startsWith('[')) && text.includes('"text"')) {
    const parts: string[] = [];
    const re = /"text"\s*:\s*"((?:\\.|[^"\\])*)"/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) != null) {
      try {
        parts.push(JSON.parse(`"${m[1]}"`));
      } catch {
        parts.push(m[1].replace(/\\n/g, '\n').replace(/\\"/g, '"'));
      }
    }
    if (parts.length > 0) {
      return parts
        .join('\n')
        .split(/\n+/)
        .map((s) => s.replace(/\s+/g, ' ').trim())
        .filter((s) => s.length > 0);
    }
  }
  // 常见段落/换行标签 → 断块
  text = text
    .replace(/<\s*br\s*\/?>/gi, '\n')
    .replace(/<\/\s*(p|div|h[1-6]|li|tr|blockquote|section)\s*>/gi, '\n')
    .replace(/<\s*(p|div|h[1-6]|li|tr|blockquote|section)(\s[^>]*)?>/gi, '\n');
  // 去掉其余标签与卡片残留
  text = text.replace(/<[^>]+>/g, '');
  text = text
    .replace(/&nbsp;/gi, ' ')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/g, "'");
  return text
    .split(/\n+/)
    .map((s) => s.replace(/\s+/g, ' ').trim())
    .filter((s) => s.length > 0);
}

export type DiffBlock =
  | { type: 'equal'; text: string }
  | { type: 'remove'; text: string }
  | { type: 'add'; text: string }
  | { type: 'change'; before: string; after: string };

/** 字级 / 片段级内联差异 */
export type InlinePart =
  { type: 'equal'; text: string } | { type: 'remove'; text: string } | { type: 'add'; text: string };

export type RefinedDiffBlock =
  | { type: 'equal'; text: string }
  | { type: 'remove'; text: string }
  | { type: 'add'; text: string }
  | { type: 'change'; before: string; after: string; parts: InlinePart[] };

/** 基于 LCS 的段落级差异 */
export function diffBlocks(leftBlocks: string[], rightBlocks: string[]): DiffBlock[] {
  const n = leftBlocks.length;
  const m = rightBlocks.length;
  const dp: number[][] = Array.from({ length: n + 1 }, () => Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      if (leftBlocks[i] === rightBlocks[j]) {
        dp[i][j] = dp[i + 1][j + 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }
  const out: DiffBlock[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (leftBlocks[i] === rightBlocks[j]) {
      out.push({ type: 'equal', text: leftBlocks[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      // 可能是删除，或与后续替换：若右侧下一块等于左侧下一块则删除，否则尝试 change
      if (i + 1 < n && leftBlocks[i + 1] === rightBlocks[j]) {
        out.push({ type: 'remove', text: leftBlocks[i] });
        i++;
      } else if (j + 1 < m && leftBlocks[i] === rightBlocks[j + 1]) {
        out.push({ type: 'add', text: rightBlocks[j] });
        j++;
      } else {
        out.push({ type: 'change', before: leftBlocks[i], after: rightBlocks[j] });
        i++;
        j++;
      }
    } else {
      if (j + 1 < m && leftBlocks[i] === rightBlocks[j + 1]) {
        out.push({ type: 'add', text: rightBlocks[j] });
        j++;
      } else if (i + 1 < n && leftBlocks[i + 1] === rightBlocks[j]) {
        out.push({ type: 'remove', text: leftBlocks[i] });
        i++;
      } else {
        out.push({ type: 'change', before: leftBlocks[i], after: rightBlocks[j] });
        i++;
        j++;
      }
    }
  }
  while (i < n) {
    out.push({ type: 'remove', text: leftBlocks[i++] });
  }
  while (j < m) {
    out.push({ type: 'add', text: rightBlocks[j++] });
  }
  return out;
}

/** 字符 LCS 长度（短串）；超长时降采样近似 */
function lcsLength(a: string, b: string): number {
  const n = a.length;
  const m = b.length;
  if (n === 0 || m === 0) return 0;
  // 限制 DP 规模，避免超长段落卡死
  if (n * m > 400_000) {
    return approxLcsLength(a, b);
  }
  let prev = new Array<number>(m + 1).fill(0);
  let curr = new Array<number>(m + 1).fill(0);
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      if (a[i - 1] === b[j - 1]) curr[j] = prev[j - 1] + 1;
      else curr[j] = Math.max(prev[j], curr[j - 1]);
    }
    const tmp = prev;
    prev = curr;
    curr = tmp;
    curr.fill(0);
  }
  return prev[m];
}

function approxLcsLength(a: string, b: string): number {
  const step = Math.max(1, Math.ceil(Math.max(a.length, b.length) / 800));
  const aa = [...a].filter((_, i) => i % step === 0).join('');
  const bb = [...b].filter((_, i) => i % step === 0).join('');
  return lcsLength(aa, bb) * step;
}

/** 两段文本相似度 0~1（基于字符 LCS） */
export function textSimilarity(a: string, b: string): number {
  if (!a && !b) return 1;
  if (!a || !b) return 0;
  if (a === b) return 1;
  return (2 * lcsLength(a, b)) / (a.length + b.length);
}

/**
 * 字级 diff：尽量合并连续同类型片段，便于阅读。
 * 中文按字比对，改几个字时只高亮那几个字。
 */
export function diffChars(left: string, right: string): InlinePart[] {
  if (left === right) return left ? [{ type: 'equal', text: left }] : [];
  if (!left) return right ? [{ type: 'add', text: right }] : [];
  if (!right) return [{ type: 'remove', text: left }];

  const a = left;
  const b = right;
  const n = a.length;
  const m = b.length;

  // 超长段落：退化为「公共前后缀 + 中间整段替换」，避免 O(n*m) 爆炸
  if (n * m > 400_000) {
    return diffCharsCoarse(a, b);
  }

  const dp: number[][] = Array.from({ length: n + 1 }, () => Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      if (a[i] === b[j]) dp[i][j] = dp[i + 1][j + 1] + 1;
      else dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }

  const raw: InlinePart[] = [];
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      raw.push({ type: 'equal', text: a[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      raw.push({ type: 'remove', text: a[i++] });
    } else {
      raw.push({ type: 'add', text: b[j++] });
    }
  }
  while (i < n) raw.push({ type: 'remove', text: a[i++] });
  while (j < m) raw.push({ type: 'add', text: b[j++] });
  return mergeInlineParts(raw);
}

function diffCharsCoarse(a: string, b: string): InlinePart[] {
  let start = 0;
  const minLen = Math.min(a.length, b.length);
  while (start < minLen && a[start] === b[start]) start++;
  let endA = a.length;
  let endB = b.length;
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) {
    endA--;
    endB--;
  }
  const parts: InlinePart[] = [];
  if (start > 0) parts.push({ type: 'equal', text: a.slice(0, start) });
  if (endA > start) parts.push({ type: 'remove', text: a.slice(start, endA) });
  if (endB > start) parts.push({ type: 'add', text: b.slice(start, endB) });
  if (endA < a.length) parts.push({ type: 'equal', text: a.slice(endA) });
  return mergeInlineParts(parts);
}

function mergeInlineParts(parts: InlinePart[]): InlinePart[] {
  const out: InlinePart[] = [];
  for (const p of parts) {
    if (!p.text) continue;
    const last = out[out.length - 1];
    if (last && last.type === p.type) {
      last.text += p.text;
    } else {
      out.push({ ...p });
    }
  }
  return out;
}

/**
 * 相邻「整段删除 + 整段新增」若相似度够高，合并为 change，避免只改几字却整段红绿对打。
 */
export function refineDiffBlocks(blocks: DiffBlock[], similarityThreshold = 0.32): DiffBlock[] {
  const out: DiffBlock[] = [];
  for (let i = 0; i < blocks.length; i++) {
    const cur = blocks[i];
    const next = blocks[i + 1];
    if (cur.type === 'remove' && next?.type === 'add') {
      const sim = textSimilarity(cur.text, next.text);
      if (sim >= similarityThreshold) {
        out.push({ type: 'change', before: cur.text, after: next.text });
        i++;
        continue;
      }
    }
    if (cur.type === 'add' && next?.type === 'remove') {
      const sim = textSimilarity(cur.text, next.text);
      if (sim >= similarityThreshold) {
        out.push({ type: 'change', before: next.text, after: cur.text });
        i++;
        continue;
      }
    }
    out.push(cur);
  }
  return out;
}

/** 正文对照：段落对齐 + 改动段落内字级高亮 */
export function diffArticleBody(left?: string | null, right?: string | null): RefinedDiffBlock[] {
  const raw = refineDiffBlocks(diffBlocks(lakeToBlocks(left), lakeToBlocks(right)));
  return raw.map((b): RefinedDiffBlock => {
    if (b.type === 'change') {
      return { type: 'change', before: b.before, after: b.after, parts: diffChars(b.before, b.after) };
    }
    return b;
  });
}

export function titleDiff(left?: string | null, right?: string | null): DiffBlock | null {
  const a = (left || '').trim();
  const b = (right || '').trim();
  if (a === b) {
    return a ? { type: 'equal', text: a } : null;
  }
  if (!a && b) return { type: 'add', text: b };
  if (a && !b) return { type: 'remove', text: a };
  return { type: 'change', before: a, after: b };
}

export type QuoteAnno = {
  quoteText: string;
  commentText?: string;
  authorName?: string;
};

export type QuoteDiffItem = {
  quote: string;
  comment?: string;
  authorName?: string;
  /** 划词在左/右稿中的命中片段（归一化后） */
  leftHit?: string | null;
  rightHit?: string | null;
  block: DiffBlock;
  /** 若划词在两侧都能定位但正文上下文有变，附字级 parts（可选） */
  parts?: InlinePart[];
};

function normalizePlain(raw?: string | null): string {
  return lakeToBlocks(raw).join('\n');
}

function findQuoteHit(plain: string, quote: string): string | null {
  const q = (quote || '').replace(/\s+/g, ' ').trim();
  if (!q || !plain) return null;
  const compactPlain = plain.replace(/\s+/g, ' ');
  const idx = compactPlain.indexOf(q);
  if (idx >= 0) return q;
  // 宽松：去掉空白再找
  const loosePlain = plain.replace(/\s+/g, '');
  const looseQ = q.replace(/\s+/g, '');
  if (looseQ && loosePlain.includes(looseQ)) return q;
  return null;
}

/**
 * 仅针对划词批注做对照：以批注原文为锚，看左右稿是否仍包含该片段。
 */
export function diffAnnotationQuotes(
  leftBody?: string | null,
  rightBody?: string | null,
  annotations?: QuoteAnno[] | null,
): QuoteDiffItem[] {
  const quotes = (annotations ?? [])
    .map((a) => ({
      quote: (a.quoteText || '').replace(/\s+/g, ' ').trim(),
      comment: (a.commentText || '').trim() || undefined,
      authorName: a.authorName,
    }))
    .filter((a) => a.quote.length > 0);
  if (quotes.length === 0) return [];

  const leftPlain = normalizePlain(leftBody);
  const rightPlain = normalizePlain(rightBody);
  const seen = new Set<string>();
  const out: QuoteDiffItem[] = [];

  for (const row of quotes) {
    if (seen.has(row.quote)) continue;
    seen.add(row.quote);
    const leftHit = findQuoteHit(leftPlain, row.quote);
    const rightHit = findQuoteHit(rightPlain, row.quote);
    let block: DiffBlock;
    if (leftHit && rightHit) {
      block = { type: 'equal', text: row.quote };
    } else if (leftHit && !rightHit) {
      block = { type: 'remove', text: row.quote };
    } else if (!leftHit && rightHit) {
      block = { type: 'add', text: row.quote };
    } else {
      // 两稿都找不到：仍展示划词原文，标为改动提示「已不在正文中」
      block = { type: 'change', before: row.quote, after: '（两稿正文中均未定位到该划词）' };
    }
    out.push({
      quote: row.quote,
      comment: row.comment,
      authorName: row.authorName,
      leftHit,
      rightHit,
      block,
    });
  }
  return out;
}
