import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result } from 'antd';
import { useNavigate, useRouteError } from 'react-router-dom';

interface PageErrorBoundaryProps {
  children: ReactNode;
}

interface PageErrorBoundaryState {
  error: Error | null;
}

export default class PageErrorBoundary extends Component<PageErrorBoundaryProps, PageErrorBoundaryState> {
  state: PageErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('PageErrorBoundary', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <Result
          status='error'
          title='当前页面出错了'
          subTitle={this.state.error.message || '可从左侧菜单切换到其他页面'}
          extra={
            <Button
              type='primary'
              onClick={() => this.setState({ error: null })}
            >
              重试
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}

export function RouteErrorPage() {
  const error = useRouteError();
  const navigate = useNavigate();
  const message = error instanceof Error ? error.message : '可从左侧菜单切换到其他页面';
  return (
    <Result
      status='error'
      title='当前页面出错了'
      subTitle={message}
      extra={
        <Button
          type='primary'
          onClick={() => navigate(0)}
        >
          刷新
        </Button>
      }
    />
  );
}
