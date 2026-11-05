# GEO 自动监测（问题监控）技术方案

> 状态：**v2 已实现（2026-11-05 下午）**——采集通道按业务决策全量切换为「C 端网页版浏览器采集」
> 对标：模力指数（molizhishu.com）「问题监控 / 问答明细」模块
> 日期：2026-11-05
> 校验：后端 `mvn compile` 通过、前端 `vite build` 通过、v27 迁移已执行

---

## 0. v2 变更说明（覆盖下文 §5 / §8 / §11 的 API 通道描述）

**为什么要改**：复核模力指数 V1.23 / V1.24 官方更新与后台问答明细页后确认，它的取证链路**不是 API 直连**，而是驱动各模型的**网页版 / 手机版真实 C 端界面**：

| 取证项 | 模力指数的真实来源 | 我们的 v2 实现 |
|---|---|---|
| 回答链接 | 模型**官方分享链接**（如 `https://www.doubao.com/thread/xztqnY1pvoh…`），V1.24 新增「回答链接直达」，并注明「不保证长期有效」 | 点 C 端「分享」按钮取 `probeUrl`，拿不到则回退会话页 URL，记录 `probeUrlSource` |
| 截图 | **整页截图存证**，任务级「禁用 / 提及截图 / 全部截图」，单独计费、保留 6 个月 | `screenshot_mode` 三态 + Playwright `fullPage` 截图落 `uploads/geo/auto-monitor/{date}/`，`screenshot_type=BROWSER_SHOT` |
| 深度思考 / 思考过程 | 任务级按模型开关，问答明细新增「思考过程 展开」 | `deep_think` 开关 + 点深度思考按钮 + 抓页面思考面板 `reasoning_text` |
| 引用信源 | 从 C 端回答页 DOM 解析引用来源（标题 + 链接） | 页面 `a[href]` 提取（带真实 url），LLM 裁判结果去重补充 |
| 提问位置 | 「全国随机」地域配置、网页版/手机版独立选配 | `probe_region` 记录，实际 IP 地域由部署环境/代理决定 |

**决策（已与业务确认）**：① 全量切换浏览器通道，停用 API 直连采集；② 采集器用 **Java 内置 Playwright**（单进程，无额外服务）。因此下文 §5.1 的 API 通道方案与 §8 的 `data_source='API'` 描述**不再执行**，以本节为准；LLM 裁判仍走 AI 网关（判定逻辑与采集通道解耦）。

**v2 新增资产**
- 依赖：`com.microsoft.playwright:playwright:1.64.0`（`driver-bundle` 内含 Node 驱动，约 196MB；本机 `application-local.yml` 指向系统 Chrome，生产需装 Chromium 或指定 `GEO_BROWSER_PATH`）
- 配置：`GeoBrowserProbeProperties`（`geo.auto-monitor.browser.*`），平台适配器 URL / 输入框 / 回答 / 深度思考 / 思考过程 / 分享 / 引用信源**选择器全部可按平台覆盖**，C 端改版不用改代码
- 服务：`GeoBrowserProbeService` 单线程串行采集，登录态用 Playwright `storageState`（`{平台名}.json`），每步耗时与选择器命中写入 `probe_request` 诊断 JSON
- 表：`geo_monitor_task` +3 列（deep_think / screenshot_mode / probe_region）；`geo_monitor_result` +6 列（deep_think / reasoning_text / probe_url / probe_request / screenshot_url / screenshot_type）
- 接口：`POST /geo/auto/browser/probe`（单题试跑，不落库）、`POST /geo/auto/browser/state`（上传登录态）、`POST /geo/auto/results/{id}/screenshot`（人工补录真实截图）
- 前端：问答明细抽屉（回答链接 / 整页截图 / 思考过程 / 可点引用信源 / 采集诊断）、任务编辑（深度思考 + 截图模式 + 提问位置）、**采集诊断抽屉**（上线前核对选择器与登录态）
- 回写：`geo_monitor_daily.data_source='浏览器'`，并顺带回写 `screenshot_url` 与 `third_party_url`（官方分享链接），日监测页可直接取证

**上线前必须做**：① 各平台导出并上传登录态 storageState；② 用「采集诊断」逐平台试跑，按实际 DOM 调整 `profiles` 选择器；③ 生产机装 Chromium / 指定浏览器路径。C 端页面选择器与风控会变，这是浏览器通道的固有维护成本。

### 0.1 本机联调记录（2026-11-05，豆包网页版）

