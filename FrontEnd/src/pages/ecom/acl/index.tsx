import { memo, useCallback, useEffect, useMemo, useState } from 'react';
import { App, Button, Card, Input, List, Switch, Tree } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { listEcomAclApi, listEcomAclUsersApi, listEcomShopsApi, removeEcomAclApi, saveEcomAclApi } from '@/api/ecom';

const PLATFORM_META = [
  { value: 'jd', label: '京东' },
  { value: 'tmall', label: '天猫' },
  { value: 'douyin', label: '抖音' },
];

function platformKey(p: string) {
  return `p:${p}`;
}
function shopKey(id: number) {
  return `s:${id}`;
}
function parsePlatformKey(k: string) {
  return k.startsWith('p:') ? k.slice(2) : null;
}
function parseShopKey(k: string) {
  return k.startsWith('s:') ? Number(k.slice(2)) : null;
}

type AclRow = Record<string, unknown>;
type UserRow = Record<string, unknown>;

const AclPage = memo(function AclPage() {
  const { message, modal } = App.useApp();
  const [aclRows, setAclRows] = useState<AclRow[]>([]);
  const [candidates, setCandidates] = useState<UserRow[]>([]);
  const [shops, setShops] = useState<Record<string, unknown>[]>([]);
  const [keyword, setKeyword] = useState('');
  const [selectedUserId, setSelectedUserId] = useState<number | undefined>();
  const [checkedKeys, setCheckedKeys] = useState<string[]>([]);
  const [enabled, setEnabled] = useState(true);
  const [remark, setRemark] = useState('');
  const [saving, setSaving] = useState(false);

  const reloadAcl = useCallback(async () => {
    const rows = (await listEcomAclApi()) ?? [];
    setAclRows(rows);
    return rows;
  }, []);

  useEffect(() => {
    void reloadAcl();
    void listEcomShopsApi().then((list) => setShops(list ?? []));
  }, [reloadAcl]);

  useEffect(() => {
    void listEcomAclUsersApi(keyword || undefined).then((list) => setCandidates(list ?? []));
  }, [keyword]);

  const treeData: DataNode[] = useMemo(() => {
    return PLATFORM_META.map((p) => ({
      key: platformKey(p.value),
      title: p.label,
      children: shops
        .filter((s) => String(s.platform) === p.value)
        .map((s) => ({
          key: shopKey(Number(s.id)),
          title: String(s.shop_name || s.shop_code || s.id),
          isLeaf: true,
        })),
    }));
  }, [shops]);

  const applyRowToRight = useCallback(
    (row: AclRow | undefined) => {
      if (!row) {
        setCheckedKeys([]);
        setEnabled(true);
        setRemark('');
        return;
      }
      setEnabled(Number(row.enabled) === 1);
      setRemark(String(row.remark || ''));
      const allP = Number(row.all_platforms) === 1;
      const allS = Number(row.all_shops) === 1;
      const platforms = (row.platforms as string[]) || [];
      const shopIds = (row.shopIds as number[]) || [];
      const keys = new Set<string>();
      if (allP && allS) {
        PLATFORM_META.forEach((p) => keys.add(platformKey(p.value)));
        shops.forEach((s) => keys.add(shopKey(Number(s.id))));
      } else {
        const plats = allP ? PLATFORM_META.map((p) => p.value) : platforms;
        plats.forEach((p) => keys.add(platformKey(p)));
        if (allS) {
          shops.filter((s) => plats.includes(String(s.platform))).forEach((s) => keys.add(shopKey(Number(s.id))));
        } else {
          shopIds.forEach((id) => keys.add(shopKey(id)));
        }
      }
      setCheckedKeys([...keys]);
    },
    [shops],
  );

  useEffect(() => {
    if (selectedUserId == null) return;
    const row = aclRows.find((r) => Number(r.user_id) === selectedUserId);
    applyRowToRight(row);
  }, [selectedUserId, aclRows, applyRowToRight]);

  const leftUsers = useMemo(() => {
    const aclById = new Map(aclRows.map((r) => [Number(r.user_id), r]));
    // 系统全部用户；已授权的排在前面
    return [...candidates]
      .map((u) => {
        const id = Number(u.user_id);
        const acl = aclById.get(id);
        return {
          user_id: id,
          username: String(u.username || ''),
          nickname: String(u.nickname || ''),
          enabled: acl ? Number(acl.enabled) === 1 : false,
          authorized: !!acl,
        };
      })
      .sort((a, b) => {
        if (a.authorized !== b.authorized) return a.authorized ? -1 : 1;
        return a.user_id - b.user_id;
      });
  }, [aclRows, candidates]);

  const deriveScope = (keys: string[]) => {
    const platforms = new Set<string>();
    const shopIds = new Set<number>();
    keys.forEach((k) => {
      const p = parsePlatformKey(k);
      if (p) platforms.add(p);
      const sid = parseShopKey(k);
      if (sid != null && !Number.isNaN(sid)) shopIds.add(sid);
    });
    shopIds.forEach((id) => {
      const shop = shops.find((s) => Number(s.id) === id);
      if (shop) platforms.add(String(shop.platform));
    });
    const allPlatforms = PLATFORM_META.every((p) => platforms.has(p.value));
    const shopsInScope = shops.filter((s) => allPlatforms || platforms.has(String(s.platform)));
    const allShops = shopsInScope.length > 0 && shopsInScope.every((s) => shopIds.has(Number(s.id)));
    return {
      allPlatforms,
      allShops,
      platforms: allPlatforms ? [] : [...platforms],
      shopIds: allShops ? [] : [...shopIds],
    };
  };

  const onSave = async () => {
    if (selectedUserId == null) return;
    const scope = deriveScope(checkedKeys);
    if (enabled && !scope.allPlatforms && scope.platforms.length === 0) {
      message.warning('请勾选至少一个平台或店铺');
      return;
    }
    if (enabled && !scope.allShops && scope.shopIds.length === 0) {
      message.warning('请勾选至少一个店铺，或勾选平台下全部店铺');
      return;
    }
    setSaving(true);
    try {
      await saveEcomAclApi({
        userId: selectedUserId,
        enabled,
        allPlatforms: scope.allPlatforms,
        allShops: scope.allShops,
        platforms: scope.platforms,
        shopIds: scope.shopIds,
        remark,
      });
      message.success('已保存授权');
      const rows = await reloadAcl();
      if (!rows.some((r) => Number(r.user_id) === selectedUserId) && !enabled) {
        setSelectedUserId(undefined);
      }
    } finally {
      setSaving(false);
    }
  };

  const onRemove = () => {
    if (selectedUserId == null) return;
    modal.confirm({
      title: '移除该用户授权？',
      onOk: async () => {
        await removeEcomAclApi(selectedUserId);
        message.success('已移除');
        setSelectedUserId(undefined);
        await reloadAcl();
      },
    });
  };

  const selected = leftUsers.find((u) => u.user_id === selectedUserId);

  return (
    <div className='flex min-h-[560px] gap-4'>
      <Card
        title='用户'
        size='small'
        className='w-72 shrink-0'
        styles={{ body: { padding: 12 } }}
      >
        <Input
          className='mb-3'
          placeholder='搜索用户名/昵称'
          value={keyword}
          allowClear
          onChange={(e) => setKeyword(e.target.value)}
        />
        <List
          size='small'
          className='max-h-[480px] overflow-y-auto'
          dataSource={leftUsers}
          locale={{ emptyText: '无匹配用户' }}
          renderItem={(u) => {
            const active = u.user_id === selectedUserId;
            return (
              <List.Item
                className={
                  active
                    ? 'cursor-pointer rounded bg-[var(--ant-color-primary-bg)] px-2'
                    : 'cursor-pointer rounded px-2 hover:bg-neutral-50'
                }
                onClick={() => setSelectedUserId(u.user_id)}
              >
                <div className='min-w-0'>
                  <div className='truncate text-sm font-medium text-neutral-800'>
                    {u.nickname || u.username}
                    {u.authorized ? (
                      <span className='ml-1 text-xs font-normal text-[var(--ant-color-primary)]'>已授权</span>
                    ) : null}
                  </div>
                  <div className='truncate text-xs text-neutral-400'>{u.username}</div>
                </div>
              </List.Item>
            );
          }}
        />
      </Card>

      <Card
        title={selected ? `可见范围 · ${selected.nickname || selected.username}` : '可见范围'}
        size='small'
        className='min-w-0 flex-1'
        extra={
          selected ? (
            <div className='flex items-center gap-2'>
              <span className='text-sm text-neutral-500'>启用</span>
              <Switch
                checked={enabled}
                onChange={setEnabled}
              />
              {selected.authorized ? (
                <Button
                  danger
                  onClick={onRemove}
                >
                  移除
                </Button>
              ) : null}
              <Button
                type='primary'
                loading={saving}
                onClick={() => void onSave()}
              >
                保存
              </Button>
            </div>
          ) : null
        }
      >
        {!selected ? (
          <div className='py-16 text-center text-sm text-neutral-400'>请从左侧选择用户，勾选可访问的平台与店铺</div>
        ) : (
          <div className='space-y-3'>
            <Input
              placeholder='备注（可选）'
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              allowClear
            />
            <p className='m-0 text-sm text-neutral-500'>勾选平台与下属店铺；勾满全部平台/店铺时视为全量授权。</p>
            <Tree
              checkable
              defaultExpandAll
              checkedKeys={checkedKeys}
              treeData={treeData}
              onCheck={(keys) => {
                const list = Array.isArray(keys) ? keys : keys.checked;
                setCheckedKeys(list.map(String));
              }}
            />
          </div>
        )}
      </Card>
    </div>
  );
});

export default AclPage;
