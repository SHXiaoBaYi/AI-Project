import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { GeoContentReviewCompareDrawer } from '@/components/geo/content-placement/GeoContentReviewCompareDrawer';
import { GeoContentWriteDrawer } from '@/components/geo/content-placement/GeoContentWriteDrawer';
import { decideGeoContentReviewApi, getPendingGeoReviewsApi } from '@/api/geo';
import type { GeoContentReviewRow } from '@/types/geo';

const PendingReviewPage = memo(function PendingReviewPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [viewOpen, setViewOpen] = useState(false);
  const [viewPlacementId, setViewPlacementId] = useState<number | null>(null);
  const [viewReviewId, setViewReviewId] = useState<number | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);
  const [comparePlacementId, setComparePlacementId] = useState<number | null>(null);
  const [compareFocusId, setCompareFocusId] = useState<number | null>(null);
  const [decideOpen, setDecideOpen] = useState(false);
  const [decideAction, setDecideAction] = useState<'approve' | 'reject'>('approve');
  const [deciding, setDeciding] = useState<GeoContentReviewRow | null>(null);
  const [decideForm] = Form.useForm();

  const openDecide = (record: GeoContentReviewRow, action: 'approve' | 'reject') => {
    setDeciding(record);
    setDecideAction(action);
    decideForm.resetFields();
    setDecideOpen(true);
  };

  const columns: ProColumnType<GeoContentReviewRow>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 280,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'view',
              label: '查看文章',
              perm: 'geo:write:review',
              onClick: () => {
                setViewPlacementId(record.placementId);
                setViewReviewId(record.reviewId);
                setViewOpen(true);
              },
            },
            {
              key: 'compare',
              label: '文章对照',
              perm: 'geo:write:review',
              onClick: () => {
                setComparePlacementId(record.placementId);
                setCompareFocusId(record.reviewId);
                setCompareOpen(true);
              },
            },
            {
              key: 'approve',
              label: '通过',
              perm: 'geo:write:review',
              onClick: () => openDecide(record, 'approve'),
            },
            {
              key: 'reject',
              label: '退回',
              perm: 'geo:write:review',
              onClick: () => openDecide(record, 'reject'),
            },
          ]}
        />
      ),
    },
    {
      title: '话题',
      dataIndex: 'topicName',
      width: 120,
      ellipsis: true,
      search: false,
    },
    {
      title: '目标问题',
      dataIndex: 'targetQuestion',
      ellipsis: true,
      search: false,
    },
    {
      title: '正文标题',
      dataIndex: 'contentTitle',
      ellipsis: true,
      search: false,
    },
    {
      title: '发起人',
      dataIndex: 'submitterName',
      width: 100,
      search: false,
    },
    {
      title: '轮次',
      dataIndex: 'roundNo',
      width: 70,
      search: false,
    },
    {
      title: '审核人',
      dataIndex: 'reviewerSummary',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '提审说明',
      dataIndex: 'submitRemark',
      ellipsis: true,
      search: false,
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
        headerTitle='待审核文章'
        search={false}
        pagination={{ pageSize: 20 }}
        scroll={{ x: 1200 }}
        request={async () => {
          const rows = await getPendingGeoReviewsApi();
          return { data: rows ?? [], success: true, total: rows?.length ?? 0 };
        }}
      />

      <GeoContentWriteDrawer
        open={viewOpen}
        placementId={viewPlacementId}
        reviewId={viewReviewId}
        mode='review'
        onClose={() => {
          setViewOpen(false);
          setViewPlacementId(null);
          setViewReviewId(null);
          actionRef.current?.reload();
        }}
      />

      <GeoContentReviewCompareDrawer
        open={compareOpen}
        placementId={comparePlacementId}
        focusReviewId={compareFocusId}
        onClose={() => {
          setCompareOpen(false);
          setComparePlacementId(null);
          setCompareFocusId(null);
        }}
      />

      <Modal
        title={decideAction === 'approve' ? '确认通过' : '退回修改'}
        open={decideOpen}
        destroyOnHidden
        onCancel={() => {
          setDecideOpen(false);
          setDeciding(null);
          decideForm.resetFields();
        }}
        onOk={async () => {
          if (!deciding) return;
          const values = await decideForm.validateFields();
          try {
            await decideGeoContentReviewApi({
              reviewId: deciding.reviewId,
              action: decideAction,
              comment: values.comment,
            });
            message.success(decideAction === 'approve' ? '已通过' : '已退回');
            setDecideOpen(false);
            setDeciding(null);
            decideForm.resetFields();
            actionRef.current?.reload();
          } catch (e) {
            message.error(e instanceof Error ? e.message : '操作失败');
          }
        }}
      >
        <p className='mb-3 text-sm text-neutral-500'>
          {deciding?.topicName || '话题'} · {deciding?.contentTitle || deciding?.targetQuestion || ''}
        </p>
        {decideAction === 'approve' ? <Tag color='success'>全部审核人通过后，撰写任务将自动完成</Tag> : null}
        <Form
          form={decideForm}
          layout='vertical'
          className='mt-3'
        >
          <Form.Item
            name='comment'
            label='审核意见'
            rules={decideAction === 'reject' ? [{ required: true, message: '退回请填写意见' }] : undefined}
          >
            <Input.TextArea
              rows={3}
              placeholder={decideAction === 'reject' ? '请说明需修改的点' : '可选'}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default PendingReviewPage;
