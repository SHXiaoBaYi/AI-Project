import { useRef } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import { listRobotMessagesApi } from '@/api/robot';

type Row = Record<string, unknown>;

export default function RobotMessagePage() {
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumnType<Row>[] = [
    {
      title: '方向',
      dataIndex: 'direction',
      width: 80,
      valueType: 'select',
      valueEnum: { IN: '入站', OUT: '出站' },
      render: (_, r) => <Tag color={r.direction === 'IN' ? 'blue' : 'green'}>{String(r.direction)}</Tag>,
    },
    { title: '员工', dataIndex: 'staff_id', width: 120, search: false },
    { title: '意图', dataIndex: 'intent_code', width: 160 },
    { title: '内容', dataIndex: 'content', ellipsis: true, search: false },
    {
      title: '成功',
      dataIndex: 'success',
      width: 80,
      search: false,
      render: (_, r) => (Number(r.success) === 1 ? '是' : '否'),
    },
    { title: '错误', dataIndex: 'error_msg', ellipsis: true, search: false },
    { title: '时间', dataIndex: 'create_time', width: 170, search: false },
  ];

  return (
    <BaseProTable<Row>
      actionRef={actionRef}
      rowKey='id'
      columns={columns}
      headerTitle='机器人消息流水'
      request={async (params) => {
        const res = await listRobotMessagesApi({
          pageNum: params.current,
          pageSize: params.pageSize,
          intentCode: params.intent_code ? String(params.intent_code) : undefined,
        });
        return { data: res.rows || [], success: true, total: res.total || 0 };
      }}
    />
  );
}
