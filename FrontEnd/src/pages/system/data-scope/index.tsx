import { memo, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Empty,
  Input,
  Radio,
  Result,
  Select,
  Space,
  Spin,
  Switch,
  Tabs,
  Tag,
} from 'antd';
import {
  getDataScopeAccessApi,
  getDataScopeApi,
  getDataScopeMetaApi,
  listDataScopeUsersApi,
  saveDataScopeApi,
  type UserDataScopeGeo,
  type UserDataScopeHr,
  type UserDataScopeMeta,
  type UserDataScopeTask,
  type UserDataScopeVO,
} from '@/api/dataScope';

const emptyGeo = (): UserDataScopeGeo => ({
  enabled: false,
  topicIds: [],
  platformIds: [],
  selfOwnerOnly: false,
  selfWriterOnly: false,
  selfPublisherOnly: false,
});

const emptyHr = (): UserDataScopeHr => ({
  enabled: false,
  deptIds: [],
  personMode: 'DEFAULT',
  targetUserIds: [],
});

const emptyTask = (): UserDataScopeTask => ({
  enabled: false,
  taskTypeIds: [],
  ownerOnly: false,
  assigneeOnly: false,
});

const DataScopePage = memo(function DataScopePage() {
  const { message, modal } = App.useApp();
  const [accessLoading, setAccessLoading] = useState(true);
  const [allowed, setAllowed] = useState(false);
  const [meta, setMeta] = useState<UserDataScopeMeta | null>(null);
  const [users, setUsers] = useState<UserDataScopeVO[]>([]);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<UserDataScopeVO | null>(null);
  const [globalAll, setGlobalAll] = useState(false);
  const [geo, setGeo] = useState<UserDataScopeGeo>(emptyGeo());
  const [hr, setHr] = useState<UserDataScopeHr>(emptyHr());
  const [task, setTask] = useState<UserDataScopeTask>(emptyTask());
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
          const [rows, metaData] = await Promise.all([listDataScopeUsersApi(), getDataScopeMetaApi()]);
          setUsers(rows ?? []);
          setMeta(metaData);
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

  const topicOptions = useMemo(() => (meta?.topics ?? []).map((o) => ({ value: o.value, label: o.label })), [meta]);
  const platformOptions = useMemo(
    () =>
      (meta?.platforms ?? []).map((o) => ({
        value: o.value,
        label: o.extra ? `${o.label}（${o.extra}）` : o.label,
      })),
    [meta],
  );
  const deptOptions = useMemo(() => (meta?.departments ?? []).map((o) => ({ value: o.value, label: o.label })), [meta]);
  const taskTypeOptions = useMemo(
    () => (meta?.taskTypes ?? []).map((o) => ({ value: o.value, label: o.label })),
    [meta],
  );

  const applyDetail = (data: UserDataScopeVO) => {
    setDetail(data);
    setGlobalAll(!!data.globalAll);
    setGeo({ ...emptyGeo(), ...(data.geo || {}) });
    setHr({
      ...emptyHr(),
      ...(data.hr || {}),
      personMode: data.hr?.personMode || data.mode || 'DEFAULT',
      targetUserIds: data.hr?.targetUserIds ?? data.targetUserIds ?? [],
    });
    setTask({ ...emptyTask(), ...(data.task || {}) });
    setDirty(false);
  };

  const loadDetail = async (userId: number) => {
    setLoadingDetail(true);
    try {
      const data = await getDataScopeApi(userId);
      applyDetail(data);
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

  const markDirty = () => setDirty(true);

  const handleSave = async () => {
    if (!selectedId) return;
    setSaving(true);
    try {
      await saveDataScopeApi({
        userId: selectedId,
        globalAll,
        geo,
        hr,
        task,
      });
      message.success('已保存（本版先落配置；GEO/任务行级强制过滤后续接入）');
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
    <div className='flex min-h-[640px] gap-4'>
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
        <div className='max-h-[540px] overflow-auto'>
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
                {u.username} · {u.summary || '默认'}
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
            <div className='flex flex-col gap-4'>
              <Alert
                type='info'
                showIcon
                message='按模块配置可见数据切片。同模块内多条件为「且」；未开启的模块沿用系统默认（角色 / 我的）。'
                description='本版先实现配置页与落库；招聘「指定可见人」仍会收窄招聘列表，GEO/任务强制过滤后续接入。'
              />

              <div className='flex items-center justify-between rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3'>
                <div>
                  <div className='text-sm font-medium text-neutral-800'>全局全量业务数据</div>
                  <div className='text-xs text-neutral-500'>开启后忽略下方 GEO / 招聘 / 任务切片（适合 Bella）</div>
                </div>
                <Switch
                  checked={globalAll}
                  onChange={(v) => {
                    setGlobalAll(v);
                    markDirty();
                  }}
                />
              </div>

              <Tabs
                className={globalAll ? 'pointer-events-none opacity-50' : undefined}
                items={[
                  {
                    key: 'geo',
                    label: (
                      <span>
                        GEO{' '}
                        {geo.enabled ? (
                          <Tag
                            color='blue'
                            className='ml-1!'
                          >
                            开
                          </Tag>
                        ) : null}
                      </span>
                    ),
                    children: (
                      <div className='flex max-w-2xl flex-col gap-4 pt-2'>
                        <div className='flex items-center justify-between'>
                          <span className='text-sm font-medium'>启用 GEO 切片</span>
                          <Switch
                            checked={!!geo.enabled}
                            disabled={globalAll}
                            onChange={(v) => {
                              setGeo((g) => ({ ...g, enabled: v }));
                              markDirty();
                            }}
                          />
                        </div>
                        <div>
                          <div className='mb-2 text-sm text-neutral-700'>可见话题</div>
                          <Select
                            mode='multiple'
                            allowClear
                            showSearch
                            optionFilterProp='label'
                            className='w-full'
                            disabled={globalAll || !geo.enabled}
                            placeholder='不选 = 不按话题限制'
                            options={topicOptions}
                            value={geo.topicIds}
                            onChange={(ids) => {
                              setGeo((g) => ({ ...g, topicIds: ids }));
                              markDirty();
                            }}
                          />
                        </div>
                        <div>
                          <div className='mb-2 text-sm text-neutral-700'>可见平台（与话题同时配置时为且）</div>
                          <Select
                            mode='multiple'
                            allowClear
                            showSearch
                            optionFilterProp='label'
                            className='w-full'
                            disabled={globalAll || !geo.enabled}
                            placeholder='例如仅百家号'
                            options={platformOptions}
                            value={geo.platformIds}
                            onChange={(ids) => {
                              setGeo((g) => ({ ...g, platformIds: ids }));
                              markDirty();
                            }}
                          />
                        </div>
                        <Checkbox.Group
                          disabled={globalAll || !geo.enabled}
                          value={[
                            geo.selfOwnerOnly ? 'owner' : '',
                            geo.selfWriterOnly ? 'writer' : '',
                            geo.selfPublisherOnly ? 'publisher' : '',
                          ].filter(Boolean)}
                          onChange={(vals) => {
                            const set = new Set(vals as string[]);
                            setGeo((g) => ({
                              ...g,
                              selfOwnerOnly: set.has('owner'),
                              selfWriterOnly: set.has('writer'),
                              selfPublisherOnly: set.has('publisher'),
                            }));
                            markDirty();
                          }}
                          options={[
                            { label: '仅本人负责的日监测', value: 'owner' },
                            { label: '仅本人撰写的投放', value: 'writer' },
                            { label: '仅本人发布的投放', value: 'publisher' },
                          ]}
                        />
                      </div>
                    ),
                  },
                  {
                    key: 'hr',
                    label: (
                      <span>
                        招聘{' '}
                        {hr.enabled ? (
                          <Tag
                            color='blue'
                            className='ml-1!'
                          >
                            开
                          </Tag>
                        ) : null}
                      </span>
                    ),
                    children: (
                      <div className='flex max-w-2xl flex-col gap-4 pt-2'>
                        <div className='flex items-center justify-between'>
                          <span className='text-sm font-medium'>启用招聘切片</span>
                          <Switch
                            checked={!!hr.enabled}
                            disabled={globalAll}
                            onChange={(v) => {
                              setHr((h) => ({ ...h, enabled: v }));
                              markDirty();
                            }}
                          />
                        </div>
                        <div>
                          <div className='mb-2 text-sm text-neutral-700'>可见部门（含下级）</div>
                          <Select
                            mode='multiple'
                            allowClear
                            showSearch
                            optionFilterProp='label'
                            className='w-full'
                            disabled={globalAll || !hr.enabled}
                            placeholder='例如仅财务部'
                            options={deptOptions}
                            value={hr.deptIds}
                            onChange={(ids) => {
                              setHr((h) => ({ ...h, deptIds: ids }));
                              markDirty();
                            }}
                          />
                        </div>
                        <div>
                          <div className='mb-2 text-sm text-neutral-700'>人员相关范围</div>
                          <Radio.Group
                            disabled={globalAll || !hr.enabled}
                            value={hr.personMode || 'DEFAULT'}
                            onChange={(e) => {
                              setHr((h) => ({ ...h, personMode: e.target.value }));
                              markDirty();
                            }}
                            options={[
                              { label: '沿用角色范围', value: 'DEFAULT' },
                              { label: '指定可见人', value: 'PERSON' },
                              { label: '仅本人相关', value: 'SELF' },
                            ]}
                          />
                        </div>
                        {hr.personMode === 'PERSON' ? (
                          <div>
                            <div className='mb-2 text-sm text-neutral-700'>可查看的人员</div>
                            <Select
                              mode='multiple'
                              allowClear
                              showSearch
                              optionFilterProp='label'
                              className='w-full'
                              disabled={globalAll || !hr.enabled}
                              placeholder='需求负责人 / 面试官等相关'
                              options={userOptions}
                              value={hr.targetUserIds}
                              onChange={(ids) => {
                                setHr((h) => ({ ...h, targetUserIds: ids }));
                                markDirty();
                              }}
                            />
                          </div>
                        ) : (
                          <p className='text-xs text-neutral-400'>
                            {hr.personMode === 'SELF'
                              ? '仅看与本人相关的招聘数据（负责人/面试官）。'
                              : '不额外按人收窄，仍按角色上的 hr:scope。'}
                          </p>
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'task',
                    label: (
                      <span>
                        任务{' '}
                        {task.enabled ? (
                          <Tag
                            color='blue'
                            className='ml-1!'
                          >
                            开
                          </Tag>
                        ) : null}
                      </span>
                    ),
                    children: (
                      <div className='flex max-w-2xl flex-col gap-4 pt-2'>
                        <div className='flex items-center justify-between'>
                          <span className='text-sm font-medium'>启用任务切片</span>
                          <Switch
                            checked={!!task.enabled}
                            disabled={globalAll}
                            onChange={(v) => {
                              setTask((t) => ({ ...t, enabled: v }));
                              markDirty();
                            }}
                          />
                        </div>
                        <div>
                          <div className='mb-2 text-sm text-neutral-700'>可见任务类型</div>
                          <Select
                            mode='multiple'
                            allowClear
                            showSearch
                            optionFilterProp='label'
                            className='w-full'
                            disabled={globalAll || !task.enabled}
                            placeholder='例如仅「日常」或「薪资沟通」'
                            options={taskTypeOptions}
                            value={task.taskTypeIds}
                            onChange={(ids) => {
                              setTask((t) => ({ ...t, taskTypeIds: ids }));
                              markDirty();
                            }}
                          />
                        </div>
                        <Checkbox.Group
                          disabled={globalAll || !task.enabled}
                          value={[task.ownerOnly ? 'owner' : '', task.assigneeOnly ? 'assignee' : ''].filter(Boolean)}
                          onChange={(vals) => {
                            const set = new Set(vals as string[]);
                            setTask((t) => ({
                              ...t,
                              ownerOnly: set.has('owner'),
                              assigneeOnly: set.has('assignee'),
                            }));
                            markDirty();
                          }}
                          options={[
                            { label: '仅本人负责', value: 'owner' },
                            { label: '仅本人办理', value: 'assignee' },
                          ]}
                        />
                      </div>
                    ),
                  },
                ]}
              />
            </div>
          </Spin>
        )}
      </Card>
    </div>
  );
});

export default DataScopePage;
