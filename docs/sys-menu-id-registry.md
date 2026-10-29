# sys_menu.menu_id 号段登记

> **硬性约定**：新增功能菜单前，必须先查本表选空闲 ID，写入代码的同时**更新本表**与 `SysMenuIdRanges.java`。  
> 禁止凭感觉「接下一个整数」；禁止跨模块占用；禁止用 `ON DUPLICATE KEY UPDATE` 覆盖他模块已有菜单。

运行时守卫：`SysMenuSeedSupport.upsert`（号段校验 + 拒绝跨模块覆盖）+ 启动审计 `SysMenuIdAuditRunner`。

## 号段总表

| 号段 | 模块 | 维护入口 |
|------|------|----------|
| 1–99 | 系统核心（用户/角色/菜单等） | 初始化 SQL / 系统管理 |
| 100–139、150–159、170–199、310–319 | GEO | `GeoSchemaMigrator` |
| 140–149 | 系统扩展（AI / 钉钉 / 日程规则） | `AiSchemaMigrator` / `DingTalkSchemaMigrator` |
| 160–169 | 数据看板 | `db/v13_board_menu.sql` 等 |
| **200–249、261–279** | **招聘 HR** | `HrSchemaMigrator` |
| **250–260** | **机器人** | `RobotSchemaMigrator` |
| 280–299 | **预留（未分配）** | 新大模块优先用此段 |
| 300–309、320–349 | 收银培训 | `TrainSchemaMigrator` |
| 350+ | 预留 | 新模块 |

## 招聘 HR（200–249、261–279）

| ID | 名称 | 备注 |
|----|------|------|
| 200 | 招聘管理 | 目录 |
| 201–227、230–249 | 各 HR 子菜单/按钮 | 见 `HrSchemaMigrator#seedMenus` |
| 228–229 | 已停用 | `is_active=0` |
| **261** | **HR面试日历** | `hr/calendar` |
| **262–279** | **空闲（HR 下一菜单从 262 起）** | |

## 机器人（250–260）— 禁止占用

| ID | 名称 |
|----|------|
| 250 | 机器人（目录） |
| 251–260 | 意图/会话/消息/链接监控及按钮 |

## 新增菜单检查清单

1. 打开本文件，确认目标 ID **未被占用**且落在本模块号段。  
2. 更新本文件「已占用」行，并如有需要更新 `SysMenuIdRanges`。  
3. SchemaMigrator 使用 `SysMenuSeedSupport.upsert(jdbc, Owner.XXX, id, …)`，不要裸写 `INSERT … ON DUPLICATE`。  
4. 本地启动看日志：不应出现「拒绝覆盖菜单」；审计应「号段审计通过」。

## 历史事故

- 曾用 `menu_id=250` 种子「HR面试日历」，与机器人顶级菜单冲突 → 已迁到 **261**，并由 `repairCalendarMenuIdConflict` 交还 250。
