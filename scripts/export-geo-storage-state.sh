#!/usr/bin/env bash
# 导出 GEO 自动监测所需的「平台网页版登录态」（Playwright storageState JSON）。
#
# 用法（Git Bash / Windows）：
#   ./scripts/export-geo-storage-state.sh 豆包 https://www.doubao.com/chat/
#   ./scripts/export-geo-storage-state.sh DS https://chat.deepseek.com/ "C:/Program Files/Google/Chrome/Application/chrome.exe"
#
# 参数：<平台名> <提问页URL> [Chrome路径] [输出json路径]
# 产出默认落到 BackEnd/uploads/geo/auto-monitor/state/{平台名}.json，
# 与 application.yml 的 geo.auto-monitor.browser.storage-state-dir 对齐。
set -euo pipefail

PLATFORM="${1:-豆包}"
URL="${2:-https://www.doubao.com/chat/}"
CHROME="${3:-C:/Program Files/Google/Chrome/Application/chrome.exe}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Git Bash 下 Java / Maven 不认识 /c/... 形式的 MSYS 路径，统一转成 Windows 路径
if command -v cygpath >/dev/null 2>&1; then
  ROOT="$(cygpath -m "$ROOT")"
fi
OUT="${4:-$ROOT/BackEnd/uploads/geo/auto-monitor/state/${PLATFORM}.json}"

# 只装 JDK 也能跑：本机便携 JDK21 / Maven 兜底
if ! command -v mvn >/dev/null 2>&1; then
  export PATH="/d/AI-XBY/.tools/apache-maven-3.9.11/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
  export JAVA_HOME="${JAVA_HOME:-/d/AI-XBY/.tools/jdk-21.0.13+9}"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# 用 Maven 生成完整 runtime classpath：Playwright Java 启动时会从 driver jar 解压
# cli.js / lib/*，只手工拼 playwright+driver+driver-bundle 三个 jar 会解压不全而报
# "Cannot find module './lib/bootstrap'"。
mkdir -p "$ROOT/BackEnd/target"
CP_OUT="$ROOT/BackEnd/target/geo-state-classpath.txt"
if [ ! -s "$CP_OUT" ]; then
  echo "首次运行：生成后端 runtime classpath ..."
  (cd "$ROOT/BackEnd" && mvn -q dependency:build-classpath \
      -Dmdep.outputFile="$CP_OUT" -DincludeScope=runtime -DskipTests)
fi
CP="$(cat "$CP_OUT")"

echo "平台：$PLATFORM"
echo "页面：$URL"
echo "输出：$OUT"
echo "浏览器：$CHROME"
echo "--- 将打开有头浏览器，登录完成后回到本终端按回车 ---"
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 --class-path "$CP" \
    "$ROOT/scripts/GeoStorageStateExporter.java" "$PLATFORM" "$URL" "$CHROME" "$OUT"
