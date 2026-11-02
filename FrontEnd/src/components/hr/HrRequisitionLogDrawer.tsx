import { memo, useEffect, useRef } from 'react';
import type { ActionType } from '@ant-design/pro-components';
import { Drawer, Modal, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { listHrRequisitionLogsApi, type HrRequisitionLog } from '@/api/hr';

const LOG_ACTIONS = [
  { label: '新增', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '改状态', value: 'STATUS' },
  { label: '任务要求', value: 'TASK' },
];

const ENTRY_OPTIONS = [
  { label: '招聘需求', value: 'REQUISITION' },
  { label: '招聘面板', value: 'PANEL' },
];

function prettyJson(raw?: string) {
  if (!raw) return '—';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

type Props = {
  open: boolean;
  onClose: () => void;
  /** 按岗位过滤；不传则看全部 */
  requisitionId?: number;
  jobName?: string;
  title?: string;
};

const HrRequisitionLogDrawer = memo(function HrRequisitionLogDrawer({
  open,
  onClose,
  requisitionId,
  jobName,
  title,
}: Props) {
  const logActionRef = useRef<ActionType>(null);

  useEffect(() => {
    if (!open) return;
    setTimeout(() => logActionRef.current?.reload(), 0);
  }, [open, requisitionId]);

  const drawerTitle =
    title || (requisitionId != null ? `操作日志 · ${jobName || `#${requisitionId}`}` : '招聘需求操作日志');

  return (
    <Drawer
      title={drawerTitle}
      width={960}
      open={open}
      onClose={onClose}
      destroyOnClose
    >
      <BaseProTable<HrRequisitionLog>
        rowKey='id'
        actionRef={logActionRef}
        headerTitle='操作日志'
        columns={[
          {
            title: '操作',
            valueType: 'option',
            width: 72,
            render: (_, record) => (
              <ActionButtons
                items={[
                  {
                    key: 'detail',
                    label: '详情',
                    onClick: () => {
                      Modal.info({
                        title: `操作详情 · ${record.jobName || record.requisitionId || ''}`,
                        width: 720,
                        content: (
                          <div className='space-y-3 text-sm'>
                            <div>
                              <div className='mb-1 font-medium'>入口</div>
                              <div>{record.entrySourceLabel || record.entrySource || '—'}</div>
                            </div>
                            <div>
                              <div className='mb-1 font-medium'>做了什么</div>
                              <div className='break-all whitespace-pre-wrap text-gray-700'>{record.summary || '—'}</div>
                            </div>
                            <div>
                              <div className='mb-1 font-medium'>原值</div>
                              <pre className='max-h-56 overflow-auto rounded bg-gray-50 p-2 text-xs'>
                                {prettyJson(record.beforeJson)}
                              </pre>
                            </div>
                            <div>
                              <div className='mb-1 font-medium'>新值</div>
                              <pre className='max-h-56 overflow-auto rounded bg-gray-50 p-2 text-xs'>
                                {prettyJson(record.afterJson)}
                              </pre>
                            </div>
                          </div>
                        ),
                      });
                    },
                  },
                ]}
              />
            ),
          },
          {
            title: '关键词',
            dataIndex: 'keyword',
            hideInTable: true,
            fieldProps: { allowClear: true, placeholder: '岗位 / 操作人 / 摘要' },
          },
          {
            title: '动作',
            dataIndex: 'action',
            valueType: 'select',
            width: 90,
            fieldProps: { options: LOG_ACTIONS, allowClear: true },
            render: (_, r) => {
              const color =
                r.action === 'CREATE'
                  ? 'success'
                  : r.action === 'DELETE'
                    ? 'error'
                    : r.action === 'STATUS'
                      ? 'warning'
                      : r.action === 'TASK'
                        ? 'geekblue'
                        : 'processing';
              return <Tag color={color}>{r.actionLabel || r.action || '—'}</Tag>;
            },
          },
          {
            title: '入口',
            dataIndex: 'entrySource',
            valueType: 'select',
            width: 100,
            fieldProps: { options: ENTRY_OPTIONS, allowClear: true },
            render: (_, r) =>
              r.entrySourceLabel || (r.entrySource === 'PANEL' ? '招聘面板' : r.entrySource ? '招聘需求' : '—'),
          },
          {
            title: '操作时间',
            dataIndex: 'operatedAtRange',
            valueType: 'dateRange',
            hideInTable: true,
            search: {
              transform: (value) => ({
                createTimeStart: value?.[0],
                createTimeEnd: value?.[1],
              }),
            },
            fieldProps: { allowClear: true },
          },
          {
            title: '时间',
            dataIndex: 'operatedAt',
            search: false,
            width: 170,
          },
          {
            title: '操作人',
            dataIndex: 'operatorNickname',
            search: false,
            width: 100,
            render: (_, r) => r.operatorNickname || r.operatorUsername || '—',
          },
          {
            title: '岗位',
            dataIndex: 'jobName',
            search: false,
            ellipsis: true,
            width: 140,
            hideInTable: requisitionId != null,
          },
          {
            title: '做了什么',
            dataIndex: 'summary',
            search: false,
            ellipsis: true,
          },
        ]}
        request={async (params) => {
          const res = await listHrRequisitionLogsApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            requisitionId,
            action: params.action,
            entrySource: params.entrySource,
            keyword: params.keyword,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
          });
          return { data: res.rows || [], success: true, total: res.total || 0 };
        }}
      />
    </Drawer>
  );
});

export default HrRequisitionLogDrawer;
