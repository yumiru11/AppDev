#!/usr/bin/env bash
# ============================================================
# 模拟器 adb 驱动 helpers（从 screenshots.sh 抽出，供多支 CI 脚本共用）
#
# 为什么抽出：ci.yml 的截图 job（API 30）与 glass-verify.yml 的毛玻璃验证 job
# （API 31+）需要**同一套**等待/点击/断言逻辑。复制一份的代价是两边行为漂移——
# 已经踩过一次的坑（input service 竞态）不该踩第二次。
#
# 使用方需先定义：PKG（应用包名）。$OUT（截图输出目录）仅 long_shot 使用。
# 依赖：模拟器已启动（adb 可用）。
# ============================================================

# ── 输入服务就绪等待（#170）──────────────────────────────────────────────
# PR #174 实测：android-emulator-runner 的 sys.boot_completed 已满足，但 input
# 服务仍可能尚未注册，紧接着的 adb shell input swipe 直接抛
# "No service published for: input" → set -e 让整条截图流水线变红。
# 这是模拟器启动竞态（基础设施），与应用无关，故前置轮询 + 单次重试。
wait_for_input_service() {
  local _
  for _ in $(seq 1 60); do
    if adb shell service check input 2>/dev/null | grep -q "found"; then
      return 0
    fi
    sleep 1
  done
  echo "::error::input service 未就绪（60s 超时）——模拟器启动异常"
  return 1
}

# 带重试的 input 调用：偶发 ServiceNotFoundException 时等待服务恢复再试，
# 三次都失败只告警不中断（后续步骤仍能产出部分截图，比整条 job 红更有价值）
retry_input() {
  local attempt
  for attempt in 1 2 3; do
    if adb shell input "$@" >/dev/null 2>&1; then
      return 0
    fi
    echo "::warning::input $* 第 $attempt 次失败，等待 input 服务恢复"
    sleep 2
    wait_for_input_service || true
  done
  echo "::warning::input $* 连续 3 次失败，跳过本次交互"
  return 0
}

wait_for_input_service

# ImageMagick 后台预装（拼板前才需要）：安装的 ~20s 完全藏在前面截图时间里。
# 直装优先（镜像常命中缓存），失败再 update+装，全程 timeout 兜底。
APT_PID=""
if ! command -v montage >/dev/null 2>&1; then
  ( sudo timeout 120 apt-get install -y -qq imagemagick >/dev/null 2>&1 \
      || { sudo timeout 90 apt-get update -qq >/dev/null 2>&1 || true
           sudo timeout 150 apt-get install -y -qq imagemagick >/dev/null 2>&1; } ) &
  # shellcheck disable=SC2034  # 消费方在 screenshots.sh（wait "$APT_PID"）——跨文件使用
  APT_PID=$!
fi

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

# 帧断言专用：同一个判据但墙钟上限 15s。
# 为什么另立一个：断言在「导航刚做完」时执行，前台 activity 要么立刻就在、要么
# 说明这次导航失败；沿用 40s 会让一个坏帧额外烧 40s，20 帧串起来能吃掉整条 job 的
# 预算（本 job 只有 30min，历史实测已跑到 16.5min）。launch_app 仍用 40s 版本。
wait_for_activity_quick() {
  local expect="$1"
  for _ in $(seq 1 15); do
    if adb shell dumpsys activity activities 2>/dev/null | grep -q "mResumedActivity.*$expect"; then
      return 0
    fi
    sleep 1
  done
  echo "::warning::timeout(15s) waiting for activity $expect"
  return 1
}


# uiautomator dump 带重试：动画/加载期会报 idle 错误且静默失败（旧实现拿过期缓存
# 继续点，是「点了没反应/两帧一致」类问题的根因）。成功标准：本地 xml 含 <hierarchy。
dump_ui() {
  local attempt
  rm -f /tmp/ui.xml
  for attempt in 1 2 3 4; do
    # 先删设备侧旧 ui.xml 再 dump 并校验成功输出：uiautomator 偶发静默失败时
    # 会残留上一次的层级，pull 拿到陈旧内容 →「元素明明可见却找不到」假超时
    # （New issue/Title 连环超时的实证根因）
    if adb shell "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml" 2>/dev/null | grep -q "dumped to"; then
      adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || { sleep 1; continue; }
      if grep -q "<hierarchy" /tmp/ui.xml 2>/dev/null; then return 0; fi
    fi
    sleep 1
  done
  echo "::warning::uiautomator dump failed after retries"
  return 1
}

# 按可见文本 tap（uiautomator dump 拿 bounds 中心）——比硬编码坐标稳
tap_text() {
  local text="$1"
  dump_ui || return 0
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
  dump_ui || return 0
  local bounds
  bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'content-desc=\"$desc\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
  if [ -n "$bounds" ]; then
    adb shell input tap $bounds >/dev/null
  else
    echo "::warning::content-desc '$desc' not found in uiautomator dump"
  fi
}

# 轮询等待文本/content-desc 出现（墙钟上限默认 12s）。找到输出坐标到
# /tmp/wait_bounds（供 tap 复用同一次 dump），超时返回 1 并告警。
#
# 性能要点（CI 16.5min 实测复盘）：
# - 动画/加载态下 uiautomator 单次 dump 可卡 5-10s 等 idle，固定次数循环会把
#   「20s 超时」拖成 60-90s 墙钟——下调默认值并按迭代数近似计时；
# - 页面已呈错误态时继续轮询目标控件纯属浪费（token 缺权限段落曾各烧一分钟），
#   dump 出现任一错误文案即早退。
ERROR_MARKERS='Repository not found|No access with current sign-in|Network error|网络错误|当前登录无权访问'

