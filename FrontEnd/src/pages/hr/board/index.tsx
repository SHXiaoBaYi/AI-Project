import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Card, Tabs } from 'antd';
import BoardAnalyticsPanel from './BoardAnalyticsPanel';
import JobDetailPanel from './JobDetailPanel';

export default function HrBoardPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab') === 'analytics' ? 'analytics' : 'jobs';
  const [tab, setTab] = useState(initialTab);
  const initialOwnerUserIds = useMemo(() => {
    const multi = searchParams.getAll('ownerUserIds').flatMap((raw) => raw.split(','));
    const single = searchParams.get('ownerUserId');
    const raws = [...multi, ...(single ? [single] : [])];
    const ids = raws.map((raw) => Number(raw)).filter((n) => Number.isFinite(n) && n > 0);
    return [...new Set(ids)];
  }, [searchParams]);

  return (
    <div className='flex flex-col gap-3 p-4'>
      <Card
        size='small'
        styles={{ body: { paddingTop: 8, paddingBottom: 8 } }}
      >
        <Tabs
          activeKey={tab}
          onChange={(key) => {
            setTab(key);
            const next = new URLSearchParams(searchParams);
            if (key === 'analytics') next.set('tab', 'analytics');
            else next.delete('tab');
            setSearchParams(next, { replace: true });
          }}
          items={[
            { key: 'jobs', label: '岗位明细' },
            { key: 'analytics', label: '招聘看板' },
          ]}
        />
      </Card>
      {tab === 'jobs' ? (
        <JobDetailPanel initialOwnerUserIds={initialOwnerUserIds} />
      ) : (
        <BoardAnalyticsPanel initialOwnerUserIds={initialOwnerUserIds} />
      )}
    </div>
  );
}
