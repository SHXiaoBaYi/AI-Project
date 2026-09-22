/** 粗略校验主机名：需含 TLD 的域名，或 IPv4 */
function isValidHostname(host: string): boolean {
  if (!host || host.length > 253) return false;
  const h = host.replace(/\.$/, '').toLowerCase();
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(h)) {
    return h.split('.').every((n) => {
      const num = Number(n);
      return num >= 0 && num <= 255;
    });
  }
  // example.com / sub.example.co.uk
  return /^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,}$/i.test(h);
}

/**
 * 是否像可打开的第三方 http(s) 链接。
 * 有值但明显不是链接（中文、无域名、乱填）→ false，仅展示不可点。
 */
export function canOpenExternalInApp(url?: string | null): boolean {
  if (!url?.trim()) return false;
  const u = url.trim();
  if (/\s/.test(u)) return false;
  // 含中日韩等字符，基本不是合法外链格式
  if (/[\u0080-\uffff]/.test(u.replace(/%[0-9a-f]{2}/gi, ''))) return false;
  if (u.startsWith('/') || u.startsWith('data:') || u.startsWith('javascript:')) return false;

  try {
    let parsed: URL;
    if (/^https?:\/\//i.test(u)) {
      parsed = new URL(u);
    } else if (u.startsWith('//')) {
      parsed = new URL(`https:${u}`);
    } else {
      // 无协议时必须已像 host/path，禁止把「无」「错误」拼成 https://无
      const hostCandidate = u.split('/')[0]?.split('?')[0]?.split('#')[0]?.split(':')[0] || '';
      if (!hostCandidate.includes('.')) return false;
      parsed = new URL(`https://${u}`);
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false;
    return isValidHostname(parsed.hostname);
  } catch {
    return false;
  }
}

/** 是否以 http(s) 开头（不校验域名合法性） */
export function isHttpUrl(v?: string | null): boolean {
  return !!v && /^https?:\/\//i.test(v.trim());
}

/** 合法外链补全协议；非法返回空串 */
export function resolveExternalUrl(url?: string | null): string {
  if (!canOpenExternalInApp(url)) return '';
  const u = url!.trim();
  if (/^https?:\/\//i.test(u)) return u;
  if (u.startsWith('//')) return `https:${u}`;
  return `https://${u}`;
}

/**
 * 已知禁止被 iframe 嵌入的站点（X-Frame-Options / CSP frame-ancestors）。
 * 浏览器无法跨域读取响应头，只能按主机名启发式判断。
 */
const IFRAME_BLOCKED_HOST_SUFFIXES = [
  'douyin.com',
  'iesdouyin.com',
  'toutiao.com',
  'jinritoutiao.com',
  'zhihu.com',
  'zhimg.com',
  'weixin.qq.com',
  'qq.com',
  'bilibili.com',
  'b23.tv',
  'xiaohongshu.com',
  'xhslink.com',
  'weibo.com',
  'weibo.cn',
  'sina.com.cn',
  'kuaishou.com',
  'chenzhongtech.com',
  'baidu.com',
  'csdn.net',
  'juejin.cn',
  'jianshu.com',
  'sspai.com',
  'thepaper.cn',
  '163.com',
  'sohu.com',
  'feishu.cn',
  'larksuite.com',
  'dingtalk.com',
  'aliwork.com',
  'yuque.com',
  'notion.so',
  'notion.site',
  'linkedin.com',
  'twitter.com',
  'x.com',
  'facebook.com',
  'instagram.com',
  'youtube.com',
  'youtu.be',
  'tiktok.com',
];

function hostMatchesSuffix(host: string, suffix: string): boolean {
  return host === suffix || host.endsWith(`.${suffix}`);
}

/** 该链接是否大概率禁止 iframe 嵌入，应直接新标签打开 */
export function blocksIframeEmbed(url?: string | null): boolean {
  const resolved = resolveExternalUrl(url);
  if (!resolved) return false;
  try {
    const host = new URL(resolved).hostname.toLowerCase();
    return IFRAME_BLOCKED_HOST_SUFFIXES.some((suffix) => hostMatchesSuffix(host, suffix));
  } catch {
    return false;
  }
}

/** 在系统外用浏览器新标签打开；失败时返回 false */
export function openExternalInNewTab(url?: string | null): boolean {
  const resolved = resolveExternalUrl(url);
  if (!resolved) return false;
  window.open(resolved, '_blank', 'noopener,noreferrer');
  return true;
}
