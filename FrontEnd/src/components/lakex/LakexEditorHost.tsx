import { useEffect, useRef } from 'react';
import { withBase } from '@/utils/basePath';
import {
  LAKE_SCHEME,
  loadLakexAssets,
  removeLakexAntdCss,
  type LakexEditorInstance,
} from '@/components/lakex/loadLakexAssets';

type Props = {
  value: string;
  readOnly?: boolean;
  onChange?: (lake: string) => void;
  onReady?: () => void;
  onError?: (err: Error) => void;
  uploadImage?: (file: File) => Promise<string>;
};

/**
 * 挂载本仓库镜像的 Lakex 编辑器（非 iframe 语雀站点）。
 * 内容格式固定 text/lake。
 */
export function LakexEditorHost({ value, readOnly, onChange, onReady, onError, uploadImage }: Props) {
  const rootRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<LakexEditorInstance | null>(null);
  const onChangeRef = useRef(onChange);
  const lastSetRef = useRef('');
  const valueRef = useRef(value);

  onChangeRef.current = onChange;
  valueRef.current = value;

  useEffect(() => {
    let disposed = false;
    const disposers: Array<() => void> = [];

    void (async () => {
      if (!rootRef.current) return;
      try {
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
          scrollNode: () => mount,
          typography: { typography: 'classic', paragraphSpacing: 'default' },
          toc: { enable: false },
          codeblock: {
            codemirrorURL: withBase('lakex/CodeMirror.js'),
            supportCustomStyle: true,
          },
          math: { KaTexURL: withBase('lakex/katex.js') },
          image: uploadImage
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
            : undefined,
        });

        if (disposed) {
          editor.destroy?.();
          return;
        }
        editorRef.current = editor;

        const initial = valueRef.current || '';
        lastSetRef.current = initial;
        editor.setDocument?.(LAKE_SCHEME, initial);

        let mute = 3;
        if (typeof editor.on === 'function' && !readOnly) {
          const off = editor.on('contentchange', () => {
            const next = editor.getDocument?.(LAKE_SCHEME, { includeMeta: true }) ?? '';
            if (mute > 0) {
              mute -= 1;
              lastSetRef.current = next;
              return;
            }
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
      removeLakexAntdCss();
      for (const d of disposers) d();
      try {
        editorRef.current?.destroy?.();
      } catch {
        /* ignore */
      }
      editorRef.current = null;
      if (rootRef.current) rootRef.current.innerHTML = '';
    };
    // 仅挂载一次；外部 value 变化由下方 effect 同步
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [readOnly]);

  useEffect(() => {
    const editor = editorRef.current;
    if (!editor?.setDocument) return;
    const current = editor.getDocument?.(LAKE_SCHEME, { includeMeta: true }) ?? '';
    if (value === current) return;
    lastSetRef.current = value || '';
    editor.setDocument(LAKE_SCHEME, value || '');
  }, [value]);

  return (
    <div
      ref={rootRef}
      className='lakex-host h-full w-full overflow-auto bg-white'
    />
  );
}
