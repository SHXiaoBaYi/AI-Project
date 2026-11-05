import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, DatePicker, Input, Select, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import { getGeoTopicOptionsApi } from '@/api/geo';
import { getGeoAutoPlatformOptionsApi, getGeoAutoResultListApi, getGeoAutoResultMatrixApi } from '@/api/geoAuto';
import type { GeoAutoMatrixRow, GeoAutoResult, GeoAutoResultQuery } from '@/types/geoAuto';
import type { GeoTopic } from '@/types/geo';
import {
  GEO_AUTO_RECOMMEND_OPTIONS,
  GEO_AUTO_RESULT_STATUS,
  GEO_AUTO_RESULT_STATUS_MAP,
  GEO_AUTO_SENTIMENTS,
  geoAutoCellStatus,
  geoAutoSentimentColor,
} from '@/constants/geoAuto';
import ResultDetailDrawer from './components/ResultDetailDrawer';

const { RangePicker } = DatePicker;

/** 问题监控：矩阵视图（问题×平台）+ 明细列表 */
const AutoResultPage = memo(function AutoResultPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);

  // 矩阵视图状态
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([dayjs().subtract(6, 'day'), dayjs()]);
  const [matrixTopicId, setMatrixTopicId] = useState<number | undefined>();
  const [matrixKeyword, setMatrixKeyword] = useState('');
  const [matrix, setMatrix] = useState<{ platforms: string[]; rows: GeoAutoMatrixRow[] }>({ platforms: [], rows: [] });
  const [matrixLoading, setMatrixLoading] = useState(false);

  const [detailId, setDetailId] = useState<number | null>(null);

  useEffect(() => {
    getGeoTopicOptionsApi()
      .then(setTopics)
      .catch(() => setTopics([]));
    getGeoAutoPlatformOptionsApi()
      .then((list) => setPlatforms(list.map((p) => p.platformName)))
      .catch(() => setPlatforms([]));
  }, []);

  const loadMatrix = useCallback(async () => {
    setMatrixLoading(true);
    try {
      const query: GeoAutoResultQuery = {
        dateStart: dateRange[0].format('YYYY-MM-DD'),
        dateEnd: dateRange[1].format('YYYY-MM-DD'),
        topicId: matrixTopicId,
        questionText: matrixKeyword.trim() || undefined,
      };
      const res = await getGeoAutoResultMatrixApi(query);
      setMatrix({ platforms: res.platforms || [], rows: res.rows || [] });
    } catch {
      message.error('矩阵数据加载失败');
    } finally {
      setMatrixLoading(false);
    }
  }, [dateRange, matrixTopicId, matrixKeyword, message]);

  useEffect(() => {
    void loadMatrix();
  }, [loadMatrix]);

  const openDetail = (resultId: number) => setDetailId(resultId);

  const matrixColumns: ColumnsType<GeoAutoMatrixRow> = useMemo(() => {
    const base: ColumnsType<GeoAutoMatrixRow> = [
      {
        title: '监控问题',
        dataIndex: 'questionText',
        fixed: 'left',
        width: 300,
        render: (_, row) => (
          <div className='flex flex-col gap-0.5'>
            <span className='text-sm'>{row.questionText}</span>
            <span className='flex items-center gap-2 text-xs text-neutral-400'>
              {row.topicName && (
                <Tag
                  color='blue'
                  style={{ marginInlineEnd: 0 }}
                >
                  {row.topicName}
                </Tag>
              )}
              <span>提及 {row.mentionSummary}</span>
            </span>
          </div>
        ),
      },
    ];
    const platformCols: ColumnsType<GeoAutoMatrixRow> = matrix.platforms.map((p) => ({
      title: p,
      key: p,
      width: 130,
      align: 'center',
      render: (_, row) => {
        const cell = row.cells?.[p];
        if (!cell) {
          return <span className='text-neutral-300'>—</span>;
        }
        if (cell.status !== 'SUCCESS' && cell.status !== 'SKIPPED_MANUAL') {
          const meta = GEO_AUTO_RESULT_STATUS_MAP[cell.status];
          return (
            <Tooltip title={meta?.label || cell.status}>
              <span className='text-xs text-neutral-400'>{meta?.label || cell.status}</span>
            </Tooltip>
          );
        }
        const color = geoAutoCellStatus(cell.recommendStatus);
        const sentiment = cell.sentiment;
        return (
          <Tooltip
            title={
              <div className='text-xs'>
                <div>{cell.recommendStatus || '未出现'}</div>
                {cell.rankNo != null && <div>排名：第 {cell.rankNo} 位</div>}
                {sentiment && <div>情感：{sentiment}</div>}
                <div>监测日：{cell.runDate}</div>
                <div>点击查看回答原文</div>
              </div>
            }
          >
            <button
              type='button'
              className='flex w-full cursor-pointer flex-col items-center gap-0.5 rounded-md border border-transparent px-2 py-1 hover:border-neutral-300 hover:bg-neutral-50'
              onClick={() => openDetail(cell.resultId)}
            >
              <span
                className={
                  color === 'green' ? 'text-green-600' : color === 'gold' ? 'text-amber-500' : 'text-neutral-400'
                }
              >
                {cell.recommendStatus === '出现且推荐'
                  ? '● 推荐'
                  : cell.recommendStatus === '出现未推荐'
                    ? '● 提及'
                    : '○ 未出现'}
              </span>
              <span className='text-xs text-neutral-500'>
                {cell.rankNo != null ? `第${cell.rankNo}位` : sentiment ? sentiment : cell.runDate?.slice(5)}
              </span>
              {sentiment && (
                <span
                  className='inline-block h-1.5 w-1.5 rounded-full'
                  style={{
                    background: sentiment === '正面' ? '#52c41a' : sentiment === '负面' ? '#ff4d4f' : '#d9d9d9',
                  }}
                />
              )}
            </button>
          </Tooltip>
        );
      },
    }));
    return [...base, ...platformCols];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matrix.platforms]);

  // 明细列表列
  const listColumns: ProColumnType<GeoAutoResult>[] = [
    { title: '监测日期', dataIndex: 'runDate', width: 105, search: false },
    { title: '问题', dataIndex: 'questionText', ellipsis: true, width: 240, fieldProps: { placeholder: '问题搜索' } },
    { title: '话题', dataIndex: 'topicName', width: 110, search: false },
    {
      title: '平台',
      dataIndex: 'platform',
      width: 110,
      valueType: 'select',
      fieldProps: { options: platforms.map((p) => ({ label: p, value: p })) },
    },
    {
      title: '话题',
      dataIndex: 'topicId',
      hideInTable: true,
      valueType: 'select',
      fieldProps: { showSearch: true, options: topics.map((t) => ({ label: t.topicName, value: t.id })) },
    },
    {
      title: '监测日期',
      dataIndex: 'runDateRange',
      hideInTable: true,
      valueType: 'dateRange',
      search: {
        transform: (v) => ({
          dateStart: v?.[0] ? dayjs(v[0]).format('YYYY-MM-DD') : undefined,
          dateEnd: v?.[1] ? dayjs(v[1]).format('YYYY-MM-DD') : undefined,
        }),
      },
    },
    {
      title: '提及',
      dataIndex: 'mentioned',
      width: 80,
      valueType: 'select',
      fieldProps: {
        options: [
          { label: '提及', value: 1 },
          { label: '未提及', value: 0 },
        ],
      },
      render: (_, r) =>
        r.mentioned === 1 ? (
          <span className='text-green-600'>提及</span>
        ) : (
          <span className='text-neutral-400'>未提及</span>
        ),
    },
    {
      title: '排名',
      dataIndex: 'rankNo',
      width: 80,
      search: false,
      render: (_, r) => (r.rankNo != null ? `第${r.rankNo}位` : '-'),
    },
    {
      title: '推荐状态',
      dataIndex: 'recommendStatus',
      width: 110,
      valueType: 'select',
      fieldProps: { options: [...GEO_AUTO_RECOMMEND_OPTIONS] },
      render: (_, r) => r.recommendStatus || '-',
    },
    {
      title: '情感',
      dataIndex: 'sentiment',
      width: 80,
      valueType: 'select',
      fieldProps: { options: [...GEO_AUTO_SENTIMENTS] },
      render: (_, r) => (r.sentiment ? <Tag color={geoAutoSentimentColor(r.sentiment)}>{r.sentiment}</Tag> : '-'),
    },
    {
      title: '竞品',
      dataIndex: 'competitors',
      ellipsis: true,
      search: false,
      render: (_, r) => r.competitors || '-',
    },
    {
      title: '取证',
      width: 110,
      search: false,
      render: (_, r) => (
        <div className='flex gap-1'>
          <Tag color={r.deepThink === 1 ? 'cyan' : 'default'}>{r.deepThink === 1 ? '思考' : '常规'}</Tag>
          {r.hasScreenshot && <Tag color='blue'>截图</Tag>}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      fieldProps: { options: [...GEO_AUTO_RESULT_STATUS] },
      render: (_, r) => {
        const meta = GEO_AUTO_RESULT_STATUS_MAP[r.status];
        return <Tag color={meta?.color || 'default'}>{meta?.label || r.status}</Tag>;
      },
    },
    {
      title: '操作',
      valueType: 'option',
      width: 80,
      render: (_, record) => (
        <Button
          type='link'
          size='small'
          onClick={() => openDetail(record.id)}
        >
          详情
        </Button>
      ),
    },
  ];

  return (
    <>
      <Tabs
        defaultActiveKey='matrix'
        items={[
          {
            key: 'matrix',
            label: '矩阵视图',
            children: (
              <div className='flex flex-col gap-3'>
                <div className='flex flex-wrap items-center gap-3'>
                  <RangePicker
                    value={dateRange}
                    onChange={(v) => {
                      if (v && v[0] && v[1]) {
                        setDateRange([v[0], v[1]]);
                      }
                    }}
                    allowClear={false}
                  />
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp='label'
                    placeholder='全部话题'
                    style={{ width: 160 }}
                    options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
                    value={matrixTopicId}
                    onChange={setMatrixTopicId}
                  />
                  <Input.Search
                    allowClear
                    placeholder='问题搜索'
                    style={{ width: 240 }}
                    defaultValue={matrixKeyword}
                    onSearch={(v) => setMatrixKeyword(v)}
                  />
                  <Button
                    onClick={() => {
                      void loadMatrix();
                    }}
                  >
                    刷新
                  </Button>
                  <span className='text-xs text-neutral-400'>
                    格子 = 区间内该平台最新一次监测；点击查看回答原文与对比
                  </span>
                </div>
                <Table<GeoAutoMatrixRow>
                  rowKey='questionId'
                  size='small'
                  loading={matrixLoading}
                  columns={matrixColumns}
                  dataSource={matrix.rows}
                  scroll={{ x: 'max-content' }}
                  pagination={{ showSizeChanger: true, defaultPageSize: 20, pageSizeOptions: [20, 50, 100] }}
                />
              </div>
            ),
          },
          {
            key: 'list',
            label: '明细列表',
            children: (
              <BaseProTable<GeoAutoResult>
                rowKey='id'
                actionRef={actionRef}
                columns={listColumns}
                headerTitle='监测明细'
                scroll={{ x: 1200 }}
                request={async (params) => {
                  const res = await getGeoAutoResultListApi({
                    pageNum: params.current,
                    pageSize: params.pageSize,
                    dateStart: params.dateStart,
                    dateEnd: params.dateEnd,
                    platform: params.platform,
                    topicId: params.topicId,
                    questionText: params.questionText,
                    mentioned: params.mentioned,
                    sentiment: params.sentiment,
                    status: params.status,
                  });
                  return { data: res.rows, success: true, total: res.total };
                }}
              />
            ),
          },
        ]}
      />
      <ResultDetailDrawer
        resultId={detailId}
        onClose={() => setDetailId(null)}
      />
    </>
  );
});

export default AutoResultPage;
