import { Card, Typography } from 'antd';

/** Legacy day board URL retired */
export default function DayBoardRetired() {
  return (
    <Card>
      <Typography.Title level={5}>日报入口已下线</Typography.Title>
      <Typography.Paragraph type='secondary'>统一数据看板前端交互待重新实现。</Typography.Paragraph>
    </Card>
  );
}
