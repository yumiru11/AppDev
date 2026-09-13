#!/usr/bin/env bash
# ============================================================
# CI 模拟器截图脚本（轻量 adb 方案，零第三方依赖）
#
# 背景：Maestro 安装 3 次翻车（官方脚本不落盘 / release zip 下载失败），
# 调研证明 adb screencap + input + uimode 足够截图自动化
# （docs/research/actions-emulator-feasibility.md Q5——kzahel/kiwix/
# inaturalist 等真实 workflow 同款模式）。
# 本脚本是**唯一**截图驱动：`.maestro/screenshots/*.yaml`（5 个 flow）从未被任何
# workflow 执行，已随本轮清理删除（其画面 100% 由本脚本覆盖，见 ci.yml 截图 job）。
#
# 依赖：模拟器已由 android-emulator-runner 启动（adb 可用），
# debug APK 已装（assembleDebug 产物被 action 自动安装）。
#
# 导航说明：
# - 首页/仓库/我的 = 底部 3 tab（pixel_6 1080x2400@420dpi，NavigationBar
#   高 80dp=210px，tab 中心 y≈2295；x=180/540/900 三等分）
# - README 用深链（app 注册 github.com VIEW intent，T3）
# - 深色 = cmd uimode night + 冷启动（SYSTEM 主题模式跟随系统）
#
# ── 截图可信度（2026-09-12 修复；UI 审计 P1）───────────────────────────────
# 旧实现「等不到就 `|| true` 然后照截」，产物里分不清「拍对了但界面有问题」与
# 「根本没拍到目标屏」。实证后果（多份 release 交叉比对）：
#   - readme-webview.png 实为 Files 文件树（README 主渲染路径零回归信号）
#   - editor.png 与 commit-dialog.png md5 完全相同（2d93e91a…）且都是 README 错误屏
#   - file-tree / issue-comments / pr-commits 等帧的「就绪信号」同样被 `|| true` 吞掉
#
# 新规则（三条不可绕过）：
#   R1 **断言前置**：每一帧都经 capture_frame 拍摄，截图前必须确认已在目标屏
#      （act/text/exact/checked/desc/log 六种断言，见 lib/adb-helpers.sh）。
#   R2 **坏帧显式化**：断言不过 → 不产出正常帧，改走坏帧路径（原图改名
#      <name>.FAILED.png + 现场 UI 层级 <name>.ui.xml + bad-frames.txt + 拼板水印）。
#   R3 **md5 去重**：帧集合两两比对，像素完全相同即判后一帧为 DUPLICATE 坏帧
#      （豁免必须显式写进 lib/adb-helpers.sh 的 FRAME_MD5_EXEMPT_PAIRS 并附理由）。
# 帧的 severity 决定坏帧报 ::error:: 还是 ::warning::（见各帧 capture_frame 第 2 参）；
# 是否染红 job 只看 critical 帧，决策与理由写在脚本结尾的决策段。
#
# ── 断言选择器三条铁律（2026-09-12 第二轮修复，均有 ui.xml 实证）────────────
#   1. **placeholder 不是就绪信号**：M3 OutlinedTextField 的 placeholder 仅在
#      「聚焦且为空」时渲染（有 label 时更严格）—— 点了按钮但没点输入框，dump 里
#      只有 label（任 #250：commit-dialog）。断言用 label / 对话框标题这类恒存文本。
#   2. **selected≠checked**：Tab 用 selected=true，M3 SegmentedButton 用
#      checkable/checked=true（pr-diff 两帧假红的根因）；capture_frame 的
#      exact: 与 checked: 分开表达。
#   3. **ExtendedFAB 的文案不进 dump**（NAF 节点，text/desc 均空）——
#      wait_for_text "Comment"/"New issue" 永远等不到；改用 try_tap_fab 结构性
#      定位后断言点击后弹出的 Sheet/表单内容（issue-authed/pr-actions/create-issue）。
# ============================================================
set -euo pipefail

OUT="artifacts/screenshots"
PKG="com.yumiru11.githubapp"
mkdir -p "$OUT"

# 共用 adb 驱动 helpers（等待/点击/断言/取帧）——与 glass-verify 脚本同一份实现，
# 避免两处行为漂移（详见该文件头部说明）
# shellcheck source=lib/adb-helpers.sh
source "$(dirname "$0")/lib/adb-helpers.sh"

# 清理上一轮的帧锁（灰机复用 / 本地重跑时残留会让第一个 capture_frame 空等 300s）
rm -rf "${FRAME_LOCK_DIR:-/tmp/appdev-frame.lock}" 2>/dev/null || true

# ── 0. 安装 debug APK（android-emulator-runner 不自动装；APK 由 quality job
#    构建上传、本 job 开头已下载到工作区原位）───────────────────────────────
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm disable com.android.launcher3 --user 0 >/dev/null 2>&1 || true
# 动画时长归零：点击即时生效，进度圈/转场不再让 uiautomator 等 idle 卡死
# （dump 单次 5-10s 是本轮 CI 拖到 16min 的主放大器）
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# 宿主机能力自检：断言全靠 python3 解析 uiautomator dump，缺了就退化成
# 「无断言」——那正是本 bug 的形态，所以必须显式告警而不是静默降级。
if ! command -v python3 >/dev/null 2>&1; then
  echo "::warning::宿主机没有 python3 —— 帧断言无法判分（截图可信度不受保障）"
fi

# ── 0.5 截图登录（若提供 SCREENSHOT_TOKEN 机密）：注入只读 PAT →
# EncryptedTokenStorage（ScreenshotTokenReceiver），使 app 进入开发者模式
# （Star/评论框/PR 操作可见）。未配置机密时整段跳过，其余截图不受影响。
AUTHED=false
if [ -n "${SCREENSHOT_TOKEN:-}" ]; then
  echo "::notice::SCREENSHOT_TOKEN provided — injecting PAT for authenticated screenshots"
  adb shell am start -n "$PKG/com.yumiru11.githubapp.ScreenshotTokenReceiver" -e pat "$SCREENSHOT_TOKEN" >/dev/null 2>&1 || true
  sleep 2
  AUTHED=true
fi

