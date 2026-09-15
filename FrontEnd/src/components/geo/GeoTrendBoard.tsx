import { useMemo } from 'react';
import { Card, Col, Row, Statistic, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons';
import { Column, Line } from '@ant-design/charts';
import type { GeoBoardCompareSummary, GeoChartPoint } from '@/types/geo';

export interface GeoTrendRow {
  axisLabel: string;
  topicName: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  mentionRateMom?: number | null;
  mentionRateYoy?: number | null;
  firstMentionRate: number;
  firstMentionRateMom?: number | null;
  firstMentionRateYoy?: number | null;
  recommendCount: number;
  recommendCountMom?: number | null;
  recommendCountYoy?: number | null;
  competitorTop?: string;
  citePlatformTop?: string;
  fromSnapshot?: boolean;
}

function formatDelta(value?: number | null, suffix = 'pp') {
  if (value == null || Number.isNaN(value)) return '-';
  const sign = value > 0 ? '+' : '';
  return `${sign}${value}${suffix}`;
}

function DeltaTag({ value, suffix = 'pp' }: { value?: number | null; suffix?: string }) {
  if (value == null || Number.isNaN(value)) return <span className='text-neutral-400'>-</span>;
  if (value > 0) {
    return (
      <Tag
        color='success'
        icon={<ArrowUpOutlined />}
      >
        {formatDelta(value, suffix)}
      </Tag>
    );
  }
  if (value < 0) {
    return (
      <Tag
        color='error'
        icon={<ArrowDownOutlined />}
      >
        {formatDelta(value, suffix)}
      </Tag>
    );
  }
  return <Tag>0{suffix}</Tag>;
}

/** 从明细行推导同比/环比摘要（后端字段缺失时兜底） */
export function buildCompareSummaryFromRows(rows: GeoTrendRow[], hint: string): GeoBoardCompareSummary {
  if (!rows.length) {
    return { compareHint: hint, mentionRate: 0, firstMentionRate: 0, recommendCount: 0, sampleCount: 0 };
  }
  const axes = [...new Set(rows.map((r) => r.axisLabel))].sort();
  const latest = axes[axes.length - 1];
  const latestRows = rows.filter((r) => r.axisLabel === latest);
  const sample = latestRows.reduce((s, r) => s + (r.sampleCount || 0), 0);
  const mention =
    sample > 0
      ? latestRows.reduce((s, r) => s + r.mentionRate * (r.sampleCount || 0), 0) / sample
      : latestRows.reduce((s, r) => s + r.mentionRate, 0) / latestRows.length;
  const first =
    sample > 0
      ? latestRows.reduce((s, r) => s + r.firstMentionRate * (r.sampleCount || 0), 0) / sample
      : latestRows.reduce((s, r) => s + r.firstMentionRate, 0) / latestRows.length;
  const recommend = latestRows.reduce((s, r) => s + (r.recommendCount || 0), 0);
  const avg = (vals: (number | null | undefined)[]) => {
    const nums = vals.filter((v): v is number => v != null && !Number.isNaN(v));
    if (!nums.length) return null;
    return Math.round((nums.reduce((a, b) => a + b, 0) / nums.length) * 100) / 100;
  };
  return {
    compareHint: `${hint}（当前周期：${latest}）`,
    mentionRate: Math.round(mention * 100) / 100,
    firstMentionRate: Math.round(first * 100) / 100,
    recommendCount: recommend,
    sampleCount: sample,
    mentionRateMom: avg(latestRows.map((r) => r.mentionRateMom)),
    mentionRateYoy: avg(latestRows.map((r) => r.mentionRateYoy)),
    firstMentionRateMom: avg(latestRows.map((r) => r.firstMentionRateMom)),
    firstMentionRateYoy: avg(latestRows.map((r) => r.firstMentionRateYoy)),
    recommendCountMom: avg(latestRows.map((r) => r.recommendCountMom)),
    recommendCountYoy: avg(latestRows.map((r) => r.recommendCountYoy)),
  };
}

export function GeoCompareSummaryCards({
  summary,
  title = '同比 / 环比看板',
  alwaysShow = true,
}: {
  summary?: GeoBoardCompareSummary | null;
  title?: string;
  alwaysShow?: boolean;
}) {
  const data =
    summary ??
    (alwaysShow
      ? {
          compareHint: '暂无对比基期数据（需有上一周期 / 去年同期样本）',
          mentionRate: 0,
          firstMentionRate: 0,
          recommendCount: 0,
        }
      : null);
  if (!data) return null;

  const momChart: GeoChartPoint[] = [
    { axis: '提及率', series: '环比(pp)', value: Number(data.mentionRateMom ?? 0) },
    { axis: '首位提及率', series: '环比(pp)', value: Number(data.firstMentionRateMom ?? 0) },
    { axis: '推荐次数', series: '环比', value: Number(data.recommendCountMom ?? 0) },
  ];
  const yoyChart: GeoChartPoint[] = [
    { axis: '提及率', series: '同比(pp)', value: Number(data.mentionRateYoy ?? 0) },
    { axis: '首位提及率', series: '同比(pp)', value: Number(data.firstMentionRateYoy ?? 0) },
    { axis: '推荐次数', series: '同比', value: Number(data.recommendCountYoy ?? 0) },
  ];

  return (
    <Card
      title={title}
      size='small'
    >
      <div className='mb-3 text-sm text-neutral-500'>{data.compareHint || '环比=上一周期；同比=去年同期'}</div>
      <Row gutter={[16, 16]}>
        <Col
          xs={24}
          sm={12}
          lg={8}
        >
          <Card size='small'>
            <Statistic
              title='提及率%'
              value={data.mentionRate ?? 0}
              precision={2}
            />
            <div className='mt-2 flex flex-wrap gap-2'>
              <span className='text-xs text-neutral-500'>环比</span>
              <DeltaTag value={data.mentionRateMom} />
              <span className='text-xs text-neutral-500'>同比</span>
              <DeltaTag value={data.mentionRateYoy} />
            </div>
          </Card>
        </Col>
        <Col
          xs={24}
          sm={12}
          lg={8}
        >
          <Card size='small'>
            <Statistic
              title='首位提及率%'
              value={data.firstMentionRate ?? 0}
              precision={2}
            />
            <div className='mt-2 flex flex-wrap gap-2'>
              <span className='text-xs text-neutral-500'>环比</span>
              <DeltaTag value={data.firstMentionRateMom} />
              <span className='text-xs text-neutral-500'>同比</span>
              <DeltaTag value={data.firstMentionRateYoy} />
            </div>
          </Card>
        </Col>
        <Col
          xs={24}
          sm={12}
          lg={8}
        >
          <Card size='small'>
            <Statistic
              title='推荐次数'
              value={data.recommendCount ?? 0}
            />
            <div className='mt-2 flex flex-wrap gap-2'>
              <span className='text-xs text-neutral-500'>环比</span>
              <DeltaTag
                value={data.recommendCountMom}
                suffix=''
              />
              <span className='text-xs text-neutral-500'>同比</span>
              <DeltaTag
                value={data.recommendCountYoy}
                suffix=''
              />
            </div>
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            size='small'
            title='环比变化看板'
          >
            <Column
              data={momChart}
              xField='axis'
              yField='value'
              colorField='series'
              height={220}
            />
          </Card>
        </Col>
        <Col
          xs={24}
          lg={12}
        >
          <Card
            size='small'
            title='同比变化看板'
          >
            <Column
              data={yoyChart}
              xField='axis'
              yField='value'
              colorField='series'
              height={220}
            />
          </Card>
        </Col>
      </Row>
    </Card>
  );
}

export function GeoTrendBoard({
  mentionChart = [],
  firstMentionChart = [],
  recommendChart = [],
  rows = [],
  axisTitle,
  tableTitle,
  compareSummary,
  showCompare = false,
  compareHint = '环比=上一周期；同比=去年同期',
}: {
  mentionChart?: GeoChartPoint[];
  firstMentionChart?: GeoChartPoint[];
  recommendChart?: GeoChartPoint[];
  rows?: GeoTrendRow[];
  axisTitle: string;
  tableTitle: string;
  compareSummary?: GeoBoardCompareSummary | null;
  showCompare?: boolean;
  compareHint?: string;
}) {
  const resolvedCompare = useMemo(() => {
    if (!showCompare) return null;
    if (compareSummary && (compareSummary.mentionRate != null || compareSummary.sampleCount != null)) {
      return compareSummary;
    }
    return buildCompareSummaryFromRows(rows, compareHint);
  }, [showCompare, compareSummary, rows, compareHint]);

  const columns: ColumnsType<GeoTrendRow> = [
    { title: axisTitle, dataIndex: 'axisLabel' },
    { title: '话题', dataIndex: 'topicName' },
    { title: '平台', dataIndex: 'platform' },
    { title: '样本', dataIndex: 'sampleCount' },
    { title: '提及率%', dataIndex: 'mentionRate' },
    ...(showCompare
      ? ([
          { title: '提及环比', dataIndex: 'mentionRateMom', render: (v: number | null) => <DeltaTag value={v} /> },
          { title: '提及同比', dataIndex: 'mentionRateYoy', render: (v: number | null) => <DeltaTag value={v} /> },
        ] as ColumnsType<GeoTrendRow>)
      : []),
    { title: '首位提及率%', dataIndex: 'firstMentionRate' },
    ...(showCompare
      ? ([
          { title: '首位环比', dataIndex: 'firstMentionRateMom', render: (v: number | null) => <DeltaTag value={v} /> },
          { title: '首位同比', dataIndex: 'firstMentionRateYoy', render: (v: number | null) => <DeltaTag value={v} /> },
        ] as ColumnsType<GeoTrendRow>)
      : []),
    { title: '推荐次数', dataIndex: 'recommendCount' },
    ...(showCompare
      ? ([
          {
            title: '推荐环比',
            dataIndex: 'recommendCountMom',
            render: (v: number | null) => (
              <DeltaTag
                value={v}
                suffix=''
              />
            ),
          },
          {
            title: '推荐同比',
            dataIndex: 'recommendCountYoy',
            render: (v: number | null) => (
              <DeltaTag
                value={v}
                suffix=''
              />
            ),
          },
        ] as ColumnsType<GeoTrendRow>)
      : []),
    { title: '竞品TOP', dataIndex: 'competitorTop', ellipsis: true },
    { title: '引用平台TOP', dataIndex: 'citePlatformTop', ellipsis: true },
    {
      title: '来源',
      dataIndex: 'fromSnapshot',
      width: 90,
      render: (v: boolean | undefined) => (v ? <Tag color='success'>已落库</Tag> : <Tag>实时</Tag>),
    },
  ];

  return (
    <>
      {showCompare ? (
        <GeoCompareSummaryCards
          summary={resolvedCompare}
          title={`${axisTitle}同比 / 环比看板`}
          alwaysShow
        />
      ) : null}
      <Card title={`提及率%（折线，横轴=${axisTitle}，系列=平台）`}>
        <Line
          data={mentionChart}
          xField='axis'
          yField='value'
          colorField='series'
          height={280}
        />
      </Card>
      <Card title={`首位提及率%（折线，横轴=${axisTitle}，系列=平台）`}>
        <Line
          data={firstMentionChart}
          xField='axis'
          yField='value'
          colorField='series'
          height={280}
        />
      </Card>
      <Card title={`推荐次数（柱状，横轴=${axisTitle}，系列=平台）`}>
        <Column
          data={recommendChart}
          xField='axis'
          yField='value'
          colorField='series'
          height={260}
        />
      </Card>
      <Card title={tableTitle}>
        <Table
          rowKey={(r) => `${r.axisLabel}-${r.topicName}-${r.platform}`}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 'max-content' }}
          columns={columns}
        />
      </Card>
    </>
  );
}