| 现象 | 根因 | 处理 |
|---|---|---|
| 首次试跑 3m45s 后 `TimeoutException`，诊断为空 | Playwright Java 的 `Playwright.create()` 会顺带执行 `cli.js install` 下载自带 Chrome for Testing，国内 CDN 卡住；外层 future 225s 超时后只中断线程，子进程仍在下载 | 指定 `executable-path` 时用 `Playwright.CreateOptions.setEnv(PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1)` 跳过下载；本机 Chrome 实测启动 2s。外层超时也改为返回带 `stage` 的诊断快照 |
| 二次试跑 12s「成功」，但回答是 `To pick up a draggable item…` | 未登录的豆包落地页有推广弹窗 + `to_login_button`，提交问题被重定向到 `?from_logout=1`；DOM 兜底把无障碍拖拽提示当成回答，`PAGE_URL` 被当成回答链接 | 新增 `login-required-selectors` / `dismiss-selectors` / `answer-reject-patterns` 三组可配置信号：命中登录标志直接返回「请上传 storageState」并附登录墙截图；回答命中 UI 文案或过短一律判失败；采集失败时不再输出 `probeUrl`，避免把落地页 URL 冒充官方分享链接 |
| 前端 `tsc -b` 报错 | 仓库既有页面（`style-pack`、`yearly-target`、`hr/*`、`train/*`）历史类型问题，与本次改动无关 | 本次新增/修改的 `geo/auto/**` 文件无报错，用 `vite build` 校验产物即可 |

**结论**：采集链路（本机 Chrome → 导航 → 整页截图 → 上传目录）已跑通并落盘真实 PNG，`/api/uploads/geo/auto-monitor/{date}/*.png` 可直接访问；另用本地 mock C 端页面做了全链路自测：`answerSource=SELECTOR`、思考过程 45 字、引用信源 1 条、`probeUrlSource=OFFICIAL_SHARE`（`https://www.doubao.com/thread/...`）、整页截图 `BROWSER_SHOT`、`stage=DONE`，即「回答 / 思考 / 信源 / 官方分享链接 / 截图」五类取证均已验证。**真实平台数据必须以登录态为前提**，未登录时系统在 5 秒内明确报错并附登录墙截图，不再产出假数据。

**登录态怎么来**（已提供一键脚本，不用手拼 Playwright 命令）：

```bash
# Git Bash，在项目根目录；会打开有头 Chrome，人工登录完成后回终端按回车
export JAVA_HOME=/d/AI-XBY/.tools/jdk-21.0.13+9 && export PATH="$JAVA_HOME/bin:$PATH"
./scripts/export-geo-storage-state.sh 豆包 https://www.doubao.com/chat/
# 产出 BackEnd/uploads/geo/auto-monitor/state/豆包.json
```

脚本由 `scripts/GeoStorageStateExporter.java` 调 Playwright `context.storageState()` 导出 cookie + localStorage；也可在「监测任务 → 采集诊断」直接上传同名 JSON。登录态会过期，失效后重新导出即可。

---

## 1. 背景与目标

当前 GEO 监测的数据全部依赖**人工巡查**：同事每天手动到豆包 / DS（DeepSeek）/ 小红书等平台提问，把"是否提及 / 排名 / 是否推荐 / 负面 / 竞品"录入 `geo_monitor_daily`，再人工截图上传。痛点：

- 每天重复提问、录入，人力成本高，且容易漏检、晚检；
- 人工记录粒度不一致（推荐状态、竞品填写随意）；
- 无法做到 7×24 或一日多次的高频监测，优化动作的效果反馈滞后。

**目标**：在 GEO 监测模块下新增「自动监测」子模块——按配置把"监控问题"自动发送到多个 AI 平台，自动判定品牌提及 / 排名 / 推荐状态 / 情感 / 竞品，**自动回写日监测数据**，让现有的日报 / 周报 / 月报 / 全年目标看板零改造直接点亮，并提供对标模力指数的问题监控分析页（达标日历、趋势、竞品对比、变化提醒）。

### 1.1 名词映射

| 模力指数概念 | 本系统对应 | 落库位置 |
|---|---|---|
| 核心词 / 监控词 | 话题 | `geo_topic` |
| 监控问题 | 目标问题（现为 `geo_monitor_daily.keyword` 的自由文本） | 新表 `geo_monitor_question`（结构化管理） |
| 模型 | 平台 | `geo_platform`（豆包 / DS / 小红书…） |
| 达标 | 提及率 ≥ 全年目标 `geo_year_target.target_rate` | 看板已有口径 |

---

## 2. 现状盘点（可直接复用的资产）

