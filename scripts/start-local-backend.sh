#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="${HOME}/bin:/tmp/apache-maven-3.9.16/bin:/usr/local/bin:${PATH}"
cd "$ROOT/BackEnd"

if lsof -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Backend already listening on :8080"
  exit 0
fi

if [ ! -f target/base-admin-1.0.0.jar ]; then
  echo "Building jar..."
  mvn -q -DskipTests package
fi

echo "Starting backend (profile=local, RDS external)..."
nohup env SPRING_PROFILES_ACTIVE=local java -jar target/base-admin-1.0.0.jar \
  >/tmp/xby-admin-local.log 2>&1 &
echo $! >/tmp/xby-admin-local.pid
disown || true

for i in $(seq 1 60); do
  if curl -s -o /dev/null --connect-timeout 1 -X POST http://127.0.0.1:8080/api/auth/login \
    -H 'Content-Type: application/json' -d '{}' ; then
    echo "Backend ready on http://127.0.0.1:8080/api  (pid $(cat /tmp/xby-admin-local.pid))"
    exit 0
  fi
  if ! kill -0 "$(cat /tmp/xby-admin-local.pid)" 2>/dev/null; then
    echo "Backend exited early. Log:"
    tail -40 /tmp/xby-admin-local.log
    exit 1
  fi
  sleep 1
done

echo "Timeout waiting for backend. Log:"
tail -40 /tmp/xby-admin-local.log
exit 1
