import { useEffect, useState } from 'react';
import { Alert, App, Button, DatePicker, Form, Input, Select, Space, Upload } from 'antd';
import { InboxOutlined, PaperClipOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm/index';
import {
  deleteHrPortfolioApi,
  downloadHrPortfolioApi,
  listHrPortfolioApi,
  parseHrResumeApi,
  saveHrApplicationApi,
  uploadHrPortfolioApi,
  uploadHrResumeApi,
  type HrCandidatePortfolio,
} from '@/api/hr';

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

const RESUME_ACCEPT =
  '.pdf,.doc,.docx,.xls,.xlsx,.txt,.csv,.rtf,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/plain';

const RESUME_EXTS = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.txt', '.csv', '.rtf', '.md'];

const PORTFOLIO_ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.webp,.zip,.rar,.7z,.txt,.md,.csv';

function dateOf(value: unknown) {
  if (value == null || value === '') return undefined;
  if (dayjs.isDayjs(value)) return value.format('YYYY-MM-DD');
  if (Array.isArray(value)) return dayjs(`${value[0]}-${value[1]}-${value[2]}`).format('YYYY-MM-DD');
  return String(value).slice(0, 10);
}

function isResumeExt(name: string) {
  const lower = name.toLowerCase();
  return RESUME_EXTS.some((ext) => lower.endsWith(ext));
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
  const [parseTip, setParseTip] = useState<string>();
  const [parsing, setParsing] = useState(false);
  const [portfolioList, setPortfolioList] = useState<HrCandidatePortfolio[]>([]);
  const [pendingPortfolios, setPendingPortfolios] = useState<File[]>([]);
  const [pendingPortfolioUi, setPendingPortfolioUi] = useState<UploadFile[]>([]);

  const resetLocalFiles = () => {
    setResumeFile(null);
    setResumeFileList([]);
    setParseTip(undefined);
    setPendingPortfolios([]);
    setPendingPortfolioUi([]);
  };

  const loadPortfolios = async (applicationId: number) => {
    try {
      const rows = await listHrPortfolioApi(applicationId);
      setPortfolioList(rows ?? []);
    } catch {
      setPortfolioList([]);
    }
  };

  useEffect(() => {
    if (!open) return;
    resetLocalFiles();
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
    if (editing?.id) {
      void loadPortfolios(editing.id);
    } else {
      setPortfolioList([]);
    }
  }, [editing, form, open, presetRequisitionId]);

  const applyResumeFile = async (file: File) => {
    setResumeFile(file);
    setResumeFileList([
      {
        uid: String(Date.now()),
        name: file.name,
        status: 'done',
        size: file.size,
      },
    ]);
    setParsing(true);
    setParseTip(undefined);
    try {
      const preview = await parseHrResumeApi(file);
      const patch: Record<string, string> = {};
      if (preview.displayName) patch.displayName = preview.displayName;
      if (preview.phone) patch.phone = preview.phone;
      if (preview.email) patch.email = preview.email;
      if (Object.keys(patch).length) {
        form.setFieldsValue(patch);
      }
      setParseTip(preview.tip || undefined);
      const bits = [
        preview.jobHint && `岗位 ${preview.jobHint}`,
        preview.cityHint,
        preview.salaryHint,
        preview.yearsHint,
      ]
        .filter(Boolean)
        .join(' · ');
      if (preview.nameFound && preview.phoneFound && preview.emailFound) {
        message.success(
          bits ? `已识别姓名/电话/邮箱（文件名：${bits}），请核对` : '已从简历识别姓名、电话、邮箱，请核对',
        );
      } else {
        message.warning(preview.tip || '部分信息未识别到，请手动填写');
      }
    } catch (e) {
      setParseTip(e instanceof Error ? e.message : '简历解析失败，请手动填写');
      message.warning('简历已选中，但未能自动识别，请手动填写姓名/电话/邮箱');
    } finally {
      setParsing(false);
    }
  };

  return (
    <BaseModalForm
      title={editing ? '编辑候选人' : '新增候选人'}
      open={open}
      form={form}
      width={760}
      onOpenChange={(next) => {
        if (!next) resetLocalFiles();
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
        const id = Number(applicationId);
        if (resumeFile) {
          await uploadHrResumeApi(id, resumeFile);
        }
        for (const file of pendingPortfolios) {
          await uploadHrPortfolioApi(id, file);
        }
        message.success(editing ? '已保存' : '已新增');
        resetLocalFiles();
        onSuccess();
        return true;
      }}
    >
      <div className='grid grid-cols-2 gap-x-8 gap-y-1'>
        <Form.Item
          label='简历'
          className='col-span-2'
          required={!editing}
          extra='先上传简历，系统会尝试识别姓名、电话、邮箱，请核对后再保存'
        >
          {editing?.resumeName ? (
            <p className='mb-2 text-sm text-black/45'>当前：{editing.resumeName}，重新上传将覆盖</p>
          ) : null}
          <Upload.Dragger
            maxCount={1}
            accept={RESUME_ACCEPT}
            disabled={parsing}
            fileList={resumeFileList}
            beforeUpload={(file) => {
              if (!isResumeExt(file.name)) {
                message.warning('简历支持 pdf / doc / docx / xls / xlsx / txt / csv / rtf');
                return Upload.LIST_IGNORE;
              }
              void applyResumeFile(file);
              return false;
            }}
            onRemove={() => {
              setResumeFile(null);
              setResumeFileList([]);
              setParseTip(undefined);
            }}
          >
            <p className='ant-upload-drag-icon'>
              <InboxOutlined />
            </p>
            <p className='ant-upload-text'>{parsing ? '正在识别简历…' : '点击或拖拽简历到这里（优先）'}</p>
            <p className='ant-upload-hint'>支持 pdf / doc / docx / xls / xlsx / txt 等常见格式</p>
          </Upload.Dragger>
          {parseTip ? (
            <Alert
              className='mt-3'
              type={parseTip.includes('未识别') ? 'warning' : 'success'}
              showIcon
              message={parseTip}
            />
          ) : null}
        </Form.Item>

        <Form.Item
          name='displayName'
          label='姓名'
          rules={[{ required: true, message: '请填写姓名' }]}
          extra='可从简历自动识别，请核对'
        >
          <Input
            allowClear
            placeholder='请填写或核对姓名'
          />
        </Form.Item>
        <Form.Item
          name='phone'
          label='电话'
          extra='可从简历自动识别，请核对'
        >
          <Input
            allowClear
            placeholder='手机号'
          />
        </Form.Item>
        <Form.Item
          name='email'
          label='邮箱'
          className='col-span-2'
          extra='可从简历自动识别，请核对'
        >
          <Input
            allowClear
            placeholder='邮箱'
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
          className='col-span-2'
        >
          <DatePicker className='w-full' />
        </Form.Item>

        <Form.Item
          label='作品集'
          className='col-span-2'
          extra='可选。支持作品集 PDF/文档/图片/压缩包等，保存时一并上传'
        >
          {portfolioList.length > 0 ? (
            <ul className='mb-3 list-none space-y-2 p-0'>
              {portfolioList.map((item) => (
                <li
                  key={item.id}
                  className='flex items-center justify-between gap-3 rounded border border-neutral-200 px-3 py-2'
                >
                  <span className='min-w-0 truncate'>
                    <PaperClipOutlined className='mr-1' />
                    {item.fileName}
                  </span>
                  <Space size={8}>
                    <Button
                      type='link'
                      className='px-0'
                      onClick={() => {
                        void downloadHrPortfolioApi(item.id, item.fileName).catch((e) =>
                          message.error(e instanceof Error ? e.message : '下载失败'),
                        );
                      }}
                    >
                      下载
                    </Button>
                    <Button
                      type='link'
                      danger
                      className='px-0'
                      onClick={async () => {
                        await deleteHrPortfolioApi(item.id);
                        message.success('已删除');
                        if (editing?.id) await loadPortfolios(editing.id);
                      }}
                    >
                      删除
                    </Button>
                  </Space>
                </li>
              ))}
            </ul>
          ) : null}
          <Upload
            multiple
            accept={PORTFOLIO_ACCEPT}
            fileList={pendingPortfolioUi}
            beforeUpload={(file) => {
              setPendingPortfolios((prev) => [...prev, file]);
              setPendingPortfolioUi((prev) => [
                ...prev,
                {
                  uid: `${file.uid || Date.now()}-${file.name}`,
                  name: file.name,
                  status: 'done',
                  size: file.size,
                },
              ]);
              return false;
            }}
            onRemove={(file) => {
              setPendingPortfolioUi((prev) => prev.filter((f) => f.uid !== file.uid));
              setPendingPortfolios((prev) => prev.filter((f) => f.name !== file.name || f.size !== file.size));
            }}
          >
            <Button icon={<PaperClipOutlined />}>添加作品集附件</Button>
          </Upload>
        </Form.Item>
      </div>
    </BaseModalForm>
  );
}
