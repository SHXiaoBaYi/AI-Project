#!/usr/bin/env bash
# 将本地已构建的前后端发布到 ECS（/opt/xby-admin）
# 用法：
#   ./deploy/publish-ecs.sh prod
#   ./deploy/publish-ecs.sh test
#   ECS_ENV=test ./deploy/publish-ecs.sh
# 测试也可：./deploy/publish-ecs-test.sh
# 可选：ECS_USER  ECS_DIR  ECS_IDENTITY  SSHPASS
#
# 生产会对比上次发布 SHA：若有表结构 / 初始化数据 / 默认值回填，重启后必须等
# SchemaMigrator 跑完（.schema-migrate-ok / PROD_RELEASE_READY）才算成功。
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
RELEASE_FILE="$ECS_DIR/.prod-release"
LOCAL_LAST="$ROOT/deploy/.last-prod-commit"
DELTA_REPORT="$ROOT/deploy/.prod-delta.report"
CURRENT_SHA="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"

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

read_kv() {
  local key="$1" file="$2"
  awk -F= -v k="$key" '$1==k {print $2; exit}' "$file" 2>/dev/null || true
}

echo "==> 环境 $ENV_NAME  目标 $REMOTE:$ECS_DIR"

NEED_APPLY=0
if [[ "$ENV_NAME" == "prod" ]]; then
  LAST_SHA=""
  if remote_rel="$(ssh_run "test -f '$RELEASE_FILE' && cat '$RELEASE_FILE'" 2>/dev/null)"; then
    LAST_SHA="$(printf '%s\n' "$remote_rel" | awk -F= '$1=="SHA" {print $2; exit}')"
  fi
  if [[ -z "$LAST_SHA" && -f "$LOCAL_LAST" ]]; then
    LAST_SHA="$(read_kv SHA "$LOCAL_LAST")"
    if [[ -z "$LAST_SHA" ]]; then
      LAST_SHA="$(head -n 1 "$LOCAL_LAST" | tr -d '[:space:]')"
    fi
  fi
  echo "==> 对照上次生产发布 ${LAST_SHA:-（无基线）} → $CURRENT_SHA"
  set +e
  LAST_PROD_SHA="$LAST_SHA" DELTA_REPORT="$DELTA_REPORT" bash "$ROOT/deploy/detect-prod-delta.sh"
  DETECT_RC=$?
  set -e
  if [[ "$DETECT_RC" -eq 2 ]]; then
    echo "存在未挂到 SchemaMigrator 的 SQL，已中止生产发布。" >&2
    exit 2
  fi
  if [[ "$DETECT_RC" -ne 0 ]]; then
    echo "生产变更检测失败（exit $DETECT_RC）" >&2
    exit 1
  fi
  NEED_APPLY="$(read_kv NEED_APPLY "$DELTA_REPORT")"
  NEED_APPLY="${NEED_APPLY:-0}"
  if [[ "$NEED_APPLY" == "1" ]]; then
    echo "==> 本次有表结构/初始化数据/默认值逻辑，重启后必须等 PROD_RELEASE_READY"
  else
    echo "==> 相对上次生产无 schema/init/default 增量，仍会启动幂等 Migrator"
  fi
fi

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

if [[ "$ENV_NAME" == "prod" ]]; then
  echo "==> 清除上次 schema 完成标记"
  ssh_run "rm -f '$ECS_DIR/.schema-migrate-ok'"
fi

echo "==> reload nginx + 重启后端"
ssh_run 'systemctl daemon-reload; nginx -t && nginx -s reload && systemctl restart --no-block xby-admin && echo RESTART_ISSUED'

if [[ "$ENV_NAME" == "prod" ]]; then
  echo "==> 等待生产 SchemaMigrator / 初始化 / 默认值回填"
  READY=0
  i=0
  while [[ "$i" -lt 24 ]]; do
    i=$((i + 1))
    sleep 5
    if ssh_run "test -f '$ECS_DIR/.schema-migrate-ok'" >/dev/null 2>&1; then
      echo "PROD_RELEASE_READY 标记已写入"
      READY=1
      break
    fi
    # 只看本轮重启后的日志，避免崩溃重试留下的旧 "schema migrate failed" 误判
    logs="$(ssh_run "journalctl -u xby-admin --since '45 sec ago' --no-pager -o cat" 2>/dev/null || true)"
    if printf '%s\n' "$logs" | grep -q 'schema migrate failed'; then
      # 若随后已出现成功标记或 READY，说明新进程已修好，不立刻失败
      if ! ssh_run "test -f '$ECS_DIR/.schema-migrate-ok'" >/dev/null 2>&1 \
        && ! printf '%s\n' "$logs" | grep -q 'PROD_RELEASE_READY'; then
        echo "SchemaMigrator 失败，见 journalctl -u xby-admin" >&2
        printf '%s\n' "$logs" | grep -E 'schema migrate failed|ERROR' | tail -n 20 >&2
        exit 1
      fi
    fi
    if printf '%s\n' "$logs" | grep -q 'PROD_RELEASE_READY'; then
      echo "日志已出现 PROD_RELEASE_READY"
      READY=1
      break
    fi
    echo "  …等待 Migrator ($i/24)"
  done
  if [[ "$READY" -ne 1 ]]; then
    echo "等待 PROD_RELEASE_READY 超时。生产表结构/初始化可能未跑完，请查 journalctl -u xby-admin" >&2
    ssh_run "systemctl is-active xby-admin; journalctl -u xby-admin -n 40 --no-pager -o cat" || true
    exit 1
  fi

  {
    echo "SHA=$CURRENT_SHA"
    echo "TIME=$(date +%Y-%m-%dT%H:%M:%S%z)"
    echo "NEED_APPLY=$NEED_APPLY"
  } >"$LOCAL_LAST.tmp"
  scp_file "$LOCAL_LAST.tmp" "$REMOTE:$RELEASE_FILE"
  mv "$LOCAL_LAST.tmp" "$LOCAL_LAST"
  echo "==> 已记录生产基线 $CURRENT_SHA"
fi

echo "UPLOAD_DONE  http://${ECS_HOST}/shxby/"
echo "请轮询 :8080 与 /shxby/、/shxby/api/auth/login 直至 200"
