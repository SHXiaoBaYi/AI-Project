import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import dayjs from 'dayjs';
import { useSearchParams } from 'react-router-dom';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import { AiAnalysisDrawer } from '@/components/hr/AiAnalysisDrawer';
import InterviewReviewDrawer from '@/components/hr/InterviewReviewDrawer';
import ApplicationFormModal from './components/ApplicationFormModal';
import ImportApplicationModal from './components/ImportApplicationModal';
import {
  deleteHrApplicationApi,
  deleteHrApplicationBatchApi,
  getHrApplicationsApi,
  getHrChannelsApi,
  getHrRequisitionsApi,
  getHrStagesApi,
  getHrUsersApi,
} from '@/api/hr';

interface AppRow {
  id: number;
  displayName: string;
  phone?: string;
  email?: string;
  requisitionId?: number;
  jobName?: string;
  channelCode?: string;
  channelName?: string;
  currentStage?: string;
  stageName?: string;
  submitterUserId?: number;
  submitterName?: string;
  submittedAt?: string;
  resumeName?: string;
  aiScore?: number;
  aiCount?: number;
}

function textOf(value: unknown) {
  if (value == null || value === '') return undefined;
  return String(value);
}

function dateOf(value: unknown) {
  if (value == null || value === '') return undefined;
  if (Array.isArray(value)) return dayjs(`${value[0]}-${value[1]}-${value[2]}`).format('YYYY-MM-DD');
  return String(value).slice(0, 10);
}

function mapRow(raw: Record<string, unknown>): AppRow {
  return {
    id: Number(raw.id),
    displayName: String(raw.display_name ?? ''),
    phone: textOf(raw.phone),
    email: textOf(raw.email),
    requisitionId: raw.requisition_id == null ? undefined : Number(raw.requisition_id),
    jobName: textOf(raw.job_name),
    channelCode: textOf(raw.channel_code),
    channelName: textOf(raw.channel_name),
    currentStage: textOf(raw.current_stage),
    stageName: textOf(raw.stage_name),
    submitterUserId: raw.submitter_user_id == null ? undefined : Number(raw.submitter_user_id),
    submitterName: textOf(raw.submitter_name),
    submittedAt: dateOf(raw.submitted_at),
    resumeName: textOf(raw.file_name),
    aiScore: raw.ai_score == null || raw.ai_score === '' ? undefined : Number(raw.ai_score),
    aiCount: raw.ai_count == null ? 0 : Number(raw.ai_count),
  };
}