# ── 0.6 冷启动耗时实测（#26 验收「冷启动 < 1.5s」的 CI 侧证据）─────────
# 用 am start -W 拿系统口径的 TotalTime（Activity 首帧），比"截图拍脑袋"可靠。
# **诚实标注**：这是模拟器（KVM + 宿主 CPU）的数字，不等于中端机；它只用于
# ① 发现回归（同一环境前后对比）② 给真机基线一个量级参考。
# 真机 <1.5s 的最终判定仍需人工走查——这是本项无法在 CI 闭环的部分。
adb shell am force-stop "$PKG"
STARTUP=$(adb shell am start -W -n "$PKG/com.yumiru11.githubapp.MainActivity" 2>/dev/null | tr -d '\r' || true)
printf '%s\n' "$STARTUP" > "$OUT/startup.txt"
COLD_TOTAL=$(printf '%s\n' "$STARTUP" | awk -F': ' '/TotalTime/ {print $2; exit}')
if [ -n "${COLD_TOTAL:-}" ]; then
  echo "::notice::cold start TotalTime=${COLD_TOTAL}ms（模拟器 API 30，非真机；用于回归对比）"
  {
    echo ""
    echo "### Cold start (emulator)"
    echo ""
    echo '```'
    printf '%s\n' "$STARTUP"
    echo '```'
  } >> "${GITHUB_STEP_SUMMARY:-/dev/null}" 2>/dev/null || true
else
  echo "::warning::am start -W 未返回 TotalTime（冷启动实测缺失）"
fi
sleep 3

# 登录态帧的前置条件判定：没 token 就**明确标 MISSING**（而不是拍一张游客态冒充
# 登录态——「拍到了登录失败的应用」比「没拍」更糟，见 assert_signed_in 注释）。
# readme-webview 正文渲染不受登录影响，无需 token。
require_token() {
  local name="$1" why="$2"
  if [ "$AUTHED" = "true" ]; then return 0; fi
  mark_missing_frame "$name" "$why（SCREENSHOT_TOKEN 未配置）"
  return 1
}

# ══════════════════════════════════════════════════════════════════════
# 1. 首页（浅色）
# ══════════════════════════════════════════════════════════════════════
# ── 1. 首页（浅色）──────────────────────────────────────────
adb shell cmd uimode night no
adb shell am force-stop "$PKG"
launch_app
# 游客态首页：底栏 3 tab 常驻（Home/Repos/Profile），断言它存在＝首页真的画出来了
capture_frame home-light critical 0 \
  act:"$PKG" text:"Profile" text:"Repos"

# ══════════════════════════════════════════════════════════════════════
# 2. 首页（深色：uimode + 冷启动）
# ══════════════════════════════════════════════════════════════════════
adb shell cmd uimode night yes
adb shell am force-stop "$PKG"
launch_app
capture_frame home-dark warn 0 \
  act:"$PKG" text:"Profile" text:"Repos"

# ══════════════════════════════════════════════════════════════════════
# 3. 仓库 tab（列表）
# ══════════════════════════════════════════════════════════════════════
tap_text "Repos"
capture_frame repos warn 3 \
  act:"$PKG" exact:"Repos"
# 游客态仓库 tab 是登录引导占位（无 token 时不该出现仓库列表）。
# 断言从 opt 升为必需：本帧的目的就是「游客看到登录引导」，引导缺失时帧本身
# 无价值（opt 会让帧静默通过）。文案在 ReposScreen.kt:204 无条件渲染，可当硬门槛。
if [ "$AUTHED" = "false" ]; then
  capture_frame repos-guest warn 1 \
    act:"$PKG" text:"Sign in to see your repositories"
else
  # 登录态下该帧**按设计不适用**（它验证的正是「游客看到登录引导」）。显式写 SKIPPED
  # 标记而不是什么都不留：ci.yml 的 Verify screenshots collected 要求「每一帧都有产出
  # 或显式处置」，留空会被判成漏拍（GATE-5b 的收集校验）。不进 bad-frames.txt
  # （不是坏帧也不是 MISSING）。
  {
    echo "frame: repos-guest"
    echo "kind: SKIPPED"
    echo "severity: info"
    echo "reason: 仅游客态拍摄（SCREENSHOT_TOKEN 已配置，登录态下该屏为仓库列表）"
  } > "$OUT/repos-guest.skipped.txt"
fi

# ══════════════════════════════════════════════════════════════════════
# 4-5. README 正文（WebView 主渲染路径，ADR-0007）——本 bug 的核心修复点
# ══════════════════════════════════════════════════════════════════════
# 为什么这里换仓库：README 帧必须拍到**正文**，而 yumiru11/AppDev 仓库**没有
# README**（唯一真源 `git ls-tree`：根目录只有 AGENTS.md/plan.md/CONTEXT.md…
# → GET /repos/.../readme 返回 404 → ReadmeState.Empty，README tab 恒显示
# 「No README」）。拿 AppDev 拍 README 帧等于永远拍不到正文。
# mermaid-js/mermaid：README 达 24KB/485 行（CI 日志实证），且脚本本来就用它拍
# readme-mermaid 帧；这一帧给出「正文真的渲染出来了」的稳定回归信号。
# 断言三件套（强断言在前，文本探针可选）：
#   exact:README         —— README tab 处于 selected=true（旧的 readme-webview
#                           拍的正是 Files tab，只看文字「README」无法发现）
#   log:ReadmeRender.*repo=mermaid-js/mermaid —— RepoRepository 在本帧窗口内打出
#                            「readme 元数据取到了」的日志（正文渲染链路的机器证据）
#   opt:text:Mermaid     —— WebView 正文文本是否进 uiautomator dump 依 WebView 版本而定
#                            （WebView 内容不属于原生 view hierarchy，文本不可依赖），
#                            故降级为可选探针：命中是加分，不命中不算坏帧。
adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null
capture_frame readme-webview critical 5 \
  act:"$PKG" exact:"README" opt:log:"ReadmeRender.*renderMode=" opt:text:"Mermaid"

