import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Breadcrumb,
  Button,
  Card,
  Col,
  Drawer,
  Progress,
  Row,
  Segmented,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { Dayjs } from 'dayjs';
import {
  boardTaskOpsDrillApi,
  boardTaskOpsPersonRateApi,
  boardTaskOpsSummaryApi,
  type BoardTaskOpsMetric,
  type BoardTaskOpsPersonRate,
  type BoardTaskOpsRow,
  type BoardTaskOpsSummary,
} from '@/api/board';
import type { DemoBoardGrain } from '@/constants/demoData';

const PRIORITY: Record<number, string> = { 1: '低', 2: '中', 3: '高', 4: '紧急' };

type DrillState = {
  metric: BoardTaskOpsMetric;
  title: string;
  /** person=员工完成率；task=任务明细 */
  level: 'person' | 'task';
  personUserId?: number;
  personName?: string;
  showSub?: 'timing' | 'completion';
};

function TimingTag({ row }: { row: BoardTaskOpsRow }) {
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

function MetricClick({
  label,
  value,
  danger,
  onClick,
}: {
  label: string;
  value: number;
  danger?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type='button'
      className={`flex min-w-[88px] flex-col items-start rounded-md border border-transparent px-2 py-1 text-left transition hover:border-neutral-200 hover:bg-neutral-50 ${
        danger ? 'text-red-600' : 'text-neutral-800'
      }`}
      onClick={onClick}
    >
      <span className='text-xs text-neutral-500'>{label}</span>
      <span className='text-2xl leading-tight font-semibold'>{value}</span>
    </button>
  );
}

function RateCard({
  title,
  rate,
  numerator,
  denominator,
  formula,
  rangeText,
  onDrill,
}: {
  title: string;
  rate: number;
  numerator: number;
  denominator: number;
  formula: string;
  rangeText: string;
  onDrill: () => void;
}) {
  return (
    <Card
      size='small'
      title={title}
      className='h-full cursor-pointer transition hover:shadow-sm'
      onClick={onDrill}
      extra={<span className='text-xs text-neutral-400'>点击按员工下钻</span>}
    >
      <div className='flex flex-col gap-2'>
        <div className='text-3xl font-semibold text-neutral-800'>{rate.toFixed(1)}%</div>
        <div className='text-sm text-neutral-600'>
          {numerator} / {denominator}
          <span className='ml-2 text-xs text-neutral-400'>{formula}</span>
        </div>
        <div className='text-xs text-neutral-400'>{rangeText}</div>
      </div>
    </Card>
  );
}

export function TaskOpsBoard({
  grain,
  range,
  asOfDate,
}: {
  grain: DemoBoardGrain;
  range: [Dayjs, Dayjs];
  asOfDate?: string;
}) {
  const [summary, setSummary] = useState<BoardTaskOpsSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [drill, setDrill] = useState<DrillState | null>(null);
  const [taskRows, setTaskRows] = useState<BoardTaskOpsRow[]>([]);
  const [personRows, setPersonRows] = useState<BoardTaskOpsPersonRate[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [drillLoading, setDrillLoading] = useState(false);
  const [subFilter, setSubFilter] = useState('all');

  const queryBase = useMemo(
    () => ({
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      asOfDate,
    }),
    [range, asOfDate],
  );

  const loadSummary = useCallback(async () => {
    setLoading(true);
    try {
      const data = await boardTaskOpsSummaryApi(queryBase);
      setSummary(data);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载任务看板失败');
    } finally {
      setLoading(false);
    }
  }, [queryBase]);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  const loadDrill = useCallback(
    async (state: DrillState, page: number, size: number, filter: string) => {
      setDrillLoading(true);
      try {
        if (state.level === 'person') {
          const data = await boardTaskOpsPersonRateApi({
            ...queryBase,
            metric: state.metric,
            pageNum: page,
            pageSize: size,
          });
          setPersonRows(data.rows ?? []);
          setTaskRows([]);
          setTotal(data.total ?? 0);
        } else {
          const data = await boardTaskOpsDrillApi({
            ...queryBase,
            metric: state.metric,
            subFilter: filter,
            personUserId: state.personUserId,
            pageNum: page,
            pageSize: size,
          });
          setTaskRows(data.rows ?? []);
          setPersonRows([]);
          setTotal(data.total ?? 0);
        }
      } catch (e) {
        message.error(e instanceof Error ? e.message : '加载明细失败');
      } finally {
        setDrillLoading(false);
      }
    },
    [queryBase],
  );

  const openDrill = (state: DrillState) => {
    setSubFilter('all');
    setPageNum(1);
    setPageSize(10);
    setTaskRows([]);
    setPersonRows([]);
    setTotal(0);
    setDrill(state);
  };

  useEffect(() => {
    if (!drill) return;
    void loadDrill(drill, pageNum, pageSize, subFilter);
  }, [drill, pageNum, pageSize, subFilter, loadDrill]);

  const openPersonTasks = (person: BoardTaskOpsPersonRate) => {
    if (!drill) return;
    setSubFilter('all');
    setPageNum(1);
    setDrill({
      ...drill,
      level: 'task',
      personUserId: person.userId,
      personName: person.userName,
      title: `${drill.metric === 'onTimeRate' ? '按时完成率' : '任务完成率'} · ${person.userName}`,
      showSub: drill.metric === 'onTimeRate' ? 'timing' : 'completion',
    });
  };

  const backToPerson = () => {
    if (!drill) return;
    setSubFilter('all');
    setPageNum(1);
    setDrill({
      ...drill,
      level: 'person',
      personUserId: undefined,
      personName: undefined,
      title: drill.metric === 'onTimeRate' ? '按时完成率 · 员工' : '任务完成率 · 员工',
      showSub: undefined,
    });
  };

  const taskColumns: ColumnsType<BoardTaskOpsRow> = [
    { title: '任务', dataIndex: 'title', ellipsis: true },
    {
      title: '责任人',
      dataIndex: 'assigneeNames',
      width: 120,
      render: (v, r) => v || r.ownerName || '-',
    },
    {
      title: '进度',
      dataIndex: 'progress',
      width: 110,
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
  ];

  const personColumns: ColumnsType<BoardTaskOpsPersonRate> = (() => {
    const isOnTime = drill?.metric === 'onTimeRate';
    return [
      {
        title: '员工',
        dataIndex: 'userName',
        render: (v, r) => (
          <Button
            type='link'
            className='!px-0'
            onClick={(e) => {
              e.stopPropagation();
              openPersonTasks(r);
            }}
          >
            {v || `用户${r.userId}`}
          </Button>
        ),
      },
      {
        title: '完成率',
        dataIndex: 'rate',
        width: 100,
        render: (v: number) => <span className='font-semibold'>{Number(v ?? 0).toFixed(1)}%</span>,
      },
      {
        title: isOnTime ? '按时/已完成' : '完成/总数',
        key: 'frac',
        width: 120,
        render: (_, r) => `${r.numerator} / ${r.denominator}`,
      },
      ...(isOnTime
        ? [
            { title: '正常', dataIndex: 'onTimeCount', width: 72 },
            { title: '提前', dataIndex: 'earlyCount', width: 72 },
            { title: '超时', dataIndex: 'lateCount', width: 72 },
          ]
        : [{ title: '未完成', dataIndex: 'openCount', width: 88 }]),
      {
        title: '操作',
        key: 'act',
        width: 88,
        render: (_, r) => (
          <Button
            type='link'
            className='!px-0'
            onClick={(e) => {
              e.stopPropagation();
              openPersonTasks(r);
            }}
          >
            看明细
          </Button>
        ),
      },
    ] as ColumnsType<BoardTaskOpsPersonRate>;
  })();

  const rangeText =
    summary?.startDate && summary?.endDate
      ? `${summary.startDate} ~ ${summary.endDate}`
      : `${queryBase.startDate} ~ ${queryBase.endDate}`;

  const pagination: TablePaginationConfig = {
    current: pageNum,
    pageSize,
    total,
    showSizeChanger: true,
    onChange: (p, s) => {
      setPageNum(p);
      setPageSize(s);
    },
  };

  const isRatePerson = drill?.level === 'person';
  const isRateTask = drill?.level === 'task' && (drill.metric === 'onTimeRate' || drill.metric === 'completionRate');

  return (
    <div className='flex flex-col gap-3'>
      <div className='text-xs text-neutral-400'>
        ①②按「今日/本周」（锚定 {summary?.asOfDate ?? asOfDate ?? '今天'}）；③④受页顶日期筛选（{grain}
        ）；完成率先按员工再下钻任务
      </div>
      <Row gutter={[12, 12]}>
        <Col
          xs={24}
          lg={12}
          xl={6}
        >
          <Card
            size='small'
            title='到期 / 超时'
            loading={loading}
            className='h-full'
          >
            <Space
              wrap
              size={[8, 8]}
            >
              <MetricClick
                label='今日到期'
                value={summary?.todayDue ?? 0}
                onClick={() => openDrill({ metric: 'todayDue', title: '今日到期任务', level: 'task' })}
              />
              <MetricClick
                label='今日超时'
                value={summary?.todayOverdue ?? 0}
                danger
                onClick={() => openDrill({ metric: 'todayOverdue', title: '今日超时任务', level: 'task' })}
              />
              <MetricClick
                label='本周到期'
                value={summary?.weekDue ?? 0}
                onClick={() => openDrill({ metric: 'weekDue', title: '本周到期任务', level: 'task' })}
              />
              <MetricClick
                label='本周超时'
                value={summary?.weekOverdue ?? 0}
                danger
                onClick={() => openDrill({ metric: 'weekOverdue', title: '本周超时任务', level: 'task' })}
              />
            </Space>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
          xl={6}
        >
          <Card
            size='small'
            title='已完成'
            loading={loading}
            className='h-full'
          >
            <Space
              wrap
              size={[8, 8]}
            >
              <MetricClick
                label='今日完成'
                value={summary?.todayDone ?? 0}
                onClick={() =>
                  openDrill({ metric: 'todayDone', title: '今日完成任务', level: 'task', showSub: 'timing' })
                }
              />
              <MetricClick
                label='本周完成'
                value={summary?.weekDone ?? 0}
                onClick={() =>
                  openDrill({ metric: 'weekDone', title: '本周完成任务', level: 'task', showSub: 'timing' })
                }
              />
            </Space>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
          xl={6}
        >
          <RateCard
            title='按时完成率'
            rate={summary?.onTimeRate ?? 0}
            numerator={summary?.onTimeDone ?? 0}
            denominator={summary?.rangeDone ?? 0}
            formula='按时完成 / 已完成'
            rangeText={rangeText}
            onDrill={() => openDrill({ metric: 'onTimeRate', title: '按时完成率 · 员工', level: 'person' })}
          />
        </Col>
        <Col
          xs={24}
          lg={12}
          xl={6}
        >
          <RateCard
            title='任务完成率'
            rate={summary?.completionRate ?? 0}
            numerator={summary?.rangeCompleted ?? 0}
            denominator={summary?.rangeTotal ?? 0}
            formula='完成 / 总数'
            rangeText={rangeText}
            onDrill={() => openDrill({ metric: 'completionRate', title: '任务完成率 · 员工', level: 'person' })}
          />
        </Col>
      </Row>

      <Drawer
        title={drill?.title}
        open={!!drill}
        width={960}
        onClose={() => setDrill(null)}
        destroyOnClose
      >
        {isRateTask ? (
          <div className='mb-3 flex flex-wrap items-center gap-2'>
            <Button
              type='text'
              size='small'
              icon={<ArrowLeftOutlined />}
              onClick={backToPerson}
            >
              返回员工列表
            </Button>
            <Breadcrumb
              items={[
                {
                  title: (
                    <button
                      type='button'
                      className='text-neutral-500 hover:text-neutral-800'
                      onClick={backToPerson}
                    >
                      {drill?.metric === 'onTimeRate' ? '按时完成率' : '任务完成率'}
                    </button>
                  ),
                },
                { title: drill?.personName || '员工' },
              ]}
            />
          </div>
        ) : null}

        {isRatePerson ? (
          <Typography.Paragraph
            type='secondary'
            className='!mb-2 text-xs'
          >
            点击员工姓名或「看明细」查看该员工任务；共 {total} 人
          </Typography.Paragraph>
        ) : null}

        {drill?.showSub === 'timing' ? (
          <div className='mb-3'>
            <Segmented
              value={subFilter}
              options={[
                { label: '全部', value: 'all' },
                { label: '正常完成', value: 'onTime' },
                { label: '提前完成', value: 'early' },
                { label: '超时完成', value: 'late' },
              ]}
              onChange={(v) => {
                setPageNum(1);
                setSubFilter(String(v));
              }}
            />
          </div>
        ) : null}
        {drill?.showSub === 'completion' ? (
          <div className='mb-3'>
            <Segmented
              value={subFilter}
              options={[
                { label: '全部', value: 'all' },
                { label: '已完成', value: 'done' },
                { label: '未完成', value: 'open' },
              ]}
              onChange={(v) => {
                setPageNum(1);
                setSubFilter(String(v));
              }}
            />
          </div>
        ) : null}

        {!isRatePerson ? (
          <Typography.Paragraph
            type='secondary'
            className='!mb-2 text-xs'
          >
            共 {total} 条
          </Typography.Paragraph>
        ) : null}

        {isRatePerson ? (
          <Table
            rowKey={(r) => `${r.userId}-${r.userName}`}
            size='small'
            loading={drillLoading}
            columns={personColumns}
            dataSource={personRows}
            pagination={pagination}
            onRow={(r) => ({
              onClick: () => openPersonTasks(r),
              className: 'cursor-pointer',
            })}
          />
        ) : (
          <Table
            rowKey='id'
            size='small'
            loading={drillLoading}
            columns={taskColumns}
            dataSource={taskRows}
            pagination={pagination}
            scroll={{ x: 980 }}
          />
        )}
      </Drawer>
    </div>
  );
}
