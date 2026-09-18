import {
  Children,
  cloneElement,
  isValidElement,
  useEffect,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from 'react';
import { calcBoardColumnChartWidth } from '@/components/geo/boardColumnChartProps';

/** 柱图横向滚动容器：按数据量撑开宽度，避免类目多时柱子被挤扁 */
export function BoardColumnScrollArea({
  data,
  children,
}: {
  data: Array<{ axis?: unknown; series?: unknown }> | undefined | null;
  children: ReactNode;
}) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState(0);

  useEffect(() => {
    const el = wrapRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect?.width;
      if (typeof w === 'number' && Number.isFinite(w)) setContainerWidth(w);
    });
    ro.observe(el);
    setContainerWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  const width = Math.max(calcBoardColumnChartWidth(data), containerWidth || 0);

  return (
    <div
      ref={wrapRef}
      className='w-full overflow-x-auto'
    >
      <div style={{ width: width || '100%' }}>
        {Children.map(children, (child) => {
          if (!isValidElement(child)) return child;
          return cloneElement(child as ReactElement<Record<string, unknown>>, {
            width: width || undefined,
            autoFit: !width,
          });
        })}
      </div>
    </div>
  );
}
