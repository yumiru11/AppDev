package com.yumiru11.githubapp.feature.editor

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.githubdata.repository.RepositoryRepository

/**
 * 测试用 [RepositoryRepository] 替身（编辑器 `@mention` 只需协作者）。
 *
 * [getRepository] 显式失败：编辑器从不使用仓库概览，误用要立刻炸出来而不是静默返回假值。
 */
internal class FakeRepositoryRepository(
    private val collaborators: List<User> = emptyList(),
    private val failure: Throwable? = null,
) : RepositoryRepository {
    var requestedOwner: String? = null
        private set
    var requestedName: String? = null
        private set

    override suspend fun getRepository(
        owner: String,
        name: String,
    ): Repository = error("编辑器 @mention 候选不使用仓库概览")

    override suspend fun listCollaborators(
        owner: String,
        name: String,
    ): List<User> {
        requestedOwner = owner
        requestedName = name
        failure?.let { throw it }
        return collaborators
    }
}
