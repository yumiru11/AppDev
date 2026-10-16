package com.yumiru11.githubapp.feature.home

/**
 * 首页「内容分区」（议题/拉取请求/讨论/发现）的加载状态。
 *
 * 与 [HomeUiState]（动态流的分页状态机）**刻意分离**：这些分区各自独立请求、
 * 独立失败——一个分区 5xx 不应该把整个首页推进 Error 态（M3E 同样是每分区一个 PagedLoader）。
 */
sealed interface HomeSectionState<out T> {
    /** 首次加载中（UI 显示骨架列表） */
    data object Loading : HomeSectionState<Nothing>

    /** 加载成功（空列表 = 空态） */
    data class Data<T>(
        val items: List<T>,
    ) : HomeSectionState<T>

    /** 加载失败（分区内错误态 + 重试；不拖垮其它分区） */
    data object Error : HomeSectionState<Nothing>
}
