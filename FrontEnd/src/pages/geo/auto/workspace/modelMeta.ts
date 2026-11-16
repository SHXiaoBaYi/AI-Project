import type { GeoAutoPlatformOption } from '@/types/geoAuto';
import { withBase } from '@/utils/basePath';

const OVERSEAS_RE = /chatgpt|gpt|openai|google|gemini|grok|copilot|perplexity|amazon|claude|bing/i;

/** 免费图标：Lobe Icons（MIT）+ Simple Icons 小红书（CC0） */
const ICON_RULES: { test: RegExp; file: string; bg: string }[] = [
  { test: /豆包/, file: 'doubao-color.svg', bg: '#EEF0FF' },
  { test: /deepseek|^ds$/i, file: 'deepseek-color.svg', bg: '#ECFDF5' },
  { test: /通义|千问|qwen/i, file: 'qwen-color.svg', bg: '#FFF7ED' },
  { test: /kimi|月之暗面|moonshot/i, file: 'kimi-color.svg', bg: '#F5F3FF' },
  { test: /元宝/, file: 'yuanbao-color.svg', bg: '#FEF2F2' },
  { test: /混元|hunyuan/i, file: 'hunyuan-color.svg', bg: '#FEF2F2' },
  { test: /腾讯/, file: 'tencent-color.svg', bg: '#FEF2F2' },
  { test: /文心|一言/, file: 'wenxin-color.svg', bg: '#EFF6FF' },
  { test: /百度/, file: 'baidu-color.svg', bg: '#EFF6FF' },
  { test: /小红书/, file: 'xiaohongshu.svg', bg: '#FFF1F2' },
  { test: /夸克/, file: 'quark.svg', bg: '#ECFEFF' },
];

const COLOR_MAP: { test: RegExp; bg: string; fg: string }[] = [
  { test: /豆包/, bg: '#fff3e8', fg: '#d97706' },
  { test: /deepseek|^ds$/i, bg: '#ecfdf5', fg: '#059669' },
  { test: /通义|千问/, bg: '#fff7ed', fg: '#ea580c' },
  { test: /文心|百度/, bg: '#eff6ff', fg: '#2563eb' },
  { test: /kimi|月之暗面/i, bg: '#f5f3ff', fg: '#7c3aed' },
  { test: /元宝|腾讯|混元/, bg: '#fef2f2', fg: '#dc2626' },
  { test: /小红书/, bg: '#fff1f2', fg: '#e11d48' },
  { test: /夸克/, bg: '#ecfeff', fg: '#0891b2' },
  { test: /知乎/, bg: '#eff6ff', fg: '#1d4ed8' },
];

export function isOverseasPlatform(name: string) {
  return OVERSEAS_RE.test(name);
}

export function platformIconSrc(name: string) {
  const hit = ICON_RULES.find((item) => item.test.test(name));
  return hit ? withBase(`geo-models/${hit.file}`) : undefined;
}

export function platformAvatar(name: string) {
  const color = COLOR_MAP.find((item) => item.test.test(name));
  const icon = ICON_RULES.find((item) => item.test.test(name));
  return {
    bg: icon?.bg ?? color?.bg ?? '#f1f5f9',
    fg: color?.fg ?? '#475569',
    src: platformIconSrc(name),
    letter:
      name
        .replace(/网页版|AI/g, '')
        .trim()
        .slice(0, 1) || 'A',
  };
}

export function splitPlatforms(list: GeoAutoPlatformOption[]) {
  const domestic: GeoAutoPlatformOption[] = [];
  const overseas: GeoAutoPlatformOption[] = [];
  list.forEach((p) => {
    if (isOverseasPlatform(p.platformName)) {
      overseas.push(p);
    } else {
      domestic.push(p);
    }
  });
  return { domestic, overseas };
}
