import { Button, Upload } from 'antd';
import { uploadGeoScreenshotApi } from '@/api/geo';
import GeoScreenshot from '@/components/geo/GeoScreenshot';

/** 引用截图：上传后写入表单字段，并可直接预览 */
export function CiteScreenshotUpload({ value, onChange }: { value?: string; onChange?: (value?: string) => void }) {
  return (
    <div>
      <Upload
        maxCount={1}
        accept='image/*'
        showUploadList={false}
        customRequest={async (opt) => {
          try {
            const res = await uploadGeoScreenshotApi(opt.file as File);
            onChange?.(res.url);
            opt.onSuccess?.(res);
          } catch (error) {
            opt.onError?.(error as Error);
          }
        }}
      >
        <Button>上传截图</Button>
      </Upload>
      {value ? (
        <div className='mt-2 flex items-center gap-3'>
          <GeoScreenshot
            src={value}
            width={160}
          />
          <Button
            type='link'
            className='px-0'
            onClick={() => onChange?.('')}
          >
            移除
          </Button>
        </div>
      ) : null}
    </div>
  );
}