wait_for_attr() {
  local attr="$1" value="$2" timeout="${3:-12}"
  local deadline=$(( $(date +%s) + timeout )) bounds
  while :; do
    if ! adb shell "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml" 2>/dev/null | grep -q "dumped to"; then
      sleep 1; continue
    fi
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || { sleep 1; continue; }
    if grep -q "<hierarchy" /tmp/ui.xml 2>/dev/null; then
      if grep -qE "$ERROR_MARKERS" /tmp/ui.xml; then
        echo "::notice::error state on screen while waiting for $attr '$value' — abort early"
        return 1
      fi
      bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'$attr=\"$value\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
      if [ -n "$bounds" ]; then
        echo "$bounds" > /tmp/wait_bounds
        return 0
      fi
    fi
    [ "$(date +%s)" -ge "$deadline" ] && {
      echo "::warning::timeout waiting for $attr '$value'"
      return 1
    }
    sleep 1
  done
}

wait_for_text() { wait_for_attr "text" "$@"; }
wait_for_desc() { wait_for_attr "content-desc" "$@"; }

# ── 子串版等待（帧断言用）────────────────────────────────────────────────
# 为什么必须有：上面的正则要求 `text="<整段>"` 后紧跟 bounds —— 也就是**只匹配
# 完整节点文本**。带参数的文案永远等不到，例如提交对话框里的
# `repo_file_branch_current` = "Commit to current branch (%1$s)" 在屏上是
# "Commit to current branch (main)"，用整串匹配会稳定超时（本地离线干跑实证）。
# 帧断言的口径是「这个屏上出现该文案」而不是「文案恰好等于」，故用子串匹配；
# wait_for_text 的既有调用方（tap 前的就绪检查）语义不变。
wait_for_attr_sub() {
  local attr="$1" value="$2" timeout="${3:-12}"
  local deadline=$(( $(date +%s) + timeout )) bounds
  while :; do
    if ! adb shell "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml" 2>/dev/null | grep -q "dumped to"; then
      sleep 1; continue
    fi
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || { sleep 1; continue; }
    if grep -q "<hierarchy" /tmp/ui.xml 2>/dev/null; then
      if grep -qE "$ERROR_MARKERS" /tmp/ui.xml; then
        echo "::notice::error state on screen while waiting for $attr~'$value' — abort early"
        return 1
      fi
      bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'$attr=\"[^\"]*${value}[^\"]*\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
      if [ -n "$bounds" ]; then
        echo "$bounds" > /tmp/wait_bounds
        return 0
      fi
    fi
    [ "$(date +%s)" -ge "$deadline" ] && {
      echo "::warning::timeout waiting for $attr containing '$value'"
      return 1
    }
    sleep 1
  done
}

# ── 帧断言基建（CI 截图可信度，2026-09-12）────────────────────────────────
# 背景（UI 审计实证 P1）：截图脚本此前「等不到就照截」，产物里**看不出**某一帧
# 是「拍对了但界面有问题」还是「根本没拍到目标屏」。三张关键帧因此长期骗人：
#   - readme-webview.png 实为 Files 文件树（导航残留）
#   - editor.png / commit-dialog.png md5 完全相同且都是 README 错误屏
# 现在每帧在 screencap **之前**必须先过断言；不过则走「坏帧」路径（保留现场 +
# 标记 + 汇总），而不是把一帧来路不明的画面当成回归信号交出去。

# 单次断言（check|reason 三连）：check 为 ok / fail / skip（无此能力＝不判分，
# 例如宿主机没有 python3 时无法解析 dump —— 静默降级会精确复现本 bug，必须显式）。
# 只负责「此刻成立吗」，重试与退出码由 assert_frame 负责。
# 只调用既有的 dump_ui / wait_for_attr（自带轮询 + 错误态早退）。
assert_attr_holds() {
  local attr="$1" value="$2" timeout="${3:-12}"
  # 快路径：dump 里已经有（子串）就不用再等 —— 与 wait_for_attr_sub 同口径，
  # 避免「断言已满足却白等一轮轮询」
  if [ -f /tmp/ui.xml ] && grep -qF "$value" /tmp/ui.xml 2>/dev/null; then
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    if wait_for_attr_sub "$attr" "$value" "$timeout" >/dev/null 2>&1; then return 0; fi
    return 1
  fi
  return 2
}

# Tab 选中态断言的「能力缺失」包装：dump 解析全依赖 python3，缺了就 skip（2）。
assert_selected_holds() {
  local value="$1" timeout="${2:-12}"
  if command -v python3 >/dev/null 2>&1; then
    if wait_for_selected "$value" "$timeout" >/dev/null 2>&1; then return 0; fi
    return 1
  fi
  return 2
}

# checked 语义（radio / M3 SegmentedButton 的选中态）的能力缺失包装，同 selected。
assert_checked_holds() {
  local value="$1" timeout="${2:-12}"
  if command -v python3 >/dev/null 2>&1; then
    if wait_for_checked "$value" "$timeout" >/dev/null 2>&1; then return 0; fi
    return 1
  fi
  return 2
}

