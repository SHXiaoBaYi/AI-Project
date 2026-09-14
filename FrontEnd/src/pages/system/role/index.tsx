import { useState, useEffect, useMemo } from 'react';
import { ProFormText, ProFormSelect, ProFormDigit, ProFormTextArea } from '@ant-design/pro-components';
import { Button, Card, List, App, Tree, Empty, Space, Tag, Popconfirm, Input, Checkbox, theme } from 'antd';
import { SafetyOutlined, UserOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import {
  getRoleListApi,
  createRoleApi,
  updateRoleApi,
  deleteRoleApi,
  assignMenusApi,
  getRoleByIdApi,
} from '@/api/role';
import { getMenuTreeApi } from '@/api/menu';
import PermissionButton from '@/components/Buttons/PermissionButton';
import BaseModalForm from '@/components/BaseModalForm';
import type { RoleVO } from '@/types/role';
import type { MenuTree } from '@/types/menu';
import dictionary from '../../../dictionary';

export default function RoleManage() {
  const { message } = App.useApp();
  const [roles, setRoles] = useState<RoleVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedRole, setSelectedRole] = useState<RoleVO | null>(null);
  const [menuTree, setMenuTree] = useState<MenuTree[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
  const [allMenuIds, setAllMenuIds] = useState<number[]>([]);
  const parentMap = useMemo(() => buildParentMap(menuTree), [menuTree]);
  const leafIdSet = useMemo(() => collectLeafIds(menuTree), [menuTree]);
  const disabledMenuIds = useMemo(() => collectDisabledIds(menuTree), [menuTree]);
  const enabledLeafIds = useMemo(
    () => new Set([...leafIdSet].filter((id) => !disabledMenuIds.has(id))),
    [leafIdSet, disabledMenuIds],
  );
  const [saving, setSaving] = useState(false);
  const [editingRole, setEditingRole] = useState<RoleVO | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [isExpanded, setIsExpanded] = useState(true);
  const { token } = theme.useToken();

  const loadRoles = async () => {
    setLoading(true);
    try {
      const res = await getRoleListApi({ pageNum: 1, pageSize: 100 });
      setRoles(res.rows);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const [roleRes, tree] = await Promise.all([getRoleListApi({ pageNum: 1, pageSize: 100 }), getMenuTreeApi()]);
        setRoles(roleRes.rows);
        setMenuTree(tree);
        setAllMenuIds(collectAllIds(tree));
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleSelectRole = async (role: RoleVO) => {
    const detail = await getRoleByIdApi(role.roleId);
    setSelectedRole(detail);
    const leafChecked = (detail.menuIds || []).filter((id) => leafIdSet.has(id));
    setCheckedKeys(leafChecked);
    setExpandedKeys(allMenuIds);
    setIsExpanded(true);
  };

  const handleSavePermissions = async () => {
    if (!selectedRole) return;
    setSaving(true);
    try {
      const allIds = new Set(checkedKeys);
      for (const id of checkedKeys) {
        let parent = parentMap.get(id);
        while (parent !== undefined) {
          allIds.add(parent);
          parent = parentMap.get(parent);
        }
      }
      const savedIds = [...allIds].filter((id) => !disabledMenuIds.has(id));
      await assignMenusApi(selectedRole.roleId, savedIds);
      message.success('已分配权限');
      setSelectedRole((prev) => (prev ? { ...prev, menuIds: savedIds } : prev));
      loadRoles();
    } catch {
      const detail = await getRoleByIdApi(selectedRole.roleId);
      setSelectedRole(detail);
      setCheckedKeys((detail.menuIds || []).filter((id) => leafIdSet.has(id)));
    } finally {
      setSaving(false);
    }
  };

  const handleSelectAll = () => {
    setCheckedKeys(allMenuIds.filter((id) => !disabledMenuIds.has(id)));
  };

  const handleDeselectAll = () => {
    setCheckedKeys([]);
  };

  const filteredRoles = roles.filter(
    (r) => !searchValue || r.roleName.includes(searchValue) || r.roleKey.includes(searchValue),
  );

  return (
    <div
      className='flex h-full gap-4'
      style={{ height: 'calc(100vh - 100px)' }}
    >
      <Card
        className='shrink-0'
        style={{ width: 400, display: 'flex', flexDirection: 'column' }}
        styles={{ body: { flex: 1, overflow: 'auto', padding: '12px' } }}
        title={
          <div className='flex items-center justify-between'>
            <span>角色列表</span>
            <PermissionButton
              perm='system:role:add'
              type='primary'
              onClick={() => {
                setEditingRole(null);
                setModalOpen(true);
              }}
            >
              新增
            </PermissionButton>
          </div>
        }
      >
        <Input.Search
          placeholder='搜索角色'
          allowClear
          className='mb-3'
          onChange={(e) => setSearchValue(e.target.value)}
        />
        <List
          loading={loading}
          dataSource={filteredRoles}
          size='small'
          renderItem={(role) => (
            <List.Item
              key={role.roleId}
              className='mb-1 cursor-pointer border-l-4 px-3 py-2'
              style={{
                backgroundColor: selectedRole?.roleId === role.roleId ? token.colorPrimaryBg : 'transparent',
                borderLeftColor: selectedRole?.roleId === role.roleId ? token.colorPrimary : 'transparent',
              }}
              onClick={() => handleSelectRole(role)}
            >
              <div className='flex w-full items-center justify-between'>
                <div className='flex items-center gap-2'>
                  <UserOutlined
                    style={{
                      fontSize: '18px',
                      color: selectedRole?.roleId === role.roleId ? token.colorPrimary : '#999',
                    }}
                  />
                  <div>
                    <div className='text-sm font-medium'>{role.roleName}</div>
                    <div className='text-xs text-gray-400'>{role.roleKey}</div>
                  </div>
                </div>
                <Space size={6}>
                  {role.status === 1 && (
                    <Tag
                      color='error'
                      style={{ display: 'block', marginRight: '6px' }}
                    >
                      禁用
                    </Tag>
                  )}
                  {role.status === 0 && (
                    <Tag
                      color='success'
                      style={{ display: 'block', marginRight: '6px' }}
                    >
                      启用
                    </Tag>
                  )}
                  <PermissionButton
                    perm='system:role:edit'
                    variant='link'
                    type='link'
                    style={{ padding: '0px' }}
                    onClick={(e) => {
                      e.stopPropagation();
                      setEditingRole(role);
                      setModalOpen(true);
                    }}
                  >
                    编辑
                  </PermissionButton>
                  <Popconfirm
                    title='确认删除该角色？'
                    onConfirm={async (e) => {
                      e?.stopPropagation();
                      await deleteRoleApi(role.roleId);
                      message.success('已删除角色');
                      if (selectedRole?.roleId === role.roleId) {
                        setSelectedRole(null);
                      }
                      loadRoles();
                    }}
                  >
                    <PermissionButton
                      perm='system:role:delete'
                      variant='link'
                      type='link'
                      style={{ padding: '0px' }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      删除
                    </PermissionButton>
                  </Popconfirm>
                </Space>
              </div>
            </List.Item>
          )}
        />
      </Card>

      <Card
        className='flex-1'
        style={{ display: 'flex', flexDirection: 'column' }}
        styles={{ body: { flex: 1, display: 'flex', flexDirection: 'column', padding: '16px', overflow: 'hidden' } }}
        title={
          <div className='flex items-center gap-2'>
            <SafetyOutlined />
            <span>权限配置</span>
          </div>
        }
      >
        {selectedRole ? (
          <div className='flex flex-1 flex-col overflow-hidden'>
            <div className='mb-3 border-b border-gray-100 pb-3'>
              <div className='flex items-center gap-2 text-sm'>
                <span className='text-gray-500'>正在为</span>
                <span className='font-semibold text-gray-800'>{selectedRole.roleName}</span>
                <span className='text-gray-500'>分配菜单权限</span>
              </div>
              {disabledMenuIds.size > 0 && (
                <div className='mt-2 flex items-center gap-1.5 text-xs text-amber-600'>
                  <ExclamationCircleOutlined />
                  <span>存在已禁用的菜单，勾选框已置灰不可选。如需分配，请前往菜单管理启用对应菜单</span>
                </div>
              )}
            </div>
            <div className='mb-3 flex items-center gap-4'>
              <Checkbox
                checked={
                  checkedKeys.length > 0 &&
                  enabledLeafIds.size > 0 &&
                  checkedKeys.filter((id) => enabledLeafIds.has(id)).length === enabledLeafIds.size
                }
                indeterminate={
                  checkedKeys.some((id) => enabledLeafIds.has(id)) &&
                  checkedKeys.filter((id) => enabledLeafIds.has(id)).length < enabledLeafIds.size
                }
                onChange={(e) => (e.target.checked ? handleSelectAll() : handleDeselectAll())}
              >
                全选
              </Checkbox>
              <Checkbox
                checked={isExpanded}
                onChange={(e) => {
                  setIsExpanded(e.target.checked);
                  setExpandedKeys(e.target.checked ? allMenuIds : []);
                }}
              >
                <Space size={4}>
                  <span>展开</span>
                  <span>/</span>
                  <span>收起</span>
                </Space>
              </Checkbox>
              <span className='text-xs text-gray-400'>
                已选 {checkedKeys.filter((id) => enabledLeafIds.has(id)).length} / {enabledLeafIds.size} 项
              </span>
            </div>
            <div className='flex-1 overflow-auto'>
              <Tree
                checkable
                expandedKeys={expandedKeys}
                onExpand={(keys) => setExpandedKeys(keys as number[])}
                checkedKeys={checkedKeys}
                onCheck={(keys: any) => setCheckedKeys(Array.isArray(keys) ? keys : keys.checked)}
                treeData={menuTree.map((t) => convertTreeData(t, disabledMenuIds))}
              />
            </div>
            <div
              className='mt-auto flex justify-end border-t border-gray-100'
              style={{ padding: '12px 0 4px 0px' }}
            >
              <Space>
                <Button
                  onClick={() => {
                    const ids = (selectedRole.menuIds || []).filter((id) => leafIdSet.has(id));
                    setCheckedKeys(ids);
                  }}
                >
                  重置
                </Button>
                <Button
                  type='primary'
                  loading={saving}
                  onClick={handleSavePermissions}
                >
                  保存
                </Button>
              </Space>
            </div>
          </div>
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description='请在左侧选择角色以配置权限'
            className='mt-20'
          />
        )}
      </Card>

      <BaseModalForm
        title={editingRole ? '编辑角色' : '新增角色'}
        open={modalOpen}
        onOpenChange={setModalOpen}
        grid
        rowProps={{ gutter: 16 }}
        colProps={{ span: 12 }}
        initialValues={(editingRole || { status: 0, sortOrder: 0 }) as any}
        onFinish={async (values) => {
          const dto = { ...values } as any;
          if (editingRole) {
            dto.roleId = editingRole.roleId;
            await updateRoleApi(dto);
            message.success('更新成功');
          } else {
            await createRoleApi(dto);
            message.success('角色已创建，请分配权限');
          }
          loadRoles();
          return true;
        }}
      >
        <ProFormText
          name='roleName'
          label='角色名称'
          rules={[{ required: true }]}
        />
        <ProFormText
          name='roleKey'
          label='角色标识'
          rules={[{ required: true }]}
          disabled={!!editingRole}
        />
        <ProFormDigit
          name='sortOrder'
          label='排序'
          min={0}
        />
        <ProFormSelect
          name='status'
          label='状态'
          rules={[{ required: true }]}
          options={dictionary.userStatus}
        />
        <ProFormTextArea
          name='remark'
          label='备注'
          colProps={{ span: 24 }}
        />
      </BaseModalForm>
    </div>
  );
}

function convertTreeData(tree: MenuTree, disabledIds: Set<number>): any {
  return {
    key: tree.menuId,
    title: tree.menuName,
    disableCheckbox: disabledIds.has(tree.menuId),
    children: tree.children?.map((t) => convertTreeData(t, disabledIds)),
  };
}

function buildParentMap(tree: MenuTree[]): Map<number, number> {
  const map = new Map<number, number>();
  const walk = (nodes: MenuTree[], parentId?: number) => {
    for (const node of nodes) {
      if (parentId !== undefined) map.set(node.menuId, parentId);
      if (node.children?.length) walk(node.children, node.menuId);
    }
  };
  walk(tree);
  return map;
}

function collectLeafIds(tree: MenuTree[]): Set<number> {
  const ids = new Set<number>();
  const walk = (nodes: MenuTree[]) => {
    for (const node of nodes) {
      if (!node.children?.length) ids.add(node.menuId);
      else walk(node.children);
    }
  };
  walk(tree);
  return ids;
}

function collectAllIds(tree: MenuTree[]): number[] {
  const ids: number[] = [];
  const walk = (nodes: MenuTree[]) => {
    for (const node of nodes) {
      ids.push(node.menuId);
      if (node.children?.length) walk(node.children);
    }
  };
  walk(tree);
  return ids;
}

function collectDisabledIds(tree: MenuTree[]): Set<number> {
  const ids = new Set<number>();
  const walk = (nodes: MenuTree[], ancestorDisabled: boolean) => {
    for (const node of nodes) {
      const isDisabled = ancestorDisabled || node.status === 1;
      if (isDisabled) ids.add(node.menuId);
      if (node.children?.length) walk(node.children, isDisabled);
    }
  };
  walk(tree, false);
  return ids;
}
