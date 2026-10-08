import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Tag,
  Timeline,
  Typography,
  Upload,
} from 'antd';
import { PhoneOutlined, PlusOutlined, ReloadOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import InviteFormModal, { type InviteFormPreset } from '@/components/hr/InviteFormModal';
import InterviewReviewDrawer from '@/components/hr/InterviewReviewDrawer';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import ApplicationFormModal from '@/pages/hr/application/components/ApplicationFormModal';
import ImportApplicationModal from '@/pages/hr/application/components/ImportApplicationModal';
import {
  downloadHrPortfolioApi,
  getHrChannelsApi,
  getHrPipelineBoardApi,
  getHrPipelineDetailApi,
  getHrRequisitionsApi,
  getHrStagesApi,
  getHrUsersApi,
  saveHrPipelineOnboardApi,
  saveHrPipelinePhoneApi,
  saveHrPipelineScreenApi,
  uploadHrPortfolioApi,
  type HrPipelineBoard,
  type HrPipelineCandidate,
  type HrPipelineDetail,
  type HrPipelinePhase,
} from '@/api/hr';

const ONBOARD_STATUS = [
  { value: 'SALARY', label: '薪资谈判' },
  { value: 'BG_COLLECT', label: '背调资料收集' },
  { value: 'BG_CHECK', label: '背调中' },
  { value: 'MEDICAL', label: '体检中' },
  { value: 'OFFER_PENDING', label: '待发 Offer' },
  { value: 'PENDING_ONBOARD', label: '入职准备' },
];

const RESULT_OPTS = [
  { value: 'PASS', label: '合适' },
  { value: 'FAIL', label: '不合适' },
];

function fmt(value?: string) {
  if (!value) return '—';
  const t = dayjs(String(value).replace('T', ' '));
  return t.isValid() ? t.format('YYYY-MM-DD HH:mm') : String(value).slice(0, 16);
}

function fmtDate(value?: string) {
  if (!value) return '—';
  return String(value).slice(0, 10);
}

function conclusionLabel(v?: string) {
  if (v === 'PASS') return '通过';
  if (v === 'FAIL') return '淘汰';
  if (v === 'PENDING') return '待定';
  return v || '—';
}

function CandidateCard({
  item,
  onOpen,
  onScreen,
  onPhone,
  onInterview,
  onInvite,
  onOnboard,
}: {
  item: HrPipelineCandidate;
  onOpen: () => void;
  onScreen: () => void;
  onPhone: () => void;
  onInterview: () => void;
  onInvite: () => void;
  onOnboard: () => void;
}) {
  const phase = item.phase;
  return (
    <Card
      size='small'
      hoverable
      className='shadow-sm'
      styles={{ body: { padding: 12 } }}
      onClick={onOpen}
    >
      <div className='mb-1 flex items-start justify-between gap-2'>
        <Typography.Text
          strong
          className='text-sm'
        >
          {item.displayName}
        </Typography.Text>
        <Tag>{item.stageName || item.currentStage || '—'}</Tag>
      </div>
      <div className='mb-2 space-y-0.5 text-xs text-neutral-500'>
        <div>{item.phone || '无电话'}</div>
        <div>投递 {fmtDate(item.submittedAt)}</div>
        {phase === 'PHONE' && item.phoneInterviewAt ? <div>约面 {fmt(item.phoneInterviewAt)}</div> : null}
        {phase === 'INTERVIEW' && item.latestInterviewerName ? (
          <div>
            {item.latestInterviewerName} · {conclusionLabel(item.latestInterviewConclusion)}
          </div>
        ) : null}
        {phase === 'ONBOARD' && item.salaryAmount != null ? <div>Offer {item.salaryAmount}</div> : null}
        {phase === 'ONBOARD' && item.onboardDate ? <div>预计入职 {fmtDate(item.onboardDate)}</div> : null}
      </div>
      <Space
        size={4}
        wrap
        onClick={(e) => e.stopPropagation()}
      >
        {phase === 'SCREEN' ? (
          <Button
            size='small'
            type='link'
            onClick={onScreen}
          >
            初筛
          </Button>
        ) : null}
        {phase === 'PHONE' ? (
          <Button
            size='small'
            type='link'
            icon={<PhoneOutlined />}
            onClick={onPhone}
          >
            电话沟通
          </Button>
        ) : null}
        {phase === 'INTERVIEW' ? (
          <>
            <Button
              size='small'
              type='link'
              onClick={onInvite}
            >
              邀约
            </Button>
            <Button
              size='small'
              type='link'
              onClick={onInterview}
            >
              面试结果
            </Button>
          </>
        ) : null}
        {phase === 'ONBOARD' ? (
          <Button
            size='small'
            type='link'
            onClick={onOnboard}
          >
            更新状态
          </Button>
        ) : null}
        {item.resumeName ? (
          <ResumeViewButton
            applicationId={item.applicationId}
            fileName={item.resumeName}
          />
        ) : null}
      </Space>
    </Card>
  );
}

export default function HrPipelinePage() {
  const { message } = App.useApp();
  const [jobs, setJobs] = useState<{ value: number; label: string }[]>([]);
  const [channels, setChannels] = useState<{ value: string; label: string }[]>([]);
  const [stages, setStages] = useState<{ value: string; label: string }[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);
  const [requisitionId, setRequisitionId] = useState<number>();
  const [board, setBoard] = useState<HrPipelineBoard | null>(null);
  const [loading, setLoading] = useState(false);

  const [manualOpen, setManualOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [detail, setDetail] = useState<HrPipelineDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const [screenTarget, setScreenTarget] = useState<HrPipelineCandidate | null>(null);
  const [phoneTarget, setPhoneTarget] = useState<HrPipelineCandidate | null>(null);
  const [onboardTarget, setOnboardTarget] = useState<HrPipelineCandidate | null>(null);
  const [reviewTarget, setReviewTarget] = useState<HrPipelineCandidate | null>(null);
  const [invitePreset, setInvitePreset] = useState<InviteFormPreset | null>(null);
  const [inviteOpen, setInviteOpen] = useState(false);

  const [screenForm] = Form.useForm();
  const [phoneForm] = Form.useForm();
  const [onboardForm] = Form.useForm();
  const phoneResult = Form.useWatch('result', phoneForm);

  const loadMeta = useCallback(async () => {
    const [reqRows, channelRows, stageRows, userRows] = await Promise.all([
      getHrRequisitionsApi({ status: 'OPEN' }),
      getHrChannelsApi(),
      getHrStagesApi(),
      getHrUsersApi(),
    ]);
    setJobs(
      (reqRows || []).map((r) => ({
        value: Number(r.id),
        label: String(r.job_name ?? r.jobName ?? ''),
      })),
    );
    setChannels(
      (channelRows || []).map((c) => {
        const row = c as { channelCode?: string; channel_code?: string; channelName?: string; channel_name?: string };
        return {
          value: String(row.channelCode ?? row.channel_code),
          label: String(row.channelName ?? row.channel_name),
        };
      }),
    );
    setStages(
      (stageRows || []).map((s) => {
        const row = s as { stageCode?: string; stage_code?: string; stageName?: string; stage_name?: string };
        return {
          value: String(row.stageCode ?? row.stage_code),
          label: String(row.stageName ?? row.stage_name),
        };
      }),
    );
    setUsers(
      (userRows || []).map((u) => {
        const row = u as { userId?: number; user_id?: number; nickname?: string };
        return { value: Number(row.userId ?? row.user_id), label: String(row.nickname ?? '') };
      }),
    );
  }, []);

  const loadBoard = useCallback(async () => {
    if (!requisitionId) {
      setBoard(null);
      return;
    }
    setLoading(true);
    try {
      setBoard(await getHrPipelineBoardApi(requisitionId));
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载流程失败');
    } finally {
      setLoading(false);
    }
  }, [message, requisitionId]);

  useEffect(() => {
    void loadMeta();
  }, [loadMeta]);

  useEffect(() => {
    void loadBoard();
  }, [loadBoard]);

  const openDetail = async (applicationId: number) => {
    try {
      const data = await getHrPipelineDetailApi(applicationId);
      setDetail(data);
      setDetailOpen(true);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载详情失败');
    }
  };

  const phaseMap = useMemo(() => {
    const map = new Map<HrPipelinePhase, HrPipelineBoard['phases'][number]>();
    board?.phases?.forEach((p) => map.set(p.phase, p));
    return map;
  }, [board]);

  return (
    <div className='space-y-4'>
      <div className='flex flex-wrap items-end justify-between gap-3'>
        <div>
          <Typography.Title
            level={5}
            className='!mb-0'
          >
            简历流程
          </Typography.Title>
          <Typography.Text
            type='secondary'
            className='text-xs'
          >
            按单个岗位跟踪：初筛 → 电话沟通 → 面试 → 待入职。点击候选人查看全流程详情。
          </Typography.Text>
        </div>
        <Space wrap>
          <Select
            showSearch
            optionFilterProp='label'
            placeholder='选择岗位'
            className='min-w-[260px]'
            options={jobs}
            value={requisitionId}
            onChange={setRequisitionId}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void loadBoard()}
            disabled={!requisitionId}
          >
            刷新
          </Button>
          <PermissionButton
            perm='hr:application:add'
            icon={<PlusOutlined />}
            disabled={!requisitionId}
            onClick={() => setManualOpen(true)}
          >
            手动录入
          </PermissionButton>
          <PermissionButton
            perm='hr:application:add'
            disabled={!requisitionId}
            onClick={() => setImportOpen(true)}
          >
            批量导入
          </PermissionButton>
        </Space>
      </div>

      {!requisitionId ? (
        <Card>
          <Empty description='请先选择一个岗位' />
        </Card>
      ) : (
        <div className='grid grid-cols-1 gap-3 xl:grid-cols-4'>
          {(['SCREEN', 'PHONE', 'INTERVIEW', 'ONBOARD'] as HrPipelinePhase[]).map((phase) => {
            const col = phaseMap.get(phase);
            return (
              <Card
                key={phase}
                size='small'
                loading={loading}
                title={
                  <span className='text-sm'>
                    {col?.phaseLabel || phase}
                    <Typography.Text
                      type='secondary'
                      className='ml-2 text-xs'
                    >
                      {col?.count ?? 0}
                    </Typography.Text>
                  </span>
                }
                className='min-h-[420px] shadow-sm'
                styles={{ body: { padding: 10 } }}
              >
                <div className='flex max-h-[70vh] flex-col gap-2 overflow-auto'>
                  {(col?.candidates || []).length === 0 ? (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description='暂无候选人'
                    />
                  ) : (
                    col?.candidates.map((item) => (
                      <CandidateCard
                        key={item.applicationId}
                        item={item}
                        onOpen={() => void openDetail(item.applicationId)}
                        onScreen={() => {
                          setScreenTarget(item);
                          screenForm.setFieldsValue({ result: undefined, remark: undefined });
                        }}
                        onPhone={() => {
                          setPhoneTarget(item);
                          phoneForm.setFieldsValue({
                            calledAt: dayjs(),
                            result: undefined,
                            interviewAt: undefined,
                            rejectReason: undefined,
                            remark: undefined,
                          });
                        }}
                        onInterview={() => setReviewTarget(item)}
                        onInvite={() => {
                          setInvitePreset({
                            applicationId: item.applicationId,
                            displayName: item.displayName,
                            jobName: board?.jobName,
                            roundNo: 1,
                          });
                          setInviteOpen(true);
                        }}
                        onOnboard={() => {
                          setOnboardTarget(item);
                          onboardForm.setFieldsValue({
                            subStatus:
                              item.currentStage && ONBOARD_STATUS.some((s) => s.value === item.currentStage)
                                ? item.currentStage
                                : 'SALARY',
                            salaryAmount: item.salaryAmount,
                            onboardDate: item.onboardDate ? dayjs(item.onboardDate) : undefined,
                            remark: undefined,
                          });
                        }}
                      />
                    ))
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}

      <ApplicationFormModal
        open={manualOpen}
        editing={null}
        jobs={jobs}
        channels={channels}
        stages={stages}
        users={users}
        presetRequisitionId={requisitionId}
        onOpenChange={setManualOpen}
        onSuccess={() => void loadBoard()}
      />
      <ImportApplicationModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => void loadBoard()}
      />

      <Modal
        title={`初筛 · ${screenTarget?.displayName || ''}`}
        open={!!screenTarget}
        onCancel={() => setScreenTarget(null)}
        onOk={async () => {
          const values = await screenForm.validateFields();
          await saveHrPipelineScreenApi({
            applicationId: screenTarget!.applicationId,
            result: values.result,
            remark: values.remark,
          });
          message.success('初筛已保存');
          setScreenTarget(null);
          void loadBoard();
        }}
        destroyOnHidden
      >
        <Form
          form={screenForm}
          layout='vertical'
        >
          <Form.Item
            name='result'
            label='初筛状态'
            rules={[{ required: true, message: '请选择' }]}
          >
            <Select options={RESULT_OPTS} />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`电话沟通 · ${phoneTarget?.displayName || ''}`}
        open={!!phoneTarget}
        onCancel={() => setPhoneTarget(null)}
        onOk={async () => {
          const values = await phoneForm.validateFields();
          await saveHrPipelinePhoneApi({
            applicationId: phoneTarget!.applicationId,
            calledAt: values.calledAt.format('YYYY-MM-DD HH:mm:ss'),
            result: values.result,
            interviewAt: values.interviewAt ? values.interviewAt.format('YYYY-MM-DD HH:mm:ss') : undefined,
            rejectReason: values.rejectReason,
            remark: values.remark,
          });
          message.success(values.result === 'PASS' ? '已记录并创建面试提醒' : '已记录淘汰');
          setPhoneTarget(null);
          void loadBoard();
        }}
        destroyOnHidden
      >
        <Form
          form={phoneForm}
          layout='vertical'
        >
          <Form.Item
            name='calledAt'
            label='沟通时间'
            rules={[{ required: true, message: '请选择沟通时间' }]}
          >
            <DatePicker
              showTime
              className='w-full'
            />
          </Form.Item>
          <Form.Item
            name='result'
            label='沟通结果'
            rules={[{ required: true, message: '请选择' }]}
          >
            <Select options={RESULT_OPTS} />
          </Form.Item>
          {phoneResult === 'PASS' ? (
            <Form.Item
              name='interviewAt'
              label='约面时间'
              rules={[{ required: true, message: '请填写约面时间' }]}
            >
              <DatePicker
                showTime
                className='w-full'
              />
            </Form.Item>
          ) : null}
          {phoneResult === 'FAIL' ? (
            <Form.Item
              name='rejectReason'
              label='不合适原因'
              rules={[{ required: true, message: '请填写原因' }]}
            >
              <Input.TextArea rows={3} />
            </Form.Item>
          ) : null}
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`待入职 · ${onboardTarget?.displayName || ''}`}
        open={!!onboardTarget}
        onCancel={() => setOnboardTarget(null)}
        onOk={async () => {
          const values = await onboardForm.validateFields();
          await saveHrPipelineOnboardApi({
            applicationId: onboardTarget!.applicationId,
            subStatus: values.subStatus,
            salaryAmount: values.salaryAmount,
            onboardDate: values.onboardDate ? values.onboardDate.format('YYYY-MM-DD') : undefined,
            remark: values.remark,
          });
          message.success('待入职信息已更新');
          setOnboardTarget(null);
          void loadBoard();
        }}
        destroyOnHidden
      >
        <Form
          form={onboardForm}
          layout='vertical'
        >
          <Form.Item
            name='subStatus'
            label='状态'
            rules={[{ required: true, message: '请选择状态' }]}
          >
            <Select options={ONBOARD_STATUS} />
          </Form.Item>
          <Form.Item
            name='salaryAmount'
            label='最终 Offer 金额'
          >
            <InputNumber
              className='w-full'
              min={0}
              precision={2}
            />
          </Form.Item>
          <Form.Item
            name='onboardDate'
            label='预计入职日期'
          >
            <DatePicker className='w-full' />
          </Form.Item>
          <Form.Item label='上传背调/入职资料'>
            <Upload
              beforeUpload={async (file) => {
                if (!onboardTarget) return false;
                await uploadHrPortfolioApi(onboardTarget.applicationId, file);
                message.success(`已上传 ${file.name}`);
                return false;
              }}
              showUploadList={false}
            >
              <Button icon={<UploadOutlined />}>选择文件</Button>
            </Upload>
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
          >
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <InterviewReviewDrawer
        open={!!reviewTarget}
        applicationId={reviewTarget?.applicationId}
        candidateName={reviewTarget?.displayName}
        onClose={() => {
          setReviewTarget(null);
          void loadBoard();
        }}
      />
      <InviteFormModal
        open={inviteOpen}
        preset={invitePreset}
        lockCandidate
        onOpenChange={(open) => {
          setInviteOpen(open);
          if (!open) setInvitePreset(null);
        }}
        onSuccess={() => void loadBoard()}
      />

      <Drawer
        title={
          <span className='inline-flex items-center gap-2'>
            <UserOutlined />
            {detail?.displayName || '候选人详情'}
          </span>
        }
        width={720}
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        destroyOnHidden
      >
        {!detail ? (
          <Empty />
        ) : (
          <div className='space-y-4'>
            <Descriptions
              size='small'
              column={2}
              bordered
            >
              <Descriptions.Item label='岗位'>{detail.jobName || '—'}</Descriptions.Item>
              <Descriptions.Item label='阶段'>
                {detail.phaseLabel} / {detail.stageName || detail.currentStage}
              </Descriptions.Item>
              <Descriptions.Item label='电话'>{detail.phone || '—'}</Descriptions.Item>
              <Descriptions.Item label='邮箱'>{detail.email || '—'}</Descriptions.Item>
              <Descriptions.Item label='初筛'>
                {detail.screenResult === 'PASS' ? '合适' : detail.screenResult === 'FAIL' ? '不合适' : '—'}
              </Descriptions.Item>
              <Descriptions.Item label='投递日'>{fmtDate(detail.submittedAt)}</Descriptions.Item>
            </Descriptions>

            {detail.phoneScreen ? (
              <Card
                size='small'
                title='电话沟通'
              >
                <Descriptions
                  size='small'
                  column={1}
                >
                  <Descriptions.Item label='时间'>{fmt(detail.phoneScreen.calledAt)}</Descriptions.Item>
                  <Descriptions.Item label='结果'>
                    {detail.phoneScreen.result === 'PASS' ? '合适' : '不合适'}
                  </Descriptions.Item>
                  <Descriptions.Item label='约面'>{fmt(detail.phoneScreen.interviewAt)}</Descriptions.Item>
                  <Descriptions.Item label='原因'>{detail.phoneScreen.rejectReason || '—'}</Descriptions.Item>
                </Descriptions>
              </Card>
            ) : null}

            <Card
              size='small'
              title='面试记录与评价'
            >
              {(detail.interviews || []).length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description='暂无面试记录'
                />
              ) : (
                <div className='space-y-2'>
                  {detail.interviews?.map((it, idx) => (
                    <div
                      key={`${it.roundNo}-${idx}`}
                      className='rounded border border-neutral-100 bg-neutral-50 px-3 py-2 text-sm'
                    >
                      <div className='mb-1 font-medium'>
                        {it.roundName} · {it.interviewerName || '—'} · {conclusionLabel(it.conclusion)}
                      </div>
                      <div className='text-xs text-neutral-500'>{fmt(it.interviewedAt)}</div>
                      {it.failReason ? <div className='text-xs text-red-500'>淘汰原因：{it.failReason}</div> : null}
                      {it.comment ? <div className='mt-1 text-neutral-700'>{it.comment}</div> : null}
                    </div>
                  ))}
                </div>
              )}
            </Card>

            <Card
              size='small'
              title='全流程时间线'
            >
              <Timeline
                items={(detail.timeline || []).map((t) => ({
                  children: (
                    <div>
                      <div className='font-medium'>{t.summary || t.stageName || t.stageCode}</div>
                      <div className='text-xs text-neutral-400'>
                        {fmt(t.eventAt)} · {t.source || ''}
                      </div>
                    </div>
                  ),
                }))}
              />
            </Card>

            <Card
              size='small'
              title='相关文档'
            >
              {(detail.documents || []).length === 0 ? (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description='暂无文档'
                />
              ) : (
                <div className='space-y-1'>
                  {detail.documents?.map((doc) => (
                    <div
                      key={`${doc.kind}-${doc.id}`}
                      className='flex items-center justify-between gap-2 text-sm'
                    >
                      <span className='truncate'>
                        [{doc.kind}] {doc.fileName}
                      </span>
                      {doc.kind === '简历' && detail.resumeName ? (
                        <ResumeViewButton
                          applicationId={detail.applicationId}
                          fileName={detail.resumeName}
                        />
                      ) : doc.downloadable ? (
                        <Button
                          type='link'
                          size='small'
                          onClick={() => void downloadHrPortfolioApi(doc.id, doc.fileName)}
                        >
                          下载
                        </Button>
                      ) : null}
                    </div>
                  ))}
                </div>
              )}
            </Card>
          </div>
        )}
      </Drawer>
    </div>
  );
}
