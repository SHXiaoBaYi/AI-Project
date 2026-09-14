import { lazy, type LazyExoticComponent, type ComponentType } from 'react';

// 自动扫描 src/pages/ 下所有 index.tsx 页面组件
const modules = import.meta.glob<{ default: ComponentType }>('@/pages/**/index.tsx');

// 从文件路径推导组件 key
// '/src/pages/system/user/index.tsx' → '/system/user'
function buildAutoMap(): Record<string, LazyExoticComponent<any>> {
  const map: Record<string, LazyExoticComponent<any>> = {};
  for (const [filePath, importFn] of Object.entries(modules)) {
    const key = filePath.replace(/^\/src\/pages/, '').replace(/\/index\.tsx$/, '');
    map[key] = lazy(importFn);
  }
  return map;
}

const autoMap = buildAutoMap();

/**
 * 组件映射Map
 * 由 import.meta.glob 自动扫描 src/pages/ 目录生成，key 格式为 "/form/base"，
 * 与路由 fullPath 前加 "/" 对应。
 * 仅当目录结构与菜单 path 不一致时，在下方手动添加映射即可。
 */
const componentMap: Record<string, LazyExoticComponent<any>> = {
  ...autoMap,
  // '/view/test': lazy(() => import('@/pages/test/demo')),// 这里自定义路由，前提是当前用户有该路由的访问权限
};

export default componentMap;
