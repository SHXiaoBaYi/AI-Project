#!/usr/bin/env bash
set -euo pipefail
KEY="${HOME}/.ssh/xby-test-ecs"
JAR="/d/AI-XBY/AI-Project/BackEnd/target/base-admin-1.0.0.jar"
UNIT="/d/AI-XBY/AI-Project/deploy/xby-admin.service"
echo "==> scp jar to test $(wc -c < "$JAR") bytes"
scp -o StrictHostKeyChecking=no -o BatchMode=yes -o ConnectTimeout=30 -i "$KEY" "$JAR" "$UNIT" root@121.40.170.82:/tmp/
echo SCP_TEST_OK
