import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Drawer, Progress, Row, Segmented, Space, Table, Tag, Typography, message } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import {
  boardGeoTaskAggApi,
  boardGeoTaskDrillApi,
  boardGeoTaskSummaryApi,
  type BoardGeoTaskAggRow,
  type BoardGeoTaskDim,
  type BoardGeoTaskRow,
  type BoardGeoTaskSummary,
} from '@/api/board';
import type { DemoBoardGrain } from '@/constants/demoData';

const PRIORITY: Record<number, string> = { 1: '低', 2: '中', 3: '高', 4: '紧急' };

type DrillState = {
  dim: BoardGeoTaskDim;
  title: string;
  topicId?: number;
  writerUserId?: number;
  taskType?: string;
};

function TimingTag({ row }: { row: BoardGeoTaskRow }) {
  if (row.timingLabel) {
    const color =
      row.timingTag === 'LATE'
        ? 'red'
        : row.timingTag === 'EARLY'
          ? 'green'
          : row.timingTag === 'ON_TIME'
            ? 'blue'
            : 'default';
    return <Tag color={color}>{row.timingLabel}</Tag>;
  }
  if (row.overdue) {
    return <Tag color='red'>已超时（超时{row.overdueDays ?? 0}天）</Tag>;
  }
  return <span className='text-neutral-400'>-</span>;
}

function DimTableCard({
  title,
  hint,
  loading,
  rows,
  nameTitle,
  onRowClick,
}: {
  title: string;
  hint: string;
  loading: boolean;
  rows: BoardGeoTaskAggRow[];
  nameTitle: string;
  onRowClick: (row: BoardGeoTaskAggRow) => void;
}) {
  const columns: ColumnsType<BoardGeoTaskAggRow> = [
    {
      title: '操作',
      key: 'option',
      width: 72,
      fixed: 'left',
      render: (_, r) => (
        <Button
          type='link'
          size='small'
          className='px-0'
          onClick={() => onRowClick(r)}
        >
          详情
        </Button>
      ),
    },
    { title: nameTitle, dataIndex: 'name', ellipsis: true },
    {
      title: '完成率',
      dataIndex: 'completionRate',
      width: 100,
      render: (v: number) => `${(v ?? 0).toFixed(1)}%`,
    },
    {
      title: '已完成/总数',
      key: 'ratio',
      width: 110,
      render: (_, r) => `${r.doneCount}/${r.totalCount}`,
    },
    { title: '未完成', dataIndex: 'openCount', width: 72 },
    { title: '应到期', dataIndex: 'dueCount', width: 72 },
    {
      title: '逾期',
      dataIndex: 'overdueCount',
      width: 72,
      render: (v: number) => (v > 0 ? <span className='text-red-600'>{v}</span> : v),
    },
  ];

  return (
    <Card
      size='small'
      title={title}
      className='h-full'
    >
      <Table
        size='small'
        rowKey='dimKey'
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        scroll={{ x: 640 }}
        locale={{ emptyText: '所选时间段暂无 GEO 任务' }}
        onRow={(r) => ({
          onClick: () => onRowClick(r),
          className: 'cursor-pointer',
        })}
      />
      <p className='mt-2 mb-0 text-xs text-neutral-400'>{hint}</p>
    </Card>
  );
}

