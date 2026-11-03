#!/usr/bin/env bash
# 变体由 VARIANT 决定：release 出正式版包（包名不带 .debug），debug 出预览包（默认）。
# 两个变体都开了 R8，这里跳过 lint vital：本机整跑 580 秒都没过，且它只是发布前的静态检查，不影响 APK。
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

VARIANT="${VARIANT:-debug}"
case "$VARIANT" in
  debug|release) ;;
  *)
    echo "未知变体：$VARIANT（只支持 debug / release）" >&2
    exit 1 ;;
esac
# Gradle 任务名里的变体首字母大写：githubDebug / githubRelease
Variant="$(printf '%s' "${VARIANT:0:1}" | tr '[:lower:]' '[:upper:]')${VARIANT:1}"

# 内置 Web UI（web-ui → web/src/main/resources/static）需要 pnpm。
# 默认跳过（本地只要 pnpm 没装也能出包）；CI 用 WITH_WEB_UI=1 把它一起打进去。
EXTRA=()
if [ "${WITH_WEB_UI:-0}" != "1" ]; then
  EXTRA+=(-x :web:buildWebUi)
fi

exec ./gradlew ":app:assembleGithub${Variant}" "${EXTRA[@]}" \
  -x ":app:lintVitalGithub${Variant}" \
  -x ":app:lintVitalAnalyzeGithub${Variant}" \
  -x ":app:lintVitalReportGithub${Variant}" \
  --console=plain "$@"
