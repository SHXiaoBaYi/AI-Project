# 前端说明文档

基于 React 19 + Ant Design 6 + Vite 8 的后台管理系统前端，搭配 Spring Boot 后端实现开箱即用的 RBAC 权限管理。

---

## 技术栈

| 技术 | 版本 | 说明 |
|---|---|---|
| React | 19.x | UI 框架，函数组件 + Hooks |
| TypeScript | 6.x | 类型系统，严格模式，target ES2023 |
| Vite | 8.x | 开发服务器与构建工具，毫秒级冷启动 |
| Ant Design | 6.x | 企业级组件库，含 ProComponents 系列 |
| Redux Toolkit | 2.x | 全局状态管理，内置 immer |
| React Router | 7.x | 路由，通过 `createBrowserRouter` 动态构建 |
| Tailwind CSS | 4.x | 原子化 CSS，通过 `@tailwindcss/vite` 插件集成 |
| Axios | 1.x | HTTP 客户端，统一拦截器处理 token 和错误 |
| ESLint | 10.x | 代码检查，React Hooks + TypeScript 规则 |

---

## 代码规范

多人协作项目中，统一的代码风格不是锦上添花，而是代码可维护性的底线。没有强制规范的团队会面临三个典型问题：

- **无效的 Code Review** — 每次 PR 都混杂大量格式争议，真正的逻辑问题被噪音淹没
- **Git 历史污染** — 不同编辑器/开发者的缩进和引号偏好反复横跳，`git blame` 失去追溯价值
- **合并冲突** — 格式化差异导致的冲突本可完全避免

本项目通过 **ESLint + Prettier** 的组合来消除这些问题：

- **Prettier** 负责代码格式（缩进、引号、换行、分号），属于"怎么写的"——规则确定，没有争议空间
- **ESLint** 负责代码质量（未使用变量、Hooks 依赖检查、类型安全），属于"写得对不对"——发现潜在 bug

### Prettier 配置

```json
{
  "tabWidth": 2,
  "printWidth": 120,
  "singleQuote": true,
  "jsxSingleQuote": true,
  "singleAttributePerLine": true,
  "semi": true
}
```

所有格式争议由工具裁决，不接受讨论。`singleAttributePerLine: true` 让 JSX 属性过多时自动每行一个，提高可读性。

### ESLint 配置

启用了四组规则集：

| 规则集 | 作用 |
|---|---|
| `@eslint/js` recommended | JavaScript 基础最佳实践 |
| `typescript-eslint` recommended | TypeScript 类型安全规则 |
| `react-hooks` recommended | Hooks 依赖检查，防止闭包陷阱 |
| `react-refresh` | 确保组件导出符合 Fast Refresh 要求 |

关键覆盖：未使用变量仅 `warn`（不阻塞编译），`no-explicit-any` 关闭（业务开发中过度使用 `unknown` 会降低效率），`no-undef` 为 `error`。

### 必装插件

项目 `.vscode/extensions.json` 中配置了推荐插件，**团队成员必须在 VS Code 中安装以下三个插件**，否则配置好的自动格式化不会生效：

| 插件 | ID | 作用 |
|---|---|---|
| **Prettier - Code formatter** | `esbenp.prettier-vscode` | 保存时自动格式化代码 |
| **ESLint** | `dbaeumer.vscode-eslint` | 保存时自动修复 ESLint 问题 |
| **Tailwind CSS IntelliSense** | `bradlc.vscode-tailwindcss` | Tailwind 类名自动补全与提示 |

打开项目时 VS Code 会弹出推荐提示，点"安装"即可。如果错过了，可以在扩展面板搜索 `@recommended` 查看并一键安装。

### 保存时自动处理

项目 `.vscode/settings.json` 配置了以下自动化行为，**无需手动执行任何命令**：

```
保存文件 → Prettier 格式化 → ESLint 自动修复 → 写入磁盘
```

这套流程确保所有提交到仓库的代码都经过统一格式化，任何人不按规范写的代码在保存瞬间就会被纠正。

