import React from 'react';
import { DownOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import { ProCard, ProTable, TableDropdown } from '@ant-design/pro-components';
import { Button } from 'antd';

import { createTableDataSource, DEMO_CREATOR_VALUE_ENUM, DEMO_STATUS_VALUE_ENUM } from './mockData';

export type TableListItem = {
  key: number;
  name: string;
  containers: number;
  creator: string;
  status: string;
  createdAt: number;
  memo: string;
};

const tableListDataSource = createTableDataSource({
  count: 5,
}) as TableListItem[];

const columns: ProColumns<TableListItem>[] = [
  {
    title: '应用名称',
    width: 120,
    dataIndex: 'name',
    render: (_) => <a>{_}</a>,
  },
  {
    title: '容器数量',
    dataIndex: 'containers',
    align: 'right',
    sorter: (a, b) => a.containers - b.containers,
  },
  {
    title: '状态',
    width: 80,
    dataIndex: 'status',
    initialValue: 'all',
    valueEnum: DEMO_STATUS_VALUE_ENUM,
  },
  {
    title: '负责人',
    width: 80,
    dataIndex: 'creator',
    valueEnum: DEMO_CREATOR_VALUE_ENUM,
  },
  {
    title: '操作',
    width: 180,
    key: 'option',
    valueType: 'option',
    render: () => [
      <a key='detail'>详情</a>,
      <a key='log'>日志</a>,
      <a key='monitor'>监控</a>,
      <TableDropdown
        key='actionGroup'
        menus={[
          { key: 'restart', name: '重启' },
          { key: 'delete', name: '下线' },
        ]}
      />,
    ],
  },
];

const App: React.FC = () => {
  return (
    <div className='flex flex-col gap-5'>
      <ProCard>
        <div className='mb-3 text-xl font-medium'>高级表格示例</div>
        <div className='text-gray-500'>
          当你的表格需要与服务端进行交互或者需要多种单元格样式时，ProTable 是不二选择。
        </div>
        <div className='text-gray-500'>
          ProTable 的诞生是为了解决项目中需要写很多 table
          的样板代码的问题，所以在其中封装了很多常用的逻辑。这些封装可以简单的分类为预设行为与预设逻辑。
        </div>
      </ProCard>
      <ProCard styles={{ body: { paddingTop: '20px' } }}>
        <ProTable<TableListItem>
          dataSource={tableListDataSource}
          rowKey='key'
          pagination={{
            showQuickJumper: true,
          }}
          columns={columns}
          search={false}
          dateFormatter='string'
          headerTitle='微服务应用列表'
          toolBarRender={() => [
            <Button key='log'>查看日志</Button>,
            <Button key='export'>
              导出数据
              <DownOutlined />
            </Button>,
            <Button
              type='primary'
              key='primary'
            >
              部署应用
            </Button>,
          ]}
        />
      </ProCard>
    </div>
  );
};

export default App;
