package com.yumiru11.githubapp.feature.pullrequest

import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestErrorType
import retrofit2.HttpException
import java.io.IOException

/**
 * 异常 → [PullRequestErrorType] 的统一映射（列表/详情 VM 与列表分页错误共用，消除重复）。
 *
 * 规则：404 → NOT_FOUND；其余 HttpException 与 IOException → NETWORK；其余 → UNKNOWN。
 * ViewModel/UI 只产类型，不产英文文案（文案由 UI 层 stringResource 本地化）。
 */
internal fun Throwable.toPullRequestErrorType(): PullRequestErrorType =
    when {
        this is HttpException && code() == 404 -> PullRequestErrorType.NOT_FOUND
        this is HttpException || this is IOException -> PullRequestErrorType.NETWORK
        else -> PullRequestErrorType.UNKNOWN
    }
