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
import {
  BoardChartLegend,
  boardColorScale,
  filterBoardSeries,
  uniqueBoardSeries,
  useHiddenBoardSeries,
} from '@/components/geo/BoardChartLegend';
import { calcBoardColumnChartWidth } from '@/components/geo/boardColumnChartProps';

/** 左侧固定宽度：盖住 Y 轴刻度，横向滚动时不跟着走 */
const Y_AXIS_WIDTH = 72;

/** 柱图横向滚动容器：按数据量撑开宽度；Y 轴固定在左侧 */
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

  const series = uniqueBoardSeries(data);
  const { hidden, toggle } = useHiddenBoardSeries(series);
  const chartData = filterBoardSeries(data, hidden);
  const width = Math.max(calcBoardColumnChartWidth(chartData), containerWidth || 0);
  const chartWidth = width || undefined;
  const shared = {
    width: chartWidth,
    autoFit: !chartWidth,
    /** 绘图区从固定槽右侧开始，滚动层与钉住层对齐 */
    marginLeft: 0,
    paddingLeft: Y_AXIS_WIDTH,
    /** 图例改到画布外，避免被左侧 Y 轴挡住 */
    legend: false as const,
  };

  const paint = (child: ReactNode, extra?: Record<string, unknown>) => {
    if (!isValidElement(child)) return child;
    const props = child.props as Record<string, unknown>;
    const prevScale = props.scale && typeof props.scale === 'object' ? (props.scale as Record<string, unknown>) : {};
    const source = Array.isArray(props.data) ? (props.data as Array<{ series?: unknown }>) : data;
    return cloneElement(child as ReactElement<Record<string, unknown>>, {
      ...shared,
      ...extra,
      data: filterBoardSeries(source, hidden),
      scale: {
        ...prevScale,
        color: boardColorScale(series),
      },
    });
  };

  return (
    <div className='w-full'>
      <BoardChartLegend
        series={series}
        hidden={hidden}
        onToggle={toggle}
      />
      <div
        ref={wrapRef}
        className='relative w-full'
      >
        <div className='w-full overflow-x-auto'>
          <div style={{ width: width || '100%' }}>{Children.map(children, (child) => paint(child))}</div>
        </div>
        <div
          aria-hidden
          className='pointer-events-none absolute top-0 bottom-0 left-0 z-10 overflow-hidden bg-white shadow-[6px_0_8px_-6px_rgba(0,0,0,0.25)]'
          style={{ width: Y_AXIS_WIDTH }}
        >
          <div style={{ width: width || '100%' }}>
            {Children.map(children, (child) =>
              paint(child, {
                onReady: undefined,
                tooltip: false,
              }),
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
