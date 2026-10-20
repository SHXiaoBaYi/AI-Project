import { useRef } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { endRobotSessionApi, listRobotSessionsApi } from '@/api/robot';

type Row = Record<string, unknown>;

export default function RobotSessionPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumnType<Row>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, row) => (
        <ActionButtons
          items={[
            {
              key: 'end',
              label: '结束',
              perm: 'robot:session:end',
              onClick: () => {
                modal.confirm({
                  title: '强制结束该会话？',
                  onOk: async () => {
                    await endRobotSessionApi(Number(row.id));
                    message.success('已结束');
                    actionRef.current?.reload();
                  },
                });
              },
            },
          ]}
        />
      ),
    },
    { title: '会话键', dataIndex: 'session_key', ellipsis: true, copyable: true, search: false },
    { title: '员工', dataIndex: 'staff_id', width: 120, search: false },
    {
      title: '类型',
      dataIndex: 'conversation_type',
      width: 80,
      search: false,
      render: (_, r) => (String(r.conversation_type) === '2' ? '群' : '单聊'),
    },
    {
      title: '状态',
      dataIndex: 'state',
      width: 120,
      valueType: 'select',
      valueEnum: { IDLE: 'IDLE', WAIT_URL: 'WAIT_URL', WAIT_MORE: 'WAIT_MORE' },
      render: (_, r) => <Tag>{String(r.state || '')}</Tag>,
    },
    { title: '意图', dataIndex: 'intent_code', width: 160, search: false },
    { title: '过期', dataIndex: 'expire_at', width: 170, search: false },
    { title: '更新', dataIndex: 'update_time', width: 170, search: false },
  ];

  return (
    <BaseProTable<Row>
      actionRef={actionRef}
      rowKey='id'
      columns={columns}
      headerTitle='机器人会话'
      request={async (params) => {
        const res = await listRobotSessionsApi({
          pageNum: params.current,
          pageSize: params.pageSize,
          state: params.state ? String(params.state) : undefined,
        });
        return { data: res.rows || [], success: true, total: res.total || 0 };
      }}
    />
  );
}
