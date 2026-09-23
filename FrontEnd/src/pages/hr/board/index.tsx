import { useState } from 'react';
import { Card, Tabs } from 'antd';
import BoardAnalyticsPanel from './BoardAnalyticsPanel';
import JobDetailPanel from './JobDetailPanel';

export default function HrBoardPage() {
  const [tab, setTab] = useState('jobs');

  return (
    <div className='flex flex-col gap-3 p-4'>
      <Card
        size='small'
        styles={{ body: { paddingTop: 8, paddingBottom: 8 } }}
      >
        <Tabs
          activeKey={tab}
          onChange={setTab}
          items={[
            { key: 'jobs', label: '岗位明细' },
            { key: 'analytics', label: '招聘看板' },
          ]}
        />
      </Card>
      {tab === 'jobs' ? <JobDetailPanel /> : <BoardAnalyticsPanel />}
    </div>
  );
}
