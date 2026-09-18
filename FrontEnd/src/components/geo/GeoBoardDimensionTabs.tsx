import { Tabs } from 'antd';
import type { ReactNode } from 'react';

export type GeoBoardDimension = 'topic' | 'owner';

export function GeoBoardDimensionTabs({
  topic,
  owner,
  defaultActiveKey = 'topic',
}: {
  topic: ReactNode;
  owner: ReactNode;
  defaultActiveKey?: GeoBoardDimension;
}) {
  return (
    <Tabs
      defaultActiveKey={defaultActiveKey}
      items={[
        { key: 'topic', label: '话题维度', children: <div className='flex flex-col gap-4'>{topic}</div> },
        { key: 'owner', label: '负责人维度', children: <div className='flex flex-col gap-4'>{owner}</div> },
      ]}
    />
  );
}