| 资产 | 位置 | 复用点 |
|---|---|---|
| 日监测表 | `geo_monitor_daily`（日期×平台×问题×话题，mentioned / rank_no / recommend_status / negative_content / competitors / screenshot_url） | 自动监测结果的**回写目标**，全看板数据源 |
| 话题表 | `geo_topic`（话题名称唯一） | 问题库的归属维度（核心词） |
| 平台表 | `geo_platform`（AI平台 / 内容发布平台两类） | 监测目标平台；需**新增 provider 映射列** |
| AI 网关 | `AiChatService`（743 行，OpenAI 兼容 chat，支持通义 / DeepSeek / 智谱 / Moonshot / 本地，429 限流冷却、模型 fallback） | 自动提问的调用底座，扩展豆包（火山方舟 Ark）即可 |
| AI 厂商配置 | `sys_ai_provider`（provider / apiKey / model / baseUrl，后台可维护） | 各平台 API Key 管理与连通性测试 |
| 调度基建 | `GeoBoardPersistScheduler`（@Scheduled + properties 开关 + 启动补跑） | 自动监测定时器直接照抄该模式 |
| 异步任务进度 | `GeoImportJobService`（jobId + 进度轮询，前端已有进度条交互） | 任务运行进度展示 |
| 钉钉推送 | `DingTalkRobotAssistantService` 等一系服务 | 变化提醒通道（P2） |
| 菜单号段 | GEO：100–139、170–199、310–319；`docs/sys-menu-id-registry.md` 登记制 | 新菜单取号 |
| 迁移机制 | `v{n}_xxx.sql` + `GeoSchemaMigrator` | 新表 `v26_geo_auto_monitor.sql` |

**注意**：`geo_monitor_daily` 唯一键为（inspect_date, platform, keyword, term_type），term_type 取值「日巡查/周巡查」；回写时需按问题频率映射。

---

## 3. 对标拆解：模力指数「问题监控」功能 → 本系统取舍

| 模力指数功能 | 本期取舍 | 说明 |
|---|---|---|
| 7×24 自动监控、每日 9 点前完成 | ✅ P1 | 每日定时批跑（默认 08:30），支持手动触发 |
| 多模型（豆包/DeepSeek/Kimi/通义/百度AI+…） | ✅ P1（API 通道） | 复用 AI 网关，新增豆包 Ark provider；小红书非对话 AI 见 §5 |
| 监控词 / 核心词 / 监控问题多维筛选 | ✅ P1 | 话题（核心词）+ 问题库两级筛选 |
| 回答变化实时提醒 | ✅ P2 | 站内提醒 + 钉钉机器人推送（排名下滑 / 失去提及 / 新增负面 / 竞品上榜） |
| 达标日历 | ✅ P2 | 按日历展示每日提及率是否达标（目标取 `geo_year_target`） |
| 提及位置趋势 / 最佳排名变化 | ✅ P2 | 复用 GeoAntCharts 折线 |
| 竞品排名对比 | ✅ P2 | 问题维度：品牌 vs 竞品在同一回答中的排名顺位 |
| 引用渠道与内容分析 | ⏸ P3 | 依赖裁判模型抽取引用来源，先存数据后出分析 |
| 数据 / 截图导出 | ✅ P2 | Excel 导出复用 `GeoExcelTemplateWriter` 模式 |
| 300 万品牌库 / 行业基准 | ❌ 不做 | 非本系统定位 |

---

## 4. 总体架构

```
┌────────────────────────── FrontEnd（新页面） ──────────────────────────┐
│  /geo/auto/questions 监控问题库   /geo/auto/tasks 监测任务             │
│  /geo/auto/results  问题监控分析页（筛选/日历/趋势/竞品/详情抽屉）      │
└──────────────────────────────┬────────────────────────────────────────┘
                               │ /api/geo/auto/**
┌──────────────────────────────▼────────────────────────────────────────┐
│ GeoAutoMonitorController      配置 CRUD / 手动触发 / 进度与结果查询     │
│ GeoAutoMonitorScheduler       @Scheduled 扫描到期任务 → 派发执行        │
│ GeoAutoMonitorExecutor        执行引擎：问题×平台矩阵展开、并发、限流、  │
│                               重试、断点；产出 run + result             │
│  ├─ MonitorChannel(SPI)       ─ ApiMonitorChannel(P1)：AiChatService    │
│  │                               豆包Ark/DeepSeek/Kimi/通义 直连提问     │
│  │                            ─ BrowserMonitorChannel(P3)：Playwright   │
│  └─ MonitorJudgeService       LLM 裁判：判定提及/排名/推荐/情感/竞品/引用 │
│ GeoMonitorDailySyncService    结果 upsert 回写 geo_monitor_daily        │
│ GeoMonitorAlertService(P2)    与昨日快照 diff → 生成变化提醒            │
└──────┬──────────────────────────────┬─────────────────────────────────┘
       │                              │
  MySQL 新表 4 张                既有表复用
  geo_monitor_question          geo_monitor_daily（回写）
  geo_monitor_task              geo_topic / geo_platform
  geo_monitor_run               sys_ai_provider
  geo_monitor_result(+alert)    全年目标/周月看板（自动生效）
```

