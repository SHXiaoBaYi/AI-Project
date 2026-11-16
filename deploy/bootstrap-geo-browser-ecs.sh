#!/usr/bin/env bash
# 在测试/生产 ECS 上以 root 执行：安装有界面 Chrome + Xvfb，供 GEO 自动监测 Playwright 使用。
# 用法：
#   scp deploy/bootstrap-geo-browser-ecs.sh deploy/xvfb-geo.service root@47.117.132.161:/tmp/
#   ssh root@47.117.132.161 'bash /tmp/bootstrap-geo-browser-ecs.sh'
set -euo pipefail

CHROME_BIN="${GEO_BROWSER_PATH:-/usr/bin/google-chrome-stable}"
DISPLAY_NUM="${GEO_DISPLAY:-:99}"

install_chrome() {
  if [[ -x "$CHROME_BIN" ]]; then
    echo "==> Chrome 已存在: $CHROME_BIN"
    "$CHROME_BIN" --version | head -1
    return 0
  fi
  echo "==> 安装 Google Chrome..."
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y wget || true
    rpm -qa | grep -q google-chrome-stable || {
      tmp="/tmp/google-chrome-stable.rpm"
      wget -O "$tmp" https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm
      dnf install -y "$tmp" || yum localinstall -y "$tmp"
      rm -f "$tmp"
    }
  elif command -v yum >/dev/null 2>&1; then
    tmp="/tmp/google-chrome-stable.rpm"
    wget -O "$tmp" https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm
    yum localinstall -y "$tmp"
    rm -f "$tmp"
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y wget gnupg ca-certificates
    wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome.gpg
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome.gpg] http://dl.google.com/linux/chrome/deb/ stable main" \
      >/etc/apt/sources.list.d/google-chrome.list
    apt-get update -y
    apt-get install -y google-chrome-stable
  else
    echo "无法识别包管理器，请手动安装 Chrome 到 $CHROME_BIN" >&2
    exit 1
  fi
  if [[ ! -x "$CHROME_BIN" ]]; then
    for c in /usr/bin/google-chrome-stable /usr/bin/google-chrome; do
      if [[ -x "$c" ]]; then CHROME_BIN="$c"; break; fi
    done
  fi
  if [[ ! -x "$CHROME_BIN" ]]; then
    echo "安装后仍未找到 Chrome，请检查 PATH" >&2
    exit 1
  fi
  "$CHROME_BIN" --version
}

install_xvfb() {
  echo "==> 安装 Xvfb"
  if command -v dnf >/dev/null 2>&1; then
    dnf install -y xorg-x11-server-Xvfb || dnf install -y xorg-x11-server-Xvfb-core
  elif command -v yum >/dev/null 2>&1; then
    yum install -y xorg-x11-server-Xvfb
  elif command -v apt-get >/dev/null 2>&1; then
    apt-get install -y xvfb
  fi
  command -v Xvfb >/dev/null 2>&1 || { echo "Xvfb 安装失败" >&2; exit 1; }
}

install_xvfb_unit() {
  local src="${1:-/tmp/xvfb-geo.service}"
  if [[ ! -f "$src" ]]; then
    cat >/etc/systemd/system/xvfb-geo.service <<EOF
[Unit]
Description=Virtual framebuffer (${DISPLAY_NUM}) for GEO browser
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/Xvfb ${DISPLAY_NUM} -screen 0 1440x900x24 -nolisten tcp
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
  else
    cp "$src" /etc/systemd/system/xvfb-geo.service
  fi
  systemctl daemon-reload
  systemctl enable --now xvfb-geo.service
  systemctl is-active xvfb-geo.service
}

patch_xby_admin_env() {
  mkdir -p /etc/systemd/system/xby-admin.service.d
  cat >/etc/systemd/system/xby-admin.service.d/geo-browser.conf <<EOF
[Unit]
After=xvfb-geo.service
Requires=xvfb-geo.service

[Service]
Environment=DISPLAY=${DISPLAY_NUM}
Environment=GEO_BROWSER_PATH=${CHROME_BIN}
Environment=GEO_BROWSER_HEADLESS=false
EOF
  systemctl daemon-reload
  echo "==> 已写入 xby-admin.service.d/geo-browser.conf"
}

verify_launch() {
  echo "==> 冒烟：无界面下启动 Chrome（验证 Xvfb + 可执行文件）"
  export DISPLAY="${DISPLAY_NUM}"
  timeout 15 "$CHROME_BIN" --no-sandbox --disable-gpu --dump-dom about:blank >/dev/null \
    && echo "Chrome 在 ${DISPLAY_NUM} 上可启动" \
    || echo "警告：Chrome 冒烟未通过，请检查 journalctl -u xvfb-geo"
}

install_chrome
install_xvfb
install_xvfb_unit "${1:-}"
patch_xby_admin_env
verify_launch

echo "==> 完成。请执行: systemctl restart xby-admin"
echo "    采集中日志应出现: GEO 浏览器采集器就绪（headless=false, ...）"