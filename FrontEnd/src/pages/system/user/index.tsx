import { memo, useRef, useState, useMemo, useEffect, type Key } from 'react';
import { ProFormText, ProFormSelect } from '@ant-design/pro-components';
import type { ActionType, ProColumnType } from '@ant-design/pro-components';
import BaseProTable from '@/components/BaseProTable';
import BaseModalForm from '@/components/BaseModalForm/index';
import { App, Tag, Modal } from 'antd';
import {
  getUserListApi,
  createUserApi,
  updateUserApi,
  deleteUserApi,
  deleteUserBatchApi,
  resetPwdApi,
  assignRolesApi,
  exportUsersApi,
} from '@/api/user';
import { getRoleListApi } from '@/api/role';
import PermissionButton from '@/components/Buttons/PermissionButton';
import ActionButtons from '@/components/Buttons/ActionButtons';
import TableModal from '@/components/TableModal';
import dictionary from '@/dictionary';
import IconStatus from '@/components/IconStatus';
import tools from '@/utils/tools';
import validate from '@/utils/validate';
import type { UserVO } from '@/types/user';
import ImportUserModal from './components/ImportUserModal';

const UserManage = memo(function UserManage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType>(null);
  const [editingUser, setEditingUser] = useState<UserVO | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [resetPwdOpen, setResetPwdOpen] = useState(false);
  const [resetPwdUserId, setResetPwdUserId] = useState<number>(0);
  const [assignRoleOpen, setAssignRoleOpen] = useState(false);
  const [assignRoleUserId, setAssignRoleUserId] = useState<number>(0);
  const [initialRoleIds, setInitialRoleIds] = useState<number[]>([]);
  const [allRoles, setAllRoles] = useState<{ key: string; title: string }[]>([]);
  const [formModalKey, setFormModalKey] = useState(0);
  const [importOpen, setImportOpen] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);
  /** 当前页所有行的 key，用于跨页选择时正确合并 */
  const currentPageKeysRef = useRef<Set<number>>(new Set());

  const openUserModal = async (user?: UserVO) => {
    setEditingUser(user ?? null);
    setFormModalKey((k) => k + 1);
  };

  /** 页面挂载时预加载角色列表，避免弹窗打开时下拉数据为空 */
  useEffect(() => {
    getRoleListApi({ pageNum: 1, pageSize: 100 }).then((res) => {
      setAllRoles(res.rows?.map((r) => ({ key: String(r.roleId), title: r.roleName })));
    });
  }, []);

  /** 统一列配置：表格 + 详情（只读表单）+ 新增/编辑（可编辑表单）共用 */
  const columns: ProColumnType<UserVO>[] = useMemo(() => {
    const result: ProColumnType<UserVO>[] = [
      // 搜索框disabled会被编辑弹窗的disabled影响，需要写2个配置
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
        formItemProps: { rules: validate.username },
        fieldProps: editingUser ? { disabled: true } : undefined,
        search: false,
      },
      ...(!editingUser
        ? [
            {
              title: '密码',
              dataIndex: 'password',
              valueType: 'password',
              hideInTable: true,
              search: false,
              formItemProps: { rules: validate.password },
            } as ProColumnType<UserVO>,
          ]
        : []),
      { title: '昵称', dataIndex: 'nickname', width: 100, ellipsis: true },
      { title: '手机号', dataIndex: 'phone', width: 140, formItemProps: { rules: validate.phone } },
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
        title: '创建时间',
        dataIndex: 'createTime',
        width: 160,
        search: false,
        valueType: 'dateTime',
        hideInForm: true,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 200,
        fixed: 'right',
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
                key: 'resetPwd',
                perm: 'system:user:resetPwd',
                label: '重置密码',
                onClick: () => {
                  setResetPwdUserId(record.userId);
                  setResetPwdOpen(true);
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
                key: 'detail',
                label: '详情',
                onClick: () => {
                  openUserModal(record);
                  setDetailOpen(true);
                },
              },
              {
                key: 'delete',
                perm: 'system:user:delete',
                label: '删除',
                onClick: async () => {
                  await deleteUserApi(record.userId);
                  message.success('已删除');
                  actionRef.current?.reload();
                },
                confirmTitle: '确定删除吗?',
              },
            ]}
          />
        ),
      },
    ];
    return result;
  }, [allRoles, editingUser, message]);

  return (
    <>
      <BaseProTable<UserVO>
        headerTitle='用户列表'
        actionRef={actionRef}
        rowKey='userId'
        scroll={{ x: 600 }}
        search={{ labelWidth: 'auto' }}
        request={async (params) => {
          const { rows: data, total } = await getUserListApi(tools.handleSearchParams(params));
          currentPageKeysRef.current = new Set(data.map((u) => u.userId));
          return { data, total, success: true };
        }}
        columns={columns}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys, _rows, { type }) => {
            // 取消选择
            if (type === 'none') {
              return setSelectedRowKeys([]);
            }
            setSelectedRowKeys((prev) => {
              const global = new Set(prev as number[]);
              // 先移除当前页所有 key，再用 onChange 传入的 key 重新加入
              currentPageKeysRef.current.forEach((k) => global.delete(k));
              (keys as number[]).forEach((k) => global.add(k));
              return [...global];
            });
          },
        }}
        toolBarRender={() => [
          <PermissionButton
            key='import'
            color='default'
            variant='filled'
            perm='system:user:import'
            onClick={() => setImportOpen(true)}
          >
            批量导入
          </PermissionButton>,
          <PermissionButton
            key='export'
            color='default'
            variant='filled'
            perm='system:user:export'
            onClick={async () => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要导出的用户');
                return;
              }
              if (selectedRowKeys.length > 500) {
                message.warning('单次最多导出500条');
                return;
              }
              try {
                await exportUsersApi(selectedRowKeys as number[]);
                message.success('导出成功');
              } catch {
                message.error('导出失败');
              }
            }}
          >
            批量导出
          </PermissionButton>,
          <PermissionButton
            key='del'
            color='danger'
            variant='filled'
            perm='system:user:delete'
            onClick={() => {
              if (selectedRowKeys.length === 0) {
                message.warning('请先选择要删除的用户');
                return;
              }
              Modal.confirm({
                title: '批量删除用户',
                content: `确定要删除选中的 ${selectedRowKeys.length} 个用户吗？此操作不可撤销。`,
                okText: '确定删除',
                cancelText: '取消',
                okButtonProps: { danger: true },
                onOk: async () => {
                  await deleteUserBatchApi(selectedRowKeys as number[]);
                  message.success(`已删除 ${selectedRowKeys.length} 个用户`);
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
            perm='system:user:add'
            type='primary'
            onClick={() => {
              openUserModal();
              setFormOpen(true);
            }}
          >
            添加
          </PermissionButton>,
        ]}
      />

      {/* 新增 / 编辑 — 与详情共用 columns，TableModal 自动过滤 option 列 */}
      <TableModal
        key={formModalKey}
        readonly={false}
        title={editingUser ? '编辑用户' : '添加用户'}
        columns={columns as any}
        open={formOpen}
        onOpenChange={setFormOpen}
        initialValues={
          editingUser
            ? ({ ...editingUser, roleIds: editingUser.roles?.map((r) => r.roleId) } as any)
            : ({ gender: 0, status: 0 } as any)
        }
        onFinish={async (values) => {
          const dto = { ...values } as any;
          if (editingUser) {
            dto.userId = editingUser.userId;
            await updateUserApi(dto);
            message.success('已更新');
          } else {
            await createUserApi(dto);
            message.success('创建成功');
          }
          actionRef.current?.reload();
          return true;
        }}
      />

      {/* 详情 — 只读，columns 中 hideInForm: true 的列自动隐藏 */}
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

      {/* 重置密码 / 分配角色 — ProForm children 模式，继续使用 BaseModalForm */}
      <BaseModalForm
        title='重置密码'
        width={400}
        open={resetPwdOpen}
        onOpenChange={setResetPwdOpen}
        onFinish={async (values) => {
          await resetPwdApi(resetPwdUserId, values.password);
          message.success('密码已重置');
          return true;
        }}
      >
        <ProFormText.Password
          name='password'
          label='新密码'
          rules={[{ required: true, message: '请输入新密码' }]}
        />
        <ProFormText.Password
          name='confirm'
          label='确认密码'
          dependencies={['password']}
          rules={[
            { required: true, message: '请确认密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('password') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('两次输入的密码不一致'));
              },
            }),
          ]}
        />
      </BaseModalForm>

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
      {/* 批量导入 */}
      <ImportUserModal
        open={importOpen}
        onOpenChange={setImportOpen}
        onSuccess={() => actionRef.current?.reload()}
      />
    </>
  );
});
export default UserManage;
