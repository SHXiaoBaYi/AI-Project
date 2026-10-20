#!/usr/bin/env python3
"""Upload jar to prod and restart. Password via ECS_PASS env. Do not print password."""
from __future__ import annotations

import os
import sys
import time
from pathlib import Path

import paramiko

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "BackEnd" / "target" / "base-admin-1.0.0.jar"
UNIT = ROOT / "deploy" / "xby-admin.service"
HOST = os.environ.get("ECS_HOST", "121.40.119.134")
USER = os.environ.get("ECS_USER", "root")
PASSWORD = os.environ["ECS_PASS"]
SHA = os.popen(f'git -C "{ROOT}" rev-parse HEAD').read().strip() or "unknown"


def connect() -> paramiko.SSHClient:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASSWORD, timeout=40, allow_agent=False, look_for_keys=False)
    return c


def run(c: paramiko.SSHClient, cmd: str, timeout: int = 120) -> tuple[int, str]:
    print(f"$ {cmd}")
    _, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    text = (out + err).strip()
    if text:
        print(text[-2000:])
    return code, text


def main() -> int:
    if not JAR.is_file():
        print("missing jar", file=sys.stderr)
        return 1
    print(f"==> upload {JAR.name} ({JAR.stat().st_size} bytes) to {HOST} SHA={SHA}")
    c = connect()
    try:
        sftp = c.open_sftp()
        sftp.put(str(JAR), "/tmp/base-admin-1.0.0.jar")
        sftp.put(str(UNIT), "/tmp/xby-admin.service")
        sftp.close()
        print("==> uploaded")
        run(
            c,
            r"""
set -e
install -m 644 /tmp/base-admin-1.0.0.jar /opt/xby-admin/base-admin-1.0.0.jar
install -m 644 /tmp/xby-admin.service /etc/systemd/system/xby-admin.service
mkdir -p /opt/xby-admin/config /etc/systemd/system/xby-admin.service.d
if [ ! -f /opt/xby-admin/config/application.yml ]; then
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
rm -f /opt/xby-admin/.schema-migrate-ok /tmp/base-admin-1.0.0.jar /tmp/xby-admin.service
systemctl daemon-reload
systemctl restart xby-admin
echo RESTART_ISSUED
""",
            timeout=60,
        )
        ready = False
        for i in range(1, 25):
            time.sleep(5)
            code, text = run(
                c,
                "systemctl is-active xby-admin; "
                "test -f /opt/xby-admin/.schema-migrate-ok && echo MIGRATE_OK; "
                "journalctl -u xby-admin --since '3 min ago' --no-pager | "
                "grep -iE 'PROD_RELEASE_READY|schema migrate failed|Stream|Started BaseAdmin' | tail -12",
                timeout=30,
            )
            print(f"[{i}]")
            if "schema migrate failed" in text:
                return 2
            if "active" in text and (
                "MIGRATE_OK" in text or "PROD_RELEASE_READY" in text or "Stream" in text or "Started BaseAdmin" in text
            ):
                ready = True
                break
        if not ready:
            print("TIMEOUT waiting for prod")
            return 3
        run(
            c,
            f"printf 'SHA={SHA}\\nTIME=%(%Y-%m-%dT%H:%M:%S%z)T\\nNEED_APPLY=0\\n' > /opt/xby-admin/.prod-release; "
            "curl -m 8 -s -o /dev/null -w 'shxby=%{http_code}\\n' http://127.0.0.1/shxby/; "
            "journalctl -u xby-admin -n 20 --no-pager | grep -iE 'Stream|Started BaseAdmin|GRANT|resume' | tail -10",
            timeout=30,
        )
        local = ROOT / "deploy" / ".last-prod-commit"
        local.write_text(f"SHA={SHA}\nNEED_APPLY=0\n", encoding="utf-8")
        print("PROD_OK")
        return 0
    finally:
        c.close()


if __name__ == "__main__":
    raise SystemExit(main())
