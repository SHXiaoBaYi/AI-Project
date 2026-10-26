import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Form, Modal, Select, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';
import { listTrainQaMissApi, markTrainQaMissHandledApi, type TrainQaHandleStatus, type TrainQaMiss } from '@/api/train';
import {
  HANDLE_REASON_OPTIONS,
  HANDLE_STATUS_OPTIONS,
  HANDLED_FILTER_OPTIONS,
  handleStatusColor,
  handleStatusLabel,
  parseHandledFilter,
} from '@/pages/train/qaHandle';

const TrainQaMissPage = memo(function TrainQaMissPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [handleOpen, setHandleOpen] = useState(false);
  const [handling, setHandling] = useState(false);
  const [current, setCurrent] = useState<TrainQaMiss | null>(null);
  const [form] = Form.useForm<{ handleStatus: TrainQaHandleStatus }>();

  const openHandle = (record: TrainQaMiss) => {
    setCurrent(record);
    form.setFieldsValue({
      handleStatus:
        record.handleStatus && record.handleStatus !== 'OPEN'
          ? (record.handleStatus as TrainQaHandleStatus)
          : 'INTENT_BAD',
    });
    setHandleOpen(true);
  };

  const submitHandle = async () => {
    if (!current) return;
    const values = await form.validateFields();
    setHandling(true);
    try {
      await markTrainQaMissHandledApi({ id: current.id, handleStatus: values.handleStatus });
      message.success('已标记处理');
      setHandleOpen(false);
      actionRef.current?.reload();
    } finally {
      setHandling(false);
    }
  };

  const columns: ProColumnType<TrainQaMiss>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      fixed: 'left',
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'handle',
              label: record.handled ? '改处理' : '标记处理',
              perm: 'train:qa:edit',
              onClick: () => openHandle(record),
            },
            ...(record.handled
              ? [
                  {
                    key: 'reopen',
                    label: '重开',
                    perm: 'train:qa:edit',
                    confirmTitle: '确认恢复为未处理？',
                    onClick: async () => {
                      await markTrainQaMissHandledApi({ id: record.id, handleStatus: 'OPEN' });
                      message.success('已恢复为未处理');
                      actionRef.current?.reload();
                    },
                  },
                ]
              : []),
          ]}
        />
      ),
    },
    {
      title: '处理情况',
      dataIndex: 'handled',
      width: 110,
      valueType: 'select',
      fieldProps: { options: HANDLED_FILTER_OPTIONS, allowClear: true },
      render: (_, r) =>
        r.handled ? (
          <Tag color={handleStatusColor(r.handleStatus)}>{handleStatusLabel(r.handleStatus)}</Tag>
        ) : (
          <Tag color='processing'>未处理</Tag>
        ),
    },
    {
      title: '处理原因',
      dataIndex: 'handleStatus',
      hideInTable: true,
      valueType: 'select',
      fieldProps: {
        options: HANDLE_STATUS_OPTIONS.filter((item) => item.value !== 'OPEN'),
        allowClear: true,
      },
    },
    {
      title: '提问人',
      dataIndex: 'nickname',
      width: 120,
      search: false,
      render: (_, r) => r.nickname || r.username || '-',
    },
    {
      title: '账号',
      dataIndex: 'username',
      width: 120,
      search: false,
      ellipsis: true,
    },
    {
      title: '关键词',
      dataIndex: 'keyword',
      hideInTable: true,
      fieldProps: { placeholder: '问法 / 提问人 / 账号' },
    },
    createTimeRangeColumn({ title: '提问时间' }),
    {
      title: '原问',
      dataIndex: 'question',
      ellipsis: true,
      search: false,
      width: 280,
    },
    {
      title: '分词意图',
      dataIndex: 'intentTokens',
      ellipsis: true,
      search: false,
      width: 200,
    },
    {
      title: '结果',
      dataIndex: 'status',
      width: 110,
      search: false,
      render: (_, r) => (r.status === 'NEED_OLD' ? <Tag color='warning'>建议搜旧版</Tag> : <Tag>没有找到答案</Tag>),
    },
    {
      title: '处理人',
      dataIndex: 'handleBy',
      width: 90,
      search: false,
      render: (_, r) => r.handleBy || '-',
    },
    {
      title: '处理时间',
      dataIndex: 'handleTime',
      width: 170,
      search: false,
      render: (_, r) => r.handleTime || '-',
    },
    createTimeDisplayColumn({ title: '提问时间', width: 180 }),
  ];

  return (
    <>
      <BaseProTable<TrainQaMiss>
        rowKey='id'
        actionRef={actionRef}
        headerTitle='未命中提问'
        columns={columns}
        scroll={{ x: 1400 }}
        request={async (params) => {
          const res = await listTrainQaMissApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            keyword: params.keyword ? String(params.keyword) : undefined,
            createTimeStart: params.createTimeStart ? String(params.createTimeStart) : undefined,
            createTimeEnd: params.createTimeEnd ? String(params.createTimeEnd) : undefined,
            handled: parseHandledFilter(params.handled),
            handleStatus: params.handleStatus ? String(params.handleStatus) : undefined,
          });
          return { data: res.rows || [], total: res.total || 0, success: true };
        }}
      />

      <Modal
        title='标记处理'
        open={handleOpen}
        onCancel={() => setHandleOpen(false)}
        onOk={() => void submitHandle()}
        confirmLoading={handling}
        destroyOnHidden
      >
        <div className='mb-3 text-sm text-neutral-500'>原问：{current?.question || '-'}</div>
        <Form
          form={form}
          layout='vertical'
        >
          <Form.Item
            name='handleStatus'
            label='处理原因'
            rules={[{ required: true, message: '请选择处理原因' }]}
          >
            <Select options={HANDLE_REASON_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
});

export default TrainQaMissPage;
