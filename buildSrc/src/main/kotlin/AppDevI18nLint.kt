import com.android.build.api.dsl.Lint

/**
 * i18n lint 规则契约（plan.md §11.4）。
 *
 * 四条规则由**约定插件**（application / library）统一启用，因此 app 与全部 core/feature 模块
 * 生效，而不是只写在 `app/build.gradle.kts` 里 —— 本仓的用户可见文案 99% 在 feature/core，
 * 只配 app 等于没配（`I18nHardcodedStringGuardTest` 的 KDoc 记录了同一结论）。
 *
 * 与既有两道 i18n 守卫的**分工**（互补，不是重复）：
 * - `I18nParityTest`：**资源键存在性**。en `values/strings.xml` 的每个 key，zh-rCN 必须有；
 *   纯文本解析，不看引用关系，也不管资源是否被使用。
 * - `I18nHardcodedStringGuardTest`：**Kotlin 源码硬编码文案**。扫描 `src/main` 下的 `.kt`
 *   字面量（`Text("…")`、UI 形参、中文、UI 文件内整句英文常量）。lint 的 `HardcodedText`
 *   只作用于 **XML 布局**，对 Compose 代码无效 —— 两者覆盖面不重叠。
 * - 本契约（lint 四条）：**lint 视野内**的 i18n 缺陷 —— 资源缺失翻译（`MissingTranslation`）、
 *   XML 布局硬编码（`HardcodedText`）、`TextView.setText` 字面量（`SetTextI18n`）、
 *   资源格式串非法（`StringFormatInvalid`）。这四条里 `StringFormatInvalid` 是**没有任何
 *   其他守卫覆盖**的（格式串写错只会在真机上以 `IllegalFormatException` / 丢字暴露）。
 *
 * ⚠️ 防退化：这四条规则一旦被静默移除，lint 不会报错（不违规的规则在报告里没有任何痕迹）。
 * 因此有两道防线：
 * 1. [verifyI18nRulesEnabled]：约定插件配置期自检，规则被删/被 disable → 构建直接失败；
 * 2. `.github/scripts/lint-i18n-canary.sh`：在 `feature:editor` 注入 4 条**故意违规**的
 *    canary 文件 → 跑单模块 lint → 断言报告里出现这 4 个 issue id → 清理。它证明的是
 *    「检测器真的在跑」，而不只是「配置里写了」（项目史上出现过 registry 被整包跳过、
 *    门禁看起来在跑其实什么都没查，见 `app/build.gradle.kts` 的 lint 段注释）。
 */
val I18N_LINT_RULES: List<String> =
    listOf(
        "MissingTranslation",
        "HardcodedText",
        "SetTextI18n",
        "StringFormatInvalid",
    )

/**
 * 验收用的**独立字面量**：故意与 [I18N_LINT_RULES] 重复一份。
 *
 * 为什么不能直接拿 [I18N_LINT_RULES] 当期望值：`I18N_LINT_RULES.filterNot { it in enable }`
 * 是**自指断言** —— 把某条规则从 list 里删掉时，它同时从 enable 与「期望」里消失，
 * check 永远通过。2026-09-12 红证明实测：从 [I18N_LINT_RULES] 删掉 "HardcodedText" 后
 * 配置期守卫静默放行（断言恒真），canary 也只是因为该规则是 lint 默认开启才没变红。
 * 期望值必须独立写死：删一条规则要同时改两处，不可能再「顺手删掉」。
 */
private val REQUIRED_I18N_LINT_RULES: List<String> =
    listOf(
        "MissingTranslation",
        "HardcodedText",
        "SetTextI18n",
        "StringFormatInvalid",
    )

/** 在约定插件的 `lint {}` 块内启用四条 i18n 规则（`enable` 而非 `checkOnly`：不排他）。 */
fun Lint.enableI18nRules() {
    enable += I18N_LINT_RULES
}

/**
 * 配置期自检：四条规则必须**出现在 `enable` 里且不在 `disable` 里**。
 *
 * 用 `check` 而不是注释：注释拦不住「为了让它绿而把规则删掉」，
 * 而配置期失败会在任何 Gradle 调用（含 `assembleDebug`）里立刻暴露。
 *
 * 两个关键点：
 * 1. 期望值取自独立的 [REQUIRED_I18N_LINT_RULES]（见其 KDoc：自指断言会恒真）；
 * 2. **同时查 `disable`** —— lint 的 disable 优先级高于 enable（`Configuration.isEnabled`
 *    先查 disabled 集合），只查 enable 会漏掉「enable 保留、另加一行 disable」这种
 *    最省事的绕过方式；被 disable 的规则在报告里没有任何痕迹，必须配置期就拦。
 *
 * 注意本检查发生在约定插件 apply 时（模块自己的 `android { lint { … } }` 之前），
 * 因此模块级 disable 看不到 —— 那种情况由运行期的 canary 兜底（2026-09-12 红证明实测）。
 */
fun Lint.verifyI18nRulesEnabled(modulePath: String) {
    val missing = REQUIRED_I18N_LINT_RULES.filterNot { it in enable }
    val disabled = REQUIRED_I18N_LINT_RULES.filter { it in this.disable }
    check(missing.isEmpty() && disabled.isEmpty()) {
        "$modulePath: i18n lint 规则被移除：未启用=$missing、被 disable=$disabled（plan.md §11.4）。" +
            "删规则 = 门禁失效；确需变更请同步改 buildSrc/src/main/kotlin/AppDevI18nLint.kt " +
            "（两处清单）与 .github/scripts/lint-i18n-canary.sh。"
    }
}
