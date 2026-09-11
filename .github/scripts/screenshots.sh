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

# 共用 adb 驱动 helpers（等待/点击/断言）——与 glass-verify 脚本同一份实现，
# 避免两处行为漂移（详见该文件头部说明）
# shellcheck source=lib/adb-helpers.sh
source "$(dirname "$0")/lib/adb-helpers.sh"

# ── 0. 安装 debug APK（android-emulator-runner 不自动装；APK 由 quality job
#    构建上传、本 job 开头已下载到工作区原位）───────────────────────────────
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm disable com.android.launcher3 --user 0 >/dev/null 2>&1 || true
# 动画时长归零：点击即时生效，进度圈/转场不再让 uiautomator 等 idle 卡死
# （dump 单次 5-10s 是本轮 CI 拖到 16min 的主放大器）
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

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

# ── 3. 仓库 tab（列表）──────────────────────────────────────
tap_text "Repos"
sleep 3
adb exec-out screencap -p > "$OUT/repos.png"

# ── 4. 普通 README（WebView）—— 由 5.7 的 readme-webview 帧覆盖，不单独截 ──

# ── 5. mermaid 仓库 README（WebView——mermaid 代码块特殊内容路径）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null
sleep 5
adb exec-out screencap -p > "$OUT/readme-mermaid.png"

# ── 5.5 导航到 Issue #71（就位供 5.6 评论区截图）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 5

# ── 5.6 Issue 评论（原生短文本渲染——正文 WebView 很高，滑到评论区）─
retry_input swipe 540 1800 540 400 500
sleep 2
retry_input swipe 540 1800 540 400 500
sleep 2
retry_input swipe 540 1800 540 400 500
sleep 3
adb exec-out screencap -p > "$OUT/issue-comments.png"

# ── 5.7 PR 详情 Conversation（T15）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/74" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 5
adb exec-out screencap -p > "$OUT/pr-conversation.png"

# ── 5.8 PR Commits Tab（T15）──
tap_text "Commits"
sleep 3
adb exec-out screencap -p > "$OUT/pr-commits.png"

# ── 5.7 仓库详情三连帧（T11/T12）：一次深链串拍 操作区/Releases/Files 树。
# 此前为 EchoMusic 独立深链——AppDev 同样具备语言栏与 Releases（截图产物仓库），
# 且登录态下 Star 按钮可见，信息量只增不减，省两次深链往返
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 3
adb exec-out screencap -p > "$OUT/repo-actions.png"
tap_text "Releases"
sleep 3
adb exec-out screencap -p > "$OUT/repo-releases.png"
tap_text "Files"
wait_for_text ".github" || true          # 树条目就绪信号（首屏可见的顶层目录）
adb exec-out screencap -p > "$OUT/file-tree.png"
# README WebView 渲染帧（ADR-0007 主路径）：树下方滚动一屏拍 README 区，
# 替代被砍的 readme-long 长截图保住渲染分流回归信号
retry_input swipe 540 1800 540 500 400
sleep 2
adb exec-out screencap -p > "$OUT/readme-webview.png"

# ── 5.8 Markdown 编辑器（T21：blob 深链 → FileViewer Rendered → Edit）+ 提交对话框（T22）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/README.md" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 3
if wait_for_desc "Edit"; then
  tap_desc "Edit"                        # 编辑入口是 IconButton，desc=「Edit」
fi
wait_for_text "Commit" || true            # 编辑屏就绪信号（顶栏 Commit 动作）
sleep 2
adb exec-out screencap -p > "$OUT/editor.png"
# T22：同一编辑会话直接点开 Commit 对话框（需登录态），省一次 blob 深链 +
# Edit 往返；409 冲突态需并发篡改，无法确定性复现，不自动化
if [ -n "${SCREENSHOT_TOKEN:-}" ]; then
  tap_text "Commit" || true
  wait_for_text "Describe your changes…" || true   # 对话框 placeholder 出现
  sleep 1
  adb exec-out screencap -p > "$OUT/commit-dialog.png"
  adb shell input keyevent 4             # 关对话框回编辑器
fi

# ── 5.9 Sora 代码查看（T11：blob 深链直达 .kt 只读高亮——BLOB 深链多段路径已修复）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/app/src/main/java/com/yumiru11/githubapp/MainActivity.kt" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
wait_for_desc "Edit" || true              # FileViewer 就绪信号；Sora 自绘无文本节点
sleep 3
adb exec-out screencap -p > "$OUT/code-sora.png"

