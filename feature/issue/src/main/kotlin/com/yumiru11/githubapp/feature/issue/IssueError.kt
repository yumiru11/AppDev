package com.yumiru11.githubapp.feature.issue

import com.yumiru11.githubapp.feature.issue.model.IssueErrorType
import retrofit2.HttpException
import java.io.IOException

/**
 * 异常 → [IssueErrorType] 的统一映射（列表/详情 VM 与列表分页错误共用，消除重复）。
 *
 * 规则：404 → NOT_FOUND；其余 HttpException 与 IOException → NETWORK；其余 → UNKNOWN。
 * ViewModel/UI 只产类型，不产英文文案（文案由 UI 层 stringResource 本地化）。
 */
internal fun Throwable.toIssueErrorType(): IssueErrorType =
    when {
        this is HttpException && code() == 404 -> IssueErrorType.NOT_FOUND
        this is HttpException || this is IOException -> IssueErrorType.NETWORK
        else -> IssueErrorType.UNKNOWN
    }

/**
 * 写操作失败异常 → [IssueSnackbarMessage]（T14 写失败 Snackbar 规整）。
 *
 * 规则：403 → FORBIDDEN；404 → NOT_FOUND；422 → VALIDATION；其余 HttpException/IOException → NETWORK；其余 → UNKNOWN。
 */
internal fun Throwable.toIssueSnackbarMessage(): IssueSnackbarMessage =
    when {
        this is HttpException && code() == 403 -> IssueSnackbarMessage.ERROR_FORBIDDEN
        this is HttpException && code() == 404 -> IssueSnackbarMessage.ERROR_NOT_FOUND
        this is HttpException && code() == 422 -> IssueSnackbarMessage.ERROR_VALIDATION
        this is HttpException || this is IOException -> IssueSnackbarMessage.ERROR_NETWORK
        else -> IssueSnackbarMessage.ERROR_UNKNOWN
    }
