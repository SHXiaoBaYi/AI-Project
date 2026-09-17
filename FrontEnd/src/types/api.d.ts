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
  /** 创建时间起 YYYY-MM-DD */
  createTimeStart?: string;
  /** 创建时间止 YYYY-MM-DD */
  createTimeEnd?: string;
}
