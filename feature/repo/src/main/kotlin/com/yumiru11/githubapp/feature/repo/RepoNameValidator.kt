package com.yumiru11.githubapp.feature.repo

/**
 * 仓库名非法原因（L04；UI 层 stringResource 映射文案，ViewModel 不产英文）。
 *
 * 与 [RepoNameValidator] 一一对应，枚举名即规则名，便于测试矩阵逐条断言。
 */
enum class RepoNameError {
    /** 空名（含纯空白） */
    EMPTY,

    /** 含合法字符集之外的字符（仅允许字母/数字/下划线/连字符/点） */
    INVALID_CHARS,

    /** 纯点（"." / ".." 等，git 语义保留） */
    ONLY_DOTS,

    /** 以 ".git" 结尾（GitHub 保留名） */
    GIT_SUFFIX,

    /** 以下划线结尾（GitHub 保留） */
    TRAILING_UNDERSCORE,

    /** 超过 [RepoNameValidator.MAX_LENGTH] 字符 */
    TOO_LONG,
}

/**
 * 仓库名校验（L04，纯函数，GitHub 命名规则参考）。
 *
 * 规则（与 GitHub 网页创建仓库表单一致）：
 * 1. 非空（纯空白视为空）
 * 2. 长度上限 [MAX_LENGTH]
 * 3. 仅允许 `[A-Za-z0-9_.-]`（正则 `^[\w.-]+$`）
 * 4. 不能是纯点（"." / ".."）
 * 5. 不能以 `.git` 结尾（大小写不敏感）
 * 6. 不能以下划线结尾
 *
 * 纯函数 + 无 Android 依赖：表格化单测直接覆盖（feature:repo 覆盖率门禁 0.92）。
 */
object RepoNameValidator {
    /** GitHub 仓库名长度上限（字符数） */
    const val MAX_LENGTH = 100

    private const val GIT_SUFFIX = ".git"

    /** `\w` 在 Kotlin/JVM 上等价于 `[a-zA-Z_0-9]` */
    private val ALLOWED_CHARS = Regex("^[\\w.-]+$")

    /** 命中则非法；null = 合法名。 */
    fun error(name: String): RepoNameError? =
        when {
            name.isBlank() -> RepoNameError.EMPTY
            name.length > MAX_LENGTH -> RepoNameError.TOO_LONG
            !ALLOWED_CHARS.matches(name) -> RepoNameError.INVALID_CHARS
            name.all { it == '.' } -> RepoNameError.ONLY_DOTS
            name.endsWith(GIT_SUFFIX, ignoreCase = true) -> RepoNameError.GIT_SUFFIX
            name.endsWith('_') -> RepoNameError.TRAILING_UNDERSCORE
            else -> null
        }

    /** 便捷判定（表单提交按钮 enabled 用）。 */
    fun isValid(name: String): Boolean = error(name) == null
}
