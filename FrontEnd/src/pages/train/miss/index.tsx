import { memo, useRef } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';
import { listTrainQaMissApi, type TrainQaMiss } from '@/api/train';

const TrainQaMissPage = memo(function TrainQaMissPage() {
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumnType<TrainQaMiss>[] = [
    {
      title: '提问人',
      dataIndex: 'nickname',
      width: 120,
      search: false,
      render: (_, r) => r.nickname || r.username || '-',
    },
    {
      title: '账号',
      dataIndex: 'username',
      width: 120,
      search: false,
      ellipsis: true,
    },
    {
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '问法 / 提问人 / 账号' },
    },
    createTimeRangeColumn({ title: '提问时间' }),
    {
      title: '原问',
      dataIndex: 'question',
      ellipsis: true,
      search: false,
      width: 280,
    },
    {
      title: '分词意图',
      dataIndex: 'intentTokens',
      ellipsis: true,
      search: false,
      width: 200,
    },
    {
      title: '结果',
      dataIndex: 'status',
      width: 110,
      search: false,
      render: (_, r) => (r.status === 'NEED_OLD' ? <Tag color='warning'>建议搜旧版</Tag> : <Tag>没有找到答案</Tag>),
    },
    createTimeDisplayColumn({ title: '提问时间', width: 180 }),
  ];

  return (
    <BaseProTable<TrainQaMiss>
      rowKey='id'
      actionRef={actionRef}
      headerTitle='未命中提问'
      columns={columns}
      scroll={{ x: 1100 }}
      request={async (params) => {
        const res = await listTrainQaMissApi({
          pageNum: params.current,
          pageSize: params.pageSize,
          keyword: params.keyword ? String(params.keyword) : undefined,
          createTimeStart: params.createTimeStart ? String(params.createTimeStart) : undefined,
          createTimeEnd: params.createTimeEnd ? String(params.createTimeEnd) : undefined,
        });
        return { data: res.rows || [], total: res.total || 0, success: true };
      }}
    />
  );
});

export default TrainQaMissPage;
