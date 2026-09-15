import { Card, Table, Tag } from 'antd';
import { Column, Line } from '@ant-design/charts';
import type { GeoChartPoint } from '@/types/geo';

export interface GeoTrendRow {
  axisLabel: string;
  topicName: string;
  platform: string;
  sampleCount: number;
  mentionRate: number;
  firstMentionRate: number;
  recommendCount: number;
  competitorTop?: string;
  citePlatformTop?: string;
  fromSnapshot?: boolean;
}

export function GeoTrendBoard({
  mentionChart = [],
  firstMentionChart = [],
  recommendChart = [],
  rows = [],
  axisTitle,
  tableTitle,
}: {
  mentionChart?: GeoChartPoint[];
  firstMentionChart?: GeoChartPoint[];
  recommendChart?: GeoChartPoint[];
  rows?: GeoTrendRow[];
  axisTitle: string;
  tableTitle: string;
}) {
  return (
    <>
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
          columns={[
            { title: axisTitle, dataIndex: 'axisLabel' },
            { title: '话题', dataIndex: 'topicName' },
            { title: '平台', dataIndex: 'platform' },
            { title: '样本', dataIndex: 'sampleCount' },
            { title: '提及率%', dataIndex: 'mentionRate' },
            { title: '首位提及率%', dataIndex: 'firstMentionRate' },
            { title: '推荐次数', dataIndex: 'recommendCount' },
            { title: '竞品TOP', dataIndex: 'competitorTop', ellipsis: true },
            { title: '引用平台TOP', dataIndex: 'citePlatformTop', ellipsis: true },
            {
              title: '来源',
              dataIndex: 'fromSnapshot',
              width: 90,
              render: (v: boolean | undefined) => (v ? <Tag color='success'>已落库</Tag> : <Tag>实时</Tag>),
            },
          ]}
        />
      </Card>
    </>
  );
}
