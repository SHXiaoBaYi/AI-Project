---
name: ecs-publish
description: >-
  编译 Spring Boot + React 并发布到测试或生产 ECS。必须先确认环境，禁止默认打生产。
  Use when the user asks to 重新编译并发布到ECS / 发布到ECS / 发布测试 / 发布生产,
  or mentions 测试环境 ECS 121.40.170.82 / 生产环境 ECS 121.40.119.134.
---

# ECS 发布

用户说「重新编译并发布到 ECS」时，**必须带环境**：测试或生产。没说就先问，不要猜、不要默认生产。

## 对照

| 环境 | 公网 | Profile | systemd 源 | 入口 |
|------|------|---------|------------|------|
| 测试 | `121.40.170.82` | `test` | `deploy/xby-admin-test.service` | http://121.40.170.82/shxby/ |
| 生产 | `121.40.119.134` | `prod` | `deploy/xby-admin.service` | http://121.40.119.134/shxby/ |

两边布局相同：jar `/opt/xby-admin/base-admin-1.0.0.jar`，前端 `/opt/xby-admin/html/shxby/`，nginx `/etc/nginx/conf.d/xby-admin.conf`，服务名 `xby-admin`。`VITE_BASE=/shxby/`。不要自创发布路径。

脚本：`./deploy/publish-ecs.sh test` 或 `./deploy/publish-ecs.sh prod`（也可 `./deploy/publish-ecs-test.sh`）。

## 编译

并行：

- 后端：Homebrew `/usr/local/opt/maven/bin/mvn -DskipTests clean package`（不要用坏掉的 `~/bin/mvn`），`all` 权限写 `~/.m2`
- 前端：node `v20.20.2`；缺 `docx-preview` 则中止；`npx vite build`（`.env.production` 已有 `VITE_BASE=/shxby/`）

打完再上传。缺 jar / `dist/index.html` 不要发。

## 上传（本机已验证）

密码 SSH（`sshpass`，`SSHPASS` 已在会话里则沿用）。**回复里不要打印密码。** zsh 不要把 `$SSH_OPTS` 当字符串再拆，`-o` 写在命令上。

1. `scp` jar
2. `scp` nginx 配置；`scp` 对应环境的 systemd 单元到 `/etc/systemd/system/xby-admin.service`
3. 清空 `/opt/xby-admin/html/shxby` 后 `COPYFILE_DISABLE=1 tar --disable-copyfile --no-xattrs` 管道上传 dist（不要 `scp -r` / 不要指望 rsync）
4. `systemctl daemon-reload; nginx -t && nginx -s reload && systemctl restart --no-block xby-admin`
5. 等约 25s；**短 SSH** 看 `is-active`、`:8080`、远端 `index-*.js` 是否等于本地
6. 机内 `curl -m 8`：`/shxby/` 与 `POST /shxby/api/auth/login`；再本机公网同样检查。以机内 200 为准

探活不要一条 SSH 里串一长串 curl 公网 IP（会卡住）。卡住就杀残留 sshpass 再短命令重试。

## 不要做的

- 没指定环境就发到生产
- 提交 git、改 `git config`、在回复里写 SSH/RDS 密码
- 把操作列改到表格右侧
