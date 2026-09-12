package com.yumiru11.githubapp.core.datastore.draft

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * [DraftRepository] 默认实现（Preferences DataStore，与用户偏好共用同一个 `user_preferences` 文件）。
 *
 * 存储布局（每个草稿两个键，前缀把两族键隔开）：
 * ```text
 * draft.<键>       -> String  草稿正文
 * draft_at.<键>    -> Long    最后写入时间（仅用于 [trimToMaxDrafts] 淘汰最旧）
 * ```
 * 键只按**名字前缀**枚举（Preferences 无"按前缀删除"，故 [trimToMaxDrafts] 在 edit 事务内扫描 `asMap()`）。
 *
 * 写入时间取 `System.currentTimeMillis()`：只用于"谁更旧"的相对排序，同一毫秒内的并列顺序无关紧要
 * （单测用 sleep 拉开毫秒以断言淘汰顺序）。
 */
class DefaultDraftRepository(
    private val dataStore: DataStore<Preferences>,
) : DraftRepository {
    override suspend fun load(key: DraftKey): String? = dataStore.data.first()[contentKey(key)]

    override suspend fun save(
        key: DraftKey,
        content: String,
    ) {
        // 空内容不算草稿；超长内容不落盘（见 DraftRepository 契约：宁缺勿截断）
        if (content.isBlank() || content.length > MAX_DRAFT_CHARS) {
            clear(key)
            return
        }
        dataStore.edit { prefs ->
            prefs[contentKey(key)] = content
            prefs[timestampKey(key)] = System.currentTimeMillis()
            trimToMaxDrafts(prefs)
        }
    }

    override suspend fun clear(key: DraftKey) {
        dataStore.edit { prefs ->
            prefs.remove(contentKey(key))
            prefs.remove(timestampKey(key))
        }
    }

    /**
     * 条数上限：超出 [MAX_DRAFT_COUNT] 时按写入时间淘汰最旧的若干条。
     *
     * 与单条字数上限一起构成磁盘占用的硬上限（`MAX_DRAFT_COUNT × MAX_DRAFT_CHARS`），
     * 避免"用户长期编辑大量不同文件"把 DataStore 文件撑大。
     */
    private fun trimToMaxDrafts(prefs: MutablePreferences) {
        val contentKeyNames = prefs.asMap().keys.map { it.name }.filter { it.startsWith(CONTENT_KEY_PREFIX) }
        if (contentKeyNames.size <= MAX_DRAFT_COUNT) return
        val excessCount = contentKeyNames.size - MAX_DRAFT_COUNT
        contentKeyNames
            .sortedBy { contentKeyName -> prefs[timestampKeyOf(contentKeyName)] ?: 0L }
            .take(excessCount)
            .forEach { contentKeyName ->
                prefs.remove(stringPreferencesKey(contentKeyName))
                prefs.remove(timestampKeyOf(contentKeyName))
            }
    }

    private fun contentKey(key: DraftKey) = stringPreferencesKey(CONTENT_KEY_PREFIX + key.value)

    private fun timestampKey(key: DraftKey) = timestampKeyOf(CONTENT_KEY_PREFIX + key.value)

    private fun timestampKeyOf(contentKeyName: String) =
        longPreferencesKey(TIMESTAMP_KEY_PREFIX + contentKeyName.removePrefix(CONTENT_KEY_PREFIX))

    companion object {
        /**
         * 单条草稿字数上限（≈200 KB）。
         *
         * 取值依据：GitHub 单条评论上限 65536 字符、Issue/PR 正文与绝大多数 Markdown/代码文件都远小于它；
         * 超过此值的文件（超大 Markdown/代码）不享受草稿保护 —— 这是**有意的**取舍（见类 KDoc 与
         * [DraftRepository] 契约），不是漏判：Preferences DataStore 每次写入整文件重写，超大正文代价过高，
         * 真要覆盖需换 Room（那是一次独立选型，不随本票）。
         */
        const val MAX_DRAFT_CHARS = 200_000

        /** 草稿条数上限（超出按最旧淘汰）。 */
        const val MAX_DRAFT_COUNT = 12

        private const val CONTENT_KEY_PREFIX = "draft."
        private const val TIMESTAMP_KEY_PREFIX = "draft_at."
    }
}
