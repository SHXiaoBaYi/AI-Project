import { useState, useEffect, useRef, useCallback, type Key, type ReactNode } from 'react';
import {
  Card,
  Tree,
  Button,
  Form,
  Input,
  Select,
  InputNumber,
  Switch,
  Space,
  Tag,
  Empty,
  App,
  Popconfirm,
  Spin,
  Tooltip,
} from 'antd';
import type { DataNode, TreeProps } from 'antd/es/tree';
import {
  PlusOutlined,
  DeleteOutlined,
  MenuOutlined,
  SettingOutlined,
  FolderOutlined,
  FileOutlined,
  TagOutlined,
  CaretDownOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import { getMenuTreeApi, getMenuListApi, createMenuApi, updateMenuApi, deleteMenuApi } from '@/api/menu';
import PermissionButton from '@/components/Buttons/PermissionButton';
import type { MenuVO, MenuTree } from '@/types/menu';
import IconPicker from '@/components/IconPicker';
import styles from './index.module.css';

const menuTypeMap: Record<string, { text: string; color: string; icon: ReactNode }> = {
  M: { text: '目录', color: 'blue', icon: <FolderOutlined /> },
  C: { text: '菜单', color: 'green', icon: <FileOutlined /> },
  F: { text: '按钮', color: 'orange', icon: <TagOutlined /> },
};

export default function MenuManage() {
  const { message } = App.useApp();
  const [menuTree, setMenuTree] = useState<MenuTree[]>([]);
  const [flatMenus, setFlatMenus] = useState<MenuVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedMenuId, setSelectedMenuId] = useState<number | null>(null);
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [searchValue, setSearchValue] = useState('');
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [isAddChild, setIsAddChild] = useState(false);
  const [showInfo, setShowInfo] = useState(true);
  const treeContainerRef = useRef<HTMLDivElement>(null);

  const menuMap = useRef(new Map<number, MenuVO>());
  const fullPathMap = useRef(new Map<number, string>());

  const buildFullPathMap = useCallback((tree: MenuTree[], parentPath = '') => {
    for (const node of tree) {
      const cleanPath = (node.path || '').replace(/^\//, '');
      const relativePath =
        parentPath && cleanPath.startsWith(parentPath + '/') ? cleanPath.slice(parentPath.length + 1) : cleanPath;
      const fullPath = parentPath ? `${parentPath}/${relativePath}` : relativePath;
      fullPathMap.current.set(node.menuId, fullPath);
      if (node.children?.length) {
        // eslint-disable-next-line react-hooks/immutability
        buildFullPathMap(node.children, fullPath);
      }
    }
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [tree, list] = await Promise.all([getMenuTreeApi(), getMenuListApi()]);
      setMenuTree(tree);
      setFlatMenus(list);
      menuMap.current = new Map(list.map((m) => [m.menuId, m]));
      fullPathMap.current.clear();
      buildFullPathMap(tree);
    } finally {
      setLoading(false);
    }
  }, [buildFullPathMap]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadData();
  }, [loadData]);

  const selectedMenu = selectedMenuId != null ? (flatMenus.find((m) => m.menuId === selectedMenuId) ?? null) : null;

  const getLeafPath = (menuId: number) => {
    const fullPath = fullPathMap.current.get(menuId) || '';
    const menu = menuMap.current.get(menuId);
    const parentPath = menu?.parentId ? fullPathMap.current.get(menu.parentId) || '' : '';
    return parentPath ? fullPath.replace(parentPath + '/', '') : fullPath;
  };

  const getParentPrefix = (menuId: number | undefined) => {
    if (!menuId) return '';
    return fullPathMap.current.get(menuId) || '';
  };

  useEffect(() => {
    if (selectedMenu && !isAddChild) {
      form.setFieldsValue({
        menuName: selectedMenu.menuName,
        menuType: selectedMenu.menuType,
        parentId: selectedMenu.parentId,
        path: getLeafPath(selectedMenu.menuId),
        component: selectedMenu.component,
        perms: selectedMenu.perms,
        icon: selectedMenu.icon,
        sortOrder: selectedMenu.sortOrder,
        status: selectedMenu.status === 0,
        remark: selectedMenu.remark,
      });
    }
  }, [selectedMenu, isAddChild, form]);

  const handleSelect = (keys: Key[]) => {
    if (keys.length === 0) return;
    const menuId = keys[0] as number;
    setSelectedMenuId(menuId);
    setIsAddChild(false);
    const menu = flatMenus.find((m) => m.menuId === menuId);
    if (menu) {
      form.setFieldsValue({
        menuName: menu.menuName,
        menuType: menu.menuType,
        parentId: menu.parentId,
        path: getLeafPath(menu.menuId),
        component: menu.component,
        perms: menu.perms,
        icon: menu.icon,
        sortOrder: menu.sortOrder,
        status: menu.status === 0,
        remark: menu.remark,
      });
    }
  };

  const getNextSortOrder = (parentId: number) => {
    const siblings = flatMenus.filter((m) => m.parentId === parentId);
    return siblings.length > 0 ? Math.max(...siblings.map((m) => m.sortOrder)) + 1 : 0;
  };

  const handleAddRoot = () => {
    setSelectedMenuId(null);
    setIsAddChild(true);
    form.resetFields();
    form.setFieldsValue({
      menuType: 'M',
      parentId: 0,
      sortOrder: getNextSortOrder(0),
      icon: 'MenuOutlined',
      status: true,
    });
  };

  const handleAddChild = (parentId: number) => {
    const menu = flatMenus.find((m) => m.menuId === parentId);
    if (!menu) return;
    setSelectedMenuId(parentId);
    setIsAddChild(true);
    form.resetFields();
    form.setFieldsValue({
      menuType: 'C',
      parentId: menu.menuId,
      sortOrder: getNextSortOrder(menu.menuId),
      icon: 'MenuOutlined',
      status: true,
    });
  };

  const handleDelete = async (menuId: number) => {
    await deleteMenuApi(menuId);
    message.success('已删除');
    if (selectedMenu?.menuId === menuId) {
      setSelectedMenuId(null);
      setIsAddChild(false);
      form.resetFields();
    }
    loadData();
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const statusValue =
        values.menuType === 'F' ? (isAddChild ? 0 : (selectedMenu?.status ?? 0)) : values.status ? 0 : 1;
      const dto = {
        ...values,
        visible: 0,
        status: statusValue,
      };
      dto.path = parentPathPrefix ? `${parentPathPrefix}/${values.path}` : values.path;

      if (isAddChild) {
        await createMenuApi(dto);
        message.success('创建成功');
      } else if (selectedMenu) {
        dto.menuId = selectedMenu.menuId;
        await updateMenuApi(dto);
        message.success('已更新');
      }
      setIsAddChild(false);
      loadData();
    } finally {
      setSaving(false);
    }
  };

  const handleDrop: TreeProps['onDrop'] = async (info) => {
    const dragId = info.dragNode.key as number;
    const dropId = info.node.key as number;
    const dropToGap = info.dropToGap;

    if (dragId === dropId) return;

    const dragMenu = flatMenus.find((m) => m.menuId === dragId);
    if (!dragMenu) return;

    let newParentId: number;
    let newSortOrder: number;

    if (dropToGap) {
      const dropMenu = flatMenus.find((m) => m.menuId === dropId);
      newParentId = dropMenu?.parentId ?? 0;
      newSortOrder = (dropMenu?.sortOrder ?? 0) + (info.dropPosition < 0 ? -1 : 1);
    } else {
      newParentId = dropId;
      newSortOrder = 0;
    }

    try {
      await updateMenuApi({
        menuId: dragId,
        menuName: dragMenu.menuName,
        menuType: dragMenu.menuType,
        parentId: newParentId,
        sortOrder: newSortOrder,
        path: dragMenu.path,
        component: dragMenu.component,
        perms: dragMenu.perms,
        icon: dragMenu.icon,
        status: dragMenu.status,
      });
      loadData();
    } catch {
      message.error('排序失败');
    }
  };

  const isEditing = selectedMenu !== null || isAddChild;

  // eslint-disable-next-line react-hooks/refs
  const parentPathPrefix = isAddChild ? getParentPrefix(selectedMenu?.menuId) : getParentPrefix(selectedMenu?.parentId);
  const formTitle = isAddChild
    ? selectedMenu
      ? `在「${selectedMenu.menuName}」下新增子菜单`
      : '新增顶级菜单'
    : selectedMenu
      ? `编辑「${selectedMenu.menuName}」`
      : '';

  return (
    <div className='flex h-[calc(100vh-100px)] flex-col gap-4'>
      {showInfo && (
        <Card styles={{ body: { padding: '12px 20px' } }}>
          <div className='flex-1'>
            <div className='flex items-center gap-x-3'>
              <div className='text-xl font-medium'>菜单管理</div>
              <Space size={8}>
                <Tag color='blue'>目录</Tag>
                <Tag color='green'>菜单</Tag>
                <Tag color='orange'>按钮</Tag>
              </Space>
              <div className='ml-auto cursor-pointer bg-gray-50 text-[#999] hover:text-[#333]'>
                <CloseOutlined
                  style={{
                    fontSize: '12px',
                    width: '24px',
                    height: '24px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                  onClick={() => setShowInfo(false)}
                />
              </div>
            </div>
            <div className='mt-4 text-xs text-[#999]'>
              <Space
                vertical
                size={6}
              >
                <span>
                  以往开发配置菜单时，经常填错序号，序号重复，不清楚前一个菜单序号，或者你填99，下一个就填999，继续就是9999...
                </span>
                <span>树形结构管理系统菜单与权限,左侧拖拽调整排序，点击节点右侧编辑信息，解决痛点！</span>
              </Space>
            </div>
          </div>
        </Card>
      )}

      <div className='flex flex-1 gap-4 overflow-hidden'>
        {/* Left: Tree */}
        <Card
          title={
            <div className='flex items-center gap-2'>
              <MenuOutlined />
              <span>菜单树</span>
            </div>
          }
          extra={
            <PermissionButton
              perm='system:menu:add'
              type='primary'
              icon={<PlusOutlined />}
              onClick={handleAddRoot}
            >
              新增顶级
            </PermissionButton>
          }
          className='flex w-110 shrink-0 flex-col'
          styles={{ body: { flex: 1, overflow: 'auto', padding: '12px' } }}
        >
          <Input.Search
            placeholder='搜索菜单'
            allowClear
            className='mb-3'
            onChange={(e) => setSearchValue(e.target.value)}
          />
          <Spin spinning={loading}>
            <div
              ref={treeContainerRef}
              className={styles.menuTree}
            >
              <Tree
                className='menu-tree'
                switcherIcon={<CaretDownOutlined style={{ fontSize: '14px' }} />}
                styles={{ itemTitle: { width: '100%' } }}
                draggable={(node) => (node as any).menuType !== 'F'}
                blockNode
                expandedKeys={expandedKeys}
                onExpand={(keys) => setExpandedKeys(keys as number[])}
                selectedKeys={selectedMenu ? [selectedMenu.menuId] : []}
                onSelect={(keys) => handleSelect(keys)}
                onDrop={handleDrop}
                // eslint-disable-next-line react-hooks/refs
                treeData={buildTreeData(menuTree, searchValue, menuMap.current)}
                titleRender={(node) => renderTreeNode(node as MenuTreeNode, selectedMenu, handleDelete, handleAddChild)}
              />
            </div>
          </Spin>
        </Card>

        <Card
          title={
            <div className='flex items-center gap-2'>
              <SettingOutlined />
              <span>{formTitle || '菜单详情'}</span>
            </div>
          }
          className='flex flex-1 flex-col'
          styles={{ body: { flex: 1, overflow: 'auto', padding: '16px 24px' } }}
        >
          {isEditing ? (
            <Form
              form={form}
              layout='horizontal'
              labelCol={{ span: 4 }}
              wrapperCol={{ span: 20 }}
              initialValues={{ menuType: 'C', sortOrder: 0, status: true }}
            >
              <Form.Item
                name='parentId'
                hidden
              >
                <Input />
              </Form.Item>

              <Form.Item
                name='menuName'
                label='菜单名称'
                rules={[{ required: true, message: '请输入菜单名称' }]}
              >
                <Input placeholder='请输入菜单名称' />
              </Form.Item>

              <Form.Item
                name='menuType'
                label='菜单类型'
                rules={[{ required: true, message: '请选择菜单类型' }]}
              >
                <Select
                  options={[
                    { value: 'M', label: '目录' },
                    { value: 'C', label: '菜单' },
                    { value: 'F', label: '按钮' },
                  ]}
                />
              </Form.Item>

              <Form.Item
                noStyle
                shouldUpdate={(prev, cur) => prev.menuType !== cur.menuType}
              >
                {({ getFieldValue }) => {
                  const type = getFieldValue('menuType');
                  return (
                    <>
                      {type !== 'F' && (
                        <>
                          <Form.Item
                            name='path'
                            label='路由路径'
                            rules={[{ required: true, message: '请输入路由地址' }]}
                          >
                            <Input
                              placeholder='例如: user'
                              addonBefore={parentPathPrefix ? parentPathPrefix + '/' : undefined}
                            />
                          </Form.Item>
                          {/* 已改为vite自动扫描目录加载组件 */}
                          {/* <Form.Item
                            name='component'
                            label='组件路径'
                          >
                            <Input placeholder='例如: system/user/index' />
                          </Form.Item> */}
                          {type === 'M' && (
                            <Form.Item
                              name='icon'
                              label='图标'
                            >
                              <IconPicker style={{ fontSize: '14px' }} />
                            </Form.Item>
                          )}
                          <Form.Item
                            name='status'
                            label='状态'
                            valuePropName='checked'
                          >
                            <Switch
                              checkedChildren='启用'
                              unCheckedChildren='禁用'
                            />
                          </Form.Item>
                        </>
                      )}
                      {type == 'F' && (
                        <Form.Item
                          name='perms'
                          label='权限标识'
                        >
                          <Input placeholder='例如: system:user:list' />
                        </Form.Item>
                      )}
                    </>
                  );
                }}
              </Form.Item>

              <Form.Item
                name='sortOrder'
                label='排序'
              >
                <InputNumber
                  min={0}
                  className='w-full'
                  disabled
                />
              </Form.Item>

              <Form.Item
                name='remark'
                label='备注'
              >
                <Input.TextArea
                  rows={2}
                  placeholder='备注信息'
                />
              </Form.Item>

              <div className='border-t border-gray-200 pt-3'>
                <div className='flex justify-end gap-2'>
                  <Button
                    onClick={() => {
                      setIsAddChild(false);
                      form.resetFields();
                      if (!selectedMenu) setSelectedMenuId(null);
                    }}
                  >
                    取消
                  </Button>
                  <PermissionButton
                    perm={isAddChild ? 'system:menu:add' : 'system:menu:edit'}
                    type='primary'
                    loading={saving}
                    onClick={handleSave}
                  >
                    {isAddChild ? '创建' : '保存'}
                  </PermissionButton>
                </div>
              </div>
            </Form>
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description='请在左侧选择菜单进行编辑，或点击「新增顶级」创建菜单'
              className='mt-20'
            />
          )}
        </Card>
      </div>
    </div>
  );
}

interface MenuTreeNode {
  key: number;
  title: string;
  menuType: 'M' | 'C' | 'F';
  status: number;
  children?: MenuTreeNode[];
}

function buildTreeData(tree: MenuTree[], searchValue: string, menuMap: Map<number, MenuVO>): DataNode[] {
  return tree
    .map((node) => {
      const children = node.children?.length ? buildTreeData(node.children, searchValue, menuMap) : undefined;
      const matchSearch =
        !searchValue || node.menuName.toLowerCase().includes(searchValue.toLowerCase()) || children?.length;
      if (!matchSearch) return null;
      const full = menuMap.get(node.menuId);
      return {
        key: node.menuId,
        title: node.menuName,
        menuType: full?.menuType || 'M',
        status: full?.status ?? 0,
        children,
      } as unknown as DataNode;
    })
    .filter(Boolean) as DataNode[];
}

function renderTreeNode(
  node: MenuTreeNode,
  selectedMenu: MenuVO | null,
  onDelete: (id: number) => void,
  onAddChild: (id: number) => void,
) {
  const typeInfo = menuTypeMap[node.menuType] || menuTypeMap.M;
  const isSelected = selectedMenu?.menuId === node.key;
  const isDisabled = node.status === 1;

  return (
    <div className={`flex w-full items-center justify-between py-px ${isDisabled ? styles.disabledNode : ''}`}>
      <div className='flex min-w-0 items-center gap-2'>
        <span
          className='flex shrink-0 items-center justify-center'
          style={{ color: typeInfo.color, fontSize: '1.5em', width: '1.5em', height: '1.5em' }}
        >
          {typeInfo.icon}
        </span>
        <span
          className={`truncate ${isSelected ? 'font-medium' : 'font-normal'} ${isDisabled ? styles.nodeTitle : ''}`}
        >
          {node.title}
        </span>
        {isDisabled && (
          <Tag
            color='error'
            className={styles.disabledTag}
          >
            禁用
          </Tag>
        )}
      </div>
      <Space size={0}>
        {node.menuType !== 'F' && (
          <Tooltip title='新增子菜单'>
            <PermissionButton
              perm='system:menu:add'
              type='text'
              size='small'
              icon={<PlusOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                onAddChild(node.key);
              }}
            />
          </Tooltip>
        )}
        <Popconfirm
          title='确认删除该菜单？'
          onConfirm={(e) => {
            e?.stopPropagation();
            onDelete(node.key);
          }}
          onCancel={(e) => e?.stopPropagation()}
        >
          <Tooltip title='删除'>
            <PermissionButton
              perm='system:menu:delete'
              type='text'
              size='small'
              danger
              icon={<DeleteOutlined />}
              onClick={(e) => e.stopPropagation()}
            />
          </Tooltip>
        </Popconfirm>
      </Space>
    </div>
  );
}
