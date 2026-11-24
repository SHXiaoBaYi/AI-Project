import { memo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Button, Input, Modal, Select, Table } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';
import BlacklistReasonModal from '@/pages/hr/application/components/BlacklistReasonModal';
import {
  getHrBlacklistApi,
  getHrBlacklistLogsApi,
  moveHrBlacklistOutApi,
  type HrBlacklistLog,
  type HrBlacklistRow,
} from '@/api/hr';

const BlacklistPage = memo(function BlacklistPage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [movingIds, setMovingIds] = useState<number[]>([]);
  const [logOpen, setLogOpen] = useState(false);
  const [logs, setLogs] = useState<HrBlacklistLog[]>([]);
  const [logTotal, setLogTotal] = useState(0);
  const [logPage, setLogPage] = useState(1);
  const [logKeyword, setLogKeyword] = useState('');
  const [logAction, setLogAction] = useState<string | undefined>();
  const [logLoading, setLogLoading] = useState(false);

  const loadLogs = async (page = logPage, keyword = logKeyword, action = logAction) => {
    setLogLoading(true);
    try {
      const res = await getHrBlacklistLogsApi({
        keyword: keyword || undefined,
        action,
        pageNum: page,
        pageSize: 10,
      });
      setLogs(res.rows ?? []);
      setLogTotal(res.total ?? 0);
      setLogPage(page);
    } finally {
      setLogLoading(false);
    }
  };

  const columns: ProColumnType<HrBlacklistRow>[] = [
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'out',
              label: '移出',
              perm: 'hr:blacklist:out',
              onClick: () => setMovingIds([record.id]),
            },
          ]}
        />
      ),
    },
    {
      title: '姓名/手机/邮箱',
      dataIndex: 'keyword',
      hideInTable: true,
    },
    {
      title: '姓名',
      dataIndex: 'displayName',
      search: false,
      width: 120,
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 140,
      search: false,
      render: (_, record) => record.phone || '—',
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 200,
      search: false,
      ellipsis: true,
      render: (_, record) => record.email || '—',
    },
    {
      title: '关联岗位',
      dataIndex: 'jobSummary',
      search: false,
      width: 280,
      ellipsis: true,
      render: (_, record) => record.jobSummary || '—',
    },
    {
      title: '移入原因',
      dataIndex: 'reason',
      search: false,
      width: 200,
      ellipsis: true,
    },
    {
      title: '备注',
      dataIndex: 'remark',
      search: false,
      width: 200,
      ellipsis: true,
    },
    {
      title: '操作人',
      dataIndex: 'operatorName',
      search: false,
      width: 110,
      render: (_, record) => record.operatorName || '—',
    },
    {
      title: '移入时间',
      dataIndex: 'blacklistedAt',
      search: false,
      width: 170,
      render: (_, record) => record.blacklistedAt || '—',
    },
  ];

  return (
    <>
      <BaseProTable<HrBlacklistRow>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='黑名单人才库'
        scroll={{ x: 1500 }}
        request={async (params) => {
          const keyword = params.keyword == null ? '' : String(params.keyword).trim();
          const rows = await getHrBlacklistApi(keyword || undefined);
          const pageSize = params.pageSize || 10;
          const current = params.current || 1;
          const start = (current - 1) * pageSize;
          const pageRows = rows.slice(start, start + pageSize);
          currentPageKeysRef.current = new Set(pageRows.map((row) => row.id));
          return { data: pageRows, success: true, total: rows.length };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => {
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((key) => global.delete(key));
              (keys as number[]).forEach((key) => global.add(key));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='out'
            perm='hr:blacklist:out'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要移出的人才');
                return;
              }
              setMovingIds(selectedRowKeys as number[]);
            }}
          >
            批量移出
          </PermissionButton>,
          <PermissionButton
            key='log'
            perm='hr:blacklist:log'
            onClick={() => {
              setLogKeyword('');
              setLogAction(undefined);
              setLogOpen(true);
              void loadLogs(1, '', undefined);
            }}
          >
            移入移出记录
          </PermissionButton>,
        ]}
      />
      <BlacklistReasonModal
        open={movingIds.length > 0}
        title={`移回正常人才库（${movingIds.length} 人）`}
        okText='确认移出'
        hint='移出后可以重新入库，并恢复面试邀约和考核计分。原因和备注都必填。'
        onClose={() => setMovingIds([])}
        onSubmit={async (values) => {
          await moveHrBlacklistOutApi({ ids: movingIds, reason: values.reason, remark: values.remark });
          message.success('已移回正常人才库');
          setSelectedRowKeys([]);
          actionRef.current?.reload();
        }}
      />
      <Modal
        title='移入移出记录'
        open={logOpen}
        footer={null}
        width={980}
        destroyOnHidden
        onCancel={() => setLogOpen(false)}
      >
        <div className='mb-3 flex flex-wrap items-center gap-2'>
          <Input
            value={logKeyword}
            allowClear
            placeholder='姓名、手机号或操作人'
            className='w-56'
            onChange={(event) => setLogKeyword(event.target.value)}
            onPressEnter={() => void loadLogs(1, logKeyword, logAction)}
          />
          <Select
            allowClear
            placeholder='全部动作'
            className='w-32'
            value={logAction}
            options={[
              { value: 'IN', label: '移入' },
              { value: 'OUT', label: '移出' },
            ]}
            onChange={(value) => setLogAction(value)}
          />
          <Button
            type='primary'
            onClick={() => void loadLogs(1, logKeyword, logAction)}
          >
            查询
          </Button>
        </div>
        <Table<HrBlacklistLog>
          rowKey='id'
          size='small'
          loading={logLoading}
          dataSource={logs}
          pagination={{
            current: logPage,
            pageSize: 10,
            total: logTotal,
            showSizeChanger: false,
            onChange: (page) => void loadLogs(page, logKeyword, logAction),
          }}
          columns={[
            { title: '动作', dataIndex: 'actionName', width: 80 },
            { title: '姓名', dataIndex: 'displayName', width: 100 },
            { title: '手机号', dataIndex: 'phone', width: 130, render: (value) => value || '—' },
            { title: '原因', dataIndex: 'reason', ellipsis: true },
            { title: '备注', dataIndex: 'remark', ellipsis: true },
            { title: '操作人', dataIndex: 'operatorName', width: 100 },
            { title: '时间', dataIndex: 'operatedAt', width: 170 },
          ]}
        />
      </Modal>
    </>
  );
});

export default BlacklistPage;
