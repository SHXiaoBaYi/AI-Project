# 生产 / 测试环境说明

## 对照表

| 环境 | ECS 公网 | RDS 实例 | Profile | 本机是否默认连接 |
|------|----------|----------|---------|------------------|
| **测试** | `121.40.170.82` | `rm-bp15f2a3k5b93293k` | `test`（ECS）/ `local`（本机） | **是** |
| **生产** | `121.40.119.134` | `rm-bp18jx12jxs1884h9` | `prod` | 否 |

RDS 地址约定（阿里云）：

- 外网：`{实例ID}so.mysql.rds.aliyuncs.com`（需在控制台**申请外网地址**后才有 DNS）
- 内网：`{实例ID}.mysql.rds.aliyuncs.com`

库名默认均为 `base_admin`。账号密码可用环境变量 `RDS_USERNAME` / `RDS_PASSWORD`，或 ECS 上 `/opt/xby-admin/config/application.yml` 覆盖。

### 当前探测结果（本机）

| 目标 | 结果 |
|------|------|
| 生产 RDS 外网 | 可连通，`base_admin` 约 88 表 |
| 测试 RDS `…so.mysql…` | **DNS 不存在** → 尚未申请外网地址 |
| 测试 RDS 内网 | 仅解析到 VPC 私网 `172.18.x.x`，本机不可达 |
| SSH `root@121.40.170.82` | 密码登录可用（`sshpass`）；无密钥时不要用 `BatchMode` |

因此：**数据库全量同步必须在测试 ECS 上跑**（见 `sync-prod-to-test-on-ecs.sh`）；本机开发在申请测试库外网前，前端默认代理到测试 ECS API。

## 数据与结构同步方向

| 方向 | 内容 | 做法 |
|------|------|------|
| 生产 → 测试 | **数据**（含表结构快照） | `deploy/sync-prod-db-to-test.sh` 全量覆盖 |
| 测试 → 生产 | **表结构** | 改代码里的 SchemaMigrator → 发版到生产，启动自动补齐 |
| 本地 | 只连测试库 | `application.yml` 默认已是测试 RDS |

**不要**用测试库 dump DDL 直接打生产；结构以 Git 中 Migrator 为准。

## 测试环境首次初始化

### A. 阿里云控制台（人工）

1. 测试 RDS `rm-bp15f2a3k5b93293k`：
   - 建库 `base_admin`（utf8mb4）
   - 账号与生产一致或记下新账号
   - **白名单**：测试 ECS 内网网段 / 弹性网卡 IP；若要本机直连再**申请外网地址**并加开发机公网 IP
2. 生产 RDS 白名单增加：`121.40.170.82`（测试 ECS 公网，用于在测试机 dump 生产）
3. 测试 ECS：安全组放行 **22**（运维）、**80**（HTTP）；把本机 SSH 公钥写入 `root`（或提供密钥路径）

### B. 本机有 SSH 后一键 bootstrap

```bash
# 把密钥路径设好，例如：
export ECS_IDENTITY=/path/to/test-ecs.pem

scp -i "$ECS_IDENTITY" \
  deploy/bootstrap-test-ecs.sh deploy/nginx-xby-admin.conf deploy/xby-admin-test.service \
  deploy/sync-prod-to-test-on-ecs.sh \
  root@121.40.170.82:/tmp/

ssh -i "$ECS_IDENTITY" root@121.40.170.82 'bash /tmp/bootstrap-test-ecs.sh /tmp/nginx-xby-admin.conf /tmp/xby-admin-test.service'

# 在测试机上：生产 → 测试 全量同步
ssh -i "$ECS_IDENTITY" root@121.40.170.82 \
  'export RDS_USER=xiaobayi0099 RDS_PASS='"'"'你的密码'"'"'; bash /tmp/sync-prod-to-test-on-ecs.sh'
```

### C. 构建发布

```bash
cd BackEnd && mvn -DskipTests package
cd ../FrontEnd && VITE_BASE=/shxby/ pnpm build
ECS_IDENTITY=... ./deploy/publish-ecs-test.sh
ssh -i "$ECS_IDENTITY" root@121.40.170.82 'systemctl restart xby-admin && systemctl status xby-admin --no-pager'
```

打开 `http://121.40.170.82/shxby/` 冒烟登录。

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

本机无密钥时导出 `SSHPASS` 再用脚本（依赖 `sshpass`）。有密钥则 `ECS_IDENTITY=/path/to.pem`。

## 前端本地代理

默认（`vite.config.ts` / `.env.example`）→ 测试 ECS。临时连生产：

```bash
# FrontEnd/.env.development.local
VITE_API_PROXY_TARGET=http://121.40.119.134/shxby
VITE_UPLOAD_PUBLIC_BASE=http://121.40.119.134/shxby/api
```
