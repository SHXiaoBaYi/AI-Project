import { canOpenExternalInApp, resolveExternalUrl } from '@/utils/externalUrl';

/** 常见 AI 平台名称 → 允许的链接主机后缀（含子域） */
const PLATFORM_HOST_ALIASES: Record<string, string[]> = {
  豆包: ['doubao.com'],
  DS: ['deepseek.com'],
  DeepSeek: ['deepseek.com'],
  deepseek: ['deepseek.com'],
  通义: ['tongyi.aliyun.com', 'qianwen.com', 'aliyun.com'],
  通义千问: ['tongyi.aliyun.com', 'qianwen.com', 'aliyun.com'],
  千问: ['tongyi.aliyun.com', 'qianwen.com', 'aliyun.com'],
  文心: ['yiyan.baidu.com', 'baidu.com'],
  文心一言: ['yiyan.baidu.com', 'baidu.com'],
  Kimi: ['kimi.moonshot.cn', 'moonshot.cn'],
  kimi: ['kimi.moonshot.cn', 'moonshot.cn'],
  月之暗面: ['kimi.moonshot.cn', 'moonshot.cn'],
  智谱: ['chatglm.cn', 'bigmodel.cn', 'zhipuai.cn'],
  智谱清言: ['chatglm.cn', 'bigmodel.cn', 'zhipuai.cn'],
  元宝: ['yuanbao.tencent.com'],
  腾讯元宝: ['yuanbao.tencent.com'],
  ChatGPT: ['chatgpt.com', 'openai.com'],
  GPT: ['chatgpt.com', 'openai.com'],
  Gemini: ['gemini.google.com', 'google.com'],
  讯飞: ['xfyun.cn', 'xinghuo.xfyun.cn'],
  星火: ['xfyun.cn', 'xinghuo.xfyun.cn'],
  百川: ['baichuan-ai.com'],
  海螺: ['hailuoai.com', 'minimax.io'],
  秘塔: ['metaso.cn'],
  Perplexity: ['perplexity.ai'],
};

function hostMatchesSuffix(host: string, suffix: string): boolean {
  const h = host.toLowerCase();
  const s = suffix.toLowerCase().replace(/^\./, '');
  return h === s || h.endsWith(`.${s}`);
}

function hostnameFromUrl(url?: string | null): string | undefined {
  if (!url?.trim()) return undefined;
  try {
    const resolved =
      resolveExternalUrl(url) || (/^https?:\/\//i.test(url.trim()) ? url.trim() : `https://${url.trim()}`);
    return new URL(resolved).hostname.toLowerCase() || undefined;
  } catch {
    return undefined;
  }
}

/** 解析某平台允许的链接主机后缀 */
export function getGeoPlatformAllowedHosts(platformName: string, loginUrl?: string | null): string[] {
  const hosts = new Set<string>();
  const fromLogin = hostnameFromUrl(loginUrl);
  if (fromLogin) {
    // login 主机本身 + 去掉首段子域后的主域（如 www.doubao.com → doubao.com）
    hosts.add(fromLogin);
    const parts = fromLogin.split('.');
    if (parts.length >= 2) {
      hosts.add(parts.slice(-2).join('.'));
    }
  }
  const name = platformName.trim();
  const aliases = PLATFORM_HOST_ALIASES[name];
  if (aliases) {
    aliases.forEach((h) => hosts.add(h.toLowerCase()));
  } else {
    // 模糊匹配：平台名包含别名 key
    for (const [key, list] of Object.entries(PLATFORM_HOST_ALIASES)) {
      if (name.includes(key) || key.includes(name)) {
        list.forEach((h) => hosts.add(h.toLowerCase()));
      }
    }
  }
  return [...hosts];
}

/**
 * 校验第三方链接是否为选中平台的合法外链。
 * - 必须先是可解析的 http(s) 链接
 * - 若能解析到该平台允许域名，则主机名须匹配其一
 * - 无可用域名规则时（未知平台且无登录地址），仅要求是合法外链
 */
export function isGeoPlatformLink(platformName: string, url?: string | null, loginUrl?: string | null): boolean {
  if (!canOpenExternalInApp(url)) return false;
  const resolved = resolveExternalUrl(url);
  if (!resolved) return false;
  let host: string;
  try {
    host = new URL(resolved).hostname.toLowerCase();
  } catch {
    return false;
  }
  const allowed = getGeoPlatformAllowedHosts(platformName, loginUrl);
  if (!allowed.length) return true;
  return allowed.some((suffix) => hostMatchesSuffix(host, suffix));
}

/** 提及=是时，推荐状态仅允许这两种 */
export const GEO_MENTIONED_RECOMMEND_STATUSES = ['出现且推荐', '出现未推荐'] as const;

export function isMentionedRecommendStatus(value?: string | null): boolean {
  return GEO_MENTIONED_RECOMMEND_STATUSES.includes(value as (typeof GEO_MENTIONED_RECOMMEND_STATUSES)[number]);
}