关键设计原则：

1. **执行与判定分离**：Channel 负责"拿到回答原文"，Judge 负责"从原文判定指标"。换通道（API→浏览器）只换 Channel，判定逻辑不变。
2. **判定与回写分离**：`geo_monitor_result` 是自动监测的完整事实（含回答原文、判定原始输出），`geo_monitor_daily` 是全站统一口径的投影。看板口径永远来自 daily，自动监测只是 daily 的一个新数据源。
3. **人工优先**：同一（日期×平台×问题）已有人工记录时，默认不覆盖人工数据，避免人工核实过的结论被自动结果冲掉。

---

## 5. 监控通道策略（核心决策点）

> ⚠️ **本节为 v1 原始设计，已被 §0 的 v2 决策覆盖**：实际实现为全量 C 端网页版浏览器采集，API 直连仅用于 LLM 裁判（不用于采集）。以下保留作决策对比记录。

### 5.1 P1：API 直连通道（推荐首选）

| 平台 | 接入方式 | 备注 |
|---|---|---|
| 豆包 | 火山方舟 Ark，OpenAI 兼容 `base_url` | 需在 `sys_ai_provider` 新增 provider=`doubao`，`AiProperties` 加内置预设 |
| DS（DeepSeek） | 官方 API，OpenAI 兼容 | `AiChatService` 已支持 |
| Kimi（月之暗面） | Moonshot API，OpenAI 兼容 | 已支持 |
| 通义千问 | DashScope，OpenAI 兼容 | 已支持，含 429 冷却与模型 fallback |
| 百度 AI+ | 千帆 v2 OpenAI 兼容端点 | P1 可选 |
| 小红书 | ❌ 无对话 API | 搜索平台非对话 AI，P1 保持人工巡查，P3 走浏览器通道 |

- 优点：稳定、合规、实现快、天然并发；每条结果可溯源（answer_text 全文留档）。
- 局限（必须向用户明示）：**API 回答 ≠ C 端 App 回答**——App 端有联网搜索、推荐卡片、个性化，API 是纯模型回答。页面上每条数据标注 `channel=API`，与人工数据（`channel=人工`）可区分、可筛选。

### 5.2 P3：浏览器自动化通道（可选增强）

Playwright 无头浏览器驱动各平台 Web 端：真实提问 → 抓回答文本 + 截图（自动填 `screenshot_url`）+ 推荐位标记。价值：补齐"小红书"和"截图证据"。成本：账号矩阵登录态维护、风控对抗、独立 worker 部署。**P1 不做，接口层预留 `MonitorChannel` SPI。**

---

## 6. 数据模型（DDL 草案）

### 6.1 新表 1：`geo_monitor_question` 监控问题库

```sql
CREATE TABLE IF NOT EXISTS geo_monitor_question (
  id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  question_text    VARCHAR(512) NOT NULL                COMMENT '监控问题（目标问题原文）',
  topic_id         BIGINT       NOT NULL                COMMENT '归属话题ID（核心词）',
  brand_words      VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '本问题品牌词，逗号分隔，空=用全局默认',
  competitor_words VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '本问题竞品词，逗号分隔，空=用全局默认',
  freq             VARCHAR(16)  NOT NULL DEFAULT '每日' COMMENT '监测频率：每日/每周',
  owner_user_id    BIGINT       NOT NULL DEFAULT 0      COMMENT '负责人，0=未分配',
  enabled          TINYINT      NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
  sort_order       INT          NOT NULL DEFAULT 0,
  remark           VARCHAR(500) DEFAULT '',
  -- BaseEntity 标准审计字段（create_by/time、update_by/time、is_active）
  PRIMARY KEY (id),
  UNIQUE KEY uk_question (question_text),
  KEY idx_topic (topic_id),
  KEY idx_enabled_freq (enabled, freq)
) COMMENT='GEO自动监测-监控问题库';
```

- 存量迁移：一键把 `geo_monitor_daily` 中出现过的 distinct keyword 洗入问题库（后端提供 seed 接口，同 `yearly/seed-sample` 模式）。
- 全局默认品牌词 / 竞品词放系统参数（如 `小巴依`；竞品清单由业务提供），支持配置页维护。

