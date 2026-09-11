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


# 验证 SegmentedButton 某段处于选中态（Compose selected 语义会暴露到节点属性）；
# 未选中则重按一次——修复「点击生效了但截图时序早于状态翻转」的两帧一致问题。
tap_segment_until_selected() {
  local label="$1" try n
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
  sleep 2   # 首帧稳定（动画归零后无需更长）
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

