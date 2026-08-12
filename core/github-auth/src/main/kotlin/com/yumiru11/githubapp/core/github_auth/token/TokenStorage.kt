package com.yumiru11.githubapp.core.github_auth.token

/**
 * Token 存储抽象（ADR-0002）——token 只经此接口进出，禁止散落其他模块。
 *
 * 同步接口：本地持久化无需挂起。生产实现 [EncryptedTokenStorage]
 * （EncryptedSharedPreferences 加密落盘），测试/预览用 [InMemoryTokenStorage]（内存 Map）。
 */
interface TokenStorage {

    /**
     * 读取当前会话快照；未登录（从未写入或已清除）返回全空字段的 [SessionData]，不抛异常。
     */
    fun loadSession(): SessionData

    /**
     * 整体覆盖写入会话快照。未提供的字段（null）会清除对应存储项，
     * 调用方需用 `copy()` 保留未变更字段（避免残留旧会话数据）。
     */
    fun saveSession(session: SessionData)

    /** 清除全部凭据与会话元数据（登出）。对空存储调用是幂等 no-op。 */
    fun clear()
}
