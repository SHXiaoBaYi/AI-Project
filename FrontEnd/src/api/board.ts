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
