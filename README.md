# SpringBoot-React-Antd-Template

这是一个全栈项目，基于 Spring Boot 3.4.5 + React 19 + Ant Design 6.x + ProComponents3.X 构建的后台管理模板。提供完整的用户认证、权限管理、角色管理、菜单管理、Dashboard 等基础功能，开箱即用，适合快速启动企业级中后台项目。

---

## 预览

**在线地址**：[RBAC权限管理系统](http://8.137.207.183/)

| 账号    | 密码       |
| ------- | ---------- |
| `admin` | `admin123` |

站点`preview`分支已启用**演示模式**（`demo.enabled: true`），在此模式下：

- **用户管理** — 超级管理员账号（admin）不允许编辑、删除、重置密码、分配角色；新建用户时不允许分配超级管理员角色
- **角色管理** — 超级管理员角色不允许编辑、删除、修改权限配置
- **菜单管理** — 系统初始菜单受保护，不允许编辑和删除，仅支持新增菜单后对新菜单进行操作
- 所有接口层均有后端保护，前端仅为 UI 层面的便利性限制

> 部署到自有环境时，将 `application.yml` 中 `demo.enabled` 设为 `false` 即可关闭演示模式，所有操作恢复正常。

---

## 页面截图

**登录**

![登录](./演示截图/登录.png)

**工作台**

![工作台](./演示截图/工作台.png)

**用户管理**

![用户管理](./演示截图/用户管理.png)

**角色管理**

![角色管理](./演示截图/角色管理.png)

**菜单管理**

![菜单管理](./演示截图/菜单管理.png)

## 技术选型

### 后端

| 技术            | 版本   | 选型理由                                                                                             |
| --------------- | ------ | ---------------------------------------------------------------------------------------------------- |
| Spring Boot     | 3.4.x  | Java 生态事实标准，3.x 基于 Jakarta EE 9，最低要求 Java 17，本项目锁定 Java 21（当前 LTS）           |
| Spring Security | 6.x    | 与 Spring Boot 深度集成，提供完整的认证/授权过滤器链，本项目采用无状态 JWT 方案                      |
| MyBatis-Plus    | 3.5.x  | MyBatis 增强工具，零 SQL 的 CRUD、分页插件、自动填充、逻辑删除，比 JPA 更灵活，比原生 MyBatis 更高效 |
| MySQL           | 8.x    | 关系型数据存储，选型默认选项                                                                         |
| jjwt            | 0.12.x | JWT 标准库，HMAC-SHA 签名，无状态认证的基础                                                          |

核心思路：用 Spring Security 的过滤器链拦截请求，JWT 承载用户身份，MyBatis-Plus 提供数据访问层，三者组合成无状态、可水平扩展的认证体系。

### 前端

| 技术          | 版本 | 选型理由                                                                   |
| ------------- | ---- | -------------------------------------------------------------------------- |
| React         | 19.x | UI 框架，函数组件 + Hooks 已是社区共识范式                                 |
| TypeScript    | 6.x  | 类型安全，配合 TS 严格模式在编译期拦截大量低级错误                         |
| Vite          | 8.x  | 开发服务器毫秒级冷启动，基于 ESM 的 HMR，替代 Webpack 的下一代构建工具     |
| Ant Design    | 6.x  | 企业级中后台组件库，ProComponents 系列进一步封装了表格/表单/布局的常见模式 |
| Redux Toolkit | 2.x  | 全局状态管理，比裸 Redux 减少样板代码，内置 immer 不可变更新               |
| Tailwind CSS  | 4.x  | 原子化 CSS，通过 Vite 插件集成，与 Ant Design 的 Design Token 体系互补     |
| pnpm          | —    | 磁盘空间友好，幽灵依赖问题远小于 npm/yarn                                  |

前端详细说明见 [FrontEnd/README.md](FrontEnd/README.md)。

---

## 架构设计

### 整体分层

```
┌──────────────────────────────────────────┐
│               React SPA                  │
│  ProLayout → Pages → Components          │
│  Redux Store → Axios → /api proxy       │
└──────────────┬───────────────────────────┘
               │ HTTP (Bearer JWT)
┌──────────────▼───────────────────────────┐
│          Spring Boot /api                │
│  Security Filter → Controller           │
│       → Service → Mapper → MySQL        │
└──────────────────────────────────────────┘
```

### 后端分层

采用 Controller → Service → Mapper 三层架构：

- **Controller** — 接收请求，参数校验（`@Valid`），调用 Service，返回统一响应体 `Result<T>`
- **Service** — 业务逻辑，事务管理，权限注解（`@RequiresPermission`）
- **Mapper** — MyBatis-Plus BaseMapper，多数 CRUD 无需手写 SQL
- **Security 横切层** — `JwtAuthenticationFilter` 在所有 Controller 之前解析 token，将用户权限注入 SecurityContext

全局异常通过 `GlobalExceptionHandler` 统一兜底，AOP 切面处理操作日志（`@Log` 注解）。

### 前端分层

- **Pages** — 页面级组件，组合业务逻辑
- **Components** — 可复用组件（BaseProTable、BaseModalForm、PermissionButton 等）
- **Store (Redux)** — 全局状态：用户信息、权限、菜单树、主题
- **API 层** — Axios 实例，统一注入 token、拦截 401/403
- **Router** — 从服务端菜单树动态生成路由表，`import.meta.glob` 自动扫描页面组件

### 认证流程

```
Login → POST /auth/login → 返回 JWT
       → 存入 localStorage
       → 每次请求 Axios 拦截器自动带 Authorization: Bearer <token>
       → JwtAuthenticationFilter 解析 → 注入 SecurityContext
       → @RequiresPermission 检查权限字符串
       → 前端 PermissionButton 根据 permissions[] 控制 UI 显隐
```

### RBAC 权限模型

用户 N:N 角色，角色 N:N 菜单/按钮。五张核心表：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`。

- **菜单类型**：目录（M）、菜单（C）、按钮（F），按钮级权限控制到接口和 UI 元素
- **权限标识**：`system:user:list` 格式，后端注解 + 前端组件双重校验
- **管理员特权**：`admin` 角色键自动获得 `*:*:*` 通配权限

---

## 快速开始

### 环境要求

- JDK 21
- Maven 3.8+
- MySQL 8.0+
- Node.js 20+
- pnpm（`npm i -g pnpm`）

### 1. 初始化数据库

创建数据库并执行建表脚本：

```sql
CREATE DATABASE base_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

然后导入 `BackEnd/src/main/resources/db/schema.sql`（含种子数据，默认管理员 `admin / admin123`）。

如果需要完整菜单数据，请导入`BackEnd/src/main/resources/db/base_admin.sql`

### 2. 启动后端

```bash
cd BackEnd
# 按需修改 application-dev.yml 中的数据库连接信息
mvn spring-boot:run
```

后端运行在 `http://localhost:8080/api`。

### 3. 启动前端

```bash
cd FrontEnd
pnpm install
pnpm dev
```

前端运行在 `http://localhost:3000`，自动代理 `/api` 到后端。

---

## 环境配置

| 配置文件               | 说明                                          |
| ---------------------- | --------------------------------------------- |
| `application.yml`      | 主配置，JWT 密钥与过期时间、MyBatis-Plus 配置 |
| `application-dev.yml`  | 开发环境数据源                                |
| `application-prod.yml` | 生产环境数据源（SQL 日志关闭）                |
| `.env.development`     | 前端开发环境 API 地址                         |
| `.env.production`      | 前端生产环境 API 地址                         |

> 部署生产环境前务必更换 JWT secret 和数据库密码。
