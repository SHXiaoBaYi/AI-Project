import { memo, useEffect, useState } from 'react';
import {
  ProFormDatePicker,
  ProFormDigit,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { App, Button, Upload } from 'antd';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm';
import GeoScreenshot from '@/components/geo/GeoScreenshot';
import { updateGeoDailyApi, uploadGeoScreenshotApi } from '@/api/geo';
import type { GeoDailyVO, GeoOwnerOption, GeoTopic } from '@/types/geo';
import { GEO_TERM_TYPES, GEO_TERM_TYPE_DEFAULT } from '@/constants/geo';

const RECOMMEND_OPTIONS = ['未出现', '出现且推荐', '出现未推荐'].map((v) => ({ label: v, value: v }));

interface EditDailyModalProps {
  open: boolean;
  record: GeoDailyVO | null;
  topics: GeoTopic[];
  platforms: string[];
  owners: GeoOwnerOption[];
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

const EditDailyModal = memo(function EditDailyModal({
  open,
  record,
  topics,
  platforms,
  owners,
  onOpenChange,
  onSuccess,
}: EditDailyModalProps) {
  const { message } = App.useApp();
  const [screenshotUrl, setScreenshotUrl] = useState<string>();

  useEffect(() => {
    if (open) {
      setScreenshotUrl(record?.screenshotUrl);
    }
  }, [open, record?.screenshotUrl]);

  return (
    <BaseModalForm
      key={record?.id ?? 'edit'}
      title='编辑日监测'
      width={640}
      open={open}
      onOpenChange={onOpenChange}
      initialValues={
        record
          ? {
              ...record,
              inspectDate: dayjs(record.inspectDate),
              termType: record.termType || GEO_TERM_TYPE_DEFAULT,
            }
          : undefined
      }
      onFinish={async (values) => {
        if (!record) return false;
        await updateGeoDailyApi({
          id: record.id,
          inspectDate: values.inspectDate ? dayjs(values.inspectDate).format('YYYY-MM-DD') : record.inspectDate,
          platform: values.platform,
          keyword: values.keyword,
          termType: values.termType || GEO_TERM_TYPE_DEFAULT,
          ownerUserId: values.ownerUserId,
          topicId: values.topicId,
          mentioned: values.mentioned,
          rankNo: values.rankNo,
          recommendStatus: values.recommendStatus,
          screenshotUrl: screenshotUrl,
          thirdPartyUrl: values.thirdPartyUrl,
          negativeContent: values.negativeContent,
          competitors: values.competitors,
        });
        message.success('已更新');
        onSuccess();
        return true;
      }}
    >
      <div className='grid grid-cols-2 gap-x-4'>
        <ProFormDatePicker
          name='inspectDate'
          label='巡查日期'
          rules={[{ required: true, message: '请选择巡查日期' }]}
        />
        <ProFormSelect
          name='platform'
          label='平台'
          rules={[{ required: true, message: '请选择平台' }]}
          options={platforms.map((p) => ({ label: p, value: p }))}
        />
        <ProFormSelect
          name='topicId'
          label='话题'
          rules={[{ required: true, message: '请选择话题' }]}
          options={topics.map((t) => ({ label: t.topicName, value: t.id }))}
        />
        <ProFormText
          name='keyword'
          label='关键字'
          rules={[{ required: true, message: '请输入关键字' }]}
        />
        <ProFormSelect
          name='termType'
          label='长短词'
          rules={[{ required: true, message: '请选择长短词' }]}
          options={[...GEO_TERM_TYPES]}
          initialValue={GEO_TERM_TYPE_DEFAULT}
        />
        <ProFormSelect
          name='ownerUserId'
          label='负责人'
          placeholder='选择用户（展示昵称或用户名）'
          showSearch
          options={
            record?.ownerUserId && !owners.some((u) => u.userId === record.ownerUserId)
              ? [
                  { label: record.ownerName || String(record.ownerUserId), value: record.ownerUserId },
                  ...owners.map((u) => ({ label: u.displayName, value: u.userId })),
                ]
              : owners.map((u) => ({ label: u.displayName, value: u.userId }))
          }
          fieldProps={{ optionFilterProp: 'label', allowClear: true }}
        />
        <ProFormSelect
          name='mentioned'
          label='提及'
          options={[
            { label: '是', value: 1 },
            { label: '否', value: 0 },
          ]}
        />
        <ProFormDigit
          name='rankNo'
          label='排名'
          min={0}
          fieldProps={{
            precision: 0,
            step: 1,
            parser: (text) => {
              const digits = String(text ?? '').replace(/[^\d]/g, '');
              return digits === '' ? ('' as unknown as number) : Number(digits);
            },
          }}
        />
        <ProFormSelect
          name='recommendStatus'
          label='推荐状态'
          options={RECOMMEND_OPTIONS}
        />
        <ProFormText
          name='thirdPartyUrl'
          label='第三方链接'
        />
        <ProFormText
          name='competitors'
          label='竞品'
          colProps={{ span: 24 }}
        />
        <ProFormTextArea
          name='negativeContent'
          label='负面/错误内容'
          fieldProps={{ rows: 2 }}
          colProps={{ span: 24 }}
        />
      </div>
      <div className='mt-2'>
        <div className='mb-1 text-sm'>监测截图</div>
        <Upload
          maxCount={1}
          accept='image/*'
          showUploadList={false}
          customRequest={async (opt) => {
            try {
              const res = await uploadGeoScreenshotApi(opt.file as File);
              setScreenshotUrl(res.url);
              opt.onSuccess?.(res);
            } catch (e) {
              opt.onError?.(e as Error);
            }
          }}
        >
          <Button size='small'>上传截图</Button>
        </Upload>
        {screenshotUrl ? (
          <div className='mt-2'>
            <GeoScreenshot
              src={screenshotUrl}
              width={160}
            />
          </div>
        ) : null}
      </div>
    </BaseModalForm>
  );
});

export default EditDailyModal;
