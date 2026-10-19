#!/usr/bin/env bash
# 对比「上次发布到生产」与当前代码，判断是否有：
#   SCHEMA  — 建表/加列/改列
#   DATA    — 菜单、字典、功能初始化数据
#   DEFAULT — 新列默认值、历史行回填
# 可选样例灌数（*Seeder / 显式开关 Runner）只告警，生产默认不执行。
# 新增 SQL 若未挂到 *SchemaMigrator 且不是「仅作说明」，视为未执行风险并失败。
#
# 用法：
#   ./deploy/detect-prod-delta.sh [上次生产 SHA]
#   LAST_PROD_SHA=abc ./deploy/detect-prod-delta.sh
# 退出码：0 正常；2 有未挂接 SQL；1 用法/仓库错误
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LAST_SHA="${1:-${LAST_PROD_SHA:-}}"
CURRENT_SHA="$(git rev-parse HEAD)"
REPORT="${DELTA_REPORT:-$ROOT/deploy/.prod-delta.report}"

WATCH=(
  "BackEnd/src/main/java/com/base/admin/config"
  "BackEnd/src/main/resources/db"
)

NO_BASELINE=0
if [[ -z "$LAST_SHA" ]]; then
  NO_BASELINE=1
elif ! git cat-file -e "${LAST_SHA}^{commit}" 2>/dev/null; then
  echo "本地没有基线提交 $LAST_SHA，按无基线处理（Migrator 仍会全部幂等执行）" >&2
  NO_BASELINE=1
  LAST_SHA=""
fi

collect_files() {
  if [[ "$NO_BASELINE" -eq 1 ]]; then
    # 无基线不扫全量文件（历史说明性 SQL 未挂 Migrator，会误杀）。
    # 只标记 NEED_APPLY，启动时全部幂等 Migrator 仍会跑。
    return
  fi
  git diff --name-only "$LAST_SHA" -- "${WATCH[@]}"
  git ls-files --others --exclude-standard -- "${WATCH[@]}"
}

is_optional_seed() {
  local f="$1" base
  base="$(basename "$f")"
  case "$base" in
    *Seeder.java|PlacementTaskSyncRunner.java|GeoDailyWorkbookIngestRunner.java) return 0 ;;
  esac
  return 1
}

is_schema_migrator() {
  local f="$1"
  [[ "$f" == *SchemaMigrator.java ]]
}

is_doc_only_sql() {
  local f="$1"
  local base
  base="$(basename "$f")"
  case "$base" in
    schema.sql|base_admin.sql|sys_user_online.sql) return 0 ;;
  esac
  if [[ "$f" == *.sql ]] && head -n 8 "$f" | grep -qE '仅作说明|由 .+ 自动补齐|由 Java migrator|幂等补齐|若启动迁移未生效'; then
    return 0
  fi
  return 1
}

sql_is_wired() {
  local f="$1" base
  base="$(basename "$f")"
  grep -R --include='*SchemaMigrator.java' -l "db/${base}" \
    "BackEnd/src/main/java/com/base/admin/config" >/dev/null 2>&1
}

blob_for() {
  local f="$1"
  if [[ "$NO_BASELINE" -eq 1 ]]; then
    cat "$f" 2>/dev/null || true
  else
    git diff "$LAST_SHA" -- "$f" 2>/dev/null || cat "$f" 2>/dev/null || true
  fi
}

NEED_SCHEMA=0
NEED_DATA=0
NEED_DEFAULT=0
OPTIONAL_SEED=0
UNWIRED=0

TMP_FILES="$(mktemp)"
TMP_LIST="$(mktemp)"
TMP_OPT="$(mktemp)"
TMP_UNWIRED="$(mktemp)"
trap 'rm -f "$TMP_FILES" "$TMP_LIST" "$TMP_OPT" "$TMP_UNWIRED"' EXIT
collect_files | sed '/^$/d' | sort -u >"$TMP_FILES"

