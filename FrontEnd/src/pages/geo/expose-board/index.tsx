import { lazy, memo, Suspense, useMemo, useState } from 'react';
import { Card, Segmented, Typography } from 'antd';
import { Link, useSearchParams } from 'react-router-dom';

const DayBoardPage = lazy(() => import('@/pages/geo/day/DayBoardPanel'));
const WeeklyPage = lazy(() => import('@/pages/geo/weekly'));
const MonthlyPage = lazy(() => import('@/pages/geo/monthly'));
const YearlyPage = lazy(() => import('@/pages/geo/yearly'));

type Period = 'day' | 'week' | 'month' | 'year';

const OPTIONS: { label: string; value: Period }[] = [
  { label: '日报', value: 'day' },
  { label: '周报', value: 'week' },
  { label: '月报', value: 'month' },
  { label: '年报', value: 'year' },
];

const ExposeBoardPage = memo(function ExposeBoardPage() {
  const [params, setParams] = useSearchParams();
  const initial = (params.get('period') as Period) || 'week';
  const [period, setPeriod] = useState<Period>(OPTIONS.some((o) => o.value === initial) ? initial : 'week');

  const hint = useMemo(() => {
    switch (period) {
      case 'day':
        return '日报：近区间实时聚合；含露出率、测试问题数、排名与负面/错误内容。';
      case 'week':
        return '周报：已结束周读落库快照（同比/环比基期稳定）；当前周实时。';
      case 'month':
        return '月报：已结束月读落库快照；当前月实时；筛话题类型时按实时聚合。';
      case 'year':
        return '年报：已结束年读落库快照；筛话题类型时按实时聚合；目标配置见「全年目标」。';
      default:
        return '';
    }
  }, [period]);

  const onChange = (v: string | number) => {
    const next = v as Period;
    setPeriod(next);
    setParams({ period: next }, { replace: true });
  };

  return (
    <div className='flex flex-col gap-4'>
      <Card size='small'>
        <div className='flex flex-wrap items-center justify-between gap-3'>
          <div>
            <Typography.Text strong>露出看板</Typography.Text>
            <div className='mt-1 text-sm text-neutral-500'>{hint}</div>
          </div>
          <div className='flex flex-wrap items-center gap-3'>
            <Segmented
              value={period}
              options={OPTIONS}
              onChange={onChange}
            />
            <Link
              to='/geo/daily'
              className='text-sm'
            >
              日监测数据
            </Link>
            <Link
              to='/geo/yearly-target'
              className='text-sm'
            >
              全年目标
            </Link>
          </div>
        </div>
      </Card>
      <Suspense fallback={<Card loading />}>
        {period === 'day' ? <DayBoardPage /> : null}
        {period === 'week' ? <WeeklyPage /> : null}
        {period === 'month' ? <MonthlyPage /> : null}
        {period === 'year' ? <YearlyPage /> : null}
      </Suspense>
    </div>
  );
});

export default ExposeBoardPage;
