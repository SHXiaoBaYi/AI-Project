#!/usr/bin/env bash
# 一次性：旧生产（121.40.119.134 / 旧 RDS）只读同步到新生产。
# 由 xby-prod-final-sync.timer 在 2026-11-10 23:59:59（Asia/Shanghai）触发。
# 数据库账号口令放在 /root/.my-old-prod.cnf 与 /root/.my-new-prod.cnf，不写进本脚本。
set -euo pipefail

LOG=/var/log/xby-prod-final-sync.log
exec >>"$LOG" 2>&1

echo "===== START $(date '+%F %T %z') ====="

OLD_RDS_HOST=121.41.254.111
NEW_RDS_HOST=rm-uf6l17zg54sh9378v.mysql.rds.aliyuncs.com
OLD_ECS=root@121.40.119.134
KEY=/root/.ssh/xby-old-prod-sync
DB=base_admin
DUMP=/root/base_admin_final_sync.sql
INCOMING=/opt/xby-admin/uploads.incoming
OLD_CNF=/root/.my-old-prod.cnf
NEW_CNF=/root/.my-new-prod.cnf

start_app() {
  systemctl start xby-admin || echo "WARN start xby-admin failed"
}
trap start_app EXIT

echo "==> stop xby-admin"
systemctl stop xby-admin || true

echo "==> dump old prod ${OLD_RDS_HOST}/${DB}"
mysqldump --defaults-extra-file="$OLD_CNF" -h "$OLD_RDS_HOST" -P 3306 \
  --single-transaction --routines --triggers --hex-blob --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  "$DB" >"$DUMP"
ls -lh "$DUMP"

echo "==> import new prod ${NEW_RDS_HOST}/${DB}"
mysql --defaults-extra-file="$NEW_CNF" -h "$NEW_RDS_HOST" -P 3306 \
  -e "CREATE DATABASE IF NOT EXISTS \`${DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql --defaults-extra-file="$NEW_CNF" -h "$NEW_RDS_HOST" -P 3306 "$DB" <"$DUMP"

echo -n "old_tables "
mysql --defaults-extra-file="$OLD_CNF" -h "$OLD_RDS_HOST" -P 3306 -N \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB}' AND table_type='BASE TABLE';"
echo -n "new_tables "
mysql --defaults-extra-file="$NEW_CNF" -h "$NEW_RDS_HOST" -P 3306 -N \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB}' AND table_type='BASE TABLE';"
echo -n "old_sys_user "
mysql --defaults-extra-file="$OLD_CNF" -h "$OLD_RDS_HOST" -P 3306 -N "$DB" -e "SELECT COUNT(*) FROM sys_user;"
echo -n "new_sys_user "
mysql --defaults-extra-file="$NEW_CNF" -h "$NEW_RDS_HOST" -P 3306 -N "$DB" -e "SELECT COUNT(*) FROM sys_user;"

echo "==> sync uploads from ${OLD_ECS}"
rm -rf "$INCOMING"
mkdir -p "$INCOMING"
ssh -i "$KEY" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=no -o ConnectTimeout=20 \
  "$OLD_ECS" 'tar -C /opt/xby-admin -czf - uploads' \
  | tar -C "$INCOMING" -xzf -
if [[ ! -d "$INCOMING/uploads" ]]; then
  echo "uploads archive missing" >&2
  exit 1
fi
rm -rf /opt/xby-admin/uploads.prev
if [[ -d /opt/xby-admin/uploads ]]; then
  mv /opt/xby-admin/uploads /opt/xby-admin/uploads.prev
fi
mv "$INCOMING/uploads" /opt/xby-admin/uploads
rm -rf "$INCOMING"
du -sh /opt/xby-admin/uploads
rm -f "$DUMP"

echo "===== DONE $(date '+%F %T %z') ====="
systemctl disable --now xby-prod-final-sync.timer || true
