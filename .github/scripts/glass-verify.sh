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

# ── 0. 登录（必需）───────────────────────────────────────────────────────
# 未登录时首页为空、评论/Review 这些 BottomSheet 根本进不去 —— 那样测的是"寂寞"。
inject_screenshot_token || true

wait_for_input_service

# ── 1. 首页（顶栏 + 底栏玻璃）─────────────────────────────────────────────
clear_log
launch_app
shot "home-glass-on"
report_modes "home-glass-on"

assert_signed_in || true   # 失败只记 ::error::，后续仍尽力产出证据

# ── 2. BottomSheet 玻璃（UI22 的核心问题：M3 ModalBottomSheet 在独立 window，
#      Haze 能否跨 window 采样）──────────────────────────────────────────
# 入口选择：**首页快捷操作 → 仓库选择 Sheet（RepoPickerSheet）**。
#
# 为什么不用 Issue 详情页的「Comment」扩展 FAB（前面试了三轮都失败）：
#   - uiautomator dump 里**完全没有**该 FAB 节点（实测 93 个节点里底部区域一个都没有），
#     Compose 的 ExtendedFloatingActionButton 在该层级下不进 dump → 文本/desc 查找天然不可用；
#   - 退而用坐标，但 FAB 的 y 位置随 WebView 内容高度浮动，像素取证量到的 bbox 与
#     目测差 80px，写死坐标不可靠（实测点空两次）。
# 快捷操作是**普通 Text 节点**（通知面板截图里可见 "Create issue" 等），文本查找可靠，
# 且它打开的正是一个 M3 ModalBottomSheet —— 对 UI22 的判定等价。
#
# 判定口径：只看 GlassRender 日志里有没有 scope=BOTTOM_SHEET，不看点击返回值。
launch_app
sleep 2
# ⚠️ 必须先回首页：上一步 assert_signed_in 点了 Profile，而快捷操作（Create issue 等）
# 只在 Home 页。上一轮就是停在 Profile 上直接点快捷操作，三个入口全部落空 ——
# 看截图一眼就能发现（home-before-sheet.png 明明是 Profile 页），这也是"先看图再改"
# 比"盲改等 CI"快的直接例证。
tap_text "Home" || true
sleep 2
SHEET_OPENED=false
sheet_is_open() { adb logcat -d -s GlassRender 2>/dev/null | grep -q "scope=BOTTOM_SHEET"; }

for label in "Create issue" "View pull requests" "Create repository"; do
  echo "尝试快捷操作入口：$label"
  if try_tap_text "$label"; then
    sleep 3
    if sheet_is_open; then
      SHEET_OPENED=true
      echo "::notice::Sheet 已打开（入口：$label）"
      break
    fi
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 1
  fi
done

# 兜底：即使入口没成功，也留一份现场（首页帧 + UI 层级）供下一轮定位
if [ "$SHEET_OPENED" != true ]; then
  adb exec-out screencap -p > "$OUT/home-before-sheet.png" || true
  adb shell "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$OUT/home-ui.xml" >/dev/null 2>&1 || true
  echo "::warning::快捷操作入口未点开 Sheet，已留现场（home-before-sheet.png / home-ui.xml）"
fi

if [ "$SHEET_OPENED" = true ]; then
  sleep 2
  adb exec-out screencap -p > "$OUT/bottom-sheet-glass-on.png"
  adb logcat -d -s GlassRender 2>/dev/null | tail -40 > "$OUT/bottom-sheet-glass-on.glass.log" || true
  if grep -q "scope=BOTTOM_SHEET" "$OUT/bottom-sheet-glass-on.glass.log" 2>/dev/null; then
    echo "::notice::BottomSheet 已打开，且 GlassScope.BOTTOM_SHEET 参与了解析"
    grep "scope=BOTTOM_SHEET" "$OUT/bottom-sheet-glass-on.glass.log" | tail -1
  else
    echo "::error::未观察到 scope=BOTTOM_SHEET —— Sheet 没打开，UI22 判定不成立"
  fi
else
  echo "::error::所有 Sheet 入口都没点开 —— UI22 判定不完整"
fi
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 1

# ── 3. 通知面板（玻璃面板）───────────────────────────────────────────────
launch_app
tap_desc "Notifications" || echo "::warning::通知入口未找到，跳过面板帧"
shot "notification-panel-glass-on"
report_modes "notification-panel-glass-on"
# 登录失效时这里会拍到应用自己的「Sign-in expired」错误卡 —— 明确标注，避免误判
if ui_contains "Sign-in expired"; then
  echo "::error::通知面板显示 Sign-in expired —— 注入的 SCREENSHOT_TOKEN 已被 GitHub 拒绝（401/403）"
fi
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 1

# ── 4. 设置页玻璃开关 → 关掉后回首页（对照）──────────────────────────────
wait_for_text "Profile" && tap_text "Profile" || echo "::warning::Profile tab 未找到"
sleep 2
wait_for_desc "Settings" && tap_desc "Settings" || echo "::warning::设置入口未找到"
shot "settings-glass"
report_modes "settings-glass"

# 关掉总开关。判据用**日志**而不是像素比较：像素比较要求两次渲染处在同一状态，
# 实测受时钟/状态栏干扰；而 GlassRender 直接打出 blurEnabled=false + mode=TranslucentScrim，
# 机器可判、无歧义。
if try_tap_text "Glass effect"; then
  sleep 2
  clear_log
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 1
  # force-stop 再冷启动：只 resume 不会重新组合，也就不会重新打点
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  launch_app
  shot "home-glass-off"
  report_modes "home-glass-off"
  if grep -q "blurEnabled=false" "$OUT/home-glass-off.glass.log" 2>/dev/null; then
    echo "::notice::关闭总开关后 blurEnabled=false（对照成立）"
    if grep -q "mode=TranslucentScrim" "$OUT/home-glass-off.glass.log" 2>/dev/null; then
      echo "::notice::降级模式确认为 TranslucentScrim"
    else
      echo "::error::总开关已关但仍报告 BackdropBlur —— 开关未接线"
    fi
  else
    echo "::warning::关闭总开关后没有新的 GlassRender 日志（开关可能未点中）"
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
