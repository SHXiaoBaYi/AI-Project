#!/usr/bin/env bash
# 将本地已构建的前后端发布到「测试」ECS（/opt/xby-admin，profile=test）
# 用法：./deploy/publish-ecs-test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ECS_ENV=test
export ECS_HOST="${ECS_HOST:-121.40.170.82}"
exec "$ROOT/deploy/publish-ecs.sh" test
