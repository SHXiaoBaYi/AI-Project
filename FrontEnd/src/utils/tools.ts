import { cloneDeep } from 'lodash-es';

const tools = {
  /**
   * 处理ProTable组件Request方法分页默认值，current改为pageNum,请根据后端接口规范自行定义
   * 同时过滤空值（'', undefined, null, 空数组），避免 POST JSON body 反序列化失败
   * @param params 查询参数
   * @returns
   */
  handleSearchParams(params: Record<string, any>) {
    const { current, ...rest } = cloneDeep(params);
    Object.keys(rest).forEach((key) => {
      const v = rest[key];
      if (v === '' || v === undefined || v === null || (Array.isArray(v) && v.length === 0)) {
        delete rest[key];
      }
    });
    return { ...rest, pageNum: current };
  },
};

export default tools;
