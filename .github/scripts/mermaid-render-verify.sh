#!/usr/bin/env bash
# ============================================================
# 离线 Mermaid 渲染的**真机（模拟器）渲染验证** —— CI 证据层。
#
# 为什么必须有它：JVM/Node 层（MermaidRenderExecutionTest + mermaid-render-harness.js）
# 只能证明「选择/配置/回退/上报」语义；图是否真的被 WebView 栅格化成 SVG，只有真机
# WebView 能证明。Roborazzi 基线与 screenshots.sh 都跑 API 30（WebView Chromium 83），
# 而 mermaid 11 需要 Chromium ≥94（class static block 是解析期语法失败）→ API 30 上
# **只可能拿到回退证据**。「CI 截图覆盖了 mermaid 渲染」在 API 30 上是错误结论。
#
# 两条腿（同一脚本，参数区分；两条都绿才是完整证据）：
#   render  （API 33 / WebView 101）：深链 mermaid-js/mermaid README，断言
#     `MermaidRender: engine=supported rendered>=1` —— 图真的渲染出来了。
#   blocked （API 30 / WebView 83）：断言 `engine=blocked rendered=0`，且日志中
#     **不存在** `engine=supported` —— ≥94 门禁真的拦下并回退，不是页面没开/探针没跑。
#
# 用法：mermaid-render-verify.sh <api-level> <render|blocked>
# 前置：模拟器已 boot（adb 可用）、debug APK 已就位（APK_PATH 可覆盖）。
# 产物：artifacts/mermaid-verify/api-<n>/{assertions.txt,logcat.txt,mermaid-render.log,
#       readme.png,ui.xml,webview-version.txt}
# ============================================================
set -uo pipefail

API="${1:?usage: mermaid-render-verify.sh <api-level> <render|blocked>}"
MODE="${2:?usage: mermaid-render-verify.sh <api-level> <render|blocked>}"
PKG="com.yumiru11.githubapp"
APK="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="artifacts/mermaid-verify/api-${API}"
mkdir -p "$OUT"

# 共用 adb helpers（dump_ui / assert_selected_holds；screenshots.sh 同款实现，避免行为漂移）
# shellcheck source=lib/adb-helpers.sh
source "$(dirname "$0")/lib/adb-helpers.sh"

FAILURES=0
note() { echo "$@" | tee -a "$OUT/assertions.txt"; }
pass() { note "PASS  $1"; }
fail() {
  note "FAIL  $1"
  FAILURES=$((FAILURES + 1))
}

adb wait-for-device

# ── 0. WebView 版本证据（复现「API n → Chromium m」映射，不用信任本文档）─────────
{
  echo "api-level=$API mode=$MODE"
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  adb shell dumpsys webviewupdate 2>/dev/null | grep -i -E "current webview|version" || true
  adb shell dumpsys package com.android.webview 2>/dev/null | grep -m1 versionName || true
  adb shell dumpsys package com.google.android.webview 2>/dev/null | grep -m1 versionName || true
} > "$OUT/webview-version.txt" 2>&1

adb install -r "$APK" > "$OUT/install.txt" 2>&1 || true
grep -q "Success" "$OUT/install.txt" || {
  echo "::error::APK 安装失败（$APK）"
  cat "$OUT/install.txt"
  exit 1
}

# 动画归零：点击/转场即时生效，避免「看似白屏」的中间态被截图
for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global "$scale" 0 || true
done

# 冷启动预热（与 screenshots.sh 顺序一致）：先落到首页再深链，排除首启竞态
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/com.yumiru11.githubapp.MainActivity" >/dev/null 2>&1 || true
sleep 3

adb logcat -c
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null

# ── 1. 等待 README 锚点（证明深链真的落到仓库页，而不是拍了个首页）──────────────
# 轮询只看日志尾部（-t 400）：adb logcat -d 全量 dump 会随日志增长越来越慢
README_OK=0
for _ in $(seq 1 90); do
  if adb logcat -d -t 400 2>/dev/null | grep -qE "ReadmeRender.*repo=mermaid-js/mermaid"; then
    README_OK=1
    break
  fi
  sleep 1
done

