import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Drawer, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { GeoDailySummaryDate, GeoDailySummaryPlatform, GeoDailySummaryTopic, GeoDailyVO } from '@/types/geo';
import GeoScreenshot from '@/components/geo/GeoScreenshot';

/** 用日监测原始记录组装汇总（含排名/链接等明细字段） */
export function buildDailySummaryFromRecords(records: GeoDailyVO[] = []): GeoDailySummaryDate[] {
  const byDate = new Map<string, Map<string, GeoDailySummaryTopic>>();

  for (const row of records) {
    const date = row.inspectDate;
    if (!date) continue;
    let topics = byDate.get(date);
    if (!topics) {
      topics = new Map();
      byDate.set(date, topics);
    }
    const topicKey = `${row.topicId || 0}||${row.keyword || ''}||${row.termType || '日巡查'}`;
    let topic = topics.get(topicKey);
    if (!topic) {
      topic = {
        topicId: row.topicId,
        topicName: row.topicName || '未命名话题',
        keyword: row.keyword,
        termType: row.termType || '日巡查',
        ownerName: row.ownerName,
        platforms: [],
      };
      topics.set(topicKey, topic);
    } else if (!topic.ownerName && row.ownerName) {
      topic.ownerName = row.ownerName;
    }
    topic.platforms.push({
      id: row.id,
      platform: row.platform,
      mentioned: row.mentioned,
      rankNo: row.rankNo,
      recommendStatus: row.recommendStatus,
      thirdPartyUrl: row.thirdPartyUrl,
      competitors: row.competitors,
      negativeContent: row.negativeContent,
      screenshotUrl: row.screenshotUrl,
    });
  }

  return [...byDate.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([inspectDate, topics]) => ({
      inspectDate,
      topics: [...topics.values()],
    }));
}

function hasDetailFields(groups?: GeoDailySummaryDate[]) {
  return !!groups?.some((g) =>
    g.topics?.some((t) =>
      t.platforms?.some(
        (p) =>
          p.mentioned != null ||
          p.rankNo != null ||
          !!p.recommendStatus ||
          !!p.thirdPartyUrl ||
          !!p.competitors ||
          !!p.negativeContent ||
          !!p.screenshotUrl,
      ),
    ),
  );
}

function resolveUrl(url?: string) {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  if (url.startsWith('/')) return url;
  return `https://${url}`;
}

