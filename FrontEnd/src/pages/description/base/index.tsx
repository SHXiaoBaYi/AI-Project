import React from 'react';
import { Descriptions } from 'antd';
import { ProCard } from '@ant-design/pro-components';

const App: React.FC = () => (
  <div className='flex flex-col gap-5'>
    <ProCard>
      <div className='mb-3 text-xl font-medium'>基础描述组件示例</div>
      <div className='text-gray-500'>展示多个只读字段的组合。</div>
    </ProCard>
    <ProCard styles={{ body: { paddingTop: '40px' } }}>
      <Descriptions title='User Info'>
        <Descriptions.Item label='UserName'>Zhou Maomao</Descriptions.Item>
        <Descriptions.Item label='Telephone'>1810000000</Descriptions.Item>
        <Descriptions.Item label='Live'>Hangzhou, Zhejiang</Descriptions.Item>
        <Descriptions.Item label='Remark'>empty</Descriptions.Item>
        <Descriptions.Item label='Address'>
          No. 18, Wantang Road, Xihu District, Hangzhou, Zhejiang, China
        </Descriptions.Item>
      </Descriptions>
    </ProCard>
  </div>
);

export default App;