# 标记「断言能力缺失」告警只打一次，避免刷屏掩盖真正的坏帧
HOST_CAPABILITY_WARNED=0
warn_capability_once() {
  [ "$HOST_CAPABILITY_WARNED" = "1" ] && return 0
  HOST_CAPABILITY_WARNED=1
  echo "::warning::宿主机缺少 python3 —— 无法解析 uiautomator dump，本轮所有断言判分被跳过（截图可信度不受保障，请修 CI 基础镜像）"
}

# 日志断言：grep 自 $probe_baseline_bytes 之后的 logcat（增量，避免上一页的
# 旧行让断言假通过）。check 与 reason 分离，便于「正则不可信」时只弱化判分。
assert_log_holds() {
  local pattern="$1"
  if command -v python3 >/dev/null 2>&1; then
    if adb logcat -d 2>/dev/null | tail -c "+${probe_baseline_bytes:-1}" | grep -qE "$pattern"; then
      return 0
    fi
    return 1
  fi
  return 2
}

# 语义选中态断言：Tab 的 selected 与 radio/分段按钮的 checked 共用同一套判据。
#
# ⚠️ 为什么两种属性都必须支持：Compose 不同组件把「选中」暴露到不同语义位——
#   - TabRow 的 Tab（Modifier.selectable）→ selected="true"（CI 实证：README/
#     Repos/Profile/Commits/Conversation 这些帧一直稳定命中）
#   - M3 SegmentedButton（radio 语义）→ checkable/checked="true"，selected 恒 false
#     （CI run 34698275775 的 pr-diff-unified.ui.xml / pr-diff-side-by-side.ui.xml 实证：
#      checked=true 随点击在两段之间移动，selected 全为 false）
#   用 selected 判分段按钮会稳定假红（pr-diff 两帧的实际失败原因），反之亦然。
#
# ⚠️ 首版实现的三个实测教训（2026-09-11/12，别重蹈）：
#   ① 要求「同一 <node> 里既有 text 又有 selected」→ 17/32 帧全判坏：Compose 语义树
#      把 selected 挂在 Tab 容器节点、文本挂在子节点，同节点要求几乎永不成立；
#   ② minidom 从 Document 节点遍历会**跳过属性**（实测 getAttribute 恒返回空串）→
#      断言恒假红。必须从 documentElement 走；
#   ③ 「子树内有该文本」判据**故意不用**：TabRow 的选中容器 View 覆盖整个 Tab 行，
#      子树里同时含 README/Files/Releases 三个标签 —— 会让「Files 选中时查 README」
#      也通过（把假红换成假绿）。
#
# 正确判据（两级，从强到弱，全部落在**同一棵子树**内）：
#   1. 同一 node 既有 text="X" 又有 <语义>="true"             → 通过（最严格）
#   2. 存在 <语义>="true" 的 node，其 bounds **包含** text="X" 的 node
#                                                             → 通过（Compose 实际形态）
# 用 minidom 走真树结构 + bounds 包含判定，而不是分开 grep text 与语义位。
wait_for_state() {
  local attr="$1" value="$2" timeout="${3:-12}"
  local deadline=$(( $(date +%s) + timeout ))
  while :; do
    if dump_ui 2>/dev/null; then
      if python3 -c "
import re, sys
import xml.dom.minidom as minidom
state_attr = '$attr'
value = '$value'

# ⚠️ 必须从 documentElement 走：minidom 的 Document 节点在遍历时会**跳过属性**
# （实测 getAttribute 恒返回空串），从 Document 出发会得到 0 个 selected 节点 →
# 断言恒假红。这是 2026-09-11 首版实现的第二个 bug，由离线夹具实测抓出。

def selected_nodes(n, acc):
    if n.nodeType == n.ELEMENT_NODE:
        if n.getAttribute(state_attr) == 'true':
            acc.append(n)
        for c in n.childNodes:
            selected_nodes(c, acc)
    return acc

def texts(n, acc):
    if n.nodeType == n.ELEMENT_NODE:
        t = n.getAttribute('text')
        if t:
            acc.append(t)
        for c in n.childNodes:
            texts(c, acc)
    return acc

def bounds(n):
    b = n.getAttribute('bounds')
    if not b:
        return None
    try:
        l, t = b.split('][')[0].lstrip('[').split(',')
        r, bb = b.split('][')[1].rstrip(']').split(',')
        return int(l), int(t), int(r), int(bb)
    except Exception:
        return None

try:
    doc = minidom.parse('/tmp/ui.xml')
except Exception:
    sys.exit(1)
root = doc.documentElement
sels = selected_nodes(root, [])

# 判据 1：同一 node 既有该 text 又 selected=true（最严格，某些版本成立）
for n in sels:
    if value == n.getAttribute('text') or value in n.getAttribute('text'):
        sys.exit(0)

# ⚠️ 判据 2（子树内出现该文本）**故意不用**：2026-09-11 用真实 CI dump
# （readme-webview.ui.xml）实测发现 Compose TabRow 的**选中容器 View** 覆盖
# 整个 Tab 行，子树里同时含 README/Files/Releases 三个标签 —— 子树判据会让
# 「Files tab 选中时查 README」也通过，等于**把本 bug 又放回来**。

# 判据 3：文本节点 bounds 落在某个 selected 节点 bounds 之内（个别版本下标在祖先）
def text_nodes(n, acc):
    if n.nodeType == n.ELEMENT_NODE:
        t = n.getAttribute('text')
        if t and (value == t or value in t):
            acc.append(n)
        for c in n.childNodes:
            text_nodes(c, acc)
    return acc

for sn in sels:
    sb = bounds(sn)
    if not sb:
        continue
    sl, st, sr, sbb = sb
    for tn in text_nodes(root, []):
        tb = bounds(tn)
        if not tb:
            continue
        tl, tt, tr, tbb = tb
        if sl <= tl and tt >= st and tr <= sr and tbb <= sbb:
            sys.exit(0)

sys.exit(1)
" 2>/dev/null

      then
        return 0
      fi
    fi
    [ "$(date +%s)" -ge "$deadline" ] && {
      echo "::warning::timeout waiting for '$value' node with $attr=true"
      return 1
    }
    sleep 1
  done
}

