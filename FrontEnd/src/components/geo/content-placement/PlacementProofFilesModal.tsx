import { memo, useEffect, useState } from 'react';
import { Modal, Tag } from 'antd';
import { getGeoContentPlacementProofFilesApi } from '@/api/geo';
import type { GeoContentPlacementProofFile } from '@/types/geo';

type Props = {
  open: boolean;
  placementId: number | null;
  title?: string;
  onClose: () => void;
};

const fileHref = (url?: string) => {
  if (!url) return '#';
  if (/^https?:\/\//i.test(url)) return url;
  return `${String(import.meta.env.VITE_API_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')}${url}`;
};

/** 列表「附件」列：查看/下载弹窗（展示所属任务与上传人） */
const PlacementProofFilesModal = memo(function PlacementProofFilesModal({ open, placementId, title, onClose }: Props) {
  const [loading, setLoading] = useState(false);
  const [files, setFiles] = useState<GeoContentPlacementProofFile[]>([]);

  useEffect(() => {
    if (!open || placementId == null) {
      setFiles([]);
      return;
    }
    setLoading(true);
    void getGeoContentPlacementProofFilesApi(placementId)
      .then((list) => setFiles(list ?? []))
      .catch(() => setFiles([]))
      .finally(() => setLoading(false));
  }, [open, placementId]);

  return (
    <Modal
      title={title || '附件'}
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnHidden
      width={720}
    >
      {loading ? (
        <div className='py-6 text-center text-neutral-400'>加载中…</div>
      ) : files.length === 0 ? (
        <div className='py-6 text-center text-neutral-400'>暂无附件</div>
      ) : (
        <ul className='m-0 list-none space-y-2 p-0'>
          {files.map((f) => (
            <li
              key={f.id}
              className='flex items-start justify-between gap-3 rounded border border-neutral-200 px-3 py-2'
            >
              <div className='min-w-0 flex-1'>
                <div className='truncate font-medium'>{f.fileName}</div>
                <div className='mt-1 flex flex-wrap items-center gap-1 text-xs text-neutral-500'>
                  {f.taskType ? <Tag className='m-0'>{f.taskType}</Tag> : null}
                  <span className='truncate'>
                    任务：
                    {f.taskTitle?.trim() ? f.taskTitle : f.taskId != null ? `（未取到标题）#${f.taskId}` : '-'}
                  </span>
                </div>
                <div className='mt-0.5 text-xs text-neutral-400'>
                  上传人：{f.uploadUserName || '-'}
                  {f.createTime ? ` · ${f.createTime}` : ''}
                  {f.fileSize ? ` · ${Math.max(1, Math.round((f.fileSize || 0) / 1024))} KB` : ''}
                </div>
              </div>
              <a
                className='shrink-0 pt-1'
                href={fileHref(f.fileUrl)}
                target='_blank'
                rel='noreferrer'
                download={f.fileName}
              >
                查看/下载
              </a>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
});

export default PlacementProofFilesModal;
