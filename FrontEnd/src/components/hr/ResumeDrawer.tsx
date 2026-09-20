import { useEffect, useRef, useState } from 'react';
import { Button, Descriptions, Drawer } from 'antd';
import { renderAsync } from 'docx-preview';
import { fetchHrResumeApi, getHrApplicationApi } from '@/api/hr';

function isPdf(fileName: string, contentType: string) {
  const lower = fileName.toLowerCase();
  return contentType.includes('pdf') || lower.endsWith('.pdf');
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

function ResumeDrawer({
  applicationId,
  fileName,
  open,
  onClose,
}: {
  applicationId: number;
  fileName?: string;
  open: boolean;
  onClose: () => void;
}) {
  const [detail, setDetail] = useState<Record<string, unknown> | null>(null);
  const [pdfUrl, setPdfUrl] = useState<string>();
  const [docxBlob, setDocxBlob] = useState<Blob | null>(null);
  const [mode, setMode] = useState<'pdf' | 'docx' | 'unsupported' | ''>('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const docxRef = useRef<HTMLDivElement>(null);

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
    <Drawer
      title='简历'
      open={open}
      onClose={onClose}
      size='large'
      className='[&_.ant-drawer-content-wrapper]:!w-[50vw]'
      styles={{
        wrapper: { width: '50vw' },
        body: { display: 'flex', flexDirection: 'column', overflow: 'hidden' },
      }}
      extra={
        <Button
          type='link'
          onClick={download}
        >
          下载
        </Button>
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
  );
}
