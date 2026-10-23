import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { GeoContentReviewCompareDrawer } from '@/components/geo/content-placement/GeoContentReviewCompareDrawer';
import { GeoContentWriteDrawer } from '@/components/geo/content-placement/GeoContentWriteDrawer';
import { aiStatusTag } from '@/components/geo/content-placement/GeoReviewAiPanel';
import { warmLakexAssetsOnIdle } from '@/components/lakex/loadLakexAssets';
import {
  batchGeoContentReviewAiApi,
  getGeoOwnerOptionsApi,
  getGeoReviewRecordsApi,
  getGeoTopicOptionsApi,
} from '@/api/geo';
import type { GeoContentReviewRow, GeoOwnerOption, GeoTopic } from '@/types/geo';

const STATUS_META: Record<string, { text: string; color: string }> = {
  pending: { text: '审核中', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已退回', color: 'error' },
};

const ReviewRecordsPage = memo(function ReviewRecordsPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [viewOpen, setViewOpen] = useState(false);
  const [viewPlacementId, setViewPlacementId] = useState<number | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);
  const [comparePlacementId, setComparePlacementId] = useState<number | null>(null);
  const [compareFocusId, setCompareFocusId] = useState<number | null>(null);
  /** 待我审时传入，对照抽屉可直接通过/退回 */
  const [compareReviewId, setCompareReviewId] = useState<number | null>(null);

  useEffect(() => {
    warmLakexAssetsOnIdle();
    void Promise.all([getGeoTopicOptionsApi(), getGeoOwnerOptionsApi()]).then(([topicOpts, ownerOpts]) => {
      setTopics(topicOpts ?? []);
      setOwners(ownerOpts ?? []);
    });
  }, []);

  const topicOptions = useMemo(() => topics.map((t) => ({ label: t.topicName, value: t.id })), [topics]);
  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);

  const columns: ProColumnType<GeoContentReviewRow>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 200,
      search: false,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'view',
              label: '查看文章',
              perm: 'geo:write:review',
              onClick: () => {
                setViewPlacementId(record.placementId);
                setViewOpen(true);
              },
            },
            {
              key: 'compare',
              label: record.myResult === 'pending' ? '审核对照' : '版本对照',
              perm: 'geo:write:review',
              onClick: () => {
                setComparePlacementId(record.placementId);
                setCompareFocusId(record.reviewId);
                setCompareReviewId(record.myResult === 'pending' ? record.reviewId : null);
                setCompareOpen(true);
              },
            },
          ]}
        />
      ),
    },
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
      ellipsis: true,
      render: (_, r) => r.topicName || '-',
    },
    {
      title: '目标问题',
      dataIndex: 'targetQuestion',
      ellipsis: true,
      width: 240,
    },
    {
      title: '正文标题',
      dataIndex: 'contentTitle',
      ellipsis: true,
      width: 200,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(STATUS_META).map(([k, v]) => [k, { text: v.text }])),
      fieldProps: { allowClear: true },
      render: (_, r) => {
        const meta = STATUS_META[r.status || 'pending'] || STATUS_META.pending;
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: 'AI预审',
      dataIndex: 'ai',
      width: 140,
      search: false,
      render: (_, r) => aiStatusTag(r.ai) || <span className='text-neutral-400'>-</span>,
    },
    {
      title: '发起人',
      dataIndex: 'submitterUserId',
      width: 120,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: userOptions,
      },
      render: (_, r) => r.submitterName || '-',
    },
    {
      title: '审核人',
      dataIndex: 'reviewerUserId',
      width: 220,
      ellipsis: true,
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        allowClear: true,
        options: userOptions,
      },
      render: (_, r) => r.reviewerSummary || '-',
    },
    {
      title: '阶段',
      dataIndex: 'stageNo',
      width: 80,
      search: false,
      render: (_, r) => (r.stageNo === 2 ? '二审' : '一审'),
    },
    {
      title: '轮次',
      dataIndex: 'roundNo',
      width: 70,
      search: false,
    },
    {
      title: '我的结果',
      dataIndex: 'myResult',
      width: 100,
      search: false,
      render: (_, r) => {
        if (!r.myResult) return '-';
        if (r.myResult === 'approved') return <Tag color='success'>已通过</Tag>;
        if (r.myResult === 'rejected') return <Tag color='error'>已退回</Tag>;
        return <Tag>待审</Tag>;
      },
    },
    {
      title: '提审时间',
      dataIndex: 'createTime',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
  ];

  return (
    <>
      <BaseProTable<GeoContentReviewRow>
        rowKey='reviewId'
        actionRef={actionRef}
        columns={columns}
        headerTitle='审核记录'
        scroll={{ x: 1540 }}
        rowSelection={{
          selectedRowKeys,
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
            key='ai-batch'
            perm='geo:write:review'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先勾选待审轮次');
                return;
              }
              Modal.confirm({
                title: '批量跑 AI 预审',
                content: `将对选中的 ${selectedRowKeys.length} 条中、你作为审核人且仍待审的轮次触发 AI 预审。`,
                onOk: async () => {
                  const n = await batchGeoContentReviewAiApi({ reviewIds: selectedRowKeys as number[] });
                  message.success(n > 0 ? `已排队 ${n} 条` : '没有可触发的轮次（需待审且你是审核人）');
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量AI预审
          </PermissionButton>,
        ]}
        request={async (params) => {
          const rows = await getGeoReviewRecordsApi({
            status: (params.status as string) || undefined,
            topicId: params.topicId != null ? Number(params.topicId) : undefined,
            targetQuestion: (params.targetQuestion as string) || undefined,
            contentTitle: (params.contentTitle as string) || undefined,
            submitterUserId: params.submitterUserId != null ? Number(params.submitterUserId) : undefined,
            reviewerUserId: params.reviewerUserId != null ? Number(params.reviewerUserId) : undefined,
          });
          currentPageKeysRef.current = new Set((rows ?? []).map((r) => r.reviewId));
          return { data: rows ?? [], success: true, total: rows?.length ?? 0 };
        }}
      />

      <GeoContentWriteDrawer
        open={viewOpen}
        placementId={viewPlacementId}
        mode='review'
        onClose={() => {
          setViewOpen(false);
          setViewPlacementId(null);
        }}
      />

      <GeoContentReviewCompareDrawer
        open={compareOpen}
        placementId={comparePlacementId}
        focusReviewId={compareFocusId}
        reviewId={compareReviewId}
        onClose={() => {
          setCompareOpen(false);
          setComparePlacementId(null);
          setCompareFocusId(null);
          setCompareReviewId(null);
          actionRef.current?.reload();
        }}
      />
    </>
  );
});

export default ReviewRecordsPage;
