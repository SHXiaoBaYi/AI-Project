import React, { useState } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import { EditableProTable, ProCard, ProFormField } from '@ant-design/pro-components';
import { Segmented } from 'antd';

import { createEditableRowId, DEMO_TASK_STATUS_ENUM } from '../pro/mockData';

type DataSourceType = {
  id: React.Key;
  title?: string;
  description?: string;
  status?: string;
  created_at?: number;
};

const defaultData: DataSourceType[] = [
  {
    id: 624748504,
    title: '优化首页加载速度',
    description: '首页白屏时间超过 3s，需优化资源加载',
    status: 'open',
    created_at: 1705286400000,
  },
  {
    id: 624691229,
    title: '修复登录超时问题',
    description: '高峰期登录请求超时，需排查连接池',
    status: 'closed',
    created_at: 1705200000000,
  },
  {
    id: 624674560,
    title: '新增数据导出功能',
    description: '支持导出 Excel 和 CSV 格式',
    status: 'processing',
    created_at: 1705113600000,
  },
];

const App: React.FC = () => {
  const [editableType, setEditableType] = useState<'single' | 'multiple'>('multiple');
  const [editableKeys, setEditableRowKeys] = useState<React.Key[]>([]);
  const [dataSource, setDataSource] = useState<readonly DataSourceType[]>([]);

  const columns: ProColumns<DataSourceType>[] = [
    {
      title: '任务名称',
      dataIndex: 'title',
      width: '30%',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'select',
      valueEnum: DEMO_TASK_STATUS_ENUM,
    },
    {
      title: '描述',
      dataIndex: 'description',
    },
    {
      title: '创建时间',
      dataIndex: 'created_at',
      valueType: 'date',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 150,
      render: (_text, record, _, action) => [
        <a
          key='editable'
          onClick={() => action?.startEditable?.(record.id)}
        >
          编辑
        </a>,
        <a
          key='delete'
          onClick={() => setDataSource(dataSource.filter((item) => item.id !== record.id))}
        >
          删除
        </a>,
      ],
    },
  ];

  return (
    <div className='flex flex-col gap-5'>
      <ProCard>
        <div className='mb-3 text-xl font-medium'>可编辑表格示例</div>
        <div className='text-gray-500'>
          可编辑表格 EditableProTable 与 ProTable 的功能基本相同，为了方便使用 EditableProTable
          增加了一些预设，关掉了查询表单和操作栏，同时修改了 value 和 onChange 使其可以方便的继承到 antd 的 Form 中。
        </div>
        <div className='text-gray-500'>
          通过 Segmented 切换 editable.type，在单行编辑（single）和多行编辑（multiple）之间一键切换。
        </div>
      </ProCard>
      <ProCard styles={{ body: { paddingTop: '40px' } }}>
        <div>
          <div style={{ marginBlockEnd: 16 }}>
            <span>editable.type 编辑模式：</span>
            <Segmented
              value={editableType}
              onChange={(v) => {
                setEditableType(v as any);
                setEditableRowKeys([]);
              }}
              options={[
                { label: '单行编辑 single', value: 'single' },
                { label: '多行编辑 multiple', value: 'multiple' },
              ]}
            />
          </div>
          <EditableProTable<DataSourceType>
            rowKey='id'
            headerTitle='编辑模式切换'
            columns={columns}
            request={async () => ({
              data: defaultData,
              total: defaultData.length,
              success: true,
            })}
            value={dataSource}
            onChange={setDataSource}
            recordCreatorProps={{
              position: 'bottom',
              record: () => ({ id: createEditableRowId() }),
              newRecordType: 'dataSource',
            }}
            editable={{
              type: editableType,
              editableKeys,
              onChange: setEditableRowKeys,
            }}
          />
          <ProCard
            title='表格数据'
            headerBordered
            collapsible
            defaultCollapsed
          >
            <ProFormField
              ignoreFormItem
              fieldProps={{ style: { width: '100%' } }}
              mode='read'
              valueType='jsonCode'
              text={JSON.stringify(dataSource)}
            />
          </ProCard>
        </div>
      </ProCard>
    </div>
  );
};

export default App;
