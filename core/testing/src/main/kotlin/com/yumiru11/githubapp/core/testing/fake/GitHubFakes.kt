package com.yumiru11.githubapp.core.testing.fake

/*
 * GitHub 领域模型 Fake 工厂（骨架）。
 *
 * 命名与位置约定（后续 GitHub 模型在 core:data / core:github-* 落地后遵守）：
 * - Fake 一律放本包，工厂函数命名 `fakeXxx(...)`（或 `FakeXxxFactory` 对象），
 *   如 fakeUser() / fakeRepository() / fakeIssue() / fakePullRequest()
 * - 参数只暴露对测试有意义的字段并给默认值，调用处显式表达测试意图
 * - 禁止在 fake 里访问网络/数据库；需要桩行为的接口放同包 `FakeXxxRepository` 命名
 *
 * 当前状态：模型层尚未实现（T2 骨架期），先以两个最小示例锁定命名与位置；
 * core:data 真实模型落地后，用真实模型类型替换下方占位 data class。
 */

/** GitHub Fake 工厂：命名与位置约定见文件头注释 */
object GitHubFakes {
    fun fakeUser(
        login: String = "octocat",
        id: Long = 1L,
    ): SampleUser = SampleUser(login = login, id = id)

    fun fakeRepository(
        owner: String = "octocat",
        name: String = "Hello-World",
        isPrivate: Boolean = false,
    ): SampleRepository = SampleRepository(owner = owner, name = name, isPrivate = isPrivate)
}

/** 示例占位模型：GitHub 用户（真实模型落地后替换为 core:data 的 User） */
data class SampleUser(
    val login: String,
    val id: Long,
)

/** 示例占位模型：GitHub 仓库（真实模型落地后替换为 core:data 的 Repository） */
data class SampleRepository(
    val owner: String,
    val name: String,
    val isPrivate: Boolean,
)