# ── 4-6. README 图表区（readme-mermaid）────────────────────────────────────
# 帧目的：README 正文**滚到图表区**——同时给出两个机器证据：① 正文可滚动（WebView
# 内滚动链路可用）② mermaid 代码块/渲染图确实出现在正文里（离线 mermaid 渲染的
# 端到端锚点。本 job 是 API 30 / WebView 83，低于 mermaid 的 Chromium ≥94 注入门禁，
# 图按设计回退为代码块——帧固定在「图表所在区」，渲染与否都落在这里）。
#
# 旧实现为什么拍假帧（CI 34740229533、34741143272 实证：readme-mermaid 与
# readme-webview 逐字节相同，md5 d5afb68c / c241871a；8 轮里坏 2 轮）：
#   1. 单次 `swipe 540 1800 → 540 600` 的起点距 WebView 顶边（首屏 ~1620px）只有
#      ~180px，手势绝大部分落在仓库头/Tab 上——有效位移 ≈ 手势落在 WebView 内的部分，
#      就算「成功」也只滚到 README hero 图，永远到不了图表区；
#   2. 滚动位置只稳定数秒：滑动触发重组 → WebView 重载 → 弹回顶部（logcat 实证）。
#      是否拍到全看时序 → 偶发 DUPLICATE（同一实现时好时坏）。
# 新实现四条硬约束：
#   1. **视口内短滑**：起点 2150 / 终点 1750 全程留在 WebView 视口内（仓库头展开时
#      WebView 约 1620→2400，收起后上移；两种状态都覆盖）→ 位移可预期、可累加；
#   2. **滚到「图表区在屏」为止**（闭环，不是盲滑固定次数）——判据见
#      readme_mermaid_target_state；
#   3. **滑动与截图之间不插 dump**：取帧走 capture_frame 的 `settle=now` 路径
#      （先截图后断言），dump 的 5-15s 挪到截图之后（窗口冲突见该函数头注释）；
#   4. **滚不到就显式判坏**：绝不把「没滚到位置」的帧当通过（旧实现正是这样骗过审计的）。
# 视口内短滑：起点/终点都留在 WebView 视口内（见上方约束 1）。每 2 次检查前台——
# ⚠️ 实测（CI 34743676596）：jank 下 `input swipe` 会退化成点击（InputDispatcher 日志
# 显示那条手势的 UP 被 2.9s 后当 click 处理），点到 README 里的链接后 CustomTabs 把
# 外部浏览器拉起来，后面的滑动全部喂给浏览器 → 判据 state=none。前台丢了就回退。
readme_mermaid_swipe() {
  local n="$1" dir="${2:-up}" i y_from y_to
  if [ "$dir" = "down" ]; then
    y_from=1750
    y_to=2150
  else
    y_from=2150
    y_to=1750
  fi
  for ((i = 0; i < n; i++)); do
    retry_input swipe 540 "$y_from" 540 "$y_to" 550
    sleep 0.15
    if [ $((i % 2)) -eq 1 ]; then readme_mermaid_recover; fi
  done
}

# 前台还是本应用吗？（同 wait_for_activity 的判据，但只看当前状态）
readme_mermaid_in_app() {
  adb shell dumpsys activity activities 2>/dev/null | grep -q "mResumedActivity.*$PKG"
}

# 重新深链打开 README（滚动位置回到顶部 → 置 RESET 标志，定位循环按「从头再来」处理）
readme_mermaid_open_readme() {
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/mermaid-js/mermaid" -p "$PKG" >/dev/null 2>&1 || true
  wait_for_activity_quick "$PKG" || true
  sleep 4
  README_MERMAID_RESET=1
}

# 前台被外部浏览器接管时回退：先 BACK（保留 README 滚动位置）；仍回不来才重开。
readme_mermaid_recover() {
  readme_mermaid_in_app && return 0
  echo "::warning::readme-mermaid 前台不是本应用（短滑退化成点击、打开了外链？）——尝试 BACK 回退"
  adb shell input keyevent 4 >/dev/null 2>&1 || true
  sleep 2
  readme_mermaid_in_app && return 0
  echo "::warning::readme-mermaid BACK 未回退——重新深链打开 README（滚动位置从头再来）"
  readme_mermaid_open_readme
  return 0
}

# 「图表区在屏」判据 = 本探针的滚动闭环信号。
# ⚠️ 关键实测（CI 34743057536 的诊断 dump）：WebView 正文**全文**都在 uiautomator
# dump 里（含不可见部分）——所以 `text:` 类断言对滚动位置零判别力（`opt:text:Mermaid`
# 恒真就是这个原因）；但**只有真正落在屏内的文本节点有非零 bounds**，不可见节点是
# [0,0][0,0]。于是「目标节点的 bounds 是否非零」是滚动位置唯一可靠、与渲染通道无关
# 的信号。
# 输出：`target <top> <bottom>`＝图表区在屏（附目标区包围盒）；
#      `overshoot`＝已滚过头（屏内出现后一段「Sequence diagram」标题）；
#      `none`＝不在屏（既没到也没过）。
readme_mermaid_target_state() {
  # dump_ui 的失败告警走 stdout——必须转 stderr，否则会混进本函数的单行返回值
  if ! dump_ui >&2; then
    echo "none"
    return 0
  fi
  python3 - <<'PY' 2>/dev/null || echo "none"
import re
xml = open('/tmp/ui.xml', encoding='utf-8').read()
pat = re.compile(r'<node[^>]*text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
tops, bottoms, overshoot = [], [], False
for text, _l, top, _r, bottom in pat.findall(xml):
    top, bottom = int(top), int(bottom)
    if bottom - top <= 0:
        continue
    if 'Flowchart [' in text or 'A[Hard]' in text:
        tops.append(top)
        bottoms.append(bottom)
    elif 'Sequence diagram [' in text or 'Gantt chart [' in text:
        overshoot = True
if tops:
    print('target %d %d' % (min(tops), max(bottoms)))
elif overshoot:
    print('overshoot')
elif 'Table of content' in xml:
    print('none')
else:
    print('noreadme')
PY
}

# 一次定位尝试：先按 CI 校准值推进（14 次短滑 ≈ 图表区入口），再按判据微调。
# 每次迭代一次 dump（5-15s）——只做位置确认，不参与取帧。
# 返回 0 = 图表区已在视口且位置合适；1 = 5 次校正后仍未到位。
readme_mermaid_position_once() {
  local i state top
  README_MERMAID_RESET=0
  readme_mermaid_swipe 14
  for i in 1 2 3 4 5; do
    if [ "$README_MERMAID_RESET" = "1" ]; then
      README_MERMAID_RESET=0
      readme_mermaid_swipe 14
      continue
    fi
    read -r state top _ <<<"$(readme_mermaid_target_state)"
    echo "::notice::readme-mermaid 定位 $i/5：state=$state top=${top:-n/a}"
    case "$state" in
      target)
        if [ "${top:-0}" -lt 400 ]; then
          readme_mermaid_swipe 2 down
        elif [ "${top:-0}" -gt 1700 ]; then
          readme_mermaid_swipe 2
        else
          return 0
        fi
        ;;
      overshoot) readme_mermaid_swipe 3 down ;;
      noreadme) readme_mermaid_open_readme ;;
      *) readme_mermaid_swipe 3 ;;
    esac
  done
  return 1
}

