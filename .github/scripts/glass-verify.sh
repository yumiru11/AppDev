#!/usr/bin/env bash
# ============================================================
# 毛玻璃（Haze backdrop blur）专项验证脚本（UI22 + 视觉验收）
#
# 为什么需要独立一支：ci.yml 的截图 job 跑在 **API 30**，而 RenderEffect 模糊
# 要求 API 31+（AppBlur.MIN_BLUR_API）。也就是说**现有的所有 CI 截图里，
# 毛玻璃从来就没有生效过** —— 拿它判断「Haze 能不能跨 window 采样」是没有意义的。
# 本脚本配 glass-verify.yml（API 31 + swiftshader）专测这一件事。
#
# 判定口径（两类证据，缺一不可）：
#   ① 日志：GlassSurface 每次解析渲染模式都会打 `GlassRender` 行，含 scope/模式/
#      三个前置条件（开关、HazeState、sdkInt）。出现 `mode=BackdropBlur` 才说明
#      真的走了模糊路径，而不是静默降级成半透明色。
#   ② 像素：对同一位置「开毛玻璃 / 关毛玻璃」各截一帧，逐字节比较。
#      **相同 = 模糊没生效**（无论日志怎么说）；不同才继续交给人眼看效果对不对。
#
# 为什么②必须是同一次运行内的对照：跨运行比较会被数据/主题/时间差污染。
#
# 依赖：模拟器已启动（API 31+）、debug APK 已装。
# ============================================================
set -euo pipefail

OUT="artifacts/glass"
PKG="com.yumiru11.githubapp"
mkdir -p "$OUT"

# shellcheck source=lib/adb-helpers.sh
source "$(dirname "$0")/lib/adb-helpers.sh"

# 渲染模式日志缓冲（GlassSurface 打点）；每次冷启动前清空，避免读到上一轮的旧行
clear_log() { adb logcat -c 2>/dev/null || true; }

# 抓一帧 + 记录当时的 GlassRender 决策
shot() {
  local name="$1"
  sleep 2
  adb exec-out screencap -p > "$OUT/$name.png"
  adb logcat -d -s GlassRender 2>/dev/null | tail -40 > "$OUT/$name.glass.log" || true
  echo "captured $name"
}

# 报告本帧里出现过的渲染模式（去重）
report_modes() {
  local name="$1"
  local modes
  modes=$(grep -o "mode=[A-Za-z]*" "$OUT/$name.glass.log" 2>/dev/null | sort -u | tr "\n" " " || true)
  if [ -z "$modes" ]; then
    echo "::warning::$name: 没有任何 GlassRender 日志（玻璃面可能未组合，或日志点未生效）"
  else
    echo "$name glass modes: $modes"
  fi
}

wait_for_input_service

# ── 1. 默认（毛玻璃开）───────────────────────────────────────────────────
clear_log
launch_app
shot "home-glass-on"
report_modes "home-glass-on"

# 通知面板（玻璃面板：顶栏铃铛）
tap_desc "Notifications" || echo "::warning::通知入口未找到，跳过面板帧"
shot "notification-panel-glass-on"
report_modes "notification-panel-glass-on"
# 关闭面板（返回键最稳，点 X 依赖面板内文案）
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 1

# ── 2. BottomSheet 玻璃（UI22 的核心问题：M3 ModalBottomSheet 在独立 window，
#      Haze 能否跨 window 采样）──────────────────────────────────────────
# 游客可达的入口：首页长条按钮 → 仓库选择 Sheet（RepoPickerSheet）
tap_desc "Choose repositories" || tap_desc "Repositories" || echo "::warning::仓库选择入口未找到，跳过 BottomSheet 帧"
shot "bottom-sheet-glass-on"
report_modes "bottom-sheet-glass-on"
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 1

# ── 3. 设置页玻璃开关区（对照用）──────────────────────────────────────────
tap_desc "Profile" || tap_desc "You" || echo "::warning::Profile tab 未找到"
sleep 2
tap_desc "Settings" || echo "::warning::设置入口未找到"
shot "settings-glass"
report_modes "settings-glass"

# ── 4. 对照：关掉毛玻璃总开关，同位置再截一帧 ─────────────────────────────
# 开关是列表里的第一组「Glass effect」行；点它的 Switch（描述文案固定）
if tap_desc "Blurred surfaces behind bars, panel and sheets"; then
  sleep 2
  clear_log
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  launch_app
  shot "home-glass-off"
  report_modes "home-glass-off"
  # 同位置对照：两帧逐字节比较，相同即模糊未生效
  if cmp -s "$OUT/home-glass-on.png" "$OUT/home-glass-off.png"; then
    echo "::error::开/关毛玻璃后首页截图逐字节相同 —— 模糊未生效（UI22 判定：Haze 未跨 window / 未采样到内容）"
  else
    echo "::notice::开/关毛玻璃首页截图不同 —— 模糊路径确实生效，请人工确认视觉效果"
  fi
else
  echo "::warning::未找到 Glass effect 开关，跳过对照帧（UI22 判定不完整）"
fi

# ── 5. 证据归档 ───────────────────────────────────────────────────────────
adb logcat -d -s GlassRender > "$OUT/glass-all.log" 2>/dev/null || true
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
grep -c "FATAL EXCEPTION" "$OUT/logcat.txt" >/dev/null 2>&1 && echo "::warning::logcat 中有 FATAL EXCEPTION" || true

echo "=== GlassRender 决策汇总 ==="
cat "$OUT/glass-all.log" 2>/dev/null || echo "(空)"
ls -la "$OUT"