export function GeoTaskOpsBoard({
  grain,
  range,
  filterUserIds,
  topicIds,
  taskTypes,
}: {
  grain: DemoBoardGrain;
  range: [Dayjs, Dayjs];
  filterUserIds?: number[];
  topicIds?: number[];
  taskTypes?: string[];
}) {
  const [summary, setSummary] = useState<BoardGeoTaskSummary | null>(null);
  const [topicRows, setTopicRows] = useState<BoardGeoTaskAggRow[]>([]);
  const [writerRows, setWriterRows] = useState<BoardGeoTaskAggRow[]>([]);
  const [typeRows, setTypeRows] = useState<BoardGeoTaskAggRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [drill, setDrill] = useState<DrillState | null>(null);
  const [taskRows, setTaskRows] = useState<BoardGeoTaskRow[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [drillLoading, setDrillLoading] = useState(false);
  const [subFilter, setSubFilter] = useState('all');

  const filterKey = (filterUserIds ?? []).join(',');
  const topicKey = (topicIds ?? []).join(',');
  const typeKey = (taskTypes ?? []).join('\u0000');

  const queryBase = useMemo(
    () => ({
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      grain,
      filterUserIds: filterKey ? filterKey.split(',').map((id) => Number(id)) : undefined,
      topicIds: topicKey ? topicKey.split(',').map((id) => Number(id)) : undefined,
      taskTypes: typeKey ? typeKey.split('\u0000') : undefined,
    }),
    [range, grain, filterKey, topicKey, typeKey],
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [sum, topics, writers, types] = await Promise.all([
        boardGeoTaskSummaryApi(queryBase),
        boardGeoTaskAggApi({ ...queryBase, dim: 'topic' }),
        boardGeoTaskAggApi({ ...queryBase, dim: 'writer' }),
        boardGeoTaskAggApi({ ...queryBase, dim: 'taskType' }),
      ]);
      setSummary(sum);
      setTopicRows(topics ?? []);
      setWriterRows(writers ?? []);
      setTypeRows(types ?? []);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载 GEO 任务看板失败');
    } finally {
      setLoading(false);
    }
  }, [queryBase]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setDrill(null);
  }, [queryBase.startDate, queryBase.endDate, queryBase.grain, filterKey, topicKey, typeKey]);

  const loadDrill = useCallback(
    async (state: DrillState, page: number, size: number, filter: string) => {
      setDrillLoading(true);
      try {
        const data = await boardGeoTaskDrillApi({
          ...queryBase,
          dim: state.dim,
          topicId: state.topicId,
          writerUserId: state.writerUserId,
          taskType: state.taskType,
          subFilter: filter,
          pageNum: page,
          pageSize: size,
        });
        setTaskRows(data.rows ?? []);
        setTotal(data.total ?? 0);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '加载明细失败');
      } finally {
        setDrillLoading(false);
      }
    },
    [queryBase],
  );

  const openDrill = (dim: BoardGeoTaskDim, row: BoardGeoTaskAggRow) => {
    const label = dim === 'topic' ? '话题' : dim === 'writer' ? '人员' : '任务类型';
    setSubFilter('all');
    setPageNum(1);
    setPageSize(10);
    setTaskRows([]);
    setTotal(0);
    setDrill({
      dim,
      title: `${label} · ${row.name}`,
      topicId: dim === 'topic' ? (row.topicId ?? Number(row.dimKey)) || 0 : undefined,
      writerUserId: dim === 'writer' ? (row.writerUserId ?? Number(row.dimKey)) || 0 : undefined,
      taskType: dim === 'taskType' ? (row.taskType ?? row.dimKey) : undefined,
    });
  };

  useEffect(() => {
    if (!drill) return;
    void loadDrill(drill, pageNum, pageSize, subFilter);
  }, [drill, pageNum, pageSize, subFilter, loadDrill]);

  const taskColumns: ColumnsType<BoardGeoTaskRow> = useMemo(
    () => [
      { title: '任务', dataIndex: 'title', ellipsis: true },
      { title: '类型', dataIndex: 'taskType', width: 120, ellipsis: true },
      { title: '话题', dataIndex: 'topicName', width: 120, ellipsis: true },
      { title: '撰写人', dataIndex: 'writerName', width: 96, render: (v) => v || '-' },
      {
        title: '审核人',
        dataIndex: 'ownerName',
        width: 96,
        render: (v, r) => (r.taskType === '审核文章' ? v || r.assigneeNames || '-' : '-'),
      },
      {
        title: '进度',
        dataIndex: 'progress',
        width: 100,
        render: (v) => (
          <Progress
            percent={v ?? 0}
            size='small'
          />
        ),
      },
      { title: '状态', dataIndex: 'status', width: 88 },
      {
        title: '优先级',
        dataIndex: 'priority',
        width: 72,
        render: (v) => PRIORITY[v as number] ?? '-',
      },
      { title: '计划截止', dataIndex: 'planEndTime', width: 160 },
      { title: '实际完成', dataIndex: 'actualEndTime', width: 160 },
      {
        title: '完成情况',
        key: 'timing',
        width: 180,
        render: (_, r) => <TimingTag row={r} />,
      },
    ],
    [],
  );

  const onTableChange = (p: TablePaginationConfig) => {
    setPageNum(p.current ?? 1);
    setPageSize(p.pageSize ?? 10);
  };

  return (
    <>
      <Card
        size='small'
        loading={loading && !summary}
        title='GEO 任务概况'
      >
        <div className='flex flex-wrap gap-6'>
          <div>
            <div className='text-xs text-neutral-500'>完成率</div>
            <div className='text-2xl font-semibold'>{(summary?.completionRate ?? 0).toFixed(1)}%</div>
            <div className='text-xs text-neutral-400'>
              {summary?.rangeCompleted ?? 0} / {summary?.rangeTotal ?? 0}
            </div>
          </div>
          <div>
            <div className='text-xs text-neutral-500'>区间已完成</div>
            <div className='text-2xl font-semibold'>{summary?.doneCount ?? 0}</div>
          </div>
          <div>
            <div className='text-xs text-neutral-500'>应到期</div>
            <div className='text-2xl font-semibold'>{summary?.dueCount ?? 0}</div>
          </div>
          <div>
            <div className='text-xs text-neutral-500'>逾期</div>
            <div className={`text-2xl font-semibold ${(summary?.overdueCount ?? 0) > 0 ? 'text-red-600' : ''}`}>
              {summary?.overdueCount ?? 0}
            </div>
          </div>
        </div>
        <p className='mt-2 mb-0 text-xs text-neutral-400'>
          完成率分母与「任务看板」一致：计划截止落在所选区间，或区间内实际完成（含待分配类任务）
        </p>
      </Card>

      <Row gutter={[16, 16]}>
        <Col
          xs={24}
          xl={8}
        >
          <DimTableCard
            title='按话题'
            hint='点击行或「详情」查看该话题下任务明细'
            loading={loading}
            rows={topicRows}
            nameTitle='话题'
            onRowClick={(r) => openDrill('topic', r)}
          />
        </Col>
        <Col
          xs={24}
          xl={8}
        >
          <DimTableCard
            title='按人员'
            hint='发布任务归发布人、撰写归撰写人、审核归审核人；点击查看明细'
            loading={loading}
            rows={writerRows}
            nameTitle='人员'
            onRowClick={(r) => openDrill('writer', r)}
          />
        </Col>
        <Col
          xs={24}
          xl={8}
        >
          <DimTableCard
            title='按任务类型'
            hint='点击行或「详情」查看该类型任务明细'
            loading={loading}
            rows={typeRows}
            nameTitle='任务类型'
            onRowClick={(r) => openDrill('taskType', r)}
          />
        </Col>
      </Row>

      <Drawer
        open={!!drill}
        width={920}
        title={drill?.title}
        onClose={() => setDrill(null)}
        destroyOnHidden
      >
        <Space
          direction='vertical'
          size='middle'
          className='w-full'
        >
          <div className='flex flex-wrap items-center justify-between gap-2'>
            <Typography.Text type='secondary'>筛选完成状态</Typography.Text>
            <Segmented
              value={subFilter}
              options={[
                { label: '全部', value: 'all' },
                { label: '已完成', value: 'done' },
                { label: '未完成', value: 'open' },
              ]}
              onChange={(v) => {
                setSubFilter(String(v));
                setPageNum(1);
              }}
            />
          </div>
          <Table
            size='small'
            rowKey='id'
            loading={drillLoading}
            columns={taskColumns}
            dataSource={taskRows}
            scroll={{ x: 1200 }}
            pagination={{
              current: pageNum,
              pageSize,
              total,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
            }}
            onChange={onTableChange}
          />
        </Space>
      </Drawer>
    </>
  );
}
