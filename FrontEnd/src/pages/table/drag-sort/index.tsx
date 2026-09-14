import React from 'react';
import { useState } from 'react';
import { App as AntdApp } from 'antd';
import { DragSortTable, ProCard } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';

const columns: ProColumns[] = [
  {
    title: '排序',
    dataIndex: 'sort',
    width: 60,
    className: 'drag-visible',
  },
  {
    title: '姓名',
    dataIndex: 'name',
    className: 'drag-visible',
  },
  {
    title: '年龄',
    dataIndex: 'age',
  },
  {
    title: '地址',
    dataIndex: 'address',
  },
];

const data = [
  {
    key: '1',
    name: 'John Brown',
    age: 32,
    address: 'New York No. 1 Lake Park',
  },
  {
    key: '2',
    name: 'Jim Green',
    age: 42,
    address: 'London No. 1 Lake Park',
  },
  {
    key: '3',
    name: 'Joe Black',
    age: 32,
    address: 'Sidney No. 1 Lake Park',
  },
];

const App: React.FC = () => {
  const { message } = AntdApp.useApp();
  const [dataSource, setDataSource] = useState(data);

  const handleDragSortEnd = (_beforeIndex: number, _afterIndex: number, newDataSource: any) => {
    setDataSource(newDataSource);
    message.success('修改列表排序成功');
  };

  return (
    <div className='flex flex-col gap-5'>
      <ProCard>
        <div className='mb-3 text-xl font-medium'>可拖拽表格示例</div>
        <div className='text-gray-500'>
          DragSortTable排序采用的dnd-kit，需要提供rowKey来确定数据的唯一值，否则不能正常工作。
        </div>
      </ProCard>
      <ProCard styles={{ body: { paddingTop: '20px' } }}>
        <DragSortTable
          headerTitle='拖拽排序(默认把手)'
          columns={columns}
          rowKey='key'
          search={false}
          pagination={false}
          dataSource={dataSource}
          dragSortKey='sort'
          onDragSortEnd={handleDragSortEnd}
        />
      </ProCard>
    </div>
  );
};

export default App;
