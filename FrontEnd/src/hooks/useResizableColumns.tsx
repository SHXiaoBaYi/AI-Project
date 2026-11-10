import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type ReactNode,
} from 'react';

type ResizableThProps = {
  width?: number;
  onResize?: (width: number) => void;
  onResizeReset?: () => void;
  children?: ReactNode;
  [key: string]: unknown;
};

/** 表头单元格：鼠标移到右边线出现拖拽条，拖动调整列宽，双击恢复默认 */
export function ResizableTh({ width, onResize, onResizeReset, children, ...rest }: ResizableThProps) {
  const startX = useRef(0);
  const startW = useRef(0);
  const [dragging, setDragging] = useState(false);

  const onMouseDown = (e: ReactMouseEvent) => {
    if (!onResize || width == null) return;
    e.preventDefault();
    e.stopPropagation();
    startX.current = e.clientX;
    startW.current = width;
    setDragging(true);
    const prevCursor = document.body.style.cursor;
    const prevSelect = document.body.style.userSelect;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    const onMove = (ev: MouseEvent) => onResize(startW.current + (ev.clientX - startX.current));
    const onUp = () => {
      setDragging(false);
      document.body.style.cursor = prevCursor;
      document.body.style.userSelect = prevSelect;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  const resizable = !!onResize && width != null;
  return (
    <th
      {...rest}
      style={{ ...(rest.style as object), width, position: resizable ? 'relative' : (rest.style as any)?.position }}
    >
      {children}
      {resizable ? (
        <span
          title='拖动调整列宽，双击恢复默认'
          className='group/resize absolute top-0 -right-1 z-10 flex h-full w-2 cursor-col-resize touch-none justify-center select-none'
          onMouseDown={onMouseDown}
          onClick={(e) => e.stopPropagation()}
          onDoubleClick={(e) => {
            e.stopPropagation();
            onResizeReset?.();
          }}
        >
          <span
            className={`h-full w-0.5 transition-colors ${dragging ? 'bg-blue-500' : 'bg-transparent group-hover/resize:bg-blue-500'}`}
          />
        </span>
      ) : null}
    </th>
  );
}

type ResizableColumn = {
  key?: unknown;
  dataIndex?: unknown;
  width?: unknown;
  hideInTable?: boolean;
  onHeaderCell?: unknown;
};

function columnKey(col: ResizableColumn): string | undefined {
  if (col.key != null && col.key !== '') return String(col.key);
  if (Array.isArray(col.dataIndex)) return col.dataIndex.join('.');
  if (col.dataIndex != null && col.dataIndex !== '') return String(col.dataIndex);
  return undefined;
}

function loadWidths(storageKey: string): Record<string, number> {
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey) || '{}') as Record<string, number>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * 表格列宽可拖拽，并按 storageKey 保存到 localStorage。
 * 仅对有数字 width 且能取到 key/dataIndex 的列生效；返回的 components 需传给 Table/ProTable。
 */
export function useResizableColumns<C extends ResizableColumn>(storageKey: string, columns: C[], minWidth = 60) {
  const [widths, setWidths] = useState<Record<string, number>>(() => loadWidths(storageKey));

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(widths));
    } catch {
      /* ignore */
    }
  }, [storageKey, widths]);

  const setWidth = useCallback(
    (key: string, width: number) => {
      setWidths((prev) => ({ ...prev, [key]: Math.max(minWidth, Math.round(width)) }));
    },
    [minWidth],
  );

  const resetWidth = useCallback((key: string) => {
    setWidths((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }, []);

  const resizedColumns = useMemo(
    () =>
      columns.map((col) => {
        const key = columnKey(col);
        if (!key || col.hideInTable || typeof col.width !== 'number') return col;
        const width = widths[key] ?? col.width;
        return {
          ...col,
          width,
          onHeaderCell: () => ({
            width,
            onResize: (next: number) => setWidth(key, next),
            onResizeReset: () => resetWidth(key),
          }),
        };
      }),
    [columns, widths, setWidth, resetWidth],
  );

  /** 当前可见列宽合计，可用作 scroll.x */
  const totalWidth = useMemo(
    () =>
      resizedColumns.reduce(
        (sum, col) => (col.hideInTable || typeof col.width !== 'number' ? sum : sum + (col.width as number)),
        0,
      ),
    [resizedColumns],
  );

  const components = useMemo(() => ({ header: { cell: ResizableTh } }), []);

  return { columns: resizedColumns, components, totalWidth };
}
