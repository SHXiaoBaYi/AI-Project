export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

export interface PageResult<T> {
  total: number;
  rows: T[];
}

export interface PageQuery {
  pageNum?: number;
  pageSize?: number;
}
