import { memo, useEffect, useState } from 'react';
import { App, Card, Select, Table } from 'antd';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { ResumeViewButton } from '@/components/hr/ResumeDrawer';
import {
  addHrInviteInterviewerApi,
  forwardHrInviteApi,
  getHrUsersApi,
  listMyHrInvitesApi,
  saveHrInterviewRecordApi,
} from '@/api/hr';

const ROUNDS = [
  { value: 1, label: '一面' },
  { value: 2, label: '二面' },
  { value: 3, label: '三面' },
  { value: 4, label: '四面' },
  { value: 5, label: '五面' },
];

const CONCLUSIONS = [
  { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '未通过' },
  { value: 'PENDING', label: '待定' },
];

function ScheduleActions({
  row,
  users,
  onDone,
}: {
  row: Record<string, unknown>;
  users: { value: number; label: string }[];
  onDone: () => void;
}) {
  const { message } = App.useApp();
  const [target, setTarget] = useState<number>();
  const [conclusion, setConclusion] = useState<string>();
  const [comment, setComment] = useState('');
  return (
    <div className='flex flex-col gap-2'>
      <Select
        className='w-full'
        placeholder='选择面试官'
        options={users}
        showSearch
        optionFilterProp='label'
        onChange={setTarget}
      />
      <div className='flex gap-2'>
        <PermissionButton
          perm='hr:interview:mine'
          onClick={async () => {
            if (!target) return message.warning('请先选择面试官');
            await forwardHrInviteApi(Number(row.id), target);
            message.success('已转发，原日程已取消');
            onDone();
          }}
        >
          转发
        </PermissionButton>
        <PermissionButton
          perm='hr:interview:mine'
          onClick={async () => {
            if (!target) return message.warning('请先选择面试官');
            await addHrInviteInterviewerApi(Number(row.id), target);
            message.success('已添加面试官并建立日程');
            onDone();
          }}
        >
          添加面试官
        </PermissionButton>
      </div>
      <Select
        placeholder='面试结论'
        options={CONCLUSIONS}
        onChange={setConclusion}
      />
      <input
        className='rounded border border-neutral-300 px-2 py-1'
        placeholder='评语'
        value={comment}
        onChange={(event) => setComment(event.target.value)}
      />
      <PermissionButton
        perm='hr:interview:mine'
        type='primary'
        onClick={async () => {
          if (!conclusion) return message.warning('请选择结论');
          const msg = await saveHrInterviewRecordApi({
            applicationId: row.application_id,
            requisitionId: row.requisition_id,
            inviteId: row.id,
            roundNo: row.round_no,
            interviewerUserId: row.interviewer_user_id,
            conclusion,
            comment,
            interviewedAt: row.interview_at,
          });
          message.success(msg || '已保存');
          onDone();
        }}
      >
        提交结论
      </PermissionButton>
    </div>
  );
}
const MinePage = memo(function MinePage() {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [users, setUsers] = useState<{ value: number; label: string }[]>([]);

  const load = () => listMyHrInvitesApi().then(setRows);

  useEffect(() => {
    load();
    getHrUsersApi('interviewer').then((list) =>
      setUsers(
        list.map((item) => {
          const row = item as { userId?: number; user_id?: number; nickname: string };
          return { value: Number(row.userId ?? row.user_id), label: row.nickname };
        }),
      ),
    );
  }, []);

  return (
    <Card title='我的面试'>
      <p className='mb-3 text-sm text-neutral-500'>
        这里是还没填写通过或未通过结论的日程。转发出去后，这场日程从你这里移走；添加面试官会给对方再建一条钉钉日程。
      </p>
      <Table
        rowKey='id'
        dataSource={rows}
        pagination={false}
        scroll={{ x: 'max-content' }}
        columns={[
          {
            title: '操作',
            fixed: 'left',
            width: 280,
            render: (_, row) => (
              <ScheduleActions
                row={row}
                users={users}
                onDone={load}
              />
            ),
          },
          {
            title: '候选人',
            dataIndex: 'display_name',
          },
          {
            title: '简历',
            render: (_, row) => (
              <ResumeViewButton
                applicationId={Number(row.application_id)}
                fileName={row.file_name ? String(row.file_name) : undefined}
              />
            ),
          },
          { title: '岗位', dataIndex: 'job_name' },
          {
            title: '轮次',
            dataIndex: 'round_no',
            render: (value: number) => ROUNDS.find((item) => item.value === Number(value))?.label || value,
          },
          { title: '时间', dataIndex: 'interview_at' },
          { title: '地点', dataIndex: 'location' },
        ]}
      />
    </Card>
  );
});

export default MinePage;
