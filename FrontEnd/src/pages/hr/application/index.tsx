import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import { useSearchParams } from 'react-router-dom';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import { AiAnalysisDrawer } from '@/components/hr/AiAnalysisDrawer';
import InterviewReviewDrawer from '@/components/hr/InterviewReviewDrawer';
import InviteFormModal, { type InviteFormPreset } from '@/components/hr/InviteFormModal';
import { canCreateInvite, maxRoundOf, nextInviteRound } from '@/components/hr/inviteRound';
import StageAdjustLogDrawer from '@/components/hr/StageAdjustLogDrawer';
import StageAdjustModal from '@/components/hr/StageAdjustModal';
import ApplicationFormModal, { type ApplicationFormValues } from './components/ApplicationFormModal';
import BlacklistReasonModal from './components/BlacklistReasonModal';
import ImportApplicationModal from './components/ImportApplicationModal';
import TalentProfileDrawer from './components/TalentProfileDrawer';
import {
  deleteHrApplicationApi,
  deleteHrApplicationBatchApi,
  getHrApplicationsApi,
  getHrChannelsApi,
  getHrRequisitionsApi,
  getHrStageCatalogApi,
  getHrStagesApi,
  getHrTalentProfileApi,
  getHrUsersApi,
  moveHrBlacklistInApi,
} from '@/api/hr';

interface AppRow {
  id: number;
  candidateId?: number;
  displayName: string;
  pending?: boolean;
  basicInfo?: string;
  currentTitle?: string;
  currentCompany?: string;
  remainingHours?: number | null;
  phone?: string;
  email?: string;
  requisitionId?: number;
  jobName?: string;
  channelCode?: string;
  channelName?: string;
  currentStage?: string;
  stageName?: string;
  submitterName?: string;
  resumeName?: string;
}

function textOf(value: unknown) {
  return value == null || value === '' ? undefined : String(value);
}

