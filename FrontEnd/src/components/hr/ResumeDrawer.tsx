import { useEffect, useRef, useState } from 'react';
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
  Modal,
  Select,
  Space,
  Spin,
} from 'antd';
import { HistoryOutlined, PhoneOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { renderAsync } from 'docx-preview';
import PermissionButton from '@/components/Buttons/PermissionButton';
import InviteFormModal, { type InviteFormPreset } from '@/components/hr/InviteFormModal';
import { nextInviteRound } from '@/components/hr/inviteRound';
import {
  fetchHrResumeApi,
  getHrApplicationApi,
  getHrDictOptionsApi,
  getHrPipelineDetailApi,
  saveHrPipelinePhoneApi,
  saveHrPipelineScreenApi,
  type HrBaseDict,
  type HrPipelineDetail,
} from '@/api/hr';

function fmtDt(value?: string) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 19);
}

function isPdf(fileName: string, contentType: string, _blob?: Blob) {
  const lower = fileName.toLowerCase();
  if (contentType.includes('pdf') || lower.endsWith('.pdf')) {
    return true;
  }
  return false;
}

async function blobLooksLikePdf(blob: Blob): Promise<boolean> {
  const head = new Uint8Array(await blob.slice(0, 5).arrayBuffer());
  // %PDF-
  return head.length >= 4 && head[0] === 0x25 && head[1] === 0x50 && head[2] === 0x44 && head[3] === 0x46;
}

function isDocx(fileName: string, contentType: string) {
  const lower = fileName.toLowerCase();
  return (
    contentType.includes('wordprocessingml') || contentType.includes('officedocument.word') || lower.endsWith('.docx')
  );
}

function isLegacyDoc(fileName: string, contentType: string) {
  const lower = fileName.toLowerCase();
  return (contentType.includes('msword') && !isDocx(fileName, contentType)) || lower.endsWith('.doc');
}

export function ResumeViewButton({ applicationId, fileName }: { applicationId?: number; fileName?: string }) {
  const [open, setOpen] = useState(false);
  if (!applicationId || !fileName) return <span>没有简历</span>;
  return (
    <>
      <Button
        type='link'
        className='h-auto max-w-full truncate p-0'
        onClick={() => setOpen(true)}
      >
        查看简历
      </Button>
      <ResumeDrawer
        applicationId={applicationId}
        fileName={fileName}
        open={open}
        onClose={() => setOpen(false)}
      />
    </>
  );
}

const PHONE_RESULT_OPTS = [
  { value: 'PASS', label: '合适' },
  { value: 'FAIL', label: '不合适' },
];

