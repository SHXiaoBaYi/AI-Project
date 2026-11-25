import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, DatePicker, Form, Input, InputNumber, Radio, Select, Space, Upload } from 'antd';
import { InboxOutlined, PaperClipOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import dayjs from 'dayjs';
import BaseModalForm from '@/components/BaseModalForm/index';
import {
  deleteHrPortfolioApi,
  downloadHrPortfolioApi,
  listHrPortfolioApi,
  matchHrTalentApi,
  parseHrResumeApi,
  saveHrTalentApi,
  uploadHrPortfolioApi,
  uploadHrResumeApi,
  type HrCandidatePortfolio,
  type HrTalentMatch,
} from '@/api/hr';

export interface ApplicationFormValues {
  candidateId?: number;
  activeApplicationId?: number;
  displayName?: string;
  phone?: string;
  email?: string;
  gender?: string;
  age?: number;
  education?: string;
  candidateNo?: string;
  currentTitle?: string;
  currentCompany?: string;
  city?: string;
  workYears?: string;
  expectedSalary?: string;
  skillTags?: string;
  schoolName?: string;
  major?: string;
  schoolTags?: string;
  requisitionId?: number;
  channelCode?: string;
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
  users,
  presetRequisitionId,
  requireJob,
  onOpenChange,
  onSuccess,
}: {
  open: boolean;
  editing: ApplicationFormValues | null;
  jobs: { value: number; label: string }[];
  channels: { value: string; label: string }[];
  users: { value: number; label: string }[];
  presetRequisitionId?: number;
  /** 从岗位看板入库时岗位必填 */
  requireJob?: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const resumeFileRef = useRef<File | null>(null);
  const [resumeFileList, setResumeFileList] = useState<UploadFile[]>([]);
  const [parseTip, setParseTip] = useState<string>();
  const [parsing, setParsing] = useState(false);
  const [portfolioList, setPortfolioList] = useState<HrCandidatePortfolio[]>([]);
  const [pendingPortfolios, setPendingPortfolios] = useState<File[]>([]);
  const pendingPortfoliosRef = useRef<File[]>([]);
  const [pendingPortfolioUi, setPendingPortfolioUi] = useState<UploadFile[]>([]);
  const [matches, setMatches] = useState<HrTalentMatch[]>([]);
  const [mergeMode, setMergeMode] = useState<'merge' | 'new'>('merge');
  const phoneWatch = Form.useWatch('phone', form);
  const emailWatch = Form.useWatch('email', form);

  const setResumeFileKeep = (file: File | null) => {
    resumeFileRef.current = file;
    setResumeFile(file);
  };

  const setPendingPortfoliosKeep = (files: File[] | ((prev: File[]) => File[])) => {
    setPendingPortfolios((prev) => {
      const next = typeof files === 'function' ? files(prev) : files;
      pendingPortfoliosRef.current = next;
      return next;
    });
  };

  const resetLocalFiles = () => {
    resumeFileRef.current = null;
    pendingPortfoliosRef.current = [];
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
            resumeGate: editing.resumeName || undefined,
          }
        : {
            requisitionId: presetRequisitionId,
            submittedAt: dayjs(),
            resumeGate: undefined,
          },
    );
    if (editing?.activeApplicationId) {
      void loadPortfolios(editing.activeApplicationId);
    } else {
      setPortfolioList([]);
    }
  }, [editing, form, open, presetRequisitionId]);

  useEffect(() => {
    if (!open || editing?.candidateId) {
      setMatches([]);
      return;
    }
    const phone = String(phoneWatch ?? '').trim();
    const email = String(emailWatch ?? '').trim();
    if (phone.length < 4 && !email.includes('@')) {
      setMatches([]);
      return;
    }
    const timer = window.setTimeout(() => {
      void matchHrTalentApi({ phone: phone || undefined, email: email || undefined })
        .then((rows) => {
          setMatches(rows ?? []);
          setMergeMode('merge');
        })
        .catch(() => setMatches([]));
    }, 400);
    return () => window.clearTimeout(timer);
  }, [editing?.candidateId, emailWatch, open, phoneWatch]);

  const applyResumeFile = async (file: File) => {
    // 先落本地文件：识别只预填表单，保存时必须再上传到候选人
    setResumeFileKeep(file);
    setResumeFileList([
      {
        uid: String(Date.now()),
        name: file.name,
        status: 'done',
        size: file.size,
      },
    ]);
    form.setFieldsValue({ resumeGate: file.name });
    setParsing(true);
    setParseTip(undefined);
    try {
      const preview = await parseHrResumeApi(file);
      const patch: Record<string, string | number> = {};
      if (preview.displayName) patch.displayName = preview.displayName;
      if (preview.gender) patch.gender = preview.gender;
      if (preview.age != null) patch.age = preview.age;
      if (preview.education) patch.education = preview.education;
      if (preview.currentTitle) patch.currentTitle = preview.currentTitle;
      if (preview.currentCompany) patch.currentCompany = preview.currentCompany;
      if (preview.phone) patch.phone = preview.phone;
      if (preview.email) patch.email = preview.email;
      if (preview.channelCode) patch.channelCode = preview.channelCode;
      if (preview.schoolName) patch.schoolName = preview.schoolName;
      if (preview.major) patch.major = preview.major;
      if (preview.schoolTags) patch.schoolTags = preview.schoolTags;
      if (preview.city || preview.cityHint) patch.city = preview.city || preview.cityHint || '';
      if (preview.workYears || preview.yearsHint) patch.workYears = preview.workYears || preview.yearsHint || '';
      if (preview.expectedSalary || preview.salaryHint) {
        patch.expectedSalary = preview.expectedSalary || preview.salaryHint || '';
      }
      if (preview.skillTags) patch.skillTags = preview.skillTags;
      if (Object.keys(patch).length) {
        form.setFieldsValue(patch);
      }
      setParseTip(preview.tip || undefined);
      const filled = Object.keys(patch).length;
      if (filled >= 3) {
        message.success(preview.tip || `已识别 ${filled} 项，请核对后保存`);
      } else if (filled > 0) {
        message.warning(preview.tip || '仅识别到部分字段，请手动补全');
      } else {
        message.warning(preview.tip || '未能自动识别，请手动填写');
      }
    } catch (e) {
      setParseTip(e instanceof Error ? e.message : '简历解析失败，请手动填写');
      message.warning('简历已选中，但未能自动识别，请手动填写');
    } finally {
      setParsing(false);
    }
  };

  return (
    <BaseModalForm
      title={editing?.candidateId ? '编辑人才' : requireJob ? '新增候选人' : '人才入库'}
      open={open}
      form={form}
      width={1120}
      onOpenChange={(next) => {
        if (!next) resetLocalFiles();
        onOpenChange(next);
      }}
      modalProps={{ destroyOnHidden: true, styles: { body: { padding: '20px 24px' } }, centered: true }}
      onFinish={async (values) => {
        const formValues = values as ApplicationFormValues;
        const fileToUpload = resumeFileRef.current ?? resumeFile;
        const isEdit = editing?.candidateId != null;
        if (!isEdit && !fileToUpload) {
          message.error('请先上传简历');
          return false;
        }
        const phone = formValues.phone?.trim();
        const email = formValues.email?.trim();
        let candidateId = isEdit ? editing?.candidateId : undefined;
        let forceNew = false;
        if (!isEdit) {
          const fresh = await matchHrTalentApi({ phone: phone || undefined, email: email || undefined });
          if (fresh.length > 0 && mergeMode === 'new') {
            forceNew = true;
          } else if (fresh.length > 0) {
            candidateId = fresh[0].candidateId;
          }
        }
        const saved = await saveHrTalentApi({
          editing: isEdit,
          candidateId,
          forceNew,
          ensureApplication: Boolean(fileToUpload),
          displayName: formValues.displayName,
          phone,
          email,
          gender: formValues.gender,
          age: formValues.age,
          education: formValues.education,
          candidateNo: formValues.candidateNo,
          currentTitle: formValues.currentTitle,
          currentCompany: formValues.currentCompany,
          city: formValues.city,
          workYears: formValues.workYears,
          expectedSalary: formValues.expectedSalary,
          skillTags: formValues.skillTags,
          schoolName: formValues.schoolName,
          major: formValues.major,
          schoolTags: formValues.schoolTags,
          requisitionId: isEdit ? undefined : formValues.requisitionId,
          channelCode: isEdit ? undefined : formValues.channelCode,
          submitterUserId: isEdit ? undefined : formValues.submitterUserId,
          submittedAt: isEdit ? undefined : dateOf(formValues.submittedAt),
        });
        const id = Number(saved.applicationId);
        if (fileToUpload) {
          if (!Number.isFinite(id) || id <= 0) {
            message.error('档案已保存，但没有可挂简历的投递');
            onSuccess();
            return false;
          }
          try {
            await uploadHrResumeApi(id, fileToUpload);
          } catch (e) {
            message.error(
              e instanceof Error
                ? `档案已保存，但简历上传失败：${e.message}`
                : '档案已保存，但简历上传失败，请编辑后重新上传',
            );
            onSuccess();
            return false;
          }
        }
        const portfolioAppId = Number.isFinite(id) && id > 0 ? id : editing?.activeApplicationId;
        if (portfolioAppId) {
          for (const file of pendingPortfoliosRef.current.length ? pendingPortfoliosRef.current : pendingPortfolios) {
            await uploadHrPortfolioApi(portfolioAppId, file);
          }
        }
        message.success(saved.merged ? '已合并到已有人才' : isEdit ? '已保存' : '已入库');
        resetLocalFiles();
        onSuccess();
        return true;
      }}
    >
      <div className='grid grid-cols-4 gap-x-4 gap-y-1'>
        <Form.Item
          label='简历'
          className='col-span-4 mb-0'
          required={!editing?.candidateId}
          extra='先上传简历：系统 AI 识别预填字段；保存时会把同一份简历上传到候选人（新增必填）'
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
              setResumeFileKeep(null);
              setResumeFileList([]);
              setParseTip(undefined);
              form.setFieldsValue({ resumeGate: undefined });
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
          name='resumeGate'
          className='col-span-4'
          rules={
            editing?.candidateId
              ? undefined
              : [
                  {
                    validator: async () => {
                      if (!resumeFileRef.current && !resumeFile) {
                        throw new Error('请上传简历（识别后仍会保存该文件）');
                      }
                    },
                  },
                ]
          }
          hidden
        >
          <Input />
        </Form.Item>

        <Form.Item
          name='displayName'
          label='姓名'
          rules={[{ required: true, message: '请填写姓名' }]}
          extra='可从简历 AI 识别，请核对'
        >
          <Input
            allowClear
            placeholder='请填写或核对姓名'
          />
        </Form.Item>
        <Form.Item
          name='candidateNo'
          label='人选编号'
        >
          <Input
            allowClear
            placeholder='不填则自动生成'
          />
        </Form.Item>
        <Form.Item
          name='gender'
          label='性别'
        >
          <Select
            allowClear
            placeholder='请选择'
            options={[
              { label: '男', value: '男' },
              { label: '女', value: '女' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name='age'
          label='年龄'
        >
          <InputNumber
            min={16}
            max={70}
            className='w-full'
            placeholder='岁'
          />
        </Form.Item>
        <Form.Item
          name='education'
          label='学历'
        >
          <Input
            allowClear
            placeholder='如本科 / MBA'
          />
        </Form.Item>
        <Form.Item
          name='currentTitle'
          label='目前职位'
        >
          <Input
            allowClear
            placeholder='目前职位'
          />
        </Form.Item>
        <Form.Item
          name='currentCompany'
          label='目前公司'
        >
          <Input
            allowClear
            placeholder='目前公司'
          />
        </Form.Item>
        <Form.Item
          name='city'
          label='城市'
        >
          <Input
            allowClear
            placeholder='目前工作城市'
          />
        </Form.Item>
        <Form.Item
          name='workYears'
          label='工作年限'
        >
          <Input
            allowClear
            placeholder='如 5年 / 3-5年'
          />
        </Form.Item>
        <Form.Item
          name='expectedSalary'
          label='期望薪资'
        >
          <Input
            allowClear
            placeholder='如 20-30K'
          />
        </Form.Item>
        <Form.Item
          name='schoolName'
          label='学校'
        >
          <Input
            allowClear
            placeholder='最高学历学校'
          />
        </Form.Item>
        <Form.Item
          name='major'
          label='专业'
        >
          <Input
            allowClear
            placeholder='专业'
          />
        </Form.Item>
        <Form.Item
          name='schoolTags'
          label='院校标签'
        >
          <Input
            allowClear
            placeholder='如 985 / 211'
          />
        </Form.Item>
        <Form.Item
          name='skillTags'
          label='技能标签'
          className='col-span-2'
        >
          <Input
            allowClear
            placeholder='顿号或逗号分隔，如 Java、招聘、可异地'
          />
        </Form.Item>
        <Form.Item
          name='phone'
          label='电话'
          extra='可从简历识别，请核对。已在库的手机会提示合并'
        >
          <Input
            allowClear
            placeholder='手机号'
          />
        </Form.Item>
        <Form.Item
          name='email'
          label='邮箱'
          extra='可从简历识别，请核对'
        >
          <Input
            allowClear
            placeholder='邮箱'
          />
        </Form.Item>
        {!editing?.candidateId && matches.length > 0 ? (
          <div className='col-span-4 mb-3'>
            <Alert
              type='warning'
              showIcon
              message={`手机号或邮箱已对应人才「${matches[0].displayName || ''}」${matches[0].candidateNo ? `（${matches[0].candidateNo}）` : ''}`}
              description={
                <Radio.Group
                  className='mt-2'
                  value={mergeMode}
                  onChange={(e) => setMergeMode(e.target.value)}
                >
                  <Radio value='merge'>合并到该人才，不新建一个人</Radio>
                  <Radio value='new'>仍作为新人才入库</Radio>
                </Radio.Group>
              }
            />
          </div>
        ) : null}

        {!editing?.candidateId ? (
          <>
            <Form.Item
              name='requisitionId'
              label='岗位'
              extra={requireJob ? undefined : '可以先入库，之后再推荐到岗位'}
              rules={requireJob ? [{ required: true, message: '请选择招聘需求' }] : undefined}
            >
              <Select
                options={jobs}
                allowClear={!requireJob}
                showSearch
                optionFilterProp='label'
                placeholder={requireJob ? '请选择招聘需求' : '暂不挂岗'}
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
              label='入库日期'
              rules={[{ required: true, message: '请选择日期' }]}
            >
              <DatePicker className='w-full' />
            </Form.Item>
          </>
        ) : null}

        <Form.Item
          label='作品集'
          className='col-span-4'
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
                        if (editing?.activeApplicationId) await loadPortfolios(editing.activeApplicationId);
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
              setPendingPortfoliosKeep((prev) => [...prev, file]);
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
              setPendingPortfoliosKeep((prev) => prev.filter((f) => f.name !== file.name || f.size !== file.size));
            }}
          >
            <Button icon={<PaperClipOutlined />}>添加作品集附件</Button>
          </Upload>
        </Form.Item>
      </div>
    </BaseModalForm>
  );
}
