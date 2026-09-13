package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.feature.pullrequest.data.DiffParser
import com.yumiru11.githubapp.feature.pullrequest.model.DiffLine

/**
 * `PullRequestDiffView` UI 测试与截图共用的最小 patch 夹具（UI-1）。
 *
 * 三个场景：
 * - [LONG_DIFF_LINE]：约 190 字符的等宽行（labelSmall 约 6.6dp/字符 → ~1250dp），
 *   远超 411dp 手机视口，用来验证「不再截断 + 横向滚动可达行尾」；
 * - [SHORT_DIFF_LINE]：短变更行，用来验证短行 diff 的列宽仍铺满视口（不产生滚动距离）；
 * - [CONTEXT_DIFF_LINE]：context 行（unified 出现 1 次、side-by-side 双栏各 1 次 → 2 次），
 *   用来证明宽窗口切段后真的渲染了两栏。
 */
internal const val DIFF_FIXTURE_PATH = "app/src/main/kotlin/Foo.kt"

/** unified 与 side-by-side 共用的 context 行（双栏时会渲染两次） */
internal const val CONTEXT_DIFF_LINE = "class Foo {"

/** 短变更行（列宽回归用） */
internal const val SHORT_DIFF_LINE = "val short = 1"

/** 超长变更行：远超 411dp 视口（横向滚动 / 不截断回归用） */
internal val LONG_DIFF_LINE: String = "val long = \"${"a".repeat(180)}\""

/** 含超长行的 patch：已变更行带行内评论锚点（可点击行）。 */
internal fun diffViewFixtureLines(): List<DiffLine> =
    DiffParser.parse(
        listOf(
            "diff --git a/$DIFF_FIXTURE_PATH b/$DIFF_FIXTURE_PATH",
            "index 1111111..2222222 100644",
            "--- a/$DIFF_FIXTURE_PATH",
            "+++ b/$DIFF_FIXTURE_PATH",
            "@@ -1,4 +1,5 @@",
            " $CONTEXT_DIFF_LINE",
            "-import old.Thing",
            "+import new.Thing",
            "+$LONG_DIFF_LINE",
            " }",
        ).joinToString("\n"),
    )

/** 全部为短行的 patch（视口铺满 / 无横向滚动距离回归用）；路径也刻意短，
 * 保证**最长行**也窄于 411dp 视口（否则 `diff --git` 头本身就会产生滚动距离）。 */
internal fun shortDiffViewFixtureLines(): List<DiffLine> =
    DiffParser.parse(
        listOf(
            "diff --git a/Foo.kt b/Foo.kt",
            "index 1111111..2222222 100644",
            "--- a/Foo.kt",
            "+++ b/Foo.kt",
            "@@ -1,4 +1,5 @@",
            " $CONTEXT_DIFF_LINE",
            "-val old = 1",
            "+$SHORT_DIFF_LINE",
            " }",
        ).joinToString("\n"),
    )
