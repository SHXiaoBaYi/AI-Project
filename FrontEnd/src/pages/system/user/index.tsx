import { memo, useRef, useState, useMemo, useEffect } from 'react';
import { ProFormSelect } from '@ant-design/pro-components';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm/index';
import { App, Tag } from 'antd';
import { getUserListApi, updateUserApi, assignRolesApi } from '@/api/user';
import { getRoleListApi } from '@/api/role';
import ActionButtons from '@/components/Buttons/ActionButtons';
import TableModal from '@/components/TableModal';
import dictionary from '@/dictionary';
import IconStatus from '@/components/IconStatus';
import tools from '@/utils/tools';
import validate from '@/utils/validate';
import type { UserVO } from '@/types/user';
import BindDingTalkModal from './components/BindDingTalkModal';
import { unbindHrDingTalkApi } from '@/api/hr';
import { createTimeDisplayColumn, createTimeRangeColumn } from '@/components/table/createTimeColumns';

const UserManage = memo(function UserManage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [editingUser, setEditingUser] = useState<UserVO | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [assignRoleOpen, setAssignRoleOpen] = useState(false);
  const [assignRoleUserId, setAssignRoleUserId] = useState<number>(0);
  const [initialRoleIds, setInitialRoleIds] = useState<number[]>([]);
  const [allRoles, setAllRoles] = useState<{ key: string; title: string }[]>([]);
  const [formModalKey, setFormModalKey] = useState(0);
  const [bindUser, setBindUser] = useState<UserVO | null>(null);
  const [bindOpen, setBindOpen] = useState(false);

  const openUserModal = async (user?: UserVO) => {
    setEditingUser(user ?? null);
    setFormModalKey((k) => k + 1);
  };

  useEffect(() => {
    getRoleListApi({ pageNum: 1, pageSize: 100 }).then((res) => {
      setAllRoles(res.rows?.map((r) => ({ key: String(r.roleId), title: r.roleName })));
    });
  }, []);

  const columns: ProColumnType<UserVO>[] = useMemo(() => {
    const result: ProColumnType<UserVO>[] = [
      {
        title: '用户名',
        dataIndex: 'username',
        width: 100,
        hideInForm: true,
        ellipsis: true,
      },
      {
        title: '用户名',
        dataIndex: 'username',
        hideInTable: true,
        search: false,
        fieldProps: { disabled: true },
      },
      { title: '昵称', dataIndex: 'nickname', width: 100, ellipsis: true },
      { title: '手机号', dataIndex: 'phone', width: 140, formItemProps: { rules: validate.phone } },
      {
        title: '钉钉',
        dataIndex: 'dingtalkBound',
        width: 90,
        search: false,
        hideInForm: true,
        render: (_, record) => (record.dingtalkBound === 1 ? <Tag color='green'>已绑定</Tag> : <Tag>未绑定</Tag>),
      },
      {
        title: '企业用户ID',
        dataIndex: 'dingtalkUserId',
        width: 140,
        search: false,
        hideInForm: true,
        ellipsis: true,
        render: (_, record) => record.dingtalkUserId || '—',
      },
      {
        title: 'unionId',
        dataIndex: 'dingtalkUnionId',
        width: 180,
        search: false,
        hideInForm: true,
        ellipsis: true,
        render: (_, record) => record.dingtalkUnionId || '—',
      },
      { title: '邮箱', dataIndex: 'email', width: 200, ellipsis: true, formItemProps: { rules: validate.email } },
      {
        title: '角色',
        dataIndex: 'roles',
        hideInForm: true,
        width: 200,
        search: false,
        render: (_, record) =>
          (record.roles?.length && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {record.roles?.map((r) => (
                <Tag
                  key={r.roleId}
                  color='blue'
                >
                  {r.roleName}
                </Tag>
              ))}
            </div>
          )) ||
          '-',
      },
      {
        title: '角色',
        dataIndex: 'roleIds',
        width: 200,
        hideInTable: true,
        valueType: 'select',
        fieldProps: {
          mode: 'multiple',
          placeholder: '请选择角色',
          options: allRoles?.map((r) => ({ value: Number(r.key), label: r.title })),
        },
        formItemProps: { rules: [{ required: true, message: '请选择角色' }] },
      },
      {
        title: '性别',
        dataIndex: 'gender',
        search: false,
        valueType: 'select',
        hideInTable: true,
        fieldProps: { options: dictionary.gender },
      },
      {
        title: '账户状态',
        dataIndex: 'status',
        width: 80,
        valueType: 'select',
        hideInForm: true,
        fieldProps: {
          options: [{ label: '全部', value: '' }, ...dictionary.userStatus],
        },
        render: (_, { status }) => (
          <IconStatus
            type='userStatus'
            value={status}
          />
        ),
      },
      {
        title: '账户状态',
        dataIndex: 'status',
        search: false,
        hideInTable: true,
        valueType: 'select',
        fieldProps: {
          options: dictionary.userStatus,
        },
        formItemProps: { rules: [{ required: true, message: '请选择状态' }] },
      },
      {
        title: '是否绑定钉钉',
        dataIndex: 'dingtalkBound',
        hideInTable: true,
        hideInForm: true,
        valueType: 'select',
        fieldProps: {
          allowClear: true,
          options: [
            { label: '已绑定', value: 1 },
            { label: '未绑定', value: 0 },
          ],
        },
      },
      createTimeRangeColumn<UserVO>(),
      createTimeDisplayColumn<UserVO>({ width: 160 }),
      {
        title: '操作',
        valueType: 'option',
        width: 200,
        render: (_, record) => (
          <ActionButtons
            items={[
              {
                key: 'edit',
                perm: 'system:user:edit',
                label: '编辑',
                onClick: () => {
                  openUserModal(record);
                  setFormOpen(true);
                },
              },
              {
                key: 'assignRole',
                perm: 'system:user:role-management',
                label: '分配角色',
                onClick: async () => {
                  setAssignRoleUserId(record.userId);
                  setInitialRoleIds(record.roles?.map((r) => r.roleId));
                  setAssignRoleOpen(true);
                },
              },
              {
                key: 'dingtalk',
                perm: 'system:user:edit',
                label: '绑定钉钉',
                onClick: () => {
                  setBindUser(record);
                  setBindOpen(true);
                },
              },
              {
                key: 'unbindDing',
                perm: 'system:user:edit',
                label: '解除钉钉',
                disabled: record.dingtalkBound !== 1,
                confirmTitle: '解除后，这个用户的面试邀约不能自动建钉钉日程',
                onClick: async () => {
                  await unbindHrDingTalkApi(record.userId);
                  message.success('已解除');
                  actionRef.current?.reload();
                },
              },
              {
                key: 'detail',
                label: '详情',
                onClick: () => {
                  openUserModal(record);
                  setDetailOpen(true);
                },
              },
            ]}
          />
        ),
      },
    ];
    return result;
  }, [allRoles, message]);

  return (
    <>
      <BaseProTable<UserVO>
        headerTitle='用户列表'
        actionRef={actionRef}
        rowKey='userId'
        scroll={{ x: 1680 }}
        request={async (params) => {
          const { rows: data, total } = await getUserListApi(tools.handleSearchParams(params));
          return { data, total, success: true };
        }}
        columns={columns}
        toolBarRender={() => [
          <span
            key='hint'
            className='text-sm text-neutral-500'
          >
            新用户请通过钉钉扫码登录自动注册（默认普通账号）
          </span>,
        ]}
      />

      <TableModal
        key={formModalKey}
        readonly={false}
        title='编辑用户'
        columns={columns as any}
        open={formOpen}
        onOpenChange={setFormOpen}
        initialValues={
          editingUser
            ? ({ ...editingUser, roleIds: editingUser.roles?.map((r) => r.roleId) } as any)
            : ({ gender: 0, status: 0 } as any)
        }
        onFinish={async (values) => {
          if (!editingUser) return false;
          const dto = { ...values, userId: editingUser.userId } as any;
          await updateUserApi(dto);
          message.success('已更新');
          actionRef.current?.reload();
          return true;
        }}
      />

      <TableModal
        title='用户详情'
        columns={columns as any}
        open={detailOpen}
        onOpenChange={setDetailOpen}
        modalProps={{ destroyOnHidden: true }}
        initialValues={{
          ...editingUser,
          roleIds: editingUser?.roles?.map((r) => r.roleId),
        }}
      />

      <BaseModalForm
        title='分配角色'
        width={400}
        open={assignRoleOpen}
        onOpenChange={setAssignRoleOpen}
        modalProps={{ styles: { body: { paddingBottom: '50px' } } }}
        initialValues={{ roleIds: initialRoleIds }}
        onFinish={async (values) => {
          await assignRolesApi(assignRoleUserId, values.roleIds);
          message.success('已分配');
          actionRef.current?.reload();
          return true;
        }}
      >
        <ProFormSelect
          name='roleIds'
          label='角色'
          rules={[{ required: true, message: '请选择角色' }]}
          mode='multiple'
          placeholder='请选择角色'
          options={allRoles?.map((r) => ({ value: Number(r.key), label: r.title }))}
        />
      </BaseModalForm>
      <BindDingTalkModal
        user={bindUser}
        open={bindOpen}
        onOpenChange={setBindOpen}
        onSuccess={() => actionRef.current?.reload()}
      />
    </>
  );
});
export default UserManage;
