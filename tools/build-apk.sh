#!/usr/bin/env bash
# 只出一个包：R8 已开（app/build.gradle.kts 的 debug 变体），这里跳过 lint vital。
# 本机 lint 极重（整跑 580 秒都没过），且它只是发布前的静态检查，不影响 APK。
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

# 内置 Web UI（web-ui → web/src/main/resources/static）需要 pnpm。
# 默认跳过（本地只要 pnpm 没装也能出包）；CI 用 WITH_WEB_UI=1 把它一起打进去。
EXTRA=()
if [ "${WITH_WEB_UI:-0}" != "1" ]; then
  EXTRA+=(-x :web:buildWebUi)
fi

exec ./gradlew :app:assembleGithubDebug "${EXTRA[@]}" \
  -x :app:lintVitalGithubDebug \
  -x :app:lintVitalAnalyzeGithubDebug \
  -x :app:lintVitalReportGithubDebug \
  --console=plain "$@"
