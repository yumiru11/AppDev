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

# 按可见 content-desc tap（图标按钮无 text 时用，如顶栏铃铛 Notifications）
tap_desc() {
  local desc="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local bounds
  bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'content-desc=\"$desc\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
  if [ -n "$bounds" ]; then
    adb shell input tap $bounds >/dev/null
  else
    echo "::warning::content-desc '$desc' not found in uiautomator dump"
  fi
}

# 轮询等待文本/content-desc 出现（默认 20s）——替代裸 sleep，页面加载完才继续。
# 找到输出坐标到 /tmp/wait_bounds（供 tap 复用同一次 dump），超时返回 1 并告警。
wait_for_attr() {
  local attr="$1" value="$2" timeout="${3:-20}"
  for _ in $(seq 1 "$timeout"); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    local bounds
    bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'$attr=\"$value\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
    if [ -n "$bounds" ]; then
      echo "$bounds" > /tmp/wait_bounds
      return 0
    fi
    sleep 1
  done
  echo "::warning::timeout waiting for $attr '$value'"
  return 1
}

wait_for_text() { wait_for_attr "text" "$@"; }
wait_for_desc() { wait_for_attr "content-desc" "$@"; }

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

# ── 3. 仓库 tab（列表）──────────────────────────────────────
tap_text "Repos"
sleep 3
adb exec-out screencap -p > "$OUT/repos.png"

# ── 4. 普通 README（WebView）—— 已由下方 readme-long 长截图覆盖，此处不再单独截，避免冗余 ──

# ── 5. mermaid 仓库 README（WebView——mermaid 代码块特殊内容路径）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null
sleep 8
adb exec-out screencap -p > "$OUT/readme-mermaid.png"

# ── 5.5 导航到 Issue #71（WebView 正文已由下方 issue-long 长截图覆盖，此处仅就位供 5.6 评论区截图）─
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 8

# ── 5.6 Issue 评论（原生短文本渲染——正文 WebView 很高，滑到评论区）─
adb shell input swipe 540 1800 540 400 500
sleep 2
adb shell input swipe 540 1800 540 400 500
sleep 2
adb shell input swipe 540 1800 540 400 500
sleep 4
adb exec-out screencap -p > "$OUT/issue-comments.png"

# ── 5.7 PR 详情 Conversation（T15）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/74" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 8
adb exec-out screencap -p > "$OUT/pr-conversation.png"

# ── 5.8 PR Commits Tab（T15）──
tap_text "Commits"
sleep 4
adb exec-out screencap -p > "$OUT/pr-commits.png"

# ── 5.7 仓库操作区（T12：语言栏 Linguist + Star/Watch 游客只读）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/hoowhoami/EchoMusic" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 6
adb exec-out screencap -p > "$OUT/repo-actions.png"

# ── 5.8 仓库 Releases Tab（T12：Releases/Tags 列表）──
tap_text "Releases"
sleep 4
adb exec-out screencap -p > "$OUT/repo-releases.png"

# ── 5.9 Markdown 编辑器（T21：blob 深链 → FileViewer Rendered → Edit）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/README.md" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 6
wait_for_desc "Edit" && tap_desc "Edit"   # 编辑入口是 IconButton，desc=「Edit」
wait_for_text "Commit" || true            # 编辑屏就绪信号（顶栏 Commit 动作）
sleep 2
adb exec-out screencap -p > "$OUT/editor.png"

# ── 5.10 文件树（T11：RepoDetail Files Tab）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
sleep 6
wait_for_text "Files" && tap_text "Files"
wait_for_text "README.md" || true         # 树条目渲染完成信号（仓库根含 README.md）
adb exec-out screencap -p > "$OUT/file-tree.png"

# ── 5.11 Sora 代码查看（T11：blob 深链直达 .kt 只读高亮——BLOB 深链多段路径已修复）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/app/src/main/java/com/yumiru11/githubapp/MainActivity.kt" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
wait_for_desc "Edit" || true              # FileViewer 就绪信号；Sora 自绘无文本节点
sleep 4
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

# ── 5.13 设置页分组卡（#87 CardGroup 分组重构）──
adb shell am force-stop "$PKG"; launch_app
wait_for_text "Profile" && tap_text "Profile"
wait_for_desc "Settings" && tap_desc "Settings"   # 顶栏齿轮是 content-desc，非文本
wait_for_text "Appearance" || true        # 分组标题渲染完成
adb exec-out screencap -p > "$OUT/settings-grouped.png"

# ── 5.14 通知面板（#88：铃铛 → 右滑覆盖层；未登录为登录引导空态）──
adb shell am force-stop "$PKG"; launch_app
wait_for_desc "Notifications" && tap_desc "Notifications"
wait_for_text "Notifications" || true     # 面板标题滑入完成
sleep 2
adb exec-out screencap -p > "$OUT/notification-panel.png"
adb shell input keyevent 4                # back 关面板

# ── 5.15 PR Files changed 双视图 Diff（T16：unified / side-by-side）──
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/101" -p "$PKG" >/dev/null
wait_for_activity "$PKG" || true
wait_for_text "Files changed" && tap_text "Files changed"
wait_for_text "Unified" || true           # Diff 工具条出现 = 补丁渲染完成
sleep 3                                   # 大 patch 再留一拍绘制余量
adb exec-out screencap -p > "$OUT/pr-diff-unified.png"
tap_text "Side-by-side"
sleep 4
adb exec-out screencap -p > "$OUT/pr-diff-side-by-side.png"

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
  # 编辑器提交动作（T22：Commit 对话框；409 冲突态需并发篡改，无法确定性复现，不自动化）
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/README.md" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  wait_for_desc "Edit" && tap_desc "Edit"
  wait_for_text "Commit" && tap_text "Commit"
  wait_for_text "Describe your changes…" || true   # 对话框 placeholder 出现
  adb exec-out screencap -p > "$OUT/commit-dialog.png"
fi

# ── 8. 长截图（滚动 + 拼接，docs/research/actions-scroll-screenshot.md 方案 A）──
long_shot "readme-long.png" "https://github.com/mikepenz/multiplatform-markdown-renderer"
long_shot "issue-long.png" "https://github.com/yumiru11/AppDev/issues/71"

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
# runner 镜像已不预装 ImageMagick（PR #102 实测：montage not found → 板图全跳过）
if ! command -v montage >/dev/null 2>&1; then
  echo "::notice::installing imagemagick for board stitching"
  sudo apt-get update -qq >/dev/null 2>&1 || true
  sudo apt-get install -y -qq imagemagick >/dev/null 2>&1 || true
fi
if command -v montage >/dev/null 2>&1; then
  montage_board board-A-home.jpg          home-light.png home-dark.png profile.png
  montage_board board-B-repo-code.jpg     repos.png repo-actions.png repo-releases.png file-tree.png code-sora.png editor.png
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
