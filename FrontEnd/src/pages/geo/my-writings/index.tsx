import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Input, Modal, Select, Tag, Typography } from 'antd';
import { useSelector } from 'react-redux';
import type { RootState } from '@/store';
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
  stage1_passed: { text: '一审已通过', color: 'success' },
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

function stageText(stageNo?: number | null) {
  if (stageNo === 2) return '二审';
  if (stageNo === 1) return '一审';
  return '';
}

const MyWritingsPage = memo(function MyWritingsPage() {
  const { message } = App.useApp();
  const currentUserId = useSelector((state: RootState) => state.user.userInfo?.userId);
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
                label: record.nextStageNo === 2 ? '发起二审' : '发起一审',
                perm: 'geo:write:mine',
                disabled: locked || !record.nextStageNo,
                onClick: () => {
                  setSubmitting(record);
                  submitForm.resetFields();
                  submitForm.setFieldsValue({
                    reviewerUserId: record.lockedReviewerUserId ?? undefined,
                  });
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
                {stageText(r.latestStageNo) ? `${stageText(r.latestStageNo)} · ` : ''}第{r.latestRoundNo ?? '-'}轮 ·{' '}
                {meta.text}
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
            固定两阶段串行审核，每阶段 1 人；一审通过后才能发二审，撰写任务进度
            80%；二审通过后任务完成并进入投放管理待投放。打回后仍由原审核人复审。
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
              {stageText(reviewRow.latestStageNo) ? `${stageText(reviewRow.latestStageNo)} · ` : ''}第
              {reviewRow.latestRoundNo ?? '-'}轮 ·{' '}
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
            <Typography.Text type='secondary'>请按意见修改后，再交给打回人复审（不可换人）。</Typography.Text>
          </div>
        ) : null}
      </Modal>

      <Modal
        title={submitting?.nextStageNo === 2 ? '发起二审' : '发起一审'}
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
              reviewerUserId: values.reviewerUserId,
              remark: values.remark,
            });
            message.success(submitting.nextStageNo === 2 ? '已发起二审' : '已发起一审');
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
        {submitting?.nextStageNo === 2 ? (
          <p className='mb-3 text-xs text-neutral-500'>
            {Number(submitting.skipStage1) === 1 && !submitting.stage1ReviewerName
              ? '按审核规则跳过一审，请直接指定二审人。发起后撰写进度记为 80%，二审通过后完成。'
              : `一审已通过${submitting.stage1ReviewerName ? `（${submitting.stage1ReviewerName}）` : ''}，请指定另一位二审人。`}
          </p>
        ) : (
          <p className='mb-3 text-xs text-neutral-500'>
            每阶段仅 1 人审核。一审通过后撰写任务进度为 80%，二审通过后才完成。
          </p>
        )}
        {submitting?.rejectSummary ? (
          <div className='mb-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800'>
            上一轮退回意见：{submitting.rejectSummary}
          </div>
        ) : null}
        {submitting?.lockedReviewerUserId ? (
          <div className='mb-3 rounded border border-neutral-200 bg-neutral-50 px-3 py-2 text-xs text-neutral-600'>
            本阶段由打回人复审，不可更换：{submitting.lockedReviewerName || submitting.lockedReviewerUserId}
          </div>
        ) : null}
        <Form
          form={submitForm}
          layout='vertical'
        >
          <Form.Item
            name='reviewerUserId'
            label='审核人'
            rules={[{ required: true, message: '请选择一位审核人' }]}
          >
            <Select
              disabled={!!submitting?.lockedReviewerUserId}
              options={userOptions.filter((opt) => {
                if (currentUserId != null && opt.value === currentUserId) return false;
                if (
                  submitting?.nextStageNo === 2 &&
                  submitting.stage1ReviewerUserId != null &&
                  opt.value === submitting.stage1ReviewerUserId
                ) {
                  return false;
                }
                if (submitting?.lockedReviewerUserId != null) {
                  return opt.value === submitting.lockedReviewerUserId;
                }
                return true;
              })}
              showSearch
              optionFilterProp='label'
              placeholder='请选择 1 人'
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
