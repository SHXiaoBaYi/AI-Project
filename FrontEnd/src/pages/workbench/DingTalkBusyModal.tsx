import { memo, useEffect, useMemo, useState } from 'react';
import { App, DatePicker, Input, Modal, Select, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
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

type TimelineRow = {
  key: string;
  label: string;
  byUser: Record<number, DingTalkBusySlot | undefined>;
};

function slotLabel(start: string, end: string) {
  const from = dayjs(start);
  const to = dayjs(end);
  if (!from.isValid() || !to.isValid()) return `${start}–${end}`;
  if (from.isSame(to, 'day')) return `${from.format('MM-DD HH:mm')}–${to.format('HH:mm')}`;
  return `${from.format('MM-DD HH:mm')}–${to.format('MM-DD HH:mm')}`;
}

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

  const timeline = useMemo(() => {
    const rows = new Map<string, TimelineRow>();
    for (const user of filtered) {
      for (const slot of user.slots ?? []) {
        const key = `${slot.start}|${slot.end}`;
        const row = rows.get(key) ?? { key, label: slotLabel(slot.start, slot.end), byUser: {} };
        row.byUser[user.userId] = slot;
        rows.set(key, row);
      }
    }
    return [...rows.values()].sort((a, b) => a.key.localeCompare(b.key));
  }, [filtered]);

  const columns = useMemo<ColumnsType<TimelineRow>>(() => {
    const people: ColumnsType<TimelineRow> = filtered.map((user) => ({
      title: (
        <div className='flex min-w-24 flex-col gap-1'>
          <span>{user.nickname || user.username}</span>
          {user.error ? <Tag color='error'>{user.error}</Tag> : null}
        </div>
      ),
      key: String(user.userId),
      width: 140,
      render: (_, row) => {
        const slot = row.byUser[user.userId];
        if (!slot) return <span className='text-neutral-300'>—</span>;
        return <Tag color={STATUS_COLOR[slot.status] || 'default'}>{slot.statusLabel}</Tag>;
      },
    }));
    return [{ title: '时间', dataIndex: 'label', width: 168, fixed: 'left' }, ...people];
  }, [filtered]);

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
        从系统用户里选择。已绑定钉钉的才能查出闲忙；未绑定的会标出来，需要先在用户管理里绑定。一次最多 20
        人。结果按半小时一段对齐展示，一段里只要有忙就整段算忙。
      </div>
      <div className='mb-3 grid gap-3 md:grid-cols-2'>
        <Select
          mode='multiple'
          allowClear
          showSearch
          optionFilterProp='label'
          placeholder={
            loadingUsers ? '正在加载系统用户' : users.length ? '选择系统用户，最多 20 人' : '没有启用的系统用户'
          }
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
            disabled: user.dingtalkBound !== 1,
            label: `${user.nickname || user.username}${user.username ? `（${user.username}）` : ''}${user.dingtalkBound === 1 ? '' : ' · 未绑定钉钉'}`,
          }))}
          notFoundContent={loadingUsers ? '加载中' : '没有系统用户'}
        />
        <DatePicker.RangePicker
          showTime={{ minuteStep: 30, format: 'HH:mm' }}
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
          ) : timeline.length === 0 ? (
            <div className='flex flex-col gap-2'>
              {filtered.map((user) => (
                <div
                  key={user.userId}
                  className='flex flex-wrap items-center gap-2 text-sm'
                >
                  <span className='font-medium text-neutral-800'>{user.nickname || user.username}</span>
                  {user.error ? (
                    <Tag color='error'>{user.error}</Tag>
                  ) : (
                    <span className='text-neutral-400'>没有可展示的时间段</span>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <Table<TimelineRow>
              size='small'
              rowKey='key'
              pagination={false}
              scroll={{ x: 168 + filtered.length * 140, y: 420 }}
              dataSource={timeline}
              columns={columns}
            />
          )}
        </>
      ) : null}
    </Modal>
  );
});

export default DingTalkBusyModal;