### 手动检查

CI 或提交前可手动运行：

```bash
pnpm lint         # 检查所有 src 下的 .ts/.tsx 文件
pnpm lint:fix     # 自动修复可修复的问题
```

---

## 架构设计

### 启动流程与数据流

```
main.tsx
  → Redux Provider + App 挂载
    → App.tsx 检查 token
      ├─ 未登录 → 仅渲染 /login 路由
      └─ 已登录 → dispatch(getInfo())
                    → /auth/info 返回 { 用户信息, 权限列表, 菜单树 }
                    → menuSlice: routesLoaded = true
                    → userSlice: 写入 permissions[], userInfo
                    → 根据菜单树 createAppRouter()
                    → ProLayout 渲染侧边栏 + <Outlet/>
```

### 动态路由

整个路由表由后端菜单树动态生成，核心机制：

1. **`componentMap.ts`** — 通过 `import.meta.glob('@/pages/**/index.tsx')` 自动扫描所有页面组件，以路径为 key（如 `/system/user`）建立映射
2. **`router/index.tsx`** — `createAppRouter(menus, isLoggedIn)` 递归遍历菜单树，将菜单的 `path` 与 `componentMap` 匹配，生成带 `lazy()` 的路由配置
3. **页面占位** — 菜单存在但对应页面文件尚未创建时，自动渲染"页面开发中"提示，不影响其他菜单使用

新增页面只需在 `src/pages/` 对应路径下创建 `index.tsx`，无需修改路由配置。

### 权限控制

前后端双重校验，前端负责 UI 显隐：

- **登录时** — 后端返回 `permissions: string[]`（如 `['system:user:list', 'system:user:add', ...]`），含 `*:*:*` 表示超级管理员
- **按钮级** — `PermissionButton` 接收 `perm` 属性，`usePermission().has(perm)` 返回 false 时渲染 `null`
- **操作列** — `ActionButtons` 批量过滤，主要操作直接展示（默认 3 个），其余收入溢出下拉菜单
- **页面级** — 菜单树由后端按用户权限过滤后返回，无权限的菜单根本不会出现在路由中

### 状态管理

Redux Toolkit 三个切片，职责分明：

| 切片 | 存储内容 | 关键操作 |
|---|---|---|
| `userSlice` | token, userInfo, permissions[], menus[] | `login`（登录获取 token）、`getInfo`（获取用户信息与权限）、`logout`（清空并跳转登录页） |
| `menuSlice` | menus[], routesLoaded | 监听 `getInfo.fulfilled` 自动更新菜单树并标记路由就绪 |
| `themeSlice` | ConfigProvider theme 配置 | `setColorPrimary` 切换主题色，`setThemeConfig` 完整覆盖 |

`menuSlice` 与 `userSlice` 的解耦设计：菜单数据由 `getInfo` thunk 一次性返回，但通过 `extraReducers` 分写入两个 slice，避免数据重复且保持职责清晰。

### API 层

`api/request.ts` 创建的 Axios 实例封装了所有通用逻辑：

- **请求拦截器** — 自动从 localStorage 读取 token，注入 `Authorization: Bearer <token>` 请求头
- **响应拦截器** — 解包 `ApiResult<T>` 统一响应体，`code !== 200` 时弹出错误提示；401/403 时清除 token 并跳转登录页
- **业务 API** — 每个模块一个文件（`auth.ts`、`user.ts`、`role.ts`、`menu.ts`），函数命名遵循 `xxxApi` 约定

### 布局

`layouts/index.tsx` 使用 ProLayout 的 `mix` 布局模式：

- 侧边栏从 Redux 中的菜单树渲染，图标通过 `iconMap.ts` 将字符串映射为 Ant Design 图标组件
- 顶栏右侧为头像下拉菜单（退出登录），底部为侧边栏折叠按钮
- 页面内容通过 React Router 的 `<Outlet />` 渲染在 `PageContainer` 内

### 样式策略

三层分工，互不冲突：

