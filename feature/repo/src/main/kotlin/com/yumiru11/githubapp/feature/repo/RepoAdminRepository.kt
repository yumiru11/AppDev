@file:Suppress("TooGenericExceptionCaught")
// 网络/IO/HTTP 错误统一归一化为 GitHubRequestException（既有 GitHubError 口径），
// 异常即结果，无需再抛（RepoRepository 同款先例）。

package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.githubdata.error.GitHubRequestException
import com.yumiru11.githubapp.core.githubdata.error.asGitHubError
import com.yumiru11.githubapp.core.githubrest.api.RepositoryApi
import com.yumiru11.githubapp.core.githubrest.api.UserApi
import com.yumiru11.githubapp.core.githubrest.model.CreateRepositoryRequest
import com.yumiru11.githubapp.core.githubrest.model.RepositoryDto
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仓库创建/删除数据仓库（L04）。
 *
 * 与 [RepoRepository]（读）/ [RepoManagementRepository]（Star/Watch/Fork/Releases）分离：
 * 这里承载账号级仓库生命周期写操作，失败一律归一化为
 * [com.yumiru11.githubapp.core.githubdata.error.GitHubError]：
 * - 创建：422 → [com.yumiru11.githubapp.core.githubdata.error.GitHubError.Validation]（重名/非法名）
 *        403 → [com.yumiru11.githubapp.core.githubdata.error.GitHubError.Forbidden]（配额/无权限）
 * - 删除：403 → Forbidden（非 admin）、404 → NotFound（仓库不存在）
 */
@Singleton
class RepoAdminRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val repositoryApi: RepositoryApi,
    ) {
        /**
         * 创建仓库（POST /user/repos）。
         *
         * @param name 仓库名（调用前必须过 [RepoNameValidator]）
         * @param description 描述（空白 → 不提交该字段）
         * @param isPrivate 私有仓库
         * @param autoInit 自动初始化 README + 默认分支
         * @param gitignoreTemplate .gitignore 模板名（如 "Android"；null = 不生成）
         * @param licenseTemplate License 模板名（如 "mit"；null = 不生成）
         */
        suspend fun createRepository(
            name: String,
            description: String?,
            isPrivate: Boolean,
            autoInit: Boolean,
            gitignoreTemplate: String? = null,
            licenseTemplate: String? = null,
        ): Result<Repository> =
            try {
                val dto =
                    userApi.createRepository(
                        CreateRepositoryRequest(
                            name = name,
                            description = description,
                            isPrivate = isPrivate,
                            autoInit = autoInit,
                            gitignoreTemplate = gitignoreTemplate,
                            licenseTemplate = licenseTemplate,
                        ),
                    )
                Result.success(dto.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e.asGitHubRequestException())
            }

        /**
         * 删除仓库（DELETE /repos/{owner}/{repo}，仅 owner/admin）。
         *
         * 204 无响应体，故以 [retrofit2.Response] 承接状态码：非 2xx 由调用方按
         * [GitHubRequestException] 归一化后分类（403 无权限 / 404 不存在）。
         */
        suspend fun deleteRepository(
            owner: String,
            repo: String,
        ): Result<Unit> =
            try {
                val response = repositoryApi.deleteRepository(owner, repo)
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(HttpException(response).asGitHubRequestException())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e.asGitHubRequestException())
            }
    }

/** 任意异常 → [GitHubRequestException]（已是该类型则原样透传，避免二次包装丢 cause）。 */
internal fun Throwable.asGitHubRequestException(): GitHubRequestException =
    if (this is GitHubRequestException) this else GitHubRequestException(asGitHubError(), this)

/** RepositoryDto → 领域模型（与 RepoRepository/RepoManagementRepository 同口径）。 */
private fun RepositoryDto.toDomain(): Repository =
    Repository(
        ownerLogin = owner.login,
        name = name,
        description = description,
        isPrivate = isPrivate,
        stargazerCount = stargazersCount,
        forkCount = forksCount,
        language = language,
        defaultBranch = defaultBranch,
    )
