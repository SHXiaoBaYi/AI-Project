import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Select, Tag, Typography } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { GeoContentReviewCompareDrawer } from '@/components/geo/content-placement/GeoContentReviewCompareDrawer';
import { GeoContentWriteDrawer } from '@/components/geo/content-placement/GeoContentWriteDrawer';
import {
  getGeoOwnerOptionsApi,
  getGeoTopicOptionsApi,
  getMyGeoWritingsApi,
  submitGeoContentMultiReviewApi,
} from '@/api/geo';
import type { GeoContentWritingRow, GeoOwnerOption, GeoTopic } from '@/types/geo';

const STATUS_META: Record<string, { text: string; color: string }> = {
  drafting: { text: '草稿', color: 'default' },
  pending_review: { text: '待审核', color: 'processing' },
  revising: { text: '修改中', color: 'warning' },
  approved: { text: '已通过', color: 'success' },
  queue: { text: '待投放', color: 'cyan' },
  published: { text: '已发布', color: 'green' },
};

const LOCKED_STATUSES = new Set(['queue', 'published']);

const REVIEW_STATUS_META: Record<string, { text: string; color: string }> = {
  pending: { text: '审核中', color: 'processing' },
  approved: { text: '已通过', color: 'success' },
  rejected: { text: '已退回', color: 'error' },
};

const RESULT_META: Record<string, { text: string; color: string }> = {
  pending: { text: '待审', color: 'default' },
  approved: { text: '通过', color: 'success' },
  rejected: { text: '退回', color: 'error' },
};

