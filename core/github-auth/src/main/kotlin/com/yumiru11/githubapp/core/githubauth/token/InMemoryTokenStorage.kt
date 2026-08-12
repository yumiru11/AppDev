package com.yumiru11.githubapp.core.githubauth.token

/**
 * 内存版 [TokenStorage]（单测/预览用，ADR-0002）。
 *
 * 语义与生产实现 [EncryptedTokenStorage] 完全一致：
 * loadSession 永不抛异常（未写入/已清除时返回全空 [SessionData]）；
 * saveSession 整体覆盖（null 字段清除旧值）。
 */
class InMemoryTokenStorage : TokenStorage {
    private var session = SessionData()

    override fun loadSession(): SessionData = session

    override fun saveSession(session: SessionData) {
        this.session = session
    }

    override fun clear() {
        session = SessionData()
    }
}
