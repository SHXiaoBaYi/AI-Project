import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { AiAnalysisDrawer } from '@/components/hr/AiAnalysisDrawer';
import InterviewReviewDrawer from '@/components/hr/InterviewReviewDrawer';
import StageAdjustModal from '@/components/hr/StageAdjustModal';
import ApplicationFormModal, {
  type ApplicationFormValues,
} from '@/pages/hr/application/components/ApplicationFormModal';
import RecommendJobModal from '@/pages/hr/application/components/RecommendJobModal';
import TalentProfileDrawer from '@/pages/hr/application/components/TalentProfileDrawer';
import {
  deleteHrTalentApi,
  deleteHrTalentBatchApi,
  getHrChannelsApi,
  getHrRequisitionsApi,
  getHrStageCatalogApi,
  getHrStagesApi,
  getHrUsersApi,
  pageHrTalentsApi,
  type HrTalentRow,
} from '@/api/hr';

const POOL_OPTIONS = [
  { value: 'LIBRARY', label: '在库 / 可激活' },
  { value: 'POOL', label: '在库可推荐' },
  { value: 'REACTIVATABLE', label: '淘汰可激活' },
  { value: 'IN_PROCESS', label: '流程中' },
  { value: 'ONBOARDED', label: '已入职' },
  { value: 'ALL', label: '全部' },
];

const POOL_CLASS: Record<string, string> = {
  POOL: 'text-blue-600',
  IN_PROCESS: 'text-orange-500',
  REACTIVATABLE: 'text-black/65',
  ONBOARDED: 'text-green-600',
};

function poolQuery(raw: unknown) {
  const status = raw == null || raw === '' ? 'LIBRARY' : String(raw);
  if (status === 'ALL') return {};
  if (status === 'LIBRARY') return { poolStatuses: ['POOL', 'REACTIVATABLE'] };
  return { poolStatus: status };
}

const TalentPage = memo(function TalentPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ApplicationFormValues | null>(null);
  const [jobs, setJobs] = useState<{ value: number; label: string }[]>([]);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [stages, setStages] = useState<{ value: string; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [aiTarget, setAiTarget] = useState<{ id: number; name: string } | null>(null);
  const [reviewTarget, setReviewTarget] = useState<{ id: number; name: string } | null>(null);
  const [stageTarget, setStageTarget] = useState<{
    id: number;
    displayName?: string;
    jobName?: string;
    currentStage?: string;
  } | null>(null);
  const [profileId, setProfileId] = useState<number | null>(null);
  const [recommendTarget, setRecommendTarget] = useState<HrTalentRow | null>(null);

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
          return { value: Number(row.userId ?? row.user_id), label: row.nickname || row.username || '' };
        }),
      ),
    );
  }, []);

  const columns: ProColumnType<HrTalentRow>[] = useMemo(
    () => [
      {
        title: '操作',
        valueType: 'option',
        width: 220,
        render: (_, record) => (
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
        ),
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
            className='cursor-pointer border-0 bg-transparent p-0 text-blue-600'
            onClick={() => setProfileId(record.candidateId)}
          >
            {record.displayName}
          </button>
        ),
      },
      {
        title: '在库状态',
        dataIndex: 'poolStatus',
        valueType: 'select',
        width: 120,
        initialValue: 'LIBRARY',
        fieldProps: { options: POOL_OPTIONS, allowClear: false },
        render: (_, record) => (
          <span className={POOL_CLASS[record.poolStatus || ''] || ''}>{record.poolStatusName || '—'}</span>
        ),
      },
      {
        title: '目前公司',
        dataIndex: 'company',
        hideInTable: true,
        fieldProps: { allowClear: true },
      },
      {
        title: '公司',
        dataIndex: 'currentCompany',
        search: false,
        width: 160,
        ellipsis: true,
        render: (_, record) => record.currentCompany || '—',
      },
      {
        title: '目前职位',
        dataIndex: 'title',
        hideInTable: true,
        fieldProps: { allowClear: true },
      },
      {
        title: '职位',
        dataIndex: 'currentTitle',
        search: false,
        width: 140,
        ellipsis: true,
        render: (_, record) => record.currentTitle || '—',
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
        title: '投递次数',
        dataIndex: 'applicationCount',
        search: false,
        width: 90,
        render: (_, record) => record.applicationCount ?? 0,
      },
      {
        title: '岗位',
        dataIndex: 'requisitionId',
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: jobs, allowClear: true, showSearch: true, optionFilterProp: 'label' },
      },
    ],
    [jobs, message],
  );

  return (
    <>
      <BaseProTable<HrTalentRow>
        rowKey='candidateId'
        actionRef={actionRef}
        columns={columns}
        headerTitle='人才库'
        scroll={{ x: 1400 }}
        request={async (params) => {
          const requisitionId =
            params.requisitionId == null || params.requisitionId === '' ? undefined : Number(params.requisitionId);
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
            requisitionId: Number.isFinite(requisitionId) ? requisitionId : undefined,
            ...poolQuery(params.poolStatus),
          });
          const pageRows = result.rows ?? [];
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.candidateId));
          return { data: pageRows, success: true, total: result.total ?? 0 };
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
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
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
        onOpenChange={setOpen}
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
    </>
  );
});

export default TalentPage;
