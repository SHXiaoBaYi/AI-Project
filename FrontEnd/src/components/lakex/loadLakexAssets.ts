import { withBase } from '@/utils/basePath';

export type LakexDocApi = {
  createOpenEditor: (root: HTMLElement, options?: Record<string, unknown>) => LakexEditorInstance;
  createOpenViewer: (root: HTMLElement, options?: Record<string, unknown>) => LakexEditorInstance;
};

export type LakexEditorInstance = {
  on?: (event: string, handler: (...args: unknown[]) => void) => (() => void) | void;
  getDocument?: (scheme: string, opts?: { includeMeta?: boolean }) => string;
  setDocument?: (scheme: string, content: string) => void;
  destroy?: () => void;
  execCommand?: (cmd: string, ...args: unknown[]) => void;
};

declare global {
  interface Window {
    Doc?: LakexDocApi;
    React?: unknown;
    ReactDOM?: unknown;
  }
}

const loadedStyles = new Set<string>();
const loadedScripts = new Set<string>();
let loadPromise: Promise<LakexDocApi> | null = null;

function asset(path: string) {
  return withBase(`lakex/${path}`);
}

function loadStyleOnce(href: string) {
  if (loadedStyles.has(href) || document.querySelector(`link[data-lakex="${href}"]`)) {
    loadedStyles.add(href);
    return Promise.resolve();
  }
  return new Promise<void>((resolve, reject) => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.dataset.lakex = href;
    link.onload = () => {
      loadedStyles.add(href);
      resolve();
    };
    link.onerror = () => reject(new Error(`加载样式失败: ${href}`));
    document.head.appendChild(link);
  });
}

/** 卸掉曾注入页面的 Lakex antd v4 样式，避免盖住后台表格 */
export function removeLakexAntdCss() {
  document.querySelectorAll('link[data-lakex]').forEach((node) => {
    const href = node.getAttribute('href') || '';
    if (!href.includes('antd.css')) return;
    node.remove();
    loadedStyles.delete(href);
  });
}

function preloadScript(src: string) {
  if (document.querySelector(`link[data-lakex-preload="${src}"]`)) return;
  const link = document.createElement('link');
  link.rel = 'preload';
  link.as = 'script';
  link.href = src;
  link.dataset.lakexPreload = src;
  document.head.appendChild(link);
}

function loadScriptOnce(src: string) {
  if (loadedScripts.has(src) || document.querySelector(`script[data-lakex="${src}"]`)) {
    loadedScripts.add(src);
    return Promise.resolve();
  }
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = false;
    script.dataset.lakex = src;
    script.onload = () => {
      loadedScripts.add(src);
      resolve();
    };
    script.onerror = () => reject(new Error(`加载脚本失败: ${src}`));
    document.body.appendChild(script);
  });
}

/** 动态加载本仓库镜像的 Lakex（public/lakex），返回 window.Doc */
export function loadLakexAssets(): Promise<LakexDocApi> {
  if (window.Doc?.createOpenEditor) {
    return Promise.resolve(window.Doc);
  }
  if (loadPromise) return loadPromise;

  loadPromise = (async () => {
    removeLakexAntdCss();
    // 先 preload 大包，再顺序执行，缩短「空等 → 下载」间隙
    preloadScript(asset('kitchen.js'));
    preloadScript(asset('doc.umd.js'));
    // 首屏只拉编辑器本体；CodeMirror / KaTeX 由编辑器按需通过 URL 再加载，避免每次打开卡顿
    await loadStyleOnce(asset('doc.css'));
    const g = window as Window & { react?: unknown; ReactDOM?: unknown; React?: unknown };
    const needReact = !g.React && !g.react;
    const needReactDom = !g.ReactDOM;
    await Promise.all([
      needReact ? loadScriptOnce(asset('react.production.min.js')) : Promise.resolve(),
      needReactDom ? loadScriptOnce(asset('react-dom.production.min.js')) : Promise.resolve(),
    ]);
    if (!g.React && g.react) g.React = g.react;
    await loadScriptOnce(asset('kitchen.js'));
    await loadScriptOnce(asset('doc.umd.js'));

    const doc = window.Doc;
    if (!doc?.createOpenEditor) {
      throw new Error('Lakex 未正确加载：window.Doc.createOpenEditor 不存在');
    }
    // 后台预热代码块/公式依赖，不阻塞首开
    void Promise.all([loadScriptOnce(asset('CodeMirror.js')), loadScriptOnce(asset('katex.js'))]).catch(
      () => undefined,
    );
    return doc;
  })().catch((err) => {
    loadPromise = null;
    throw err;
  });

  return loadPromise;
}

/** 立即预热（抽屉打开时与接口并行） */
export function prefetchLakexAssets() {
  return loadLakexAssets().catch(() => undefined);
}

/** 列表页空闲时预热，避免第一次点「撰写/审核」才开始下 4MB+ 脚本 */
export function warmLakexAssetsOnIdle() {
  if (typeof window === 'undefined') return;
  if (window.Doc?.createOpenEditor || loadPromise) return;
  const run = () => {
    void prefetchLakexAssets();
  };
  const ric = (
    window as Window & {
      requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number;
    }
  ).requestIdleCallback;
  if (typeof ric === 'function') {
    ric(run, { timeout: 2500 });
  } else {
    window.setTimeout(run, 800);
  }
}

export const LAKE_SCHEME = 'text/lake' as const;
