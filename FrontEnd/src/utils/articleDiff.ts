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
 * 不做整篇段落 diff。
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
