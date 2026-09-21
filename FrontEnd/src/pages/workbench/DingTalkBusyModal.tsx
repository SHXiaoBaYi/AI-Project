import { memo, useEffect, useMemo, useState } from 'react';
import { App, DatePicker, Input, Modal, Select, Table, Tag } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getDingTalkBusyUsersApi,
  queryDingTalkBusyApi,
  type DingTalkBusySlot,
  type DingTalkBusyUser,
  type DingTalkBusyUserOption,
} from '@/api/dingtalk';

const STATUS_COLOR: Record<string, string> = {
  FREE: 'success',
  BUSY: 'error',
  TENTATIVE: 'warning',
};

type Props = {
  open: boolean;
  onClose: () => void;
};

const DingTalkBusyModal = memo(function DingTalkBusyModal({ open, onClose }: Props) {
  const { message } = App.useApp();
  const [users, setUsers] = useState<DingTalkBusyUserOption[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [userIds, setUserIds] = useState<number[]>([]);
  const [range, setRange] = useState<[Dayjs, Dayjs]>([
    dayjs().hour(9).minute(0).second(0),
    dayjs().hour(18).minute(0).second(0),
  ]);
  const [querying, setQuerying] = useState(false);
  const [result, setResult] = useState<DingTalkBusyUser[]>([]);
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    if (!open) return;
    setLoadingUsers(true);
    void getDingTalkBusyUsersApi()
      .then((rows) => setUsers(rows ?? []))
      .catch(() => setUsers([]))
      .finally(() => setLoadingUsers(false));
  }, [open]);

  const filtered = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) return result;
    return result
      .map((user) => {
        const name = `${user.nickname || ''} ${user.username || ''}`.toLowerCase();
        if (name.includes(text) || (user.error || '').toLowerCase().includes(text)) {
          return user;
        }
        const slots = (user.slots ?? []).filter((slot) => {
          const blob = `${slot.statusLabel} ${slot.status} ${slot.start} ${slot.end}`.toLowerCase();
          return blob.includes(text);
        });
        return slots.length ? { ...user, slots } : null;
      })
      .filter((user): user is DingTalkBusyUser => user != null);
  }, [keyword, result]);

  const handleQuery = async () => {
    if (userIds.length === 0) {
      message.warning('请选择用户');
      return;
    }
    if (!range?.[0] || !range?.[1] || !range[0].isBefore(range[1])) {
      message.warning('请选择有效的时间范围');
      return;
    }
    setQuerying(true);
    try {
      const rows = await queryDingTalkBusyApi({
        userIds,
        startTime: range[0].format('YYYY-MM-DD HH:mm:ss'),
        endTime: range[1].format('YYYY-MM-DD HH:mm:ss'),
      });
      setResult(rows ?? []);
      setKeyword('');
    } finally {
      setQuerying(false);
    }
  };

  return (
    <Modal
      title='钉钉日程闲忙'
      open={open}
      width={860}
      maskClosable={false}
      okText='查询'
      cancelText='关闭'
      confirmLoading={querying}
      onOk={() => void handleQuery()}
      onCancel={() => {
        if (querying) return;
        onClose();
      }}
    >
      <div className='mb-3 text-sm text-neutral-500'>
        只列出已绑定钉钉的用户。查询时用各自的 unionId 向钉钉取忙闲，未返回的时段按空闲补齐。一次最多 20 人。
      </div>
      <div className='mb-3 grid gap-3 md:grid-cols-2'>
        <Select
          mode='multiple'
          allowClear
          showSearch
          optionFilterProp='label'
          placeholder='选择用户，最多 20 人'
          loading={loadingUsers}
          value={userIds}
          onChange={(ids) => {
            if (ids.length > 20) {
              message.warning('一次最多选择 20 个用户');
              return;
            }
            setUserIds(ids);
          }}
          options={users.map((user) => ({
            value: user.userId,
            label: `${user.nickname || user.username}${user.username ? `（${user.username}）` : ''}`,
          }))}
        />
        <DatePicker.RangePicker
          showTime
          className='w-full'
          format='YYYY-MM-DD HH:mm'
          value={range}
          onChange={(value) => {
            if (value?.[0] && value?.[1]) setRange([value[0], value[1]]);
          }}
        />
      </div>
      {result.length > 0 ? (
        <>
          <Input.Search
            allowClear
            className='mb-3'
            placeholder='检索用户、时间或闲忙状态'
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
          {filtered.length === 0 ? (
            <div className='py-8 text-center text-sm text-neutral-400'>没有匹配的记录</div>
          ) : (
            <div className='flex max-h-[420px] flex-col gap-4 overflow-auto'>
              {filtered.map((user) => (
                <div key={user.userId}>
                  <div className='mb-2 flex flex-wrap items-center gap-2'>
                    <span className='text-sm font-medium text-neutral-800'>{user.nickname || user.username}</span>
                    {user.username ? <span className='text-xs text-neutral-400'>{user.username}</span> : null}
                    {user.error ? <Tag color='error'>{user.error}</Tag> : null}
                  </div>
                  <Table<DingTalkBusySlot>
                    size='small'
                    rowKey={(slot) => `${user.userId}-${slot.status}-${slot.start}-${slot.end}`}
                    pagination={false}
                    dataSource={user.slots ?? []}
                    locale={{ emptyText: user.error ? '未取到闲忙' : '该时段没有日程片段' }}
                    columns={[
                      { title: '开始', dataIndex: 'start', width: 180 },
                      { title: '结束', dataIndex: 'end', width: 180 },
                      {
                        title: '状态',
                        dataIndex: 'statusLabel',
                        width: 100,
                        render: (label: string, slot) => (
                          <Tag color={STATUS_COLOR[slot.status] || 'default'}>{label}</Tag>
                        ),
                      },
                    ]}
                  />
                </div>
              ))}
            </div>
          )}
        </>
      ) : null}
    </Modal>
  );
});

export default DingTalkBusyModal;