# 定位失败（中途被外链/重载打断）就再来一轮；两轮都失败 → 显式坏帧。
if readme_mermaid_position_once || { readme_mermaid_recover; readme_mermaid_position_once; }; then
  capture_frame readme-mermaid critical now act:"$PKG" exact:"README"
else
  FRAME_SEVERITY=critical
  mark_bad_frame readme-mermaid FAILED "两轮滚动定位后图表区仍未进入视口——不拿无关注的滚动帧当通过"
fi

# ══════════════════════════════════════════════════════════════════════
# 5.5-5.6 Issue 详情 → 评论列表（原生短文本渲染）
# ══════════════════════════════════════════════════════════════════════
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null
# 先确认 Issue 详情自己到位（旧脚本这里 `wait_for_activity || true` 吞掉后直接滑屏，
# 一旦详情没打开，滑的是上一屏、拍到的是无关注面）
capture_frame issue-detail warn 5 \
  act:"$PKG" text:"yumiru11/AppDev" text:"Open"
retry_input swipe 540 1800 540 400 500
retry_input swipe 540 1800 540 400 500
retry_input swipe 540 1800 540 400 500
capture_frame issue-comments warn 3 \
  act:"$PKG" text:"yumiru11/AppDev"

# ══════════════════════════════════════════════════════════════════════
# 5.7 PR 详情 Conversation + Commits Tab（T15）
# ══════════════════════════════════════════════════════════════════════
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/74" -p "$PKG" >/dev/null
capture_frame pr-conversation warn 5 \
  act:"$PKG" text:"yumiru11/AppDev" exact:"Conversation"
tap_text "Commits"
capture_frame pr-commits warn 3 \
  act:"$PKG" exact:"Commits"

# ══════════════════════════════════════════════════════════════════════
# 5.7b 仓库详情三连帧（T11/T12）：操作区 / Releases / Files 树
# ══════════════════════════════════════════════════════════════════════
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev" -p "$PKG" >/dev/null
# 仓库详情默认 tab = README，故 repo-actions 帧同时是「AppDev README 空态」的留档：
# 这里**要求** "No README" —— 仓库确实没有 README，拍到的就是真实状态；
# 哪天仓库补了 README，这条断言会失败并把人叫来看（比静默拍个空态强）。
# 2026-09-12 补 desc:"Avatar" 头部存在性闸门：此前本帧只断言 tab/文案，仓库头
# （UI-C01）整块缺失时照样「通过」—— 探针空洞。Avatar 是仓库头卡片的恒定语义
# （AsyncImage contentDescription），与登录态无关，适合当锚点。
capture_frame repo-actions warn 4 \
  act:"$PKG" text:"yumiru11/AppDev" exact:"README" desc:"Avatar" opt:text:"No README"
tap_text "Releases"
capture_frame repo-releases warn 3 \
  act:"$PKG" exact:"Releases" text:"yumiru11/AppDev"
tap_text "Files"
# 树条目就绪信号（旧写法 `wait_for_text ".github" || true` 被吞掉 → 树没出来也照截）
capture_frame file-tree warn 3 \
  act:"$PKG" exact:"Files" text:".github"

# ══════════════════════════════════════════════════════════════════════
# 5.8 Markdown 编辑器（T21）+ 提交对话框（T22）——另一个本 bug 的修复点
# ══════════════════════════════════════════════════════════════════════
# 旧实现两个错误叠加：
#   ① 深链打的是 blob/main/README.md —— **该文件不存在**（仓库无 README），
#      FileViewState.Error → 顶栏没有 Edit 按钮（EditFileButton 只在 Loaded 且
#      CODE/MARKDOWN 时渲染）→ 编辑态永远进不去；
#   ② `wait_for_text "Commit" || true` 把失败吞掉后照截 → editor.png 拍到的是
#      README 错误屏；commit-dialog.png 没有断言、又没真的点开对话框 → 与
#      editor.png 像素完全相同（md5 2d93e91a…）。
# 修法：改用**确实存在**的 AGENTS.md（根目录，git ls-tree 实证）承载「打开 →
# 进编辑态 → 点开提交对话框」链；并把 ①② 都变成硬断言。
# 可达性：EditFileButton 需 `editable = state.isLoggedIn` → 无 token 时编辑器
# **确实不可达**，如实标 MISSING，不伪造一个看起来对的帧。
#
# 2026-09-12 有界等待改造（为什么本帧仍可能合法地判 FAILED）：
#   - editor：由「settle 3s + 12s 断言窗」改为「30s 有界轮询顶栏 Commit 动作
#     （出现即早退）→ 再取帧」——把「探针抢跑」与「编辑态真的进不去」分开；
#   - commit-dialog：同样先有界等待对话框占位文案（15s）再取帧；editor 未起来时
#     **不再盲点 Commit**，直接标 MISSING（依赖帧缺失 ≠ 该帧自身失败）；
#   - ⚠️ 静态接线追踪已确认（file:line 见 PR）：blob 深链 → MainActivity.BlobRoute
#     （app/.../MainActivity.kt:597-633）直接渲染 FileViewerScreen，**不消费**
#     RepoFilesUiState.editState；FileEditScreen 的渲染分支只存在于
#     RepoDetailScreen.kt:224。即：深链路径点 Edit 后屏幕不切换、Commit 永不出现。
#     该 FAILED 是**真实缺陷**（深链文件编辑入口死路），不是探针问题；
#     修复应在 BlobRoute 补 editState → FileEditScreen 分支（需另开票）。
if require_token editor "编辑器链路需登录态（Edit 按钮仅在 LoggedIn 时渲染）"; then
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/AGENTS.md" -p "$PKG" >/dev/null
  # FileViewer 顶栏标题 = 选中路径：它出现即「blob 深链真的加载了文件」
  capture_frame file-viewer warn 4 \
    act:"$PKG" text:"AGENTS.md" desc:"Edit"
  if tap_desc "Edit"; then
    # 编辑态就绪判据 = FileEditScreen 顶栏的 Commit 动作（TextButton，文本恰为
    # repo_file_commit="Commit"）。有界等待 30s + 1s 轮询，出现即早退：
    # - 命中：编辑态确实到了（此后 capture_frame 自己的断言仍然兜底）；
    # - 超时：30s 足够排除「导航/首帧/草稿回填慢」，此时的 FAILED 是真失败。
    if wait_for_text "Commit" 30; then
      capture_frame editor warn 1 \
        act:"$PKG" text:"Commit" text:"AGENTS.md"
      # T22：同一编辑会话打开提交对话框。
      # #250 修正：旧探针等的是 placeholder「Describe your changes…」，但 M3
      # OutlinedTextField 的 placeholder **只在聚焦且为空时**渲染（本字段还有 label
      # 「Commit message」顶着）——只点顶栏 Commit 不点输入框时层级里根本没有该文案，
      # 15s 必然超时（CI run 34698275775 的 commit-dialog.ui.xml 实证：对话框已打开，
      # EditText focused="false"，全部文本 = Commit message / Commit to current branch
      # (main) / Create a new branch / Commit / Cancel）。
      # 改用对话框打开后**必然存在**的 label 文案做就绪判据 + 帧断言。
      if tap_text "Commit" && wait_for_attr_sub "text" "Commit message" 15; then
        capture_frame commit-dialog warn 1 \
          act:"$PKG" text:"Commit message" text:"Commit to current branch"
      else
        mark_bad_frame commit-dialog FAILED "点了 Commit 后 15s 内提交对话框未出现（label「Commit message」缺失）"
      fi
    else
      mark_bad_frame editor FAILED "点了 Edit 后 30s 内编辑态未出现（顶栏 Commit 动作缺失）"
      mark_missing_frame commit-dialog "依赖编辑器帧：编辑态未出现，提交对话框无法打开"
    fi
    adb shell input keyevent 4             # 关对话框回编辑器
  else
    mark_missing_frame editor "FileViewer 的 Edit 按钮未找到（文件未加载成功或无写权限）"
    mark_missing_frame commit-dialog "依赖编辑器帧，编辑器未进入"
  fi
