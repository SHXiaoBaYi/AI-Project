import { withBase } from '@/utils/basePath';

/** 线上附件根（含 /api）。查看/下载一律拼公网地址 */
export const UPLOAD_PUBLIC_BASE = 'http://121.40.119.134/shxby/api';

function uploadPublicBase(): string {
  const fromEnv = String(import.meta.env.VITE_UPLOAD_PUBLIC_BASE || '')
    .trim()
    .replace(/\/$/, '');
  return fromEnv || UPLOAD_PUBLIC_BASE;
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
 * 把后端落库或接口返回的附件路径，统一成公网可打开地址：
 * http://121.40.119.134/shxby/api/uploads/...
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
