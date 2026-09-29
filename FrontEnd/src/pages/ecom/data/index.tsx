import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, DatePicker, Modal, Progress, Select, Tag, Upload } from 'antd';
import type { UploadFile } from 'antd/es/upload/interface';
import dayjs, { type Dayjs } from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import {
  archiveEcomWeekApi,
  downloadEcomTemplateApi,
  getEcomAccessApi,
  listEcomReportTypesApi,
  listEcomShopsApi,
  pageEcomFactsApi,
  startEcomImportApi,
  waitEcomImportJob,
  type EcomAccessScope,
  type EcomImportConflict,
  type EcomImportResult,
  type EcomReportType,
} from '@/api/ecom';

type FactRow = Record<string, unknown>;

const ALL_PLATFORM_OPTS = [
  { value: 'jd', label: '京东' },
  { value: 'tmall', label: '天猫' },
  { value: 'douyin', label: '抖音' },
];

const DataCenterPage = memo(function DataCenterPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const runImportRef = useRef<(file: File, opts?: { ignoreLocked?: boolean; forceUpdate?: boolean }) => Promise<void>>(
    async () => undefined,
  );
  const [types, setTypes] = useState<EcomReportType[]>([]);
  const [shops, setShops] = useState<Record<string, unknown>[]>([]);
  const [scope, setScope] = useState<EcomAccessScope | undefined>();
  const [reportType, setReportType] = useState<string>();
  const [metricKeys, setMetricKeys] = useState<string[]>([]);

  const [importOpen, setImportOpen] = useState(false);
  const [templateType, setTemplateType] = useState<string>();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [importing, setImporting] = useState(false);
  const [percent, setPercent] = useState(0);

  const [archiveOpen, setArchiveOpen] = useState(false);
  const [archiveShopId, setArchiveShopId] = useState<number | undefined>();
  const [archivePlatform, setArchivePlatform] = useState<string>();
  const [archiveAnchor, setArchiveAnchor] = useState<Dayjs>(dayjs());
  const [archiving, setArchiving] = useState(false);

  const platformOpts = useMemo(() => {
    if (!scope || scope.fullAccess || scope.allPlatforms) return ALL_PLATFORM_OPTS;
    const allow = new Set((scope.platforms || []).map((p) => p.toLowerCase()));
    const fromShops = new Set(shops.map((s) => String(s.platform).toLowerCase()));
    return ALL_PLATFORM_OPTS.filter((p) => allow.has(p.value) || fromShops.has(p.value));
  }, [scope, shops]);

  useEffect(() => {
    void (async () => {
      const [access, typeList, shopList] = await Promise.all([
        getEcomAccessApi(),
        listEcomReportTypesApi(),
        listEcomShopsApi(),
      ]);
      setScope(access?.scope);
      setTypes(typeList ?? []);
      setShops(shopList ?? []);
      if (typeList?.length) {
        setReportType(typeList[0].code);
        setTemplateType(typeList[0].code);
      }
      const plats =
        access?.scope?.fullAccess || access?.scope?.allPlatforms
          ? ALL_PLATFORM_OPTS.map((p) => p.value)
          : (access?.scope?.platforms || []).map((p) => p.toLowerCase());
      const shopPlats = [...new Set((shopList ?? []).map((s) => String(s.platform)))];
      setArchivePlatform(plats[0] || shopPlats[0] || 'jd');
    })();
  }, []);

  const grain = types.find((t) => t.code === reportType)?.grain;

  const showSkipped = useCallback(
    (conflicts: EcomImportConflict[]) => {
      if (!conflicts?.length) return;
      modal.info({
        title: `已跳过 ${conflicts.length} 条归档锁行`,
        width: 640,
        content: (
          <ul className='m-0 max-h-80 list-disc space-y-2 overflow-y-auto pl-5 text-sm'>
            {conflicts.map((c, i) => (
              <li key={`${c.bizDate}-${c.dimension}-${i}`}>
                <div className='font-medium'>
                  {c.bizDate} · {c.dimension}
                </div>
                <div className='text-red-500'>{c.reason}</div>
              </li>
            ))}
          </ul>
        ),
      });
    },
    [modal],
  );

  const showConfirm = useCallback(
    (pendingFile: File, result: EcomImportResult) => {
      const conflicts = result.lockedConflicts || [];
      const instance = modal.confirm({
        title: '部分数据已归档，无法直接覆盖',
        width: 640,
        content: (
          <div className='max-h-80 space-y-2 overflow-y-auto text-sm'>
            <p className='text-neutral-500'>
              以下记录所在日期已归档。可跳过锁行只保存未锁定数据，或强制覆盖并保持归档锁定。
            </p>
            <ul className='m-0 list-disc space-y-2 pl-5'>
              {conflicts.map((c, i) => (
                <li key={`${c.bizDate}-${c.dimension}-${i}`}>
                  <div className='font-medium'>
                    {c.bizDate} · {c.dimension}
                  </div>
                  <div className='text-red-500'>{c.reason}</div>
                </li>
              ))}
            </ul>
          </div>
        ),
        footer: () => (
          <div className='flex justify-end gap-2'>
            <Button onClick={() => instance.destroy()}>取消</Button>
            <Button
              danger
              onClick={() => {
                instance.destroy();
                void runImportRef.current(pendingFile, { forceUpdate: true });
              }}
            >
              强制覆盖
            </Button>
            <Button
              type='primary'
              onClick={() => {
                instance.destroy();
                void runImportRef.current(pendingFile, { ignoreLocked: true });
              }}
            >
              跳过锁行并导入
            </Button>
          </div>
        ),
      });
    },
    [modal],
  );

  const runImport = useCallback(
    async (file: File, opts?: { ignoreLocked?: boolean; forceUpdate?: boolean }) => {
      setImporting(true);
      setPercent(0);
      try {
        const job = await waitEcomImportJob(
          () => startEcomImportApi(file, opts),
          (j) => setPercent(j.status === 'RUNNING' ? Math.max(j.percent, 1) : 100),
        );
        if (job.status === 'FAILED') {
          message.error(job.message || '导入失败');
          return;
        }
        const result = job.result;
        if (!result) {
          message.error('导入完成但没有返回结果');
          return;
        }
        if (job.status === 'NEED_CONFIRM' || result.needConfirm) {
          showConfirm(file, result);
          return;
        }
        message.success(result.message || '导入完成');
        if ((result.skippedLockedRows || 0) > 0 && result.lockedConflicts?.length) {
          showSkipped(result.lockedConflicts);
        }
        setFileList([]);
        setImportOpen(false);
        actionRef.current?.reload();
        void listEcomShopsApi().then((list) => setShops(list ?? []));
      } catch (e) {
        message.error(e instanceof Error ? e.message : '导入失败');
      } finally {
        setImporting(false);
      }
    },
    [message, showConfirm, showSkipped],
  );

  runImportRef.current = runImport;

  const onArchive = async () => {
    setArchiving(true);
    try {
      if (!archivePlatform) {
        message.warning('请选择平台');
        return;
      }
      const res = await archiveEcomWeekApi({
        platform: archivePlatform,
        shopId: archiveShopId,
        anchorDate: archiveAnchor.format('YYYY-MM-DD'),
      });
      message.success(`已归档 ${res.periodLabel}：锁定 ${res.lockedRowCount} 行（店铺 ${res.shopCount}）`);
      setArchiveOpen(false);
      actionRef.current?.reload();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '归档失败');
    } finally {
      setArchiving(false);
    }
  };

  const cmpText = (a: unknown, b: unknown) => String(a ?? '').localeCompare(String(b ?? ''), 'zh-CN');
  const cmpNum = (a: unknown, b: unknown) => {
    const na = Number(a);
    const nb = Number(b);
    if (Number.isFinite(na) && Number.isFinite(nb)) return na - nb;
    return cmpText(a, b);
  };

  const columns: ProColumnType<FactRow>[] = useMemo(() => {
    const cols: ProColumnType<FactRow>[] = [
      {
        title: '源数据类型',
        dataIndex: 'reportType',
        hideInTable: true,
        valueType: 'select',
        initialValue: reportType,
        fieldProps: {
          options: types.map((t) => ({ value: t.code, label: t.label })),
          allowClear: false,
        },
      },
      {
        title: '源数据类型',
        dataIndex: 'report_type',
        width: 160,
        search: false,
        ellipsis: true,
        sorter: (a, b) => cmpText(a.report_type_label || a.report_type, b.report_type_label || b.report_type),
        render: (_, r) =>
          String(r.report_type_label || types.find((t) => t.code === r.report_type)?.label || r.report_type || '-'),
      },
      {
        title: '平台',
        dataIndex: 'platform',
        width: 90,
        valueType: 'select',
        fieldProps: { options: platformOpts, allowClear: true },
        sorter: (a, b) => cmpText(a.platform, b.platform),
      },
      {
        title: '店铺',
        dataIndex: 'shopId',
        width: 140,
        valueType: 'select',
        fieldProps: {
          options: shops.map((s) => ({
            value: Number(s.id),
            label: String(s.shop_name || s.shop_code),
          })),
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
        },
        sorter: (a, b) => cmpText(a.shop_name || a.shop_code, b.shop_name || b.shop_code),
        render: (_, r) => String(r.shop_name || r.shop_code || '-'),
      },
      {
        title: '业务日期',
        dataIndex: 'bizDateRange',
        valueType: 'dateRange',
        hideInTable: true,
        search: { transform: (v) => ({ startDate: v?.[0], endDate: v?.[1] }) },
      },
      {
        title: '日期',
        dataIndex: 'biz_date',
        width: 110,
        search: false,
        sorter: (a, b) => cmpText(a.biz_date, b.biz_date),
      },
      {
        title: '导入时间',
        dataIndex: 'import_time',
        width: 170,
        search: false,
        valueType: 'dateTime',
        sorter: (a, b) => cmpText(a.import_time, b.import_time),
      },
      {
        title: '导入人',
        dataIndex: 'import_by',
        width: 100,
        search: false,
        ellipsis: true,
        sorter: (a, b) => cmpText(a.import_by, b.import_by),
        render: (v) => (v ? String(v) : '-'),
      },
    ];

    if (grain === 'spu_day') {
      cols.push(
        { title: 'SPU', dataIndex: 'spu', width: 100, search: false, sorter: (a, b) => cmpText(a.spu, b.spu) },
        {
          title: '名称',
          dataIndex: 'spu_name',
          width: 160,
          ellipsis: true,
          search: false,
          sorter: (a, b) => cmpText(a.spu_name, b.spu_name),
        },
      );
    }
    if (grain === 'traffic_source') {
      cols.push({
        title: '渠道',
        width: 200,
        search: false,
        sorter: (a, b) =>
          cmpText(
            [a.channel_l1, a.channel_l2, a.channel_l3, a.channel_l4].filter(Boolean).join('/'),
            [b.channel_l1, b.channel_l2, b.channel_l3, b.channel_l4].filter(Boolean).join('/'),
          ),
        render: (_, r) => [r.channel_l1, r.channel_l2, r.channel_l3, r.channel_l4].filter(Boolean).join('/') || '-',
      });
    }
    if (grain === 'ad_plan' || grain === 'ad_effect') {
      cols.push(
        {
          title: '计划ID',
          dataIndex: 'plan_id',
          width: 100,
          search: false,
          sorter: (a, b) => cmpText(a.plan_id, b.plan_id),
        },
        {
          title: '计划',
          dataIndex: 'plan_name',
          width: 140,
          ellipsis: true,
          search: false,
          sorter: (a, b) => cmpText(a.plan_name, b.plan_name),
        },
      );
    }

    cols.push({
      title: '归档',
      dataIndex: 'stat_locked',
      width: 90,
      search: false,
      sorter: (a, b) => cmpNum(a.stat_locked, b.stat_locked),
      render: (v) => (Number(v) === 1 ? <Tag color='warning'>已归档</Tag> : <Tag>未归档</Tag>),
    });

    metricKeys.forEach((k) => {
      cols.push({
        title: k,
        width: 110,
        search: false,
        sorter: (a, b) => {
          const ma = a.metrics as Record<string, unknown> | undefined;
          const mb = b.metrics as Record<string, unknown> | undefined;
          return cmpNum(ma?.[k], mb?.[k]);
        },
        render: (_, r) => {
          const m = r.metrics as Record<string, unknown> | undefined;
          const val = m?.[k];
          return val == null ? '-' : String(val);
        },
      });
    });

    return cols;
  }, [types, shops, reportType, grain, metricKeys, platformOpts]);

  const archiveShopOpts = useMemo(
    () =>
      shops
        .filter((s) => !archivePlatform || String(s.platform) === archivePlatform)
        .map((s) => ({
          value: Number(s.id),
          label: String(s.shop_name || s.shop_code),
        })),
    [shops, archivePlatform],
  );

  return (
    <>
      <BaseProTable<FactRow>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='数据中心'
        columns={columns}
        scroll={{ x: 1200 }}
        toolBarRender={() => [
          <Button
            key='import'
            type='primary'
            onClick={() => setImportOpen(true)}
          >
            导入
          </Button>,
          <Button
            key='archive'
            onClick={() => setArchiveOpen(true)}
          >
            归档本周
          </Button>,
        ]}
        params={{ reportType }}
        request={async (params) => {
          const rt = String(params.reportType || reportType || '');
          if (!rt) return { data: [], success: true, total: 0 };
          if (rt !== reportType) setReportType(rt);
          const res = await pageEcomFactsApi({
            reportType: rt,
            shopId: params.shopId != null ? Number(params.shopId) : undefined,
            platform: params.platform ? String(params.platform) : undefined,
            startDate: params.startDate ? String(params.startDate) : undefined,
            endDate: params.endDate ? String(params.endDate) : undefined,
            pageNum: params.current,
            pageSize: params.pageSize,
          });
          const rows = res?.rows ?? [];
          const keys = new Set<string>();
          rows.forEach((r) => {
            const m = r.metrics as Record<string, unknown> | undefined;
            if (m)
              Object.keys(m)
                .slice(0, 8)
                .forEach((k) => keys.add(k));
          });
          setMetricKeys([...keys]);
          return { data: rows, success: true, total: res?.total ?? 0 };
        }}
      />

      <Modal
        title='导入数据'
        open={importOpen}
        onCancel={() => !importing && setImportOpen(false)}
        footer={null}
        destroyOnHidden
        width={560}
      >
        <p className='mb-3 text-sm text-neutral-500'>
          仅可导入已授权平台与店铺的数据（xlsx、csv）。文件名含店铺 ID 时自动建店；越权店铺将被拒绝。
        </p>
        <div className='mb-3 flex flex-wrap items-center gap-2'>
          <Select
            className='min-w-56'
            placeholder='源数据类型'
            value={templateType}
            options={types.map((t) => ({ value: t.code, label: t.label }))}
            onChange={setTemplateType}
          />
          <Button
            disabled={!templateType}
            onClick={() => {
              const t = types.find((x) => x.code === templateType);
              if (!t) return;
              void downloadEcomTemplateApi(t.code, t.label).catch(() => message.error('模板下载失败'));
            }}
          >
            下载模板
          </Button>
        </div>
        <Upload.Dragger
          accept='.xlsx,.xls,.csv'
          maxCount={1}
          fileList={fileList}
          beforeUpload={(file) => {
            setFileList([{ uid: String(Date.now()), name: file.name, status: 'done' }]);
            void runImport(file);
            return false;
          }}
          onRemove={() => setFileList([])}
          disabled={importing}
        >
          <p className='text-neutral-700'>点击或拖拽文件到此处导入</p>
        </Upload.Dragger>
        {importing || percent > 0 ? (
          <div className='mt-4'>
            <Progress
              percent={importing ? percent : 100}
              status={importing ? 'active' : 'success'}
            />
          </div>
        ) : null}
      </Modal>

      <Modal
        title='归档本周'
        open={archiveOpen}
        onCancel={() => !archiving && setArchiveOpen(false)}
        onOk={() => void onArchive()}
        confirmLoading={archiving}
        okText='确认归档'
        destroyOnHidden
      >
        <div className='flex flex-col gap-3 py-2'>
          <Select
            className='w-full'
            placeholder='平台'
            value={archivePlatform}
            options={platformOpts}
            onChange={(v) => {
              setArchivePlatform(v);
              setArchiveShopId(undefined);
            }}
          />
          <Select
            allowClear
            className='w-full'
            placeholder='全部店铺'
            value={archiveShopId}
            options={archiveShopOpts}
            onChange={setArchiveShopId}
          />
          <DatePicker
            className='w-full'
            value={archiveAnchor}
            onChange={(v) => setArchiveAnchor(v || dayjs())}
          />
          <p className='m-0 text-sm text-neutral-500'>
            以所选日期所在自然周（周一至周日）为归档区间。归档后导入冲突需跳过锁行或强制覆盖。
          </p>
        </div>
      </Modal>
    </>
  );
});

export default DataCenterPage;
