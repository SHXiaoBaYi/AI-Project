#!/usr/bin/env bash
# 生产库 → 测试库 全量同步（结构+数据）
#
# 用法（在能同时访问生产外网与测试外网的机器上执行，需已安装 mysql / mysqldump）：
#   export PROD_RDS_HOST=rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com
#   export TEST_RDS_HOST=rm-bp15f2a3k5b93293kso.mysql.rds.aliyuncs.com
#   export RDS_USER=xiaobayi0099
#   export RDS_PASS='***'
#   ./deploy/sync-prod-db-to-test.sh
#
# 可选：DB_NAME=base_admin  DUMP_DIR=/tmp
#
# 注意：会覆盖测试库全部业务表数据。同步后请重启测试 ECS 上的 xby-admin，
# 让 SchemaMigrator 把「测试已开发、生产尚未发」的结构补回。
set -euo pipefail

PROD_RDS_HOST="${PROD_RDS_HOST:-rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com}"
TEST_RDS_HOST="${TEST_RDS_HOST:-rm-bp15f2a3k5b93293kso.mysql.rds.aliyuncs.com}"
DB_NAME="${DB_NAME:-base_admin}"
RDS_USER="${RDS_USER:?请设置 RDS_USER}"
RDS_PASS="${RDS_PASS:?请设置 RDS_PASS}"
DUMP_DIR="${DUMP_DIR:-/tmp}"
STAMP="$(date +%Y%m%d_%H%M%S)"
DUMP_FILE="${DUMP_DIR}/base_admin_prod_${STAMP}.sql"

echo "==> dump 生产 ${PROD_RDS_HOST}/${DB_NAME} → ${DUMP_FILE}"
mysqldump \
  -h "$PROD_RDS_HOST" -P 3306 -u "$RDS_USER" -p"$RDS_PASS" \
  --single-transaction --routines --triggers --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  "$DB_NAME" > "$DUMP_FILE"

echo "==> 导入测试 ${TEST_RDS_HOST}/${DB_NAME}（覆盖）"
mysql \
  -h "$TEST_RDS_HOST" -P 3306 -u "$RDS_USER" -p"$RDS_PASS" \
  --default-character-set=utf8mb4 \
  "$DB_NAME" < "$DUMP_FILE"

echo "==> 完成。请重启测试 ECS：ssh root@121.40.170.82 'systemctl restart xby-admin'"
echo "    dump 文件保留：${DUMP_FILE}"