const MyWritingsPage = memo(function MyWritingsPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);
  const [topics, setTopics] = useState<GeoTopic[]>([]);
  const [writeOpen, setWriteOpen] = useState(false);
  const [writePlacementId, setWritePlacementId] = useState<number | null>(null);
  const [submitOpen, setSubmitOpen] = useState(false);
  const [submitting, setSubmitting] = useState<GeoContentWritingRow | null>(null);
  const [submitForm] = Form.useForm();
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewRow, setReviewRow] = useState<GeoContentWritingRow | null>(null);
  const [compareOpen, setCompareOpen] = useState(false);
  const [comparePlacementId, setComparePlacementId] = useState<number | null>(null);
  const [compareFocusId, setCompareFocusId] = useState<number | null>(null);

  useEffect(() => {
    void Promise.all([getGeoOwnerOptionsApi(), getGeoTopicOptionsApi()]).then(([ownerOpts, topicOpts]) => {
      setOwners(ownerOpts ?? []);
      setTopics(topicOpts ?? []);
    });
  }, []);

  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);
  const topicOptions = useMemo(() => topics.map((t) => ({ label: t.topicName, value: t.id })), [topics]);

  const columns: ProColumnType<GeoContentWritingRow>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 320,
      search: false,
      render: (_, record) => {
        const locked = LOCKED_STATUSES.has(record.contentStatus || '');
        return (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '修改文章',
                perm: 'geo:write:mine',
                disabled: locked,
                onClick: () => {
                  setWritePlacementId(record.placementId);
                  setWriteOpen(true);
                },
              },
              {
                key: 'compare',
                label: '文章对照',
                perm: 'geo:write:mine',
                disabled: !record.latestReviewId,
                onClick: () => {
                  setComparePlacementId(record.placementId);
                  setCompareFocusId(record.latestReviewId ?? record.currentReviewId ?? null);
                  setCompareOpen(true);
                },
              },
              {
                key: 'review',
                label: '审核意见',
                perm: 'geo:write:mine',
                disabled: !record.latestReviewId,
                onClick: () => {
                  setReviewRow(record);
                  setReviewOpen(true);
                },
              },
              {
                key: 'submit',
                label: '发起审核',
                perm: 'geo:write:mine',
                disabled: locked || record.contentStatus === 'pending_review',
                onClick: () => {
                  setSubmitting(record);
                  submitForm.resetFields();
                  setSubmitOpen(true);
                },
              },
            ]}
          />
        );
      },
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
      dataIndex: 'contentStatus',
      width: 100,
      valueType: 'select',
      valueEnum: Object.fromEntries(Object.entries(STATUS_META).map(([k, v]) => [k, { text: v.text }])),
      fieldProps: { allowClear: true },
      render: (_, r) => {
        const meta = STATUS_META[r.contentStatus || 'drafting'] || STATUS_META.drafting;
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '最近审核',
      dataIndex: 'latestReviewStatus',
      width: 220,
      search: false,
      render: (_, r) => {
        if (!r.latestReviewId) return <span className='text-neutral-400'>-</span>;
        const meta = REVIEW_STATUS_META[r.latestReviewStatus || ''] || {
          text: r.latestReviewStatus || '-',
          color: 'default',
        };
        return (
          <div className='flex min-w-0 flex-col gap-1'>
            <div className='flex flex-wrap items-center gap-1'>
              <Tag
                color={meta.color}
                className='mr-0'
              >
                第{r.latestRoundNo ?? '-'}轮 · {meta.text}
              </Tag>
            </div>
            {r.rejectSummary ? (
              <Typography.Paragraph
                className='!mb-0 text-xs text-red-600'
                ellipsis={{ rows: 2, tooltip: r.rejectSummary }}
              >
                {r.rejectSummary}
              </Typography.Paragraph>
            ) : r.reviewerSummary ? (
              <Typography.Text
                type='secondary'
                className='text-xs'
                ellipsis={{ tooltip: r.reviewerSummary }}
              >
                {r.reviewerSummary}
              </Typography.Text>
            ) : null}
          </div>
        );
      },
    },
    {
      title: '版本',
      dataIndex: 'contentVersion',
      width: 70,
      search: false,
    },
    {
      title: '最近保存',
      dataIndex: 'contentUpdatedAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
  ];

  return (
    <>
      <BaseProTable<GeoContentWritingRow>
        rowKey='placementId'
        actionRef={actionRef}
        columns={columns}
        headerTitle='我的撰写记录'
        scroll={{ x: 1200 }}
        request={async (params) => {
          const rows = await getMyGeoWritingsApi({
            topicId: params.topicId != null ? Number(params.topicId) : undefined,
            targetQuestion: (params.targetQuestion as string) || undefined,
            contentTitle: (params.contentTitle as string) || undefined,
            contentStatus: (params.contentStatus as string) || undefined,
          });
          return { data: rows ?? [], success: true, total: rows?.length ?? 0 };
        }}
        toolBarRender={() => [
          <span
            key='hint'
            className='text-sm text-neutral-500'
          >
            审核通过后自动进入文章列表维护投放；待投放/已发布不可再改稿
          </span>,
        ]}
      />

      <GeoContentWriteDrawer
        open={writeOpen}
        placementId={writePlacementId}
        mode='write'
        onClose={() => {
          setWriteOpen(false);
          setWritePlacementId(null);
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
        title='审核意见'
        open={reviewOpen}
        destroyOnHidden
        footer={null}
        onCancel={() => {
          setReviewOpen(false);
          setReviewRow(null);
        }}
      >
        <p className='mb-3 text-sm text-neutral-500'>
          {reviewRow?.topicName || '话题'} · {reviewRow?.contentTitle || reviewRow?.targetQuestion || ''}
        </p>
        {reviewRow?.latestReviewStatus ? (
          <div className='mb-3'>
            <Tag color={(REVIEW_STATUS_META[reviewRow.latestReviewStatus] || { color: 'default' }).color}>
              第{reviewRow.latestRoundNo ?? '-'}轮 ·{' '}
              {(REVIEW_STATUS_META[reviewRow.latestReviewStatus] || { text: reviewRow.latestReviewStatus }).text}
            </Tag>
          </div>
        ) : null}
        {(reviewRow?.reviewers ?? []).length === 0 ? (
          <p className='text-sm text-neutral-400'>暂无审核记录</p>
        ) : (
          <ul className='m-0 list-none space-y-3 p-0'>
            {(reviewRow?.reviewers ?? []).map((item) => {
              const meta = RESULT_META[item.result || 'pending'] || RESULT_META.pending;
              return (
                <li
                  key={`${item.userId}-${item.actedAt || ''}`}
                  className='rounded border border-neutral-100 px-3 py-2'
                >
                  <div className='mb-1 flex items-center gap-2'>
                    <span className='font-medium'>{item.userName || item.userId}</span>
                    <Tag
                      color={meta.color}
                      className='mr-0'
                    >
                      {meta.text}
                    </Tag>
                    {item.actedAt ? <span className='text-xs text-neutral-400'>{item.actedAt}</span> : null}
                  </div>
                  <div className='text-sm text-neutral-700'>{item.comment?.trim() || '（无文字意见）'}</div>
                </li>
              );
            })}
          </ul>
        )}
        {reviewRow?.latestReviewStatus === 'rejected' || reviewRow?.contentStatus === 'revising' ? (
          <div className='mt-4'>
            <Typography.Text type='secondary'>请按意见修改后，再点「发起审核」。</Typography.Text>
          </div>
        ) : null}
      </Modal>

      <Modal
        title='发起多人审核'
        open={submitOpen}
        destroyOnHidden
        onCancel={() => {
          setSubmitOpen(false);
          setSubmitting(null);
          submitForm.resetFields();
        }}
        onOk={async () => {
          if (!submitting) return;
          const values = await submitForm.validateFields();
          try {
            await submitGeoContentMultiReviewApi({
              placementId: submitting.placementId,
              reviewerUserIds: values.reviewerUserIds,
              remark: values.remark,
            });
            message.success('已发起审核');
            setSubmitOpen(false);
            setSubmitting(null);
            submitForm.resetFields();
            actionRef.current?.reload();
          } catch (e) {
            message.error(e instanceof Error ? e.message : '发起失败');
          }
        }}
      >
        <p className='mb-3 text-sm text-neutral-500'>
          {submitting?.topicName || '话题'} · {submitting?.contentTitle || submitting?.targetQuestion || ''}
        </p>
        {submitting?.rejectSummary ? (
          <div className='mb-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800'>
            上一轮退回意见：{submitting.rejectSummary}
          </div>
        ) : null}
        <Form
          form={submitForm}
          layout='vertical'
        >
          <Form.Item
            name='reviewerUserIds'
            label='审核人'
            rules={[{ required: true, message: '请选择至少一位审核人' }]}
          >
            <Select
              mode='multiple'
              options={userOptions}
              showSearch
              optionFilterProp='label'
              placeholder='可多选'
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='提审说明'
          >
            <Input.TextArea
              rows={3}
              placeholder='可选，可说明本轮已按意见修改的点'
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default MyWritingsPage;
