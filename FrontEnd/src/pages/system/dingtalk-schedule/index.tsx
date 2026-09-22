import { memo, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App } from 'antd';
import dayjs from 'dayjs';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  cancelDingTalkAssistantScheduleApi,
  listDingTalkAssistantSchedulesApi,
  updateDingTalkAssistantScheduleApi,
  type DingTalkAssistantSchedule,
} from '@/api/dingtalk';

const KIND_OPTIONS = [
  { value: 'MEETING', label: '会议' },
  { value: 'REPORT', label: '工作汇报' },
];

const STATUS_OPTIONS = [
  { value: 'SUCCESS', label: '有效' },
  { value: 'CANCELLED', label: '已取消' },
];

const DingTalkSchedulePage = memo(function DingTalkSchedulePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [editing, setEditing] = useState<DingTalkAssistantSchedule | null>(null);
  const [open, setOpen] = useState(false);

  const columns: ProColumnType<DingTalkAssistantSchedule>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 160,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                label: '编辑',
                perm: 'system:dingtalk:schedule:edit',
                disabled: record.status === 'CANCELLED',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'cancel',
                label: '取消',
                perm: 'system:dingtalk:schedule:cancel',
                disabled: record.status === 'CANCELLED',
                confirmTitle: `确认取消「${record.title}」？将同步取消钉钉日程。`,
                onClick: async () => {
                  const result = await cancelDingTalkAssistantScheduleApi(record.id);
                  message.success(result?.message || '已取消');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
      {
        title: '类型',
        dataIndex: 'kind',
        valueType: 'select',
        fieldProps: { options: KIND_OPTIONS, allowClear: true },
        render: (_, record) => record.kindLabel || record.kind,
      },
      {
        title: '主题',
        dataIndex: 'title',
        search: false,
        ellipsis: true,
      },
      {
        title: '对方',
        dataIndex: 'targetNickname',
        search: false,
      },
      {
        title: '开始时间',
        dataIndex: 'startTime',
        search: false,
        render: (_, record) => (record.startTime ? String(record.startTime).replace('T', ' ').slice(0, 16) : '—'),
      },
      {
        title: '时长',
        dataIndex: 'durationMin',
        search: false,
        width: 80,
        render: (_, record) => (record.durationMin ? `${record.durationMin} 分` : '—'),
      },
      {
        title: '地点',
        dataIndex: 'location',
        search: false,
        ellipsis: true,
        render: (_, record) => record.location || '—',
      },
      {
        title: '视频会议',
        dataIndex: 'onlineMeeting',
        search: false,
        width: 90,
        render: (_, record) => (record.onlineMeeting === 1 ? '是' : '否'),
      },
      {
        title: '状态',
        dataIndex: 'status',
        valueType: 'select',
        fieldProps: { options: STATUS_OPTIONS, allowClear: true },
        render: (_, record) => record.statusLabel || record.status,
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        search: false,
        render: (_, record) => (record.createTime ? String(record.createTime).replace('T', ' ').slice(0, 16) : '—'),
      },
    ],
    [message],
  );

  return (
    <>
      <BaseProTable<DingTalkAssistantSchedule>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='助手日程'
        request={async (params) => {
          const rows = await listDingTalkAssistantSchedulesApi({
            kind: params.kind ? String(params.kind) : undefined,
            status: params.status ? String(params.status) : undefined,
          });
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const list = rows ?? [];
          return { data: list.slice(start, start + pageSize), success: true, total: list.length };
        }}
      />
      <TableModal
        readonly={false}
        title='编辑助手日程'
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing
            ? {
                title: editing.title,
                startTime: editing.startTime ? dayjs(editing.startTime) : undefined,
                durationMin: editing.durationMin || 60,
                location: editing.location,
                description: editing.description,
                onlineMeeting: editing.onlineMeeting === 1,
              }
            : undefined
        }
        columns={
          [
            {
              title: '主题',
              dataIndex: 'title',
              formItemProps: { rules: [{ required: true, message: '请填写主题' }] },
            },
            {
              title: '开始时间',
              dataIndex: 'startTime',
              valueType: 'dateTime',
              formItemProps: { rules: [{ required: true, message: '请选择开始时间' }] },
              fieldProps: { showTime: { minuteStep: 15, format: 'HH:mm' }, format: 'YYYY-MM-DD HH:mm' },
            },
            {
              title: '时长（分钟）',
              dataIndex: 'durationMin',
              valueType: 'digit',
              fieldProps: { min: 15, max: 240, step: 15 },
            },
            {
              title: '地点',
              dataIndex: 'location',
            },
            {
              title: '描述',
              dataIndex: 'description',
              valueType: 'textarea',
            },
            {
              title: '钉钉视频会议',
              dataIndex: 'onlineMeeting',
              valueType: 'switch',
            },
          ] as never
        }
        onFinish={async (values) => {
          if (!editing) return false;
          const form = values as {
            title: string;
            startTime: dayjs.Dayjs | string;
            durationMin?: number;
            location?: string;
            description?: string;
            onlineMeeting?: boolean;
          };
          const result = await updateDingTalkAssistantScheduleApi({
            id: editing.id,
            title: form.title,
            startTime: dayjs(form.startTime).format('YYYY-MM-DD HH:mm:ss'),
            durationMin: form.durationMin,
            location: form.location,
            description: form.description,
            onlineMeeting: !!form.onlineMeeting,
          });
          message.success(result?.message || '已保存');
          setEditing(null);
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default DingTalkSchedulePage;
