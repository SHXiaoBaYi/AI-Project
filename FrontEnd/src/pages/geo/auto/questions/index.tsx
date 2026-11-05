import { memo, useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal, Switch, Tag } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import { getGeoTopicOptionsApi } from '@/api/geo';
import {
  createGeoAutoQuestionApi,
  deleteGeoAutoQuestionApi,
  deleteGeoAutoQuestionBatchApi,
  getGeoAutoQuestionListApi,
  seedGeoAutoQuestionsApi,
  setGeoAutoQuestionEnabledApi,
  updateGeoAutoQuestionApi,
} from '@/api/geoAuto';
import type { GeoAutoQuestion, GeoAutoQuestionDTO } from '@/types/geoAuto';
import type { GeoTopic } from '@/types/geo';
import { GEO_AUTO_FREQ, GEO_AUTO_FREQ_DEFAULT } from '@/constants/geoAuto';
import { createTimeDisplayColumn } from '@/components/table/createTimeColumns';

/** 监控问题库：目标问题结构化管理（自动监测的数据源） */
const AutoQuestionPage = memo(function AutoQuestionPage() {
  const { message, modal } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoAutoQuestion | null>(null);
  const [topics, setTopics] = useState<GeoTopic[]>([]);

  useEffect(() => {
    getGeoTopicOptionsApi()
      .then(setTopics)
      .catch(() => setTopics([]));
  }, []);

  const topicName = (topicId?: number) => topics.find((t) => t.id === topicId)?.topicName || '-';

  const doSeed = () => {
    modal.confirm({
      title: '从历史数据洗入问题库',
      content: '将扫描「日监测关键字」与「内容投放目标问题」，去重后导入不存在的问题。已存在的问题不受影响。',
      okText: '开始洗入',
      cancelText: '取消',
      onOk: async () => {
        const res = await seedGeoAutoQuestionsApi();
        message.success(
          `洗入完成：扫描 ${res.scanned} 个候选，新导入 ${res.imported} 个，跳过已存在 ${res.skipped} 个`,
        );
        actionRef.current?.reload();
      },
    });
  };

  const setEnabled = async (record: GeoAutoQuestion, enabled: boolean) => {
    await setGeoAutoQuestionEnabledApi([record.id], enabled ? 1 : 0);
    message.success(enabled ? '已启用' : '已停用');
    actionRef.current?.reload();
  };

  const columns: ProColumnType<GeoAutoQuestion>[] = [
    {
      title: '监控问题',
      dataIndex: 'questionText',
      ellipsis: true,
      width: 300,
      formItemProps: { rules: [{ required: true, message: '请输入监控问题' }] },
      fieldProps: {
        showCount: true,
        maxLength: 512,
        placeholder: '用户会向AI提出的完整问题，如：新疆适合寄内地的礼品有哪些？',
      },
    },
    {
      title: '所属话题',
      dataIndex: 'topicId',
      width: 140,
      valueType: 'select',
      fieldProps: { showSearch: true, options: topics.map((t) => ({ label: t.topicName, value: t.id })) },
      formItemProps: { rules: [{ required: true, message: '请选择话题' }] },
      render: (_, r) => <Tag color='blue'>{r.topicName || topicName(r.topicId)}</Tag>,
    },
    {
      title: '品牌词',
      dataIndex: 'brandWords',
      width: 140,
      search: false,
      ellipsis: true,
      fieldProps: { placeholder: '留空使用全局默认' },
      render: (_, r) => r.brandWords || <span className='text-neutral-400'>全局默认</span>,
    },
    {
      title: '竞品词',
      dataIndex: 'competitorWords',
      width: 140,
      search: false,
      ellipsis: true,
      fieldProps: { placeholder: '逗号分隔，留空使用全局默认' },
      render: (_, r) => r.competitorWords || <span className='text-neutral-400'>全局默认</span>,
    },
    {
      title: '频率',
      dataIndex: 'freq',
      width: 90,
      valueType: 'select',
      fieldProps: { options: [...GEO_AUTO_FREQ] },
      render: (_, r) => (r.freq === '每周' ? <Tag color='purple'>每周</Tag> : <Tag>每日</Tag>),
    },
    {
      title: '负责人',
      dataIndex: 'ownerName',
      width: 100,
      search: false,
      ellipsis: true,
      render: (_, r) => r.ownerName || '-',
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
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
          checked={r.enabled !== 0}
          onChange={(checked) => {
            void setEnabled(r, checked);
          }}
        />
      ),
    },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true, fieldProps: { placeholder: '选填' } },
    createTimeDisplayColumn<GeoAutoQuestion>(),
    {
      title: '操作',
      valueType: 'option',
      width: 140,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:auto:question:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:auto:question:delete',
              confirmTitle: '确定删除该问题？',
              onClick: async () => {
                await deleteGeoAutoQuestionApi(record.id);
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
      <BaseProTable<GeoAutoQuestion>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='监控问题库'
        scroll={{ x: 1100 }}
        request={async (params) => {
          const res = await getGeoAutoQuestionListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            questionText: params.questionText,
            topicId: params.topicId,
            freq: params.freq,
            enabled: params.enabled,
          });
          currentPageKeysRef.current = new Set(res.rows.map((r) => r.id));
          return { data: res.rows, success: true, total: res.total };
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, _rows, { type }) => {
            if (type === 'none') {
              return setSelectedRowKeys([]);
            }
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='seed'
            perm='geo:auto:question:add'
            onClick={() => {
              doSeed();
            }}
          >
            从历史数据洗入
          </PermissionButton>,
          <PermissionButton
            key='disable'
            perm='geo:auto:question:edit'
            disabled={selectedRowKeys.length === 0}
            onClick={() => {
              void (async () => {
                await setGeoAutoQuestionEnabledApi(selectedRowKeys as number[], 0);
                message.success(`已停用 ${selectedRowKeys.length} 个问题`);
                setSelectedRowKeys([]);
                actionRef.current?.reload();
              })();
            }}
          >
            批量停用
          </PermissionButton>,
          <PermissionButton
            key='enable'
            perm='geo:auto:question:edit'
            disabled={selectedRowKeys.length === 0}
            onClick={() => {
              void (async () => {
                await setGeoAutoQuestionEnabledApi(selectedRowKeys as number[], 1);
                message.success(`已启用 ${selectedRowKeys.length} 个问题`);
                setSelectedRowKeys([]);
                actionRef.current?.reload();
              })();
            }}
          >
            批量启用
          </PermissionButton>,
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='geo:auto:question:delete'
            disabled={selectedRowKeys.length === 0}
            onClick={() => {
              Modal.confirm({
                title: '批量删除监控问题',
                content: `确定要删除选中的 ${selectedRowKeys.length} 个问题吗？已有监测结果的问题不允许删除。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoAutoQuestionBatchApi(selectedRowKeys as number[]);
                  message.success('已删除');
                  setSelectedRowKeys([]);
                  currentPageKeysRef.current = new Set();
                  actionRef.current?.reload();
                },
              });
            }}
          >
            批量删除
          </PermissionButton>,
          <PermissionButton
            key='add'
            type='primary'
            perm='geo:auto:question:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增问题
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑监控问题' : '新增监控问题'}
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={editing ?? { freq: GEO_AUTO_FREQ_DEFAULT, enabled: 1 }}
        onFinish={async (values: Partial<GeoAutoQuestionDTO>) => {
          const payload: GeoAutoQuestionDTO = {
            ...values,
            id: editing?.id,
            questionText: values.questionText || '',
            topicId: values.topicId ?? editing?.topicId ?? 0,
            ownerName: values.ownerName || '',
            ownerUserId: editing?.ownerUserId ?? 0,
          };
          if (editing) {
            await updateGeoAutoQuestionApi(payload);
            message.success('已更新');
          } else {
            await createGeoAutoQuestionApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default AutoQuestionPage;
