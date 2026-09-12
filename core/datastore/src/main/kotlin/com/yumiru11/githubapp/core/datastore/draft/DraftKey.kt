package com.yumiru11.githubapp.core.datastore.draft

/**
 * 草稿键：一个「编辑目标」的稳定标识（需求审计 2026-09-11 §10 P2「草稿无持久化」的存储侧身份）。
 *
 * 三条设计约束：
 * 1. **身份 = 编辑目标，不是编辑器实例** —— 重建 ViewModel、退到查看器再进、进程被杀重启之后，
 *    同一个编辑目标必须算出同一个键（否则草稿永远恢复不出来）。
 * 2. **不透明** —— 键只作存储标量使用，**永不反解**。段拼接允许出现 `/` 等字符而不产生歧义
 *    （无需转义/哈希），代价是键不可读；可读性由 [DraftTargets] 的工厂函数承担。
 * 3. **目标类型前缀隔离键空间** —— 同 owner/repo/path 的「文件编辑」与（后续接线的）「Issue 正文」
 *    落在不同前缀下，互不覆盖。
 *
 * 实例只能经 [DraftTargets] 构造，禁止在 feature 里手拼字符串（键规则单点维护）。
 */
@JvmInline
value class DraftKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "DraftKey must not be blank" }
    }
}
