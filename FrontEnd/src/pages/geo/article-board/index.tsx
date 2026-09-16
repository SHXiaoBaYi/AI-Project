import { lazy, memo, Suspense, useEffect, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, QueryFilter } from '@ant-design/pro-components';
import { Button, Card, Col, Modal, Row, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Link } from 'react-router-dom';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getGeoContentArticleBoardApi,
  getGeoContentArticleDetailApi,
  getGeoOwnerOptionsApi,
  getGeoPlatformOptionsApi,
  getGeoTopicOptionsApi,
} from '@/api/geo';
import type {
  GeoContentArticleBoard,
  GeoContentArticleDetailRow,
  GeoContentPublisherCiteRow,
  GeoContentPublishAggRow,
  GeoOwnerOption,
  GeoPlatform,
  GeoRankItem,
  GeoTopic,
} from '@/types/geo';
import { GEO_CONTENT_FORMS, GEO_PLATFORM_TYPE_AI, GEO_PLATFORM_TYPE_CONTENT } from '@/constants/geo';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { toDayjs } from '@/utils/geoBoardQuery';

const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Line })));
const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Column })));

const emptyPublish: GeoContentArticleBoard = {
  publishCountChart: [],
  citeRateChart: [],
  publisherCiteCompareChart: [],
  publishPlatformCiteRank: [],
  articleCiteRank: [],
  publishRows: [],
  citeRows: [],
  publisherCiteRows: [],
};

function ChartFallback({ height = 280 }: { height?: number }) {
  return (
    <div
      className='flex items-center justify-center text-sm text-neutral-400'
      style={{ height }}
    >
      图表加载中…
    </div>
  );
}

