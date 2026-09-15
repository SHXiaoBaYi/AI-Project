import { Image } from 'antd';
import { ZoomInOutlined } from '@ant-design/icons';

function resolveScreenshotUrl(src?: string) {
  if (!src) return '';
  if (/^https?:\/\//i.test(src) || src.startsWith('data:')) return src;
  if (src.startsWith('/api')) return src;
  return `/api${src.startsWith('/') ? src : `/${src}`}`;
}

/** 监测截图：缩略图点击弹窗预览，支持缩放放大 */
export function GeoScreenshot({
  src,
  width = 40,
  height,
  className,
}: {
  src?: string | null;
  width?: number;
  height?: number;
  className?: string;
}) {
  if (!src) return <span className='text-neutral-400'>-</span>;
  const url = resolveScreenshotUrl(src);

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
