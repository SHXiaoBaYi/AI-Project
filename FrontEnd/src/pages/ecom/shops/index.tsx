import { memo, useRef } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { Alert } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import { listEcomShopsApi } from '@/api/ecom';

type ShopRow = Record<string, unknown>;

const PLATFORM_ENUM = {
  jd: { text: '京东' },
  tmall: { text: '天猫' },
  douyin: { text: '抖音' },
};

const ShopsPage = memo(function ShopsPage() {
  const actionRef = useRef<ActionType>(null);

  const columns: ProColumnType<ShopRow>[] = [
    {
      title: '平台',
      dataIndex: 'platform',
      width: 100,
      valueType: 'select',
      valueEnum: PLATFORM_ENUM,
    },
    { title: '店铺编码', dataIndex: 'shop_code', width: 160 },
    { title: '店铺名称', dataIndex: 'shop_name', ellipsis: true },
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '创建时间', dataIndex: 'create_time', width: 180, search: false, valueType: 'dateTime' },
  ];

  return (
    <div className='space-y-3'>
      <Alert
        type='info'
        showIcon
        title='店铺仅通过导入文件名自动创建（如京东 11623441），此处只读。'
      />
      <BaseProTable<ShopRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='店铺列表'
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={false}
        request={async (params) => {
          const platform = params.platform ? String(params.platform) : undefined;
          let rows = (await listEcomShopsApi(platform)) ?? [];
          const code = params.shop_code ? String(params.shop_code).trim() : '';
          const name = params.shop_name ? String(params.shop_name).trim() : '';
          if (code) rows = rows.filter((r) => String(r.shop_code || '').includes(code));
          if (name) rows = rows.filter((r) => String(r.shop_name || '').includes(name));
          return { data: rows, success: true, total: rows.length };
        }}
      />
    </div>
  );
});

export default ShopsPage;
