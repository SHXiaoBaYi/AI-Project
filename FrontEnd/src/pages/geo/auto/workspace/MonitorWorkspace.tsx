import { memo, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { App, Button, DatePicker, Empty, Input, Select, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  FilterOutlined,
  InboxOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getGeoTopicOptionsApi } from '@/api/geo';
import {
  createGeoAutoTaskApi,
  deleteGeoAutoTaskApi,
  getGeoAutoPlatformOptionsApi,
  getGeoAutoQuestionOptionsApi,
  getGeoAutoResultListApi,
  getGeoAutoTaskListApi,
  runGeoAutoTaskApi,
  toggleGeoAutoTaskApi,
} from '@/api/geoAuto';
import type { GeoAutoQuestionOption, GeoAutoResult, GeoAutoTask } from '@/types/geoAuto';
import type { GeoTopic } from '@/types/geo';
import { GEO_AUTO_RESULT_STATUS_MAP, GEO_AUTO_RUN_STATUS_MAP } from '@/constants/geoAuto';
import ResultDetailDrawer from '../results/components/ResultDetailDrawer';
import RunsDrawer from '../tasks/components/RunsDrawer';
import BrowserProbeDrawer from '../tasks/components/BrowserProbeDrawer';
import BatchAddMonitorModal from './BatchAddMonitorModal';

const { RangePicker } = DatePicker;

const TABS = [
  { key: 'kpi', label: '指标看板' },
  { key: 'qa', label: '问答明细' },
  { key: 'insight', label: '指标洞察' },
  { key: 'trend', label: '趋势分析' },
  { key: 'rank', label: '竞品排名' },
  { key: 'scene', label: '场景分析' },
  { key: 'sentiment', label: '舆情分析' },
  { key: 'compStat', label: '竞品统计' },
  { key: 'cite', label: '引用分析' },
  { key: 'recommend', label: '推荐问题' },
] as const;

type TabKey = (typeof TABS)[number]['key'];

function InfoTip({ text }: { text: string }) {
  return (
    <Tooltip title={text}>
      <span className='ml-1 inline-flex h-3.5 w-3.5 items-center justify-center rounded-full border border-neutral-300 text-[10px] text-neutral-400'>
        ?
      </span>
    </Tooltip>
  );
}

function SectionCard({
  title,
  extra,
  children,
  minHeight = 220,
}: {
  title: string;
  extra?: ReactNode;
  children: ReactNode;
  minHeight?: number;
}) {
  return (
    <div className='rounded-2xl border border-neutral-100 bg-white p-4 shadow-sm'>
      <div className='mb-3 flex items-center justify-between'>
        <div className='flex items-center text-sm font-medium text-neutral-800'>
          <span className='mr-1.5 h-3 w-3 rounded-sm bg-violet-100' />
          {title}
          <InfoTip text={`${title}：后续会按监控问题 × 模型聚合展示`} />
        </div>
        {extra}
      </div>
      <div style={{ minHeight }}>{children}</div>
    </div>
  );
}

function EmptyBlock({ text = '暂无数据' }: { text?: string }) {
  return (
    <div className='flex h-full min-h-[160px] flex-col items-center justify-center text-neutral-400'>
      <InboxOutlined className='mb-2 text-4xl text-neutral-200' />
      <div className='text-sm'>{text}</div>
    </div>
  );
}

const MonitorWorkspace = memo(function MonitorWorkspace() {
  const { message, modal } = App.useApp();
  const [tasks, setTasks] = useState<GeoAutoTask[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<GeoAutoTask | null>(null);
  const [tab, setTab] = useState<TabKey>('kpi');
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [questions, setQuestions] = useState<GeoAutoQuestionOption[]>([]);
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([dayjs().subtract(6, 'day'), dayjs()]);
  const [filterBrand, setFilterBrand] = useState<number | undefined>();
  const [filterQuestion, setFilterQuestion] = useState<string | undefined>();
  const [filterPlatform, setFilterPlatform] = useState<string | undefined>();
  const [results, setResults] = useState<GeoAutoResult[]>([]);
  const [resultLoading, setResultLoading] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [runsTask, setRunsTask] = useState<GeoAutoTask | null>(null);
  const [probeOpen, setProbeOpen] = useState(false);

  const selected = useMemo(() => tasks.find((t) => t.id === selectedId) ?? null, [tasks, selectedId]);

  const loadTasks = useCallback(
    async (preferName?: string) => {
      setLoadingList(true);
      try {
        const res = await getGeoAutoTaskListApi({ pageNum: 1, pageSize: 200, taskName: keyword.trim() || undefined });
        const rows = res.rows || [];
        setTasks(rows);
        setSelectedId((prev) => {
          if (preferName) {
            const hit = rows.find((t) => t.taskName === preferName);
            if (hit) {
              return hit.id;
            }
          }
          if (prev && rows.some((t) => t.id === prev)) {
            return prev;
          }
          return rows[0]?.id ?? null;
        });
      } finally {
        setLoadingList(false);
      }
    },
    [keyword],
  );

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  useEffect(() => {
    getGeoTopicOptionsApi()
      .then(setTopics)
      .catch(() => setTopics([]));
    getGeoAutoPlatformOptionsApi()
      .then((list) => setPlatforms(list.map((p) => p.platformName)))
      .catch(() => setPlatforms([]));
    getGeoAutoQuestionOptionsApi()
      .then(setQuestions)
      .catch(() => setQuestions([]));
  }, []);

  const loadResults = useCallback(async () => {
    if (!selected?.lastRunId) {
      setResults([]);
      return;
    }
    setResultLoading(true);
    try {
      const res = await getGeoAutoResultListApi({
        pageNum: 1,
        pageSize: 200,
        runId: selected.lastRunId,
        dateStart: dateRange[0].format('YYYY-MM-DD'),
        dateEnd: dateRange[1].format('YYYY-MM-DD'),
        platform: filterPlatform,
        topicId: filterBrand,
        questionText: filterQuestion,
      });
      setResults(res.rows || []);
    } finally {
      setResultLoading(false);
    }
  }, [selected?.lastRunId, dateRange, filterPlatform, filterBrand, filterQuestion]);

  useEffect(() => {
    void loadResults();
  }, [loadResults]);

  const kpi = useMemo(() => {
    const total = results.length;
    const mentioned = results.filter((r) => r.mentioned === 1).length;
    const ranks = results.map((r) => r.rankNo).filter((n): n is number => n != null);
    const avgRank = ranks.length ? ranks.reduce((a, b) => a + b, 0) / ranks.length : null;
    const pos = results.filter((r) => r.sentiment === '正面').length;
    const neu = results.filter((r) => r.sentiment === '中性').length;
    const neg = results.filter((r) => r.sentiment === '负面').length;
    const sentimentTotal = pos + neu + neg || 1;
    return {
      total,
      mentionRate: total ? ((mentioned / total) * 100).toFixed(2) : '0.00',
      avgRank: avgRank != null ? avgRank.toFixed(1) : '-',
      posPct: Math.round((pos / sentimentTotal) * 100),
      neuPct: Math.round((neu / sentimentTotal) * 100),
      negPct: Math.round((neg / sentimentTotal) * 100),
      top1: results.filter((r) => r.rankNo === 1).length,
      top3: results.filter((r) => r.rankNo != null && r.rankNo <= 3).length,
      top5: results.filter((r) => r.rankNo != null && r.rankNo <= 5).length,
    };
  }, [results]);

  const brandLabel = (task: GeoAutoTask) => {
    if (task.scopeType === 'TOPIC' && task.scopeIdList?.length) {
      const names = task.scopeIdList.map((id) => topics.find((t) => t.id === id)?.topicName).filter(Boolean);
      return names[0] || task.taskName;
    }
    const q = questions.find((item) => task.scopeIdList?.includes(item.id));
    return q?.topicName || task.taskName;
  };

  const runTask = (record: GeoAutoTask) => {
    modal.confirm({
      title: '立即执行监控',
      content: `将按已选模型逐题提问并采集回答。浏览器采集为串行执行，耗时较长。`,
      okText: '立即执行',
      onOk: async () => {
        const run = await runGeoAutoTaskApi(record.id);
        message.success(`已启动（批次 #${run.id}）`);
        await loadTasks();
        setRunsTask({ ...record, lastRunId: run.id });
      },
    });
  };

  const duplicateTask = async (record: GeoAutoTask) => {
    await createGeoAutoTaskApi({
      taskName: `${record.taskName}-副本`,
      scopeType: record.scopeType,
      scopeIds: record.scopeIdList ?? [],
      platforms: record.platformList ?? [],
      freq: record.freq,
      runTime: record.runTime,
      weekDay: record.weekDay,
      overwriteManual: record.overwriteManual,
      deepThink: record.deepThink,
      screenshotMode: record.screenshotMode,
      probeRegion: record.probeRegion,
      notifyEnabled: record.notifyEnabled,
      remark: record.remark,
    });
    message.success('已复制监控');
    await loadTasks(`${record.taskName}-副本`);
  };

  const resultColumns: ColumnsType<GeoAutoResult> = [
    {
      title: '操作',
      key: 'option',
      width: 72,
      fixed: 'left',
      render: (_, r) => (
        <Button
          type='link'
          size='small'
          onClick={() => setDetailId(r.id)}
        >
          详情
        </Button>
      ),
    },
    { title: '问题', dataIndex: 'questionText', ellipsis: true, width: 220 },
    { title: '模型', dataIndex: 'platform', width: 90 },
    {
      title: '登录账号',
      dataIndex: 'platformAccountMask',
      width: 110,
      render: (v: string) => v || <span className='text-neutral-300'>-</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string) => {
        const meta = GEO_AUTO_RESULT_STATUS_MAP[v];
        return <Tag color={meta?.color || 'default'}>{meta?.label || v || '-'}</Tag>;
      },
    },
    {
      title: '失败原因',
      dataIndex: 'errorMsg',
      ellipsis: { showTitle: false },
      render: (v: string, r) =>
        r.status === 'SUCCESS' || r.status === 'SKIPPED_MANUAL' || !v ? (
          <span className='text-neutral-300'>-</span>
        ) : (
          <Tooltip title={v}>
            <span className='text-red-600'>{v}</span>
          </Tooltip>
        ),
    },
    {
      title: '提及',
      dataIndex: 'mentioned',
      width: 80,
      render: (v, r) =>
        r.status !== 'SUCCESS' && r.status !== 'SKIPPED_MANUAL' ? (
          <span className='text-neutral-300'>-</span>
        ) : v === 1 ? (
          <span className='text-violet-600'>提及</span>
        ) : (
          <span className='text-neutral-400'>未提及</span>
        ),
    },
    {
      title: '排名',
      dataIndex: 'rankNo',
      width: 80,
      render: (v, r) => (r.status !== 'SUCCESS' && r.status !== 'SKIPPED_MANUAL' ? '-' : v != null ? `第${v}位` : '-'),
    },
    {
      title: '情感',
      dataIndex: 'sentiment',
      width: 80,
      render: (v, r) => (r.status !== 'SUCCESS' && r.status !== 'SKIPPED_MANUAL' ? '-' : v || '-'),
    },
  ];

  const laterTab = TABS.find((t) => t.key === tab && t.key !== 'kpi' && t.key !== 'qa');

  return (
    <div className='-m-5 flex h-[calc(100vh-88px)] min-h-[640px] overflow-hidden bg-[#f4f5f8]'>
      <aside className='flex w-[220px] shrink-0 flex-col border-r border-neutral-100 bg-white'>
        <div className='flex items-center justify-between px-4 py-3'>
          <div className='text-sm font-semibold'>
            监控列表 <span className='text-neutral-400'>({tasks.length})</span>
          </div>
          <PermissionButton
            perm='geo:auto:task:add'
            type='link'
            size='small'
            className='!px-0'
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            添加
          </PermissionButton>
        </div>
        <div className='px-3 pb-2'>
          <Input
            allowClear
            size='small'
            prefix={<SearchOutlined className='text-neutral-400' />}
            placeholder='搜索监控名称'
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => {
              void loadTasks();
            }}
          />
        </div>
        <div className='min-h-0 flex-1 overflow-y-auto px-2 pb-3'>
          {loadingList && !tasks.length ? (
            <div className='py-10 text-center text-xs text-neutral-400'>加载中…</div>
          ) : tasks.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description='暂无监控'
            />
          ) : (
            tasks.map((task) => {
              const active = task.id === selectedId;
              return (
                <button
                  key={task.id}
                  type='button'
                  onClick={() => setSelectedId(task.id)}
                  className={`mb-1 w-full rounded-xl px-3 py-2.5 text-left transition ${
                    active ? 'bg-violet-50 ring-1 ring-violet-200' : 'hover:bg-neutral-50'
                  }`}
                >
                  <div className={`truncate text-sm ${active ? 'font-semibold text-violet-700' : 'text-neutral-800'}`}>
                    {task.taskName}
                  </div>
                  <div className='mt-1 flex flex-wrap gap-1 text-[11px] text-neutral-400'>
                    <span>品牌:{brandLabel(task)}</span>
                    <span>周期:{task.freq || '每日'}</span>
                  </div>
                </button>
              );
            })
          )}
        </div>
        <div className='border-t border-neutral-100 px-3 py-2'>
          <button
            type='button'
            className='text-xs text-neutral-400 hover:text-violet-500'
            onClick={() => setProbeOpen(true)}
          >
            采集诊断
          </button>
        </div>
      </aside>

      <main className='min-w-0 flex-1 overflow-y-auto p-4'>
        {!selected ? (
          <div className='flex h-full items-center justify-center rounded-2xl bg-white'>
            <Empty description='请先添加一个监控'>
              <PermissionButton
                perm='geo:auto:task:add'
                type='primary'
                className='!bg-violet-600'
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditing(null);
                  setModalOpen(true);
                }}
              >
                添加监控
              </PermissionButton>
            </Empty>
          </div>
        ) : (
          <>
            <div className='mb-3 flex flex-wrap items-start justify-between gap-3'>
              <div>
                <div className='text-lg font-semibold text-neutral-900'>{selected.taskName}</div>
                <div className='mt-1 flex flex-wrap items-center gap-3 text-xs text-neutral-400'>
                  <span>创建时间：{selected.createTime?.replace('T', ' ') || '-'}</span>
                  <span>数据更新：{selected.lastRunStartTime?.replace('T', ' ') || '-'}</span>
                  <span>
                    任务状态：
                    {selected.enabled === 1 ? (
                      <Tag
                        color='success'
                        className='ml-1'
                      >
                        已开启
                      </Tag>
                    ) : (
                      <Tag
                        color='default'
                        className='ml-1'
                      >
                        已暂停
                      </Tag>
                    )}
                  </span>
                  {selected.lastRunStatus && (
                    <Tag color={GEO_AUTO_RUN_STATUS_MAP[selected.lastRunStatus]?.color || 'default'}>
                      {GEO_AUTO_RUN_STATUS_MAP[selected.lastRunStatus]?.label || selected.lastRunStatus}
                    </Tag>
                  )}
                  <PermissionButton
                    perm='geo:auto:task:run'
                    type='link'
                    size='small'
                    className='!px-0'
                    icon={<PlayCircleOutlined />}
                    onClick={() => runTask(selected)}
                  >
                    立即执行
                  </PermissionButton>
                  <Button
                    type='link'
                    size='small'
                    className='!px-0'
                    onClick={() => setRunsTask(selected)}
                  >
                    运行记录
                  </Button>
                </div>
              </div>
              <div className='flex flex-wrap items-center gap-2'>
                <Button
                  icon={<DownloadOutlined />}
                  disabled
                >
                  下载报告
                </Button>
                <Button icon={<FilterOutlined />}>表格筛选</Button>
                <Button
                  icon={<ExportOutlined />}
                  disabled
                >
                  导出
                </Button>
                <PermissionButton
                  perm='geo:auto:task:add'
                  icon={<CopyOutlined />}
                  onClick={() => {
                    void duplicateTask(selected);
                  }}
                >
                  复制
                </PermissionButton>
                <Button
                  icon={<SettingOutlined />}
                  onClick={() => {
                    setEditing(selected);
                    setModalOpen(true);
                  }}
                >
                  调整
                </Button>
                <PermissionButton
                  perm='geo:auto:task:edit'
                  onClick={async () => {
                    const next = selected.enabled === 1 ? 0 : 1;
                    await toggleGeoAutoTaskApi(selected.id, next);
                    message.success(next ? '已开启' : '已暂停');
                    await loadTasks();
                  }}
                >
                  {selected.enabled === 1 ? '暂停' : '开启'}
                </PermissionButton>
                <PermissionButton
                  perm='geo:auto:task:delete'
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => {
                    modal.confirm({
                      title: '删除监控',
                      content: '确定删除该监控？历史批次与结果将保留。',
                      okButtonProps: { danger: true },
                      onOk: async () => {
                        await deleteGeoAutoTaskApi(selected.id);
                        message.success('已删除');
                        setSelectedId(null);
                        await loadTasks();
                      },
                    });
                  }}
                >
                  删除
                </PermissionButton>
                <PermissionButton
                  perm='geo:auto:task:edit'
                  type='primary'
                  className='!bg-violet-600'
                  icon={<EditOutlined />}
                  onClick={() => {
                    setEditing(selected);
                    setModalOpen(true);
                  }}
                >
                  编辑
                </PermissionButton>
              </div>
            </div>

            <div className='mb-3 flex flex-wrap gap-3'>
              <Select
                allowClear
                placeholder='品牌 全部话题'
                className='min-w-[180px]'
                value={filterBrand}
                onChange={setFilterBrand}
                options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
                showSearch
                optionFilterProp='label'
              />
              <Select
                allowClear
                showSearch
                placeholder='问题 全部问题'
                className='min-w-[220px] flex-1'
                value={filterQuestion}
                onChange={setFilterQuestion}
                options={questions.map((q) => ({ label: q.questionText, value: q.questionText }))}
              />
              <Select
                allowClear
                placeholder='模型 全部模型'
                className='min-w-[160px]'
                value={filterPlatform}
                onChange={setFilterPlatform}
                options={platforms.map((p) => ({ label: p, value: p }))}
              />
              <Select
                defaultValue='7d'
                className='min-w-[120px]'
                options={[
                  { label: '近7天', value: '7d' },
                  { label: '近30天', value: '30d' },
                  { label: '自定义', value: 'custom' },
                ]}
                onChange={(v) => {
                  if (v === '7d') {
                    setDateRange([dayjs().subtract(6, 'day'), dayjs()]);
                  } else if (v === '30d') {
                    setDateRange([dayjs().subtract(29, 'day'), dayjs()]);
                  }
                }}
              />
              <RangePicker
                value={dateRange}
                onChange={(v) => {
                  if (v?.[0] && v[1]) {
                    setDateRange([v[0], v[1]]);
                  }
                }}
              />
            </div>

            <div className='mb-3 flex flex-wrap gap-1 rounded-full bg-white px-2 py-1'>
              {TABS.map((item) => (
                <button
                  key={item.key}
                  type='button'
                  onClick={() => setTab(item.key)}
                  className={`rounded-full px-3 py-1.5 text-sm ${
                    tab === item.key
                      ? 'bg-violet-50 font-medium text-violet-700'
                      : 'text-neutral-500 hover:text-neutral-800'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>

            {tab === 'kpi' && (
              <div className='flex flex-col gap-3'>
                <SectionCard title='指标看板'>
                  <div className='grid grid-cols-5 gap-3'>
                    <div className='rounded-xl bg-violet-50/80 px-4 py-3'>
                      <div className='text-xs text-neutral-500'>
                        提问次数 <InfoTip text='所选日期内该监控产生的提问条数' />
                      </div>
                      <div className='mt-3 text-3xl font-semibold text-violet-600'>{kpi.total}</div>
                    </div>
                    <div className='rounded-xl bg-violet-50/40 px-4 py-3'>
                      <div className='text-xs text-neutral-500'>
                        提及率 <InfoTip text='回答中提及监控品牌的比例' />
                      </div>
                      <div className='mt-3 text-3xl font-semibold text-violet-600'>{kpi.mentionRate}%</div>
                    </div>
                    <div className='rounded-xl bg-sky-50 px-4 py-3'>
                      <div className='text-xs text-neutral-500'>
                        平均排名 <InfoTip text='被提及时的平均位次' />
                      </div>
                      <div className='mt-3 text-3xl font-semibold text-sky-600'>{kpi.avgRank}</div>
                    </div>
                    <div className='rounded-xl bg-rose-50 px-4 py-3'>
                      <div className='mb-2 text-xs text-neutral-500'>
                        竞品曝光 <InfoTip text='Top1 / Top3 / Top5 出现次数' />
                      </div>
                      {[
                        ['top1', kpi.top1],
                        ['top3', kpi.top3],
                        ['top5', kpi.top5],
                      ].map(([label, val]) => (
                        <div
                          key={String(label)}
                          className='mb-1 flex items-center gap-2 text-xs text-neutral-500'
                        >
                          <span className='w-8'>{label}</span>
                          <span className='h-1.5 flex-1 rounded-full bg-rose-100' />
                          <span>{val}</span>
                          <span>0%</span>
                        </div>
                      ))}
                    </div>
                    <div className='rounded-xl bg-sky-50/70 px-4 py-3'>
                      <div className='mb-2 text-xs text-neutral-500'>
                        舆情分析 <InfoTip text='正面 / 中性 / 负面占比' />
                      </div>
                      {[
                        ['正面', kpi.posPct, 'bg-emerald-200'],
                        ['中性', kpi.neuPct, 'bg-sky-200'],
                        ['负面', kpi.negPct, 'bg-rose-200'],
                      ].map(([label, val, bar]) => (
                        <div
                          key={String(label)}
                          className='mb-1 flex items-center gap-2 text-xs text-neutral-500'
                        >
                          <span className='w-8'>{label}</span>
                          <span className={`h-1.5 flex-1 rounded-full ${bar}`} />
                          <span>{val}%</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </SectionCard>

                <SectionCard
                  title='问答明细'
                  extra={
                    <div className='flex items-center gap-2 text-xs text-neutral-400'>
                      <span>此问题：{filterQuestion || '全部问题'}</span>
                      <Button
                        size='small'
                        disabled
                      >
                        全屏
                      </Button>
                      <Button
                        size='small'
                        disabled
                      >
                        导出
                      </Button>
                    </div>
                  }
                >
                  {results.length ? (
                    <Table
                      size='small'
                      rowKey='id'
                      loading={resultLoading}
                      columns={resultColumns}
                      dataSource={results.slice(0, 8)}
                      pagination={false}
                      scroll={{ x: 1100 }}
                    />
                  ) : (
                    <EmptyBlock />
                  )}
                </SectionCard>

                <SectionCard
                  title='监控明细'
                  extra={
                    <div className='flex gap-3 text-xs text-violet-500'>
                      <span>监控问题</span>
                      <span>核心问</span>
                      <span>监控模型</span>
                    </div>
                  }
                >
                  {selected.questionCount ? (
                    <div className='text-sm text-neutral-600'>
                      圈选 {selected.scopeType === 'TOPIC' ? '话题' : '问题'} {selected.scopeIdList?.length || 0}{' '}
                      个，平台 {(selected.platformList || []).join('、') || '-'}
                    </div>
                  ) : (
                    <EmptyBlock />
                  )}
                </SectionCard>
              </div>
            )}

            {tab === 'qa' && (
              <SectionCard
                title='问答明细'
                minHeight={360}
              >
                {results.length ? (
                  <Table
                    size='small'
                    rowKey='id'
                    loading={resultLoading}
                    columns={resultColumns}
                    dataSource={results}
                    pagination={{ pageSize: 20 }}
                    scroll={{ x: 1100 }}
                  />
                ) : (
                  <EmptyBlock />
                )}
              </SectionCard>
            )}

            {laterTab && (
              <SectionCard
                title={laterTab.label}
                minHeight={360}
              >
                <EmptyBlock text={`${laterTab.label} 后续接入，先对齐交互框架`} />
              </SectionCard>
            )}
          </>
        )}
      </main>

      <BatchAddMonitorModal
        open={modalOpen}
        editing={editing}
        onClose={() => setModalOpen(false)}
        onSaved={(name) => {
          void loadTasks(name);
        }}
      />
      <ResultDetailDrawer
        resultId={detailId}
        onClose={() => setDetailId(null)}
      />
      <RunsDrawer
        task={runsTask}
        onClose={() => setRunsTask(null)}
      />
      <BrowserProbeDrawer
        open={probeOpen}
        onClose={() => setProbeOpen(false)}
      />
    </div>
  );
});

export default MonitorWorkspace;
