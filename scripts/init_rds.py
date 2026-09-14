#!/usr/bin/env python3
"""Initialize base_admin database on Aliyun RDS from local env file."""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env.rds.local"
SCHEMA = ROOT / "BackEnd/src/main/resources/db/schema.sql"
FULL_DUMP = ROOT / "BackEnd/src/main/resources/db/base_admin.sql"


def load_env(path: Path) -> dict[str, str]:
    if not path.exists():
        raise SystemExit(f"Missing env file: {path}")
    data: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def split_sql(sql: str) -> list[str]:
    # Remove CREATE DATABASE / USE so we run inside selected DB
    sql = re.sub(r"(?im)^\s*CREATE\s+DATABASE\b.*?;\s*", "", sql)
    sql = re.sub(r"(?im)^\s*USE\s+\w+\s*;\s*", "", sql)
    statements: list[str] = []
    buff: list[str] = []
    for line in sql.splitlines():
        if line.strip().startswith("--"):
            continue
        buff.append(line)
        if line.rstrip().endswith(";"):
            stmt = "\n".join(buff).strip()
            buff = []
            if stmt and stmt != ";":
                statements.append(stmt)
    if buff:
        stmt = "\n".join(buff).strip()
        if stmt:
            statements.append(stmt)
    return statements


def main() -> None:
    env = load_env(ENV_FILE)
    host = env["RDS_HOST"]
    port = int(env.get("RDS_PORT", "3306"))
    user = env["RDS_USER"]
    password = env["RDS_PASSWORD"]
    database = env.get("RDS_DATABASE", "base_admin")

    print(f"Connecting to {host}:{port} as {user} ...")
    conn = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=password,
        charset="utf8mb4",
        autocommit=True,
        connect_timeout=20,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                f"CREATE DATABASE IF NOT EXISTS `{database}` "
                "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
            )
            print(f"Database `{database}` ready.")
            cur.execute(f"USE `{database}`")

            # Prefer full dump with menus if present
            sql_path = FULL_DUMP if FULL_DUMP.exists() else SCHEMA
            print(f"Importing {sql_path.name} ...")
            sql = sql_path.read_text(encoding="utf-8")
            statements = split_sql(sql)
            ok = 0
            for stmt in statements:
                try:
                    cur.execute(stmt)
                    ok += 1
                except Exception as exc:  # noqa: BLE001
                    preview = stmt[:120].replace("\n", " ")
                    print(f"WARN skip: {exc} | {preview}")
            print(f"Executed {ok}/{len(statements)} statements.")

            cur.execute("SHOW TABLES")
            tables = [r[0] for r in cur.fetchall()]
            print("Tables:", ", ".join(tables) if tables else "(none)")
            if "sys_user" in tables:
                cur.execute("SELECT user_id, username, nickname FROM sys_user LIMIT 5")
                rows = cur.fetchall()
                print("Users:", rows)
    finally:
        conn.close()
    print("RDS init done.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
