import request from './request';

export type BoardChartStackItem = {
  field: string;
  key: string;
  label?: string;
};

export type BoardChartBar = {
  key: string;
  label: string;
  value: number;
  mom?: number | null;
  yoy?: number | null;
  sampleCount?: number;
  drillable: boolean;
};

export type BoardChartDrill = {
  title: string;
  domain: string;
  axisField: string;
  metric: string;
  metricLabel: string;
  grain: string;
  startDate: string;
  endDate: string;
  breadcrumb: BoardChartStackItem[];
  currentValue?: number | null;
  mom?: number | null;
  yoy?: number | null;
  bars: BoardChartBar[];
  trend?: BoardChartTrendPoint[];
  chartDrillable: boolean;
};

export type BoardChartTrendPoint = {
  axis: string;
  series: string;
  seriesKey?: string;
  value: number;
  sampleCount?: number;
  drillable?: boolean;
};

export type BoardChartDrillQuery = {
  domain: 'geo' | 'task';
  dim: 'topic' | 'theme' | 'person';
  personRole?: 'writer' | 'publisher' | 'owner';
  grain: 'day' | 'week' | 'month' | 'year';
  startDate?: string;
  endDate?: string;
  metric?: string;
  stack?: BoardChartStackItem[];
  clickKey?: string;
};

export function boardChartDrillApi(data: BoardChartDrillQuery) {
  return request.post<unknown, BoardChartDrill>('/board/chart/drill', data);
}

export type BoardTaskTofuChartType =
  'publishCount' | 'citeRate' | 'employeeCiteCompare' | 'employeeCiteMom' | 'employeeCiteYoy' | 'topicCiteCount';

export type BoardTaskTofuQuery = {
  startDate?: string;
  endDate?: string;
  grain?: string;
  chartType: BoardTaskTofuChartType;
  topicId?: number;
  targetQuestion?: string;
  publisherUserId?: number;
  publisherName?: string;
  contentPlatform?: string;
  aiPlatform?: string;
};

export type BoardTaskTofuChart = {
  chartType: string;
  level: string;
  seriesField?: string;
  grain?: string;
  metricLabel?: string;
  topicId?: number;
  topicName?: string;
  targetQuestion?: string;
  publisherUserId?: number;
  publisherName?: string;
  contentPlatform?: string;
  aiPlatform?: string;
  chart?: { axis: string; series: string; value: number; key?: string }[];
};

export type BoardTaskPublishDetail = {
  placementId?: number;
  itemId?: number;
  targetQuestion?: string;
  title?: string;
  topicName?: string;
  publisherName?: string;
  publishPlatform?: string;
  contentForm?: string;
  publishStatus?: string;
  publishTime?: string;
  publishUrl?: string;
  citeCount?: number;
};

export function boardTaskTofuChartApi(data: BoardTaskTofuQuery) {
  return request.post<unknown, BoardTaskTofuChart>('/board/task/tofu-chart', data);
}

export function boardTaskTofuPublishDetailApi(data: BoardTaskTofuQuery) {
  return request.post<unknown, BoardTaskPublishDetail[]>('/board/task/tofu-publish-detail', data);
}
