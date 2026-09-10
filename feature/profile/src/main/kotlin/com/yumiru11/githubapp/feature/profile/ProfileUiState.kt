package com.yumiru11.githubapp.feature.profile

import com.yumiru11.githubapp.core.data.model.User

/**
 * 个人页 UI 状态（T20；L10 增他人主页只读态与关注态）。
 */
sealed interface ProfileUiState {
    /** 初始加载中 */
    data object Loading : ProfileUiState

    /** 未登录（Anonymous）：显式区分，UI 展示登录引导 */
    data object Anonymous : ProfileUiState

    /**
     * 加载成功（四列表为独立 PagingData 流，不走本状态）。
     *
     * @param isSelf 本人主页（PROFILE 路由）/ 他人主页（USER 路由，L10）
     * @param isFollowing 他人主页的当前关注态（[isSelf] 时恒 false，UI 不展示关注按钮）
     */
    data class Success(
        val user: User,
        val isSelf: Boolean = true,
        val isFollowing: Boolean = false,
    ) : ProfileUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: ProfileErrorType,
    ) : ProfileUiState
}

/**
 * 个人页一次性事件（Snackbar 提示；写操作失败回滚的可见反馈）。
 */
sealed interface ProfileEvent {
    /** 关注/取关失败：乐观更新已回滚，提示用户重试 */
    data object FollowActionFailed : ProfileEvent
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