while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  [[ -f "$f" ]] || continue
  case "$f" in
    *.java|*.sql) ;;
    *) continue ;;
  esac
  # 环境守卫、监听器等与表结构无关
  case "$(basename "$f")" in
    RdsEnvironmentGuard.java|DingTalkStreamListener.java|GeoBoardPersistScheduler.java)
      continue
      ;;
  esac

  if is_optional_seed "$f"; then
    OPTIONAL_SEED=1
    echo "$f" >>"$TMP_OPT"
    echo "[OPTIONAL_SEED] $f" >>"$TMP_LIST"
    continue
  fi

  if [[ "$f" == *.sql ]] && is_doc_only_sql "$f"; then
    echo "[DOC] $f" >>"$TMP_LIST"
    continue
  fi

  if [[ "$f" == *.sql ]] && ! sql_is_wired "$f"; then
    UNWIRED=1
    echo "$f" >>"$TMP_UNWIRED"
    echo "[UNWIRED] $f" >>"$TMP_LIST"
    continue
  fi

  blob="$(blob_for "$f")"
  tags=""
  if echo "$blob" | grep -qiE 'CREATE TABLE|ALTER TABLE|ADD COLUMN|DROP COLUMN|MODIFY COLUMN|CHANGE COLUMN|ADD INDEX|ADD KEY|ADD UNIQUE'; then
    NEED_SCHEMA=1
    tags="${tags}[SCHEMA]"
  fi
  if echo "$blob" | grep -qiE 'INSERT INTO|ON DUPLICATE KEY|seedMenu|seedRoles|seedUsers|seedAliases|seedTaskType|seedAiProviders|sys_menu'; then
    NEED_DATA=1
    tags="${tags}[DATA]"
  fi
  if echo "$blob" | grep -qiE 'UPDATE[[:space:]]|backfill|DEFAULT[[:space:]]|ensure[A-Za-z0-9]+Column|SET[[:space:]].*WHERE'; then
    NEED_DEFAULT=1
    tags="${tags}[DEFAULT]"
  fi
  if is_schema_migrator "$f" && [[ -z "$tags" ]]; then
    NEED_SCHEMA=1
    tags="[SCHEMA]"
  fi
  if [[ -n "$tags" ]]; then
    echo "${tags} $f" >>"$TMP_LIST"
  fi
done <"$TMP_FILES"

NEED_APPLY=0
if [[ "$NEED_SCHEMA" -eq 1 || "$NEED_DATA" -eq 1 || "$NEED_DEFAULT" -eq 1 || "$NO_BASELINE" -eq 1 ]]; then
  NEED_APPLY=1
fi

{
  echo "LAST_SHA=${LAST_SHA:-}"
  echo "CURRENT_SHA=$CURRENT_SHA"
  echo "NO_BASELINE=$NO_BASELINE"
  echo "NEED_APPLY=$NEED_APPLY"
  echo "NEED_SCHEMA=$NEED_SCHEMA"
  echo "NEED_DATA=$NEED_DATA"
  echo "NEED_DEFAULT=$NEED_DEFAULT"
  echo "OPTIONAL_SEED=$OPTIONAL_SEED"
  echo "UNWIRED=$UNWIRED"
  echo "---"
  if [[ ! -s "$TMP_LIST" && "$NO_BASELINE" -eq 0 ]]; then
    echo "相对上次生产发布，无表结构 / 初始化数据 / 默认值回填变更。"
  else
    if [[ "$NO_BASELINE" -eq 1 ]]; then
      echo "无上次生产基线：启动时全部 SchemaMigrator 仍会幂等执行（建表/菜单/回填）。"
    fi
    if [[ -s "$TMP_LIST" ]]; then
      cat "$TMP_LIST"
    fi
  fi
  if [[ "$OPTIONAL_SEED" -eq 1 ]]; then
    echo "---"
    echo "以下为可选灌数/刷数（生产默认关闭，不会随发版执行）："
    sed 's/^/  /' "$TMP_OPT"
  fi
  if [[ "$UNWIRED" -eq 1 ]]; then
    echo "---"
    echo "以下 SQL 未挂到 *SchemaMigrator，生产启动不会执行。请写入 Migrator 或标明「仅作说明」。"
    sed 's/^/  /' "$TMP_UNWIRED"
  fi
  echo "---"
  if [[ "$UNWIRED" -eq 1 ]]; then
    echo "结论：存在未挂接 SQL，禁止发布到生产。"
  elif [[ "$NEED_APPLY" -eq 1 ]]; then
    echo "结论：本次相对生产有表结构/初始化数据/默认值逻辑，发布到生产时必须等 SchemaMigrator 执行完毕。"
  else
    echo "结论：无需额外初始化，发布后只需确认服务起来。"
  fi
} | tee "$REPORT"

if [[ "$UNWIRED" -eq 1 ]]; then
  exit 2
fi
exit 0
