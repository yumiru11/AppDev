#!/usr/bin/env bash
# ============================================================
# 截图产物收集校验（GATE-5b）：把 ci.yml 的 "Verify screenshots collected"
# 从「仅 echo 文件数」升级为**可失败**的硬校验。
#
# 背景（残余审计 S4 P2-8 / G-08）：该步骤原来只打印 `find ... | wc -l`——
# 产物为空、帧文件 0 字节、脚本中途挂掉都会照样绿，是「守卫在跑但什么都没查」
# 的典型形态。
#
# 校验四项（任一不满足即 ::error:: + exit 1）：
#   1. 至少产出一张 PNG（截图管线真的出图了）；
#   2. PNG 无 0 字节（screencap 失败/被截断的典型形态）；
#   3. bad-frames.txt 存在（= screenshots.sh 跑到了自己的坏帧汇总段，产物自洽）；
#   4. 帧清单闭合：screenshots.sh 自身声明的每一帧都必须有**显式处置**——
#      <name>.png（拍到）/ <name>.FAILED.png（拍到但断言不过被改名）/
#      <name>.badframe.txt（FAILED/MISSING/DUPLICATE 标记）/
#      <name>.skipped.txt（按设计不适用，如登录态下的 repos-guest）。
#      「某帧从产物里静默消失」正是历史上 readme-webview/editor 骗过审计的形态。
#
# 用法：bash .github/scripts/verify-screenshots.sh [产物目录，默认 artifacts/screenshots]
# 负向验证（本地离线，无需模拟器）：见 PR body —— 删帧/截断 PNG/删汇总文件均判红。
# ============================================================
set -euo pipefail

OUT="${1:-artifacts/screenshots}"
SCRIPT=".github/scripts/screenshots.sh"

fail() {
  echo "::error::$1"
  exit 1
}

[ -d "$OUT" ] || fail "截图产物目录不存在：$OUT（screenshots.sh 未运行或未产出）"

PNGS=$(find "$OUT" -name '*.png' | wc -l)
[ "$PNGS" -gt 0 ] || fail "截图产物里一张 PNG 都没有（$OUT）——截图管线空转"

ZERO=$(find "$OUT" -name '*.png' -size 0 | sort | tr '\n' ' ')
if [ -n "$ZERO" ]; then
  fail "存在 0 字节 PNG（screencap 失败/被截断）：$ZERO"
fi

[ -f "$OUT/bad-frames.txt" ] || fail "缺少 $OUT/bad-frames.txt —— screenshots.sh 未跑到坏帧汇总段（产物不自洽）"

# 帧清单从截图脚本自身提取（唯一真源）：capture_frame / mark_bad_frame /
# mark_missing_frame / record_md5 调用里的字面量帧名。
# 这样 workflow 与本脚本都不维护第二份列表，脚本加帧自动纳入校验。
mapfile -t EXPECTED < <(
  grep -oE '(capture_frame|mark_bad_frame|mark_missing_frame|record_md5)[[:space:]]+[A-Za-z0-9_-]+' "$SCRIPT" |
    awk '{print $2}' | sort -u
)
[ "${#EXPECTED[@]}" -gt 0 ] || fail "从 $SCRIPT 解析不到任何帧名（脚本结构变了？本校验的提取规则需要同步更新）"

# 帧数下限（只增不减）：防「悄悄删掉几帧」——删帧必须显式下调本值并在 PR 说明。
# 2026-09-13 定值 = 当时脚本的 28 帧（含仅游客态的 repos-guest）。
MIN_FRAMES=28
[ "${#EXPECTED[@]}" -ge "$MIN_FRAMES" ] ||
  fail "screenshots.sh 的帧清单缩水到 ${#EXPECTED[@]} 帧（下限 $MIN_FRAMES）——删帧必须在本脚本显式下调并说明理由"

MISSING=()
for name in "${EXPECTED[@]}"; do
  if [ -f "$OUT/$name.png" ] || [ -f "$OUT/$name.FAILED.png" ] ||
    [ -f "$OUT/$name.badframe.txt" ] || [ -f "$OUT/$name.skipped.txt" ]; then
    continue
  fi
  MISSING+=("$name")
done
if [ "${#MISSING[@]}" -gt 0 ]; then
  fail "帧清单不闭合：${#MISSING[@]}/${#EXPECTED[@]} 帧既无产出也无显式处置标记（$OUT）—— ${MISSING[*]}"
fi

echo "screenshots: $PNGS 张 PNG，帧清单闭合（${#EXPECTED[@]} 帧全部有产出或显式处置）"
