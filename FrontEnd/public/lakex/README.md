# Lakex 静态资源（一期镜像）

来源：`@alipay/lakex-doc` UMD（版本见 VERSION.txt），镜像到本仓库供抽屉内动态加载，避免运行时依赖公网 CDN。

请勿直接改 `doc.umd.js`；升级时替换整套文件并同步改 VERSION.txt。

## 缓存

- Nginx：`/shxby/lakex/` 一年长缓存（`immutable`），避免每次前端发版重下 7MB+ 包。
- 前端加载 URL 带 `?v=<VERSION.txt>`；升级镜像时务必更新 VERSION.txt，否则浏览器会继续用旧缓存。
