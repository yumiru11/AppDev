package com.yumiru11.githubapp.feature.editor.lintcanary

import android.content.Context
import android.widget.TextView
import com.yumiru11.githubapp.feature.editor.R

/**
 * i18n lint 规则 canary（CI 临时注入，见 `.github/scripts/lint-i18n-canary.sh`）。
 *
 * 覆盖两条**真实代码里 0 命中**的规则 —— 本项目是纯 Compose 应用，既没有 `res/layout`，
 * 也没有 `TextView#setText`，所以 `HardcodedText` / `SetTextI18n` 的检测器是否在跑，
 * 只能靠 canary 证明（"规则没在跑"在 lint 报告里是静默的）。
 *
 * `StringFormatInvalid` 的资源层触发点在同目录 `res/values/lint_canary.xml`（`%1$ g`），
 * 本文件再补一条**调用层**触发（非格式串被 getString(res, args) 当格式串用）。
 * 注意：不能用 `String.format("...%q...")` —— lint 对字面量只查 TRIVIAL，
 * 且 `%q` 是未知转换符会被整体判 IGNORE（见 StringFormatDetector.resourceFormatString），
 * 不会产生 StringFormatInvalid（2026-09-12 实测，第一版探针就踩了这个坑）。
 *
 * ⚠️ 下面的字面量是**故意**的违规，不是待修的欠账；本文件不参与常规编译（仅 CI 注入期间存在）。
 */
internal object LintCanary {
    /** SetTextI18n：字面量直接进 setText。 */
    fun hardcodedSetText(view: TextView) {
        view.setText("Lint canary hardcoded")
    }

    /** StringFormatInvalid：把含裸 `%` 的「非格式串」当格式串调用（"not a valid format string"）。 */
    fun badFormatResource(context: Context): String = context.getString(R.string.lint_canary_missing_conversion, "x")
}