wait_for_selected() { wait_for_state selected "$@"; }
wait_for_checked() { wait_for_state checked "$@"; }

# 帧断言编排：FRAME_CHECKS/FRAME_REASONS/FRAME_OPTIONAL/FRAME_SEVERITY 由
# capture_frame 设好。
# - 必需断言：任一失败 → 整帧判坏
# - opt: 断言（可选）：失败只告警，不影响判分。**只给证据强度不确定的探针用**
#   （典型：WebView 正文文本是否进 uiautomator dump 依 WebView 版本而变），
#   已有的强断言（Tab 选中态 / ReadmeRender 日志）仍然是硬门槛。
# - skip（无此能力）→ 告警但不判死：宿主机能力缺失已在脚本开头单独告警。
assert_frame() {
  local i rc
  for i in "${!FRAME_CHECKS[@]}"; do
    # 探针命令以退出码表态（0=成立 / 1=不成立 / 2=宿主机无判定能力），
    # 多数探针成功时**不打印任何东西**，故用 rc 判定而不是输出文本判定
    eval "${FRAME_CHECKS[$i]}" >/dev/null 2>&1
    rc=$?
    case "$rc" in
      0) ;;
      2)
        warn_capability_once
        echo "::warning::断言能力缺失，无法判定：${FRAME_REASONS[$i]}（本帧判分已跳过）"
        ;;
      *)
        if [ "${FRAME_OPTIONAL[$i]}" = "1" ]; then
          echo "::notice::可选探针未命中（不影响判分）：${FRAME_REASONS[$i]}"
        else
          FRAME_FAIL_REASON="${FRAME_REASONS[$i]}"
          return 1
        fi
        ;;
    esac
  done
  return 0
}

# ── 坏帧清单 ─────────────────────────────────────────────────────────────
# 三种标记（都会写进 bad-frames.txt，拼板时打水印）：
#   FAILED    断言未通过——画面没到目标屏
#   MISSING   前置条件缺失（如 SCREENSHOT_TOKEN 未配置）——压根没拍
#   DUPLICATE 与另一帧像素完全相同——两帧必有一帧没拍到位
BAD_FRAMES=()          # 人读行：<name>.png  <KIND>  <原因>
BAD_CRITICAL_FRAMES=() # 其中的 critical 帧名（决定是否染红 job，见 screenshots.sh 结尾）
# 每个帧**自己声明**的 severity（capture_frame 第 2 参）。必须按帧名存：
# md5 去重断言在所有 capture 之后才跑，那时全局 FRAME_SEVERITY 已被最后一帧覆盖，
# 直接读全局会把「critical 帧失败」降级成 warn → 关键帧闸门静默失效
# （2026-09-13 事故：readme-mermaid(critical) 被判 DUPLICATE 却 severity=warn）。
declare -A FRAME_SEVERITIES=()

record_bad_frame() {
  local name="$1" kind="$2" reason="$3"
  BAD_FRAMES+=("$(printf '%-26s %-9s %s' "$name.png" "$kind" "$reason")")
}

# 「坏」到什么程度：CRITICAL 的帧会以 ::error:: 报出（是否染红 job 由脚本决定）
# 非 critical 只告警——截图是审计证据，不该让一条偶尔抽风的帧阻塞合并。
mark_bad_frame() {
  local name="$1" kind="$2" reason="$3"
  local path="$OUT/$name.png"
  # 帧自己的 severity 优先（捕获时登记）；取不到才回退当前全局值。
  # 去重断言在末尾跑时只能靠这张表，否则 critical 会被降级（见 FRAME_SEVERITIES 注释）。
  local severity="${FRAME_SEVERITIES[$name]:-${FRAME_SEVERITY:-fail}}"
  record_bad_frame "$name" "$kind" "$reason"
  if [ "$severity" = "critical" ]; then
    BAD_CRITICAL_FRAMES+=("$name")
  fi
  if [ -f "$path" ]; then
    mv -f "$path" "$OUT/$name.FAILED.png" 2>/dev/null || true
  fi
  {
    echo "frame: $name"
    echo "kind: $kind"
    echo "severity: $severity"
    echo "reason: $reason"
  } > "$OUT/$name.badframe.txt" 2>/dev/null || true
  # 现场取证：当帧 UI 层级（WebView 文本/控件树都在里面），供下一轮定位
  if dump_ui 2>/dev/null; then
    cp -f /tmp/ui.xml "$OUT/$name.ui.xml" 2>/dev/null || true
  fi
  if [ "$severity" = "critical" ]; then
    echo "::error::$name.png $kind — $reason（第一优先级帧，本轮不给过）"
  else
    echo "::warning::$name.png $kind — $reason"
  fi
  return 0
}

