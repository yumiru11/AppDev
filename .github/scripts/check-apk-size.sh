#!/usr/bin/env bash
# ============================================================
# APK 体积回归门禁（nightly release 变体）
#
# 背景：离线 KaTeX 已合入（#269，实测 +0.33 MiB），Mermaid Tiny（预计 +0.64 MiB）
# 可能跟进；KaTeX/Mermaid 可行性研究 R11 要求在 nightly 做体积回归。
# 原先 nightly.yml 的 "Report shrink size" 步骤只打印体积、不与任何预算比较 ——
# 静默膨胀（资源/依赖/代码）无人发现。本脚本把它升级为**可失败**的硬门禁。
#
# 判定：release APK 字节数 > budget_bytes → ::error:: + exit 1（硬失败）。
#   nightly 失败通道：notify-failure job 会开/更新 nightly-failure issue。
#   刻意不提供「告警模式」开关：守卫必须能红（#260 纪律）。
#
# 预算唯一真源：.github/apk-size-budget.properties（budget_bytes / baseline_bytes，
# 含「如何有意提高预算」的说明）。
#
# 硬失败清单（任一命中即 ::error:: + exit 1，绝不静默跳过 —— #260）：
#   1. 预算文件缺失，或 budget_bytes / baseline_bytes 解析失败，或预算 < 基线；
#   2. APK 不存在（默认路径与显式路径都算）；
#   3. APK 字节数超过 budget_bytes。
#
# 用法：bash .github/scripts/check-apk-size.sh [apk路径]
#   缺省 = <repo>/app/build/outputs/apk/release/ 下的 APK（多个时取排序首个）。
#
# 负向验证（本地离线，无需模拟器；PR body 有完整红→绿记录）：
#   * 超预算：对 budget_bytes+1 字节的伪造 APK 跑 → 判红；
#   * 缺产物：对不存在的路径跑 → 判红；
#   * 缺配置：拷本脚本到无 properties 的目录树跑 → 判红。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONF="$SCRIPT_DIR/../apk-size-budget.properties"

fail() {
  echo "::error::$1"
  exit 1
}

# 严格解析 key=<整数>（不接受尾随注释/其他形态；配置写歪 = 硬失败）
read_conf() {
  sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*([0-9]+)[[:space:]]*$/\1/p" "$CONF" | head -1
}

[ -f "$CONF" ] || fail "体积预算文件缺失：$CONF —— 门禁配置丢失，不允许静默跳过"

BASELINE="$(read_conf baseline_bytes)"
BUDGET="$(read_conf budget_bytes)"
[ -n "$BASELINE" ] || fail "$CONF 缺少可解析的 baseline_bytes=<整数>"
[ -n "$BUDGET" ] || fail "$CONF 缺少可解析的 budget_bytes=<整数>"
[ "$BUDGET" -ge "$BASELINE" ] ||
  fail "$CONF 配置非法：budget_bytes=$BUDGET 小于 baseline_bytes=$BASELINE（预算不可能低于已 pin 的基线）"

APK="${1:-}"
if [ -z "$APK" ]; then
  APK="$(find "$ROOT/app/build/outputs/apk/release" -name '*.apk' 2>/dev/null | sort | head -1 || true)"
fi
[ -n "$APK" ] && [ -f "$APK" ] ||
  fail "release APK 不存在：${APK:-$ROOT/app/build/outputs/apk/release/*.apk}（assembleRelease 未产出？门禁要求硬失败而非跳过）"

SIZE="$(stat -c%s "$APK")"
SIZE_MIB="$(awk -v b="$SIZE" 'BEGIN { printf "%.2f", b / 1048576 }')"
BASELINE_MIB="$(awk -v b="$BASELINE" 'BEGIN { printf "%.2f", b / 1048576 }')"
BUDGET_MIB="$(awk -v b="$BUDGET" 'BEGIN { printf "%.2f", b / 1048576 }')"

echo "APK:  $APK"
echo "实际: $SIZE B ($SIZE_MIB MiB)"
echo "基线: $BASELINE B ($BASELINE_MIB MiB)"
echo "预算: $BUDGET B ($BUDGET_MIB MiB)"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Release APK 体积（预算门禁）"
    echo ""
    echo "| 项 | 值 |"
    echo "|---|---|"
    echo "| APK | \`$APK\` |"
    echo "| 实际 | $SIZE B ($SIZE_MIB MiB) |"
    echo "| 基线（pin） | $BASELINE B ($BASELINE_MIB MiB) |"
    echo "| 预算 | $BUDGET B ($BUDGET_MIB MiB) |"
  } >>"$GITHUB_STEP_SUMMARY"
fi

if [ "$SIZE" -gt "$BUDGET" ]; then
  OVER=$((SIZE - BUDGET))
  OVER_MIB="$(awk -v b="$OVER" 'BEGIN { printf "%.2f", b / 1048576 }')"
  fail "APK 体积超预算：$SIZE B ($SIZE_MIB MiB) > $BUDGET B ($BUDGET_MIB MiB)，超出 $OVER B ($OVER_MIB MiB)。\
若为有意的体积增长（如新增离线 assets）：在 PR 中说明新增内容与实测增量，\
并同步上调 .github/apk-size-budget.properties 的 budget_bytes（必要时重定 baseline_bytes）；禁止为过门禁临时抬高。"
fi

echo "体积门禁通过 ✓（余量 $((BUDGET - SIZE)) B）"