else
  mark_missing_frame file-viewer "依赖登录态（编辑器链路不可达）"
fi

# ══════════════════════════════════════════════════════════════════════
# 5.9 Sora 代码查看（T11：blob 深链直达 .kt 只读高亮）
# ══════════════════════════════════════════════════════════════════════
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/blob/main/app/src/main/java/com/yumiru11/githubapp/MainActivity.kt" -p "$PKG" >/dev/null
# 该文件真实存在 → 不依赖登录态即可断言到路径标题；Sora 自绘无文本节点，
# 故再叠一个「就绪探针」desc:Edit（登录态才可见）作为可选探针。
capture_frame code-sora warn 4 \
  act:"$PKG" text:"MainActivity.kt" opt:desc:"Edit"

# ══════════════════════════════════════════════════════════════════════
# 5.12 全局搜索（T18：历史/建议态 + 输入后结果 Tab）
# ══════════════════════════════════════════════════════════════════════
adb shell am force-stop "$PKG"; launch_app
if wait_for_text "Search GitHub…"; then
  tap_text "Search GitHub…"
  capture_frame search-history warn 2 \
    act:"$PKG" text:"Search GitHub…"
  adb shell input text "material"
  # 结果 Tab 行出现 = 防抖查询完成（旧写法 `wait_for_text "Repos" || true` 被吞掉，
  # 输入没生效也照截 → 两帧同 md5 的经典成因）
  capture_frame search-tabs warn 4 \
    act:"$PKG" text:"Repos"
  adb shell input keyevent 111              # ESC 收起键盘
else
  mark_missing_frame search-history "首页搜索入口未找到"
  mark_missing_frame search-tabs "搜索输入未进入，结果帧无意义"
fi

# ══════════════════════════════════════════════════════════════════════
# 5.13 设置分组卡（#87）+ 5.14 通知面板（#88）
# ══════════════════════════════════════════════════════════════════════
adb shell am force-stop "$PKG"; launch_app
if wait_for_text "Profile"; then
  tap_text "Profile"
  if wait_for_desc "Settings"; then
    tap_desc "Settings"
    capture_frame settings-grouped warn 3 \
      act:"$PKG" text:"Appearance"
    adb shell input keyevent 4              # 返回 Profile
    tap_text "Home"                         # 底栏切首页（铃铛在首页顶栏）
    if wait_for_desc "Notifications"; then
      tap_desc "Notifications"
      # 「Mark all read」是面板头部的按钮（NotificationsPanel.kt:245），
      # 比裸 "Notifications"（底栏也有同名节点）更能证明面板真的滑出来了
      capture_frame notification-panel warn 3 \
        act:"$PKG" text:"Mark all read"
      adb shell input keyevent 4            # back 关面板
    else
      mark_missing_frame notification-panel "首页顶栏通知入口未找到"
    fi
  else
    mark_missing_frame settings-grouped "Profile 页顶栏设置入口未找到"
    mark_missing_frame notification-panel "依赖设置段导航（未走到首页）"
  fi
else
  mark_missing_frame settings-grouped "冷启动后底栏 Profile 未出现"
  mark_missing_frame notification-panel "冷启动后底栏未就绪"
fi

# ══════════════════════════════════════════════════════════════════════
# 5.15 PR Files changed 双视图 Diff（T16：unified / side-by-side）
# ══════════════════════════════════════════════════════════════════════
adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/101" -p "$PKG" >/dev/null
if wait_for_text "Files changed"; then
  tap_text "Files changed"
  # 文件行默认折叠——Unified/Side-by-side 分段按钮在展开补丁后才渲染
  # （此前两帧实为同一文件列表页：Unified 必然超时，重试全在列表页空转）
  if wait_for_desc "Show patch"; then
    tap_desc "Show patch"
  fi
  # M3 SingleChoiceSegmentedButtonRow 的选中态是 checkable/checked=true（radio 语义），
  # 不是 selected —— 旧断言用 exact:（=selected）必然假红。CI run 34698275775 的
  # pr-diff-unified.ui.xml 实证：选中段 checkable=true checked=true、selected=false，
  # 且 checked 随点击在两段间移动（side-by-side 帧里移到右段）。
  capture_frame pr-diff-unified warn 4 \
    act:"$PKG" checked:"Unified" text:"Side-by-side"
  tap_text "Side-by-side"
  # 换段两件套证据：① 帧必须与 unified 不同（capture_until_changed，其内部重按重截）；
  # ② Side-by-side 段必须真的进入 checked=true —— 否则这帧只是 unified 的复制品，
  #    宁可标坏帧也不产出一张看起来对的图。
  capture_until_changed "$OUT/pr-diff-unified.png" "$OUT/pr-diff-side-by-side.png" "Side-by-side" || true
  sel_rc=0
  if [ -f "$OUT/pr-diff-side-by-side.png" ]; then
    assert_checked_holds "Side-by-side" 12 || sel_rc=$?
  else
    sel_rc=1
  fi
  if [ "$sel_rc" -eq 0 ]; then
    record_md5 pr-diff-side-by-side "$OUT/pr-diff-side-by-side.png"
  elif [ "$sel_rc" -eq 2 ]; then
    # 宿主机无 python3：无法判定选中态（整脚本已在开头告警），保留帧但记为不确定
    echo "::warning::无法判定 Side-by-side 选中态（缺少 python3）—— 该帧未纳入 md5 去重"
  else
    rm -f "$OUT/pr-diff-side-by-side.png"
    mark_bad_frame pr-diff-side-by-side FAILED "Side-by-side 段未进入 checked=true（分段切换未生效，帧与 unified 无区别）"
  fi
