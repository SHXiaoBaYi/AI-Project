---
name: board-dashboard
description: >-
  按仓库现有数据看板规范新增或改造面板（豆腐块、日周月年筛选、分组柱图下钻）。
  Use when the user asks to 做数据面板/数据看板/豆腐块看板, fill docs/board-prompt-template.md,
  or implement /board/* chart pages.
---

# 数据看板

先让用户填 `docs/board-prompt-template.md` 的「填写区」，或根据对话把该表补全后再写代码。不要另起布局或图表库。

## 必读对照

- 页：`FrontEnd/src/pages/board/geo/index.tsx`、`task/index.tsx`、`work/index.tsx`
- 块：`GeoTopicPlatformTofuBoard.tsx`、`TaskTofuBoard.tsx`、`TaskOpsBoard.tsx`、`ChartDrillBoard.tsx`
- 图：`boardColumnChartProps.ts`、`BoardColumnScrollArea.tsx`、`GeoAntCharts.tsx`
- API：`FrontEnd/src/api/board.ts`、`BoardController.java`

## 硬约束

- 页壳：`flex flex-col gap-4 p-4`；顶栏小 `Card` + 日/周/月/年 `Radio.Group` + 不可清空的 `RangePicker`；切粒度用 `demoRangeByGrain` + `normalizeRange`
- 一块一卡、各自请求与下钻；父级只传 `grain`/`range`/筛选
- 柱图：`lazy` 自 `GeoAntCharts`；外包 `BoardColumnScrollArea`；展开 `boardColumnChartProps`（柱宽 30、柱内白字 1 位小数）
- 环比/同比可为负 → 禁止点柱，只点表下钻
- 明细用 `Drawer` + 小表；操作列第一列且 `fixed: 'left'`
- 后端：专用 DTO/VO + `@Schema`（数值必 `example`）；禁止 `Map` 请求体；`Result<T>`；权限字符串
- 改 `FrontEnd/` 后立刻 `npx prettier --write` 该文件
