#!/usr/bin/env bash
# 在「测试 ECS」上执行：从生产 RDS 外网 dump → 导入本 VPC 测试 RDS 内网。
# 前置：
#   1) 生产 RDS 白名单已加入「测试 ECS 公网 IP」121.40.170.82
#   2) 测试 RDS 已建库 base_admin，账号可写
#   3) 本机已装 mysql / mysqldump
#
# 用法：
#   export RDS_USER=xiaobayi0099 RDS_PASS='***'
#   bash /opt/xby-admin/sync-prod-to-test-on-ecs.sh
set -euo pipefail

PROD_HOST="${PROD_RDS_HOST:-rm-bp18jx12jxs1884h9so.mysql.rds.aliyuncs.com}"
# 测试走内网（公网域名尚未申请时只有内网可解析）
TEST_HOST="${TEST_RDS_HOST:-rm-bp15f2a3k5b93293k.mysql.rds.aliyuncs.com}"
DB_NAME="${DB_NAME:-base_admin}"
RDS_USER="${RDS_USER:?请设置 RDS_USER}"
RDS_PASS="${RDS_PASS:?请设置 RDS_PASS}"
DUMP_DIR="${DUMP_DIR:-/tmp}"
STAMP="$(date +%Y%m%d_%H%M%S)"
DUMP_FILE="${DUMP_DIR}/base_admin_prod_${STAMP}.sql"

echo "==> dump 生产(外网) ${PROD_HOST}/${DB_NAME}"
mysqldump \
  -h "$PROD_HOST" -P 3306 -u "$RDS_USER" -p"$RDS_PASS" \
  --single-transaction --routines --triggers --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  "$DB_NAME" > "$DUMP_FILE"

echo "==> 确保测试库存在"
mysql -h "$TEST_HOST" -P 3306 -u "$RDS_USER" -p"$RDS_PASS" \
  -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "==> 导入测试(内网) ${TEST_HOST}/${DB_NAME}"
mysql -h "$TEST_HOST" -P 3306 -u "$RDS_USER" -p"$RDS_PASS" \
  --default-character-set=utf8mb4 \
  "$DB_NAME" < "$DUMP_FILE"

echo "==> 完成。重启应用：systemctl restart xby-admin"
echo "    dump: ${DUMP_FILE}"
