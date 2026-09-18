import { useState } from 'react';
import { Button, Image } from 'antd';
import { ZoomInOutlined } from '@ant-design/icons';
import { withBase } from '@/utils/basePath';

function resolveScreenshotUrl(src?: string) {
  if (!src) return '';
  if (/^https?:\/\//i.test(src) || src.startsWith('data:')) return src;
  const path = src.startsWith('/') ? src : `/${src}`;
  if (path.startsWith('/api/') || path === '/api') return withBase(path);
  return withBase(`/api${path}`);
}

/** 监测截图：缩略图点击弹窗预览；列表可用「查看」再打开 */
export function GeoScreenshot({
  src,
  width = 40,
  height,
  className,
  trigger = 'thumb',
}: {
  src?: string | null;
  width?: number;
  height?: number;
  className?: string;
  /** link：只显示「查看」，点击后再打开大图 */
  trigger?: 'thumb' | 'link';
}) {
  const [open, setOpen] = useState(false);
  if (!src) return <span className='text-neutral-400'>-</span>;
  const url = resolveScreenshotUrl(src);

  if (trigger === 'link') {
    return (
      <>
        <Button
          type='link'
          size='small'
          className='px-0'
          onClick={() => setOpen(true)}
        >
          查看
        </Button>
        <Image
          style={{ display: 'none' }}
          src={url}
          alt='监测截图'
          preview={{
            visible: open,
            onVisibleChange: setOpen,
            movable: true,
            minScale: 0.5,
            maxScale: 8,
          }}
        />
      </>
    );
  }

  return (
    <Image
      width={width}
      height={height}
      src={url}
      alt='监测截图'
      className={className ?? 'cursor-zoom-in rounded object-cover'}
      style={{ objectFit: 'cover' }}
      preview={{
        mask: (
          <span className='inline-flex items-center gap-1 text-xs'>
            <ZoomInOutlined />
            放大预览
          </span>
        ),
        movable: true,
        minScale: 0.5,
        maxScale: 8,
      }}
    />
  );
}

export default GeoScreenshot;
