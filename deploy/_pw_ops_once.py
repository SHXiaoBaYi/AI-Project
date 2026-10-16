#!/usr/bin/env python3
"""一次性运维助手：密码 SSH → 装公钥 → bootstrap → 同步库 → 发布。勿把密码写进仓库。"""
from __future__ import annotations

import os
import posixpath
import sys
import time
from pathlib import Path

import paramiko

HOST = os.environ.get("ECS_HOST", "121.40.170.82")
USER = os.environ.get("ECS_USER", "root")
PASSWORD = os.environ["ECS_PASS"]
RDS_USER = os.environ.get("RDS_USER", "xiaobayi0099")
RDS_PASS = os.environ.get("RDS_PASS", PASSWORD)
ROOT = Path(__file__).resolve().parents[1]
KEY_PUB = Path.home() / ".ssh" / "xby-test-ecs.pub"
KEY_PRIV = Path.home() / ".ssh" / "xby-test-ecs"
REMOTE_DIR = "/opt/xby-admin"


def connect(password: str | None = None, pkey: paramiko.PKey | None = None) -> paramiko.SSHClient:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    kwargs = dict(hostname=HOST, username=USER, timeout=30, allow_agent=False, look_for_keys=False)
    if pkey is not None:
        kwargs["pkey"] = pkey
    else:
        kwargs["password"] = password
    c.connect(**kwargs)
    return c


def run(c: paramiko.SSHClient, cmd: str, check: bool = True, timeout: int = 600) -> tuple[int, str, str]:
    print(f"$ {cmd}")
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.rstrip())
    if err.strip():
        print(err.rstrip(), file=sys.stderr)
    if check and code != 0:
        raise RuntimeError(f"remote exit {code}: {cmd}")
    return code, out, err


def sftp_put(c: paramiko.SSHClient, local: Path, remote: str) -> None:
    print(f"put {local} -> {remote}")
    with c.open_sftp() as sftp:
        sftp.put(str(local), remote)


def install_pubkey(c: paramiko.SSHClient) -> None:
    pub = KEY_PUB.read_text(encoding="utf-8").strip() + "\n"
    run(c, "mkdir -p ~/.ssh && chmod 700 ~/.ssh")
    with c.open_sftp() as sftp:
        path = "/root/.ssh/authorized_keys"
        existing = ""
        try:
            with sftp.file(path, "r") as f:
                existing = f.read().decode("utf-8", "replace")
        except FileNotFoundError:
            existing = ""
        if pub.strip() not in existing:
            with sftp.file(path, "w") as f:
                f.write(existing.rstrip() + ("\n" if existing.strip() else "") + pub)
    run(c, "chmod 600 ~/.ssh/authorized_keys")
    run(
        c,
        "sed -i 's/^#\\?PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config; "
        "sed -i 's/^#\\?PubkeyAuthentication.*/PubkeyAuthentication yes/' /etc/ssh/sshd_config; "
        "systemctl reload sshd 2>/dev/null || systemctl reload ssh 2>/dev/null || true",
        check=False,
    )


def main() -> int:
    step = os.environ.get("OPS_STEP", "all")
    print(f"==> connect {USER}@{HOST} (step={step})")
    c = connect(password=PASSWORD)
    try:
        run(c, "uname -a; whoami")
        if step in ("all", "key"):
            print("==> install pubkey")
            install_pubkey(c)
    finally:
        c.close()

    # verify key login
    if not KEY_PRIV.exists():
        raise SystemExit(f"missing private key {KEY_PRIV}")
    pkey = paramiko.Ed25519Key.from_private_key_file(str(KEY_PRIV))
    print("==> verify key login")
    c = connect(pkey=pkey)
    try:
        run(c, "echo KEY_OK; whoami")

        if step in ("all", "bootstrap"):
            print("==> upload bootstrap files")
            for name in (
                "bootstrap-test-ecs.sh",
                "nginx-xby-admin.conf",
                "xby-admin-test.service",
                "sync-prod-to-test-on-ecs.sh",
            ):
                sftp_put(c, ROOT / "deploy" / name, f"/tmp/{name}")
            run(c, "sed -i 's/\\r$//' /tmp/*.sh; chmod +x /tmp/bootstrap-test-ecs.sh /tmp/sync-prod-to-test-on-ecs.sh")
            run(c, "bash /tmp/bootstrap-test-ecs.sh /tmp/nginx-xby-admin.conf /tmp/xby-admin-test.service", timeout=900)

        if step in ("all", "sync"):
            print("==> sync prod DB -> test DB")
            # create DB first with same creds; may fail if whitelist missing
            env = f"export RDS_USER={RDS_USER!s} RDS_PASS={RDS_PASS!s}"
            # escape for remote shell - use env file
            with c.open_sftp() as sftp:
                with sftp.file("/tmp/rds.env", "w") as f:
                    f.write(f"export RDS_USER='{RDS_USER}'\n")
                    f.write(f"export RDS_PASS='{RDS_PASS}'\n")
            run(
                c,
                "set -a; . /tmp/rds.env; set +a; bash /tmp/sync-prod-to-test-on-ecs.sh; shred -u /tmp/rds.env 2>/dev/null || rm -f /tmp/rds.env",
                timeout=3600,
            )

        if step in ("all", "publish"):
            jar = ROOT / "BackEnd" / "target" / "base-admin-1.0.0.jar"
            dist = ROOT / "FrontEnd" / "dist"
            if not jar.exists():
                raise SystemExit(f"missing jar {jar}")
            if not (dist / "index.html").exists():
                raise SystemExit(f"missing front dist {dist}")
            print("==> publish jar + html")
            run(c, f"mkdir -p {REMOTE_DIR}/html/shxby {REMOTE_DIR}/config {REMOTE_DIR}/uploads")
            sftp_put(c, jar, f"{REMOTE_DIR}/base-admin-1.0.0.jar.new")
            run(c, f"mv {REMOTE_DIR}/base-admin-1.0.0.jar.new {REMOTE_DIR}/base-admin-1.0.0.jar")
            # Vite base=/shxby/ → 静态资源落在 html/shxby/
            with c.open_sftp() as sftp:
                def ensure_dir(path: str) -> None:
                    parts = path.strip("/").split("/")
                    cur = ""
                    for p in parts:
                        cur += "/" + p
                        try:
                            sftp.stat(cur)
                        except FileNotFoundError:
                            sftp.mkdir(cur)

                for path in dist.rglob("*"):
                    if path.is_dir():
                        continue
                    rel = path.relative_to(dist).as_posix()
                    remote = f"{REMOTE_DIR}/html/shxby/{rel}"
                    ensure_dir(posixpath.dirname(remote))
                    print(f"put {rel}")
                    sftp.put(str(path), remote)
            run(c, "systemctl daemon-reload; systemctl enable xby-admin; systemctl restart xby-admin; sleep 4; systemctl is-active xby-admin")
            run(c, "curl -sS -o /dev/null -w 'local_api %{http_code}\\n' http://127.0.0.1:8080/api/auth/info || true", check=False)
            time.sleep(2)
            run(c, "journalctl -u xby-admin -n 40 --no-pager || true", check=False)

        print("==> DONE")
        print(f"打开 http://{HOST}/shxby/")
    finally:
        c.close()
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        raise