export function GeoDailySummaryBoard({
  groups,
  records,
  activeDate,
  onActiveDateChange,
  dimension = 'topic',
}: {
  groups?: GeoDailySummaryDate[];
  /** 日监测原始记录（优先用于话题维度汇总明细展示） */
  records?: GeoDailyVO[];
  activeDate?: string;
  onActiveDateChange?: (date: string) => void;
  dimension?: 'topic' | 'owner';
}) {
  const data = useMemo(() => {
    // 优先使用接口汇总（避免再拉全量列表）；无明细时再回退 records 组装
    if (hasDetailFields(groups)) {
      return groups || [];
    }
    if (dimension !== 'owner' && records && records.length > 0) {
      return buildDailySummaryFromRecords(records);
    }
    return groups || [];
  }, [groups, records, dimension]);

  const [innerKey, setInnerKey] = useState<string>();
  const [iframeUrl, setIframeUrl] = useState<string>();

  useEffect(() => {
    if (!data.length) {
      setInnerKey(undefined);
      return;
    }
    const next =
      (activeDate && data.some((g) => g.inspectDate === activeDate) && activeDate) ||
      (innerKey && data.some((g) => g.inspectDate === innerKey) && innerKey) ||
      data[0].inspectDate;
    if (next !== innerKey) setInnerKey(next);
    if (next && next !== activeDate) onActiveDateChange?.(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const currentKey = innerKey && data.some((g) => g.inspectDate === innerKey) ? innerKey : data[0]?.inspectDate;
  const activeGroup = useMemo(() => data.find((g) => g.inspectDate === currentKey), [data, currentKey]);

  const handleTabChange = (key: string) => {
    setInnerKey(key);
    onActiveDateChange?.(key);
  };

  const platformColumns: ColumnsType<GeoDailySummaryPlatform> = useMemo(() => {
    const base: ColumnsType<GeoDailySummaryPlatform> = [
      {
        title: '平台',
        dataIndex: 'platform',
        width: 88,
        ellipsis: true,
        render: (v) => <span className='px-1 font-medium'>{v}</span>,
      },
    ];
    if (dimension === 'owner') {
      base.push(
        {
          title: '话题',
          dataIndex: 'topicName',
          width: 100,
          ellipsis: true,
          render: (v) => v || '-',
        },
        {
          title: '关键字',
          dataIndex: 'keyword',
          width: 120,
          ellipsis: true,
          render: (v) => v || '-',
        },
      );
    }
    base.push(
      {
        title: '提及',
        dataIndex: 'mentioned',
        width: 64,
        render: (v) => (Number(v) ? <Tag color='success'>是</Tag> : <Tag>否</Tag>),
      },
      {
        title: '排名',
        dataIndex: 'rankNo',
        width: 56,
        render: (v) => (v == null || v === '' ? '-' : v),
      },
      {
        title: '推荐状态',
        dataIndex: 'recommendStatus',
        width: 100,
        ellipsis: true,
        render: (v) => v || '-',
      },
      {
        title: '第三方链接',
        dataIndex: 'thirdPartyUrl',
        width: 160,
        ellipsis: true,
        render: (v?: string) =>
          v ? (
            <Typography.Link
              ellipsis
              title={v}
              className='max-w-full'
              onClick={() => setIframeUrl(resolveUrl(v))}
            >
              {v}
            </Typography.Link>
          ) : (
            '-'
          ),
      },
      {
        title: '竞品',
        dataIndex: 'competitors',
        width: 100,
        ellipsis: true,
        render: (v) => v || '-',
      },
      {
        title: '负面内容',
        dataIndex: 'negativeContent',
        width: 120,
        ellipsis: true,
        render: (v) => v || '-',
      },
      {
        title: '截图',
        dataIndex: 'screenshotUrl',
        width: 72,
        render: (v?: string) => (
          <GeoScreenshot
            src={v}
            width={40}
          />
        ),
      },
    );
    return base;
  }, [dimension]);

  const topicColumns: ColumnsType<GeoDailySummaryTopic> = useMemo(() => {
    const renderPlatformTable = (row: GeoDailySummaryTopic, rowKey: string) => (
      <div className='geo-nested-platform-scroll'>
        <Table<GeoDailySummaryPlatform>
          size='small'
          bordered
          pagination={false}
          tableLayout='fixed'
          rowKey={(r) => `${rowKey}-${r.platform}-${r.id ?? ''}-${r.keyword ?? ''}`}
          columns={platformColumns}
          dataSource={row.platforms}
          className='geo-nested-platform-table bg-white'
        />
      </div>
    );

    if (dimension === 'owner') {
      return [
        {
          title: '负责人',
          dataIndex: 'ownerName',
          width: 120,
          ellipsis: true,
          render: (v, row) => <span className='px-2 font-medium'>{v || row.topicName || '未指定'}</span>,
        },
        {
          title: '各平台监测',
          dataIndex: 'platforms',
          render: (_, row) => renderPlatformTable(row, `${currentKey}-${row.ownerName}-${row.topicName}`),
        },
      ];
    }
    return [
      {
        title: '话题',
        dataIndex: 'topicName',
        width: 120,
        ellipsis: true,
        render: (v) => <span className='px-2 font-medium'>{v}</span>,
      },
      {
        title: '关键字',
        dataIndex: 'keyword',
        width: 160,
        ellipsis: true,
        render: (v) => v || '-',
      },
      {
        title: '长短词',
        dataIndex: 'termType',
        width: 80,
        render: (v) => v || '日巡查',
      },
      {
        title: '负责人',
        dataIndex: 'ownerName',
        width: 96,
        ellipsis: true,
        render: (v) => v || '-',
      },
      {
        title: '各平台监测',
        dataIndex: 'platforms',
        render: (_, row) => renderPlatformTable(row, `${currentKey}-${row.topicId}-${row.keyword}`),
      },
    ];
  }, [currentKey, platformColumns, dimension]);

  if (!data.length) {
    return (
      <Card title={dimension === 'owner' ? '日报汇总（负责人维度）' : '日报汇总'}>
        <div className='py-8 text-center text-neutral-400'>
          当前筛选范围内暂无汇总数据，请调整日期范围或先在「日监测」录入数据
        </div>
      </Card>
    );
  }

  return (
    <>
      <Card
        title={dimension === 'owner' ? '日报汇总（负责人维度 · 日期 Tab）' : '日报汇总（话题维度 · 日期 Tab）'}
        extra={<span className='text-sm text-neutral-500'>当前：{currentKey || '-'}</span>}
      >
        <Tabs
          type='card'
          activeKey={currentKey}
          onChange={handleTabChange}
          items={data.map((group) => ({
            key: group.inspectDate,
            label: `${group.inspectDate}（${group.topics?.length || 0}）`,
          }))}
        />
        <div className='mb-2 text-sm text-neutral-500'>
          已切换到 <b>{activeGroup?.inspectDate}</b>，共 {activeGroup?.topics?.length || 0}{' '}
          {dimension === 'owner' ? '位负责人' : '个话题'}；下方提及率/首位提及率/推荐次数为完整日期范围
        </div>
        <Table<GeoDailySummaryTopic>
          key={`summary-table-${dimension}-${currentKey}`}
          size='small'
          bordered
          pagination={false}
          tableLayout='fixed'
          scroll={{ y: 300 }}
          rowKey={(r) => `${currentKey}-${r.topicId}-${r.keyword}-${r.topicName}-${r.ownerName}`}
          columns={topicColumns}
          dataSource={[...(activeGroup?.topics || [])]}
        />
      </Card>

      <Drawer
        title='第三方页面'
        width='70%'
        open={!!iframeUrl}
        onClose={() => setIframeUrl(undefined)}
        destroyOnHidden
        extra={
          iframeUrl ? (
            <Button
              type='link'
              href={iframeUrl}
              target='_blank'
              rel='noreferrer'
            >
              新窗口打开
            </Button>
          ) : null
        }
      >
        {iframeUrl ? (
          <iframe
            title='geo-link-preview'
            src={iframeUrl}
            className='h-[75vh] w-full border-0'
            sandbox='allow-scripts allow-same-origin allow-popups allow-forms'
          />
        ) : null}
      </Drawer>
    </>
  );
}
