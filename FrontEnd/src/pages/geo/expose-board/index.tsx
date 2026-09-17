import { Card, Typography } from 'antd';

/** 旧露出看板已下线，统一数据看板前端待重做 */
export default function ExposeBoardRetired() {
  return (
    <Card>
      <Typography.Title level={5}>露出看板已下线</Typography.Title>
      <Typography.Paragraph type='secondary'>统一数据看板前端交互待重新实现。</Typography.Paragraph>
    </Card>
  );
}
