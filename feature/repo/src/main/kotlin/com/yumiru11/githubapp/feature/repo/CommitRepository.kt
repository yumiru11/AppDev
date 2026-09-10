@file:Suppress("TooGenericExceptionCaught", "SwallowedException")
// 网络/IO 错误统一兜底为 Result.failure（UI 层按 RepoErrorType 分类），异常即结果。

package com.yumiru11.githubapp.feature.repo

import com.yumiru11.githubapp.core.githubrest.api.CommitApi
import com.yumiru11.githubapp.core.githubrest.model.CommitDetailDto
import com.yumiru11.githubapp.core.githubrest.model.CommitFileDto
import javax.inject.Inject
import javax.inject.Singleton

/** 单页文件数（GitHub 上限 100）。 */
private const val COMMIT_FILES_PAGE_SIZE = 100

/** 单提交最多 300 个文件（GitHub 服务端上限）：最多翻 3 页。 */
private const val COMMIT_FILES_MAX_PAGES = 3

/**
 * 提交详情数据仓库（L09）。
 *
 * GET /repos/{owner}/{repo}/commits/{ref} 对 `files` 数组分页（每页上限 100），
 * 本仓库按页累积到 [CommitDetail.files]（最多 3 页，与 GitHub 单提交 300 文件上限一致）。
 * 任一页失败 → 整体 [Result.failure]（不返回半截文件列表，避免 +N/−M 与列表不一致）。
 */
@Singleton
class CommitRepository
    @Inject
    constructor(
        private val commitApi: CommitApi,
    ) {
        /**
         * 拉取单提交详情（含文件变更与 patch）。
         *
         * @param ref commit SHA / 分支名 / Tag 名
         */
        suspend fun getCommit(
            owner: String,
            repo: String,
            ref: String,
        ): Result<CommitDetail> =
            try {
                Result.success(loadPaged(owner, repo, ref))
            } catch (e: Exception) {
                Result.failure(e)
            }

        private suspend fun loadPaged(
            owner: String,
            repo: String,
            ref: String,
        ): CommitDetail {
            val files = ArrayList<CommitFile>()
            var head: CommitDetailDto? = null
            var page = 1
            var hasMore = true
            while (hasMore && page <= COMMIT_FILES_MAX_PAGES) {
                val dto = commitApi.getCommit(owner, repo, ref, COMMIT_FILES_PAGE_SIZE, page)
                head = head ?: dto
                files += dto.files.map { it.toDomain() }
                hasMore = dto.files.size == COMMIT_FILES_PAGE_SIZE
                page++
            }
            val first = checkNotNull(head) { "commit response is null" }
            return CommitDetail(
                sha = first.sha,
                message = first.commit.message.orEmpty(),
                authorName = first.commit.author?.name,
                authorEmail = first.commit.author?.email,
                authorDate = first.commit.author?.date,
                authorLogin = first.author?.login,
                authorAvatarUrl = first.author?.avatarUrl,
                additions = first.stats?.additions ?: 0,
                deletions = first.stats?.deletions ?: 0,
                files = files,
            )
        }
    }

/**
 * 提交详情（L09 UI 状态源）。
 *
 * @param message 提交信息全文（标题 + 正文，UI 按首行/余下分段渲染）
 * @param authorLogin GitHub 账号 login（与 git 身份 [authorName] 不同；未关联账号时为 null）
 * @param authorDate ISO-8601 时间串（UI 走 core:ui 的 relativeTimeText）
 * @param files 全部变更文件（已跨页累积）
 */
data class CommitDetail(
    val sha: String,
    val message: String,
    val authorName: String? = null,
    val authorEmail: String? = null,
    val authorDate: String? = null,
    val authorLogin: String? = null,
    val authorAvatarUrl: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: List<CommitFile> = emptyList(),
) {
    /** 短 SHA（7 位，GitHub 网页同款）。 */
    val shortSha: String
        get() = sha.take(SHORT_SHA_LENGTH)

    private companion object {
        const val SHORT_SHA_LENGTH = 7
    }
}

/**
 * 变更文件条目。
 *
 * @param patch unified diff 文本（二进制/过大文件为 null → 详情页给提示而非空 diff）
 */
data class CommitFile(
    val filename: String,
    val status: CommitFileStatus = CommitFileStatus.UNKNOWN,
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null,
)

/** 文件变更类型（GitHub status 字段）。 */
enum class CommitFileStatus {
    ADDED,
    REMOVED,
    MODIFIED,
    RENAMED,
    UNKNOWN,
    ;

    companion object {
        /** GitHub status 字符串 → 类型（未知值归入 [UNKNOWN]，不崩溃）。 */
        fun fromRaw(raw: String?): CommitFileStatus =
            when (raw?.lowercase()) {
                "added" -> ADDED
                "removed" -> REMOVED
                "modified" -> MODIFIED
                "renamed" -> RENAMED
                else -> UNKNOWN
            }
    }
}

/** CommitFileDto → 领域模型。 */
private fun CommitFileDto.toDomain(): CommitFile =
    CommitFile(
        filename = filename,
        status = CommitFileStatus.fromRaw(status),
        additions = additions,
        deletions = deletions,
        patch = patch,
    )
