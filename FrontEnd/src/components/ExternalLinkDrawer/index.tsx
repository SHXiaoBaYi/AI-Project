import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { Button, Drawer, Tooltip, Typography } from 'antd';
import { blocksIframeEmbed, canOpenExternalInApp, openExternalInNewTab, resolveExternalUrl } from '@/utils/externalUrl';

type ExternalLinkContextValue = {
  openExternal: (url: string, title?: string) => void;
};

const ExternalLinkContext = createContext<ExternalLinkContextValue | null>(null);

export function ExternalLinkProvider({ children }: { children: ReactNode }) {
  const [url, setUrl] = useState<string>();
  const [title, setTitle] = useState('外部链接');

  const openExternal = useCallback((raw: string, nextTitle?: string) => {
    if (!canOpenExternalInApp(raw)) return;
    const resolved = resolveExternalUrl(raw);
    if (!resolved) return;
    // 禁止 iframe 嵌入的站点直接新标签打开，避免空白抽屉
    if (blocksIframeEmbed(resolved)) {
      openExternalInNewTab(resolved);
      return;
    }
    setTitle(nextTitle || '外部链接');
    setUrl(resolved);
  }, []);

  const value = useMemo(() => ({ openExternal }), [openExternal]);

  return (
    <ExternalLinkContext.Provider value={value}>
      {children}
      <Drawer
        title={title}
        width='70%'
        open={!!url}
        onClose={() => setUrl(undefined)}
        destroyOnHidden
        zIndex={2100}
        styles={{ body: { height: 'calc(100vh - 55px)', padding: 0, overflow: 'hidden' } }}
        extra={
          <Button
            type='link'
            disabled={!url}
            onClick={() => {
              if (url) openExternalInNewTab(url);
            }}
          >
            新窗口打开
          </Button>
        }
      >
        {url ? (
          <div className='flex h-full flex-col'>
            <p className='m-0 shrink-0 border-b border-black/6 px-4 py-2 text-xs text-black/45'>
              若下方空白，请点右上角「新窗口打开」。
            </p>
            <iframe
              title={title}
              src={url}
              className='min-h-0 w-full flex-1 border-0'
              sandbox='allow-scripts allow-same-origin allow-popups allow-forms allow-popups-to-escape-sandbox'
            />
          </div>
        ) : null}
      </Drawer>
    </ExternalLinkContext.Provider>
  );
}

export function useExternalLink() {
  const ctx = useContext(ExternalLinkContext);
  if (!ctx) {
    throw new Error('useExternalLink 需在 ExternalLinkProvider 内使用');
  }
  return ctx;
}

export function useExternalLinkOptional() {
  return useContext(ExternalLinkContext);
}

/** 表格内展示外链：省略号 + Tooltip；可嵌套则抽屉预览，禁止嵌套则新标签打开 */
export function ExternalLinkText({
  href,
  children,
  drawerTitle,
  className,
  ellipsis = true,
  emptyText = '-',
}: {
  href?: string | null;
  children?: ReactNode;
  drawerTitle?: string;
  className?: string;
  ellipsis?: boolean;
  emptyText?: ReactNode;
}) {
  const ctx = useExternalLinkOptional();
  if (!href?.trim()) return <>{emptyText}</>;

  const display = (typeof children === 'string' || typeof children === 'number' ? String(children) : null) ?? href;
  const tip = href;
  const boxClass = ['min-w-0 w-full overflow-hidden', className].filter(Boolean).join(' ');

  // 有值但格式不对：当错误链接，仅文本展示、不可点
  if (!canOpenExternalInApp(href)) {
    const text = (
      <span className={ellipsis ? 'inline-block max-w-full truncate text-neutral-800' : undefined}>{display}</span>
    );
    if (!ellipsis) return <span className={className}>{display}</span>;
    return (
      <div className={boxClass}>
        <Tooltip title={tip}>{text}</Tooltip>
      </div>
    );
  }

  const resolved = resolveExternalUrl(href);
  const link = (
    <Typography.Link
      className={ellipsis ? '!inline-block max-w-full truncate' : undefined}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        if (!resolved) return;
        if (blocksIframeEmbed(resolved) || !ctx) {
          openExternalInNewTab(resolved);
          return;
        }
        ctx.openExternal(resolved, drawerTitle);
      }}
    >
      {display}
    </Typography.Link>
  );

  if (!ellipsis) return <span className={className}>{link}</span>;

  return (
    <div className={boxClass}>
      <Tooltip title={tip}>{link}</Tooltip>
    </div>
  );
}

export default ExternalLinkProvider;