function numberOf(value: unknown) {
  if (value == null || value === '') return undefined;
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

function mapRow(raw: Record<string, unknown>): AppRow {
  const remaining = raw.remaining_hours ?? raw.remainingHours;
  return {
    id: Number(raw.id),
    candidateId: numberOf(raw.candidate_id ?? raw.candidateId),
    displayName: String(raw.display_name ?? raw.displayName ?? ''),
    pending: raw.pending === true || raw.pending === 1 || raw.pending === '1',
    basicInfo: textOf(raw.basic_info ?? raw.basicInfo),
    currentTitle: textOf(raw.current_title ?? raw.currentTitle),
    currentCompany: textOf(raw.current_company ?? raw.currentCompany),
    remainingHours: remaining == null || remaining === '' || Number.isNaN(Number(remaining)) ? null : Number(remaining),
    phone: textOf(raw.phone),
    email: textOf(raw.email),
    requisitionId: numberOf(raw.requisition_id ?? raw.requisitionId),
    jobName: textOf(raw.job_name ?? raw.jobName),
    channelCode: textOf(raw.channel_code ?? raw.channelCode),
    channelName: textOf(raw.channel_name ?? raw.channelName),
    currentStage: textOf(raw.current_stage ?? raw.currentStage),
    stageName: textOf(raw.stage_name ?? raw.stageName),
    submitterName: textOf(raw.submitter_name ?? raw.submitterName),
    resumeName: textOf(raw.file_name ?? raw.fileName ?? raw.resumeName),
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
  const [editing, setEditing] = useState<ApplicationFormValues | null>(null);
  const [jobs, setJobs] = useState<{ value: number; label: string }[]>([]);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [stages, setStages] = useState<{ value: string; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [aiTarget, setAiTarget] = useState<{ id: number; name: string } | null>(null);
  const [reviewTarget, setReviewTarget] = useState<{ id: number; name: string } | null>(null);
  const [invitePreset, setInvitePreset] = useState<InviteFormPreset | null>(null);
  const [stageTarget, setStageTarget] = useState<{
    id: number;
    displayName?: string;
    jobName?: string;
    currentStage?: string;
  } | null>(null);
  const [stageLogOpen, setStageLogOpen] = useState(false);
  const [blacklistOpen, setBlacklistOpen] = useState(false);
  const [profileId, setProfileId] = useState<number | null>(null);
  const [roundPlans, setRoundPlans] = useState<Map<number, Map<number, number[]>>>(new Map());
  const [ccRoundPlans, setCcRoundPlans] = useState<Map<number, Map<number, number[]>>>(new Map());

  useEffect(() => {
    getHrRequisitionsApi().then((rows) => {
      setJobs(
        (rows as Record<string, unknown>[]).map((row) => ({
          value: Number(row.id),
          label: `${row.job_name}${row.location_code === 'XJ' ? ' · 新疆' : ' · 上海'}`,
        })),
      );
      const nextInterviewers = new Map<number, Map<number, number[]>>();
      const nextCcs = new Map<number, Map<number, number[]>>();
      const parseRounds = (text: unknown) => {
        const rounds = new Map<number, number[]>();
        String(text ?? '')
          .split(',')
          .filter(Boolean)
          .forEach((part) => {
            const [round, ids] = part.split(':');
            rounds.set(
              Number(round),
              (ids ?? '')
                .split('|')
                .filter(Boolean)
                .map(Number)
                .filter((id) => id > 0),
            );
          });
        return rounds;
      };
      rows.forEach((row) => {
        nextInterviewers.set(Number(row.id), parseRounds(row.interview_rounds));
        nextCcs.set(Number(row.id), parseRounds(row.interview_round_ccs));
      });
      setRoundPlans(nextInterviewers);
      setCcRoundPlans(nextCcs);
    });
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
    getHrStageCatalogApi()
      .then((catalog) => {
        const list = catalog?.stages ?? [];
        if (list.length) {
          setStages(list.map((item) => ({ value: String(item.code), label: String(item.name) })));
          return;
        }
        return getHrStagesApi().then((rows) =>
          setStages(
            rows.map((item) => ({
              value: String(item.stageCode ?? item.stage_code),
              label: String(item.stageName ?? item.stage_name),
            })),
          ),
        );
      })
      .catch(() =>
        getHrStagesApi().then((rows) =>
          setStages(
            rows.map((item) => ({
              value: String(item.stageCode ?? item.stage_code),
              label: String(item.stageName ?? item.stage_name),
            })),
          ),
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
        width: 260,
        render: (_, record) => {
          const roundNo = nextInviteRound(record.currentStage);
          const maxRound = maxRoundOf(roundPlans, record.requisitionId);
          const inviteable = canCreateInvite(record.currentStage, maxRound) && record.requisitionId != null;
          return (
            <ActionButtons
              maxVisible={3}
              items={[
                {
                  key: 'profile',
                  label: '档案',
                  disabled: record.candidateId == null,
                  onClick: () => record.candidateId != null && setProfileId(record.candidateId),
                },
                ...(inviteable && roundNo != null
                  ? [
                      {
                        key: 'invite',
                        label: '邀约',
                        perm: 'hr:invite:add',
                        onClick: () => {
                          const reqId = record.requisitionId!;
                          setInvitePreset({
                            applicationId: record.id,
                            displayName: record.displayName,
                            jobName: record.jobName,
                            roundNo,
                            interviewerUserIds: roundPlans.get(reqId)?.get(roundNo),
                            ccUserIds: ccRoundPlans.get(reqId)?.get(roundNo),
                            durationMin: 60,
                          });
                        },
                      },
                    ]
                  : []),
                {
                  key: 'review',
                  label: '面试评价',
                  onClick: () => setReviewTarget({ id: record.id, name: record.displayName }),
                },
                {
                  key: 'ai',
                  label: 'AI',
                  onClick: () => setAiTarget({ id: record.id, name: record.displayName }),
                },
                {
                  key: 'stage',
                  label: '调整阶段',
                  perm: 'hr:application:stage',
                  onClick: () =>
                    setStageTarget({
                      id: record.id,
                      displayName: record.displayName,
                      jobName: record.jobName,
                      currentStage: record.currentStage,
                    }),
                },
                {
                  key: 'edit',
                  label: '编辑',
                  perm: 'hr:application:edit',
                  disabled: record.candidateId == null,
                  onClick: async () => {
                    if (record.candidateId == null) return;
                    const profile = await getHrTalentProfileApi(record.candidateId);
                    setEditing({ ...profile.talent, activeApplicationId: record.id });
                    setOpen(true);
                  },
                },
                {
                  key: 'delete',
                  label: '删除',
                  perm: 'hr:application:delete',
                  confirmTitle: `确认删除「${record.displayName}」在「${record.jobName || '该岗位'}」的这条投递？人才档案会保留。`,
                  onClick: async () => {
                    await deleteHrApplicationApi(record.id);
                    message.success('已删除');
                    actionRef.current?.reload();
                  },
                },
              ]}
            />
          );
        },
      },
      {
        title: '范围',
        dataIndex: 'listScope',
        valueType: 'select',
        hideInTable: true,
        initialValue: 'OPEN',
        fieldProps: {
          allowClear: false,
          options: [
            { value: 'OPEN', label: '流程中' },
            { value: 'ALL', label: '全部投递' },
          ],
        },
      },
      {
        title: '姓名',
        dataIndex: 'candidateName',
        hideInTable: true,
        fieldProps: { allowClear: true },
      },
      {
        title: '手机号',
        dataIndex: 'phone',
        hideInTable: true,
        fieldProps: { allowClear: true, maxLength: 20, placeholder: '完整手机号' },
      },
      {
        title: '候选人',
        dataIndex: 'displayName',
        search: false,
        width: 120,
        render: (_, record) => (
          <button
            type='button'
            className='inline-flex cursor-pointer items-center gap-1.5 border-0 bg-transparent p-0'
            onClick={() => record.candidateId != null && setProfileId(record.candidateId)}
          >
            <span
              className={`inline-block h-1.5 w-1.5 rounded-full ${record.pending ? 'bg-red-500' : 'bg-blue-500'}`}
            />
            <span className='text-blue-600'>{record.displayName}</span>
          </button>
        ),
      },
      {
        title: '基本信息',
        dataIndex: 'basicInfo',
        search: false,
        width: 180,
        ellipsis: true,
        render: (_, record) => record.basicInfo || '—',
      },
      {
        title: '目前职位 / 公司',
        dataIndex: 'currentTitle',
        search: false,
        width: 200,
        ellipsis: true,
        render: (_, record) => [record.currentTitle, record.currentCompany].filter(Boolean).join(' · ') || '—',
      },
      {
        title: '岗位',
        dataIndex: 'requisitionId',
        valueType: 'select',
        width: 160,
        ellipsis: true,
        fieldProps: { options: jobs, allowClear: true, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.jobName || '—',
      },
      {
        title: '阶段',
        dataIndex: 'stageCode',
        valueType: 'select',
        width: 140,
        ellipsis: true,
        fieldProps: { options: stages, allowClear: true, showSearch: true, optionFilterProp: 'label' },
        render: (_, record) => record.stageName || '—',
      },
      {
        title: '剩余处理时间',
        tooltip: '只对尚未处理的投递计 48 小时。已沟通、改过阶段或有面试评价后显示「—」。',
        dataIndex: 'remainingHours',
        search: false,
        width: 130,
        render: (_, record) => {
          if (record.remainingHours == null || Number.isNaN(record.remainingHours)) return '—';
          if (record.remainingHours < 0) return <span className='text-red-500'>已超时</span>;
          return <span className='text-orange-500'>剩余{record.remainingHours.toFixed(2)}小时</span>;
        },
      },
      {
        title: '简历',
        dataIndex: 'resumeName',
        search: false,
        width: 90,
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
        width: 110,
        ellipsis: true,
        fieldProps: { options: channels, allowClear: true },
        render: (_, record) => record.channelName || '—',
      },
      {
        title: '招聘负责人',
        dataIndex: 'ownerUserIds',
        valueType: 'select',
        hideInTable: true,
        fieldProps: {
          mode: 'multiple',
          maxTagCount: 'responsive',
          options: users,
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
        },
      },
      {
        title: '提交人',
        dataIndex: 'submitterUserId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: users, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: '投递时间',
        dataIndex: 'submittedRange',
        valueType: 'dateRange',
        hideInTable: true,
        search: { transform: (value: string[]) => ({ startDate: value?.[0], endDate: value?.[1] }) },
      },
    ],
    [ccRoundPlans, channels, jobs, message, roundPlans, stages, users],
  );

  return (
    <>
      <BaseProTable<AppRow>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='候选人'
        scroll={{ x: 1500 }}
        params={presetRequisitionId ? { requisitionId: presetRequisitionId } : undefined}
        request={async (params) => {
          const requisitionId =
            params.requisitionId == null || params.requisitionId === '' ? undefined : Number(params.requisitionId);
          const ownerUserIds = Array.isArray(params.ownerUserIds)
            ? (params.ownerUserIds as unknown[]).map((v) => Number(v)).filter((n) => Number.isFinite(n) && n > 0)
            : undefined;
          const rows = await getHrApplicationsApi({
            candidateName: params.candidateName ? String(params.candidateName).trim() : undefined,
            phone: params.phone ? String(params.phone).trim() : undefined,
            requisitionId: Number.isFinite(requisitionId) ? requisitionId : undefined,
            channelCode: params.channelCode,
            stageCode: params.stageCode,
            submitterUserId: params.submitterUserId,
            ownerUserIds: ownerUserIds?.length ? ownerUserIds : undefined,
            startDate: params.startDate,
            endDate: params.endDate,
            pipelineOnly: params.listScope !== 'ALL',
          });
          const mapped = (Array.isArray(rows) ? rows : []).map((row) => mapRow(row));
          const page = Number(params.current) || 1;
          const size = Number(params.pageSize) || 10;
          const start = (page - 1) * size;
          const pageRows = mapped.slice(start, start + size);
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
                message.warning('请先选择要删除的投递');
                return;
              }
              Modal.confirm({
                title: '批量删除投递',
                content: `确定删除选中的 ${selectedRowKeys.length} 条投递吗？人才档案会保留。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteHrApplicationBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条投递`);
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
            key='blacklist'
            perm='hr:blacklist:in'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要移入黑名单的候选人');
                return;
              }
              setBlacklistOpen(true);
            }}
          >
            批量移入黑名单
          </PermissionButton>,
          <PermissionButton
            key='import'
            perm='hr:application:add'
            onClick={() => setImportOpen(true)}
          >
            导入
          </PermissionButton>,
          <PermissionButton
            key='stage-log'
            perm='hr:application:stage-log'
            onClick={() => setStageLogOpen(true)}
          >
            阶段调整记录
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
        users={users}
        presetRequisitionId={presetRequisitionId}
        requireJob
        onOpenChange={setOpen}
        onSuccess={() => actionRef.current?.reload()}
      />
      <ImportApplicationModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => actionRef.current?.reload()}
      />
      <TalentProfileDrawer
        candidateId={profileId ?? undefined}
        open={profileId != null}
        onClose={() => setProfileId(null)}
        onChanged={() => actionRef.current?.reload()}
        onAi={(id, name) => setAiTarget({ id, name })}
        onReviews={(id, name) => setReviewTarget({ id, name })}
        onStage={(id, name, jobName, stage) => setStageTarget({ id, displayName: name, jobName, currentStage: stage })}
      />
      <AiAnalysisDrawer
        applicationId={aiTarget?.id || 0}
        name={aiTarget?.name || ''}
        open={aiTarget != null}
        onClose={() => {
          setAiTarget(null);
          actionRef.current?.reload();
        }}
      />
      <InterviewReviewDrawer
        applicationId={reviewTarget?.id}
        candidateName={reviewTarget?.name}
        open={reviewTarget != null}
        onClose={() => setReviewTarget(null)}
      />
      <StageAdjustModal
        open={stageTarget != null}
        target={stageTarget}
        stages={stages}
        onClose={() => setStageTarget(null)}
        onSuccess={() => {
          message.success('阶段已调整');
          actionRef.current?.reload();
        }}
      />
      <StageAdjustLogDrawer
        open={stageLogOpen}
        onClose={() => setStageLogOpen(false)}
      />
      <BlacklistReasonModal
        open={blacklistOpen}
        title={`移入黑名单（${selectedRowKeys.length} 人）`}
        okText='确认移入'
        hint='移入后，该人才不能再从任何入口新增，不能创建面试邀约，也不计入考核得分。原因和备注都必填。'
        onClose={() => setBlacklistOpen(false)}
        onSubmit={async (values) => {
          await moveHrBlacklistInApi({
            applicationIds: selectedRowKeys as number[],
            reason: values.reason,
            remark: values.remark,
          });
          message.success('已移入黑名单');
          setSelectedRowKeys([]);
          actionRef.current?.reload();
        }}
      />
      <InviteFormModal
        open={invitePreset != null}
        preset={invitePreset}
        lockCandidate
        lockRound
        onOpenChange={(next) => {
          if (!next) setInvitePreset(null);
        }}
        onSuccess={() => actionRef.current?.reload()}
      />
    </>
  );
});

export default ApplicationPage;
