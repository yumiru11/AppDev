package com.yumiru11.githubapp.feature.search.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.feature.search.CodeRow
import com.yumiru11.githubapp.feature.search.IssueRow
import com.yumiru11.githubapp.feature.search.RepositoryRow
import com.yumiru11.githubapp.feature.search.UserRow

/**
 * 搜索结果四类行组件的 Light/Dark 双主题 @Preview（#86）。
 *
 * 独立 preview 子包：JaCoCo 与 diff 覆盖率门禁按 `preview/` 路径排除 UI，
 * 行组件为 internal（同模块可见）。样例数据离线自足（avatar 置空避免 Coil 取网）。
 */
@Preview(name = "Light", showBackground = true)
@Composable
private fun RepositoryRowPreviewLight() {
    AppTheme(darkTheme = false) {
        RepositoryRow(
            repository =
                Repository(
                    ownerLogin = "yumiru11",
                    name = "AppDev",
                    description = "功能全面的 Android GitHub 客户端",
                    stargazerCount = 128,
                    forkCount = 12,
                    language = "Kotlin",
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun RepositoryRowPreviewDark() {
    AppTheme(darkTheme = true) {
        RepositoryRow(
            repository =
                Repository(
                    ownerLogin = "yumiru11",
                    name = "AppDev",
                    description = "功能全面的 Android GitHub 客户端",
                    stargazerCount = 128,
                    forkCount = 12,
                    language = "Kotlin",
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun UserRowPreviewLight() {
    AppTheme(darkTheme = false) {
        UserRow(user = User(login = "octocat", name = "The Octocat"), onClick = {})
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun UserRowPreviewDark() {
    AppTheme(darkTheme = true) {
        UserRow(user = User(login = "octocat", name = "The Octocat"), onClick = {})
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun SearchIssueRowPreviewLight() {
    AppTheme(darkTheme = false) {
        IssueRow(
            issue =
                SearchIssue(
                    id = 86L,
                    number = 86,
                    title = "perf(list): Paging itemKey 迁移与模型稳定性标注",
                    state = "open",
                    isPullRequest = false,
                    authorLogin = "yumiru11",
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun SearchIssueRowPreviewDark() {
    AppTheme(darkTheme = true) {
        IssueRow(
            issue =
                SearchIssue(
                    id = 73L,
                    number = 73,
                    title = "feat(markdown): WebView 主渲染切换收尾",
                    state = "closed",
                    isPullRequest = true,
                    authorLogin = "octocat",
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun CodeRowPreviewLight() {
    AppTheme(darkTheme = false) {
        CodeRow(
            item =
                SearchCodeItem(
                    name = "HomeScreen.kt",
                    path = "feature/home/src/main/kotlin/HomeScreen.kt",
                    repoFullName = "yumiru11/AppDev",
                    htmlUrl = null,
                ),
            onClick = {},
        )
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun CodeRowPreviewDark() {
    AppTheme(darkTheme = true) {
        CodeRow(
            item =
                SearchCodeItem(
                    name = "HomeScreen.kt",
                    path = "feature/home/src/main/kotlin/HomeScreen.kt",
                    repoFullName = "yumiru11/AppDev",
                    htmlUrl = null,
                ),
            onClick = {},
        )
    }
}
