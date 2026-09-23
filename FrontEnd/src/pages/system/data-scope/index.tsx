import { memo, useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Empty, Input, Radio, Result, Select, Space, Spin } from 'antd';
import {
  getDataScopeAccessApi,
  getDataScopeApi,
  listDataScopeUsersApi,
  saveDataScopeApi,
  type UserDataScopeVO,
} from '@/api/dataScope';

const DataScopePage = memo(function DataScopePage() {
  const { message, modal } = App.useApp();
  const [accessLoading, setAccessLoading] = useState(true);
  const [allowed, setAllowed] = useState(false);
  const [users, setUsers] = useState<UserDataScopeVO[]>([]);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<UserDataScopeVO | null>(null);
  const [mode, setMode] = useState('DEFAULT');
  const [targetIds, setTargetIds] = useState<number[]>([]);
  const [dirty, setDirty] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      setAccessLoading(true);
      try {
        const res = await getDataScopeAccessApi();
        setAllowed(!!res?.allowed);
        if (res?.allowed) {
          const rows = await listDataScopeUsersApi();
          setUsers(rows ?? []);
        }
      } catch {
        setAllowed(false);
      } finally {
        setAccessLoading(false);
      }
    })();
  }, []);

  const filtered = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    if (!text) return users;
    return users.filter((u) => `${u.nickname} ${u.username}`.toLowerCase().includes(text));
  }, [keyword, users]);

  const userOptions = useMemo(
    () =>
      users.map((u) => ({
        value: u.userId,
        label: `${u.nickname || u.username}（${u.username}）`,
      })),
    [users],
  );

  const loadDetail = async (userId: number) => {
    setLoadingDetail(true);
    try {
      const data = await getDataScopeApi(userId);
      setDetail(data);
      setMode(data.mode || 'DEFAULT');
      setTargetIds(data.targetUserIds ?? []);
      setDirty(false);
    } finally {
      setLoadingDetail(false);
    }
  };

  const selectUser = (userId: number) => {
    if (dirty) {
      modal.confirm({
        title: '有未保存的修改',
        content: '切换用户将丢弃当前修改，是否继续？',
        onOk: async () => {
          setSelectedId(userId);
          await loadDetail(userId);
        },
      });
      return;
    }
    setSelectedId(userId);
    void loadDetail(userId);
  };

  const handleSave = async () => {
    if (!selectedId) return;
    setSaving(true);
    try {
      await saveDataScopeApi({
        userId: selectedId,
        mode,
        targetUserIds: mode === 'PERSON' ? targetIds : [],
      });
      message.success('已保存');
      setDirty(false);
      const rows = await listDataScopeUsersApi();
      setUsers(rows ?? []);
      await loadDetail(selectedId);
    } finally {
      setSaving(false);
    }
  };

  if (accessLoading) {
    return (
      <div className='flex min-h-60 items-center justify-center'>
        <Spin />
      </div>
    );
  }

  if (!allowed) {
    return (
      <Result
        status='403'
        title='无权访问'
        subTitle='该页面仅本机 localhost 或指定账号可见'
      />
    );
  }

  return (
    <div className='flex min-h-[560px] gap-4'>
      <Card
        title='选择用户'
        className='w-72 shrink-0'
        styles={{ body: { paddingTop: 12 } }}
      >
        <Input
          allowClear
          placeholder='搜索昵称/用户名'
          className='mb-3'
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <div className='max-h-[480px] overflow-auto'>
          {filtered.map((u) => (
            <button
              key={u.userId}
              type='button'
              className={`mb-1 flex w-full flex-col rounded-lg px-3 py-2 text-left text-sm ${
                selectedId === u.userId ? 'bg-blue-50 text-blue-700' : 'hover:bg-neutral-50'
              }`}
              onClick={() => selectUser(u.userId)}
            >
              <span className='font-medium'>{u.nickname || u.username}</span>
              <span className='text-xs text-neutral-400'>
                {u.username} · {u.mode || 'DEFAULT'}
              </span>
            </button>
          ))}
          {!filtered.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> : null}
        </div>
      </Card>

      <Card
        className='min-w-0 flex-1'
        title={detail ? `配置：${detail.nickname || detail.username}` : '数据权限配置'}
        extra={
          <Space>
            <Button
              disabled={!selectedId || !dirty}
              onClick={() => selectedId && void loadDetail(selectedId)}
            >
              重置
            </Button>
            <Button
              type='primary'
              disabled={!selectedId || !dirty}
              loading={saving}
              onClick={() => void handleSave()}
            >
              保存
            </Button>
          </Space>
        }
      >
        {!selectedId ? (
          <Empty description='请从左侧选择用户' />
        ) : (
          <Spin spinning={loadingDetail}>
            <div className='flex max-w-xl flex-col gap-5'>
              <div>
                <div className='mb-2 text-sm font-medium text-neutral-700'>可见模式</div>
                <Radio.Group
                  value={mode}
                  onChange={(e) => {
                    setMode(e.target.value);
                    setDirty(true);
                  }}
                  options={[
                    { label: '沿用角色范围', value: 'DEFAULT' },
                    { label: '指定可见人', value: 'PERSON' },
                    { label: '仅本人相关', value: 'SELF' },
                  ]}
                />
              </div>
              {mode === 'PERSON' ? (
                <div>
                  <div className='mb-2 text-sm font-medium text-neutral-700'>可查看的人员</div>
                  <Select
                    mode='multiple'
                    allowClear
                    showSearch
                    optionFilterProp='label'
                    className='w-full'
                    placeholder='选择其相关数据对该用户可见的人员'
                    options={userOptions}
                    value={targetIds}
                    onChange={(ids) => {
                      setTargetIds(ids);
                      setDirty(true);
                    }}
                  />
                  <p className='mt-2 text-xs text-neutral-400'>
                    生效后，该用户只能看到与勾选人员相关的招聘数据（需求负责人、面试官等）。
                  </p>
                </div>
              ) : (
                <p className='text-sm text-neutral-500'>
                  {mode === 'SELF'
                    ? '仅看与本人相关的招聘数据。'
                    : '不额外收窄，仍按该用户角色上的数据范围（全量/负责人/面试官）执行。'}
                </p>
              )}
            </div>
          </Spin>
        )}
      </Card>
    </div>
  );
});

export default DataScopePage;
