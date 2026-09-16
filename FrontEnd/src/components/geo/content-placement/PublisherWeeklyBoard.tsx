import { lazy, memo, Suspense, useEffect, useMemo, useState } from 'react';
import { ProFormDateRangePicker, ProFormSelect, QueryFilter } from '@ant-design/pro-components';
import { Button, Card, Col, Modal, Row, Segmented, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  getGeoContentPublisherWeeklyBoardApi,
  getGeoContentPublisherWeeklyDetailApi,
  getGeoOwnerOptionsApi,
} from '@/api/geo';
import type {
  GeoContentPublisherWeekDetail,
  GeoContentPublisherWeekRow,
  GeoContentWeekMetric,
  GeoOwnerOption,
} from '@/types/geo';
import { GEO_CONTENT_WEEK_METRICS } from '@/constants/geo';
import { BUTTERFLY_SEARCH } from '@/constants/searchLayout';
import { endOfIsoWeek, startOfIsoWeek, toDayjs } from '@/utils/geoBoardQuery';
import { STATUS_COLOR, isHttpUrl } from '@/components/geo/content-placement/constants';

const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Line })));
const Column = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Column })));

const defaultWeeks = [startOfIsoWeek(dayjs().subtract(3, 'week')), endOfIsoWeek(dayjs())];

type MetricKey = GeoContentWeekMetric;

const METRIC_LABEL: Record<MetricKey, string> = Object.fromEntries(
  GEO_CONTENT_WEEK_METRICS.map((m) => [m.key, m.label]),
) as Record<MetricKey, string>;

function metricValue(row: GeoContentPublisherWeekRow, key: MetricKey): number {
  switch (key) {
    case 'produced':
      return row.producedCount ?? 0;
    case 'pendingReview':
      return row.pendingReviewCount ?? 0;
    case 'published':
      return row.publishedCount ?? 0;
    case 'pendingProduce':
      return row.pendingProduceCount ?? 0;
    case 'videoPublished':
      return row.videoPublishedCount ?? 0;
    case 'videoPendingReview':
      return row.videoPendingReviewCount ?? 0;
    default:
      return 0;
  }
}

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

type ChartPoint = { axis: string; value: number; series: string };

