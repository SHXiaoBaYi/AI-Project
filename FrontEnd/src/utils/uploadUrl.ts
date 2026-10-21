import { withBase } from '@/utils/basePath';

/**
 * 仅作非浏览器兜底；线上页面优先跟当前站点同源，避免写死测试/生产 IP。
 * 本地若要强制打开某台 ECS 附件，设 VITE_UPLOAD_PUBLIC_BASE。
 */
export const UPLOAD_PUBLIC_BASE = 'http://121.40.170.82/shxby/api';

function uploadPublicBase(): string {
  const fromEnv = String(import.meta.env.VITE_UPLOAD_PUBLIC_BASE || '')
    .trim()
    .replace(/\/$/, '');
  if (fromEnv) return fromEnv;

  // 钉钉 H5 / 线上后台：跟当前页面同源（含 /shxby/api），配图与接口同机，文档更新后不再指到错环境
  if (typeof window !== 'undefined' && window.location?.origin) {
    const apiPath = withBase('/api').replace(/\/$/, '');
    return `${window.location.origin}${apiPath}`;
  }

  return UPLOAD_PUBLIC_BASE;
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
 * 默认：当前站点同源 `/shxby/api/uploads/...`；本地可用 VITE_UPLOAD_PUBLIC_BASE 指向某台 ECS。
 */
export function resolveUploadUrl(url?: string | null): string {
  if (!url) return '#';
  const raw = String(url).trim();
  if (!raw) return '#';
  if (/^(data|blob):/i.test(raw)) return raw;

  const uploadsPath = extractUploadsPath(raw);
  if (uploadsPath) {
    return `${uploadPublicBase()}${uploadsPath}`;
  }

  // 非上传资源：保持相对 api 前缀
  if (/^https?:\/\//i.test(raw)) return raw;
  const path = raw.startsWith('/') ? raw : `/${raw}`;
  return withBase(path.startsWith('/api/') ? path : `/api${path}`);
}
