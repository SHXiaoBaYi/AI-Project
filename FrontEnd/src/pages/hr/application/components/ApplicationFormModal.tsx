import { useEffect, useState } from 'react';
import { App, DatePicker, Form, Input, Select, Upload } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm/index';
import { saveHrApplicationApi, uploadHrResumeApi } from '@/api/hr';

export interface ApplicationFormValues {
  id?: number;
  displayName?: string;
  phone?: string;
  email?: string;
  requisitionId?: number;
  channelCode?: string;
  currentStage?: string;
  submitterUserId?: number;
  submittedAt?: string;
  resumeName?: string;
}

function dateOf(value: unknown) {
  if (value == null || value === '') return undefined;
  if (dayjs.isDayjs(value)) return value.format('YYYY-MM-DD');
  if (Array.isArray(value)) return dayjs(`${value[0]}-${value[1]}-${value[2]}`).format('YYYY-MM-DD');
  return String(value).slice(0, 10);
}

export default function ApplicationFormModal({
  open,
  editing,
  jobs,
  channels,
  stages,
  users,
  presetRequisitionId,
  onOpenChange,
  onSuccess,
}: {
  open: boolean;
  editing: ApplicationFormValues | null;
  jobs: { value: number; label: string }[];
  channels: { value: string; label: string }[];
  stages: { value: string; label: string }[];
  users: { value: number; label: string }[];
  presetRequisitionId?: number;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [resumeFileList, setResumeFileList] = useState<UploadFile[]>([]);

  const resetResume = () => {
    setResumeFile(null);
    setResumeFileList([]);
  };

  useEffect(() => {
    if (!open) return;
    resetResume();
    form.setFieldsValue(
      editing
        ? {
            ...editing,
            submittedAt: editing.submittedAt ? dayjs(editing.submittedAt) : undefined,
          }
        : {
            requisitionId: presetRequisitionId,
            currentStage: 'ENTERED',
            submittedAt: dayjs(),
          },
    );
  }, [editing, form, open, presetRequisitionId]);

  return (
    <BaseModalForm
      title={editing ? '修改候选人' : '新增候选人'}
      open={open}
      form={form}
      width={720}
      onOpenChange={(next) => {
        if (!next) resetResume();
        onOpenChange(next);
      }}
      modalProps={{ destroyOnHidden: true, styles: { body: { padding: '24px' } } }}
      onFinish={async (values) => {
        const formValues = values as ApplicationFormValues;
        const applicationId = await saveHrApplicationApi({
          id: editing?.id,
          displayName: formValues.displayName,
          phone: formValues.phone,
          email: formValues.email,
          requisitionId: formValues.requisitionId,
          channelCode: formValues.channelCode,
          currentStage: formValues.currentStage,
          submitterUserId: formValues.submitterUserId,
          submittedAt: dateOf(formValues.submittedAt),
        });
        if (resumeFile) {
          await uploadHrResumeApi(Number(applicationId), resumeFile);
        }
        message.success(editing ? '已保存' : resumeFile ? '已新增并上传简历' : '已新增');
        resetResume();
        onSuccess();
        return true;
      }}
    >
      <div className='grid grid-cols-2 gap-x-8 gap-y-1'>
        <Form.Item
          name='displayName'
          label='姓名'
          rules={[{ required: true, message: '请填写姓名' }]}
        >
          <Input
            allowClear
            placeholder='请填写姓名'
          />
        </Form.Item>
        <Form.Item
          name='requisitionId'
          label='岗位'
          rules={[{ required: true, message: '请选择招聘需求' }]}
        >
          <Select
            options={jobs}
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='请选择招聘需求'
          />
        </Form.Item>
        <Form.Item
          name='channelCode'
          label='渠道'
        >
          <Select
            options={channels}
            allowClear
            placeholder='请选择渠道'
          />
        </Form.Item>
        <Form.Item
          name='currentStage'
          label='阶段'
          rules={[{ required: true, message: '请选择阶段' }]}
        >
          <Select
            options={stages}
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='请选择阶段'
          />
        </Form.Item>
        <Form.Item
          name='submitterUserId'
          label='提交人'
        >
          <Select
            options={users}
            allowClear
            showSearch
            optionFilterProp='label'
            placeholder='请选择提交人'
          />
        </Form.Item>
        <Form.Item
          name='submittedAt'
          label='投递日期'
          rules={[{ required: true, message: '请选择投递日期' }]}
        >
          <DatePicker className='w-full' />
        </Form.Item>
        <Form.Item
          name='phone'
          label='电话'
        >
          <Input
            allowClear
            placeholder='手机号'
          />
        </Form.Item>
        <Form.Item
          name='email'
          label='邮箱'
        >
          <Input
            allowClear
            placeholder='邮箱'
          />
        </Form.Item>
        <Form.Item
          label='简历'
          className='col-span-2'
        >
          {editing?.resumeName ? (
            <p className='mb-2 text-sm text-black/45'>当前：{editing.resumeName}，重新上传将覆盖</p>
          ) : null}
          <Upload.Dragger
            maxCount={1}
            accept='.pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'
            fileList={resumeFileList}
            beforeUpload={(file) => {
              const lower = file.name.toLowerCase();
              if (!lower.endsWith('.pdf') && !lower.endsWith('.doc') && !lower.endsWith('.docx')) {
                message.warning('简历仅支持 pdf/doc/docx');
                return Upload.LIST_IGNORE;
              }
              setResumeFile(file);
              setResumeFileList([
                {
                  uid: String(file.uid || '-1'),
                  name: file.name,
                  status: 'done',
                  size: file.size,
                },
              ]);
              return false;
            }}
            onRemove={() => {
              resetResume();
            }}
          >
            <p className='ant-upload-drag-icon'>
              <InboxOutlined />
            </p>
            <p className='ant-upload-text'>点击或拖拽简历到这里</p>
            <p className='ant-upload-hint'>支持 pdf / doc / docx，单次一个文件</p>
          </Upload.Dragger>
        </Form.Item>
      </div>
    </BaseModalForm>
  );
}
