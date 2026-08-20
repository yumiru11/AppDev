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

# 长截图（纯 adb 滚动 + 多帧截图，无 Python/PIL 依赖）：
# 导航到 $2 深链，循环「上滑 → 截一帧」最多 $3 帧（默认 20），相邻帧二进制相同即视为到底、停止。
# 输出到 $OUT/$1-01.png、$1-02.png …（多帧即长截图，人工翻看）。
long_shot() {
  local name="$1" url="$2" max="${3:-20}"
  local delta=2000          # pixel_6 视口 2400：自底(y=2000)上滑 2000px，留 ~400 重叠
  adb shell am start -a android.intent.action.VIEW -d "$url" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 6
  local prev="" f i
  for i in $(seq 1 "$max"); do
    f="$OUT/${name}-$(printf '%02d' "$i").png"
    adb exec-out screencap -p > "$f"
    # 相邻帧相同 → 已到底（或页面不可滚），删重复帧并停止
    if [ -n "$prev" ] && cmp -s "$prev" "$f"; then
      rm -f "$f"
      echo "::notice::long screenshot ($name) reached bottom at frame $((i - 1))"
      break
    fi
    prev="$f"
    # 末帧无需上滑
    if [ "$i" -lt "$max" ]; then
      adb shell input swipe 540 2000 540 $((2000 - delta)) 250
      sleep 1.5
    fi
  done
}

# ── 0. 安装 debug APK（action 只启动模拟器，不装 APK——PR #60 第 8 轮实测）─
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb shell pm disable com.android.launcher3 --user 0 >/dev/null 2>&1 || true

# ── 0. 安装 APK（android-emulator-runner 不自动装；assembleDebug 产物在工作区）─
adb install -r app/build/outputs/apk/debug/app-debug.apk

# ── 0.5 截图登录（若提供 SCREENSHOT_TOKEN 机密）：注入只读 PAT →
# EncryptedTokenStorage（ScreenshotTokenReceiver），使 app 进入开发者模式
# （Star/评论框/PR 操作可见）。未配置机密时整段跳过，其余截图不受影响。
if [ -n "${SCREENSHOT_TOKEN:-}" ]; then
  echo "::notice::SCREENSHOT_TOKEN provided — injecting PAT for authenticated screenshots"
  adb shell am start -n "$PKG/com.yumiru11.githubapp.ScreenshotTokenReceiver" -e pat "$SCREENSHOT_TOKEN" >/dev/null 2>&1 || true
  sleep 2
fi

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

# ── 4. 普通 README（WebView 主渲染——mikepenz 样例：表格/代码/图片/徽章齐全）
# Task B 后 README 一律 WebView（ADR-0007）；renderMode 判定以 ReadmeRender 日志为准
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mikepenz/multiplatform-markdown-renderer" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 6
adb exec-out screencap -p > "$OUT/readme-regular.png"

# ── 5. mermaid 仓库 README（WebView——mermaid 代码块特殊内容路径）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null
sleep 8
adb exec-out screencap -p > "$OUT/readme-mermaid.png"

# ── 5.5 Issue 正文（WebView 渲染——测试面板 #71 覆盖全 md 格式）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 8
adb exec-out screencap -p > "$OUT/issue-body.png"

# ── 5.6 Issue 评论（原生短文本渲染——正文 WebView 很高，滑到评论区）─
adb shell input swipe 540 1800 540 400 500
sleep 2
adb shell input swipe 540 1800 540 400 500
sleep 2
adb shell input swipe 540 1800 540 400 500
sleep 4
adb exec-out screencap -p > "$OUT/issue-comments.png"

# ── 6. 我的 tab（force-stop 冷启动回首页——am start 对已在前台 app 不重置
# 导航栈，深链页仍在前台导致 uiautomator 拿不到底栏）──────────
adb shell cmd uimode night no
adb shell am force-stop "$PKG"
launch_app
tap_text "Profile"
sleep 3
adb exec-out screencap -p > "$OUT/profile.png"

# ── 7. 登录后段（需 SCREENSHOT_TOKEN 注入）：Star 按钮 / 评论框 / PR 操作可见 ──
if [ -n "${SCREENSHOT_TOKEN:-}" ]; then
  adb shell am force-stop "$PKG"
  # 仓库详情（Star 按钮）
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 4
  adb exec-out screencap -p > "$OUT/repo-star.png"
  # Issue 详情（评论框可见）
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 6
  adb exec-out screencap -p > "$OUT/issue-authed.png"
  # PR 详情（PR 操作可见）
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/73" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 6
  adb exec-out screencap -p > "$OUT/pr-actions.png"
fi

# ── 8. 长截图（滚动 + 拼接，docs/research/actions-scroll-screenshot.md 方案 A）──
long_shot "readme-long.png" "https://github.com/mikepenz/multiplatform-markdown-renderer"
long_shot "issue-long.png" "https://github.com/yumiru11/AppDev/issues/71"

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