# MISSING：压根没拍（前置条件缺失，如未配置 SCREENSHOT_TOKEN / 上一屏没到导致
# 后续帧无意义）。不进 critical 集合——「没测」与「测出问题」是两回事。
mark_missing_frame() {
  local name="$1" reason="$2"
  FRAME_SEVERITY="warn"
  record_bad_frame "$name" MISSING "$reason"
  {
    echo "frame: $name"
    echo "kind: MISSING"
    echo "severity: warn"
    echo "reason: $reason"
  } > "$OUT/$name.badframe.txt" 2>/dev/null || true
  echo "::warning::$name.png MISSING — $reason"
  return 0
}

# 极简互斥锁：多个 capture_frame 之间串行执行（同一时刻只有一个帧在等待/取图）。
# 作用是把 RULE 3 的「窗口不重叠」变成代码保证，而不是依赖人工审查帧顺序。
FRAME_LOCK_DIR="${TMPDIR:-/tmp}/appdev-frame.lock"
frame_lock() {
  [ "${FRAME_LOCK_DISABLE:-0}" = "1" ] && return 0
  local n=0
  while ! mkdir "$FRAME_LOCK_DIR" 2>/dev/null; do
    n=$((n + 1))
    if [ "$n" -gt 300 ]; then echo "::warning::frame lock 超时，强制继续"; break; fi
    sleep 1
  done
}
frame_unlock() {
  [ "${FRAME_LOCK_DISABLE:-0}" = "1" ] && return 0
  rmdir "$FRAME_LOCK_DIR" 2>/dev/null || true
}

# ── 取帧唯一入口：先确认在目标屏，再截图 ──────────────────────────────────
# 用法：
#   capture_frame <name> <severity> <settle秒> [断言规格 ...]
# 断言规格（前缀 opt: 表示可选探针，见 assert_frame 注释）：
#   act:<substr>         前台 activity 含该子串
#   text:<文本>          UI 层级出现该文本（含 WebView 渲染出的文本）
#   exact:<文本>         该文本节点处于 selected=true（Tab 选中态）
#   checked:<文本>       该文本节点处于 checked=true（radio/分段按钮选中态）
#   desc:<content-desc>  出现该 content-desc
#   log:<正则>           **本帧开始之后**的 logcat 里出现该正则
# severity：critical | warn（见各帧注释；决定 mark_bad_frame 用 ::error:: 还是 ::warning::）
#
# 判定口径：断言在截图**之前**跑（先确认在目标屏 → 再取图）。这修掉的是本 bug 的
# 根因——旧写法「等不到就照截」，产物里分不清「拍对了但界面有问题」与「没拍到」。
capture_frame() {
  local name="$1" severity="$2" settle="$3"; shift 3
  local spec key val lc_before optional

  # 帧内断言按临界区串行：保证「断言窗口」与「截图瞬间」不被其它帧的等待穿插
  frame_lock
  FRAME_CHECKS=(); FRAME_REASONS=(); FRAME_OPTIONAL=()
  FRAME_SEVERITY="$severity"
  FRAME_SEVERITIES["$name"]="$severity"
  FRAME_FAIL_REASON=""
  for spec in "$@"; do
    optional=0
    case "$spec" in opt:*) optional=1; spec="${spec#opt:}" ;; esac
    key="${spec%%:*}"; val="${spec#*:}"
    case "$key" in
      act | activity)
        FRAME_CHECKS+=("wait_for_activity_quick '$val'")
        FRAME_REASONS+=("前台 activity 未出现 $val")
        ;;
      text)
        FRAME_CHECKS+=("assert_attr_holds text '$val'")
        FRAME_REASONS+=("屏幕文本未出现「$val」")
        ;;
      exact)
        FRAME_CHECKS+=("assert_selected_holds '$val'")
        FRAME_REASONS+=("「$val」节点不在 selected=true 状态（Tab 未选中）")
        ;;
      checked)
        # radio/分段按钮的选中态（M3 SegmentedButton → checkable/checked=true，
        # selected 恒 false；与 exact: 分开，避免把两种语义混为一谈）
        FRAME_CHECKS+=("assert_checked_holds '$val'")
        FRAME_REASONS+=("「$val」节点不在 checked=true 状态（分段按钮未选中）")
        ;;
      desc)
        FRAME_CHECKS+=("assert_attr_holds content-desc '$val'")
        FRAME_REASONS+=("content-desc 未出现「$val」")
        ;;
      log)
        FRAME_CHECKS+=("assert_log_holds '$val'")
        FRAME_REASONS+=("logcat 未出现 /$val/")
        ;;
      *)
        echo "::warning::未知断言规格 '$spec'（帧 $name）——已忽略"
        key=""
        ;;
    esac
    [ -n "$key" ] && FRAME_OPTIONAL+=("$optional")
  done

  # 日志基线：断言只看本帧开始之后的日志（否则上一帧的旧行会让 log: 断言假通过）
  lc_before=$(adb logcat -d 2>/dev/null | wc -c || echo 0)
  probe_baseline_bytes=$((lc_before + 1))

  sleep "$settle"
  if ! assert_frame; then
    mark_bad_frame "$name" FAILED "$FRAME_FAIL_REASON"
    frame_unlock
    # 关键：**仍然返回 0**。坏帧已记进清单（bad-frames.txt / 水印 / 单帧标记），
    # 由脚本结尾按 critical 与否统一决定门禁；这里若返回非 0，set -e 会在第一个
    # 非关键坏帧处掐断整条流水线——那等于「一个非关键帧坏 = 整份产物没了」，
    # 比不检查更糟（也更不该让合并被偶发 dump 超时阻塞）。
    return 0
  fi

  adb exec-out screencap -p > "$OUT/$name.png"
  record_md5 "$name" "$OUT/$name.png"
  frame_unlock
  return 0
}

