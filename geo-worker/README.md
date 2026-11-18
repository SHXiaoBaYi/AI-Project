# 本机 GEO 采集

在这台 Windows 上用已安装的 Chrome 提问。每个平台账号单独一个用户目录，登录一次后会留下来。服务器只发题和收结果，不打开浏览器。

## 准备

1. 安装 Node.js 20 和 Google Chrome。
2. 在本目录执行 `npm install`。不需要再执行 `playwright install`。
3. 复制 `config.example.json` 为 `config.json`。
   - `baseUrl`：测试环境 `http://47.117.132.161/shxby/api`。本机后端调试用 `http://127.0.0.1:8080/api`。
   - `token`：与服务器环境变量 `GEO_WORKER_TOKEN` 相同。本机后端默认是 `local-dev-worker`。
   - `node`：采集电脑名称，要和平台账号里的「采集电脑」完全一致，例如 `office-pc-01`。
4. 在系统的「平台账号」里，把要参与监测的号设为：浏览器监测开启、登录方式「本机 Chrome 用户目录」、采集电脑填同一个名称。

## 检查服务器

```bash
npm run check
```

能打印「服务器已接通」后再登录。若提示测试环境还没有本机采集接口，说明这次改动还没发布到测试 ECS。

## 第一次登录

```bash
npm run login
```

每个账号会打开一个 Chrome 窗口。在窗口里扫码或短信登录，回到终端按回车。完成后该账号变为就绪。

## 日常采集

```bash
npm start
```

程序会一直领取题目。电脑在采集时段保持唤醒，不要同时用普通 Chrome 打开同一个用户目录。遇到验证码或登录墙时，到平台账号里看状态，再执行一次 `npm run login`。
