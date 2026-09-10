package com.yumiru11.githubapp.logging

import com.yumiru11.githubapp.core.common.logging.LogRedaction
import timber.log.Timber

/**
 * Debug 日志树（#169 / L14）：默认 [Timber.DebugTree] 的脱敏包装。
 *
 * 只替换消息体：throwable 由 Timber 单独格式化，栈里不含凭据。
 * release 构建不 plant 任何树（且 R8 会裁掉调用点），因此线上零日志开销。
 */
class RedactingDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        super.log(priority, tag, LogRedaction.redact(message), t)
    }
}
