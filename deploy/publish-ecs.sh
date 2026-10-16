#!/usr/bin/env bash
# 将本地已构建的前后端发布到「生产」ECS（/opt/xby-admin）
# 用法：
#   ./deploy/publish-ecs.sh
#   ECS_HOST=121.40.119.134 ./deploy/publish-ecs.sh
# 测试环境请用：./deploy/publish-ecs-test.sh
# 可选：ECS_USER=root  ECS_DIR=/opt/xby-admin
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ECS_HOST="${ECS_HOST:-121.40.119.134}"
ECS_USER="${ECS_USER:-root}"
ECS_DIR="${ECS_DIR:-/opt/xby-admin}"
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

REMOTE="${ECS_USER}@${ECS_HOST}"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=15 -o StrictHostKeyChecking=accept-new)
if [[ -n "${ECS_IDENTITY:-}" ]]; then
  SSH_OPTS+=(-o IdentitiesOnly=yes -i "$ECS_IDENTITY")
fi
echo "==> 目标 $REMOTE:$ECS_DIR"

ssh "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$ECS_DIR/html' '$ECS_DIR/config'"

echo "==> 上传后端 jar"
scp "${SSH_OPTS[@]}" "$JAR" "$REMOTE:$ECS_DIR/base-admin-1.0.0.jar.new"
ssh "${SSH_OPTS[@]}" "$REMOTE" "mv '$ECS_DIR/base-admin-1.0.0.jar.new' '$ECS_DIR/base-admin-1.0.0.jar'"

echo "==> 同步前端静态资源（VITE_BASE=/shxby/ → html/shxby/）"
ssh "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$ECS_DIR/html/shxby'"
RSYNC_SSH="ssh ${SSH_OPTS[*]}"
rsync -avz --delete -e "$RSYNC_SSH" \
  --exclude='.DS_Store' \
  "$DIST/" "$REMOTE:$ECS_DIR/html/shxby/"

echo "==> 重启后端服务"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'systemctl restart xby-admin && sleep 2 && systemctl is-active xby-admin'

echo "==> 健康检查"
ssh "${SSH_OPTS[@]}" "$REMOTE" "curl -sS -o /dev/null -w 'local_api %{http_code}\n' http://127.0.0.1:8080/api/auth/info || true"
curl -sS -o /dev/null -w "public_fe %{http_code}\n" "http://${ECS_HOST}/shxby/" || true
curl -sS -o /dev/null -w "public_api %{http_code}\n" "http://${ECS_HOST}/shxby/api/auth/info" || true

echo "发布完成：http://${ECS_HOST}/shxby/"