### 6.2 新表 2：`geo_monitor_task` 监测任务

```sql
CREATE TABLE IF NOT EXISTS geo_monitor_task (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  task_name       VARCHAR(128) NOT NULL                COMMENT '任务名',
  scope_type      VARCHAR(16)  NOT NULL DEFAULT 'TOPIC' COMMENT '圈选方式：TOPIC=按话题 / QUESTION=按问题',
  scope_ids       VARCHAR(1024) NOT NULL DEFAULT ''    COMMENT '圈选ID列表，逗号分隔',
  platforms       VARCHAR(255) NOT NULL                COMMENT '监测平台名列表，逗号分隔，须为AI平台',
  freq            VARCHAR(16)  NOT NULL DEFAULT '每日' COMMENT '每日/每周',
  run_time        TIME         NOT NULL DEFAULT '08:30:00' COMMENT '每日运行时刻',
  week_day        TINYINT      DEFAULT NULL            COMMENT '每周模式：1-7',
  overwrite_manual TINYINT     NOT NULL DEFAULT 0      COMMENT '同日同题同平台有人工记录时是否覆盖 0=人工优先',
  notify_enabled  TINYINT      NOT NULL DEFAULT 1      COMMENT '变化提醒开关',
  notify_channels VARCHAR(64)  NOT NULL DEFAULT '站内'  COMMENT '站内/站内,钉钉',
  enabled         TINYINT      NOT NULL DEFAULT 1,
  last_run_id     BIGINT       DEFAULT NULL,
  next_run_time   DATETIME     DEFAULT NULL,
  remark          VARCHAR(500) DEFAULT '',
  PRIMARY KEY (id),
  KEY idx_enabled_next (enabled, next_run_time)
) COMMENT='GEO自动监测-任务配置';
```

### 6.3 新表 3：`geo_monitor_run` 运行批次

```sql
CREATE TABLE IF NOT EXISTS geo_monitor_run (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  task_id      BIGINT      NOT NULL COMMENT '任务ID',
  task_name    VARCHAR(128) NOT NULL DEFAULT '' COMMENT '任务名快照',
  run_date     DATE        NOT NULL COMMENT '监测归属日期（回写daily的inspect_date）',
  trigger_type VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED/MANUAL',
  status       VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
  total_count  INT         NOT NULL DEFAULT 0 COMMENT '问题×平台总数',
  done_count   INT         NOT NULL DEFAULT 0,
  success_count INT        NOT NULL DEFAULT 0,
  failed_count INT         NOT NULL DEFAULT 0,
  start_time   DATETIME    NOT NULL,
  end_time     DATETIME    DEFAULT NULL,
  error_msg    VARCHAR(1000) DEFAULT '',
  PRIMARY KEY (id),
  KEY idx_task_date (task_id, run_date),
  KEY idx_status (status)
) COMMENT='GEO自动监测-运行批次';
```

### 6.4 新表 4：`geo_monitor_result` 监测明细

```sql
CREATE TABLE IF NOT EXISTS geo_monitor_result (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  run_id           BIGINT       NOT NULL COMMENT '批次ID',
  question_id      BIGINT       NOT NULL,
  question_text    VARCHAR(512) NOT NULL COMMENT '问题快照',
  topic_id         BIGINT       NOT NULL COMMENT '话题快照',
  platform         VARCHAR(32)  NOT NULL COMMENT '平台名，同geo_platform',
  channel          VARCHAR(16)  NOT NULL DEFAULT 'API' COMMENT 'API/BROWSER',
  answer_model     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '实际调用的模型名',
  answer_text      MEDIUMTEXT   COMMENT 'AI回答原文（留档溯源）',
  -- ↓ 判定结果（与 geo_monitor_daily 字段对齐）
  mentioned        TINYINT      NOT NULL DEFAULT 0,
  rank_no          INT          DEFAULT NULL COMMENT '品牌在推荐列表中的位次',
  recommend_status VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '未出现/出现且推荐/出现未推荐',
  sentiment        VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '正面/中性/负面',
  mention_position INT          DEFAULT NULL COMMENT '品牌首次出现于回答第几段',
  negative_content VARCHAR(500) DEFAULT '',
  competitors      VARCHAR(500) DEFAULT '' COMMENT '出现的竞品，逗号分隔',
  citations        JSON         COMMENT '引用来源[{title,site,context}]',
  judge_raw        JSON         COMMENT '裁判模型原始输出（复核用）',
  -- ↓ 执行信息
  status           VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/JUDGE_FAILED/CALL_FAILED',
  error_msg        VARCHAR(500) DEFAULT '',
  latency_ms       INT          DEFAULT NULL,
  token_cost       INT          DEFAULT NULL,
  daily_id         BIGINT       DEFAULT NULL COMMENT '回写 geo_monitor_daily 的行ID',
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_q_p (run_id, question_id, platform),
  KEY idx_q_date (question_id, create_time),
  KEY idx_platform (platform)
) COMMENT='GEO自动监测-监测明细';
```

