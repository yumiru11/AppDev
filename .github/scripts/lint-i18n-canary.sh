#!/usr/bin/env bash
#
# i18n lint 规则 canary —— 证明「四条规则真的在分析」，而不只是「配置里写了」。
#
# 为什么需要它（项目铁律：不经红证明的守卫会以「在跑但什么都没查」的形态上线）：
#   lint 里「规则没在跑」是**静默**的 —— 没有违规就没有任何痕迹。registry 被整包
#   跳过（见 app/build.gradle.kts 的 #170 注释）、检测器被 disable、enable 行被删，
#   报告里完全看不出来。所以这里往 feature:editor 的 debug 源集注入 4 个**必然违规**的
#   canary 文件，跑单模块 lint，断言报告里 4 个 issue id 都在，然后清理。
#
# 注入物（.github/lint-canary/feature/editor/src/debug/**，不是任何模块的源码目录）：
#   - res/values/lint_canary.xml  → MissingTranslation（values-zh-rCN 缺该 key）
#                                   + StringFormatInvalid（%1$ g；调用层的裸 % 辅助触发）
#   - res/layout/lint_canary.xml  → HardcodedText（本项目全 Compose，真实代码永远 0 命中）
#   - kotlin/.../LintCanary.kt    → SetTextI18n（TextView.setText 字面量，同样永远 0 命中）
#
# 防退化两道防线（互补）：
#   1. buildSrc/src/main/kotlin/AppDevI18nLint.kt 的 verifyI18nRulesEnabled()：
#      配置期自检，enable 行被删 → 构建直接失败（本脚本跑之前就拦下）。
#   2. 本脚本：运行期证明，检测器真的执行并产出这 4 个 issue id。
#
# 用法：bash .github/scripts/lint-i18n-canary.sh
# 退出码：0 = 四条规则全部证明在跑；非 0 = 报告缺失或缺少任一 issue id
#        （lint 任务本身因 canary 的 error 级违规非零退出属预期，不作为失败判据）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CANARY_ROOT="$REPO_ROOT/.github/lint-canary"
MODULE_DIR="feature/editor"
MODULE_PATH=":feature:editor"
SRC_SET_DIR="$REPO_ROOT/$MODULE_DIR/src/debug"
REPORT="$REPO_ROOT/$MODULE_DIR/build/reports/lint-results-debug.txt"

ISSUE_IDS=(MissingTranslation HardcodedText SetTextI18n StringFormatInvalid)

log() { echo "[i18n-canary] $*"; }

cleanup() {
  # 只删注入物；目录链从**仍存在的父目录**向上删（lintcanary 会被 rm -rf 整棵删掉，
  # 不能再拿它当 rmdir 起点 —— 否则 rmdir 报 ENOENT 后不会继续清祖先，留下空目录）。
  rm -rf "$SRC_SET_DIR/kotlin/com/yumiru11/githubapp/feature/editor/lintcanary"
  rm -f "$SRC_SET_DIR/res/layout/lint_canary.xml" "$SRC_SET_DIR/res/values/lint_canary.xml"
  rmdir -p --ignore-fail-on-non-empty \
    "$SRC_SET_DIR/kotlin/com/yumiru11/githubapp/feature/editor" \
    "$SRC_SET_DIR/res/layout" \
    "$SRC_SET_DIR/res/values" 2>/dev/null || true
}
trap cleanup EXIT

# ── 前置检查：canary 夹具必须存在（被误删时不能静默通过）──────────────────────────
for f in \
  "$CANARY_ROOT/$MODULE_DIR/src/debug/kotlin/com/yumiru11/githubapp/feature/editor/lintcanary/LintCanary.kt" \
  "$CANARY_ROOT/$MODULE_DIR/src/debug/res/layout/lint_canary.xml" \
  "$CANARY_ROOT/$MODULE_DIR/src/debug/res/values/lint_canary.xml"; do
  if [ ! -f "$f" ]; then
    echo "::error::canary 夹具缺失：$f（删除夹具 = 本门禁失效）"
    exit 1
  fi
done

# 幂等：先清掉上次异常退出的残留，再删报告防「读旧报告假绿」。
cleanup
rm -f "$REPORT"

log "注入 canary 到 $SRC_SET_DIR"
mkdir -p "$SRC_SET_DIR"
cp -r "$CANARY_ROOT/$MODULE_DIR/src/debug/." "$SRC_SET_DIR/"

cd "$REPO_ROOT"
# canary 故意注入 error 级违规（MissingTranslation / StringFormatInvalid），abortOnError=true
# 会让 lint 任务以非零退出 —— 这是预期现象，不是 canary 失败；真正的判据是报告内容。
# 配置期守卫失败 / 编译失败 / lint 崩溃时报告不会生成（上面已先删旧报告），由下面的断言兜底。
log "运行 $MODULE_PATH:lintDebug --no-daemon（预期非零退出：canary 含 error 级违规）"
set +e
./gradlew "$MODULE_PATH:lintDebug" --no-daemon
GRADLE_EXIT=$?
set -e
log "lint 任务退出码：$GRADLE_EXIT（非零属预期，继续断言报告）"

if [ ! -f "$REPORT" ]; then
  echo "::error::lint 报告未生成：$REPORT —— 配置期守卫失败 / 编译失败 / lint 未跑完（退出码 $GRADLE_EXIT）"
  exit 1
fi

# ── 断言：4 个 issue id 必须出现在报告里（缺一即红）────────────────────────────
MISSING=()
for id in "${ISSUE_IDS[@]}"; do
  if grep -q "\[$id\]" "$REPORT"; then
    log "OK   $id"
  else
    MISSING+=("$id")
    log "MISS $id"
  fi
done

if [ "${#MISSING[@]}" -ne 0 ]; then
  echo "::error::i18n canary 失败，报告缺少：${MISSING[*]}（见 $REPORT）"
  echo "::error::规则被 disable/跳过，或检测器未执行 —— 门禁已静默退化"
  exit 1
fi

log "四条 i18n 规则全部证明在跑（报告：$REPORT）"
