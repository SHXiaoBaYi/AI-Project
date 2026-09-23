import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type MouseEvent as ReactMouseEvent,
  type ReactNode,
} from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { App, Button, Space, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import { getHrJobDetailsApi, type HrJobDetailRow } from '@/api/hr';

const DEFAULT_HIDE = new Set(['deptName', 'headcount']);
const WIDTH_STORAGE_KEY = 'hr-board-job-detail-widths';

function uniqueOptions(rows: HrJobDetailRow[], pick: (r: HrJobDetailRow) => string | undefined) {
  const set = new Set<string>();
  rows.forEach((r) => {
    const v = pick(r)?.trim();
    if (v) set.add(v);
  });
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-CN')).map((text) => ({ text, value: text }));
}

function loadStoredWidths(): Record<string, number> {
  try {
    const raw = localStorage.getItem(WIDTH_STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, number>;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

/** 可拖拽调整列宽的表头单元格 */
function ResizableTh({
  width,
  onResize,
  children,
  ...rest
}: {
  width?: number;
  onResize?: (width: number) => void;
  children?: ReactNode;
  [key: string]: unknown;
}) {
  const startX = useRef(0);
  const startW = useRef(0);
  const dragging = useRef(false);

  const onMouseDown = (e: ReactMouseEvent) => {
    if (!onResize || width == null) return;
    e.preventDefault();
    e.stopPropagation();
    dragging.current = true;
    startX.current = e.clientX;
    startW.current = width;
    const onMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      const next = Math.max(56, startW.current + (ev.clientX - startX.current));
      onResize(next);
    };
    const onUp = () => {
      dragging.current = false;
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  return (
    <th
      {...rest}
      style={{ ...(rest.style as object), width, position: 'relative' }}
    >
      {children}
      {onResize && width != null ? (
        <span
          className='absolute top-0 right-0 z-10 h-full w-1.5 cursor-col-resize touch-none select-none hover:bg-blue-500'
          onMouseDown={onMouseDown}
          onClick={(e) => e.stopPropagation()}
        />
      ) : null}
    </th>
  );
}

export default function JobDetailPanel() {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<HrJobDetailRow[]>([]);
  const [widths, setWidths] = useState<Record<string, number>>(loadStoredWidths);

  const setColumnWidth = useCallback((key: string, width: number) => {
    setWidths((prev) => {
      const next = { ...prev, [key]: width };
      try {
        localStorage.setItem(WIDTH_STORAGE_KEY, JSON.stringify(next));
      } catch {
        /* ignore */
      }
      return next;
    });
  }, []);

  const load = async () => {
    setLoading(true);
    try {
      const data = await getHrJobDetailsApi({});
      setRows(data ?? []);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载岗位明细失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const columns: ProColumns<HrJobDetailRow>[] = useMemo(() => {
    const jobFilters = uniqueOptions(rows, (r) => r.jobName);
    const statusFilters = uniqueOptions(rows, (r) => r.statusLabel || r.status);
    const locationFilters = uniqueOptions(rows, (r) => r.location);
    const deptFilters = uniqueOptions(rows, (r) => r.deptName);
    const ownerFilters = uniqueOptions(rows, (r) => r.ownerName);
    const priorityFilters = uniqueOptions(rows, (r) => r.priorityLabel);
    const targetFilters = uniqueOptions(rows, (r) => r.targetText);

    const w = (key: string, fallback: number) => widths[key] ?? fallback;

    const base: ProColumns<HrJobDetailRow>[] = [
      {
        title: '岗位名称',
        dataIndex: 'jobName',
        key: 'jobName',
        fixed: 'left',
        ellipsis: true,
        width: w('jobName', 160),
        filters: jobFilters,
        onFilter: (value, record) => (record.jobName || '') === value,
      },
      {
        title: '岗位状态',
        dataIndex: 'statusLabel',
        key: 'statusLabel',
        width: w('statusLabel', 100),
        filters: statusFilters,
        onFilter: (value, record) => (record.statusLabel || record.status || '') === value,
        render: (_, r) => r.statusLabel || r.status || '—',
      },
      {
        title: '工作地',
        dataIndex: 'location',
        key: 'location',
        width: w('location', 90),
        filters: locationFilters,
        onFilter: (value, record) => (record.location || '') === value,
      },
      {
        title: '部门',
        dataIndex: 'deptName',
        key: 'deptName',
        width: w('deptName', 120),
        ellipsis: true,
        filters: deptFilters,
        onFilter: (value, record) => (record.deptName || '') === value,
        render: (_, r) => r.deptName || '—',
      },
      {
        title: '负责人',
        dataIndex: 'ownerName',
        key: 'ownerName',
        width: w('ownerName', 120),
        ellipsis: true,
        filters: ownerFilters,
        onFilter: (value, record) => (record.ownerName || '') === value,
        render: (_, r) => r.ownerName || '—',
      },
      {
        title: '优先级',
        dataIndex: 'priorityLabel',
        key: 'priorityLabel',
        width: w('priorityLabel', 90),
        filters: priorityFilters,
        onFilter: (value, record) => (record.priorityLabel || '') === value,
        render: (_, r) => r.priorityLabel || '—',
      },
      {
        title: '目标到岗',
        dataIndex: 'targetText',
        key: 'targetText',
        width: w('targetText', 110),
        ellipsis: true,
        filters: targetFilters,
        onFilter: (value, record) => (record.targetText || '') === value,
        render: (_, r) => r.targetText || '—',
      },
      {
        title: '人数',
        dataIndex: 'headcount',
        key: 'headcount',
        width: w('headcount', 70),
        align: 'right',
        render: (_, r) => r.headcount ?? '—',
      },
      {
        title: '接收日',
        dataIndex: 'receivedDate',
        key: 'receivedDate',
        width: w('receivedDate', 110),
        render: (_, r) => r.receivedDate || '—',
      },
      {
        title: '招聘天数',
        dataIndex: 'recruitingDays',
        key: 'recruitingDays',
        width: w('recruitingDays', 90),
        align: 'right',
        render: (_, r) => (r.recruitingDays == null ? '—' : r.recruitingDays),
      },
      {
        title: '日进展',
        dataIndex: 'dayProgress',
        key: 'dayProgress',
        ellipsis: true,
        width: w('dayProgress', 260),
        render: (_, r) => (
          <Typography.Text
            className={r.dayProgress && r.dayProgress !== '无' ? 'text-neutral-800' : 'text-black/35'}
            ellipsis={{ tooltip: r.dayProgress }}
          >
            {r.dayProgress || '无'}
          </Typography.Text>
        ),
      },
      {
        title: '周进展',
        dataIndex: 'weekProgress',
        key: 'weekProgress',
        ellipsis: true,
        width: w('weekProgress', 260),
        render: (_, r) => (
          <Typography.Text
            className={r.weekProgress && r.weekProgress !== '无' ? 'text-neutral-800' : 'text-black/35'}
            ellipsis={{ tooltip: r.weekProgress }}
          >
            {r.weekProgress || '无'}
          </Typography.Text>
        ),
      },
    ];

    return base.map((col) => {
      const key = String(col.key ?? col.dataIndex);
      const width = typeof col.width === 'number' ? col.width : undefined;
      return {
        ...col,
        onHeaderCell: () => ({
          width,
          onResize: (next: number) => setColumnWidth(key, next),
        }),
      };
    });
  }, [rows, setColumnWidth, widths]);

  const components = useMemo(
    () => ({
      header: {
        cell: ResizableTh,
      },
    }),
    [],
  );

  return (
    <div className='flex flex-col gap-3'>
      <div className='flex items-center justify-between gap-3'>
        <Typography.Text type='secondary'>
          实时岗位明细 · 共 {rows.length} 个岗位 · 日/周进展按阶段事件统计；入职显示姓名+日期 · 表头右侧可拖动调列宽
        </Typography.Text>
        <Space>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => void load()}
          >
            刷新
          </Button>
        </Space>
      </div>
      <BaseProTable<HrJobDetailRow>
        rowKey='id'
        loading={loading}
        search={false}
        options={{
          density: true,
          reload: false,
          setting: true,
        }}
        columnsState={{
          persistenceKey: 'hr-board-job-detail-columns',
          persistenceType: 'localStorage',
          defaultValue: Object.fromEntries([...DEFAULT_HIDE].map((key) => [key, { show: false }])),
        }}
        pagination={false}
        dataSource={rows}
        columns={columns}
        components={components}
        scroll={{ x: 'max-content', y: 'calc(100vh - 260px)' }}
        cardProps={{ bodyStyle: { padding: 0 } }}
      />
    </div>
  );
}