else
  mark_missing_frame pr-diff-unified "PR 详情页未出现 Files changed Tab"
  mark_missing_frame pr-diff-side-by-side "依赖 unified 帧"
fi

# ══════════════════════════════════════════════════════════════════════
# 6. 我的 tab（force-stop 冷启动回首页——am start 对已在前台 app 不重置
#    导航栈，深链页仍在前台导致 uiautomator 拿不到底栏）
# ══════════════════════════════════════════════════════════════════════
adb shell cmd uimode night no
adb shell am force-stop "$PKG"
launch_app
tap_text "Profile"
capture_frame profile warn 3 \
  act:"$PKG" exact:"Profile"

# ══════════════════════════════════════════════════════════════════════
# 7. 登录后段（需 SCREENSHOT_TOKEN）：Star 按钮 / 评论框 / PR 操作可见
# ══════════════════════════════════════════════════════════════════════
# 先证明「登录态确实生效」：Profile 页应出现登录名（同一份断言，glass-verify 也在用）
if [ "$AUTHED" = "true" ] && assert_signed_in; then
  # 仓库详情（Star 按钮；登录态下 Star 是文本按钮）
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev" -p "$PKG" >/dev/null
  # 真实缺陷闸门（UI-C01，2026-09-11 审计 §1.4）：仓库头（Avatar/描述/统计/
  # Star·Watch·Fork）在初始进入（未滚动）时**必须可见**；实证全屏缺失（截图
  # repo-actions/readme-webview/repo-releases 顶栏下方直接是 Tab 行；dump 里无
  # Avatar/Star 等任何头部节点），疑为 #167「README 下滑收起头部」的零高度测量
  # 反馈回路（headerHeightPx 初值 0 → 外层 height(0) 约束下内层测不出自然高度）。
  # 该缺陷属 app 侧（feature/repo），不在本探针 PR 修；这里只把失败原因说清楚，
  # **仍然判坏帧**（不弱化、不销声）。
  if ! wait_for_desc "Avatar" 5; then
    FRAME_SEVERITY="warn"
    mark_bad_frame repo-star FAILED "仓库头整体未渲染（Avatar/描述/统计/Star·Watch·Fork 全缺）—— 真实缺陷 UI-C01：初始未滚动态头部不应收起；同屏 repo-actions/readme-webview 亦有同证"
  else
    capture_frame repo-star warn 5 \
      act:"$PKG" text:"yumiru11/AppDev" text:"Star"
  fi
  # Issue 详情 → 评论输入 Sheet。
  # 旧探针等「Write a comment…」(issue_comment_hint)：该字符串在代码里**零引用**
  # （全仓 grep 只有 strings.xml 定义）—— 评论入口是右下 ExtendedFAB（文案
  # 「Comment」），而 ExtendedFAB 的文案不进 uiautomator dump（NAF 节点，ci run
  # 34698275775 的 issue-authed.ui.xml 实证：clickable 节点 text='' desc=''），
  # 旧断言必然 FAILED。改为：结构性定位 FAB → 点开输入 Sheet → 断言 Sheet 特有文案。
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues/71" -p "$PKG" >/dev/null
  if wait_for_text "Open"; then
    if try_tap_fab; then
      # Sheet 内必现文本：Edit/Preview 双 Tab（MarkdownComposer）+ Cancel（issue_comment_cancel）
      if wait_for_attr_sub "text" "Preview" 10 && wait_for_attr_sub "text" "Cancel" 5; then
        capture_frame issue-authed warn 2 \
          act:"$PKG" text:"Preview" text:"Cancel" opt:text:"Comment"
      else
        FRAME_SEVERITY="warn"
        mark_bad_frame issue-authed FAILED "评论 FAB 已点击但输入 Sheet 未出现（Preview/Cancel 均缺失——MarkdownComposer 未渲染）"
      fi
    else
      # FAB 受 canComment 门控（IssueDetailScreen.kt:207）：无权评论时入口按设计隐藏 → MISSING
      mark_missing_frame issue-authed "Issue 详情已加载但评论入口（右下 FAB）不存在——canComment=false（无评论权限）"
    fi
  else
    mark_missing_frame issue-authed "Issue 详情未加载（依赖帧不可达）"
  fi
  adb shell input keyevent 4   # 收起评论 Sheet，避免影响后续帧
  # PR 详情 → 评论输入 Sheet。
  # 旧探针等 placeholder「Leave a comment…」在**详情页本体**出现：该 placeholder
  # 只在 CommentInputSheet 的 OutlinedTextField 里（PullRequestDetailScreen.kt:1161），
  # 详情页本体只有 FAB（pull_request_comment="Comment"，同样不进 dump）。
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/pull/73" -p "$PKG" >/dev/null
  if wait_for_text "Conversation"; then
    if try_tap_fab; then
      # Sheet 标题「Add comment」(pull_request_add_comment) 是打开后必现文本；
      # placeholder 是否渲染依 M3 聚焦规则，降级为可选探针。
      if wait_for_attr_sub "text" "Add comment" 10; then
        capture_frame pr-actions warn 2 \
          act:"$PKG" text:"Add comment" opt:text:"Leave a comment…"
      else
        FRAME_SEVERITY="warn"
        mark_bad_frame pr-actions FAILED "评论 FAB 已点击但输入 Sheet 未出现（Add comment 标题缺失）"
      fi
    else
      # PR 评论 FAB 在 Success 态无条件渲染（PullRequestDetailScreen.kt:264）——缺失即缺陷
      FRAME_SEVERITY="warn"
      mark_bad_frame pr-actions FAILED "PR 详情已加载但评论 FAB 不存在（无条件渲染的入口缺失）"
    fi
  else
    mark_missing_frame pr-actions "PR 详情未加载（依赖帧不可达）"
  fi
  adb shell input keyevent 4   # 收起评论 Sheet
  # 创建 Issue 表单（T14）。
  # 同 FAB 问题：列表页 FAB 文案「New issue」不进 dump（ExtendedFAB 语义），旧
  # wait_for_text "New issue" 永远超时 → 帧被误标 MISSING。改为点 FAB 后断言表单。
  adb shell am start -a android.intent.action.VIEW -d "https://github.com/yumiru11/AppDev/issues" -p "$PKG" >/dev/null
  if wait_for_text "Open"; then
    if try_tap_fab; then
      # 表单标签 Title/Body（issue_create_title_label / body_label）是打开后必现文本
      capture_frame create-issue warn 4 \
        act:"$PKG" text:"Title" text:"Body"
    else
      # 列表页 FAB 无条件渲染（IssueListScreen.kt:110）——缺失即缺陷
      FRAME_SEVERITY="warn"
      mark_bad_frame create-issue FAILED "Issue 列表页已加载但 New issue FAB 不存在（无条件渲染的入口缺失）"
    fi
  else
    mark_missing_frame create-issue "Issue 列表页未加载（依赖帧不可达）"
  fi
