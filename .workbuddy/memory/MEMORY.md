# 项目长期记忆（GEO 自动监测 / AI-Project）

## 工程环境（本机）
- 无系统 JDK/Maven，便携版在 `D:/AI-XBY/.tools/`：`jdk-21.0.13+9`、`apache-maven-3.9.11`。编译/启动命令须先 `export JAVA_HOME/PATH` 并 `unset SERVER__PORT`（否则环境自带 `SERVER__PORT=54943` 会覆盖 8080 撞宿主端口）。
- 测试库经 SSH 隧道：`127.0.0.1:13306` → 测试 RDS（ECS `121.40.170.82` 转发）。密钥 `~/.ssh/xby-test-ecs`。
- 前端 `VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev`（:3000）；Vite 重建依赖缓存若被 safe-delete 拦，先 `rm -rf node_modules/.vite`。

## Playwright 选择器铁律（已踩坑，勿再犯）
- **不要**把「CSS 选择器」与 `text=` 正则用逗号拼成单个字符串（如 `"[data-testid='x'], text=/登录/"`）——Playwright 会当纯 CSS 解析抛 `Unexpected token "="`；且单条异常会被 `isAnyVisible` 静默吞掉导致功能静默失效。
- 正确做法：拆成 `List<String>` 候选选择器，逐条 `page.locator(sel).first()` 尝试（参考 `loginRequiredSelectors` / `loginEntrySelectors` 等）。

## 浏览器采集登录态
- 真实平台取证（回答/分享链接/思考/引用）必须先有登录态；豆包等默认扫码登录，密码模拟登录路线不适用，正确路径是 `STORAGE_STATE`（人工用 `scripts/export-geo-storage-state.sh` 导出 Playwright storageState JSON）。登录态会过期。
- `executablePath` 非空时 `Playwright.create()` 须带 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，否则国内 CDN 卡死安装 Chromium。

## 提交约定
- 提交信息用 `feat:`/`fix:` 前缀；`.schema-migrate-ok` 是迁移运行时间戳，属本地产物，勿纳入提交。