# ── 2. 等待 MermaidRender 上报（mermaid.run 异步 settle；给 10 张图 180s）─────────
MERMAID_WAIT=0
for _ in $(seq 1 180); do
  if adb logcat -d -t 400 2>/dev/null | grep -qE "MermaidRender: engine="; then
    MERMAID_WAIT=1
    break
  fi
  sleep 1
done

sleep 3 # 渲染/清理（源码块隐藏）稳定窗口

# ── 3. 断言（先跑 UI 断言，再落 dump 产物，避免 dump 被后续断言覆盖错位）───────────
if [ "$README_OK" = "1" ]; then
  pass "README 已加载（ReadmeRender repo=mermaid-js/mermaid）"
else
  fail "90s 内未见 ReadmeRender repo=mermaid-js/mermaid（README 未加载/深链失败）"
fi

if [ "$MERMAID_WAIT" = "1" ]; then
  pass "收到 MermaidRender 上报行（renderMermaid 已执行并 settle）"
else
  fail "180s 内未见 MermaidRender 上报行（renderMermaid 未执行/未 settle）"
fi

# 非白屏/非崩溃的**原生**证据：README tab 处于 selected（与 WebView 内部无关）
if assert_selected_holds "README" 15; then
  pass "README tab selected（原生 UI 已就绪，非白屏）"
else
  fail "README tab 未选中（UI 未就绪/白屏/崩溃）"
fi

if adb shell pidof "$PKG" >/dev/null 2>&1; then
  pass "app 进程存活"
else
  fail "app 进程不存在（崩溃后被杀？）"
fi

# ── 4. 取证：截图 / UI 层级 / logcat ─────────────────────────────────────────
adb exec-out screencap -p > "$OUT/readme.png" 2>/dev/null || true
[ -f /tmp/ui.xml ] && cp /tmp/ui.xml "$OUT/ui.xml"
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
grep -E "MermaidRender: engine=" "$OUT/logcat.txt" > "$OUT/mermaid-render.log" || true

if grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" 2>/dev/null | grep -q "$PKG"; then
  fail "logcat 出现 $PKG 的 FATAL EXCEPTION（崩溃）"
else
  pass "无 FATAL EXCEPTION"
fi

# ── 5. 模式断言（本 job 的核心证据）───────────────────────────────────────────
if [ "$MODE" = "render" ]; then
  MAX_RENDERED=$(grep -oE "engine=supported rendered=[0-9]+" "$OUT/mermaid-render.log" | sed -E 's/.*rendered=//' | sort -n | tail -1)
  if [ -n "${MAX_RENDERED:-}" ] && [ "$MAX_RENDERED" -ge 1 ] 2>/dev/null; then
    pass "engine=supported 且 rendered=$MAX_RENDERED (>=1)：图真的渲染出来了"
  else
    fail "未观察到 engine=supported rendered>=1（实际最大 rendered=${MAX_RENDERED:-none}）"
  fi
else
  if grep -qE "engine=blocked rendered=0 failed=0" "$OUT/mermaid-render.log"; then
    pass "engine=blocked rendered=0：Chromium <94 门禁拦下并回退为代码块"
  else
    fail "未见 engine=blocked rendered=0 行"
  fi
  if grep -q "engine=supported" "$OUT/mermaid-render.log"; then
    fail "API $API 不应出现 engine=supported（≥94 门禁未生效？）"
  else
    pass "无 engine=supported（门禁确实拦下，排除「页面没开」的假绿）"
  fi
fi

# ── 6. 汇总（不下载 artifact 也能在 job summary 里看到证据）────────────────────
{
  echo "### Mermaid render verify — API $API ($MODE)"
  echo ""
  echo '```'
  cat "$OUT/assertions.txt"
  echo ""
  echo "WebView:"
  cat "$OUT/webview-version.txt"
  echo ""
  echo "MermaidRender lines:"
  cat "$OUT/mermaid-render.log" 2>/dev/null || true
  echo '```'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}" 2>/dev/null || true

if [ "$FAILURES" -ne 0 ]; then
  echo "::error::mermaid-render-verify API $API ($MODE) 断言失败 $FAILURES 条（见 artifacts/mermaid-verify/api-$API）"
  exit 1
fi
echo "mermaid-render-verify API $API ($MODE) 全部断言通过"
