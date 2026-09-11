#!/usr/bin/env bash
# ============================================================
# 清理 CI 截图 release（治「Releases 页被截图刷屏」）
#
# 用法：
#   bash prune-screenshot-releases.sh [--dry-run] [--keep N] [--max-age-days D]
#
# 口径（保守，宁可不删）：
#   1. 只碰 tag 以 screenshots- 开头的 release —— **绝不碰真实发布 tag**
#   2. 永远保留最近 N 个（默认 5），避免刚跑的 PR 评论图失效
#   3. 其余满足其一才删：① 关联 PR 已 MERGED/CLOSED；② 创建超过 D 天（默认 14）
#
# 为什么单独成脚本而不是写进 workflow：这段逻辑要能**本地跑 dry-run 验证**，
# 塞在 YAML 里就只能靠 push 上去看结果（本仓库已有多次「CI 逻辑没本地验证 → 连红」的教训）。
# ============================================================
set -euo pipefail

DRY_RUN=false
KEEP=5
MAX_AGE_DAYS=14

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    --keep) KEEP="$2"; shift 2 ;;
    --max-age-days) MAX_AGE_DAYS="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

mapfile -t ROWS < <(gh release list --limit 500 --json tagName,createdAt \
  --jq '[.[] | select(.tagName | startswith("screenshots-"))] | sort_by(.createdAt) | reverse | .[] | .tagName + " " + .createdAt')

echo "截图 release 总数: ${#ROWS[@]}（保留最近 $KEEP 个；超过 ${MAX_AGE_DAYS} 天或 PR 已关闭的删除）"

NOW=$(date -u +%s)
KEPT=0; SKIPPED=0; DELETED=0

for i in "${!ROWS[@]}"; do
  read -r TAG CREATED <<< "${ROWS[$i]}"
  [ -z "${TAG:-}" ] && continue

  if [ "$i" -lt "$KEEP" ]; then
    KEPT=$((KEPT + 1))
    continue
  fi

  REASON=""
  # screenshots-pr<N>-<runId> → 取 PR 号；旧格式 screenshots-<runId> 无 PR 号
  if [[ "$TAG" =~ ^screenshots-pr([0-9]+)- ]]; then
    PR="${BASH_REMATCH[1]}"
    STATE=$(gh pr view "$PR" --json state -q .state 2>/dev/null || echo "UNKNOWN")
    if [ "$STATE" = "MERGED" ] || [ "$STATE" = "CLOSED" ]; then
      REASON="PR #$PR 已 $STATE"
    fi
  fi

  if [ -z "$REASON" ]; then
    TS=$(date -u -d "$CREATED" +%s)
    AGE_DAYS=$(( (NOW - TS) / 86400 ))
    if [ "$AGE_DAYS" -gt "$MAX_AGE_DAYS" ]; then
      REASON="创建超过 ${MAX_AGE_DAYS} 天（${AGE_DAYS} 天）"
    fi
  fi

  if [ -z "$REASON" ]; then
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  if [ "$DRY_RUN" = "true" ]; then
    echo "[dry-run] 将删除 $TAG（$REASON）"
    DELETED=$((DELETED + 1))
  elif gh release delete "$TAG" --yes --cleanup-tag >/dev/null 2>&1; then
    echo "已删除 $TAG（$REASON）"
    DELETED=$((DELETED + 1))
  else
    echo "::warning::删除失败 $TAG"
  fi
done

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### 截图 release 清理"
    echo ""
    echo "- 总数：${#ROWS[@]}（保留最近 $KEEP）"
    echo "- 保留：$KEPT / 跳过（仍活跃且未过期）：$SKIPPED / 删除：$DELETED"
    echo "- dry_run=$DRY_RUN"
  } >> "$GITHUB_STEP_SUMMARY"
fi

echo "结果：保留 $KEPT / 跳过 $SKIPPED / 删除 $DELETED（dry_run=$DRY_RUN）"
