package com.yumiru11.githubapp.core.githubauth.session

/**
 * 登出 / 切换账号时需要清空的会话级缓存（安全约束：私有数据不得跨账号残留）。
 *
 * 具体缓存实现经 Hilt `@IntoSet` 注册（如 core:github-data 的 `RoomEtagStore`），
 * [com.yumiru11.githubapp.core.githubauth.auth.OAuthSessionManager.signOut] 会逐一调用。
 * 接口放在认证层，避免认证层反向依赖具体缓存模块。
 */
fun interface SessionCacheCleaner {
    /** 清空该缓存的全部内容（跨进程持久化数据也必须删除）。 */
    suspend fun clearSessionCache()
}
