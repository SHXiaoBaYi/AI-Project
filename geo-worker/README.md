# 本机 GEO 采集

在这台 Windows 上用已安装的 Chrome 提问。每个平台账号单独一个用户目录，登录一次后会留下来。服务器只发题和收结果，不打开浏览器。

## 准备

1. 安装 Node.js 20 和 Google Chrome。
2. 在本目录执行 `npm install`。不需要再执行 `playwright install`。
3. 复制 `config.example.json` 为 `config.json`。
   - `env`：唯一开关，`test` 或 `prod`。地址由程序按开关选定，不在配置里写。
     - `test` 只连接 `http://47.117.132.161/shxby/api`
     - `prod` 只连接 `https://47.117.113.28/shxby/api`
   - `tokens.test` / `tokens.prod`：各自对应服务器环境变量 `GEO_WORKER_TOKEN`。开关拨到哪一边，就只用那一边的令牌；另一边为空时直接退出，不会拿错令牌，也不会去连另一边。
   - `node`：采集电脑名称，要和平台账号里的「采集电脑」完全一致，例如 `office-pc-01`。
   - 登录档案按环境分开，放在 `profileRoot/test` 和 `profileRoot/prod`。同一台电脑同时只允许一个采集进程。

切到生产之前，生产服务里要先写上 `GEO_WORKER_TOKEN`，生产库里的平台账号也要设成「本机 Chrome」并重新登录一次。生产账号编号和测试不是同一条，不能共用测试的登录档案。
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
