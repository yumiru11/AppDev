package com.yumiru11.githubapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus

/**
 * GitHub 状态的 TalkBack 播报文案（issue #168 / UI26，ui-design §8）。
 *
 * 与 [com.yumiru11.githubapp.core.designsystem.component.AppStateChip] 的可见标签互补：
 * 标签是短词（Open / Closed / Merged / Draft），这里给**完整短语**（"该项已开启"），
 * 两者叠加不会逐字重复；文案全部走资源（en + zh-rCN 成对）。
 *
 * 放在 core:ui 而非各 feature：Issue 详情与 PR 详情都要用同一套措辞，
 * 免得两处各写一遍、日后改词漏一处（feature 之间不得互相依赖）。
 */
@Composable
fun gitHubStatusStateDescription(status: GitHubStatus): String =
    stringResource(
        when (status) {
            GitHubStatus.OPEN -> R.string.state_description_open
            GitHubStatus.CLOSED -> R.string.state_description_closed
            GitHubStatus.MERGED -> R.string.state_description_merged
            GitHubStatus.DRAFT -> R.string.state_description_draft
            GitHubStatus.MERGEABLE -> R.string.state_description_mergeable
            GitHubStatus.CONFLICTING -> R.string.state_description_conflicting
        },
    )