| 层级 | 方案 | 用途 |
|---|---|---|
| 全局 | `index.css` + `tailwind.css` | CSS 变量、基础排版、Tailwind 入口 |
| 组件 | CSS Modules (`*.module.css`) | 组件级样式隔离，类名自动哈希 |
| 主题 | Ant Design ConfigProvider | 通过 Redux `themeSlice` 动态切换主色、圆角等 Design Token |

`tailwind.css` 中通过 `@theme` 指令将 Ant Design 的 CSS 变量映射为 Tailwind 工具类（如 `--ant-color-primary` → `text-primary`），使两套体系可以混用。

---

## 核心组件

| 组件 | 说明 |
|---|---|
| `BaseProTable` | ProTable 封装，默认关闭工具栏选项、开启横向滚动 |
| `BaseModalForm` | ModalForm 封装，统一弹窗样式（分隔线分区 header/body/footer） |
| `PermissionButton` | 带权限检查的按钮，无权限时返回 null |
| `ActionButtons` | 操作列组件，主要按钮直接展示，超出的收入下拉菜单，支持确认弹窗 |
| `IconPicker` | 图标选择器，6 列网格展示 140+ Ant Design 图标，支持搜索 |
| `IconStatus` | 字典状态徽章，根据字典配置渲染带颜色和图标的标签 |

---

## 字典系统

集中管理枚举值的前端展示，位于 `src/dictionary/`：

- 每个文件导出一个 `OptionItemType[]` 数组，包含 `label`、`value`、`color`、`iconfont` 字段
- `index.ts` 聚合所有字典，key 为字典名（如 `userStatus`、`gender`、`choose`）
- `IconStatus` 组件根据字典 key 和 value 渲染对应样式

新增字典只需在 `dictionary/` 下创建文件并在 `index.ts` 中注册。

---

## 页面清单

| 路径 | 页面 | 说明 |
|---|---|---|
| `/login` | 登录页 | 渐变背景 + 登录表单，默认账号 admin/admin123 |
| `/workbench` | 工作台 | 统计卡片、趋势图表、库存预警、待办、快捷入口 |
| `/system/user` | 用户管理 | ProTable 分页列表 + ModalForm 新增/编辑 + 分配角色 + 重置密码 |
| `/system/role` | 角色管理 | 左右分栏：左侧角色列表，右侧菜单权限树配置 |
| `/system/menu` | 菜单管理 | 可拖拽排序的菜单树 + 右侧详情/编辑表单 + 图标选择器 |
| `/form/base` | 基础表单 | antd Form 示例 |
| `/form/pro` | 高级表单 | ProForm + ProFormList + ProFormMoney 示例 |
| `/form/query` | 查询表单 | QueryFilter 搜索表单示例 |
| `/form/schema` | JSON 表单 | BetaSchemaForm 配置化表单示例 |
| `/table/base` | 基础表格 | antd Table 示例 |
| `/table/pro` | 高级表格 | ProTable 示例 |
| `/table/drag-sort` | 拖拽排序 | 可拖拽排序表格示例 |
| `/table/edit-table` | 可编辑表格 | 行内编辑表格示例 |
| `/list/base` | 基础列表 | antd List 示例 |
| `/list/pro` | 高级列表 | ProList 示例 |
| `/description/base` | 基础描述 | antd Descriptions 示例 |
| `/description/pro` | 高级描述 | ProDescriptions 示例 |

---

## 开发

```bash
pnpm dev          # 启动开发服务器（:3000），代理 /api → localhost:8080
pnpm build        # tsc 类型检查 + vite 生产构建
pnpm preview      # 预览构建产物
pnpm lint         # ESLint 检查
pnpm lint:fix     # ESLint 自动修复
```

### 环境变量

| 文件 | 说明 |
|---|---|
| `.env.development` | `VITE_API_URL=http://localhost:8080` |
| `.env.production` | `VITE_API_URL=https://api.prod.example.com` |

### 路径别名

`@/` → `src/`，在 `vite.config.ts` 和 `tsconfig.app.json` 中配置。