# ── md5 去重断言 ─────────────────────────────────────────────────────────
# 两帧像素完全相同 = 至少一帧没拍到位（历史上 editor 与 commit-dialog 就是这样
# 同 md5 骗过审计的）。例外必须显式声明 + 写清理由，见声明处的注释。
FRAME_MD5_NAMES=()
FRAME_MD5_VALUES=()
FRAME_OK_COUNT=0        # 成功产出（断言通过）的帧数，供汇总里报「通过 N/M」

record_md5() {
  local name="$1" file="$2" v
  [ -f "$file" ] || return 0
  v=$(md5sum "$file" 2>/dev/null | awk '{print $1}')
  FRAME_MD5_NAMES+=("$name")
  FRAME_MD5_VALUES+=("$v")
  FRAME_OK_COUNT=$((FRAME_OK_COUNT + 1))
}

# 显式豁免：确实应当像素相同的成对帧写在这里 + 注释理由（任务要求「例外必须显式
# 标注」，避免把真问题当例外放过去）。格式：md5 相同的两个帧名，顺序无关。
# 目前**没有**豁免项：所有帧都是不同屏/不同主题/不同滚动位置，两两相同必然意味着
# 有一帧没拍到位。例：readme-webview 与 readme-mermaid 同屏但滚动位置不同（正文不同）
# → 仍应不同，不豁免；深浅色首页同理（主题不同，像素必然不同）。
FRAME_MD5_EXEMPT_PAIRS=()

is_exempt_pair() {
  local a="$1" b="$2" pair
  for pair in ${FRAME_MD5_EXEMPT_PAIRS+"${FRAME_MD5_EXEMPT_PAIRS[@]}"}; do
    case "$pair" in
      "$a|$b" | "$b|$a") return 0 ;;
    esac
  done
  return 1
}

# md5 分组去重：先按 md5 归组，再每组只保留**最早**那一帧，其余判 DUPLICATE。
# 为什么按组而不是两两比对：两两比对在「所有帧都一样」这种最坏情况会产出 O(N²)
# 条告警（实测 27 帧 → 351 条），日志/PR 评论会被刷屏，真正的问题反而被淹没。
# 同一 md5 组报**一条**告警，逐帧只写坏帧标记。
check_duplicate_frames() {
  local i j md5 first dup_names
  local -A seen=()
  for i in "${!FRAME_MD5_NAMES[@]}"; do
    md5="${FRAME_MD5_VALUES[$i]}"
    if [ -n "${seen[$md5]:-}" ]; then
      # 已见过该 md5：本帧是组内后来者 → DUPLICATE
      j="${seen[$md5]}"
      first="${FRAME_MD5_NAMES[$j]}"
      if is_exempt_pair "$first" "${FRAME_MD5_NAMES[$i]}"; then
        echo "::notice::${FRAME_MD5_NAMES[$i]}.png 与 $first.png 像素相同（md5 ${md5:0:8}）—— 已在 FRAME_MD5_EXEMPT_PAIRS 显式豁免"
        continue
      fi
      mark_bad_frame "${FRAME_MD5_NAMES[$i]}" DUPLICATE \
        "与 $first.png 像素完全相同（md5 ${md5:0:8}）——疑似未拍到目标屏"
      continue
    fi
    seen[$md5]=$i
    # 该 md5 是否还有后续重复？有才报一条组级告警
    dup_names=""
    for j in "${!FRAME_MD5_NAMES[@]}"; do
      [ "$j" -le "$i" ] && continue
      if [ "${FRAME_MD5_VALUES[$j]}" = "$md5" ] && ! is_exempt_pair "${FRAME_MD5_NAMES[$i]}" "${FRAME_MD5_NAMES[$j]}"; then
        dup_names+="${FRAME_MD5_NAMES[$j]}.png "
      fi
    done
    if [ -n "$dup_names" ]; then
      echo "::warning::md5 ${md5:0:8} 组：${FRAME_MD5_NAMES[$i]}.png 与【${dup_names% }】像素完全相同 —— 重复帧已判坏（同一像素不可能同时是多个目标屏）"
    fi
  done
}