const PublisherWeeklyBoard = memo(function PublisherWeeklyBoard() {
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<GeoContentPublisherWeekRow[]>([]);
  const [range, setRange] = useState(defaultWeeks);
  const [publisherUserId, setPublisherUserId] = useState<number>();
  const [lineMetric, setLineMetric] = useState<MetricKey>('published');
  const [barWeek, setBarWeek] = useState<string>();

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailTitle, setDetailTitle] = useState('');
  const [detailRows, setDetailRows] = useState<GeoContentPublisherWeekDetail[]>([]);
  const [iframeUrl, setIframeUrl] = useState<string>();

  useEffect(() => {
    void getGeoOwnerOptionsApi().then(setOwners);
    void load({ weekRange: defaultWeeks });
  }, []);

  const load = async (values?: Record<string, any>) => {
    const start = toDayjs(values?.weekRange?.[0]) ?? range[0];
    const end = toDayjs(values?.weekRange?.[1]) ?? range[1];
    const weekStart = startOfIsoWeek(start);
    const weekEnd = endOfIsoWeek(end);
    setRange([weekStart, weekEnd]);
    const pubId = values?.publisherUserId as number | undefined;
    setPublisherUserId(pubId);
    setLoading(true);
    try {
      const data = await getGeoContentPublisherWeeklyBoardApi({
        startDate: weekStart.format('YYYY-MM-DD'),
        endDate: weekEnd.format('YYYY-MM-DD'),
        publisherUserId: pubId,
      });
      const next = data?.rows ?? [];
      setRows(next);
      const weeks = [...new Set(next.map((r) => r.weekLabel).filter(Boolean))] as string[];
      setBarWeek((prev) => (prev && weeks.includes(prev) ? prev : weeks[weeks.length - 1]));
    } finally {
      setLoading(false);
    }
  };

  const openDetail = async (row: GeoContentPublisherWeekRow, metric: MetricKey, label: string) => {
    if (!row.publisherUserId || !row.weekStart || !row.weekEnd) return;
    setDetailTitle(`${row.publisherName || '-'} · ${row.weekLabel || ''} · ${label}`);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const list = await getGeoContentPublisherWeeklyDetailApi({
        startDate: row.weekStart,
        endDate: row.weekEnd,
        publisherUserId: row.publisherUserId,
        metric,
      });
      setDetailRows(list ?? []);
    } finally {
      setDetailLoading(false);
    }
  };

  const weekOptions = useMemo(() => [...new Set(rows.map((r) => r.weekLabel).filter(Boolean))] as string[], [rows]);

  /** 折线：周趋势。单人=多指标；多人=选定指标按发布人拆系列 */
  const lineData = useMemo(() => {
    const points: ChartPoint[] = [];
    if (!rows.length) return points;
    if (publisherUserId) {
      const mine = rows.filter((r) => r.publisherUserId === publisherUserId);
      for (const row of mine) {
        for (const m of GEO_CONTENT_WEEK_METRICS) {
          points.push({
            axis: row.weekLabel || '',
            value: metricValue(row, m.key),
            series: m.label,
          });
        }
      }
      return points;
    }
    for (const row of rows) {
      points.push({
        axis: row.weekLabel || '',
        value: metricValue(row, lineMetric),
        series: row.publisherName || '未命名',
      });
    }
    return points;
  }, [rows, publisherUserId, lineMetric]);

  /** 柱状：选定周内，各发布人多指标对比 */
  const columnData = useMemo(() => {
    const week = barWeek || weekOptions[weekOptions.length - 1];
    if (!week) return [] as ChartPoint[];
    const weekRows = rows.filter((r) => r.weekLabel === week);
    const points: ChartPoint[] = [];
    for (const row of weekRows) {
      for (const key of ['produced', 'published', 'pendingProduce', 'videoPublished'] as MetricKey[]) {
        points.push({
          axis: row.publisherName || '未命名',
          value: metricValue(row, key),
          series: METRIC_LABEL[key],
        });
      }
    }
    return points;
  }, [rows, barWeek, weekOptions]);

  /** 汇总折线：全员按周合计（产出/已发布/待产出） */
  const summaryLineData = useMemo(() => {
    const map = new Map<string, Record<MetricKey, number>>();
    for (const row of rows) {
      const week = row.weekLabel || '';
      if (!week) continue;
      const bucket = map.get(week) ?? {
        produced: 0,
        pendingReview: 0,
        published: 0,
        pendingProduce: 0,
        videoPublished: 0,
        videoPendingReview: 0,
      };
      for (const m of GEO_CONTENT_WEEK_METRICS) {
        bucket[m.key] += metricValue(row, m.key);
      }
      map.set(week, bucket);
    }
    const points: ChartPoint[] = [];
    for (const [week, bucket] of map) {
      for (const key of ['produced', 'published', 'pendingProduce', 'videoPublished'] as MetricKey[]) {
        points.push({ axis: week, value: bucket[key], series: METRIC_LABEL[key] });
      }
    }
    return points;
  }, [rows]);

  const columns: ColumnsType<GeoContentPublisherWeekRow> = useMemo(() => {
    const base: ColumnsType<GeoContentPublisherWeekRow> = [
      { title: '周', dataIndex: 'weekLabel', width: 110, fixed: 'left' },
      { title: '发布人', dataIndex: 'publisherName', width: 120, fixed: 'left' },
    ];
    for (const m of GEO_CONTENT_WEEK_METRICS) {
      base.push({
        title: m.label,
        dataIndex: m.key,
        width: 110,
        render: (_, row) => {
          const value = metricValue(row, m.key);
          const warn =
            (m.key === 'pendingReview' && row.hasPendingReview) ||
            (m.key === 'videoPendingReview' && row.hasVideoPendingReview);
          return (
            <Button
              type='link'
              className='px-0'
              onClick={() => openDetail(row, m.key, m.label)}
            >
              <span className={warn ? 'font-semibold text-orange-500' : ''}>{value}</span>
            </Button>
          );
        },
      });
    }
    return base;
  }, []);

  return (
    <>
      <Card
        size='small'
        className='mb-3'
      >
        <div className='text-sm text-neutral-600'>
          按发布人 × ISO 周沉淀：表格数字可点开明细；上方提供折线趋势与柱状对比。
        </div>
      </Card>
      <Card className='mb-3'>
        <QueryFilter
          {...BUTTERFLY_SEARCH}
          initialValues={{ weekRange: defaultWeeks }}
          onFinish={async (v) => {
            await load(v);
            return true;
          }}
          onReset={() => {
            void load({ weekRange: defaultWeeks });
          }}
        >
          <ProFormDateRangePicker
            name='weekRange'
            label='周范围'
            fieldProps={{
              picker: 'week',
              format: 'YYYY-[第]ww[周]',
              placeholder: ['开始周', '结束周'],
            }}
          />
          <ProFormSelect
            name='publisherUserId'
            label='发布人'
            allowClear
            showSearch
            optionFilterProp='label'
            options={owners.map((u) => ({ label: u.displayName, value: u.userId }))}
          />
        </QueryFilter>
      </Card>

      <Row
        gutter={[16, 16]}
        className='mb-3'
      >
        <Col
          xs={24}
          lg={12}
        >
          <Card
            title={publisherUserId ? '个人指标周趋势（折线）' : `发布人周趋势（折线 · ${METRIC_LABEL[lineMetric]}）`}
            loading={loading}
            extra={
              publisherUserId ? null : (
                <Segmented
                  size='small'
                  value={lineMetric}
                  onChange={(v) => setLineMetric(v as MetricKey)}
                  options={GEO_CONTENT_WEEK_METRICS.map((m) => ({ label: m.label, value: m.key }))}
                />
              )
            }
          >
            <Suspense fallback={<ChartFallback />}>
              <Line
                data={lineData}
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
            title='全员周汇总趋势（折线）'
            loading={loading}
          >
            <Suspense fallback={<ChartFallback />}>
              <Line
                data={summaryLineData}
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
            title='发布人指标对比（柱状）'
            loading={loading}
            extra={
              weekOptions.length ? (
                <Segmented
                  size='small'
                  value={barWeek || weekOptions[weekOptions.length - 1]}
                  onChange={(v) => setBarWeek(String(v))}
                  options={weekOptions.map((w) => ({ label: w, value: w }))}
                />
              ) : null
            }
          >
            <Suspense fallback={<ChartFallback height={300} />}>
              <Column
                data={columnData}
                xField='axis'
                yField='value'
                colorField='series'
                height={300}
              />
            </Suspense>
          </Card>
        </Col>
      </Row>

      <Card title={`明细表${publisherUserId ? '（已筛单人）' : ''}`}>
        <Table<GeoContentPublisherWeekRow>
          rowKey={(r) => `${r.weekLabel}-${r.publisherUserId}`}
          loading={loading}
          dataSource={rows}
          columns={columns}
          size='small'
          bordered
          scroll={{ x: 900, y: 420 }}
          pagination={{ pageSize: 20, hideOnSinglePage: true, showTotal: (t) => `共 ${t} 行` }}
        />
      </Card>

      <Modal
        title={detailTitle}
        open={detailOpen}
        onCancel={() => {
          setDetailOpen(false);
          setDetailRows([]);
        }}
        footer={null}
        width={960}
        destroyOnClose
      >
        <Table<GeoContentPublisherWeekDetail>
          rowKey={(r) => `${r.itemId}`}
          loading={detailLoading}
          dataSource={detailRows}
          size='small'
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          columns={[
            { title: '话题', dataIndex: 'topicName', width: 120, ellipsis: true },
            { title: '目标问题', dataIndex: 'targetQuestion', ellipsis: true },
            { title: '标题', dataIndex: 'title', width: 160, ellipsis: true },
            { title: '平台', dataIndex: 'platformName', width: 90 },
            { title: '形态', dataIndex: 'contentForm', width: 70 },
            {
              title: '状态',
              dataIndex: 'publishStatus',
              width: 100,
              render: (v: string) => <Tag color={STATUS_COLOR[v] || 'default'}>{v || '-'}</Tag>,
            },
            {
              title: '发布时间',
              dataIndex: 'publishTime',
              width: 110,
              render: (v?: string) => v || '-',
            },
            {
              title: '链接',
              dataIndex: 'publishUrl',
              ellipsis: true,
              render: (v?: string) =>
                isHttpUrl(v) ? <Typography.Link onClick={() => setIframeUrl(v)}>{v}</Typography.Link> : v || '-',
            },
          ]}
        />
      </Modal>

      <Modal
        title='外部链接'
        open={!!iframeUrl}
        onCancel={() => setIframeUrl(undefined)}
        footer={null}
        width='70%'
        destroyOnClose
      >
        {iframeUrl ? (
          <iframe
            title='week-detail-link'
            src={iframeUrl}
            className='h-[70vh] w-full border-0'
            sandbox='allow-scripts allow-same-origin allow-popups allow-forms'
          />
        ) : null}
      </Modal>
    </>
  );
});

export default PublisherWeeklyBoard;
