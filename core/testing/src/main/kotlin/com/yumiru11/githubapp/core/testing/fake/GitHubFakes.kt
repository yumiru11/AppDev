package com.yumiru11.githubapp.core.testing.fake

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.User
import java.time.Instant

/*
 * GitHub 领域模型 Fake 工厂。
 *
 * 命名与位置约定：
 * - Fake 一律放本包，工厂函数命名 `fakeXxx(...)`（或 `FakeXxxFactory` 对象），
 *   如 fakeUser() / fakeRepository() / fakeIssue() / fakePullRequest()
 * - 参数只暴露对测试有意义的字段并给默认值，调用处显式表达测试意图
 * - 禁止在 fake 里访问网络/数据库；需要桩行为的接口放同包 `FakeXxxRepository` 命名
 *
 * 真实模型来自 core:data（T5 落地），返回带合理默认值的完整模型，测试只需覆盖关心的字段。
 */

/** GitHub Fake 工厂：命名与位置约定见文件头注释 */
object GitHubFakes {
    fun fakeUser(
        login: String = "octocat",
        name: String? = "The Octocat",
        avatarUrl: String? = "https://avatars.githubusercontent.com/u/583231?v=4",
        bio: String? = null,
    ): User = User(login = login, name = name, avatarUrl = avatarUrl, bio = bio)

    fun fakeRepository(
        ownerLogin: String = "octocat",
        name: String = "Hello-World",
        isPrivate: Boolean = false,
        stargazerCount: Int = 1_234,
        forkCount: Int = 56,
        language: String? = "Kotlin",
        defaultBranch: String? = "main",
        updatedAt: Instant? = Instant.parse("2026-01-15T10:30:00Z"),
    ): Repository =
        Repository(
            ownerLogin = ownerLogin,
            name = name,
            isPrivate = isPrivate,
            stargazerCount = stargazerCount,
            forkCount = forkCount,
            language = language,
            defaultBranch = defaultBranch,
            updatedAt = updatedAt,
        )
}
