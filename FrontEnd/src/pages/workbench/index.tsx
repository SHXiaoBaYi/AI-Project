import { useState, useEffect } from 'react';
import { Card, Badge, Button, Checkbox, Tag, Divider, Statistic, theme } from 'antd';
import {
  ArrowUpOutlined,
  WarningOutlined,
  DollarOutlined,
  ShoppingCartOutlined,
  ScanOutlined,
  AuditOutlined,
  EllipsisOutlined,
  RightOutlined,
  OrderedListOutlined,
  AlertOutlined,
  TrophyOutlined,
  InboxOutlined,
  SyncOutlined,
  MoneyCollectOutlined,
} from '@ant-design/icons';
import { Line, Column } from '@ant-design/charts';

const trendData = [
  { date: '06-10', value: 38500, type: '销售额' },
  { date: '06-10', value: 41200, type: '出库额' },
  { date: '06-11', value: 42300, type: '销售额' },
  { date: '06-11', value: 39800, type: '出库额' },
  { date: '06-12', value: 35600, type: '销售额' },
  { date: '06-12', value: 44500, type: '出库额' },
  { date: '06-13', value: 51200, type: '销售额' },
  { date: '06-13', value: 46800, type: '出库额' },
  { date: '06-14', value: 47800, type: '销售额' },
  { date: '06-14', value: 43200, type: '出库额' },
  { date: '06-15', value: 40100, type: '销售额' },
  { date: '06-15', value: 45100, type: '出库额' },
  { date: '06-16', value: 32450, type: '销售额' },
  { date: '06-16', value: 38900, type: '出库额' },
];

const inventoryWarnings = [
  { key: 1, sku: 'EL-DR-001', name: '电子元器件A', current: 5, safe: 20, suggest: 25 },
  { key: 2, sku: 'PK-BX-032', name: '包装箱B型', current: 12, safe: 50, suggest: 58 },
  { key: 3, sku: 'CH-LB-118', name: '润滑剂C', current: 3, safe: 15, suggest: 22 },
  { key: 4, sku: 'HD-SW-207', name: '开关总成D', current: 8, safe: 30, suggest: 32 },
  { key: 5, sku: 'FT-GK-089', name: '密封垫E', current: 18, safe: 40, suggest: 32 },
];

const topProductsData = [
  { name: '电子元器件A', sales: 1280 },
  { name: '包装箱B型', sales: 960 },
  { name: '润滑剂C', sales: 845 },
  { name: '开关总成D', sales: 720 },
  { name: '密封垫E', sales: 650 },
];

const initialTasks = [
  { id: 1, text: '审核采购入库单 #PO-20240616-003', done: false },
  { id: 2, text: '确认销售出库单 #SO-20240616-012', done: false },
  { id: 3, text: '处理库存盘点差异 (仓库A-3区)', done: false },
  { id: 4, text: '审批供应商付款申请 #PAY-20240615', done: true },
  { id: 5, text: '更新商品安全库存阈值', done: true },
];

const shortcuts = [
  { key: 'sale', label: '新建销售单', icon: <ShoppingCartOutlined /> },
  { key: 'inbound', label: '扫码入库', icon: <ScanOutlined /> },
  { key: 'check', label: '库存盘点', icon: <AuditOutlined /> },
  { key: 'more', label: '更多', icon: <EllipsisOutlined /> },
];

const alertItems = [
  {
    key: 'pending-order',
    label: '待审订单',
    count: 3,
    color: '#ff4d4f',
    suffix: '',
  },
  {
    key: 'low-stock',
    label: '库存下限',
    count: 12,
    color: '#fa8c16',
    suffix: '',
  },
  {
    key: 'overdue',
    label: '超期应收',
    count: 2,
    color: '#ff4d4f',
    suffix: '',
  },
];