### 6.5 既有表的最小改动

1. `geo_monitor_daily` **新增 1 列**：`data_source VARCHAR(16) NOT NULL DEFAULT '人工'`（人工/API/浏览器），便于看板与列表区分来源；不加该列也可用 update_by 前缀 hack，但不干净，建议加列（`GeoSchemaMigrator` 兼容执行，存量数据默认"人工"）。
2. `geo_platform` **新增 1 列**：`provider_code VARCHAR(32) DEFAULT ''`，映射 `sys_ai_provider.provider`（豆包→doubao、DS→deepseek、Kimi→moonshot…），未配置的（小红书）不可被自动任务勾选。
3. 新表 `geo_monitor_alert`（P2）：alert_type（失去提及/排名下滑≥N/新增负面/竞品上榜）、before/after JSON、已读状态。

---

## 7. 执行引擎与监控策略

### 7.1 触发
- **定时**：`GeoAutoMonitorScheduler` 每 30s 扫 `enabled=1 AND next_run_time<=now` 的任务 → 创建 run → 交给线程池异步执行（复用 `GeoBoardPersistScheduler` 的 properties 开关模式：`geo.auto-monitor.enabled/cron/parallelism`）。
- **手动**：任务页「立即运行」按钮，同一任务运行中禁止重复触发（run 表 RUNNING 判断）。

### 7.2 执行流程（单批次）
1. 展开矩阵：问题集 × 平台集 = N 个工作项，写入 run.total_count；
2. 按平台分组并发（每平台独立信号量，默认并发 2、QPS 1，避免触发风控；豆包/DeepSeek 各自 RPM 独立配置）；
3. 每个工作项：调 `AiChatService.chat(question)` 拿回答 → 调裁判模型出 JSON 判定 → 组装 result；
4. 单项失败：指数退避重试 2 次（2s/8s），仍失败记 CALL_FAILED/JUDGE_FAILED，**不阻塞批次**；
5. 批次完成：统计 SUCCESS/PARTIAL/FAILED，更新 run；P2 附加：与昨日 result diff 生成 alert + 推送；
6. 回写 daily（见 §8）。

### 7.3 LLM 裁判策略（`MonitorJudgeService`）
- 输入：问题原文 + AI 回答全文 + 品牌词表 + 竞品词表；
- 输出（强制 JSON，temperature=0）：
  ```json
  {
    "mentioned": true,
    "rank_no": 2,
    "recommend_status": "出现且推荐",
    "sentiment": "正面",
    "mention_position": 1,
    "negative_content": "",
    "competitors": ["竞品A"],
    "citations": [{"title": "…", "site": "zhihu.com", "context": "…"}]
  }
  ```
- 裁判模型与被测平台**解耦**，统一用一个便宜稳定的模型（默认 qwen-flash 或 deepseek-chat，配置可换）；判定 prompt 存代码常量，`judge_raw` 全量留档可人工复核；
- 一致性保障：判定规则文档化（"推荐"的定义=品牌出现在 AI 主动推荐的列表/加粗强调中），上线前用 20 条人工标注样本做回归校验。

### 7.4 频率与波动观测
- 问题级 freq：每日（默认）/ 每周；每周问题回写 daily 时 term_type=`周巡查`，每日问题=`日巡查`（对齐 v10 唯一键）；
- 可选：重点问题配置「早/晚各一次」，两次都落 result，daily 只取最新一次（避免同日唯一键冲突）。

---

## 8. 回写 geo_monitor_daily（打通全看板的关键）

- 映射：`inspect_date=run_date`、`platform`、`keyword=question_text`、`topic_id`、`mentioned`、`rank_no`、`recommend_status`、`negative_content`、`competitors`、`data_source='API'`；`screenshot_url` 留空（API 通道无截图，P3 浏览器通道补）。
- upsert 规则（uk: inspect_date+platform+keyword+term_type）：
  - 不存在 → insert；
  - 存在且为人工记录 且任务未开 `overwrite_manual` → **跳过，result 标记 SKIPPED_MANUAL**；
  - 存在且为自动记录 → 更新（同日重跑取最新）；
