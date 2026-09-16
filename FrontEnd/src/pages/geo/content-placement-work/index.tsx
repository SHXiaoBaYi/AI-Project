import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { Progress, Tag } from 'antd';
import { useSelector } from 'react-redux';
import type { RootState } from '@/store';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PlacementExecutionPanel from '@/components/geo/content-placement/PlacementExecutionPanel';
import { AGG_COLOR } from '@/components/geo/content-placement/constants';
import { getGeoContentPlacementListApi, getGeoTopicOptionsApi } from '@/api/geo';
import type { GeoContentPlacementListItem, GeoTopic } from '@/types/geo';
import { GEO_CONTENT_AGG_STATUS } from '@/constants/geo';

/** 一线视角：维护本人负责的话题/目标问题在各平台的投放详情与引用 */
const ContentPlacementWorkPage = memo(function ContentPlacementWorkPage() {
  const actionRef = useRef<ActionType>(null);
  const currentUserId = useSelector((state: RootState) => state.user.userInfo?.userId);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentPlacement, setCurrentPlacement] = useState<GeoContentPlacementListItem | null>(null);

  useEffect(() => {
    void getGeoTopicOptionsApi().then(setTopics);
  }, []);

  const topicOptions = topics.map((t) => ({ label: t.topicName, value: t.id }));

  const openWorkDrawer = (record: GeoContentPlacementListItem) => {
    setCurrentPlacement(record);
    setDrawerOpen(true);
  };

  const columns: ProColumnType<GeoContentPlacementListItem>[] = [
    {
      title: '话题',
      dataIndex: 'topicId',
      width: 140,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: topicOptions,
        allowClear: true,
      },
      hideInForm: true,
      render: (_, r) => r.topicName || '-',
    },
    {
      title: '目标问题',
      dataIndex: 'targetQuestion',
      ellipsis: true,
      width: 280,
      hideInForm: true,
    },
    {
      title: '发布人',
      dataIndex: 'publisherName',
      width: 110,
      search: false,
      hideInForm: true,
      render: (v) => v || '待分配',
    },
    {
      title: '归属人',
      dataIndex: 'ownerName',
      width: 110,
      search: false,
      hideInForm: true,
      render: (v) => v || '待分配',
    },
    {
      title: '投放进度',
      dataIndex: 'aggregateStatus',
      width: 180,
      valueType: 'select',
      valueEnum: Object.fromEntries(GEO_CONTENT_AGG_STATUS.map((s) => [s.value, { text: s.label }])),
      fieldProps: { options: [...GEO_CONTENT_AGG_STATUS], allowClear: true },
      hideInForm: true,
      render: (_, record) => {
        const status = record.aggregateStatus || '未投放';
        if (record.publishProgress == null || !record.platformCount) {
          return <Tag color={AGG_COLOR[status] || 'default'}>{status}</Tag>;
        }
        return (
          <div className='flex min-w-[140px] flex-col gap-1'>
            <Tag color={AGG_COLOR[status] || 'default'}>{status}</Tag>
            <Progress
              percent={record.publishProgress}
              size='small'
              format={(p) => `${record.successCount || 0}/${record.platformCount} (${p}%)`}
            />
          </div>
        );
      },
    },
    {
      title: '引用数',
      dataIndex: 'citeCount',
      width: 80,
      search: false,
      hideInForm: true,
      render: (v) => v ?? 0,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      hideInForm: true,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'work',
              label: '维护投放/引用',
              perm: 'geo:content:work',
              onClick: () => openWorkDrawer(record),
            },
          ]}
        />
      ),
    },
  ];

  return (
    <>
      <BaseProTable<GeoContentPlacementListItem>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='我的投放'
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          <span
            key='hint'
            className='text-sm text-neutral-500'
          >
            仅显示发布人或归属人为当前账号的任务
          </span>,
        ]}
        params={{ relatedUserId: currentUserId }}
        request={async (params) => {
          if (!currentUserId) {
            return { data: [], success: true, total: 0 };
          }
          const res = await getGeoContentPlacementListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            relatedUserId: currentUserId,
            topicId: params.topicId,
            targetQuestion: params.targetQuestion,
            aggregateStatus: params.aggregateStatus,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
      />

      <PlacementExecutionPanel
        open={drawerOpen}
        placement={currentPlacement}
        mode='edit'
        editPerm='geo:content:work'
        onClose={() => {
          setDrawerOpen(false);
          setCurrentPlacement(null);
          actionRef.current?.reload();
        }}
        onChanged={(next) => {
          setCurrentPlacement((prev) => (prev ? { ...prev, ...next } : prev));
        }}
      />
    </>
  );
});

export default ContentPlacementWorkPage;
