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
# 入口优先用**登录态才可达**的 Issue 评论输入 Sheet（§6.1 允许点位里的
# "评论输入 / 行评论 / Review / 仓库选择"），它才是 UI22 真正要回答的对象。
SHEET_OPENED=false
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null 2>&1 || true
wait_for_activity "$PKG" || true
# 用 wait_for_text（带超时轮询）而不是 sleep + 一次性查找：Issue 页要拉正文 + 评论，
# 固定 sleep 容易在"还在加载"时 dump，元素自然找不到（上一轮就是这么漏的）。
# 无论成败都留一帧 Issue 页 + 一份 UI 层级：上一轮只看到"没点开"，看不到"当时屏幕上
# 是什么"，排查全靠猜。把现场抓进 artifact，下一次就能直接定位。
sleep 6
adb exec-out screencap -p > "$OUT/issue-page.png" || true
# 点击「Comment」扩展 FAB。**文本查找不可靠**：本次实测 uiautomator dump 里**完全没有**
# 该 FAB 节点（93 个节点里底部区域一个都没有），而同一次运行的 issue-page.png 里它
# 清晰可见 —— Compose 的 ExtendedFloatingActionButton 在该层级下不进 dump。
# 所以顺序是：先试文本（万一哪天进了），失败即按坐标兜底。
# 坐标依据：pixel_6 = 1080x2400，Issue 详情页右下角扩展 FAB 中心 ≈ (875, 1972)
# （由 issue-page.png 量得；FAB 是固定停靠位，不随列表滚动）。
# ⚠️ 必须用 try_* 版本：tap_text/tap_desc 找不到也只告警、返回 0，放进 || 链会让
# 链在第一个元素就短路 —— 坐标兜底永远不执行（上一轮就是这么漏的：产物帧与
# issue-page.png 逐字节相同，说明压根没点）。
if try_tap_text "Comment" || try_tap_desc "Comment" || adb shell input tap 875 1972; then
  SHEET_OPENED=true
else
  adb shell "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$OUT/issue-page-ui.xml" >/dev/null 2>&1 || true
  # 兜底：首页快速操作 → 仓库选择 Sheet（游客也开得出来）
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  launch_app
  for label in "Create issue" "View pull requests" "Create repository"; do
    if try_tap_text "$label"; then SHEET_OPENED=true; break; fi
  done
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