# ── 5.12 全局搜索（T18：历史/建议态 + 输入后结果 Tab）──
adb shell am force-stop "$PKG"; launch_app
wait_for_text "Search GitHub…" && tap_text "Search GitHub…"
wait_for_text "Search GitHub…" || true    # SearchScreen 占位符就绪
sleep 1
adb exec-out screencap -p > "$OUT/search-history.png"
adb shell input text "material"
wait_for_text "Repos" || true             # 结果 Tab 行出现 = 防抖查询完成
sleep 1
adb exec-out screencap -p > "$OUT/search-tabs.png"
adb shell input keyevent 111              # ESC 收起键盘

# ── 5.13 设置分组卡（#87）+ 5.14 通知面板（#88）合并段：
# 同一次冷启动串接——拍完设置返回 Profile，底栏切 Home 点铃铛，
# 省一整次 force-stop 冷启动（铃铛在首页顶栏，Profile 域无入口）
adb shell am force-stop "$PKG"; launch_app
wait_for_text "Profile" && tap_text "Profile"
wait_for_desc "Settings" && tap_desc "Settings"   # 顶栏齿轮是 content-desc，非文本
wait_for_text "Appearance" || true        # 分组标题渲染完成
adb exec-out screencap -p > "$OUT/settings-grouped.png"
adb shell input keyevent 4                # 返回 Profile
tap_text "Home"                           # 底栏切首页
if wait_for_desc "Notifications"; then
  tap_desc "Notifications"
fi
wait_for_text "Notifications" || true     # 面板标题滑入完成
sleep 2
adb exec-out screencap -p > "$OUT/notification-panel.png"
adb shell input keyevent 4                # back 关面板

# ── 5.15 PR Files changed 双视图 Diff（T16：unified / side-by-side）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/101" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
wait_for_text "Files changed" && tap_text "Files changed"
# 文件行默认折叠——Unified/Side-by-side 分段按钮在展开补丁后才渲染
# （此前两帧实为同一文件列表页：Unified 必然超时，重试全在列表页空转）
if wait_for_desc "Show patch"; then
  tap_desc "Show patch"
fi
wait_for_text "Unified" || true           # Diff 工具条出现 = 补丁渲染完成
sleep 3                                   # 大 patch 再留一拍绘制余量
adb exec-out screencap -p > "$OUT/pr-diff-unified.png"
tap_text "Side-by-side"
sleep 1
capture_until_changed "$OUT/pr-diff-unified.png" "$OUT/pr-diff-side-by-side.png" "Side-by-side"

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
  # 创建 Issue 表单（T14）
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 5
  wait_for_text "New issue" && tap_text "New issue"
  wait_for_text "Title" || true            # 表单字段渲染完成
  adb exec-out screencap -p > "$OUT/create-issue.png"
fi

# ── 8. 长截图：已从 PR CI 移除（与单帧信息重叠、board 不消费、~30s/条）。
# long_shot 函数保留——夜间全量/手动排查时可按需恢复调用。

# ── 9. 分域拼板（montage 网格：PR 评论贴板图而非散图，一眼扫全功能）──
# 每板 2 列网格，单帧缩放到 540 宽；缺失帧自动跳过；输出 JPEG 控制体积。
# 原始单帧 PNG 照旧上传 release 供放大排查（上传机制保持原样）。
montage_board() {
  local out="$1"; shift
  local imgs=()
  for f in "$@"; do
    if [ -f "$OUT/$f" ]; then imgs+=("$OUT/$f"); fi   # 缺失帧（如 token 段未跑）自动跳过
  done
  if [ ${#imgs[@]} -eq 0 ]; then
    echo "::warning::board $out skipped (no frames)"
    return 0
  fi
  montage "${imgs[@]}" -thumbnail 540x1170 -tile 2x -geometry +6+6 \
    -background '#161b22' "$OUT/$out" || echo "::warning::montage failed for $out"
}
# 等待开头启动的后台安装收口（失败仅告警，板图自动跳过，原始帧照常上传）
if [ -n "$APT_PID" ]; then
  wait "$APT_PID" || echo "::warning::imagemagick install failed — boards may be skipped"
fi
if command -v montage >/dev/null 2>&1; then
  montage_board board-A-home.jpg          home-light.png home-dark.png profile.png
  montage_board board-B-repo-code.jpg     repos.png repo-actions.png repo-releases.png file-tree.png readme-webview.png code-sora.png editor.png
  montage_board board-C-issue-pr-diff.jpg issue-comments.png create-issue.png pr-conversation.png pr-commits.png pr-diff-unified.png pr-diff-side-by-side.png
  # 板 D Review/Merge 骨架——T17 合入后在此追加 review-sheet/merge-box/merge-state 三帧即自动生效
  montage_board board-E-settings-notif.jpg settings-grouped.png notification-panel.png commit-dialog.png
  montage_board board-F-search.jpg        search-history.png search-tabs.png
else
  echo "::warning::ImageMagick montage not found — boards skipped, raw frames only"
fi

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
