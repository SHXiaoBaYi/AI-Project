#!/usr/bin/env bash
set -euo pipefail
KEY="${HOME}/.ssh/xby-test-ecs"
SHA="$(git -C /d/AI-XBY/AI-Project rev-parse HEAD 2>/dev/null || echo unknown)"
echo "==> push jar test->prod SHA=$SHA"
ssh -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY" root@121.40.170.82 bash -s <<'EOS'
set -euo pipefail
export SSHPASS='Xby@123456'
ls -lh /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service
sshpass -e scp -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=60 \
  /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service root@121.40.119.134:/tmp/
echo SCP_PROD_OK
sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=20 root@121.40.119.134 'bash -s' <<'PROD'
set -euo pipefail
install -m 644 /tmp/base-admin-1.0.0.jar /opt/xby-admin/base-admin-1.0.0.jar
install -m 644 /tmp/xby-admin.service /etc/systemd/system/xby-admin.service
mkdir -p /opt/xby-admin/config /etc/systemd/system/xby-admin.service.d
cat > /etc/systemd/system/xby-admin.service.d/h5.conf <<'DROP'
[Service]
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DINGTALK_H5_BASE_URL=http://121.40.119.134/shxby
Environment=UPLOAD_PUBLIC_BASE=http://121.40.119.134/shxby/api
Environment=DINGTALK_ROBOT_STREAM_ENABLED=true
DROP
rm -f /opt/xby-admin/.schema-migrate-ok /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service
systemctl daemon-reload
systemctl restart xby-admin
echo RESTART_ISSUED
PROD
rm -f /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service
EOS
echo WAIT_READY
for i in $(seq 1 24); do
  sleep 5
  out="$(ssh -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY" root@121.40.170.82 bash -s <<'EOS'
export SSHPASS='Xby@123456'
sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -o ConnectTimeout=15 root@121.40.119.134 \
  "systemctl is-active xby-admin; test -f /opt/xby-admin/.schema-migrate-ok && echo MIGRATE_OK; journalctl -u xby-admin --since '4 min ago' --no-pager | grep -iE 'PROD_RELEASE_READY|schema migrate failed|Stream|Started BaseAdmin' | tail -10"
EOS
)"
  echo "poll $i: $out"
  if echo "$out" | grep -q 'schema migrate failed'; then
    echo FAIL_MIGRATE
    exit 1
  fi
  if echo "$out" | grep -q active && echo "$out" | grep -qE 'MIGRATE_OK|PROD_RELEASE_READY|Stream|Started BaseAdmin'; then
    ssh -o StrictHostKeyChecking=no -o BatchMode=yes -i "$KEY" root@121.40.170.82 bash -s <<EOS
export SSHPASS='Xby@123456'
sshpass -e ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no root@121.40.119.134 \
  "echo SHA=$SHA > /opt/xby-admin/.prod-release; date +TIME=%Y-%m-%dT%H:%M:%S%z >> /opt/xby-admin/.prod-release; curl -m 8 -s -o /dev/null -w shxby=%{http_code} http://127.0.0.1/shxby/; echo; journalctl -u xby-admin -n 15 --no-pager | grep -iE 'Stream|Started BaseAdmin' | tail -5"
EOS
    echo "SHA=$SHA" > /d/AI-XBY/AI-Project/deploy/.last-prod-commit
    echo PROD_OK
    exit 0
  fi
done
echo TIMEOUT
exit 1