- 效果：**日报/周报/月报/全年目标/话题×平台图表全部自动出数，一行看板代码不用改**。周/月落库锁定逻辑不受影响（落库前数据已就位）。

---

## 9. 后端接口设计（`/api/geo/auto/*`）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/questions/list` | 问题库分页（筛选：话题/关键词/启用/频率） | geo:auto:list |
| POST/PUT/DELETE | `/questions` | 问题 CRUD、批量启停 | geo:auto:edit |
| POST | `/questions/import` | Excel 批量导入（复用导入模板+进度模式） | geo:auto:edit |
| POST | `/questions/seed-from-daily` | 从历史日监测 keyword 一键洗入 | geo:auto:edit |
| GET/POST/PUT/DELETE | `/tasks` | 任务 CRUD、启停 | geo:auto:task |
| POST | `/tasks/{id}/run` | 立即运行 | geo:auto:run |
| GET | `/runs` | 批次列表（进度、成功/失败数） | geo:auto:list |
| GET | `/runs/{id}` | 批次详情 + 失败明细 | geo:auto:list |
| POST | `/results/list` | 结果分页（筛选：日期区间/平台/话题/问题/提及状态/情感/来源） | geo:auto:list |
| GET | `/results/{id}` | 明细（回答原文、judge_raw、引用、diff） | geo:auto:list |
| POST | `/results/trend` | 提及率/排名/达标率趋势序列 | geo:auto:list |
| POST | `/results/calendar` | 达标日历数据（P2） | geo:auto:list |
| POST | `/results/competitor-compare` | 竞品排名对比（P2） | geo:auto:list |

DTO/VO 全部 `@Schema` 注解，禁 Map 入参（项目 OpenAPI 规则）。菜单 ID 从 GEO 号段取（实现时查 `sys-menu-id-registry.md`，当前 138/139、175–177、319 空闲），用 `SysMenuSeedSupport.upsert` 种子，同步更新登记表与 `SysMenuIdRanges`。

---

## 10. 前端交互设计

新增菜单（GEO监测 目录下）：`自动监测` 目录 → 监控问题库 / 监测任务 / 问题监控 三页（或 P1 合并为两页：问题库+任务合一）。

### 10.1 监控问题库 `/geo/auto/questions`
- BaseProTable：问题文本（点击弹出上下文）、所属话题 Tag、品牌词/竞品词、频率、负责人、启用开关、操作；
- 顶部：新增（抽屉表单，话题下拉复用 `getGeoTopicOptionsApi`）、Excel 导入、**「从历史日监测导入」**（一键洗入，展示去重统计）；
- 交互细节：品牌词为空时展示全局默认值 placeholder。

### 10.2 监测任务 `/geo/auto/tasks`
- 任务卡片列表（每卡：名称、圈选范围统计「12 个话题 · 46 个问题」、平台 Tag 组、频率+运行时刻、上次运行结果 Badge、启停 Switch）；
- 「立即运行」→ 跳转/弹出运行进度（复用日监测导入的进度条交互：done/total 百分比 + 失败数），支持失败项单条重试（P2）；
- 新增/编辑抽屉：圈选方式（按话题勾选树 / 按问题多选）、平台多选（仅列 `provider_code` 已配置的 AI 平台，未配置禁用并提示去 AI 厂商配置）、运行时刻、覆盖策略、提醒开关；
- **成本预估提示**：根据 问题数×平台数×每日次数 预估每日调用次数与 token 量级，提交前展示。

### 10.3 问题监控分析页 `/geo/auto/results`（对标模力指数内页）
- **筛选区**：日期区间（默认近 7 天）、平台多选（含「全模型」汇总）、话题、监控问题搜索、提及状态、情感、数据来源（自动/人工）；
- **汇总卡片区**：提及率、平均最佳排名、推荐率、负面数、较昨日变化箭头；
- **主表（默认按问题聚合）**：每行一个问题，平台横向展开为列（豆包/DS/Kimi/通义…），每格显示 提及状态点（绿=提及且推荐/黄=提及未推荐/灰=未出现）+ 排名序号 + 情感色点；点击格子 → 详情抽屉；
- 切换「按平台明细」视图：行=问题×平台，与 daily 列表风格一致；
- **达标日历（P2）**：月历组件，每日一格显示提及率与达标/未达标色块，点击下钻当日明细；
- **趋势区（P2）**：提及率折线（多平台多色）、最佳排名趋势、达标率趋势，复用 `GeoAntCharts`；
- **竞品对比（P2）**：选定问题下，品牌 vs 各竞品的排名顺位表 + 变化标记；
- **详情抽屉**：问题与平台头部、回答原文（可折叠、关键句高亮品牌词）、判定指标 Tag 组、引用来源列表（标题+站点+上下文）、`judge_raw` 折叠面板（复核用）、**与昨日 diff**（新增提及↑/排名变化↕/新增负面⚠ 高亮）、跳转「查看已回写日监测」；
- **变化提醒（P2）**：页顶 Alert 汇总今日告警 + 站内通知中心，可选钉钉推送。

