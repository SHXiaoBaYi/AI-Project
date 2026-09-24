import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Modal, Progress, Tag, Upload } from 'antd';
import { InboxOutlined, DownloadOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { ExternalLinkText } from '@/components/ExternalLinkDrawer';
import GeoScreenshot from '@/components/geo/GeoScreenshot';
import {
  deleteGeoDailyApi,
  deleteGeoDailyBatchApi,
  getGeoDailyListApi,
  getGeoOwnerOptionsApi,
  getGeoPlatformsApi,
  getGeoTopicOptionsApi,
  downloadGeoDailyTemplateApi,
  waitGeoImportJob,
  startGeoDailyImportApi,
  getGeoDailyImportProgressApi,
} from '@/api/geo';
import type { GeoDailyVO, GeoOwnerOption, GeoTopic } from '@/types/geo';
import { formatDateTime, toDateTimeParam } from '@/utils/datetime';
import EditDailyModal from './components/EditDailyModal';
import AddDailyDrawer from './components/AddDailyDrawer';
import { GEO_TERM_TYPES } from '@/constants/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const RECOMMEND_OPTIONS = ['未出现', '出现且推荐', '出现未推荐'].map((v) => ({ label: v, value: v }));

const DailyPage = memo(function DailyPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [platforms, setPlatforms] = useState<string[]>([]);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<GeoDailyVO | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importPercent, setImportPercent] = useState(0);
  const [importText, setImportText] = useState('');
  const [file, setFile] = useState<File | null>(null);

  const refreshMeta = () => {
    getGeoTopicOptionsApi().then(setTopics);
    getGeoPlatformsApi().then(setPlatforms);
    getGeoOwnerOptionsApi().then(setOwners);
  };

  useEffect(() => {
    refreshMeta();
  }, []);

  const openEdit = (record: GeoDailyVO) => {
    setEditing(record);
    setEditOpen(true);
  };

  const openAdd = async () => {
    if (!platforms.length) {
      const list = await getGeoPlatformsApi();
      setPlatforms(list);
    }
    setAddOpen(true);
  };

  const columns: ProColumnType<GeoDailyVO>[] = [
    {
      title: '巡查日期',
      dataIndex: 'inspectDate',
      valueType: 'dateRange',
      width: 120,
      render: (_, r) => r.inspectDate,
    },
    {
      title: '平台',
      dataIndex: 'platform',
      width: 90,
      valueType: 'select',
      fieldProps: {
        mode: 'multiple',
        maxTagCount: 'responsive',
        options: platforms.map((p) => ({ label: p, value: p })),
      },
      search: {
        transform: (value) => ({ platforms: value }),
      },
    },
    {
      title: '话题',
      dataIndex: 'topicId',
      width: 120,
      valueType: 'select',
      fieldProps: { options: topics.map((t) => ({ label: t.topicName, value: t.id })) },
      render: (_, r) => r.topicName,
    },
    { title: '关键字', dataIndex: 'keyword', ellipsis: true, width: 220 },
    {
      title: '话题类型',
      dataIndex: 'termType',
      width: 100,
      valueType: 'select',
      valueEnum: {
        日巡查: { text: '日巡查' },
        周巡查: { text: '周巡查' },
      },
      fieldProps: { options: [...GEO_TERM_TYPES] },
      render: (_, r) => r.termType || '日巡查',
    },
    {
      title: '负责人',
      dataIndex: 'ownerUserId',
      width: 120,
      ellipsis: true,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: owners.map((u) => ({ label: u.displayName, value: u.userId })),
      },
      render: (_, r) => r.ownerName || '-',
    },
    {
      title: '露出',
      dataIndex: 'mentioned',
      width: 80,
      valueType: 'select',
      valueEnum: { 1: { text: '是', status: 'Success' }, 0: { text: '否', status: 'Default' } },
    },
    {
      title: '排名',
      dataIndex: 'rankNo',
      width: 80,
      valueType: 'digitRange',
      fieldProps: { precision: 0, min: 1 },
      search: {
        transform: (value) => ({
          rankNoMin: value?.[0],
          rankNoMax: value?.[1],
        }),
      },
      render: (_, r) => r.rankNo ?? '-',
    },
    {
      title: '推荐状态',
      dataIndex: 'recommendStatus',
      width: 120,
      valueType: 'select',
      fieldProps: { options: RECOMMEND_OPTIONS },
    },
    {
      title: '截图',
      dataIndex: 'screenshotUrl',
      width: 80,
      valueType: 'select',
      valueEnum: {
        1: { text: '有截图' },
        0: { text: '无截图' },
      },
      search: {
        transform: (value) => ({ hasScreenshot: value }),
      },
      render: (_, r) => (
        <GeoScreenshot
          src={r.screenshotUrl}
          trigger='link'
        />
      ),
    },
    {
      title: '第三方链接',
      dataIndex: 'thirdPartyUrl',
      width: 220,
      ellipsis: true,
      valueType: 'select',
      valueEnum: {
        1: { text: '有链接' },
        0: { text: '无链接' },
      },
      search: {
        transform: (value) => ({ hasThirdPartyUrl: value }),
      },
      render: (_, r) => (
        <ExternalLinkText
          href={r.thirdPartyUrl}
          drawerTitle='第三方页面'
        />
      ),
    },
    {
      title: '竞品',
      dataIndex: 'competitors',
      ellipsis: true,
      width: 140,
      fieldProps: { placeholder: '竞品关键字' },
    },
    {
      title: '负面/错误',
      dataIndex: 'negativeContent',
      ellipsis: true,
      width: 180,
      search: false,
      render: (_, r) => r.negativeContent || '—',
    },
    {
      title: '统计标记',
      dataIndex: 'boardLocked',
      width: 100,
      valueType: 'select',
      valueEnum: {
        1: { text: '已统计', status: 'Warning' },
        0: { text: '未统计', status: 'Default' },
      },
      render: (_, r) => (r.boardLocked === 1 ? <Tag color='warning'>已统计</Tag> : <Tag>未统计</Tag>),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      valueType: 'dateTimeRange',
      width: 180,
      render: (_, r) => formatDateTime(r.updateTime),
      search: {
        transform: (value) => ({
          updateTimeStart: toDateTimeParam(value?.[0]),
          updateTimeEnd: toDateTimeParam(value?.[1]),
        }),
      },
    },
    createTimeRangeColumn<GeoDailyVO>(),
    createTimeDisplayColumn<GeoDailyVO>(),
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      search: false,
      render: (_, record) => {
        const locked = record.boardLocked === 1;
        return (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: locked ? '已统计' : '编辑',
                perm: 'geo:daily:edit',
                disabled: locked,
                onClick: () => openEdit(record),
              },
              {
                key: 'delete',
                label: '删除',
                perm: 'geo:daily:delete',
                disabled: locked,
                confirmTitle: '确定删除？删除后不再计入看板统计。',
                onClick: async () => {
                  await deleteGeoDailyApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        );
      },
    },
  ];

  return (
    <>
      <BaseProTable<GeoDailyVO>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='日监测数据'
        scroll={{ x: 1800 }}
        request={async (params) => {
          const range = params.inspectDate as string[] | undefined;
          const res = await getGeoDailyListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            startDate: range?.[0],
            endDate: range?.[1],
            topicId: params.topicId,
            keyword: params.keyword,
            termType: params.termType,
            ownerUserId: params.ownerUserId,
            platforms: params.platforms,
            mentioned: params.mentioned,
            rankNoMin: params.rankNoMin,
            rankNoMax: params.rankNoMax,
            recommendStatus: params.recommendStatus,
            hasScreenshot: params.hasScreenshot,
            hasThirdPartyUrl: params.hasThirdPartyUrl,
            competitors: params.competitors,
            boardLocked: params.boardLocked,
            updateTimeStart: params.updateTimeStart,
            updateTimeEnd: params.updateTimeEnd,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          currentPageKeysRef.current = new Set(res.rows.map((r) => r.id));
          return { data: res.rows, success: true, total: res.total };
        }}
        rowSelection={{
          selectedRowKeys,
          getCheckboxProps: (record) => ({ disabled: record.boardLocked === 1 }),
          onChange: (keys, _rows, { type }) => {
            if (type === 'none') {
              return setSelectedRowKeys([]);
            }
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='geo:daily:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的记录');
                return;
              }
              Modal.confirm({
                title: '批量删除日监测',
                content: `确定要删除选中的 ${selectedRowKeys.length} 条记录吗？删除后不再计入看板统计。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoDailyBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条`);
                  setSelectedRowKeys([]);
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
          <PermissionButton
            key='import'
            perm='geo:daily:import'
            onClick={() => setImportOpen(true)}
          >
            导入 Excel
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:daily:add'
            onClick={() => void openAdd()}
          >
            新增
          </PermissionButton>,
        ]}
      />

      <EditDailyModal
        open={editOpen}
        record={editing}
        topics={topics}
        platforms={platforms}
        owners={owners}
        onOpenChange={(v) => {
          setEditOpen(v);
          if (!v) setEditing(null);
        }}
        onSuccess={() => {
          refreshMeta();
          actionRef.current?.reload();
        }}
      />

      <AddDailyDrawer
        open={addOpen}
        topics={topics}
        platforms={platforms}
        owners={owners}
        onOpenChange={setAddOpen}
        onSuccess={() => {
          refreshMeta();
          actionRef.current?.reload();
        }}
      />

      <BaseModalForm
        title='导入日监测数据 Excel'
        open={importOpen}
        onOpenChange={(v) => {
          if (!v) setFile(null);
          setImportOpen(v);
        }}
        submitter={false}
      >
        <div className='mb-3 flex flex-wrap items-center gap-2'>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => {
              downloadGeoDailyTemplateApi().catch(() => message.error('模板下载失败'));
            }}
          >
            下载导入模板
          </Button>
          <span className='text-sm text-neutral-400'>
            黄底红字为必填；橙色行为模板示例，导入时会自动跳过。工作表名是负责人。单独的月份行和重复表头也会跳过。话题对得上自动关联，对不上自动建档并关联
          </span>
        </div>
        <Upload.Dragger
          accept='.xlsx,.xls'
          maxCount={1}
          beforeUpload={(f) => {
            setFile(f);
            return false;
          }}
          onRemove={() => setFile(null)}
          fileList={file ? [{ uid: '1', name: file.name }] : []}
        >
          <p className='ant-upload-drag-icon'>
            <InboxOutlined />
          </p>
          <p>请上传与「GEO优化监测」相同的宽表。一个工作表对应一位负责人</p>
        </Upload.Dragger>
        {importing ? (
          <div className='mt-4'>
            <Progress
              percent={importPercent}
              status='active'
            />
            <div className='text-sm text-neutral-500'>{importText || '正在导入'}</div>
          </div>
        ) : null}
        <div className='mt-4 flex justify-end'>
          <Button
            type='primary'
            loading={importing}
            disabled={!file || importing}
            onClick={async () => {
              if (!file || importing) return;
              setImporting(true);
              setImportPercent(0);
              setImportText('正在上传');
              try {
                const res = await waitGeoImportJob(
                  () => startGeoDailyImportApi(file),
                  getGeoDailyImportProgressApi,
                  (job) => {
                    setImportPercent(job.percent ?? 0);
                    setImportText(job.message || '正在导入');
                  },
                );
                const head = `导入完成：新增 ${res.insertCount}，更新 ${res.updateCount}，失败 ${res.failureCount}`;
                const detail = (res.errors ?? [])
                  .slice(0, 3)
                  .map((e) => `第${e.rowIndex}行：${e.message}`)
                  .join('；');
                if (res.failureCount > 0) {
                  message.warning(detail ? `${head}。${detail}` : head);
                } else {
                  message.success(head);
                }
                setImportOpen(false);
                setFile(null);
                refreshMeta();
                actionRef.current?.reload();
              } catch (e) {
                const err = e as { geoImport?: boolean };
                if (err.geoImport) {
                  message.error(e instanceof Error ? e.message : '导入失败');
                }
              } finally {
                setImporting(false);
              }
            }}
          >
            开始导入
          </Button>
        </div>
      </BaseModalForm>
    </>
  );
});

export default DailyPage;
