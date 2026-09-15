import { memo, useEffect, useMemo, useState } from 'react';
import { App, Button, DatePicker, Drawer, Input, InputNumber, Modal, Select, Space, Table, Tabs, Upload } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { saveGeoDailyBulkApi, uploadGeoScreenshotApi } from '@/api/geo';
import type { GeoDailyBulkSaveResult, GeoTopic } from '@/types/geo';
import GeoScreenshot from '@/components/geo/GeoScreenshot';

const RECOMMEND_OPTIONS = ['未出现', '出现且推荐', '出现未推荐'].map((v) => ({ label: v, value: v }));

type PlatformRow = {
  platform: string;
  mentioned: number;
  rankNo?: number;
  recommendStatus?: string;
  thirdPartyUrl?: string;
  competitors?: string;
  negativeContent?: string;
  screenshotUrl?: string;
};

type TopicRow = {
  rowKey: string;
  topicId?: number;
  keyword?: string;
  ownerName?: string;
  platforms: PlatformRow[];
};

type DateTab = {
  key: string;
  inspectDate: string;
  topics: TopicRow[];
};

function uid(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function createPlatformRows(platforms: string[]): PlatformRow[] {
  return platforms.map((platform) => ({
    platform,
    mentioned: 0,
    recommendStatus: '未出现',
  }));
}

function createTopicRow(platforms: string[]): TopicRow {
  return {
    rowKey: uid('topic'),
    keyword: '',
    platforms: createPlatformRows(platforms),
  };
}

function createDateTab(inspectDate: string, platforms: string[]): DateTab {
  return {
    key: inspectDate,
    inspectDate,
    topics: [createTopicRow(platforms)],
  };
}

interface AddDailyDrawerProps {
  open: boolean;
  topics: GeoTopic[];
  platforms: string[];
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

const AddDailyDrawer = memo(function AddDailyDrawer({
  open,
  topics,
  platforms,
  onOpenChange,
  onSuccess,
}: AddDailyDrawerProps) {
  const { message, modal } = App.useApp();
  const [tabs, setTabs] = useState<DateTab[]>([]);
  const [activeKey, setActiveKey] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [addDateOpen, setAddDateOpen] = useState(false);
  const [pendingDate, setPendingDate] = useState<Dayjs>(dayjs());

  useEffect(() => {
    if (!open) return;
    const date = dayjs().format('YYYY-MM-DD');
    const first = createDateTab(date, platforms);
    setTabs([first]);
    setActiveKey(first.key);
    setPendingDate(dayjs(date));
    // 仅在打开时按当前平台列表初始化，避免 platforms 引用变化重置已填内容
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const topicOptions = useMemo(() => topics.map((t) => ({ label: t.topicName, value: t.id })), [topics]);

  const updateTopic = (tabKey: string, rowKey: string, patch: Partial<TopicRow>) => {
    setTabs((prev) =>
      prev.map((tab) =>
        tab.key !== tabKey
          ? tab
          : {
              ...tab,
              topics: tab.topics.map((row) => (row.rowKey === rowKey ? { ...row, ...patch } : row)),
            },
      ),
    );
  };

  const updatePlatform = (tabKey: string, topicKey: string, platform: string, patch: Partial<PlatformRow>) => {
    setTabs((prev) =>
      prev.map((tab) =>
        tab.key !== tabKey
          ? tab
          : {
              ...tab,
              topics: tab.topics.map((row) =>
                row.rowKey !== topicKey
                  ? row
                  : {
                      ...row,
                      platforms: row.platforms.map((p) => (p.platform === platform ? { ...p, ...patch } : p)),
                    },
              ),
            },
      ),
    );
  };

  const addTopic = (tabKey: string) => {
    setTabs((prev) =>
      prev.map((tab) => (tab.key !== tabKey ? tab : { ...tab, topics: [...tab.topics, createTopicRow(platforms)] })),
    );
  };

  const removeTopic = (tabKey: string, rowKey: string) => {
    const tab = tabs.find((t) => t.key === tabKey);
    if (!tab) return;
    if (tab.topics.length <= 1) {
      message.warning('每个日期至少保留一个话题行');
      return;
    }
    setTabs((prev) =>
      prev.map((item) =>
        item.key !== tabKey ? item : { ...item, topics: item.topics.filter((row) => row.rowKey !== rowKey) },
      ),
    );
  };

  const confirmAddDateTab = () => {
    const date = pendingDate.format('YYYY-MM-DD');
    const existed = tabs.find((t) => t.inspectDate === date);
    if (existed) {
      message.warning('该日期 Tab 已存在');
      setActiveKey(existed.key);
      setAddDateOpen(false);
      return;
    }
    const next = createDateTab(date, platforms);
    setTabs((prev) => [...prev, next]);
    setActiveKey(next.key);
    setAddDateOpen(false);
  };

  const removeDateTab = (targetKey: string) => {
    if (tabs.length <= 1) {
      message.warning('至少保留一个日期 Tab');
      return;
    }
    const idx = tabs.findIndex((t) => t.key === targetKey);
    const nextTabs = tabs.filter((t) => t.key !== targetKey);
    setTabs(nextTabs);
    if (activeKey === targetKey) {
      const fallback = nextTabs[Math.max(0, idx - 1)] ?? nextTabs[0];
      setActiveKey(fallback?.key);
    }
  };

  const buildGroups = () => {
    const groups: {
      inspectDate: string;
      topicId: number;
      keyword: string;
      ownerName?: string;
      items: PlatformRow[];
      tabKey: string;
    }[] = [];

    for (const tab of tabs) {
      for (const [index, row] of tab.topics.entries()) {
        const empty = !row.topicId && !String(row.keyword || '').trim();
        if (empty) continue;
        if (!row.topicId || !String(row.keyword || '').trim()) {
          message.error(`${tab.inspectDate} 第 ${index + 1} 个话题：请选择话题并填写关键字`);
          setActiveKey(tab.key);
          return null;
        }
        if (!row.platforms.length) {
          message.error(`${tab.inspectDate} 第 ${index + 1} 个话题：暂无可用平台，请先维护平台后再保存`);
          setActiveKey(tab.key);
          return null;
        }
        groups.push({
          inspectDate: tab.inspectDate,
          topicId: Number(row.topicId),
          keyword: String(row.keyword).trim(),
          ownerName: String(row.ownerName || '').trim() || undefined,
          items: row.platforms,
          tabKey: tab.key,
        });
      }
    }
    return groups;
  };

  const showLockedConfirm = (result: GeoDailyBulkSaveResult) => {
    const conflicts = result.lockedConflicts || [];
    modal.confirm({
      title: '部分数据已被统计，无法覆盖',
      width: 640,
      okText: '忽略此处更新',
      cancelText: '取消',
      content: (
        <div className='max-h-80 space-y-2 overflow-y-auto text-sm'>
          <p className='text-neutral-500'>
            以下记录已存在且已被周/月/年统计。可选择「忽略此处更新」跳过这些记录，其余未统计数据仍会一次性保存；取消则整单不保存。
          </p>
          <ul className='m-0 list-disc space-y-2 pl-5'>
            {conflicts.map((item) => (
              <li key={`${item.inspectDate}-${item.platform}-${item.keyword}-${item.id ?? 'new'}`}>
                <div className='font-medium text-neutral-800'>
                  {item.inspectDate} · {item.topicName || '话题'} · {item.platform} ·「{item.keyword}」
                </div>
                <div className='text-red-500'>{item.reason}</div>
              </li>
            ))}
          </ul>
        </div>
      ),
      onOk: () => submitBulk(true),
    });
  };

  const submitBulk = async (ignoreLocked: boolean) => {
    const groups = buildGroups();
    if (!groups) return;
    if (!groups.length) {
      message.warning('请至少完整填写一个话题后再保存');
      return;
    }

    setSaving(true);
    try {
      const result = await saveGeoDailyBulkApi({
        ignoreLocked,
        groups: groups.map((g) => ({
          inspectDate: g.inspectDate,
          topicId: g.topicId,
          keyword: g.keyword,
          ownerName: g.ownerName,
          items: g.items.map((item) => ({
            platform: item.platform,
            mentioned: Number(item.mentioned) ? 1 : 0,
            rankNo: item.rankNo == null ? undefined : Number(item.rankNo),
            recommendStatus: item.recommendStatus,
            thirdPartyUrl: item.thirdPartyUrl,
            competitors: item.competitors,
            negativeContent: item.negativeContent,
            screenshotUrl: item.screenshotUrl,
          })),
        })),
      });

      if (result.needConfirm && (result.lockedConflicts?.length || 0) > 0) {
        showLockedConfirm(result);
        return;
      }

      const saved = (result.insertCount || 0) + (result.updateCount || 0);
      const skipped = result.skippedLockedCount || 0;
      if (saved <= 0 && skipped > 0) {
        message.warning(`已忽略 ${skipped} 条已统计数据，本次无写入内容`);
        return;
      }
      message.success(
        `保存成功：新增 ${result.insertCount || 0}，覆盖 ${result.updateCount || 0}` +
          (skipped ? `，忽略已统计 ${skipped}` : ''),
      );
      onOpenChange(false);
      onSuccess();
    } catch {
      // 后端整单事务回滚；错误提示由请求拦截器统一给出，抽屉保持打开便于修正
    } finally {
      setSaving(false);
    }
  };

  const handleSave = () => {
    void submitBulk(false);
  };

  const buildPlatformColumns = (tabKey: string, topicKey: string): ColumnsType<PlatformRow> => [
    {
      title: '平台',
      dataIndex: 'platform',
      width: 100,
      render: (v: string) => <span className='px-2 font-medium'>{v}</span>,
    },
    {
      title: '提及',
      dataIndex: 'mentioned',
      width: 88,
      render: (v, row) => (
        <Select
          className='w-full'
          variant='borderless'
          value={Number(v) ? 1 : 0}
          options={[
            { label: '是', value: 1 },
            { label: '否', value: 0 },
          ]}
          onChange={(val) => updatePlatform(tabKey, topicKey, row.platform, { mentioned: val })}
        />
      ),
    },
    {
      title: '排名',
      dataIndex: 'rankNo',
      width: 96,
      render: (v, row) => (
        <InputNumber
          className='w-full'
          variant='borderless'
          min={0}
          step={1}
          precision={0}
          value={v}
          controls={false}
          parser={(text) => {
            const digits = String(text ?? '').replace(/[^\d]/g, '');
            return digits === '' ? ('' as unknown as number) : Number(digits);
          }}
          onChange={(val) => {
            if (val == null || val === ('' as unknown as number)) {
              updatePlatform(tabKey, topicKey, row.platform, { rankNo: undefined });
              return;
            }
            const n = Math.max(0, Math.floor(Number(val)));
            updatePlatform(tabKey, topicKey, row.platform, {
              rankNo: Number.isFinite(n) ? n : undefined,
            });
          }}
        />
      ),
    },
    {
      title: '推荐状态',
      dataIndex: 'recommendStatus',
      width: 140,
      render: (v, row) => (
        <Select
          className='w-full'
          variant='borderless'
          value={v}
          options={RECOMMEND_OPTIONS}
          onChange={(val) => updatePlatform(tabKey, topicKey, row.platform, { recommendStatus: val })}
        />
      ),
    },
    {
      title: '第三方链接',
      dataIndex: 'thirdPartyUrl',
      width: 180,
      render: (v, row) => (
        <Input
          variant='borderless'
          value={v}
          placeholder='链接'
          onChange={(e) => updatePlatform(tabKey, topicKey, row.platform, { thirdPartyUrl: e.target.value })}
        />
      ),
    },
    {
      title: '竞品',
      dataIndex: 'competitors',
      width: 140,
      render: (v, row) => (
        <Input
          variant='borderless'
          value={v}
          placeholder='竞品'
          onChange={(e) => updatePlatform(tabKey, topicKey, row.platform, { competitors: e.target.value })}
        />
      ),
    },
    {
      title: '负面内容',
      dataIndex: 'negativeContent',
      render: (v, row) => (
        <Input
          variant='borderless'
          value={v}
          placeholder='负面内容'
          onChange={(e) => updatePlatform(tabKey, topicKey, row.platform, { negativeContent: e.target.value })}
        />
      ),
    },
    {
      title: '截图',
      dataIndex: 'screenshotUrl',
      width: 120,
      render: (v, row) => (
        <Space size={4}>
          <Upload
            maxCount={1}
            accept='image/*'
            showUploadList={false}
            customRequest={async (opt) => {
              try {
                const res = await uploadGeoScreenshotApi(opt.file as File);
                updatePlatform(tabKey, topicKey, row.platform, { screenshotUrl: res.url });
                opt.onSuccess?.(res);
              } catch (e) {
                opt.onError?.(e as Error);
              }
            }}
          >
            <Button size='small'>上传</Button>
          </Upload>
          {v ? (
            <GeoScreenshot
              src={v}
              width={36}
            />
          ) : null}
        </Space>
      ),
    },
  ];

  const buildTopicColumns = (tabKey: string): ColumnsType<TopicRow> => [
    {
      title: '话题',
      dataIndex: 'topicId',
      width: 160,
      render: (v, row) => (
        <Select
          className='w-full'
          variant='borderless'
          showSearch
          optionFilterProp='label'
          placeholder='选择话题'
          value={v}
          options={topicOptions}
          onChange={(val) => updateTopic(tabKey, row.rowKey, { topicId: val })}
        />
      ),
    },
    {
      title: '关键字',
      dataIndex: 'keyword',
      width: 200,
      render: (v, row) => (
        <Input
          variant='borderless'
          placeholder='关键字 / 提问'
          value={v}
          onChange={(e) => updateTopic(tabKey, row.rowKey, { keyword: e.target.value })}
        />
      ),
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 120,
      render: (v, row) => (
        <Input
          variant='borderless'
          placeholder='与关键字绑定'
          value={v}
          onChange={(e) => updateTopic(tabKey, row.rowKey, { ownerName: e.target.value })}
        />
      ),
    },
    {
      title: '各平台监测',
      dataIndex: 'platforms',
      render: (_, row) => (
        <div className='geo-nested-platform-scroll max-w-full overflow-x-auto overflow-y-hidden'>
          <Table<PlatformRow>
            size='small'
            bordered
            pagination={false}
            tableLayout='auto'
            rowKey='platform'
            columns={buildPlatformColumns(tabKey, row.rowKey)}
            dataSource={row.platforms}
            className='geo-nested-platform-table bg-white'
            style={{ marginLeft: 0 }}
            components={{
              table: (props) => (
                <table
                  {...props}
                  style={{ ...props.style, marginLeft: 0 }}
                />
              ),
            }}
          />
        </div>
      ),
    },
    {
      title: '操作',
      width: 72,
      render: (_, row) => (
        <Button
          type='link'
          danger
          className='px-0'
          onClick={() => removeTopic(tabKey, row.rowKey)}
        >
          删除
        </Button>
      ),
    },
  ];

  return (
    <>
      <Drawer
        title='新增日监测'
        width='95%'
        open={open}
        onClose={() => onOpenChange(false)}
        destroyOnHidden
        styles={{ body: { paddingTop: 12 } }}
        extra={
          <Space>
            <Button onClick={() => onOpenChange(false)}>取消</Button>
            <Button
              type='primary'
              loading={saving}
              onClick={() => void handleSave()}
            >
              批量保存
            </Button>
          </Space>
        }
      >
        <div className='mb-3 text-sm text-neutral-500'>
          默认打开当天日期 Tab。按日期分
          Tab；每个话题一行并平铺全部平台。保存时一次性提交：未统计数据自动覆盖，已统计数据会提示并可选择忽略。
        </div>
        <Tabs
          type='editable-card'
          activeKey={activeKey}
          onChange={setActiveKey}
          onEdit={(targetKey, action) => {
            if (action === 'add') {
              setPendingDate(dayjs());
              setAddDateOpen(true);
              return;
            }
            if (action === 'remove' && typeof targetKey === 'string') {
              removeDateTab(targetKey);
            }
          }}
          items={tabs.map((tab) => ({
            key: tab.key,
            label: tab.inspectDate,
            children: (
              <div className='space-y-3'>
                <div className='flex items-center justify-between'>
                  <span className='text-sm text-neutral-600'>巡查日期：{tab.inspectDate}（一行一个话题）</span>
                  <Button
                    type='dashed'
                    onClick={() => addTopic(tab.key)}
                  >
                    添加话题行
                  </Button>
                </div>
                <div className='max-h-[calc(100vh-260px)] overflow-auto'>
                  <Table<TopicRow>
                    size='small'
                    bordered
                    pagination={false}
                    tableLayout='auto'
                    rowKey='rowKey'
                    columns={buildTopicColumns(tab.key)}
                    dataSource={tab.topics}
                  />
                </div>
              </div>
            ),
          }))}
        />
      </Drawer>

      <Modal
        title='选择巡查日期'
        open={addDateOpen}
        onCancel={() => setAddDateOpen(false)}
        onOk={confirmAddDateTab}
        destroyOnHidden
        okText='添加 Tab'
      >
        <div className='py-2'>
          <DatePicker
            className='w-full'
            value={pendingDate}
            allowClear={false}
            onChange={(v) => v && setPendingDate(v)}
          />
        </div>
      </Modal>
    </>
  );
});

export default AddDailyDrawer;
