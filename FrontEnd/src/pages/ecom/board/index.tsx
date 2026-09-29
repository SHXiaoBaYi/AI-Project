import { memo, useEffect, useState } from 'react';
import { App, Button, Card, DatePicker, Input, Select, Table } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { compareEcomBoardApi, listEcomBoardApi, listEcomShopsApi, persistEcomBoardApi } from '@/api/ecom';

const BoardPage = memo(function BoardPage() {
  const { message } = App.useApp();
  const [periodType, setPeriodType] = useState('week');
  const [platform, setPlatform] = useState<string | undefined>();
  const [shopId, setShopId] = useState<number | undefined>();
  const [shops, setShops] = useState<Record<string, unknown>[]>([]);
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>([dayjs().subtract(60, 'day'), dayjs()]);
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [compareKey, setCompareKey] = useState('');
  const [compareRows, setCompareRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void listEcomShopsApi().then((list) => setShops(list ?? []));
  }, []);

  const reload = async () => {
    setLoading(true);
    try {
      const data = await listEcomBoardApi({
        periodType,
        platform,
        shopId,
        startDate: range?.[0]?.format('YYYY-MM-DD'),
        endDate: range?.[1]?.format('YYYY-MM-DD'),
      });
      setRows(data ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void reload();
  }, [periodType, platform, shopId, range]);

  return (
    <div className='space-y-4'>
      <Card
        title='数据看板'
        size='small'
      >
        <div className='mb-4 flex flex-wrap gap-2'>
          <Select
            className='w-28'
            value={periodType}
            options={[
              { value: 'week', label: '周' },
              { value: 'month', label: '月' },
            ]}
            onChange={setPeriodType}
          />
          <Select
            allowClear
            className='w-32'
            placeholder='平台'
            value={platform}
            options={[
              { value: 'jd', label: '京东' },
              { value: 'tmall', label: '天猫' },
              { value: 'douyin', label: '抖音' },
            ]}
            onChange={setPlatform}
          />
          <Select
            allowClear
            className='w-52'
            placeholder='店铺'
            value={shopId}
            options={shops.map((s) => ({ value: Number(s.id), label: String(s.shop_name || s.shop_code) }))}
            onChange={setShopId}
          />
          <DatePicker.RangePicker
            value={range}
            onChange={(v) => setRange(v as [Dayjs, Dayjs] | null)}
          />
          <Button
            onClick={() => {
              void persistEcomBoardApi({
                periodType,
                shopId,
                startDate: range?.[0]?.format('YYYY-MM-DD'),
                endDate: range?.[1]?.format('YYYY-MM-DD'),
              }).then((res) => {
                message.success(`落库快照 ${res.snapshotCount} 条`);
                void reload();
              });
            }}
          >
            重算落库
          </Button>
        </div>
        <Table
          size='small'
          rowKey='id'
          loading={loading}
          dataSource={rows}
          scroll={{ x: 1000 }}
          pagination={false}
          columns={[
            { title: '周期', dataIndex: 'period_label', width: 140 },
            { title: '键', dataIndex: 'period_key', width: 110 },
            { title: '平台', dataIndex: 'platform', width: 80 },
            { title: '店铺', dataIndex: 'shop_name', width: 120, render: (v, r) => v || r.shop_id },
            { title: '成交金额', dataIndex: 'gmv', width: 120 },
            { title: '成交单量', dataIndex: 'order_cnt', width: 100 },
            { title: '成交客户', dataIndex: 'buyer_cnt', width: 100 },
            { title: '访客', dataIndex: 'visitor_cnt', width: 100 },
            { title: '退款', dataIndex: 'refund_amt', width: 100 },
          ]}
        />
      </Card>

      <Card
        title='跨平台对比'
        size='small'
      >
        <div className='mb-3 flex flex-wrap gap-2'>
          <Input
            className='w-48'
            placeholder='periodKey 如 2026-W39'
            value={compareKey}
            onChange={(e) => setCompareKey(e.target.value)}
          />
          <Button
            type='primary'
            onClick={() => {
              if (!compareKey.trim()) {
                message.warning('请输入 periodKey');
                return;
              }
              void compareEcomBoardApi(periodType, compareKey.trim()).then((data) => setCompareRows(data ?? []));
            }}
          >
            对比
          </Button>
        </div>
        <Table
          size='small'
          rowKey='platform'
          dataSource={compareRows}
          pagination={false}
          columns={[
            { title: '平台', dataIndex: 'platform' },
            { title: '成交金额', dataIndex: 'gmv' },
            { title: '成交单量', dataIndex: 'order_cnt' },
            { title: '成交客户', dataIndex: 'buyer_cnt' },
            { title: '访客', dataIndex: 'visitor_cnt' },
            { title: '退款', dataIndex: 'refund_amt' },
          ]}
        />
      </Card>
    </div>
  );
});

export default BoardPage;