# ── 坏帧汇总 ─────────────────────────────────────────────────────────────
# 产物内留 bad-frames.txt（随 release/artifact 一起上传，review 的人在产物里
# 就能看到「哪几帧不可信」）；单帧当时已按 critical 与否报 ::error:: / ::warning::。
# 是否让 job 红**不在这里**决定：由调用方看 BAD_CRITICAL_FRAMES（见
# screenshots.sh 结尾的决策段）。
summarize_bad_frames() {
  local line
  {
    echo "# 坏帧清单（bad frames）"
    echo "# FAILED=断言未通过 / MISSING=前置条件缺失没拍 / DUPLICATE=与另一帧像素相同"
    echo "# 这些帧**不能**作为回归信号；原始帧已改名 <name>.FAILED.png，现场 UI 层级见 <name>.ui.xml"
    if [ ${#BAD_FRAMES[@]} -eq 0 ]; then
      echo "(无：本轮所有帧都在断言通过后拍摄)"
    else
      for line in "${BAD_FRAMES[@]}"; do echo "$line"; done
    fi
  } > "$OUT/bad-frames.txt"

  if [ ${#BAD_FRAMES[@]} -gt 0 ]; then
    echo "::notice title=Screenshot bad frames::本次有 ${#BAD_FRAMES[@]} 帧未通过断言/未拍到，详见 bad-frames.txt 与拼板水印"
  fi
  return 0
}


# 验证 SegmentedButton 某段处于选中态（Compose selected 语义会暴露到节点属性）；
# 未选中则重按一次——修复「点击生效了但截图时序早于状态翻转」的两帧一致问题。
tap_segment_until_selected() {
  local label="$1" try n
  # shellcheck disable=SC2034  # try 只作重试计数（循环变量），值本身不被读取
  for try in 1 2; do
    tap_text "$label"
    for n in $(seq 1 6); do
      dump_ui || { sleep 1; continue; }
      if python3 -c "
import sys
label='$label'
xml=open('/tmp/ui.xml').read()
ok=any(('text=\"%s\"' % label) in seg and 'selected=\"true\"' in seg for seg in xml.split('<node'))
sys.exit(0 if ok else 1)
"; then return 0; fi
      sleep 1
    done
  done
  echo "::warning::segment '$label' not confirmed selected"
}

# 像素级验证：点击后截图必须与 prev 帧不同才算成功（md5 比对），否则重按再截。
# 背景：tap_segment_until_selected 的 Compose selected 语义校验曾误判——节点属性
# 翻转了但 Crossfade 未完成/坐标过期，最终两帧仍视觉一致（CI 实拍 C 板末两帧）。
capture_until_changed() {
  local prev="$1" out="$2" label="$3" i md5p md5n
  md5p=$(md5sum "$prev" | awk '{print $1}')
  for i in 1 2 3; do
    if [ "$i" -gt 1 ]; then
      tap_text "$label"
      sleep 1
    fi
    adb exec-out screencap -p > "$out"
    md5n=$(md5sum "$out" | awk '{print $1}')
    if [ "$md5n" != "$md5p" ]; then return 0; fi
    sleep 1
  done
  echo "::warning::frame '$out' never differed from '$prev' after tapping '$label'"
}

launch_app() {

  adb shell am start -n "$PKG/com.yumiru11.githubapp.MainActivity" >/dev/null
  wait_for_activity "$PKG" || true
  # 首帧稳定。3s（原 2s）：加了品牌化启动屏（D3）后冷启动会先显示 splash 再交接给
  # 首帧，2s 有拍到 splash 的风险。#196 的修复随 helper 抽取一并保留（#195 把本函数
  # 搬进 lib/adb-helpers.sh 时丢了这 1s）。
  sleep 3
}

# 长截图（纯 adb 滚动 + 多帧截图，无 Python/PIL 依赖）：
# 导航到 $2 深链，循环「上滑 → 截一帧」最多 $3 帧（默认 20），相邻帧二进制相同即视为到底、停止。
# 输出到 $OUT/$1-01.png、$1-02.png …（多帧即长截图，人工翻看）。
long_shot() {
  local name="$1" url="$2" max="${3:-12}"
  local delta=2000          # pixel_6 视口 2400：自底(y=2000)上滑 2000px，留 ~400 重叠
  adb shell am start -a android.intent.action.VIEW -d "$url" -p "$PKG" >/dev/null
  wait_for_activity "$PKG" || true
  sleep 4
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
    record_md5 "${name}-$(printf '%02d' "$i")" "$f"   # 参与 R3 md5 去重
    # 末帧无需上滑
    if [ "$i" -lt "$max" ]; then
      adb shell input swipe 540 2000 540 $((2000 - delta)) 250
      sleep 1
    fi
  done
}

# ── 截图登录（CI 机密 SCREENSHOT_TOKEN）─────────────────────────────────
# 为什么必须登录：未登录时首页是空的、仓库页只有只读态、评论/Review/行评论这些
# BottomSheet 根本进不去 —— 拿这种状态去「看效果」等于测了个寂寞。
# 注入方式沿用 screenshots.sh 的既有实现（debug 专用 Receiver 写 EncryptedTokenStorage）。
inject_screenshot_token() {
  if [ -z "${SCREENSHOT_TOKEN:-}" ]; then
    echo "::warning::SCREENSHOT_TOKEN 未配置 —— 本次截图是未登录态，部分界面不可达"
    return 1
  fi
  adb shell am start -n "$PKG/com.yumiru11.githubapp.ScreenshotTokenReceiver" -e pat "$SCREENSHOT_TOKEN" >/dev/null 2>&1 || true
  sleep 2
  return 0
}

# 当前 UI 层级里是否出现某个正则
ui_contains() {
  dump_ui || return 1
  grep -qE "$1" /tmp/ui.xml 2>/dev/null
}

# 与 tap_text 同源，但**找不到就返回 1**（tap_text 只告警）。
# 需要「多个候选文案逐个兜底」的调用方必须用这个 —— 否则第一个候选无论找没找到
# 都会被当成命中（glass-verify 首版就踩了这个坑）。
try_tap_text() {
  local text="$1"
  dump_ui || return 1
  local bounds
  bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'text=\"$text\"[^>]*bounds=\"\[(\\d+),(\\d+)\]\[(\\d+),(\\d+)\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
  if [ -n "$bounds" ]; then
    adb shell input tap $bounds >/dev/null
    return 0
  fi
  return 1
}

# 登录态断言：底栏 Profile → 页面上应出现登录名（默认取仓库 owner）。
# 失败即 ::error:: —— 因为「拍到了登录失败的应用」比「没拍」更糟：它会让人误以为
# 看到的是真实体验（本项目已实际发生过：SCREENSHOT_TOKEN 过期后，通知面板截图一直
# 显示应用自己的「Sign-in expired. Please sign in again.」错误卡，而没人发现）。
assert_signed_in() {
  local expect="${1:-yumiru11}"
  tap_text "Profile"
  sleep 3
  if ui_contains "$expect"; then
    echo "::notice::登录态确认：Profile 页出现 $expect"
    return 0
  fi
  echo "::error::未确认登录态（Profile 页未出现 $expect）—— SCREENSHOT_TOKEN 可能已过期；本次截图不代表真实登录体验"
  return 1
}


# 与 tap_desc 同源，但**找不到就返回 1**（tap_desc 只告警）。
# 与 try_tap_text 同理：任何要参与 `||` 兜底链的调用都必须用 try_* 版本，
# 否则链在第一个元素就短路（glass-verify 连踩两次）。
try_tap_desc() {
  local value="$1"
  dump_ui || return 1
  local bounds
  bounds=$(python3 -c "import re; xml=open('/tmp/ui.xml').read(); m=re.search(r'content-desc=\"$value\"[^>]*bounds=\"\[(\\d+),(\\d+)\]\[(\\d+),(\\d+)\]\"', xml); print((int(m.group(1))+int(m.group(3)))//2, (int(m.group(2))+int(m.group(4)))//2) if m else ''" 2>/dev/null || true)
  if [ -n "$bounds" ]; then
    adb shell input tap $bounds >/dev/null
    return 0
  fi
  return 1
}

# ── 右下角 FAB 定位点击（ExtendedFAB 的文案不进 uiautomator dump）─────────────
# 实证（CI run 34698275775 的 issue-authed.ui.xml / pr-actions.ui.xml）：
# ExtendedFloatingActionButton（Comment / New issue）在 dump 里是 clickable 的 NAF
# 节点，文本与图标子节点都没有 text/content-desc —— `wait_for_text "Comment"` /
# `wait_for_text "New issue"` 永远等不到，这是 create-issue 被误标 MISSING、
# issue-authed/pr-actions 稳定 FAILED 的共同根因之一。
#
# 判据：屏幕**右下角**（节点中心 x ≥ 0.7*屏宽 且 y ≥ 0.85*屏高）内 clickable=true
# 且无文案的节点，取面积最大者。右下角限制是必要的——只在「底部区域」取最大节点时，
# pr-diff 的整行可点击 diff 行（面积是 FAB 的两倍多）会被误点（实测 117432 vs 51597px²）。
# 屏幕尺寸用 wm size 实测而不是写死 1080x2400；取不到时退回 pixel_6 档（本 job 档位）。
# 找到即点，返回 0；未找到返回 1（调用方按「入口不可达」处理，不得当通过）。
try_tap_fab() {
  dump_ui || return 1
  local size w h bounds
  size=$(adb shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | head -1 || true)
  w="${size%%x*}"
  h="${size##*x}"
  case "$w:$h" in
    [0-9]*:[0-9]*) ;;
    *) w=1080; h=2400 ;;
  esac
  bounds=$(python3 - "$w" "$h" <<'PY' 2>/dev/null || true
import re
import sys
w, h = int(sys.argv[1]), int(sys.argv[2])
xml = open('/tmp/ui.xml').read()
best = None
best_area = 0
for m in re.finditer(r'<node[^>]*?/?>', xml):
    tag = m.group(0)
    if 'clickable="true"' not in tag:
        continue
    if 'text=""' not in tag or 'content-desc=""' not in tag:
        continue
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if not b:
        continue
    l, t, r, bb = map(int, b.groups())
    cx, cy = (l + r) // 2, (t + bb) // 2
    if cx >= w * 0.7 and cy >= h * 0.85:
        area = (r - l) * (bb - t)
        if area > best_area:
            best_area = area
            best = (cx, cy)
print('%d %d' % best if best else '')
PY
)
  if [ -n "$bounds" ]; then
    # shellcheck disable=SC2086  # "x y" 两个数字需按词拆分传给 input tap
    adb shell input tap $bounds >/dev/null
    return 0
  fi
  echo "::warning::右下角未找到无文案的 clickable 节点（FAB）—— 该屏可能没有 FAB"
  return 1
}

