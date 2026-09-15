/** Vite 注入的资源公共路径，生产环境为 `/shxby/`，开发为 `/` */
export const BASE_URL = import.meta.env.BASE_URL || '/';

/** React Router basename，无尾斜杠；根路径时为 undefined */
export function getRouterBasename(): string | undefined {
  const base = BASE_URL.replace(/\/$/, '');
  return base && base !== '/' ? base : undefined;
}

/** 拼出带部署前缀的绝对路径，如 `/shxby/login`、`/shxby/api` */
export function withBase(path: string): string {
  const base = BASE_URL.endsWith('/') ? BASE_URL : `${BASE_URL}/`;
  const normalized = path.replace(/^\//, '');
  return `${base}${normalized}`;
}