const ArticleBoardPage = memo(function ArticleBoardPage() {
  return (
    <div className='flex flex-col gap-4'>
      <Card size='small'>
        <div className='flex flex-wrap items-center justify-between gap-2'>
          <Typography.Text type='secondary'>
            聚焦文章发布与 AI 收录；露出率请到「露出看板」，明细录入请到「日监测数据」或「投放管理」。
          </Typography.Text>
          <Space wrap>
            <Link to='/geo/expose-board'>
              <Button type='link'>露出看板</Button>
            </Link>
            <Link to='/geo/daily'>
              <Button type='link'>日监测数据</Button>
            </Link>
            <Link to='/geo/content-placement-manage'>
              <Button type='link'>投放管理</Button>
            </Link>
          </Space>
        </div>
      </Card>
      <PublishPanel />
    </div>
  );
});
const PublishPanel = memo(function PublishPanel() {
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [publishPlatforms, setPublishPlatforms] = useState<GeoPlatform[]>([]);
  const [aiPlatforms, setAiPlatforms] = useState<GeoPlatform[]>([]);
  const [board, setBoard] = useState<GeoContentArticleBoard>(emptyPublish);
  const [loading, setLoading] = useState(false);
  const defaultRange: [Dayjs, Dayjs] = [dayjs().subtract(27, 'day'), dayjs()];
  const [query, setQuery] = useState<Record<string, any>>({});
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailTitle, setDetailTitle] = useState('');
  const [detailRows, setDetailRows] = useState<GeoContentArticleDetailRow[]>([]);

  const load = async (values?: Record<string, any>) => {
    const start = toDayjs(values?.dateRange?.[0]) ?? defaultRange[0];
    const end = toDayjs(values?.dateRange?.[1]) ?? defaultRange[1];
    const nextQuery = {
      startDate: start.format('YYYY-MM-DD'),
      endDate: end.format('YYYY-MM-DD'),
      topicId: values?.topicId as number | undefined,
      publisherUserIds: values?.publisherUserIds?.length ? (values.publisherUserIds as number[]) : undefined,
      publishPlatforms: values?.publishPlatforms?.length ? (values.publishPlatforms as string[]) : undefined,
      aiPlatforms: values?.aiPlatforms?.length ? (values.aiPlatforms as string[]) : undefined,
      contentForm: values?.contentForm || undefined,
    };
    setQuery(nextQuery);
    setLoading(true);
    try {
      const data = await getGeoContentArticleBoardApi(nextQuery);
      setBoard({ ...emptyPublish, ...data });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void Promise.all([
      getGeoTopicOptionsApi(),
      getGeoOwnerOptionsApi(),
      getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_CONTENT),
      getGeoPlatformOptionsApi(GEO_PLATFORM_TYPE_AI),
    ]).then(([t, o, pub, ai]) => {
      setTopics(t);
      setOwners(o);
      setPublishPlatforms(pub);
      setAiPlatforms(ai);
    });
    void load({ dateRange: defaultRange });
  }, []);

  const openDetail = async (detailType: string, dimensionKey?: string, title?: string) => {
    setDetailTitle(title || '明细');
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const list = await getGeoContentArticleDetailApi({
        ...query,
        detailType,
        dimensionKey,
      });
      setDetailRows(list ?? []);
    } finally {
      setDetailLoading(false);
    }
  };

  const publishColumns: ColumnsType<GeoContentPublishAggRow> = [
    { title: '日期', dataIndex: 'dateLabel', width: 110 },
    { title: '话题', dataIndex: 'topicName', width: 140 },
    { title: '员工', dataIndex: 'publisherName', width: 120 },
    { title: '发布平台', dataIndex: 'publishPlatform', width: 120 },
    { title: '形态', dataIndex: 'contentForm', width: 80 },
    {
      title: '发布数量',
      dataIndex: 'publishCount',
      width: 100,
      render: (v, row) => (
        <Button
          type='link'
          className='px-0'
          onClick={() =>
            openDetail(
              'publish',
              row.dateLabel || row.publisherName || row.publishPlatform,
              `发布明细 · ${row.dateLabel}`,
            )
          }
        >
          {v}
        </Button>
      ),
    },
  ];

  const publisherColumns: ColumnsType<GeoContentPublisherCiteRow> = [
    { title: '员工', dataIndex: 'publisherName', width: 120 },
    { title: '成功发布', dataIndex: 'successCount', width: 90 },
    { title: '被收录', dataIndex: 'citedCount', width: 80 },
    { title: '引用次数', dataIndex: 'citeHitCount', width: 90 },
    {
      title: '收录率%',
      dataIndex: 'citeRate',
      width: 90,
      render: (v) => (v == null ? '-' : v),
    },
    {
      title: '收录率环比',
      dataIndex: 'citeRateMom',
      width: 110,
      render: (v) => formatDelta(v),
    },
    {
      title: '收录率同比',
      dataIndex: 'citeRateYoy',
      width: 110,
      render: (v) => formatDelta(v),
    },
  ];

  const rankColumns: ColumnsType<GeoRankItem> = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '引用次数',
      dataIndex: 'value',
      width: 110,
      render: (v, row) => (
        <Button
          type='link'
          className='px-0'
          onClick={() => openDetail('articleRank', row.name, `引用明细 · ${row.name}`)}
        >
          {v}
        </Button>
      ),
    },
  ];

  return (
    <div className='flex flex-col gap-4'>
      <Card>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ dateRange: defaultRange }}
          onFinish={async (v) => {
            await load(v);
            return true;
          }}
          onReset={() => void load({ dateRange: defaultRange })}
        >
          <ProFormDateRangePicker
            name='dateRange'
            label='时间'
          />
          <ProFormSelect
            name='topicId'
            label='话题'
            allowClear
            showSearch
            optionFilterProp='label'
            options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
          />
          <ProFormSelect
            name='publisherUserIds'
            label='员工'
            allowClear
            showSearch
            optionFilterProp='label'
            options={owners.map((u) => ({ label: u.displayName, value: u.userId }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
          <ProFormSelect
            name='publishPlatforms'
            label='发布平台'
            allowClear
            options={publishPlatforms.map((p) => ({ label: p.platformName, value: p.platformName }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
          <ProFormSelect
            name='aiPlatforms'
            label='AI平台'
            allowClear
            options={aiPlatforms.map((p) => ({ label: p.platformName, value: p.platformName }))}
            fieldProps={{ mode: 'multiple', maxTagCount: 'responsive' }}
          />
          <ProFormSelect
            name='contentForm'
            label='内容形态'
            allowClear
            options={[...GEO_CONTENT_FORMS]}
          />
        </QueryFilter>
      </Card>

      <Row gutter={[16, 16]}>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='文章/视频发布数量'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Column
                data={board.publishCountChart}
                xField='axis'
                yField='value'
                colorField='series'
                height={280}
              />
            </Suspense>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='内容被AI收录率'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Line
                data={board.citeRateChart}
                xField='axis'
                yField='value'
                colorField='series'
                height={280}
              />
            </Suspense>
          </Card>
        </Col>
        <Col span={24}>
          <Card
            title='员工AI收录对比'
            loading={loading}
            extra={<span className='text-xs text-neutral-500'>{board.publisherCompareHint}</span>}
          >
            <Suspense fallback={<ChartFallback height={260} />}>
              <Column
                data={board.publisherCiteCompareChart}
                xField='axis'
                yField='value'
                colorField='series'
                height={260}
              />
            </Suspense>
            <Table<GeoContentPublisherCiteRow>
              className='mt-3'
              rowKey={(r) => `${r.publisherUserId}`}
              size='small'
              bordered
              dataSource={board.publisherCiteRows}
              columns={publisherColumns}
              pagination={{ pageSize: 10, hideOnSinglePage: true }}
              scroll={{ x: 800 }}
            />
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='发布平台被引用次数排行'
            loading={loading}
          >
            <Table<GeoRankItem>
              rowKey='name'
              size='small'
              bordered
              dataSource={board.publishPlatformCiteRank}
              pagination={false}
              columns={[
                { title: '发布平台', dataIndex: 'name' },
                {
                  title: '引用次数',
                  dataIndex: 'value',
                  width: 110,
                  render: (v, row) => (
                    <Button
                      type='link'
                      className='px-0'
                      onClick={() => openDetail('platformRank', row.name, `平台引用 · ${row.name}`)}
                    >
                      {v}
                    </Button>
                  ),
                },
              ]}
            />
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title='文章被引用次数排行'
            loading={loading}
          >
            <Table<GeoRankItem>
              rowKey='name'
              size='small'
              bordered
              dataSource={board.articleCiteRank}
              pagination={false}
              columns={rankColumns}
            />
          </Card>
        </Col>
      </Row>

      <Card title='发布数量明细（点击数量查看详情）'>
        <Table<GeoContentPublishAggRow>
          rowKey={(r) => `${r.dateLabel}-${r.topicName}-${r.publisherName}-${r.publishPlatform}-${r.contentForm}`}
          size='small'
          bordered
          loading={loading}
          dataSource={board.publishRows}
          columns={publishColumns}
          pagination={{ pageSize: 15, showTotal: (t) => `共 ${t} 行` }}
          scroll={{ x: 800, y: 360 }}
        />
      </Card>

      <Modal
        title={detailTitle}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={1000}
        destroyOnHidden
      >
        <Table<GeoContentArticleDetailRow>
          rowKey={(r) => `${r.itemId}-${r.aiPlatform}-${r.citeUrl}`}
          size='small'
          bordered
          loading={detailLoading}
          dataSource={detailRows}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1100, y: 420 }}
          columns={[
            { title: '话题', dataIndex: 'topicName', width: 120 },
            { title: '员工', dataIndex: 'publisherName', width: 100 },
            { title: '目标问题', dataIndex: 'targetQuestion', width: 180 },
            { title: '标题', dataIndex: 'title', width: 160 },
            { title: '发布平台', dataIndex: 'publishPlatform', width: 100 },
            { title: '形态', dataIndex: 'contentForm', width: 70 },
            { title: '发布时间', dataIndex: 'publishTime', width: 110 },
            { title: 'AI平台', dataIndex: 'aiPlatform', width: 90 },
            {
              title: '链接',
              dataIndex: 'publishUrl',
              width: 120,
              render: (v) =>
                v ? (
                  <a
                    href={v}
                    target='_blank'
                    rel='noreferrer'
                  >
                    打开
                  </a>
                ) : (
                  '-'
                ),
            },
          ]}
        />
      </Modal>
    </div>
  );
});

function formatDelta(value?: number | null) {
  if (value == null || Number.isNaN(value)) return <span className='text-neutral-400'>-</span>;
  if (value > 0) return <Tag color='success'>+{value}</Tag>;
  if (value < 0) return <Tag color='error'>{value}</Tag>;
  return <Tag>0</Tag>;
}

export default ArticleBoardPage;
