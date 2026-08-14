package com.yumiru11.githubapp.feature.home

import androidx.paging.PagingData
import com.yumiru11.githubapp.feature.home.model.FeedItem
import kotlinx.coroutines.flow.Flow

/**
 * 首页动态流 UI 状态（T10）。
 *
 * - [Success] 内嵌 Paging 数据流；分页加载错误由 UI 层 LazyPagingItems.loadState
 *   呈现与重试（Paging 拥有加载生命周期，不进 VM 状态机）
 * - [Error] 为 VM 层可捕获的失败（登录用户获取/数据流构造期异常）
 */
sealed interface HomeUiState {
    /** 加载中 */
    data object Loading : HomeUiState

    /** 未登录：明确引导登录（复用 T4 auth 状态驱动） */
    data object Unauthenticated : HomeUiState

    /** 加载成功（[feed] 分页数据流） */
    data class Success(
        val feed: Flow<PagingData<FeedItem>>,
    ) : HomeUiState

    /** 加载失败（错误类型驱动文案，UI 层 stringResource 映射） */
    data class Error(
        val errorType: HomeErrorType,
    ) : HomeUiState
}

/**
 * 首页动态流加载错误类型。
 */
enum class HomeErrorType {
    /** 网络/IO 错误 */
    NETWORK,

    /** 凭据无效/过期（401/403，P0-7：与网络错误区分） */
    UNAUTHORIZED,

    /** 其他未知错误 */
    UNKNOWN,
}