const ApplicationPage = memo(function ApplicationPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [searchParams] = useSearchParams();
  const presetRequisitionId = Number(searchParams.get('requisitionId') || '') || undefined;
  const [open, setOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editing, setEditing] = useState<AppRow | null>(null);
  const [jobs, setJobs] = useState<{ value: number; label: string }[]>([]);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [stages, setStages] = useState<{ value: string; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [aiTarget, setAiTarget] = useState<AppRow | null>(null);
  const [reviewTarget, setReviewTarget] = useState<AppRow | null>(null);

  useEffect(() => {
    getHrRequisitionsApi().then((rows) =>
      setJobs(
        (rows as Record<string, unknown>[]).map((row) => ({
          value: Number(row.id),
          label: `${row.job_name}${row.location_code === 'XJ' ? ' · 新疆' : ' · 上海'}`,
        })),
      ),
    );
    getHrChannelsApi().then((rows) =>
      setChannels(
        rows.map((item) => {
          const row = item as {
            channelCode?: string;
            channel_code?: string;
            channelName?: string;
            channel_name?: string;
          };
          return {
            value: String(row.channelCode ?? row.channel_code),
            label: String(row.channelName ?? row.channel_name),
          };
        }),
      ),
    );
    getHrStagesApi().then((rows) =>
      setStages(
        rows.map((item) => ({
          value: String(item.stageCode ?? item.stage_code),
          label: String(item.stageName ?? item.stage_name),
        })),
      ),
    );
    getHrUsersApi().then((list) =>
      setUsers(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname?: string; username?: string };
          const nickname = row.nickname || row.username || '';
          return { value: Number(row.userId ?? row.user_id), label: nickname };
        }),
      ),
    );
  }, []);

  const columns: ProColumnType<AppRow>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 280,
        render: (_, record) => (
          <ActionButtons
            maxVisible={4}
            items={[
              {
                key: 'reviews',
                label: '面试评价',
                onClick: () => setReviewTarget(record),
              },
              {
                key: 'ai',
                label: 'AI分析',
                onClick: () => setAiTarget(record),
              },
              {
                key: 'edit',
                label: '编辑',
                perm: 'hr:application:edit',
                onClick: () => {
                  setEditing(record);
                  setOpen(true);
                },
              },
              {
                key: 'delete',
                label: '删除',
                perm: 'hr:application:delete',
                confirmTitle: `确认删除「${record.displayName}」？`,
                onClick: async () => {
                  await deleteHrApplicationApi(record.id);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
              },
            ]}
          />
        ),
      },
      {
        title: '姓名',
        dataIndex: 'displayName',
      },
      {
        title: '岗位',
        dataIndex: 'requisitionId',
        valueType: 'select',
        fieldProps: { options: jobs, allowClear: true, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.jobName || '—',
      },
      {
        title: '简历',
        dataIndex: 'resumeName',
        search: false,
        render: (_, record) => (
          <ResumeViewButton
            applicationId={record.id}
            fileName={record.resumeName}
          />
        ),
      },
      {
        title: '渠道',
        dataIndex: 'channelCode',
        valueType: 'select',
        fieldProps: { options: channels, allowClear: true },
        render: (_, record) => record.channelName || record.channelCode || '—',
      },
      {
        title: '阶段',
        dataIndex: 'currentStage',
        valueType: 'select',
        fieldProps: { options: stages, allowClear: true, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.stageName || record.currentStage || '—',
      },
      {
        title: 'AI分析',
        dataIndex: 'aiScore',
        search: false,
        width: 110,
        render: (_, record) => (
          <button
            type='button'
            className='cursor-pointer border-0 bg-transparent p-0 text-left text-[#1677ff]'
            onClick={() => setAiTarget(record)}
          >
            {record.aiScore == null
              ? '未分析'
              : `${record.aiScore}分${record.aiCount && record.aiCount > 1 ? ` · ${record.aiCount}份` : ''}`}
          </button>
        ),
      },
      {
        title: '提交人',
        dataIndex: 'submitterName',
        search: false,
        render: (_, record) => record.submitterName || '—',
      },
      {
        title: '提交人',
        dataIndex: 'submitterUserId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: users, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: '投递日期',
        dataIndex: 'submittedRange',
        valueType: 'dateRange',
        hideInTable: true,
        search: { transform: (value: string[]) => ({ startDate: value?.[0], endDate: value?.[1] }) },
      },
      {
        title: '投递日期',
        dataIndex: 'submittedAt',
        valueType: 'date',
        width: 120,
        search: false,
      },
    ],
    [channels, jobs, message, stages, users],
  );

  return (
    <>
      <BaseProTable<AppRow>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='候选人'
        params={{ requisitionId: presetRequisitionId }}
        request={async (params) => {
          const rows = (await getHrApplicationsApi({
            candidateName: params.displayName,
            requisitionId: params.requisitionId,
            channelCode: params.channelCode,
            stageCode: params.currentStage,
            submitterUserId: params.submitterUserId,
            startDate: params.startDate,
            endDate: params.endDate,
          })) as unknown as Record<string, unknown>[];
          const mapped = rows.map(mapRow);
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const pageRows = mapped.slice(start, start + pageSize);
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.id));
          return { data: pageRows, success: true, total: mapped.length };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => {
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
            perm='hr:application:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的候选人');
                return;
              }
              Modal.confirm({
                title: '批量删除候选人',
                content: `确定要删除选中的 ${selectedRowKeys.length} 个候选人吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteHrApplicationBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 个候选人`);
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
            perm='hr:application:add'
            onClick={() => setImportOpen(true)}
          >
            导入
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='hr:application:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增
          </PermissionButton>,
        ]}
      />
      <ApplicationFormModal
        open={open}
        editing={editing}
        jobs={jobs}
        channels={channels}
        stages={stages}
        users={users}
        presetRequisitionId={presetRequisitionId}
        onOpenChange={setOpen}
        onSuccess={() => actionRef.current?.reload()}
      />
      <ImportApplicationModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => actionRef.current?.reload()}
      />
      <AiAnalysisDrawer
        applicationId={aiTarget?.id || 0}
        name={aiTarget?.displayName || ''}
        open={aiTarget != null}
        onClose={() => {
          setAiTarget(null);
          actionRef.current?.reload();
        }}
      />
      <InterviewReviewDrawer
        applicationId={reviewTarget?.id}
        candidateName={reviewTarget?.displayName}
        open={reviewTarget != null}
        onClose={() => setReviewTarget(null)}
      />
    </>
  );
});

export default ApplicationPage;
