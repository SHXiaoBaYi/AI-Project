import { Fragment, isValidElement, type ReactElement, type ReactNode } from 'react';
import type { ProColumns } from '@ant-design/pro-components';
import ActionButtons from '@/components/Buttons/ActionButtons';
import PermissionButton from '@/components/Buttons/PermissionButton';

type PermChecker = (perm: string) => boolean;

/** 探测用假行：尽量走进常见 status 分支，便于收集操作按钮上的 perm */
function makeProbeRecord<T extends Record<string, any>>(): T {
  return new Proxy(
    { id: 1, status: 'OPEN', currentStage: 'FIRST_PASS' },
    {
      get(target, prop, receiver) {
        if (prop === 'then') return undefined;
        if (typeof prop === 'symbol') return undefined;
        if (prop in target) return Reflect.get(target, prop, receiver);
        return 1;
      },
    },
  ) as unknown as T;
}

function nodeMayShowAction(node: ReactNode, has: PermChecker): boolean {
  if (node == null || node === false || node === true) return false;
  if (typeof node === 'string' || typeof node === 'number') return String(node).trim().length > 0;
  if (Array.isArray(node)) return node.some((n) => nodeMayShowAction(n, has));
  if (!isValidElement(node)) return false;

  const el = node as ReactElement<{ items?: Array<{ perm?: string }>; perm?: string; children?: ReactNode }>;
  const type = el.type;

  if (type === ActionButtons) {
    const items = el.props.items ?? [];
    return items.some((item) => !item.perm || has(item.perm));
  }
  if (type === PermissionButton) {
    const perm = el.props.perm;
    if (perm && !has(perm)) return false;
    return nodeMayShowAction(el.props.children, has);
  }
  if (type === Fragment) {
    return nodeMayShowAction(el.props.children, has);
  }

  if (el.props.children !== undefined) {
    return nodeMayShowAction(el.props.children, has);
  }

  // 无法识别的自定义组件（且无 children）：保守显示，避免误藏有操作的列
  if (typeof type === 'function' || (typeof type === 'object' && type !== null)) {
    return true;
  }
  return false;
}

/**
 * 当前用户是否可能看到该操作列的内容。
 * - 优先读列上的 actionPerms（显式声明）
 * - 否则对 render 做一次轻量探测（识别 ActionButtons / PermissionButton）
 */
export function isActionColumnVisibleForUser<T extends Record<string, any>>(
  col: ProColumns<T>,
  has: PermChecker,
): boolean {
  const explicit = (col as ProColumns<T> & { actionPerms?: string | string[] }).actionPerms;
  if (explicit != null) {
    const list = Array.isArray(explicit) ? explicit : [explicit];
    if (list.length === 0) return true;
    return list.some((p) => has(p));
  }

  if (typeof col.render !== 'function') {
    return true;
  }

  try {
    const rendered = col.render(
      '' as never,
      makeProbeRecord<T>(),
      0,
      // ProRender 多余参数：探测阶段不使用
      {} as never,
      col as never,
    ) as ReactNode;
    return nodeMayShowAction(rendered, has);
  } catch {
    // render 依赖真实数据抛错时不隐藏，避免误伤
    return true;
  }
}
