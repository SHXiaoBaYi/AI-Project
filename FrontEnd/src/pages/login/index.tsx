import { useState, useEffect } from 'react';
import { Form, Input, Button, Card, Modal } from 'antd';
import { UserOutlined, LockOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import { login, getInfo } from '@/store/slices/userSlice';
import { message } from '@/store/slices/staticFunctionSlice';
import { CODE_LOGIN_CONFLICT } from '@/api/request';
import type { AppDispatch } from '@/store';
import loginBg from '@/assets/login-bg.png';

export default function Login() {
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();

  useEffect(() => {
    const authError = sessionStorage.getItem('authError');
    if (authError) {
      sessionStorage.removeItem('authError');
      message.error(authError);
    }
  }, []);

  const completeLogin = async (username: string, password: string, force = false) => {
    await dispatch(login({ username, password, force })).unwrap();
    await dispatch(getInfo()).unwrap();
    message.success('登录成功!');
    navigate('/', { replace: true });
  };

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      await completeLogin(values.username, values.password, false);
    } catch (err) {
      const code = Number((err as { code?: number | string })?.code);
      if (code === CODE_LOGIN_CONFLICT) {
        Modal.confirm({
          title: '账号登录提示',
          icon: <ExclamationCircleOutlined />,
          content: '该账号已在其他设备登录，是否强制对方下线？',
          okText: '强制下线',
          cancelText: '取消',
          centered: true,
          onOk: async () => {
            setLoading(true);
            try {
              await completeLogin(values.username, values.password, true);
            } catch {
              // error toast already handled
            } finally {
              setLoading(false);
            }
          },
          onCancel: () => {
            // 关闭弹窗，留在登录页
          },
        });
      }
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

      <Card
        className='relative w-100 rounded-2xl! shadow-xl backdrop-blur-sm md:mr-40! md:ml-auto!'
        variant='borderless'
        style={{ background: 'rgba(255, 255, 255, 0.75)' }}
        styles={{ body: { padding: '40px 36px 32px' } }}
      >
        <div className='mb-8 text-center'>
          <div
            className='mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl'
            style={{ background: 'linear-gradient(135deg, var(--ant-color-primary), var(--ant-color-primary-hover))' }}
          >
            <span className='text-2xl font-bold text-white'>小</span>
          </div>
          <h1 className='text-2xl font-bold tracking-tight text-gray-800'>小巴依(上海)</h1>
          <p className='mt-1.5 text-sm text-gray-400'>后台数据分析系统</p>
        </div>

        <Form
          form={form}
          name='login'
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
      </Card>
    </div>
  );
}