function useCountUp(end: number, duration = 1200) {
  const [val, setVal] = useState(0);

  useEffect(() => {
    let raf: number;
    const start = performance.now();

    const tick = (now: number) => {
      const p = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - p, 3);
      setVal(Math.round(eased * end));
      if (p < 1) raf = requestAnimationFrame(tick);
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [end, duration]);

  return val;
}

export default function Workbench() {
  const [tasks, setTasks] = useState(initialTasks);
  const { token } = theme.useToken();

  const salesValue = useCountUp(32450);
  const stockValue = useCountUp(32767);
  const collectValue = useCountUp(89324);

  const { colorPrimary, colorPrimaryBg, colorSuccess } = token;

  const lineConfig = {
    data: trendData,
    xField: 'date',
    yField: 'value',
    colorField: 'type',
    smooth: true,
    scale: { color: { range: [colorPrimary, colorSuccess] } },
    point: { size: 3, shape: 'circle' as const },
    legend: { position: 'top' as const, offsetY: -4 },
    yAxis: {
      label: { formatter: (v: string) => `${(+v / 1000).toFixed(0)}K` },
      grid: { line: { style: { stroke: '#f0f0f0', lineDash: [4, 4] } } },
    },
    xAxis: {
      grid: { line: { style: { stroke: 'transparent' } } },
    },
    tooltip: {
      formatter: (datum: Record<string, unknown>) => ({
        name: datum.type,
        value: `￥${Number(datum.value).toLocaleString()}`,
      }),
    },
    area: {
      style: { fillOpacity: 0.08 },
    },
  };

  const columnConfig = {
    data: topProductsData,
    xField: 'name',
    yField: 'sales',
    style: { fill: colorPrimary },
    scale: { x: { paddingInner: 0.5, paddingOuter: 0.25 } },
    columnStyle: { radius: [6, 6, 0, 0] },
    label: {
      position: 'top' as const,
      style: { fill: '#ffffff', fontSize: 12, fontWeight: 500 },
    },
    xAxis: {
      label: { autoRotate: true, autoHide: false, style: { fontSize: 11 } },
      grid: { line: { style: { stroke: 'transparent' } } },
    },
    yAxis: {
      grid: { line: { style: { stroke: '#f0f0f0', lineDash: [4, 4] } } },
    },
    legend: false as const,
  };

  const handleToggle = (id: number) => {
    setTasks((prev) => prev.map((t) => (t.id === id ? { ...t, done: !t.done } : t)));
  };

  const undoneCount = tasks.filter((t) => !t.done).length;

  return (
    <div className='flex flex-col gap-4'>
      {/* ── 顶部统计卡片 ── */}
      <div className='grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4'>
        <Card>
          <div className='flex items-start gap-4'>
            <Statistic
              title={
                <div className='flex flex-wrap items-center gap-x-2'>
                  <div className='flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-blue-50'>
                    <DollarOutlined style={{ color: '#3b82f6', fontSize: '18px' }} />
                  </div>
                  <span className='font-medium text-gray-500'>今日销售额</span>
                </div>
              }
              value={salesValue}
              precision={0}
              prefix={<span className='text-base'>￥</span>}
              suffix={
                <div className='flex'>
                  <span className='ml-1 text-sm font-normal text-gray-400'>较昨日</span>
                  <span className='ml-1 text-sm font-normal text-green-500'>
                    <ArrowUpOutlined /> 8.2%
                  </span>
                </div>
              }
              styles={{ content: { fontSize: 26, fontWeight: 700 } }}
            />
          </div>
        </Card>

        <Card>
          <div className='flex items-start gap-4'>
            <Statistic
              title={
                <div className='flex flex-wrap items-center gap-x-2'>
                  <div
                    className='flex h-11 w-11 shrink-0 items-center justify-center rounded-xl'
                    style={{ backgroundColor: colorPrimaryBg }}
                  >
                    <InboxOutlined style={{ color: '#06b6d4', fontSize: '18px' }} />
                  </div>
                  <span className='font-medium text-gray-500'>库存金额</span>
                </div>
              }
              value={stockValue}
              precision={0}
              prefix={<span className='text-base'>￥</span>}
              styles={{ content: { fontSize: 26, fontWeight: 700 } }}
            />
          </div>
        </Card>

        <Card>
          <div className='flex items-start gap-4'>
            <Statistic
              title={
                <div className='flex flex-wrap items-center gap-x-2'>
                  <div className='flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-orange-50'>
                    <SyncOutlined style={{ color: '#f97316', fontSize: '18px' }} />
                  </div>
                  <span className='font-medium text-gray-500'>周转天数</span>
                </div>
              }
              value={32}
              suffix={<span className='text-sm font-normal text-gray-400'>天</span>}
              styles={{ content: { fontSize: 26, fontWeight: 700 } }}
            />
          </div>
        </Card>

        <Card>
          <div className='flex items-start gap-4'>
            <Statistic
              title={
                <div className='flex flex-wrap items-center gap-x-2'>
                  <div className='flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-purple-50'>
                    <MoneyCollectOutlined style={{ color: '#a855f7', fontSize: '18px' }} />
                  </div>
                  <span className='font-medium text-gray-500'>待回款</span>
                </div>
              }
              value={collectValue}
              precision={0}
              prefix={<span className='text-base'>￥</span>}
              styles={{ content: { fontSize: 26, fontWeight: 700 } }}
            />
          </div>
        </Card>
      </div>

      {/* ── 待处理预警 ── */}
      <Card
        size='small'
        className='rounded-xl!'
        styles={{ body: { padding: '12px 24px' } }}
      >
        <div className='flex flex-col items-start gap-4 sm:flex-row sm:items-center sm:gap-6'>
          <div className='flex shrink-0 items-center gap-2 text-sm font-medium text-gray-600'>
            <WarningOutlined style={{ color: 'var(--color-orange-400)' }} />
            <span>待处理预警</span>
          </div>
          <div className='flex flex-wrap gap-x-6 gap-y-3'>
            {alertItems.map((item) => (
              <div
                key={item.key}
                className='flex items-center gap-2'
              >
                <Badge
                  count={item.count}
                  color={item.color}
                  overflowCount={99}
                />
                <span className='text-sm text-gray-600'>
                  {item.label}
                  {item.suffix && <span className='ml-0.5 text-gray-400'>{item.suffix}</span>}
                </span>
              </div>
            ))}
          </div>
        </div>
      </Card>

      {/* ── 趋势图 + 库存预警 ── */}
      <div className='grid grid-cols-1 gap-4 lg:grid-cols-12'>
        <Card
          className='rounded-xl! lg:col-span-7'
          title={
            <div className='flex items-center gap-2 text-sm font-medium'>
              <DollarOutlined style={{ color: 'var(--color-blue-500)' }} />
              近7天销售 vs 出库趋势
            </div>
          }
        >
          <div className='h-64 sm:h-80'>
            <Line {...lineConfig} />
          </div>
        </Card>

        <Card
          className='rounded-xl! lg:col-span-5'
          title={
            <div className='flex items-center gap-2 text-sm font-medium'>
              <AlertOutlined style={{ color: 'var(--color-red-400)' }} />
              库存预警 TOP5
            </div>
          }
        >
          {/* header */}
          <div className='mb-2 flex items-center border-b border-gray-100 pb-2 text-xs text-gray-400'>
            <span className='min-w-0 flex-1'>SKU / 名称</span>
            <span className='w-14 text-center sm:w-16'>当前</span>
            <span className='w-14 text-center sm:w-16'>安全</span>
            <span className='w-14 text-center sm:w-16'>建议采购</span>
          </div>
          {/* rows */}
          {inventoryWarnings.map((item, idx) => (
            <div
              key={item.key}
              className={`flex items-center py-2.5 text-sm ${
                idx < inventoryWarnings.length - 1 ? 'border-b border-gray-50' : ''
              }`}
            >
              <div className='min-w-0 flex-1'>
                <div className='text-xs text-gray-400'>{item.sku}</div>
                <div className='truncate text-gray-700'>{item.name}</div>
              </div>
              <span className='w-14 text-center font-semibold text-red-500 sm:w-16'>{item.current}</span>
              <span className='w-14 text-center text-gray-500 sm:w-16'>{item.safe}</span>
              <span className='w-14 text-center sm:w-16'>
                <Tag color='blue'>{item.suggest}</Tag>
              </span>
            </div>
          ))}
        </Card>
      </div>

      {/* ── 待办任务 + 畅销商品 ── */}
      <div className='grid grid-cols-1 gap-4 lg:grid-cols-12'>
        <Card
          className='rounded-xl! lg:col-span-7'
          title={
            <div className='flex items-center gap-2 text-sm font-medium'>
              <OrderedListOutlined style={{ color: 'var(--color-blue-500)' }} />
              待办任务
              <Badge
                count={undoneCount}
                overflowCount={99}
              />
            </div>
          }
        >
          <div>
            {tasks.map((item) => (
              <div
                key={item.id}
                className='flex items-center justify-between gap-2 py-2.5'
              >
                <Checkbox
                  checked={item.done}
                  onChange={() => handleToggle(item.id)}
                  className='min-w-0'
                >
                  <span className={item.done ? 'text-gray-300 line-through' : 'text-gray-700'}>{item.text}</span>
                </Checkbox>
                <Button
                  type='link'
                  size='small'
                  icon={<RightOutlined />}
                  className='shrink-0'
                >
                  处理
                </Button>
              </div>
            ))}
          </div>
        </Card>

        <Card
          className='rounded-xl! lg:col-span-5'
          title={
            <div className='flex items-center gap-2 text-sm font-medium'>
              <TrophyOutlined style={{ color: 'var(--color-amber-500)' }} />
              畅销商品 TOP5
            </div>
          }
        >
          <div className='h-64 sm:h-80'>
            <Column {...columnConfig} />
          </div>
        </Card>
      </div>

      {/* ── 快捷入口 ── */}
      <Card
        size='small'
        className='rounded-xl!'
        styles={{ body: { padding: '12px 24px' } }}
      >
        <div className='flex flex-wrap items-center gap-x-4 gap-y-3'>
          <span className='shrink-0 text-sm font-medium text-gray-500'>快捷入口</span>
          <Divider
            orientation='vertical'
            className='hidden h-5! sm:block'
          />
          {shortcuts.map((s) => (
            <Button
              key={s.key}
              type={s.key === 'sale' ? 'primary' : 'default'}
              icon={s.icon}
            >
              {s.label}
            </Button>
          ))}
        </div>
      </Card>
    </div>
  );
}