else
  echo "::warning::登录态未确认（无 SCREENSHOT_TOKEN，或 SCREENSHOT_TOKEN 已失效）——跳过登录后帧并标记 MISSING"
  for f in repo-star issue-authed pr-actions create-issue; do
    mark_missing_frame "$f" "未确认登录态（SCREENSHOT_TOKEN 缺失或已失效）"
  done
fi

# ══════════════════════════════════════════════════════════════════════
# 8. 长截图：已从 PR CI 移除（与单帧信息重叠、board 不消费、~30s/条）。
# long_shot 函数保留——夜间全量/手动排查时可按需恢复调用。
# ══════════════════════════════════════════════════════════════════════

# ══════════════════════════════════════════════════════════════════════
# 9. md5 去重断言（R3）：两两比对，像素相同 → 后一帧判 DUPLICATE
# ══════════════════════════════════════════════════════════════════════
check_duplicate_frames

# ══════════════════════════════════════════════════════════════════════
# 10. 分域拼板（montage 网格）——坏帧打水印，避免「一眼看不出哪帧不可信」
# ══════════════════════════════════════════════════════════════════════
# 每板 2 列网格，单帧缩放到 540 宽；缺失帧替换为明确标注的占位图（不再静默跳过，
# 静默跳过正是「产物里看不出少了哪帧」的原因）；坏帧叠 MISSING/FAILED/DUPLICATE
# 水印；每格下方标注 帧名 + 状态。输出 JPEG 控制体积。
# 原始单帧 PNG 照旧上传 release 供放大排查（上传机制保持原样）。
bad_frame_info() {
  # 输出 "<KIND>|<原因>"；非坏帧输出空
  # 注意：两个变量分开声明——同一 local 里 $name 此时尚未生效（shellcheck SC2318）
  local name="$1"
  local f="$OUT/$name.badframe.txt"
  [ -f "$f" ] || return 0
  printf '%s|%s' \
    "$(sed -n 's/^kind: //p' "$f" | head -1)" \
    "$(sed -n 's/^reason: //p' "$f" | head -1)"
}

# 坏帧水印（叠在帧内），MISSING 无原图则生成占位图
watermarked() {
  local name="$1" info kind reason src tmp
  info=$(bad_frame_info "$name")
  src="$OUT/$name.png"
  if [ -z "$info" ]; then
    [ -f "$src" ] && printf '%s' "$src"
    return 0
  fi
  kind="${info%%|*}"; reason="${info#*|}"
  tmp="${TMPDIR:-/tmp}/wm-$name.png"
  if [ ! -f "$src" ]; then
    # MISSING / 断言失败未留原始帧 → 生成占位图，让 review 的人看到「这帧没拿到」
    # 标 kind（FAILED/MISSING/DUPLICATE）而不是写死 MISSING——DUPLICATE 帧的原始 PNG
    # 已被改名，走的就是这条分支，写死 MISSING 会误导 review
    convert -size 540x1170 xc:'#3a1d1d' \
      -fill '#ffb4ab' -pointsize 54 -gravity center -annotate +0-40 "$kind" \
      -fill '#ffb4ab' -pointsize 26 -annotate +0+40 "$name" \
      -fill '#ffd7d4' -pointsize 20 -gravity south -annotate +0+200 "${reason:0:46}" \
      "$tmp" 2>/dev/null || return 0
  else
    convert "$src" -resize 540x \
      -fill 'rgba(220,40,40,0.55)' -gravity center -pointsize 72 -annotate +0+0 "$kind" \
      "$tmp" 2>/dev/null || return 0
  fi
  printf '%s' "$tmp"
}

