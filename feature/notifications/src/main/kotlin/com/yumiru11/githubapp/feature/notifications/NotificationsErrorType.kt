package com.yumiru11.githubapp.feature.notifications

import retrofit2.HttpException
import java.io.IOException

/**
 * 通知加载错误类型。
 */
enum class NotificationsErrorType {
    /** 网络/IO 错误 */
    NETWORK,

    /** 凭据无效/过期（401/403，与网络错误区分） */
    UNAUTHORIZED,

    /** 其他未知错误 */
    UNKNOWN,
}

/**
 * 异常 → [NotificationsErrorType] 映射（T19 规则延续，#88 面板复用；原位于
 * NotificationsViewModel 文件，随面板化迁移至此单一事实来源）。
 *
 * 规则：401/403 → UNAUTHORIZED；其余 HttpException 与 IOException → NETWORK；其余 → UNKNOWN。
 * ViewModel/UI 只产类型，不产英文文案（文案由 UI 层 stringResource 本地化）。
 */
internal fun Throwable.toNotificationsErrorType(): NotificationsErrorType =
    when {
        this is HttpException && (code() == 401 || code() == 403) -> NotificationsErrorType.UNAUTHORIZED
        this is HttpException || this is IOException -> NotificationsErrorType.NETWORK
        else -> NotificationsErrorType.UNKNOWN
    }
