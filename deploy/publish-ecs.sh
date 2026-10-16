#!/usr/bin/env bash
# 将本地已构建的前后端发布到 ECS（/opt/xby-admin）
# 用法：
#   ./deploy/publish-ecs.sh prod
#   ./deploy/publish-ecs.sh test
#   ECS_ENV=test ./deploy/publish-ecs.sh
# 测试也可：./deploy/publish-ecs-test.sh
# 可选：ECS_USER  ECS_DIR  ECS_IDENTITY  SSHPASS
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_NAME="${1:-${ECS_ENV:-}}"
if [[ -z "$ENV_NAME" ]]; then
  echo "必须指定环境：test 或 prod。例：./deploy/publish-ecs.sh test" >&2
  exit 1
fi
case "$ENV_NAME" in
  test|prod) ;;
  *) echo "环境必须是 test 或 prod，收到: $ENV_NAME" >&2; exit 1 ;;
esac

if [[ "$ENV_NAME" == "test" ]]; then
  ECS_HOST="${ECS_HOST:-121.40.170.82}"
  UNIT_SRC="$ROOT/deploy/xby-admin-test.service"
else
  ECS_HOST="${ECS_HOST:-121.40.119.134}"
  UNIT_SRC="$ROOT/deploy/xby-admin.service"
fi

ECS_USER="${ECS_USER:-root}"
ECS_DIR="${ECS_DIR:-/opt/xby-admin}"
NGINX_SRC="$ROOT/deploy/nginx-xby-admin.conf"
JAR="$ROOT/BackEnd/target/base-admin-1.0.0.jar"
DIST="$ROOT/FrontEnd/dist"

if [[ ! -f "$JAR" ]]; then
  echo "缺少 jar: $JAR （请先 mvn -DskipTests package）" >&2
  exit 1
fi
if [[ ! -f "$DIST/index.html" ]]; then
  echo "缺少前端 dist: $DIST （请先 VITE_BASE=/shxby/ 构建）" >&2
  exit 1
fi
if [[ ! -f "$UNIT_SRC" ]]; then
  echo "缺少 systemd 单元: $UNIT_SRC" >&2
  exit 1
fi

REMOTE="${ECS_USER}@${ECS_HOST}"
SSH_OPTS=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=20 -o ServerAliveInterval=5 -o ServerAliveCountMax=2)
if [[ -n "${ECS_IDENTITY:-}" ]]; then
  SSH_OPTS+=(-o IdentitiesOnly=yes -i "$ECS_IDENTITY")
elif [[ -n "${SSHPASS:-}" ]]; then
  SSH_OPTS+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no)
else
  SSH_OPTS+=(-o BatchMode=yes)
fi

ssh_run() {
  if [[ -n "${SSHPASS:-}" ]] && command -v sshpass >/dev/null 2>&1 && [[ -z "${ECS_IDENTITY:-}" ]]; then
    sshpass -e ssh "${SSH_OPTS[@]}" "$REMOTE" "$@"
  else
    ssh "${SSH_OPTS[@]}" "$REMOTE" "$@"
  fi
}

scp_file() {
  local src="$1" dest="$2"
  if [[ -n "${SSHPASS:-}" ]] && command -v sshpass >/dev/null 2>&1 && [[ -z "${ECS_IDENTITY:-}" ]]; then
    sshpass -e scp "${SSH_OPTS[@]}" "$src" "$dest"
  else
    scp "${SSH_OPTS[@]}" "$src" "$dest"
  fi
}

echo "==> 环境 $ENV_NAME  目标 $REMOTE:$ECS_DIR"

echo "==> 上传后端 jar"
scp_file "$JAR" "$REMOTE:$ECS_DIR/base-admin-1.0.0.jar"

echo "==> 上传 nginx / systemd"
scp_file "$NGINX_SRC" "$REMOTE:/etc/nginx/conf.d/xby-admin.conf"
scp_file "$UNIT_SRC" "$REMOTE:/etc/systemd/system/xby-admin.service"

echo "==> 准备前端目录"
ssh_run "mkdir -p '$ECS_DIR/html/shxby' && rm -rf '$ECS_DIR/html/shxby/'*"

echo "==> 上传前端（tar-pipe）"
if [[ -n "${SSHPASS:-}" ]] && command -v sshpass >/dev/null 2>&1 && [[ -z "${ECS_IDENTITY:-}" ]]; then
  COPYFILE_DISABLE=1 tar --disable-copyfile --no-xattrs -C "$DIST" -czf - . \
    | sshpass -e ssh "${SSH_OPTS[@]}" "$REMOTE" "tar -C '$ECS_DIR/html/shxby' -xzf -"
else
  COPYFILE_DISABLE=1 tar --disable-copyfile --no-xattrs -C "$DIST" -czf - . \
    | ssh "${SSH_OPTS[@]}" "$REMOTE" "tar -C '$ECS_DIR/html/shxby' -xzf -"
fi

echo "==> reload nginx + 重启后端"
ssh_run 'systemctl daemon-reload; nginx -t && nginx -s reload && systemctl restart --no-block xby-admin && echo RESTART_ISSUED'

echo "UPLOAD_DONE  http://${ECS_HOST}/shxby/"
echo "请轮询 :8080 与 /shxby/、/shxby/api/auth/login 直至 200"
