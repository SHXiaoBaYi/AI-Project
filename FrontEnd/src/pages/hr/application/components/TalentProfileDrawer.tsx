import { useEffect, useState } from 'react';
import { App, Button, DatePicker, Drawer, Form, Input, Spin, Table } from 'antd';
import dayjs from 'dayjs';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import { addHrTalentNoteApi, downloadHrPortfolioApi, getHrTalentProfileApi, type HrTalentProfile } from '@/api/hr';

const ROUND_LABEL = ['', '一面', '二面', '三面', '四面', '五面', '六面'];
const CONCLUSION: Record<string, string> = { PASS: '通过', FAIL: '未通过', PENDING: '待定' };

export default function TalentProfileDrawer({
  candidateId,
  open,
  onClose,
  onChanged,
  onAi,
  onReviews,
  onStage,
}: {
  candidateId?: number;
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
  onAi: (applicationId: number, name: string) => void;
  onReviews: (applicationId: number, name: string) => void;
  onStage: (applicationId: number, name: string, jobName?: string, stage?: string) => void;
}) {
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);
  const [profile, setProfile] = useState<HrTalentProfile | null>(null);
  const [form] = Form.useForm<{ content: string; nextFollowAt?: dayjs.Dayjs }>();

  const load = async (id: number) => {
    setLoading(true);
    try {
      setProfile(await getHrTalentProfileApi(id));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!open || !candidateId) {
      setProfile(null);
      return;
    }
    void load(candidateId);
  }, [candidateId, open]);

  const talent = profile?.talent;
  const name = talent?.displayName || '';

  return (
    <Drawer
      title={name ? `${name} · 人才档案` : '人才档案'}
      open={open}
      width={760}
      destroyOnHidden
      onClose={onClose}
    >
      <Spin spinning={loading}>
        {talent ? (
          <div className='flex flex-col gap-5'>
            <div className='grid grid-cols-2 gap-x-4 gap-y-1 text-sm'>
              <p>
                <span className='text-black/45'>状态 </span>
                {talent.poolStatusName || '—'}
              </p>
              <p>
                <span className='text-black/45'>编号 </span>
                {talent.candidateNo || '—'}
              </p>
              <p>
                <span className='text-black/45'>基本信息 </span>
                {talent.basicInfo || '—'}
              </p>
              <p>
                <span className='text-black/45'>电话 </span>
                {talent.phone || '—'}
              </p>
              <p>
                <span className='text-black/45'>邮箱 </span>
                {talent.email || '—'}
              </p>
              <p>
                <span className='text-black/45'>公司 / 职位 </span>
                {[talent.currentCompany, talent.currentTitle].filter(Boolean).join(' · ') || '—'}
              </p>
              <p>
                <span className='text-black/45'>学校 </span>
                {[talent.schoolName, talent.major, talent.schoolTags].filter(Boolean).join(' · ') || '—'}
              </p>
              <p>
                <span className='text-black/45'>期望薪资 </span>
                {talent.expectedSalary || '—'}
              </p>
              <p className='col-span-2'>
                <span className='text-black/45'>标签 </span>
                {talent.skillTags || '—'}
              </p>
              <p>
                <span className='text-black/45'>最近联系 </span>
                {talent.lastContactAt || '—'}
              </p>
              <p>
                <span className='text-black/45'>下次跟进 </span>
                {talent.nextFollowAt || '—'}
              </p>
            </div>

            <section>
              <h3 className='mb-2 text-sm font-medium'>跟进</h3>
              <Form
                form={form}
                layout='vertical'
                onFinish={async (values) => {
                  await addHrTalentNoteApi({
                    candidateId: talent.candidateId,
                    content: values.content.trim(),
                    nextFollowAt: values.nextFollowAt?.format('YYYY-MM-DD'),
                  });
                  message.success('已记录跟进');
                  form.resetFields();
                  await load(talent.candidateId);
                  onChanged();
                }}
              >
                <Form.Item
                  name='content'
                  rules={[{ required: true, message: '请填写跟进内容' }]}
                  className='mb-2'
                >
                  <Input.TextArea
                    rows={2}
                    maxLength={1000}
                    placeholder='跟进内容'
                  />
                </Form.Item>
                <div className='mb-3 flex items-start gap-3'>
                  <Form.Item
                    name='nextFollowAt'
                    label='下次跟进'
                    className='mb-0'
                  >
                    <DatePicker />
                  </Form.Item>
                  <PermissionButton
                    perm='hr:application:edit'
                    type='primary'
                    className='mt-[30px]'
                    onClick={() => form.submit()}
                  >
                    记录
                  </PermissionButton>
                </div>
              </Form>
              <ul className='m-0 list-none space-y-2 p-0'>
                {(profile?.notes ?? []).map((note) => (
                  <li
                    key={note.id}
                    className='rounded border border-neutral-200 px-3 py-2 text-sm'
                  >
                    <p className='mb-1 whitespace-pre-wrap'>{note.content}</p>
                    <p className='text-black/45'>
                      {note.operatorName || '—'} · {note.createTime || '—'}
                      {note.nextFollowAt ? ` · 下次 ${note.nextFollowAt}` : ''}
                    </p>
                  </li>
                ))}
                {!profile?.notes?.length ? <li className='text-sm text-black/45'>还没有跟进</li> : null}
              </ul>
            </section>

            <section>
              <h3 className='mb-2 text-sm font-medium'>投递</h3>
              <Table
                size='small'
                rowKey='applicationId'
                pagination={false}
                dataSource={profile?.applications ?? []}
                scroll={{ x: 680 }}
                columns={[
                  {
                    title: '操作',
                    width: 160,
                    fixed: 'left',
                    render: (_, row) => (
                      <div className='flex flex-wrap gap-x-2'>
                        <Button
                          type='link'
                          className='h-auto p-0'
                          onClick={() => onReviews(row.applicationId, name)}
                        >
                          评价
                        </Button>
                        <Button
                          type='link'
                          className='h-auto p-0'
                          onClick={() => onAi(row.applicationId, name)}
                        >
                          AI
                        </Button>
                        <PermissionButton
                          perm='hr:application:stage'
                          type='link'
                          className='h-auto p-0'
                          onClick={() => onStage(row.applicationId, name, row.jobName, row.stageCode)}
                        >
                          调阶段
                        </PermissionButton>
                      </div>
                    ),
                  },
                  {
                    title: '岗位',
                    dataIndex: 'jobName',
                    render: (value: string) => value || '未挂岗',
                  },
                  { title: '阶段', dataIndex: 'stageName', width: 140 },
                  { title: '渠道', dataIndex: 'channelName', width: 90 },
                  { title: '日期', dataIndex: 'submittedAt', width: 110 },
                  {
                    title: '简历',
                    width: 80,
                    render: (_, row) => (
                      <ResumeViewButton
                        applicationId={row.applicationId}
                        fileName={row.resumeName}
                      />
                    ),
                  },
                ]}
              />
            </section>

            <section>
              <h3 className='mb-2 text-sm font-medium'>作品集</h3>
              {talent.portfolios?.length ? (
                <div className='flex flex-col gap-1'>
                  {talent.portfolios.map((item) => (
                    <button
                      key={item.id}
                      type='button'
                      className='cursor-pointer truncate border-0 bg-transparent p-0 text-left text-[#1677ff]'
                      onClick={() => {
                        void downloadHrPortfolioApi(item.id, item.fileName).catch((e) =>
                          message.error(e instanceof Error ? e.message : '下载失败'),
                        );
                      }}
                    >
                      {item.fileName}
                    </button>
                  ))}
                </div>
              ) : (
                <p className='text-sm text-black/45'>无</p>
              )}
            </section>

            <section>
              <h3 className='mb-2 text-sm font-medium'>电话沟通</h3>
              {(profile?.phones ?? []).length ? (
                <ul className='m-0 list-none space-y-2 p-0 text-sm'>
                  {profile?.phones.map((item, index) => (
                    <li key={`${item.applicationId}-${index}`}>
                      {item.calledAt || '—'} · {item.jobName || '未挂岗'} ·{' '}
                      {item.result === 'FAIL' ? '不通过' : item.result === 'PASS' ? '通过' : item.result || '—'}
                      {item.rejectReason ? ` · ${item.rejectReason}` : ''}
                      {item.remark ? ` · ${item.remark}` : ''}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className='text-sm text-black/45'>无</p>
              )}
            </section>

            <section>
              <h3 className='mb-2 text-sm font-medium'>面试评价</h3>
              {(profile?.interviews ?? []).length ? (
                <ul className='m-0 list-none space-y-2 p-0 text-sm'>
                  {profile?.interviews.map((item, index) => (
                    <li key={`${item.applicationId}-${item.roundNo}-${index}`}>
                      {item.interviewedAt || '—'} · {item.jobName || '未挂岗'} ·{' '}
                      {ROUND_LABEL[item.roundNo || 0] || `第${item.roundNo}轮`} · {item.interviewerName || '—'} ·{' '}
                      {CONCLUSION[item.conclusion || ''] || item.conclusion || '未填结论'}
                      {item.comment ? ` · ${item.comment}` : ''}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className='text-sm text-black/45'>无</p>
              )}
            </section>

            <section>
              <h3 className='mb-2 text-sm font-medium'>Offer</h3>
              {(profile?.offers ?? []).length ? (
                <ul className='m-0 list-none space-y-2 p-0 text-sm'>
                  {profile?.offers.map((item) => (
                    <li key={item.applicationId}>
                      {item.jobName || '未挂岗'} · {item.status || '—'}
                      {item.salaryAmount ? ` · ${item.salaryAmount}` : ''}
                      {item.offeredAt ? ` · ${item.offeredAt}` : ''}
                      {item.rejectReason ? ` · ${item.rejectReason}` : ''}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className='text-sm text-black/45'>无</p>
              )}
            </section>
          </div>
        ) : null}
      </Spin>
    </Drawer>
  );
}
