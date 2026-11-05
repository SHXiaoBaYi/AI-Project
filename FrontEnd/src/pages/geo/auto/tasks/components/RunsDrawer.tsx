import { useEffect, useRef, useState } from 'react';
import { Badge, Drawer, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getGeoAutoRunListApi } from '@/api/geoAuto';
import type { GeoAutoRun, GeoAutoTask } from '@/types/geoAuto';
import { GEO_AUTO_RUN_STATUS_MAP } from '@/constants/geoAuto';

interface RunsDrawerProps {
  task: GeoAutoTask | null;
  onClose: () => void;
}

/** 运行记录抽屉：批次进度轮询（RUNNING 时每 5s 刷新） */
export default function RunsDrawer({ task, onClose }: RunsDrawerProps) {
  const [rows, setRows] = useState<GeoAutoRun[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [loading, setLoading] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pageNumRef = useRef(1);

  const load = async (taskId: number, page: number) => {
    setLoading(true);
    try {
      const res = await getGeoAutoRunListApi({ taskId, pageNum: page, pageSize: 10 });
      setRows(res.rows);
      setTotal(res.total);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!task) {
      setRows([]);
      setTotal(0);
      setPageNum(1);
      pageNumRef.current = 1;
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      return;
    }
    void load(task.id, 1);
    setPageNum(1);
    pageNumRef.current = 1;
    timerRef.current = setInterval(() => {
      void load(task.id, pageNumRef.current);
    }, 5000);
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [task?.id]);

  const statusMeta = (status: string) => GEO_AUTO_RUN_STATUS_MAP[status] || { label: status, color: 'default' };

  const columns: ColumnsType<GeoAutoRun> = [
    { title: '批次', dataIndex: 'id', width: 70 },
    { title: '监测日期', dataIndex: 'runDate', width: 105 },
    {
      title: '触发',
      dataIndex: 'triggerType',
      width: 80,
      render: (v: string) => (v === 'MANUAL' ? <Tag color='blue'>手动</Tag> : <Tag>定时</Tag>),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string, r) => {
        const meta = statusMeta(v);
        return (
          <span>
            {v === 'RUNNING' ? <Badge status='processing' /> : <Badge status='default' />}
            <span className='ml-1'>{meta.label}</span>
            {v === 'RUNNING' && (
              <span className='ml-1 text-neutral-400'>
                （{r.doneCount}/{r.totalCount}）
              </span>
            )}
          </span>
        );
      },
    },
    {
      title: '成功/失败',
      width: 100,
      render: (_, r) => (
        <span>
          <span className='text-green-600'>{r.successCount}</span>
          <span className='mx-1 text-neutral-400'>/</span>
          <span className={r.failedCount > 0 ? 'text-red-500' : 'text-neutral-400'}>{r.failedCount}</span>
        </span>
      ),
    },
    { title: '开始时间', dataIndex: 'startTime', width: 150 },
    { title: '结束时间', dataIndex: 'endTime', width: 150, render: (v: string) => v || '-' },
    { title: '错误', dataIndex: 'errorMsg', ellipsis: true, render: (v: string) => v || '-' },
  ];

  return (
    <Drawer
      title={`运行记录：${task?.taskName || ''}`}
      open={!!task}
      onClose={onClose}
      width={960}
    >
      <Table<GeoAutoRun>
        rowKey='id'
        size='small'
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: pageNum,
          pageSize: 10,
          total,
          showSizeChanger: false,
          onChange: (page) => {
            setPageNum(page);
            pageNumRef.current = page;
            if (task) {
              void load(task.id, page);
            }
          },
        }}
      />
    </Drawer>
  );
}
