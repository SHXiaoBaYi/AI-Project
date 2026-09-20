import { useEffect, useState } from 'react';
import { Card, Form, Input, InputNumber, Modal, Select, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import PermissionButton from '@/components/Buttons/PermissionButton';
import { getHrDepartmentsApi, getHrUsersApi, saveHrDepartmentApi } from '@/api/hr';

interface Dept {
  id: number;
  parent_id: number;
  name: string;
  leader_user_id?: number;
  sort_order?: number;
  children?: Dept[];
}

function toNodes(rows: Dept[]): DataNode[] {
  return rows.map((row) => ({
    key: row.id,
    title: row.name,
    children: toNodes(row.children || []),
  }));
}

export default function HrDepartmentPage() {
  const [tree, setTree] = useState<Dept[]>([]);
  const [open, setOpen] = useState(false);
  const [parentId, setParentId] = useState(0);
  const [users, setUsers] = useState<{ user_id: number; nickname: string }[]>([]);

  const load = () => getHrDepartmentsApi().then((rows) => setTree(rows as unknown as Dept[]));

  useEffect(() => {
    load();
    getHrUsersApi().then((list) =>
      setUsers(
        list.map((item) => ({
          user_id: Number(
            (item as { user_id?: number; userId?: number }).user_id ?? (item as { userId?: number }).userId,
          ),
          nickname: String((item as { nickname: string }).nickname),
        })),
      ),
    );
  }, []);

  return (
    <Card
      title='部门'
      extra={
        <PermissionButton
          perm='hr:dept:edit'
          type='primary'
          onClick={() => {
            setParentId(0);
            setOpen(true);
          }}
        >
          新增根部门
        </PermissionButton>
      }
    >
      <p className='mb-3 text-sm text-neutral-500'>底稿里没有部门，树先是空的。工作地上海、新疆不是部门。</p>
      <Tree
        treeData={toNodes(tree)}
        onSelect={(keys) => {
          if (keys[0] != null) setParentId(Number(keys[0]));
        }}
      />
      <PermissionButton
        perm='hr:dept:edit'
        className='mt-3'
        disabled={!parentId}
        onClick={() => setOpen(true)}
      >
        在选中部门下新增
      </PermissionButton>
      <Modal
        title='新增部门'
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        destroyOnHidden
      >
        <Form
          layout='vertical'
          onFinish={async (values: { name: string; leaderUserId?: number; sortOrder?: number }) => {
            await saveHrDepartmentApi({ ...values, parentId });
            setOpen(false);
            load();
          }}
        >
          <Form.Item
            name='name'
            label='名称'
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name='leaderUserId'
            label='负责人'
          >
            <Select
              allowClear
              options={users.map((user) => ({ value: user.user_id, label: user.nickname }))}
            />
          </Form.Item>
          <Form.Item
            name='sortOrder'
            label='排序'
            initialValue={0}
          >
            <InputNumber className='w-full' />
          </Form.Item>
          <PermissionButton
            perm='hr:dept:edit'
            type='primary'
            htmlType='submit'
          >
            保存
          </PermissionButton>
        </Form>
      </Modal>
    </Card>
  );
}
