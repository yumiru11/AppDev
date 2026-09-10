package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings

/**
 * 应用底部导航栏：首页 / 仓库 / 我的。
 *
 * 图标一律经 [AppIcon] 解析（issue #168 / UI12）：风格由 LocalIconStyle（设置页「图标风格」）
 * 驱动，选中态 filled / 未选空心（ui-design §5.1「选中实心 / 未选空心」必须做）。
 * 「仓库」Tab 用 Octicons repo（issue #168 / UI23）——Star 是收藏语义，不得用作文档仓库
 * 分区（ui-audit #12）；Octicon 无风格变体，其选中态由 M3 导航项的胶囊指示器 + 着色表达
 * （ADR-0006）。
 *
 * 无障碍：Tab 语义由 [NavigationBarItem] 的 label 承载（en/zh 成对），图标本身是装饰 →
 * contentDescription 传 null，避免与 label 重复播报。
 *
 * 玻璃装配（T6 Wave2，ADR-0004 §6.1 允许清单）：[GlassSurface] 包住
 * [NavigationBar]，`windowInsets = navigationBars`（玻璃延伸进手势导航条区域），
 * NavigationBar 自身 insets 归零、containerColor 透明。玻璃层颜色/降级逻辑
 * 同 AppTopBar（见其 KDoc）。
 */
@Composable
fun AppBottomBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    // 玻璃开关按"底栏"点位从 LocalGlassSettings 取（#167 / UI03）；显式传值只用于测试/预览
    blurEnabled: Boolean = LocalGlassSettings.current.enabledFor(GlassScope.BOTTOM_BAR),
    modifier: Modifier = Modifier,
) {
    data class TabItem(
        val route: String,
        val labelRes: Int,
        val icon: AppIconSpec,
    )

    val tabs =
        listOf(
            TabItem(MainTab.HOME, R.string.nav_home, AppIcons.Home),
            // UI23：仓库 = GitHub 专属语义 → Octicons repo（不再用 Star 充当「仓库」）
            TabItem(MainTab.REPOS, R.string.nav_repos, AppIcons.Repo),
            TabItem(MainTab.PROFILE, R.string.nav_profile, AppIcons.Person),
        )

    GlassSurface(
        modifier = modifier,
        windowInsets = WindowInsets.navigationBars,
        scope = GlassScope.BOTTOM_BAR,
        blurEnabled = blurEnabled,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets(0.dp),
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab.route
                NavigationBarItem(
                    icon = {
                        AppIcon(
                            spec = tab.icon,
                            contentDescription = null,
                            selected = isSelected,
                        )
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    selected = isSelected,
                    onClick = { onTabSelected(tab.route) },
                    // 不传 colors：M3 默认即 onSecondaryContainer/onSecondaryContainer/
                    // secondaryContainer（NavigationBarTokens ItemActive* 三 token），
                    // 此前显式覆盖值恰等于默认（#86 清理冗余）。
                )
            }
        }
    }
}

// ── @Preview（#86）：底栏 Light/Dark 双主题预览 ──

@Preview(name = "Light", showBackground = true)
@Composable
private fun AppBottomBarPreviewLight() {
    AppTheme(darkTheme = false) {
        AppBottomBar(selectedTab = MainTab.HOME, onTabSelected = {})
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun AppBottomBarPreviewDark() {
    AppTheme(darkTheme = true) {
        AppBottomBar(selectedTab = MainTab.REPOS, onTabSelected = {})
    }
}
