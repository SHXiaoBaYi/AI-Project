import { useState, useEffect } from 'react';
import { Form, Input, Button, Card } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { login, getInfo } from '@/store/slices/userSlice';
import { message } from '@/store/slices/staticFunctionSlice';
import type { AppDispatch } from '@/store';
import loginBg from '@/assets/login-bg.png';

export default function Login() {
  const [loading, setLoading] = useState(false);
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  useEffect(() => {
    const authError = sessionStorage.getItem('authError');
    if (authError) {
      sessionStorage.removeItem('authError');
      message.error(authError);
    }
  }, []);

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      await dispatch(login(values)).unwrap();
      await dispatch(getInfo()).unwrap();
      message.success('登录成功');
      navigate('/', { replace: true });
    } catch {
      // Error
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className='relative flex min-h-screen items-center justify-center overflow-hidden md:justify-start'
      style={{
        background: `url(${loginBg}) center/cover no-repeat`,
      }}
    >
      {/* 装饰圆形 */}
      <div
        className='absolute rounded-full'
        style={{
          width: 520,
          height: 520,
          background: 'radial-gradient(circle, rgba(59,130,246,0.35) 70%, transparent 70%)',
          filter: 'blur(60px)',
          top: -160,
          right: -120,
        }}
      />
      <div
        className='absolute rounded-full'
        style={{
          width: 400,
          height: 400,
          background: 'radial-gradient(circle, rgba(99,102,241,0.3) 70%, transparent 70%)',
          filter: 'blur(60px)',
          bottom: -120,
          left: -100,
        }}
      />
      {/* <div
        className='absolute rounded-full'
        style={{
          width: 280,
          height: 280,
          background: 'radial-gradient(circle, rgba(34,197,94,0.25) 0%, transparent 70%)',
          filter: 'blur(50px)',
          top: '35%',
          left: '8%',
        }}
      /> */}

      {/* 登录卡片 */}
      <Card
        className='relative w-100 rounded-2xl! shadow-xl backdrop-blur-sm md:mr-40! md:ml-auto!'
        variant='borderless'
        style={{ background: 'rgba(255, 255, 255, 0.75)' }}
        styles={{ body: { padding: '40px 36px 32px' } }}
      >
        {/* Logo & 标题 */}
        <div className='mb-8 text-center'>
          <div
            className='mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl'
            style={{ background: 'linear-gradient(135deg, var(--ant-color-primary), var(--ant-color-primary-hover))' }}
          >
            <span className='text-2xl font-bold text-white'>B</span>
          </div>
          <h1 className='text-2xl font-bold tracking-tight text-gray-800'>Base Admin</h1>
          <p className='mt-1.5 text-sm text-gray-400'>基础权限管理系统</p>
        </div>

        {/* 登录表单 */}
        <Form
          name='login'
          initialValues={{ username: 'admin', password: 'admin123' }}
          onFinish={onFinish}
          size='large'
        >
          <Form.Item
            name='username'
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              prefix={<UserOutlined className='text-gray-400' />}
              placeholder='用户名'
              className='rounded-lg!'
            />
          </Form.Item>
          <Form.Item
            name='password'
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined className='text-gray-400' />}
              placeholder='密码'
              className='rounded-lg!'
            />
          </Form.Item>
          <Form.Item className='mb-2!'>
            <Button
              type='primary'
              htmlType='submit'
              loading={loading}
              block
              className='h-11! rounded-lg! text-base! font-medium!'
            >
              登 录
            </Button>
          </Form.Item>
        </Form>

        <p className='mt-4 text-center text-xs text-gray-400'>默认账号: admin / admin123</p>
      </Card>
    </div>
  );
}
