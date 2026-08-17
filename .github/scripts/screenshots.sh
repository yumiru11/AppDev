#!/usr/bin/env bash
# ============================================================
# CI 模拟器截图脚本（轻量 adb 方案，零第三方依赖）
#
# 背景：Maestro 安装 3 次翻车（官方脚本不落盘 / release zip 下载失败），
# 调研证明 adb screencap + input + uimode 足够截图自动化
# （docs/research/actions-emulator-feasibility.md Q5——kzahel/kiwix/
# inaturalist 等真实 workflow 同款模式）。
#
# 依赖：模拟器已由 android-emulator-runner 启动（adb 可用），
# debug APK 已装（assembleDebug 产物被 action 自动安装）。
#
# 导航说明：
# - 首页/仓库/我的 = 底部 3 tab（pixel_6 1080x2400@420dpi，NavigationBar
#   高 80dp=210px，tab 中心 y≈2295；x=180/540/900 三等分）
# - README 用深链（app 注册 github.com VIEW intent，T3）
# - 深色 = cmd uimode night + 冷启动（SYSTEM 主题模式跟随系统）
# ============================================================
set -euo pipefail

OUT="artifacts/screenshots"
PKG="com.yumiru11.githubapp"
mkdir -p "$OUT"

# 等待指定 activity 成为前台（轮询，最多 40s）——比固定 sleep 稳
wait_for_activity() {
  local expect="$1"
  for _ in $(seq 1 40); do
    if adb shell dumpsys activity activities 2>/dev/null | grep -q "mResumedActivity.*$expect"; then
      return 0
    fi
    sleep 1
  done
  echo "::warning::timeout waiting for activity $expect"
  return 1
}

# 按可见文本 tap（uiautomator dump 拿 bounds 中心）——比硬编码坐标稳
tap_text() {
  local text="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local bounds
  bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'text=\"$text\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
  if [ -n "$bounds" ]; then
    adb shell input tap $bounds >/dev/null
  else
    echo "::warning::text '$text' not found in uiautomator dump"
  fi
}

launch_app() {
  adb shell am start -n "$PKG/com.yumiru11.githubapp.MainActivity" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 3   # 首帧稳定
}

# ── 0. 安装 debug APK（action 只启动模拟器，不装 APK——PR #60 第 8 轮实测）─
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb shell pm disable com.android.launcher3 --user 0 >/dev/null 2>&1 || true

# ── 0. 安装 APK（android-emulator-runner 不自动装；assembleDebug 产物在工作区）─
adb install -r app/build/outputs/apk/debug/app-debug.apk

# ── 1. 首页（浅色）──────────────────────────────────────────
adb shell cmd uimode night no
adb shell am force-stop "$PKG"
launch_app
adb exec-out screencap -p > "$OUT/home-light.png"

# ── 2. 首页（深色：uimode + 冷启动）──────────────────────────
adb shell cmd uimode night yes
adb shell am force-stop "$PKG"
launch_app
adb exec-out screencap -p > "$OUT/home-dark.png"

# ── 3. 仓库 tab（占位页）────────────────────────────────────
tap_text "Repos"
sleep 3
adb exec-out screencap -p > "$OUT/repos.png"

# ── 4. README 原生渲染（深链进 EchoMusic——FeatureDetector 判 NATIVE 的仓库）
# 注：mikepenz README 曾被 MATH 正则误判走 WebView（readme-native.png 名不副实）；
# P1（#64）修复 MATH 误报后换回 mikepenz（其 README 展示更全）
adb shell am start -a android.intent.action.VIEW -d "https://github.com/hoowhoami/EchoMusic" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 6
adb exec-out screencap -p > "$OUT/readme-native.png"

# ── 5. README WebView 兜底（mermaid 仓库 → FeatureDetector 分流）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null
sleep 8
adb exec-out screencap -p > "$OUT/readme-webview.png"

# ── 6. 我的 tab（force-stop 冷启动回首页——am start 对已在前台 app 不重置
# 导航栈，深链页仍在前台导致 uiautomator 拿不到底栏）──────────
adb shell cmd uimode night no
adb shell am force-stop "$PKG"
launch_app
tap_text "Profile"
sleep 3
adb exec-out screencap -p > "$OUT/profile.png"

# ── 清理：恢复浅色 + 回首页 ─────────────────────────────────
adb shell cmd uimode night no
adb shell am force-stop "$PKG"

# ── 崩溃诊断：dump logcat（app 崩溃时堆栈在缓冲区——第 13 轮 profile/README
# 三张图显示桌面，疑似崩溃；无 logcat 无法定位）────────────────
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
grep -c "FATAL EXCEPTION" "$OUT/logcat.txt" >/dev/null 2>&1 && echo "::warning::FATAL EXCEPTION found in logcat" || true

# ── 渲染通道判定留档：ReadmeRender 日志（native/webview 以日志为准，禁止视觉推断）
grep "ReadmeRender" "$OUT/logcat.txt" > "$OUT/readme-render-log.txt" 2>/dev/null || true
if [ -s "$OUT/readme-render-log.txt" ]; then
  echo "ReadmeRender decisions:"
  cat "$OUT/readme-render-log.txt"
else
  echo "::warning::no ReadmeRender log found"
fi

echo "screenshots:"
ls -la "$OUT"
