import React from 'react';
import { ProCard, ProFormDatePicker, ProFormText, QueryFilter } from '@ant-design/pro-components';

const App: React.FC = () => {
  return (
    <div className='flex flex-col gap-5'>
      <ProCard>
        <div className='mb-3 text-xl font-medium'>Query筛选表单示例</div>
        <div className='text-gray-500'>
          有些时候表单要与别的组件组合使用，常见的有 Table ，List 等，这时候就需要一些特殊形态的表单。
        </div>
        <div className='text-gray-500'>
          QueryFilter 和 LightFilter 解决了配合组件使用的问题，避免了复杂的样式设置。ProTable 中默认 支持了 QueryFilter
          和 LightFilter 作为自己的筛选表单。
        </div>
      </ProCard>
      <ProCard styles={{ body: { paddingTop: '40px' } }}>
        <QueryFilter
          defaultCollapsed
          split
        >
          <ProFormText
            name='name'
            label='应用名称'
          />
          <ProFormDatePicker
            name='createDate'
            label='创建时间'
          />
          <ProFormText
            name='status'
            label='应用状态'
          />
          <ProFormDatePicker
            name='replyDate'
            label='响应日期'
          />
          <ProFormDatePicker
            name='startDate'
            label='创建时间'
          />
          <ProFormDatePicker
            name='endDate'
            label='结束时间'
          />
        </QueryFilter>
      </ProCard>
    </div>
  );
};

export default App;