export function ResumeDrawer({
  applicationId,
  fileName,
  open,
  onClose,
  onActionSuccess,
  showCommRecords,
}: {
  applicationId: number;
  fileName?: string;
  open: boolean;
  onClose: () => void;
  onActionSuccess?: () => void;
  /** 岗位看板打开简历时显示「沟通记录」 */
  showCommRecords?: boolean;
}) {
  const [detail, setDetail] = useState<Record<string, unknown> | null>(null);
  const [pdfUrl, setPdfUrl] = useState<string>();
  const [docxBlob, setDocxBlob] = useState<Blob | null>(null);
  const [mode, setMode] = useState<'pdf' | 'docx' | 'unsupported' | ''>('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const docxRef = useRef<HTMLDivElement>(null);
  const { message } = App.useApp();
  const [invitePreset, setInvitePreset] = useState<InviteFormPreset | null>(null);
  const [unsuitableOpen, setUnsuitableOpen] = useState(false);
  const [unsuitableSaving, setUnsuitableSaving] = useState(false);
  const [phoneOpen, setPhoneOpen] = useState(false);
  const [phoneSaving, setPhoneSaving] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [recordLoading, setRecordLoading] = useState(false);
  const [pipelineDetail, setPipelineDetail] = useState<HrPipelineDetail | null>(null);
  const [reasons, setReasons] = useState<HrBaseDict[]>([]);
  const [unsuitableForm] = Form.useForm<{ rejectReason: string; remark: string }>();
  const [phoneForm] = Form.useForm<{
    calledAt: dayjs.Dayjs;
    result: 'PASS' | 'FAIL';
    interviewAt?: dayjs.Dayjs;
    rejectReason?: string;
    remark: string;
  }>();
  const phoneResult = Form.useWatch('result', phoneForm);

  useEffect(() => {
    if (!open) return;
    let objectUrl = '';
    let cancelled = false;
    setError('');
    setDetail(null);
    setPdfUrl(undefined);
    setDocxBlob(null);
    setMode('');
    setLoading(true);

    (async () => {
      try {
        const [appDetail, resume] = await Promise.all([
          getHrApplicationApi(applicationId).catch(() => null),
          fetchHrResumeApi(applicationId),
        ]);
        if (cancelled) return;
        if (appDetail) setDetail(appDetail);
        const name = String(appDetail?.file_name || fileName || '');
        const { blob, contentType } = resume;

        if (isPdf(name, contentType)) {
          if (!(await blobLooksLikePdf(blob))) {
            setMode('unsupported');
            setError('简历内容不是有效 PDF（可能文件只在远程服务器，请确认本机可访问公网附件）');
            return;
          }
          const file = new Blob([blob], { type: 'application/pdf' });
          objectUrl = window.URL.createObjectURL(file);
          setPdfUrl(objectUrl);
          setMode('pdf');
          return;
        }

        if (isDocx(name, contentType)) {
          setDocxBlob(blob);
          setMode('docx');
          return;
        }

        if (isLegacyDoc(name, contentType)) {
          setMode('unsupported');
          setError('.doc 格式暂不支持在线预览，请下载后用 Word 打开');
          return;
        }

        setMode('unsupported');
        setError('该文件类型暂不支持在线预览');
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '简历打不开');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      if (objectUrl) window.URL.revokeObjectURL(objectUrl);
    };
  }, [applicationId, fileName, open]);

  useEffect(() => {
    if (!open || mode !== 'docx' || !docxBlob || !docxRef.current) return;
    let cancelled = false;
    const container = docxRef.current;
    container.innerHTML = '';
    setLoading(true);
    renderAsync(docxBlob, container, undefined, {
      className: 'hr-docx-preview',
      inWrapper: true,
      ignoreWidth: false,
      breakPages: true,
    })
      .then(() => {
        if (!cancelled) setLoading(false);
      })
      .catch(() => {
        if (!cancelled) {
          setLoading(false);
          setError('docx 预览失败，请下载后查看');
          setMode('unsupported');
        }
      });
    return () => {
      cancelled = true;
      container.innerHTML = '';
    };
  }, [docxBlob, mode, open]);

  const stage = String(detail?.current_stage || '');
  const alreadyUnsuitable = /FAIL/i.test(stage);
  const alreadyOnboarded = stage === 'ONBOARDED';
  const canAct = !alreadyUnsuitable && !alreadyOnboarded;

  const ensureReasons = () => {
    if (reasons.length === 0) {
      void getHrDictOptionsApi('UNSUITABLE').then((rows) => setReasons(rows || []));
    }
  };

  const openInvite = () => {
    const roundNo = nextInviteRound(stage) ?? 1;
    setInvitePreset({
      applicationId,
      displayName: String(detail?.display_name || ''),
      jobName: String(detail?.job_name || ''),
      roundNo,
      durationMin: 60,
    });
  };

  const openUnsuitable = () => {
    unsuitableForm.resetFields();
    ensureReasons();
    setUnsuitableOpen(true);
  };

  const openPhone = () => {
    phoneForm.resetFields();
    phoneForm.setFieldsValue({ calledAt: dayjs(), result: undefined, remark: undefined });
    ensureReasons();
    setPhoneOpen(true);
  };

  const openRecords = async () => {
    setRecordOpen(true);
    setRecordLoading(true);
    try {
      setPipelineDetail(await getHrPipelineDetailApi(applicationId));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '加载沟通记录失败');
      setPipelineDetail(null);
    } finally {
      setRecordLoading(false);
    }
  };

  const submitUnsuitable = async () => {
    const values = await unsuitableForm.validateFields();
    setUnsuitableSaving(true);
    try {
      await saveHrPipelineScreenApi({
        applicationId,
        result: 'FAIL',
        rejectReason: values.rejectReason,
        remark: values.remark,
      });
      message.success('已标记为不合适');
      setUnsuitableOpen(false);
      onActionSuccess?.();
      onClose();
    } finally {
      setUnsuitableSaving(false);
    }
  };

  const submitPhone = async () => {
    const values = await phoneForm.validateFields();
    setPhoneSaving(true);
    try {
      await saveHrPipelinePhoneApi({
        applicationId,
        calledAt: values.calledAt.format('YYYY-MM-DD HH:mm:ss'),
        result: values.result,
        interviewAt: values.interviewAt ? values.interviewAt.format('YYYY-MM-DD HH:mm:ss') : undefined,
        rejectReason: values.rejectReason,
        remark: values.remark,
      });
      message.success(values.result === 'PASS' ? '已记录电话沟通并创建面试提醒' : '已记录电话沟通');
      setPhoneOpen(false);
      onActionSuccess?.();
      onClose();
    } finally {
      setPhoneSaving(false);
    }
  };

  const download = async () => {
    try {
      const { blob, contentType } = await fetchHrResumeApi(applicationId);
      const name = String(detail?.file_name || fileName || 'resume');
      const url = window.URL.createObjectURL(new Blob([blob], { type: contentType || blob.type }));
      const link = document.createElement('a');
      link.href = url;
      link.download = name;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : '下载失败');
    }
  };

  return (
    <>
      <Drawer
        title={String(detail?.display_name || '简历')}
        open={open}
        onClose={onClose}
        size='large'
        className='[&_.ant-drawer-content-wrapper]:!w-[90vw]'
        styles={{
          wrapper: { width: '90vw', maxWidth: '90vw' },
          body: { display: 'flex', flexDirection: 'column', overflow: 'hidden' },
        }}
        extra={
          <Space wrap>
            {showCommRecords ? (
              <Button
                icon={<HistoryOutlined />}
                onClick={() => void openRecords()}
              >
                沟通记录
              </Button>
            ) : null}
            {canAct ? (
              <PermissionButton
                perm='hr:pipeline:edit'
                icon={<PhoneOutlined />}
                onClick={openPhone}
              >
                电话沟通
              </PermissionButton>
            ) : null}
            {canAct ? (
              <PermissionButton
                perm='hr:invite:add'
                type='primary'
                onClick={openInvite}
              >
                发起面试邀约
              </PermissionButton>
            ) : null}
            {canAct ? (
              <PermissionButton
                perm='hr:pipeline:edit'
                danger
                onClick={openUnsuitable}
              >
                标记此人不合适
              </PermissionButton>
            ) : null}
            <Button
              type='link'
              onClick={download}
            >
              下载
            </Button>
          </Space>
        }
      >
        <Descriptions
          size='small'
          column={2}
          className='mb-3 shrink-0'
        >
          <Descriptions.Item label='姓名'>{String(detail?.display_name || '')}</Descriptions.Item>
          <Descriptions.Item label='岗位'>{String(detail?.job_name || '—')}</Descriptions.Item>
          <Descriptions.Item label='电话'>{String(detail?.phone || '—')}</Descriptions.Item>
          <Descriptions.Item label='邮箱'>{String(detail?.email || '—')}</Descriptions.Item>
          <Descriptions.Item label='学校'>{String(detail?.school_name_raw || '—')}</Descriptions.Item>
          <Descriptions.Item label='专业'>{String(detail?.major || '—')}</Descriptions.Item>
        </Descriptions>
        {loading ? <p className='mb-2 shrink-0 text-sm text-black/45'>正在加载简历…</p> : null}
        {error ? <p className='mb-2 shrink-0 text-sm text-red-500'>{error}</p> : null}
        {mode === 'pdf' && pdfUrl ? (
          <iframe
            title='简历'
            src={pdfUrl}
            className='min-h-0 w-full flex-1 border-0'
          />
        ) : null}
        {mode === 'docx' ? (
          <div
            ref={docxRef}
            className='hr-docx-host min-h-0 w-full flex-1 overflow-auto bg-[#f5f5f5] p-3 [&_.hr-docx-preview]:mx-auto [&_.hr-docx-preview]:bg-white [&_.hr-docx-preview]:shadow-sm'
          />
        ) : null}
        {mode === 'unsupported' && !loading ? (
          <div className='flex flex-1 flex-col items-start justify-center gap-3'>
            <Button
              type='primary'
              onClick={download}
            >
              下载简历
            </Button>
          </div>
        ) : null}
      </Drawer>
      <InviteFormModal
        open={invitePreset != null}
        preset={invitePreset}
        lockCandidate
        createDingTalkCalendar
        onOpenChange={(next) => {
          if (!next) setInvitePreset(null);
        }}
        onSuccess={() => onActionSuccess?.()}
      />
      <Modal
        title={`电话沟通 · ${String(detail?.display_name || '')}`}
        open={phoneOpen}
        confirmLoading={phoneSaving}
        okText='确认提交'
        destroyOnHidden
        onCancel={() => setPhoneOpen(false)}
        onOk={() => void submitPhone()}
      >
        <Form
          form={phoneForm}
          layout='vertical'
          className='mt-2'
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
            rules={[{ required: true, message: '请选择沟通结果' }]}
          >
            <Select
              options={PHONE_RESULT_OPTS}
              placeholder='请选择结果'
            />
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
              rules={[{ required: true, message: '请选择原因' }]}
            >
              <Select
                showSearch
                optionFilterProp='label'
                placeholder='请选择原因'
                options={reasons.map((row) => ({ value: row.name, label: row.name }))}
              />
            </Form.Item>
          ) : null}
          <Form.Item
            name='remark'
            label='备注'
            rules={[{ required: true, message: '请填写备注' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder='请填写沟通备注'
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`沟通记录 · ${String(detail?.display_name || pipelineDetail?.displayName || '')}`}
        open={recordOpen}
        onCancel={() => setRecordOpen(false)}
        footer={null}
        width={560}
        destroyOnHidden
        zIndex={1200}
      >
        {recordLoading ? (
          <div className='flex justify-center py-10'>
            <Spin />
          </div>
        ) : !pipelineDetail ? (
          <Empty description='暂无记录' />
        ) : (
          <div className='max-h-[70vh] space-y-3 overflow-y-auto pr-1'>
            <Card
              size='small'
              title='标记不合适 / 初筛'
            >
              {pipelineDetail.screenResult ? (
                <Descriptions
                  size='small'
                  column={1}
                >
                  <Descriptions.Item label='结果'>
                    {pipelineDetail.screenResult === 'PASS'
                      ? '合适'
                      : pipelineDetail.screenResult === 'FAIL'
                        ? '不合适'
                        : pipelineDetail.screenResult}
                  </Descriptions.Item>
                  <Descriptions.Item label='原因'>{pipelineDetail.screenRejectReason || '—'}</Descriptions.Item>
                  <Descriptions.Item label='阶段'>
                    {pipelineDetail.stageName || pipelineDetail.currentStage || '—'}
                  </Descriptions.Item>
                </Descriptions>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description='尚未初筛或标记不合适'
                />
              )}
            </Card>
            <Card
              size='small'
              title='电话沟通'
            >
              {pipelineDetail.phoneScreen ? (
                <Descriptions
                  size='small'
                  column={1}
                >
                  <Descriptions.Item label='沟通时间'>{fmtDt(pipelineDetail.phoneScreen.calledAt)}</Descriptions.Item>
                  <Descriptions.Item label='结果'>
                    {pipelineDetail.phoneScreen.result === 'PASS'
                      ? '合适'
                      : pipelineDetail.phoneScreen.result === 'FAIL'
                        ? '不合适'
                        : pipelineDetail.phoneScreen.result || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label='约面时间'>
                    {fmtDt(pipelineDetail.phoneScreen.interviewAt)}
                  </Descriptions.Item>
                  <Descriptions.Item label='不合适原因'>
                    {pipelineDetail.phoneScreen.rejectReason || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label='备注'>{pipelineDetail.phoneScreen.remark || '—'}</Descriptions.Item>
                </Descriptions>
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description='暂无电话沟通记录'
                />
              )}
            </Card>
            {(pipelineDetail.timeline || []).filter((item) =>
              /SCREEN|PHONE/i.test(String(item.stageCode || item.source || '')),
            ).length > 0 ? (
              <Card
                size='small'
                title='相关时间线'
              >
                <div className='space-y-2'>
                  {(pipelineDetail.timeline || [])
                    .filter((item) => /SCREEN|PHONE/i.test(String(item.stageCode || item.source || '')))
                    .map((item, idx) => (
                      <div
                        key={`${item.stageCode}-${item.eventAt}-${idx}`}
                        className='rounded border border-neutral-100 px-3 py-2 text-sm'
                      >
                        <div className='font-medium text-neutral-800'>{item.stageName || item.stageCode || '节点'}</div>
                        <div className='mt-0.5 text-xs text-neutral-400'>{fmtDt(item.eventAt)}</div>
                        <div className='mt-1 text-neutral-600'>{item.summary || '—'}</div>
                      </div>
                    ))}
                </div>
              </Card>
            ) : null}
          </div>
        )}
      </Modal>
      <Modal
        title={`标记不合适 · ${String(detail?.display_name || '')}`}
        open={unsuitableOpen}
        confirmLoading={unsuitableSaving}
        okText='确认标记'
        okButtonProps={{ danger: true }}
        destroyOnHidden
        onCancel={() => setUnsuitableOpen(false)}
        onOk={() => void submitUnsuitable()}
      >
        <Form
          form={unsuitableForm}
          layout='vertical'
          className='mt-2'
        >
          <Form.Item
            name='rejectReason'
            label='不合适原因'
            rules={[{ required: true, message: '请选择原因' }]}
          >
            <Select
              showSearch
              optionFilterProp='label'
              placeholder='请选择原因'
              options={reasons.map((row) => ({ value: row.name, label: row.name }))}
            />
          </Form.Item>
          <Form.Item
            name='remark'
            label='备注'
            rules={[{ required: true, message: '请填写备注' }]}
          >
            <Input.TextArea
              rows={3}
              placeholder='请填写备注'
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
