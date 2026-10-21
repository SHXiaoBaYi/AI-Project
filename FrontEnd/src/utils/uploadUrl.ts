import { withBase } from '@/utils/basePath';

/**
 * 附件根解析规则（禁止在前端源码写死生产/测试 ECS IP）：
 * 1) VITE_UPLOAD_PUBLIC_BASE（仅本地 .env：测 170.82 / 慎用生产 119.134）
 * 2) 浏览器同源：当前页 origin + /shxby/api（生产打开就是 119.134，测试打开就是 170.82）
 * 3) 无浏览器上下文：相对路径 withBase('/api/uploads/...')
 */
function uploadPublicBase(): string {
  const fromEnv = String(import.meta.env.VITE_UPLOAD_PUBLIC_BASE || '')
    .trim()
    .replace(/\/$/, '');
  if (fromEnv) return fromEnv;

  if (typeof window !== 'undefined' && window.location?.origin) {
    const apiPath = withBase('/api').replace(/\/$/, '');
    return `${window.location.origin}${apiPath}`;
  }

  return '';
}

/** 从任意 URL/路径中取出 /uploads/... 相对路径 */
function extractUploadsPath(raw: string): string | null {
  const text = raw.trim();
  if (!text) return null;
  if (text.startsWith('/uploads/')) return text;
  if (text.startsWith('/api/uploads/')) return text.slice(4);
  try {
    if (/^https?:\/\//i.test(text)) {
      const u = new URL(text);
      const path = u.pathname || '';
      const idx = path.indexOf('/uploads/');
      if (idx >= 0) return path.slice(idx);
      if (path.startsWith('/api/uploads/')) return path.slice(4);
    }
  } catch {
    /* ignore */
  }
  const idx = text.indexOf('/uploads/');
  if (idx >= 0) return text.slice(idx);
  return null;
}

/**
 * 把后端落库或接口返回的附件路径，统一成可打开地址。
 * 线上跟打开页面的环境走；本地用 .env.example 里的测试地址。
 */
export function resolveUploadUrl(url?: string | null): string {
  if (!url) return '#';
  const raw = String(url).trim();
  if (!raw) return '#';
  if (/^(data|blob):/i.test(raw)) return raw;

  const uploadsPath = extractUploadsPath(raw);
  if (uploadsPath) {
    const base = uploadPublicBase();
    if (base) return `${base}${uploadsPath}`;
    return withBase(`/api${uploadsPath}`);
  }

  // 非上传资源：保持相对 api 前缀
  if (/^https?:\/\//i.test(raw)) return raw;
  const path = raw.startsWith('/') ? raw : `/${raw}`;
  return withBase(path.startsWith('/api/') ? path : `/api${path}`);
}
