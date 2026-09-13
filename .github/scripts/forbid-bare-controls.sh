#!/usr/bin/env bash
# ============================================================
# 禁新增裸控件门禁（设计系统 Batch 3）—— docs/design-system/implementation-plan.md §5
#
# 规则（ADR-0010「门禁新增」）：六类裸 M3 控件的词边界计数不得高于
# .github/bare-controls-baseline.txt 的基准，只允许持平或下降。
# 要放宽/删除基准条目，必须改基准文件并在 PR 说明理由（计划 §5.1）。
#
# 口径（计划 §1.1 / §5.4）：
#   - 只扫 *.kt；排除 */build/、*/src/test/、*Test.kt；
#   - 白名单目录（既有包装实现，不计迁移目标）：core/designsystem/、core/ui/、prototype/；
#   - 词边界 \b<Name>( —— 自定义命名控件（RepoListCard）与状态对象（SnackbarHostState）不算；
#   - 本机 .worktrees/ 是 git worktree 副本（已 gitignore），排除以免重复计数。
#
# 红→绿验证（计划 §5.3，强制）：临时引入一处裸 Card( 必须 exit 1 并打印超基准项；
# 撤销后必须恢复 exit 0。证据归档在 Batch 3 的 PR body。
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASELINE="$ROOT/.github/bare-controls-baseline.txt"
cd "$ROOT"

fail() {
  echo "::error::$1"
  exit 1
}

[ -f "$BASELINE" ] || fail "缺少基准文件：$BASELINE"
[ -f settings.gradle.kts ] || fail "不在仓库根目录运行（找不到 settings.gradle.kts）"

# 扫描面自检：主源码 .kt 少于 100 个说明工作目录/仓库结构异常，
# 防止「路径不对 → 一切计数为 0 → 假绿」。
SCANNED=$(find . -name '*.kt' -not -path '*/build/*' -not -path './.git/*' -not -path './.worktrees/*' | wc -l)
[ "$SCANNED" -ge 100 ] || fail "扫描到的 .kt 仅 $SCANNED 个（< 100）——仓库结构异常，拒绝以空集合判绿"

# 词边界计数（= 计划 §1.1 口径）。grep 无命中时退出码为 1，属合法空集。
count_bare() {
  local name="$1"
  grep -rEn --include='*.kt' "\\b${name}\\(" . 2>/dev/null |
    grep -v '/build/' |
    grep -v '/src/test/' |
    grep -v 'Test\.kt' |
    grep -v '/core/designsystem/' |
    grep -v '/core/ui/' |
    grep -v '/prototype/' |
    grep -v '/\.worktrees/' |
    wc -l || true
}

PATTERNS=(Card Scaffold FilterChip AlertDialog ModalBottomSheet SnackbarHost)

declare -A BASE=()
while IFS='=' read -r raw_name raw_value; do
  name="${raw_name// /}"
  value="${raw_value// /}"
  [ -z "$name" ] && continue
  [[ "$name" == \#* ]] && continue
  [[ "$value" =~ ^[0-9]+$ ]] || fail "基准行格式错误（应为 <Name>=<count>）：$raw_name=$raw_value"
  BASE["$name"]="$value"
done <"$BASELINE"

for known in "${PATTERNS[@]}"; do
  [ -n "${BASE[$known]:-}" ] || fail "基准文件缺少 $known 条目（六类必须齐全）"
done
for parsed in "${!BASE[@]}"; do
  found=0
  for known in "${PATTERNS[@]}"; do
    [ "$parsed" = "$known" ] && found=1
  done
  [ "$found" = 1 ] || fail "基准文件出现未知条目：$parsed（拼写错误会静默放弃对应守卫）"
done

VIOLATIONS=()
echo "裸控件计数（词边界，排除 build/test 与白名单）："
printf '  %-18s %6s %6s\n' '控件' '实测' '基准'
for name in "${PATTERNS[@]}"; do
  base="${BASE[$name]}"
  actual="$(count_bare "$name")"
  mark=''
  if [ "$actual" -gt "$base" ]; then
    mark='  ← 超基准'
    VIOLATIONS+=("$name: 实测 $actual > 基准 $base（只许持平或下降）")
  fi
  printf '  %-18s %6s %6s%s\n' "$name" "$actual" "$base" "$mark"
done

if [ "${#VIOLATIONS[@]}" -gt 0 ]; then
  for v in "${VIOLATIONS[@]}"; do
    echo "::error::裸控件回流 —— $v"
  done
  exit 1
fi

echo "OK：六类裸控件均未超基准（扫描 $SCANNED 个 .kt）"
