import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { withBase } from '@/utils/basePath';
import {
  ensureLakexAntdCss,
  LAKE_SCHEME,
  loadLakexAssets,
  removeLakexAntdCss,
  type LakexEditorInstance,
} from '@/components/lakex/loadLakexAssets';
import './lakex-host.css';

type Props = {
  value: string;
  readOnly?: boolean;
  onChange?: (lake: string) => void;
  onReady?: () => void;
  onError?: (err: Error) => void;
  uploadImage?: (file: File) => Promise<string>;
};

export type LakexEditorHostHandle = {
  /** 编辑器未就绪时返回 null，避免把空串当成「用户清空了正文」 */
  getLake: () => string | null;
  /** 外部流式写入（打字机）；不触发 onChange 回环 */
  setLake: (lake: string) => void;
};

/**
 * 挂载本仓库镜像的 Lakex 编辑器（非 iframe 语雀站点）。
 * 内容格式固定 text/lake。
 * 外部换稿请靠 key 重挂载，不要用 value effect 回写，否则会冲掉正在输入的内容。
 */
export const LakexEditorHost = forwardRef<LakexEditorHostHandle, Props>(function LakexEditorHost(
  { value, readOnly, onChange, onReady, onError, uploadImage },
  ref,
) {
  const rootRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<LakexEditorInstance | null>(null);
  const onChangeRef = useRef(onChange);
  const lastSetRef = useRef('');
  const valueRef = useRef(value);

  onChangeRef.current = onChange;
  valueRef.current = value;

  useImperativeHandle(ref, () => ({
    getLake: () => {
      const editor = editorRef.current;
      if (!editor?.getDocument) return null;
      return editor.getDocument(LAKE_SCHEME, { includeMeta: true }) ?? '';
    },
    setLake: (lake: string) => {
      const editor = editorRef.current;
      if (!editor?.setDocument) return;
      const next = lake ?? '';
      lastSetRef.current = next;
      editor.setDocument(LAKE_SCHEME, next);
    },
  }));

  useEffect(() => {
    let disposed = false;
    const disposers: Array<() => void> = [];

    void (async () => {
      if (!rootRef.current) return;
      try {
        // 工具栏下拉 / 排版菜单依赖 Lakex antd v4；与后台 antd6 冲突，仅挂载期间注入
        await ensureLakexAntdCss();
        const Doc = await loadLakexAssets();
        if (disposed || !rootRef.current) return;

        rootRef.current.innerHTML = '';
        const mount = document.createElement('div');
        mount.className = 'lakex-editor-root h-full min-h-[480px] w-full';
        rootRef.current.appendChild(mount);

        const create = readOnly ? Doc.createOpenViewer : Doc.createOpenEditor;
        const editor = create(mount, {
          defaultFontsize: 15,
          darkMode: false,
          // 滚动交给外层 .lakex-host，避免 overflow 裁剪工具栏下拉
          scrollNode: () => rootRef.current || mount,
          typography: { typography: 'classic', paragraphSpacing: 'default' },
          toc: { enable: false },
          codeblock: {
            codemirrorURL: withBase('lakex/CodeMirror.js'),
            supportCustomStyle: true,
          },
          math: { KaTexURL: withBase('lakex/katex.js') },
          // Lake 默认 capturePatterns 会抓取几乎所有外链图并重传；素材库 URL 必须用原址，否则变 data:image 并「上传失败」
          image: {
            capturePatterns: [/^data:image\//i, /^blob:/i],
            excludeCapturePatterns: [/\/uploads\//i, /\/api\/uploads\//i, /\/shxby\/api\/uploads\//i],
            useOriginSrc: (src?: string) => {
              const s = src || '';
              if (!s || s.startsWith('data:') || s.startsWith('blob:')) return false;
              return /\/uploads\//i.test(s) || /^https?:\/\//i.test(s) || s.startsWith('/');
            },
            ...(uploadImage
              ? {
                  async createUploadPromise(request: { data?: Blob | File; type?: string }) {
                    const blob = request.data;
                    if (!blob) throw new Error('无上传文件');
                    const file =
                      blob instanceof File
                        ? blob
                        : new File([blob], `image.${(request.type || 'png').replace('image/', '')}`, {
                            type: request.type || 'image/png',
                          });
                    const url = await uploadImage(file);
                    return { url, size: file.size, filename: file.name };
                  },
                }
              : {}),
          },
        });

        if (disposed) {
          editor.destroy?.();
          return;
        }
        editorRef.current = editor;

        const initial = valueRef.current || '';
        lastSetRef.current = initial;
        editor.setDocument?.(LAKE_SCHEME, initial);

        if (typeof editor.on === 'function' && !readOnly) {
          const off = editor.on('contentchange', () => {
            const next = editor.getDocument?.(LAKE_SCHEME, { includeMeta: true }) ?? '';
            if (next === lastSetRef.current) return;
            lastSetRef.current = next;
            onChangeRef.current?.(next);
          });
          if (typeof off === 'function') disposers.push(off);
        }

        onReady?.();
      } catch (e) {
        onError?.(e instanceof Error ? e : new Error(String(e)));
      }
    })();

    return () => {
      disposed = true;
      for (const d of disposers) d();
      try {
        editorRef.current?.destroy?.();
      } catch {
        /* ignore */
      }
      editorRef.current = null;
      if (rootRef.current) rootRef.current.innerHTML = '';
      // 抽屉关闭后卸掉 v4 antd，避免盖住后台表格
      removeLakexAntdCss();
    };
    // 仅挂载一次；换稿靠父组件 key 重挂载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readOnly]);

  return (
    <div
      ref={rootRef}
      className='lakex-host h-full w-full bg-white'
    />
  );
});
