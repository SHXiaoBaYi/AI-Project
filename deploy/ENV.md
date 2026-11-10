# 生产 / 测试环境说明

## 对照表

| 环境 | ECS 公网 | RDS 实例 | Profile | 本机是否默认连接 |
|------|----------|----------|---------|------------------|
| **测试** | `47.117.132.161` | `rm-uf64y725h1ufca0jk` | `test`（ECS）/ `local`（本机） | **是** |
| **生产** | `47.117.113.28` | `rm-uf6l17zg54sh9378v` | `prod` | 否 |

RDS 地址约定（阿里云）：

- 外网：`{实例ID}so.mysql.rds.aliyuncs.com`（需在控制台**申请外网地址**后才有 DNS）
- 内网：`{实例ID}.mysql.rds.aliyuncs.com`

库名默认均为 `base_admin`。测试库账号 `shxby`，生产库账号 `xiaobayi0099`。密码可用环境变量 `RDS_PASSWORD`，或 ECS 上 `/opt/xby-admin/config/application.yml` 覆盖。

外网、内网：

- 测试外网 `rm-uf64y725h1ufca0jkso.mysql.rds.aliyuncs.com`，内网 `rm-uf64y725h1ufca0jk.mysql.rds.aliyuncs.com`
- 生产外网 `rm-uf6l17zg54sh9378vso.mysql.rds.aliyuncs.com`，内网 `rm-uf6l17zg54sh9378v.mysql.rds.aliyuncs.com`

### 旧环境（数据仍在，切流前不要释放）

仓库地址已改到上表。旧库、旧磁盘上的文件都还在，没有迁移、没有发布。

| 环境 | ECS 公网 | RDS 实例 | 上传目录 |
|------|----------|----------|----------|
| 旧测试 | `121.40.170.82` | `rm-bp15f2a3k5b93293k` | `/opt/xby-admin/uploads` |
| 旧生产 | `121.40.119.134` | `rm-bp18jx12jxs1884h9` | `/opt/xby-admin/uploads` |

切到新地址之前：先从旧生产库 dump 导入新生产库 `rm-uf6l17zg54sh9378v`，再把旧生产机的 `uploads` 整目录同步到新生产机同一路径。核对表数量、行数和若干附件能打开之后，再发布。新库申请外网地址之前，本机不可直连，前端默认代理测试 ECS。

## 数据与结构同步方向

| 方向 | 内容 | 做法 |
|------|------|------|
| 生产 → 测试 | **数据**（含表结构快照） | `deploy/sync-prod-db-to-test.sh` 全量覆盖 |
| 测试 → 生产 | **表结构 / 初始化数据 / 新列默认值** | 改 `*SchemaMigrator`（及它执行的 `db/v*.sql`）→ `./deploy/publish-ecs.sh prod`。脚本会对比上次生产 SHA，有变更则等启动 Migrator 跑完（`PROD_RELEASE_READY`） |
| 本地 | 只连测试库 | `application.yml` 默认已是测试 RDS |

**不要**用测试库 dump DDL 直接打生产；结构以 Git 中 Migrator 为准。

## 测试环境首次初始化

### A. 阿里云控制台（人工）

1. 测试 RDS `rm-uf64y725h1ufca0jk`：
   - 建库 `base_admin`（utf8mb4）
   - 账号与生产一致或记下新账号
   - **白名单**：测试 ECS 内网网段 / 弹性网卡 IP；若要本机直连再**申请外网地址**并加开发机公网 IP
2. 生产 RDS 白名单增加：`47.117.132.161`（测试 ECS 公网，用于在测试机 dump 生产）
3. 测试 ECS：安全组放行 **22**（运维）、**80**（HTTP）；把本机 SSH 公钥写入 `root`（或提供密钥路径）

### B. 本机有 SSH 后一键 bootstrap

```bash
# 把密钥路径设好，例如：
export ECS_IDENTITY=/path/to/test-ecs.pem

scp -i "$ECS_IDENTITY" \
  deploy/bootstrap-test-ecs.sh deploy/nginx-xby-admin.conf deploy/xby-admin-test.service \
  deploy/sync-prod-to-test-on-ecs.sh \
  root@47.117.132.161:/tmp/

ssh -i "$ECS_IDENTITY" root@47.117.132.161 'bash /tmp/bootstrap-test-ecs.sh /tmp/nginx-xby-admin.conf /tmp/xby-admin-test.service'

# 在测试机上：生产 → 测试 全量同步
ssh -i "$ECS_IDENTITY" root@47.117.132.161 \
  'export RDS_USER=xiaobayi0099 RDS_PASS='"'"'你的密码'"'"'; bash /tmp/sync-prod-to-test-on-ecs.sh'
```

### C. 构建发布

```bash
cd BackEnd && mvn -DskipTests package
cd ../FrontEnd && VITE_BASE=/shxby/ pnpm build
ECS_IDENTITY=... ./deploy/publish-ecs-test.sh
ssh -i "$ECS_IDENTITY" root@47.117.132.161 'systemctl restart xby-admin && systemctl status xby-admin --no-pager'
```

打开 `http://47.117.132.161/shxby/` 冒烟登录。

同步数据后务必重启 `xby-admin`，让 Migrator 补回测试侧已有、生产 dump 中尚无的新列。

> 若测试库已申请外网，也可在本机直接跑 `./deploy/sync-prod-db-to-test.sh`（需本机 IP 在两边白名单）。

## 日常发布

必须指定环境，不要默认打生产。

```bash
# 测试
./deploy/publish-ecs.sh test
# 或 ./deploy/publish-ecs-test.sh

# 生产（验收通过后）
./deploy/publish-ecs.sh prod
```

生产发布会：

1. 读生产机 `/opt/xby-admin/.prod-release` 的上次 SHA（或本机 `deploy/.last-prod-commit`）
2. 跑 `deploy/detect-prod-delta.sh`，从代码判断本次是否有建表/加列、菜单等初始化数据、新列默认值/回填
3. 有则重启后必须等 SchemaMigrator 执行完毕（工作目录 `.schema-migrate-ok` / 日志 `PROD_RELEASE_READY`）；未挂接的 SQL 会直接失败
4. 成功后把当前 git SHA 写成新基线

样例灌数开关（`geo.seed` / `hr.seed` / `demo.seed` 等）生产保持关闭。

本机无密钥时导出 `SSHPASS` 再用脚本（依赖 `sshpass`）。有密钥则 `ECS_IDENTITY=/path/to.pem`。

## 前端本地代理

默认（`vite.config.ts` / `.env.example`）→ 测试 ECS。临时连生产：

```bash
# FrontEnd/.env.development.local
VITE_API_PROXY_TARGET=http://47.117.113.28/shxby
VITE_UPLOAD_PUBLIC_BASE=http://47.117.113.28/shxby/api
```
