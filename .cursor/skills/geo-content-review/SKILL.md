---
name: geo-content-review
description: >-
  小巴依 GEO 文章内容审核口径与 AI 预审规范。当用户提到 GEO 审稿、AI 预审、内容审核智能体、
  撰写注意点、禁词口径、审稿知识库、文章结构/AI友好度检查，或改 GeoContentReviewAi /
  geo/review-kb 时使用。
---

# GEO 内容审核（小巴依）

## 权威数据源（线上可改）

系统菜单：

- **GEO → 基础配置 → 审稿知识库**（`geo/review-kb`）— AI 预审口径
- **GEO → 基础配置 → 文章生成知识库** — 卖点 / 素材 / 风格包 / 文章骨架

| 分类 code | 拆分粒度 |
|-----------|------|
| `writing_rule` | 原编号列表 → **一规则一条**（最高优先级） |
| `ai_friendly` | 原 `## 1…9` → **一检查项一条** |
| `article_structure` | 原 `## 一…六` 下每条要点 → **一检查点一条**（含 E-E-A-T 四维拆开） |
| `brand` | 品宣要点 → **一事实一条** |
| `store` | **一门店一条**；详情在 `attrs`：`openHours` / `address` / `landline` / `mobile` / `coverage`，勿再塞进 content |
| `product` | 系列/物流等 → **一主题一条** |
| `faq` | **一问一条** |

- 不要按源文件拆侧栏菜单；页面用分类 Tab。
- 唯一种子：`geo-review-kb/atomic-seeds.json`；启动 `GeoReviewKbSeedRunner` 自动补齐并**删除**旧整篇编码。
- 表 `geo_review_kb`；接口 `/geo/review-kb`；预审 `buildReviewPromptContext()`。
- 卖点在卖点库；扫描版 PDF 人工补录。材料：`GEO/内容审核智能体要求/`。

## 改代码时注意

1. 改口径 → 改线上单条或改 `atomic-seeds.json` 后强制覆盖。
2. 预审逻辑 → `GeoContentReviewAiServiceImpl`；冲突以撰写注意点为准。
3. **禁止**再把整篇 Markdown（含多个 `##`）存成一条；操作列仍第一列左固定。

## 硬性口径摘要（勿与知识库冲突时自行发挥）

- 净化车间 ≠ 无菌车间；写「新疆牧场」不写「天山牧场」；勿写「合作牧场」。
- 品牌自称「小巴依大尾羊，肉脂型」；勿写阿勒泰/哈萨克大尾羊作品牌自称。
- 乳羔羊：三个月吃奶三个月吃草（勿写「三个月哺乳」）。
- 金羯羊勿提脂肪；勿写国家级、地理标志保护、包装溯源二维码、工厂加工过程。
- 勿写礼盒/精品产品/精品包装（对客 GEO）；勿写自建全程冷链；送达写「全国大部分城市 2–3 天」。
- 同城：保温袋，勿写顺丰骑手/冰袋；售后勿写具体时限。
- 排酸：入厂验收第一环，不是出厂前才排酸。
- 门店区：天山、新市、沙依巴克、水磨沟。

## 预审输出约定

AI 只返回 JSON：`suggestion`（approve/reject）、`score`、`summary`、`issues[]`（`quoteText` 必须能在正文找到）。
