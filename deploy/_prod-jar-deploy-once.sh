#!/usr/bin/env bash
# one-shot: upload backend jar to prod via test jump, restart, verify
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEY_TEST="${HOME}/.ssh/xby-test-ecs"
JAR="$ROOT/BackEnd/target/base-admin-1.0.0.jar"
UNIT="$ROOT/deploy/xby-admin.service"
test -f "$JAR"
test -f "$KEY_TEST"

SHA="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
echo "==> stage jar to test then prod SHA=$SHA"

scp -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY_TEST" \
  "$JAR" "$UNIT" root@121.40.170.82:/tmp/

ssh -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY_TEST" root@121.40.170.82 bash -s <<EOS
set -euo pipefail
export SSHPASS='Xby@123456'
SCP=(sshpass -e scp -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=30)
SSH=(sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=30)
"\${SCP[@]}" /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service root@121.40.119.134:/tmp/
"\${SSH[@]}" root@121.40.119.134 bash -s <<'PROD'
set -euo pipefail
install -m 644 /tmp/base-admin-1.0.0.jar /opt/xby-admin/base-admin-1.0.0.jar
install -m 644 /tmp/xby-admin.service /etc/systemd/system/xby-admin.service
# keep H5 pin if present
mkdir -p /opt/xby-admin/config /etc/systemd/system/xby-admin.service.d
if [[ ! -f /opt/xby-admin/config/application.yml ]]; then
  cat > /opt/xby-admin/config/application.yml <<'CFG'
dingtalk:
  h5-base-url: http://121.40.119.134/shxby
  robot:
    stream-enabled: true
xby:
  upload-public-base: http://121.40.119.134/shxby/api
CFG
fi
cat > /etc/systemd/system/xby-admin.service.d/h5.conf <<'DROP'
[Service]
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DINGTALK_H5_BASE_URL=http://121.40.119.134/shxby
Environment=UPLOAD_PUBLIC_BASE=http://121.40.119.134/shxby/api
Environment=DINGTALK_ROBOT_STREAM_ENABLED=true
DROP
rm -f /opt/xby-admin/.schema-migrate-ok
systemctl daemon-reload
systemctl restart xby-admin
echo RESTART_ISSUED
PROD
rm -f /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service
EOS

echo "==> wait prod ready"
for i in $(seq 1 24); do
  sleep 5
  out="$(ssh -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY_TEST" root@121.40.170.82 bash -s <<'EOS'
export SSHPASS='Xby@123456'
sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=15 root@121.40.119.134 \
  'systemctl is-active xby-admin; test -f /opt/xby-admin/.schema-migrate-ok && echo MIGRATE_OK; journalctl -u xby-admin --since "3 min ago" --no-pager | grep -iE "PROD_RELEASE_READY|schema migrate failed|Stream 已连接|Started BaseAdmin" | tail -8'
EOS
)"
  echo "[$i] $out"
  if echo "$out" | grep -q 'schema migrate failed'; then
    echo FAIL_MIGRATE
    exit 1
  fi
  if echo "$out" | grep -qE 'MIGRATE_OK|PROD_RELEASE_READY|Stream 已连接'; then
    if echo "$out" | grep -q active; then
      echo PROD_OK
      # record release
      ssh -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY_TEST" root@121.40.170.82 bash -s <<EOS
export SSHPASS='Xby@123456'
sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no root@121.40.119.134 \
  "printf 'SHA=$SHA\\nTIME=%s\\nNEED_APPLY=0\\n' \"\$(date +%Y-%m-%dT%H:%M:%S%z)\" > /opt/xby-admin/.prod-release"
EOS
      printf 'SHA=%s\nTIME=%s\nNEED_APPLY=0\n' "$SHA" "$(date +%Y-%m-%dT%H:%M:%S%z)" > "$ROOT/deploy/.last-prod-commit"
      exit 0
    fi
  fi
done
echo TIMEOUT
exit 1