---

## 11. 分期实施计划

| 阶段 | 内容 | 交付物 |
|---|---|---|
| **P1（MVP）✅ 已实现** | 表结构 v26 + 问题库（含历史洗入）+ 任务 CRUD + 定时/手动执行 + API 通道（豆包/DS/Kimi/通义/智谱）+ LLM 裁判 + 回写 daily + 结果矩阵视图/明细/详情抽屉（含与上次 diff） | 自动数据每天 08:30 自动进看板 |
| **P2** | 变化提醒（站内+钉钉）、达标日历、趋势图、竞品对比、Excel 导出、失败重试 | 对齐模力指数内页体验 |
| **P3** | 浏览器自动化通道（截图/推荐位/小红书）、引用渠道分析 | 补齐 C 端一致性 |

### P1 实现清单（2026-11-05）

- 迁移：`db/v26_geo_auto_monitor.sql`（4 新表）+ `GeoSchemaMigrator.ensureAutoMonitorColumns/ensureAutoMonitorMenus`（daily.data_source、platform.provider_code 增列 + 菜单 355–365）
- 后端：`GeoAutoMonitorController`（/geo/auto/**）+ `GeoAutoMonitorServiceImpl` + `GeoAutoMonitorExecutor`（并发/限流/重试/幂等收尾）+ `GeoMonitorJudgeService`（LLM 裁判）+ `GeoMonitorDailySyncService`（人工优先回写）+ `GeoAutoMonitorScheduler` + `GeoAutoMonitorProperties`（geo.auto-monitor.*）
- AI 网关：`AiProperties` 新增 doubao（火山方 Ark）预设与 doubaoApiKey 配置；`sys_ai_provider` 种子补 doubao 行
- 前端：`pages/geo/auto/questions`（问题库）、`pages/geo/auto/tasks`（任务+运行记录抽屉）、`pages/geo/auto/results`（矩阵/明细/详情抽屉）；`api/geoAuto.ts`、`types/geoAuto.d.ts`、`constants/geoAuto.ts`
- 使用前配置：① 系统管理→AI模型配置 填各厂商 API Key（豆包需火山方舟 Key）；② 品牌词/竞品词全局默认在 `application.yml` 的 `geo.auto-monitor.brand-words/competitor-words`

---

## 12. 风险与待确认问题

1. **API ≠ C 端回答**（最重要）：API 通道拿不到 App 的联网检索/推荐卡片/个性化结果，"排名/推荐"口径与人工巡查可能存在系统性偏差。→ 页面明确标注来源；上线后跑 1–2 周 API 与人工**并行对照**，评估偏差后再决定人工巡查降频/保留哪些平台。
2. **小红书无法 API 化**：P1 仅 AI 对话平台；小红书继续人工，或 P3 浏览器通道。
3. **裁判模型稳定性**：LLM 判定可能出现口径漂移 → temperature=0 + 固定 prompt + judge_raw 留档 + 上线前人工样本回归；后续可加"人工复核纠偏"入口反哺。
4. **成本**：按 50 问题 × 4 平台 × 1 次/日估算 ≈ 200 次提问 + 200 次裁判/日，用 flash 级模型成本可控，但需在任务配置页展示预估，避免无意识扩量。
5. **待业务确认**：
   - 全局品牌词清单（"小巴依"及别名）与竞品词清单；
   - `overwrite_manual` 默认策略（建议默认人工优先）；
   - 每日运行时刻（建议 08:30，模力指数口径为 9 点前完成）；
   - 是否需要"同问题一日多次监测"的波动对比能力。

---

## 13. 落地时的项目约定（实现阶段遵循）

- 后端：Controller→Service→Mapper 分层、`Result<T>` 包装、`@RequiresPermission`、`@Log`、`@Schema` 全量注解、新表走 `v26_geo_auto_monitor.sql` + `GeoSchemaMigrator`；
- 前端：`pages/geo/auto/**` 自动路由、Tailwind class、BaseProTable/BaseModalForm/PermissionButton 组件复用、写完跑 `npx prettier --write`；
- 菜单：GEO 号段取号并同步 `docs/sys-menu-id-registry.md` 与 `SysMenuIdRanges`。
