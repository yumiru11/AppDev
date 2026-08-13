package com.yumiru11.githubapp.feature.profile

import com.yumiru11.githubapp.core.data.model.User

/**
 * 个人页 UI 状态（T20）。
 */
sealed interface ProfileUiState {
    /** 初始加载中 */
    data object Loading : ProfileUiState

    /** 未登录（Anonymous）：显式区分，UI 展示登录引导 */
    data object Anonymous : ProfileUiState

    /** 加载成功（四列表为独立 PagingData 流，不走本状态） */
    data class Success(
        val user: User,
    ) : ProfileUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: ProfileErrorType,
    ) : ProfileUiState
}

/**
 * 资料头加载错误类型。
 */
enum class ProfileErrorType {
    /** 404：用户不存在（仅 /users/{login} 可能） */
    NOT_FOUND,

    /** 网络/IO 错误 */
    NETWORK,

    /** 其他未知错误 */
    UNKNOWN,
}
