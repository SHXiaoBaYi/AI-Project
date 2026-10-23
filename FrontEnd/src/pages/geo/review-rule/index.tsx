import { memo, useEffect, useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import { App, Modal } from 'antd';
import BaseProTable from '@/components/BaseProTable';
import TableModal from '@/components/TableModal';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import {
  createGeoReviewRuleApi,
  deleteGeoReviewRuleApi,
  deleteGeoReviewRuleBatchApi,
  getGeoOwnerOptionsApi,
  getGeoReviewRuleListApi,
  updateGeoReviewRuleApi,
} from '@/api/geo';
import type { GeoContentReviewRule, GeoOwnerOption } from '@/types/geo';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const ReviewRulePage = memo(function ReviewRulePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const currentPageKeysRef = useRef<Set<number>>(new Set());
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GeoContentReviewRule | null>(null);
  const [owners, setOwners] = useState<GeoOwnerOption[]>([]);

  useEffect(() => {
    void getGeoOwnerOptionsApi().then((list) => setOwners(list ?? []));
  }, []);

  const userOptions = useMemo(() => owners.map((u) => ({ label: u.displayName, value: u.userId })), [owners]);

  const columns: ProColumnType<GeoContentReviewRule>[] = [
    {
      title: '撰写人',
      dataIndex: 'writerUserId',
      valueType: 'select',
      fieldProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: userOptions,
        placeholder: '请选择撰写人',
      },
      formItemProps: { rules: [{ required: true, message: '请选择撰写人' }] },
      render: (_, record) => record.writerName || '-',
    },
    {
      title: '跳过一审',
      dataIndex: 'skipStage1',
      width: 100,
      valueType: 'switch',
      fieldProps: { checkedChildren: '是', unCheckedChildren: '否' },
      valueEnum: {
        1: { text: '是' },
        0: { text: '否' },
      },
      formItemProps: { rules: [{ required: true, message: '请选择是否跳过一审' }] },
      render: (_, record) => (Number(record.skipStage1) === 1 ? '是' : '否'),
    },
    {
      title: 'AI预审',
      dataIndex: 'aiPreReview',
      width: 100,
      valueType: 'switch',
      fieldProps: { checkedChildren: '开', unCheckedChildren: '关' },
      search: false,
      render: (_, record) => (Number(record.aiPreReview ?? 1) === 1 ? '开' : '关'),
    },
    {
      title: 'AI模式',
      dataIndex: 'aiMode',
      width: 180,
      valueType: 'select',
      search: false,
      fieldProps: {
        options: [
          { label: '仅辅助', value: 'assist' },
          { label: 'AI通过可跳过一审', value: 'skip_stage1_on_pass' },
        ],
      },
      render: (_, record) => (record.aiMode === 'skip_stage1_on_pass' ? 'AI通过可跳过一审' : '仅辅助'),
    },
    {
      title: '通过前等AI',
      dataIndex: 'aiBlockApprove',
      width: 120,
      valueType: 'switch',
      fieldProps: { checkedChildren: '是', unCheckedChildren: '否' },
      search: false,
      render: (_, record) => (Number(record.aiBlockApprove) === 1 ? '是' : '否'),
    },
    { title: '备注', dataIndex: 'remark', search: false, ellipsis: true },
    createTimeRangeColumn<GeoContentReviewRule>(),
    createTimeDisplayColumn<GeoContentReviewRule>(),
    {
      title: '操作',
      valueType: 'option',
      width: 160,
      render: (_, record) => (
        <ActionButtons
          items={[
            {
              key: 'edit',
              label: '编辑',
              perm: 'geo:reviewRule:edit',
              onClick: () => {
                setEditing(record);
                setOpen(true);
              },
            },
            {
              key: 'delete',
              label: '删除',
              perm: 'geo:reviewRule:delete',
              confirmTitle: '确定删除该审核规则？',
              onClick: async () => {
                await deleteGeoReviewRuleApi(record.id);
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
      <BaseProTable<GeoContentReviewRule>
        rowKey='id'
        actionRef={actionRef}
        columns={columns}
        headerTitle='文章审核规则'
        scroll={{ x: 1100 }}
        request={async (params) => {
          const skip =
            params.skipStage1 === true || params.skipStage1 === 1 || params.skipStage1 === '1'
              ? 1
              : params.skipStage1 === false || params.skipStage1 === 0 || params.skipStage1 === '0'
                ? 0
                : undefined;
          const res = await getGeoReviewRuleListApi({
            pageNum: params.current,
            pageSize: params.pageSize,
            writerUserId: params.writerUserId,
            skipStage1: skip,
            createTimeStart: params.createTimeStart,
            createTimeEnd: params.createTimeEnd,
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
            key='del'
            color='danger'
            variant='filled'
            perm='geo:reviewRule:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的规则');
                return;
              }
              Modal.confirm({
                title: '批量删除审核规则',
                content: `确定要删除选中的 ${selectedRowKeys.length} 条规则吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteGeoReviewRuleBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 条规则`);
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
            perm='geo:reviewRule:add'
            onClick={() => {
              setEditing(null);
              setOpen(true);
            }}
          >
            新增规则
          </PermissionButton>,
        ]}
      />
      <TableModal
        readonly={false}
        title={editing ? '编辑审核规则' : '新增审核规则'}
        columns={columns as any}
        open={open}
        onOpenChange={setOpen}
        initialValues={
          editing
            ? {
                ...editing,
                skipStage1: Number(editing.skipStage1) === 1,
                aiPreReview: Number(editing.aiPreReview ?? 1) === 1,
                aiMode: editing.aiMode || 'assist',
                aiBlockApprove: Number(editing.aiBlockApprove) === 1,
              }
            : { skipStage1: true, aiPreReview: true, aiMode: 'assist', aiBlockApprove: false }
        }
        onFinish={async (values) => {
          const payload = {
            writerUserId: values.writerUserId,
            skipStage1: values.skipStage1 === true || values.skipStage1 === 1 ? 1 : 0,
            aiPreReview: values.aiPreReview === true || values.aiPreReview === 1 ? 1 : 0,
            aiMode: values.aiMode === 'skip_stage1_on_pass' ? 'skip_stage1_on_pass' : 'assist',
            aiBlockApprove: values.aiBlockApprove === true || values.aiBlockApprove === 1 ? 1 : 0,
            remark: values.remark,
          };
          if (editing) {
            await updateGeoReviewRuleApi({ ...payload, id: editing.id });
            message.success('已更新');
          } else {
            await createGeoReviewRuleApi(payload);
            message.success('已创建');
          }
          actionRef.current?.reload();
          return true;
        }}
      />
    </>
  );
});

export default ReviewRulePage;
