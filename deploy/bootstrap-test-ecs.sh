#!/usr/bin/env bash
# 在「测试 ECS」上以 root 执行一次：装 Java/nginx、目录、systemd、nginx 配置。
# 用法（本机有 SSH 密钥后）：
#   scp deploy/bootstrap-test-ecs.sh deploy/nginx-xby-admin.conf deploy/xby-admin-test.service root@121.40.170.82:/tmp/
#   ssh root@121.40.170.82 'bash /tmp/bootstrap-test-ecs.sh'
set -euo pipefail

ECS_DIR="${ECS_DIR:-/opt/xby-admin}"
echo "==> 目录 ${ECS_DIR}"
mkdir -p "$ECS_DIR/html" "$ECS_DIR/config" "$ECS_DIR/uploads"

if ! command -v java >/dev/null 2>&1; then
  echo "==> 安装 Java 21"
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y java-21-openjdk-headless || dnf install -y java-17-openjdk-headless
  elif command -v yum >/dev/null 2>&1; then
    yum install -y java-21-openjdk-headless || yum install -y java-17-openjdk-headless
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y openjdk-21-jre-headless || apt-get install -y openjdk-17-jre-headless
  else
    echo "无法识别包管理器，请手动安装 Java 17+" >&2
    exit 1
  fi
fi
java -version

if ! command -v nginx >/dev/null 2>&1; then
  echo "==> 安装 nginx"
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y nginx
  elif command -v yum >/dev/null 2>&1; then
    yum install -y nginx
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get install -y nginx
  fi
fi

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "==> 安装 mysql 客户端（用于生产→测试同步）"
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y mysql || dnf install -y mariadb
  elif command -v yum >/dev/null 2>&1; then
    yum install -y mysql || yum install -y mariadb
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get install -y mysql-client || apt-get install -y mariadb-client
  fi
fi

NGINX_SRC="${1:-/tmp/nginx-xby-admin.conf}"
SERVICE_SRC="${2:-/tmp/xby-admin-test.service}"
if [[ -f "$NGINX_SRC" ]]; then
  echo "==> 安装 nginx 配置"
  if [[ -d /etc/nginx/conf.d ]]; then
    cp "$NGINX_SRC" /etc/nginx/conf.d/xby-admin.conf
  elif [[ -d /etc/nginx/sites-available ]]; then
    cp "$NGINX_SRC" /etc/nginx/sites-available/xby-admin
    ln -sfn /etc/nginx/sites-available/xby-admin /etc/nginx/sites-enabled/xby-admin
  else
    cp "$NGINX_SRC" /etc/nginx/nginx.conf.bak-xby 2>/dev/null || true
    echo "请手动把 $NGINX_SRC 并入 nginx 配置" >&2
  fi
  nginx -t
  systemctl enable nginx
  systemctl restart nginx
else
  echo "跳过 nginx 配置（未找到 $NGINX_SRC）"
fi

if [[ -f "$SERVICE_SRC" ]]; then
  echo "==> 安装 systemd 单元（SPRING_PROFILES_ACTIVE=test）"
  cp "$SERVICE_SRC" /etc/systemd/system/xby-admin.service
  systemctl daemon-reload
  systemctl enable xby-admin
else
  echo "跳过 systemd（未找到 $SERVICE_SRC）"
fi

# 防火墙放行 80（若存在 firewalld）
if command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-service=http || true
  firewall-cmd --reload || true
fi

echo "==> bootstrap 完成。下一步："
echo "    1) 确认测试 RDS 白名单含本机内网；库 base_admin 已存在"
echo "    2) 在本机执行生产→测试同步（见 deploy/ENV.md）"
echo "    3) 本机构建后：ECS_HOST=121.40.170.82 ./deploy/publish-ecs-test.sh"
echo "    4) systemctl start xby-admin && systemctl status xby-admin"