montage_board() {
  local out="$1"; shift
  local imgs=() tiles=() f base wm info label tmp
  for f in "$@"; do
    # 帧名契约：watermarked()/bad_frame_info() 收「无扩展名帧名」（内部拼 $OUT/$name.png 与
    # $OUT/$name.badframe.txt）。这里曾直接把带 .png 的 $f 传进去 → 实际查找
    # $OUT/home-light.png.png 恒不存在 → 每格退化为 NOT CAPTURED 占位图、且无任何告警
    # （2026-09-13 事故：boards 从上线起就没显示过真实帧）。保留 $f 仅用于 label 显示。
    base="${f%.png}"
    wm=$(watermarked "$base")
    if [ -n "$wm" ]; then
      imgs+=("$wm")
      info=$(bad_frame_info "$base")
      if [ -n "$info" ]; then
        label="$f [${info%%|*}]"
      else
        label="$f"
      fi
    else
      label="$f [NOT CAPTURED]"
      # 帧文件存在却没进板 = 取帧契约被破坏（脚本 bug），不是「没拍到」。这种坏法会静默
      # 把整张板变成占位图，必须显式拦下（末尾汇总后 job 红）。
      if [ -f "$OUT/$base.png" ]; then
        echo "::error::board $out: $OUT/$base.png 存在但未被取用 —— 拼板取帧契约破坏"
        BOARD_LOOKUP_MISMATCH=$((BOARD_LOOKUP_MISMATCH + 1))
      fi
      # 占位图必须落成**文件**再交给 montage：$(convert ... png:-) 会被命令替换
      # 吃掉 NUL 字节导致 PNG 损坏（本脚本首版就踩了）
      tmp="${TMPDIR:-/tmp}/placeholder-$out-$f.png"
      if convert -size 540x1170 xc:'#2b2b2b' -fill '#eeeeee' \
        -pointsize 34 -gravity center -annotate +0+0 'NOT CAPTURED' "$tmp" 2>/dev/null; then
        imgs+=("$tmp")
      else
        echo "::warning::placeholder for $f failed — skipped in board $out"
      fi
    fi
    tiles+=("$label")
  done
  if [ ${#imgs[@]} -eq 0 ]; then
    echo "::warning::board $out skipped (no frames)"
    return 0
  fi
  # 逐格标注帧名 + 状态（坏帧在格下方也能看出是 FAILED/MISSING/DUPLICATE）
  local args=()
  local i
  for i in "${!imgs[@]}"; do
    args+=(-label "${tiles[$i]}" "${imgs[$i]}")
  done
  montage "${args[@]}" -thumbnail 540x1170 -tile 2x -geometry +6+6 \
    -background '#161b22' -fill '#e6edf3' -pointsize 20 "$OUT/$out" \
    || echo "::warning::montage failed for $out"
}

# 等待开头启动的后台安装收口（失败仅告警，板图自动跳过，原始帧照常上传）
if [ -n "$APT_PID" ]; then
  wait "$APT_PID" || echo "::warning::imagemagick install failed — boards may be skipped"
fi
if command -v montage >/dev/null 2>&1; then
  BOARD_LOOKUP_MISMATCH=0
  montage_board board-A-home.jpg          home-light.png home-dark.png profile.png
  montage_board board-B-repo-code.jpg     repos.png repo-actions.png repo-releases.png file-tree.png readme-webview.png code-sora.png editor.png
  montage_board board-C-issue-pr-diff.jpg issue-comments.png create-issue.png pr-conversation.png pr-commits.png pr-diff-unified.png pr-diff-side-by-side.png
  # 板 D Review/Merge 骨架——T17 合入后在此追加 review-sheet/merge-box/merge-state 三帧即自动生效
  montage_board board-E-settings-notif.jpg settings-grouped.png notification-panel.png commit-dialog.png
  montage_board board-F-search.jpg        search-history.png search-tabs.png
else
  echo "::warning::ImageMagick montage not found — boards skipped, raw frames only"
  BOARD_LOOKUP_MISMATCH=0
fi

# ══════════════════════════════════════════════════════════════════════
# 11. 清理：恢复浅色 + 回首页
# ══════════════════════════════════════════════════════════════════════
adb shell cmd uimode night no
adb shell am force-stop "$PKG"

# ══════════════════════════════════════════════════════════════════════
# 12. 崩溃诊断：dump logcat（app 崩溃时堆栈在缓冲区——第 13 轮 profile/README
#     三张图显示桌面，疑似崩溃；无 logcat 无法定位）
# ══════════════════════════════════════════════════════════════════════
adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" 2>/dev/null; then
  echo "::warning::FATAL EXCEPTION found in logcat"
fi

# ── 渲染通道判定留档：ReadmeRender 日志（native/webview 以日志为准，禁止视觉推断）
grep "ReadmeRender" "$OUT/logcat.txt" > "$OUT/readme-render-log.txt" 2>/dev/null || true
if [ -s "$OUT/readme-render-log.txt" ]; then
  echo "ReadmeRender decisions:"
  cat "$OUT/readme-render-log.txt"
else
  echo "::warning::no ReadmeRender log found"
fi

# ══════════════════════════════════════════════════════════════════════
# 13. 坏帧汇总 + 门禁决策
# ══════════════════════════════════════════════════════════════════════
summarize_bad_frames
# 把「判定口径」写进产物本身：读 bad-frames.txt 的人要知道哪些坏帧会拦合并
{
  echo "#"
  echo "# 判定口径：critical 帧坏 → job 红（本次 exit 1）；其余坏帧只告警。"
  echo "# 本次关键帧：${BAD_CRITICAL_FRAMES[*]:-（无）}"
  echo "# 断言通过并产出的帧：$FRAME_OK_COUNT / 参与 md5 去重比对的帧：${#FRAME_MD5_NAMES[@]}"
} >> "$OUT/bad-frames.txt"

echo "── 坏帧清单（bad-frames.txt）──"
cat "$OUT/bad-frames.txt"

# 决策（写进脚本而不是 workflow，理由要跟着代码走）：
# - **critical 帧失败 = job 红**。范围只有 4 类：home-light（首页首屏）、
#   readme-webview / readme-mermaid（README = 项目第一优先级功能，历史上零信号）、
#   editor（编辑链路，历史上与 commit-dialog 同 md5 骗过审计）。它们是「回归信号
#   本身是否可信」的锚点：这几帧都拍不到，整份产物就没有判断价值，让 job 红是
#   在报告「证据链断了」而不是「UI 坏了」。
# - 其余帧坏 = 只告警（::warning:: + 水印 + bad-frames.txt + job summary + release
#   说明）。截图是辅助审计证据，30 分钟模拟器 job 里偶发一次 dump 超时不该阻塞
#   合并；但必须**显眼**，所以坏帧同时进产物、拼板水印与 PR 评论。
# - MISSING（无 token / 上游导航失败导致没拍）从不染红：没测 ≠ 测出问题。
if [ ${#BAD_CRITICAL_FRAMES[@]} -gt 0 ]; then
  echo "::error::关键帧未通过断言：${BAD_CRITICAL_FRAMES[*]} —— 本次截图不能作为回归信号（详见 artifacts/screenshots/bad-frames.txt）"
  echo "截图可信度门禁：FAIL（关键帧 ${#BAD_CRITICAL_FRAMES[@]} 个）"
  exit 1
fi
echo "截图可信度门禁：PASS（坏帧 ${#BAD_FRAMES[@]} 个，均非关键帧）"

echo "screenshots:"
ls -la "$OUT"

# 拼板取帧契约破坏（帧存在却没进板）→ job 红：产物照常上传，但必须让人知道 boards 不可信。
# 本条是 2026-09-13 事故（$OUT/$name.png 双扩展名，boards 全为占位图且零告警）的防回归闸门。
if [ "${BOARD_LOOKUP_MISMATCH:-0}" -gt 0 ]; then
  echo "::error::拼板取帧契约破坏 $BOARD_LOOKUP_MISMATCH 处 —— boards 不可信（详见上方 ::error::）"
  exit 1
fi
