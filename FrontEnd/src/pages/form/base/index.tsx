import React from 'react';
import type { FormProps } from 'antd';
import { Button, Cascader, Checkbox, DatePicker, Form, Input, TreeSelect } from 'antd';
import { ProCard } from '@ant-design/pro-components';

type FieldType = {
  username?: string;
  password?: string;
  remember?: string;
};

const onFinish: FormProps<FieldType>['onFinish'] = (values) => {
  console.log('Success:', values);
};

const onFinishFailed: FormProps<FieldType>['onFinishFailed'] = (errorInfo) => {
  console.log('Failed:', errorInfo);
};

const App: React.FC = () => (
  <div className='flex flex-col gap-5'>
    <ProCard>
      <div className='mb-3 text-xl font-medium'>基础表单示例</div>
      <div className='text-gray-500'>高性能表单控件，自带数据域管理。包含数据录入、校验以及对应样式。</div>
      <div className='text-gray-500'>基本的表单数据域控制展示，包含布局、初始化、验证、提交。</div>
    </ProCard>
    <ProCard styles={{ body: { paddingTop: '40px' } }}>
      <Form
        name='basic'
        labelCol={{ span: 8 }}
        wrapperCol={{ span: 16 }}
        style={{ maxWidth: 600 }}
        initialValues={{ remember: true }}
        onFinish={onFinish}
        onFinishFailed={onFinishFailed}
        autoComplete='off'
      >
        <Form.Item<FieldType>
          label='Username'
          name='username'
          rules={[{ required: true, message: 'Please input your username!' }]}
        >
          <Input />
        </Form.Item>

        <Form.Item<FieldType>
          label='Password'
          name='password'
          rules={[{ required: true, message: 'Please input your password!' }]}
        >
          <Input.Password />
        </Form.Item>
        <Form.Item label='TreeSelect'>
          <TreeSelect
            treeData={[{ title: 'Light', value: 'light', children: [{ title: 'Bamboo', value: 'bamboo' }] }]}
          />
        </Form.Item>

        <Form.Item label='Cascader'>
          <Cascader
            options={[
              {
                value: 'zhejiang',
                label: 'Zhejiang',
                children: [{ value: 'hangzhou', label: 'Hangzhou' }],
              },
            ]}
          />
        </Form.Item>

        <Form.Item label='DatePicker'>
          <DatePicker />
        </Form.Item>

        <Form.Item<FieldType>
          name='remember'
          valuePropName='checked'
          label={null}
        >
          <Checkbox>Remember me</Checkbox>
        </Form.Item>

        <Form.Item label={null}>
          <Button
            type='primary'
            htmlType='submit'
          >
            Submit
          </Button>
        </Form.Item>
      </Form>
    </ProCard>
  </div>
);

export default App;
