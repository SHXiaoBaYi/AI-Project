import { useEffect, useRef, useState } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Modal, Select, Space } from 'antd';
import { renderAsync } from 'docx-preview';
import PermissionButton from '@/components/Buttons/PermissionButton';
import InviteFormModal, { type InviteFormPreset } from '@/components/hr/InviteFormModal';
import { nextInviteRound } from '@/components/hr/inviteRound';
import {
  fetchHrResumeApi,
  getHrApplicationApi,
  getHrDictOptionsApi,
  saveHrPipelineScreenApi,
  type HrBaseDict,
} from '@/api/hr';

function isPdf(fileName: string, contentType: string, blob?: Blob) {
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

export function ResumeDrawer({
  applicationId,
  fileName,
  open,
  onClose,
  onActionSuccess,
}: {
  applicationId: number;
  fileName?: string;
  open: boolean;
  onClose: () => void;
  onActionSuccess?: () => void;
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
  const [reasons, setReasons] = useState<HrBaseDict[]>([]);
  const [unsuitableForm] = Form.useForm<{ rejectReason: string; remark?: string }>();

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
    if (reasons.length === 0) {
      void getHrDictOptionsApi('UNSUITABLE').then((rows) => setReasons(rows || []));
    }
    setUnsuitableOpen(true);
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
            {alreadyOnboarded || alreadyUnsuitable ? null : (
              <PermissionButton
                perm='hr:invite:add'
                type='primary'
                onClick={openInvite}
              >
                发起面试邀约
              </PermissionButton>
            )}
            {alreadyUnsuitable || alreadyOnboarded ? null : (
              <PermissionButton
                perm='hr:pipeline:edit'
                danger
                onClick={openUnsuitable}
              >
                标记此人不合适
              </PermissionButton>
            )}
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
        onOpenChange={(next) => {
          if (!next) setInvitePreset(null);
        }}
        onSuccess={() => onActionSuccess?.()}
      />
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
          >
            <Input.TextArea
              rows={3}
              placeholder='补充说明（选填）'
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
