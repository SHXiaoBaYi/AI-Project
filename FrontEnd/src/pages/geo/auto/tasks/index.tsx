import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Switch, Tag, Tooltip } from 'antd';
import { BugOutlined, HistoryOutlined, PlayCircleOutlined } from '@ant-design/icons';
import BaseProTable from '@/components/BaseProTable';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { deleteGeoAutoTaskApi, getGeoAutoTaskListApi, runGeoAutoTaskApi, toggleGeoAutoTaskApi } from '@/api/geoAuto';
import type { GeoAutoTask } from '@/types/geoAuto';
import { GEO_AUTO_RUN_STATUS_MAP, GEO_AUTO_WEEK_DAYS } from '@/constants/geoAuto';
import TaskEditModal from './components/TaskEditModal';
import RunsDrawer from './components/RunsDrawer';
import BrowserProbeDrawer from './components/BrowserProbeDrawer';

/** 监测任务：问题×平台 定时自动监测 */
const AutoTaskPage = memo(function AutoTaskPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<GeoAutoTask | null>(null);
  const [runsTask, setRunsTask] = useState<GeoAutoTask | null>(null);
  const [probeOpen, setProbeOpen] = useState(false);

  const runTask = (record: GeoAutoTask) => {
    modal.confirm({
      title: '立即运行监测任务',
      content: `将驱动所选平台的网页版逐题提问，采集回答/思考过程/官方分享链接/整页截图，判定后自动回写日监测。浏览器采集为串行执行，耗时较长。`,
      okText: '开始运行',
      cancelText: '取消',
      onOk: async () => {
        const run = await runGeoAutoTaskApi(record.id);
        message.success(`已启动（批次 #${run.id}），可在运行记录中查看进度`);
        actionRef.current?.reload();
        setRunsTask({ ...record, lastRunId: run.id });
      },
    });
  };

  const columns: ProColumnType<GeoAutoTask>[] = [
    {
      title: '任务名',
      dataIndex: 'taskName',
      width: 180,
      ellipsis: true,
      formItemProps: { rules: [{ required: true, message: '请输入任务名' }] },
      render: (_, r) => <span className='font-medium'>{r.taskName}</span>,
    },
    {
      title: '圈选范围',
      width: 120,
      search: false,
      render: (_, r) => (
        <Tooltip title={r.scopeType === 'TOPIC' ? '按话题圈选' : '按问题圈选'}>
          <Tag color={r.scopeType === 'TOPIC' ? 'geekblue' : 'cyan'}>
            {r.scopeType === 'TOPIC'
              ? `话题 ${r.scopeIdList?.length ?? 0} 个`
              : `问题 ${r.scopeIdList?.length ?? 0} 个`}
          </Tag>
        </Tooltip>
      ),
    },
    {
      title: '平台',
      dataIndex: 'platformList',
      search: false,
      render: (_, r) => (
        <span>
          {(r.platformList || []).map((p) => (
            <Tag key={p}>{p}</Tag>
          ))}
        </span>
      ),
    },
    {
      title: '频率',
      dataIndex: 'freq',
      width: 130,
      search: false,
      render: (_, r) => {
        const time = r.runTime ? r.runTime.slice(0, 5) : '08:30';
        if (r.freq === '每周') {
          const day = GEO_AUTO_WEEK_DAYS.find((d) => d.value === r.weekDay)?.label || '周一';
          return (
            <Tag color='purple'>
              {day} {time}
            </Tag>
          );
        }
        return <Tag color='green'>每日 {time}</Tag>;
      },
    },
    {
      title: '取证配置',
      width: 130,
      search: false,
      render: (_, r) => (
        <div className='flex flex-col gap-1'>
          <Tag color={r.deepThink === 1 ? 'cyan' : 'default'}>思考 {r.deepThink === 1 ? '开' : '关'}</Tag>
          <span className='text-xs text-neutral-400'>
            {r.screenshotMode === 'DISABLED' ? '禁用截图' : r.screenshotMode === 'ALL' ? '全部截图' : '提及截图'}
          </span>
        </div>
      ),
    },
    {
      title: '问题数',
      dataIndex: 'questionCount',
      width: 80,
      search: false,
      render: (_, r) => r.questionCount ?? '-',
    },
    {
      title: '预估调用/日',
      dataIndex: 'estimatedDailyCalls',
      width: 110,
      search: false,
      render: (_, r) => r.estimatedDailyCalls ?? '-',
    },
    {
      title: '上次运行',
      dataIndex: 'lastRunStatus',
      width: 130,
      search: false,
      render: (_, r) => {
        if (!r.lastRunStatus) {
          return <span className='text-neutral-400'>未运行</span>;
        }
        const meta = GEO_AUTO_RUN_STATUS_MAP[r.lastRunStatus] || { label: r.lastRunStatus, color: 'default' };
        return (
          <div className='flex flex-col'>
            <Tag color={meta.color === 'processing' ? 'processing' : meta.color}>{meta.label}</Tag>
            <span className='text-xs text-neutral-400'>{r.lastRunProgress ? `进度 ${r.lastRunProgress}` : ''}</span>
          </div>
        );
      },
    },
    {
      title: '下次运行',
      dataIndex: 'nextRunTime',
      width: 150,
      search: false,
      render: (_, r) =>
        r.enabled === 1 ? (
          <span className='text-xs'>{r.nextRunTime?.replace('T', ' ') || '-'}</span>
        ) : (
          <span className='text-neutral-400'>已停用</span>
        ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 70,
      valueType: 'select',
      fieldProps: {
        options: [
          { label: '启用', value: 1 },
          { label: '停用', value: 0 },
        ],
      },
      render: (_, r) => (
        <Switch
          size='small'
          checked={r.enabled === 1}
          onChange={async (checked) => {
            await toggleGeoAutoTaskApi(r.id, checked ? 1 : 0);
            message.success(checked ? '已启用' : '已停用');
            actionRef.current?.reload();
          }}
        />
      ),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 230,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'run',
              label: '运行',
              perm: 'geo:auto:task:run',
              icon: <PlayCircleOutlined />,
              onClick: () => {
                runTask(record);
              },
            },
            {
              key: 'runs',
              label: '记录',
              icon: <HistoryOutlined />,
              onClick: () => {
                setRunsTask(record);
              },
            },
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:auto:task:edit',
              onClick: () => {
                setEditing(record);
                setEditOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:auto:task:delete',
              confirmTitle: '确定删除该任务？历史批次与结果将保留。',
              onClick: async () => {
                await deleteGeoAutoTaskApi(record.id);
                message.success('已删除');
                actionRef.current?.reload();
              },
            },
          ]}
        />
      ),
    },
  ];

  return (
    <>
      <BaseProTable<GeoAutoTask>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='监测任务'
        scroll={{ x: 1200 }}
        request={async (params) => {
          const res = await getGeoAutoTaskListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            taskName: params.taskName,
            enabled: params.enabled,
          });
          return { data: res.rows, success: true, total: res.total };
        }}
        toolBarRender={() => [
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:auto:task:add'
            onClick={() => {
              setEditing(null);
              setEditOpen(true);
            }}
          >
            新增任务
          </PermissionButton>,
          <PermissionButton
            key='probe'
            perm='geo:auto:task:run'
            icon={<BugOutlined />}
            onClick={() => setProbeOpen(true)}
          >
            采集诊断
          </PermissionButton>,
        ]}
      />
      <TaskEditModal
        open={editOpen}
        editing={editing}
        onClose={() => setEditOpen(false)}
        onSaved={() => actionRef.current?.reload()}
      />
      <RunsDrawer
        task={runsTask}
        onClose={() => setRunsTask(null)}
      />
      <BrowserProbeDrawer
        open={probeOpen}
        onClose={() => setProbeOpen(false)}
      />
    </>
  );
});

export default AutoTaskPage;
