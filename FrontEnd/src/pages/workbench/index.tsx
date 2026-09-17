import { lazy, Suspense, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Button, Card, Col, Progress, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import {
  CalendarOutlined,
  DashboardOutlined,
  FormOutlined,
  SendOutlined,
  SettingOutlined,
  TagsOutlined,
  AppstoreOutlined,
  UserOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import dayjs from 'dayjs';
import type { RootState } from '@/store';
import { usePermission } from '@/hooks/usePermission';
import { GEO_LABEL } from '@/constants/geoLabels';
import { getGeoContentPlacementListApi, getGeoDailyBoardApi } from '@/api/geo';
import type { GeoContentPlacementListItem, GeoDailyBoard } from '@/types/geo';
import { AGG_COLOR } from '@/components/geo/content-placement/constants';
import { DEMO_DATA_END, DEMO_DATA_START } from '@/constants/demoData';

const Line = lazy(() => import('@/components/geo/GeoAntCharts').then((m) => ({ default: m.Line })));

type Shortcut = {
  key: string;
  label: string;
  desc: string;
  path: string;
  perm: string;
  icon: ReactNode;
};

const SHORTCUTS: Shortcut[] = [
  {
    key: 'daily',
    label: GEO_LABEL.daily,
    desc: '录入与查询日监测',
    path: '/geo/daily',
    perm: 'geo:daily:list',
    icon: <CalendarOutlined />,
  },
  {
    key: 'work',
    label: GEO_LABEL.contentWork,
    desc: '维护本人投放与引用',
    path: '/geo/content-placement-work',
    perm: 'geo:content:work',
    icon: <FormOutlined />,
  },
  {
    key: 'manage',
    label: GEO_LABEL.contentManage,
    desc: '目标问题生成与分配',
    path: '/geo/content-placement-manage',
    perm: 'geo:content:list',
    icon: <SendOutlined />,
  },
  {
    key: 'topic',
    label: GEO_LABEL.topic,
    desc: '话题主数据',
    path: '/geo/topic',
    perm: 'geo:topic:list',
    icon: <TagsOutlined />,
  },
  {
    key: 'platform',
    label: GEO_LABEL.platform,
    desc: 'AI/内容平台配置',
    path: '/geo/platform',
    perm: 'geo:platform:list',
    icon: <AppstoreOutlined />,
  },
  {
    key: 'yearly',
    label: GEO_LABEL.yearlyTarget,
    desc: '目标与达成',
    path: '/geo/yearly-target',
    perm: 'geo:yearly:list',
    icon: <DashboardOutlined />,
  },
  {
    key: 'user',
    label: '用户管理',
    desc: '账号与角色',
    path: '/system/user',
    perm: 'system:user:list',
    icon: <UserOutlined />,
  },
  {
    key: 'role',
    label: '角色管理',
    desc: '权限分配',
    path: '/system/role',
    perm: 'system:role:list',
    icon: <SettingOutlined />,
  },
];

function resolvePersona(has: (p: string) => boolean, roles: string[]): { title: string; hint: string } {
  if (roles.includes('admin') || has('*:*:*')) {
    return { title: '系统总览工作台', hint: '你拥有全局权限，可进入全部 GEO 与系统模块。' };
  }
  const expose = has('geo:expose:list') || has('geo:daily:list');
  const work = has('geo:content:work');
  const manage = has('geo:content:list');
  const article = has('geo:article:list');
  const config = has('geo:topic:list') || has('geo:platform:list');

  if (work && !manage && !expose) {
    return { title: '一线投放工作台', hint: '聚焦本人待投放任务与引用维护。' };
  }
  if (manage && !work && !expose) {
    return { title: '投放管理工作台', hint: '查看分配进度并进入投放管理。' };
  }
  if (expose && !work && !manage) {
    return { title: 'AI露出工作台', hint: '关注日监测录入与露出率走势。' };
  }
  if (article && !expose && !work) {
    return { title: '数据看板工作台', hint: '查看发布与收录聚合表现。' };
  }
  if (config && !expose && !work && !manage) {
    return { title: '基础配置工作台', hint: '维护话题与平台主数据。' };
  }
  return { title: '综合工作台', hint: '按你的权限展示相关看板与快捷入口。' };
}

function avgChart(points?: { value?: number }[]) {
  if (!points?.length) return null;
  const nums = points.map((p) => Number(p.value)).filter((n) => !Number.isNaN(n));
  if (!nums.length) return null;
  return Math.round((nums.reduce((a, b) => a + b, 0) / nums.length) * 10) / 10;
}

/** 按横轴聚合多平台均值为单折线 */
function aggregateByAxis(points?: { axis?: string; value?: number }[]) {
  if (!points?.length) return [];
  const map = new Map<string, number[]>();
  for (const p of points) {
    const key = p.axis || '';
    if (!key) continue;
    const arr = map.get(key) || [];
    arr.push(Number(p.value) || 0);
    map.set(key, arr);
  }
  return [...map.entries()].map(([date, vals]) => ({
    date,
    value: Math.round((vals.reduce((a, b) => a + b, 0) / vals.length) * 10) / 10,
  }));
}

export default function Workbench() {
  const navigate = useNavigate();
  const { has } = usePermission();
  const userInfo = useSelector((state: RootState) => state.user.userInfo);
  const roles = userInfo?.roles ?? [];
  const userId = userInfo?.userId;

  const persona = useMemo(() => resolvePersona(has, roles), [has, roles]);
  const shortcuts = useMemo(() => SHORTCUTS.filter((s) => has(s.perm)), [has]);

  const showExpose = has('geo:expose:list') || has('geo:daily:list') || has('geo:day:list');
  const showMyWork = has('geo:content:work');
  const showManage = has('geo:content:list');

  const [exposeLoading, setExposeLoading] = useState(false);
  const [exposeBoard, setExposeBoard] = useState<GeoDailyBoard | null>(null);
  const [myTasks, setMyTasks] = useState<GeoContentPlacementListItem[]>([]);
  const [myTasksLoading, setMyTasksLoading] = useState(false);
  const [manageStats, setManageStats] = useState({ done: 0, partial: 0, none: 0, total: 0 });
  const [manageLoading, setManageLoading] = useState(false);

  useEffect(() => {
    if (!showExpose) return;
    setExposeLoading(true);
    void getGeoDailyBoardApi({
      startDate: DEMO_DATA_START,
      endDate: DEMO_DATA_END,
    })
      .then(setExposeBoard)
      .catch(() => setExposeBoard(null))
      .finally(() => setExposeLoading(false));
  }, [showExpose]);

  useEffect(() => {
    if (!showMyWork || !userId) return;
    setMyTasksLoading(true);
    void getGeoContentPlacementListApi({
      pageNum: 1,
      pageSize: 8,
      relatedUserId: userId,
    })
      .then((res) => {
        const rows = res.rows ?? [];
        const pending = rows.filter((r) => r.aggregateStatus !== '投放完成');
        setMyTasks(pending.length ? pending : rows.slice(0, 5));
      })
      .catch(() => setMyTasks([]))
      .finally(() => setMyTasksLoading(false));
  }, [showMyWork, userId]);

  useEffect(() => {
    if (!showManage) return;
    setManageLoading(true);
    void getGeoContentPlacementListApi({ pageNum: 1, pageSize: 200 })
      .then((res) => {
        const rows = res.rows ?? [];
        setManageStats({
          total: res.total ?? rows.length,
          done: rows.filter((r) => r.aggregateStatus === '投放完成').length,
          partial: rows.filter((r) => r.aggregateStatus === '部分投放').length,
          none: rows.filter((r) => !r.aggregateStatus || r.aggregateStatus === '未投放').length,
        });
      })
      .catch(() => setManageStats({ done: 0, partial: 0, none: 0, total: 0 }))
      .finally(() => setManageLoading(false));
  }, [showManage]);

  const mentionAvg = exposeBoard?.compareSummary?.mentionRate ?? avgChart(exposeBoard?.mentionChart);
  const firstAvg = exposeBoard?.compareSummary?.firstMentionRate ?? avgChart(exposeBoard?.firstMentionChart);
  const negativeCount = exposeBoard?.negativeCount ?? 0;
  const mentionLineData = aggregateByAxis(exposeBoard?.mentionChart);

  const displayName = userInfo?.nickname || userInfo?.username || '同事';

  return (
    <div className='flex flex-col gap-4'>
      <Card size='small'>
        <div className='flex flex-wrap items-end justify-between gap-3'>
          <div>
            <Typography.Title
              level={4}
              className='!mb-1'
            >
              你好，{displayName}
            </Typography.Title>
            <Typography.Text type='secondary'>
              {persona.title} · {dayjs().format('YYYY-MM-DD dddd')} · {persona.hint}
            </Typography.Text>
          </div>
          {roles.length ? (
            <Space
              size={4}
              wrap
            >
              {roles.map((r) => (
                <Tag
                  key={r}
                  color={r === 'admin' ? 'magenta' : 'blue'}
                >
                  {r === 'admin' ? '超级管理员' : r}
                </Tag>
              ))}
            </Space>
          ) : null}
        </div>
      </Card>

      <Card
        size='small'
        title='快捷入口'
      >
        {shortcuts.length ? (
          <Row gutter={[12, 12]}>
            {shortcuts.map((s) => (
              <Col
                key={s.key}
                xs={12}
                sm={8}
                md={6}
                lg={4}
              >
                <button
                  type='button'
                  className='flex w-full cursor-pointer flex-col gap-1 rounded-lg border border-neutral-200 bg-white px-3 py-3 text-left transition hover:border-blue-400 hover:shadow-sm'
                  onClick={() => navigate(s.path)}
                >
                  <span className='text-lg text-blue-600'>{s.icon}</span>
                  <span className='text-sm font-medium text-neutral-800'>{s.label}</span>
                  <span className='text-xs text-neutral-500'>{s.desc}</span>
                </button>
              </Col>
            ))}
          </Row>
        ) : (
          <Typography.Text type='secondary'>暂无可用模块权限，请联系管理员开通菜单。</Typography.Text>
        )}
      </Card>

      <Row gutter={[16, 16]}>
        {showExpose ? (
          <Col
            xs={24}
            lg={showMyWork || showManage ? 14 : 24}
          >
            <Card
              size='small'
              loading={exposeLoading}
              title={`露出速览（近 7 日）`}
              extra={
                <Button
                  type='link'
                  className='px-0'
                  onClick={() => navigate('/geo/daily')}
                >
                  进入{GEO_LABEL.daily}
                  <RightOutlined />
                </Button>
              }
            >
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic
                    title={GEO_LABEL.mentionRate}
                    value={mentionAvg ?? '-'}
                    suffix={mentionAvg != null ? '%' : undefined}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title={GEO_LABEL.firstMentionRate}
                    value={firstAvg ?? '-'}
                    suffix={firstAvg != null ? '%' : undefined}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='负面/错误条数'
                    value={negativeCount}
                  />
                </Col>
              </Row>
              <div className='mt-4 h-52'>
                {mentionLineData.length ? (
                  <Suspense fallback={null}>
                    <Line
                      data={mentionLineData}
                      xField='date'
                      yField='value'
                      height={200}
                      smooth
                    />
                  </Suspense>
                ) : (
                  <div className='flex h-full items-center justify-center text-sm text-neutral-400'>
                    暂无露出走势数据
                  </div>
                )}
              </div>
            </Card>
          </Col>
        ) : null}

        {showMyWork ? (
          <Col
            xs={24}
            lg={showExpose ? 10 : 12}
          >
            <Card
              size='small'
              loading={myTasksLoading}
              title='我的待办投放'
              extra={
                <Button
                  type='link'
                  className='px-0'
                  onClick={() => navigate('/geo/content-placement-work')}
                >
                  {GEO_LABEL.contentWork}
                  <RightOutlined />
                </Button>
              }
            >
              <Table<GeoContentPlacementListItem>
                size='small'
                rowKey='id'
                pagination={false}
                dataSource={myTasks}
                locale={{ emptyText: '暂无待办，棒棒哒' }}
                columns={[
                  {
                    title: '目标问题',
                    dataIndex: 'targetQuestion',
                    ellipsis: true,
                  },
                  {
                    title: '进度',
                    dataIndex: 'aggregateStatus',
                    width: 100,
                    render: (v?: string) => <Tag color={AGG_COLOR[v || '未投放'] || 'default'}>{v || '未投放'}</Tag>,
                  },
                ]}
              />
            </Card>
          </Col>
        ) : null}

        {showManage ? (
          <Col
            xs={24}
            lg={showExpose || showMyWork ? 10 : 12}
          >
            <Card
              size='small'
              loading={manageLoading}
              title='投放进度概览'
              extra={
                <Button
                  type='link'
                  className='px-0'
                  onClick={() => navigate('/geo/content-placement-manage')}
                >
                  {GEO_LABEL.contentManage}
                  <RightOutlined />
                </Button>
              }
            >
              <Row gutter={12}>
                <Col span={8}>
                  <Statistic
                    title='投放完成'
                    value={manageStats.done}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='部分投放'
                    value={manageStats.partial}
                  />
                </Col>
                <Col span={8}>
                  <Statistic
                    title='未投放'
                    value={manageStats.none}
                  />
                </Col>
              </Row>
              <div className='mt-4'>
                <div className='mb-1 flex justify-between text-xs text-neutral-500'>
                  <span>完成率（本页抽样）</span>
                  <span>
                    {manageStats.done + manageStats.partial + manageStats.none
                      ? Math.round(
                          (manageStats.done / (manageStats.done + manageStats.partial + manageStats.none)) * 100,
                        )
                      : 0}
                    %
                  </span>
                </div>
                <Progress
                  percent={
                    manageStats.done + manageStats.partial + manageStats.none
                      ? Math.round(
                          (manageStats.done / (manageStats.done + manageStats.partial + manageStats.none)) * 100,
                        )
                      : 0
                  }
                  showInfo={false}
                />
                <div className='mt-2 text-xs text-neutral-400'>列表共 {manageStats.total} 条目标问题</div>
              </div>
            </Card>
          </Col>
        ) : null}
      </Row>
    </div>
  );
}
