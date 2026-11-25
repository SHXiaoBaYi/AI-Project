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
import RecommendJobModal from './components/RecommendJobModal';
import TalentProfileDrawer from './components/TalentProfileDrawer';
import {
  deleteHrTalentApi,
  deleteHrTalentBatchApi,
  downloadHrPortfolioApi,
  getHrChannelsApi,
  getHrRequisitionsApi,
  getHrStageCatalogApi,
  getHrStagesApi,
  getHrUsersApi,
  moveHrBlacklistInApi,
  pageHrTalentsApi,
  type HrTalentRow,
} from '@/api/hr';

const POOL_OPTIONS = [
  { value: 'POOL', label: '在库可推荐' },
  { value: 'IN_PROCESS', label: '流程中' },
  { value: 'REACTIVATABLE', label: '淘汰可激活' },
  { value: 'ONBOARDED', label: '已入职' },
];

const POOL_CLASS: Record<string, string> = {
  POOL: 'text-blue-600',
  IN_PROCESS: 'text-orange-500',
  REACTIVATABLE: 'text-black/65',
  ONBOARDED: 'text-green-600',
};

const ApplicationPage = memo(function ApplicationPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [selectedRows, setSelectedRows] = useState<HrTalentRow[]>([]);
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
  const [recommendTarget, setRecommendTarget] = useState<HrTalentRow | null>(null);
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

  const columns: ProColumnType<HrTalentRow>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 280,
        render: (_, record) => {
          const roundNo = nextInviteRound(record.activeStage);
          const maxRound = maxRoundOf(roundPlans, record.activeRequisitionId);
          const inviteable =
            canCreateInvite(record.activeStage, maxRound) &&
            record.activeRequisitionId != null &&
            record.activeApplicationId != null;
          return (
            <ActionButtons
              maxVisible={4}
              items={[
                {
                  key: 'profile',
                  label: '档案',
                  onClick: () => setProfileId(record.candidateId),
                },
                {
                  key: 'recommend',
                  label: '推荐到岗位',
                  perm: 'hr:application:add',
                  onClick: () => setRecommendTarget(record),
                },
                ...(inviteable && roundNo != null
                  ? [
                      {
                        key: 'invite',
                        label: '邀约',
                        perm: 'hr:invite:add',
                        onClick: () => {
                          const reqId = record.activeRequisitionId!;
                          setInvitePreset({
                            applicationId: record.activeApplicationId!,
                            displayName: record.displayName,
                            jobName: record.activeJobName,
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
                  confirmTitle: `确认删除人才「${record.displayName}」？投递记录会一并停用。`,
                  onClick: async () => {
                    await deleteHrTalentApi(record.candidateId);
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
        title: '关键词',
        dataIndex: 'keyword',
        hideInTable: true,
        fieldProps: { allowClear: true, placeholder: '姓名、公司、学校、标签' },
      },
      {
        title: '手机号',
        dataIndex: 'phone',
        hideInTable: true,
        fieldProps: { allowClear: true, maxLength: 20, placeholder: '支持片段' },
      },
      {
        title: '人才',
        dataIndex: 'displayName',
        width: 120,
        render: (_, record) => (
          <button
            type='button'
            className='inline-flex cursor-pointer items-center gap-1.5 border-0 bg-transparent p-0'
            onClick={() => setProfileId(record.candidateId)}
          >
            <span
              className={`inline-block h-1.5 w-1.5 rounded-full ${record.pending ? 'bg-red-500' : 'bg-blue-500'}`}
            />
            <span className='text-blue-600'>{record.displayName}</span>
          </button>
        ),
      },
      {
        title: '在库状态',
        dataIndex: 'poolStatus',
        valueType: 'select',
        width: 120,
        fieldProps: { options: POOL_OPTIONS, allowClear: true },
        render: (_, record) => (
          <span className={POOL_CLASS[record.poolStatus || ''] || ''}>{record.poolStatusName || '—'}</span>
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
        title: '人选编号',
        dataIndex: 'candidateNo',
        search: false,
        width: 110,
        render: (_, record) => record.candidateNo || '—',
      },
      {
        title: '目前职位',
        dataIndex: 'title',
        hideInTable: true,
        fieldProps: { allowClear: true },
      },
      {
        title: '目前职位',
        dataIndex: 'currentTitle',
        search: false,
        width: 140,
        ellipsis: true,
        render: (_, record) => record.currentTitle || '—',
      },
      {
        title: '目前公司',
        dataIndex: 'company',
        hideInTable: true,
        fieldProps: { allowClear: true },
      },
      {
        title: '目前公司',
        dataIndex: 'currentCompany',
        search: false,
        width: 160,
        ellipsis: true,
        render: (_, record) => record.currentCompany || '—',
      },
      {
        title: '学校',
        dataIndex: 'school',
        hideInTable: true,
        fieldProps: { allowClear: true, placeholder: '学校或专业' },
      },
      {
        title: '学校 / 专业',
        dataIndex: 'schoolName',
        search: false,
        width: 160,
        ellipsis: true,
        render: (_, record) => [record.schoolName, record.major].filter(Boolean).join(' · ') || '—',
      },
      {
        title: '标签',
        dataIndex: 'tags',
        hideInTable: true,
        fieldProps: { allowClear: true },
      },
      {
        title: '标签',
        dataIndex: 'skillTags',
        search: false,
        width: 160,
        ellipsis: true,
        render: (_, record) => record.skillTags || '—',
      },
      {
        title: '投递',
        dataIndex: 'applicationCount',
        search: false,
        width: 70,
        render: (_, record) => record.applicationCount ?? 0,
      },
      {
        title: '当前岗位',
        dataIndex: 'activeJobName',
        search: false,
        width: 140,
        ellipsis: true,
        render: (_, record) => record.activeJobName || '未挂岗',
      },
      {
        title: '阶段',
        dataIndex: 'activeStageName',
        search: false,
        width: 140,
        ellipsis: true,
        render: (_, record) => record.activeStageName || '—',
      },
      {
        title: '最近联系',
        dataIndex: 'lastContactAt',
        search: false,
        width: 160,
        render: (_, record) => record.lastContactAt || '—',
      },
      {
        title: '下次跟进',
        dataIndex: 'nextFollowAt',
        search: false,
        width: 110,
        render: (_, record) => record.nextFollowAt || '—',
      },
      {
        title: '剩余处理时间',
        tooltip: '只对流程中、尚未处理的人才计 48 小时。已沟通、改过阶段或有面试评价后显示「—」。',
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
        title: '岗位',
        dataIndex: 'requisitionId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: jobs, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: '简历',
        dataIndex: 'resumeName',
        search: false,
        width: 90,
        render: (_, record) =>
          record.activeApplicationId ? (
            <ResumeViewButton
              applicationId={record.activeApplicationId}
              fileName={record.resumeName}
            />
          ) : (
            '—'
          ),
      },
      {
        title: '作品集',
        dataIndex: 'portfolios',
        search: false,
        width: 180,
        ellipsis: true,
        render: (_, record) => {
          if (!record.portfolios?.length) return <span className='text-black/45'>无</span>;
          return (
            <div className='flex max-w-[180px] flex-col gap-0.5'>
              {record.portfolios.map((item) => (
                <button
                  key={item.id}
                  type='button'
                  title={item.fileName}
                  className='cursor-pointer truncate border-0 bg-transparent p-0 text-left text-[#1677ff]'
                  onClick={() => {
                    void downloadHrPortfolioApi(item.id, item.fileName).catch((e) =>
                      message.error(e instanceof Error ? e.message : '下载失败'),
                    );
                  }}
                >
                  {item.fileName}
                </button>
              ))}
            </div>
          );
        },
      },
      {
        title: '渠道',
        dataIndex: 'channelCode',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: channels, allowClear: true },
      },
      {
        title: '阶段',
        dataIndex: 'stageCode',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: stages, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
      {
        title: 'AI分析',
        dataIndex: 'aiScore',
        search: false,
        width: 110,
        render: (_, record) =>
          record.activeApplicationId ? (
            <button
              type='button'
              className='cursor-pointer border-0 bg-transparent p-0 text-left text-[#1677ff]'
              onClick={() => setAiTarget({ id: record.activeApplicationId!, name: record.displayName })}
            >
              {record.aiScore == null
                ? '未分析'
                : `${record.aiScore}分${record.aiCount && record.aiCount > 1 ? ` · ${record.aiCount}份` : ''}`}
            </button>
          ) : (
            '—'
          ),
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

  const blacklistApplicationIds = selectedRows.flatMap((row) => row.applicationIds ?? []);

  return (
    <>
      <BaseProTable<HrTalentRow>
        rowKey='candidateId'
        actionRef={actionRef}
        columns={columns}
        headerTitle='人才库'
        scroll={{ x: 2200 }}
        params={presetRequisitionId ? { requisitionId: presetRequisitionId } : undefined}
        request={async (params) => {
          const requisitionId =
            params.requisitionId == null || params.requisitionId === '' ? undefined : Number(params.requisitionId);
          const ownerUserIds = Array.isArray(params.ownerUserIds)
            ? (params.ownerUserIds as unknown[]).map((v) => Number(v)).filter((n) => Number.isFinite(n) && n > 0)
            : undefined;
          const result = await pageHrTalentsApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            candidateName: params.displayName,
            phone: params.phone ? String(params.phone).trim() : undefined,
            keyword: params.keyword ? String(params.keyword).trim() : undefined,
            company: params.company ? String(params.company).trim() : undefined,
            title: params.title ? String(params.title).trim() : undefined,
            school: params.school ? String(params.school).trim() : undefined,
            tags: params.tags ? String(params.tags).trim() : undefined,
            poolStatus: params.poolStatus,
            requisitionId: Number.isFinite(requisitionId) ? requisitionId : undefined,
            channelCode: params.channelCode,
            stageCode: params.stageCode,
            submitterUserId: params.submitterUserId,
            ownerUserIds: ownerUserIds?.length ? ownerUserIds : undefined,
            startDate: params.startDate,
            endDate: params.endDate,
          });
          const pageRows = result.rows ?? [];
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.candidateId));
          return { data: pageRows, success: true, total: result.total ?? 0 };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, rows) => {
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
            setSelectedRows((prev) => {
              const kept = prev.filter((row) => !currentPageKeysRef.current.has(row.candidateId));
              const incoming = (rows as HrTalentRow[]).filter((row) => (keys as number[]).includes(row.candidateId));
              const map = new Map<number, HrTalentRow>();
              [...kept, ...incoming].forEach((row) => map.set(row.candidateId, row));
              return [...map.values()];
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
                message.warning('请先选择要删除的人才');
                return;
              }
              Modal.confirm({
                title: '批量删除人才',
                content: `确定要删除选中的 ${selectedRowKeys.length} 位人才吗？他们的投递记录会一并停用。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteHrTalentBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 位人才`);
                  setSelectedRowKeys([]);
                  setSelectedRows([]);
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
                message.warning('请先选择要移入黑名单的人才');
                return;
              }
              if (blacklistApplicationIds.length === 0) {
                message.warning('所选人才还没有投递记录，不能移入黑名单');
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
            入库
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
      <RecommendJobModal
        open={recommendTarget != null}
        candidateId={recommendTarget?.candidateId}
        candidateName={recommendTarget?.displayName}
        jobs={jobs}
        channels={channels}
        users={users}
        onClose={() => setRecommendTarget(null)}
        onSuccess={() => actionRef.current?.reload()}
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
            applicationIds: blacklistApplicationIds,
            reason: values.reason,
            remark: values.remark,
          });
          message.success('已移入黑名单');
          setSelectedRowKeys([]);
          setSelectedRows([]);
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
