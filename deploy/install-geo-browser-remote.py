#!/usr/bin/env python3
"""在测试 ECS 上安装 Chrome + Xvfb，并让 xby-admin 以有界面浏览器跑 GEO 采集。密码只从 ECS_PASS 读取。"""
from __future__ import annotations

import os
import sys
from pathlib import Path

import paramiko

HOST = os.environ.get("ECS_HOST", "47.117.132.161")
USER = os.environ.get("ECS_USER", "root")
PASSWORD = os.environ.get("ECS_PASS", "")
ROOT = Path(__file__).resolve().parents[1]


def connect() -> paramiko.SSHClient:
    if not PASSWORD:
        raise SystemExit("缺少环境变量 ECS_PASS")
    print(f"connecting {USER}@{HOST}", flush=True)
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(
        hostname=HOST,
        username=USER,
        password=PASSWORD,
        timeout=20,
        banner_timeout=20,
        auth_timeout=20,
        allow_agent=False,
        look_for_keys=False,
    )
    print("connected", flush=True)
    return c


def run(c: paramiko.SSHClient, cmd: str, timeout: int = 900) -> int:
    print(f"$ {cmd}", flush=True)
    _, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.rstrip())
    if err.strip():
        print(err.rstrip(), file=sys.stderr)
    if code != 0:
        raise RuntimeError(f"remote exit {code}: {cmd}")
    return code


def main() -> int:
    c = connect()
    try:
        run(c, "uname -a")
        with c.open_sftp() as sftp:
            sftp.put(str(ROOT / "deploy" / "bootstrap-geo-browser-ecs.sh"), "/tmp/bootstrap-geo-browser-ecs.sh")
            sftp.put(str(ROOT / "deploy" / "xvfb-geo.service"), "/tmp/xvfb-geo.service")
        run(c, "sed -i 's/\\r$//' /tmp/bootstrap-geo-browser-ecs.sh /tmp/xvfb-geo.service; chmod +x /tmp/bootstrap-geo-browser-ecs.sh")
        run(c, "bash /tmp/bootstrap-geo-browser-ecs.sh /tmp/xvfb-geo.service", timeout=1200)
        run(c, "mkdir -p /opt/xby-admin/uploads/geo/auto-monitor/state /opt/xby-admin/config")
        run(c, "systemctl restart xby-admin; sleep 6; systemctl is-active xvfb-geo; systemctl is-active xby-admin")
        run(
            c,
            "systemctl show xby-admin -p Environment --no-pager; "
            "ls -la /usr/bin/google-chrome-stable /opt/xby-admin/uploads/geo/auto-monitor/state 2>/dev/null || true",
            timeout=60,
        )
        print("==> DONE")
    finally:
        c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
