package com.yumiru11.githubapp.feature.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RepoErrorType] 错误域与重试语义单测（#201 P0）。
 *
 * **缺陷实证**：CI release `screenshots-pr199-34600531043` 的 `editor.png` 上，深链
 * `blob/main/README.md` 撞 `/contents/README.md → 404` 后，界面显示
 * 「Repository not found + Retry」—— 而同一会话 `GET /repos/yumiru11/AppDev` 明明 200。
 * 两处错：① 错误域混用（路径 404 说成仓库 404）② 确定性失败挂了个必然复现的 Retry。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
class RepoErrorTypeTest {
    @Test
    fun isRetryable_repositoryNotFound_false() {
        assertFalse(RepoErrorType.NOT_FOUND.isRetryable)
    }

    @Test
    fun isRetryable_pathNotFound_false() {
        // 文件已删除/改名：重试同一个 path 必然再次 404
        assertFalse(RepoErrorType.PATH_NOT_FOUND.isRetryable)
    }

    @Test
    fun isRetryable_network_true() {
        assertTrue(RepoErrorType.NETWORK.isRetryable)
    }

    @Test
    fun isRetryable_unknown_true() {
        // UNKNOWN 兜住 5xx/超时等瞬时失败，重试有意义
        assertTrue(RepoErrorType.UNKNOWN.isRetryable)
    }

    @Test
    fun isRetryable_forbidden_true() {
        // 403 也可能是 GitHub 限流；文案本身已引导重新登录，保留重试入口不误导
        assertTrue(RepoErrorType.FORBIDDEN.isRetryable)
    }

    @Test
    fun notFoundDomains_repositoryAndPath_areDistinct() {
        // 两个 404 域必须可区分：文案与状态都不同（仓库级 vs 路径级）
        assertTrue(RepoErrorType.NOT_FOUND != RepoErrorType.PATH_NOT_FOUND)
    }
}
