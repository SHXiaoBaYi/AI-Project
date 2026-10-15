# 数据面板提示词模版

把下面「填写区」复制到对话里，按项填空后发给 AI。不要改「实现约束」段落。

对照现有页：`/board/geo`、`/board/task`、`/board/work`、`/hr/board`。

---

## 填写区（复制从这里开始）

请按本仓库现有「数据看板」规范，**新增 / 改造**下面这个数据面板。先对照最近的同类看板再动手，不要另起一套布局或图表库。

### 1. 基本信息

- 动作：`新增` / `改造已有页`（页路径：________）
- 看板中文名：________
- 菜单位置：数据看板下的子页 / 其它模块（________）
- 路由（`src/pages/.../index.tsx`）：`/board/________`
- 权限码：`board:view`（或：________）
- 最像哪一页：`GEO看板` / `员工收录看板` / `任务看板` / `招聘看板`

### 2. 页顶筛选（同时作用于下方所有豆腐块）

- 时间粒度：日 / 周 / 月 / 年（默认：________）
- 日期范围：不可清空；切粒度时按 `demoRangeByGrain` 重置
- 其它筛选项（可空）：
  - [ ] 人员多选
  - [ ] 任务类型多选
  - [ ] 话题 / 平台 / 部门（写清选项接口）：________
- 筛选提示文案：同时作用于下方 ____ 个看板

### 3. 豆腐块清单（一块一卡，互相独立）

对每个豆腐块填一行。布局默认 `Row` + `Col xs={24} lg={12} xl={8}`。

| # | 标题 | 指标口径（公式） | 图表 | 横轴 | 系列 | 下钻路径 | 点柱 / 点表 | 最细层动作 |
|---|------|------------------|------|------|------|----------|-------------|------------|
| 1 |      |                  | 分组柱 | 日期 |      | A → B → C | 点柱下钻 / 只点表 | 抽屉明细 / 已到最细 |
| 2 |      |                  |      |      |      |          |             |            |

口径必须写清：计数规则、跨平台是否计 2、率的分母、平均是算术平均还是加权、环比/同比是否允许为负。

### 4. 交互

- 面包屑：有 / 无；返回上一级按钮：有
- 每块底部一行灰色 hint，说明「横轴 / 系列 / 怎么点」
- 环比、同比等可能为负的指标：**禁止点柱下钻**，只允许点表格
- 明细：`Drawer` + 小表格；操作列若有，必须第一列且 `fixed: 'left'`
- 需要导出 PNG：是 / 否（用 `downloadChartImage`）

### 5. 数据与接口

- 数据来源表 / 已有接口：________
- 新接口路径（POST）：`/board/________`
- 查询 DTO 字段：`grain, startDate, endDate` + ________
- 图表点结构：`{ axis, series, value, key?, drillable? }`
- 是否走数据范围（GEO 话题∩平台 / 招聘部门+人 / 任务类型+人）：是 / 否
- 空数据、无权限时的表现：空图 + 短提示，不要假数据顶上去（演示页除外）

### 6. 明确不要做

- 不要新图表库（只用 `@ant-design/charts` 的 `Column`/`Line`，且 `lazy` 自 `GeoAntCharts`）
- 不要把多个指标揉进同一张图，除非上面表格写了「堆叠/对比」
- 不要把操作列放到表格右侧
- 不要用 `Map<String, Object>` 做请求体
- 不要在业务页里改操作列位置（`BaseProTable` 会自动左固定）

### 7. 验收

- 切日/周/月/年，四个豆腐块一起变
- 下钻能进能回，面包屑正确
- 柱宽 30px、组内贴紧、组间留白、柱内白字 1 位小数；类目多时横向滚动且 Y 轴钉左
- 前端改完立刻 `npx prettier --write` 对应文件

（填写区结束）

---

## 实现约束（给 AI，填写时不用改）

1. **页面壳**：`flex flex-col gap-4 p-4`；顶栏 `Card size="small"` + `Radio.Group optionType="button"`（日周月年）+ `DatePicker.RangePicker`（`allowClear={false}`）。粒度切换要 `normalizeRange`（周一对周日、月初月末等）。
2. **豆腐块**：独立 `Card`，各自请求、各自下钻状态；父级只传 `grain` + `range`（及其它筛选）。图表 `lazy`：`import('@/components/geo/GeoAntCharts')`。
3. **柱图**：必须包 `BoardColumnScrollArea`，并展开 `boardColumnChartProps`（柱宽 30、组内 padding 0、柱内白字 `toFixed(1)`）。图例用画布外的 `BoardChartLegend`，不要再用图表自带 legend 挡 Y 轴。
4. **轴格式**：日 `YY/MM/DD`，周 `YY/Www`，月 `YY/MM`，年 `YY`。
5. **后端**：Controller → Service → Mapper；`Result<T>`；DTO/VO 全量 `@Schema`（数值字段必带 `example`）；权限字符串；必要时补 `db/vXX_*.sql` 菜单。
6. **对照代码**：
   - 页：`FrontEnd/src/pages/board/geo/index.tsx`、`task/index.tsx`、`work/index.tsx`
   - 块：`GeoTopicPlatformTofuBoard.tsx`、`TaskTofuBoard.tsx`、`TaskOpsBoard.tsx`、`ChartDrillBoard.tsx`
   - 图：`boardColumnChartProps.ts`、`BoardColumnScrollArea.tsx`
   - 接口：`FrontEnd/src/api/board.ts`、`BackEnd/.../controller/BoardController.java`

---

## 填写示例（GEO 话题面板，仅作参考）

- 看板中文名：话题 × AI 平台数据面板
- 路由：`/board/geo`
- 最像：GEO看板
- 筛选：日周月年，默认周；无人员筛选
- 豆腐块：露出排名 / 测试样本量 / 负面出现 / 首位推荐率 / 前三位推荐率
- 下钻：话题 → AI平台 → 测试词或问题或负面内容
- 露出率话题层：各平台率算术平均；跨平台计数的指标同一问题计 2
