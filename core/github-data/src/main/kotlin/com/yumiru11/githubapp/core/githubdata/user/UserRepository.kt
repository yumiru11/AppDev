package com.yumiru11.githubapp.core.githubdata.user

import com.yumiru11.githubapp.core.data.model.User

/**
 * 当前用户仓库接口（UI 层只依赖该抽象，Hilt @Binds 装配实现）。
 */
interface UserRepository {
    /**
     * 获取当前登录用户资料。
     *
     * GraphQL viewer 读优先，失败降级 REST GET /user；双通道均失败抛
     * [com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException]。
     *
     * **保留声明（DEAD-1 核验）**：当前生产代码无调用方（Hilt 已装配 [DefaultUserRepository]，
     * Viewer 资料消费面待接入）；`DefaultUserRepositoryTest` 对双通道有完整覆盖。
     * 删除须连同接口/实现/DI 与测试整体评估，本次仅标注保留。
     */
    suspend fun getCurrentUser(): User
}
