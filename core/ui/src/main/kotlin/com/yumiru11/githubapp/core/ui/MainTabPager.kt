package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yumiru11.githubapp.core.designsystem.component.LocalHazeState
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * 底部 Tab 三分区容器（2026-08-14 真机走查分区重构）。
 *
 * 底部「首页 / 仓库 / 我的」不是独立导航页（原 NavHost 路由整页切换产生「弹窗感」），
 * 而是同一容器内横向分页：点 tab 或左右滑动切换分区，无导航动画、无页面栈。
 *
 * - 顶栏由各分区页自持（首页=搜索+铃铛、我的=标题+设置，保留页差异）
 * - 底栏 [AppBottomBar] 固定在容器底部，tab 与 pager 双向联动
 * - 分区内容由宿主注入（feature 模块无相互依赖）
 *
 * backdrop blur 接线（issue #83）：本组件持有底栏玻璃的 [LocalHazeState]，并对
 * HorizontalPager 内容侧挂 `hazeSource`——底栏 [AppBottomBar] 的 GlassSurface 经
 * hazeEffect 模糊本内容。provider 覆盖 Scaffold 全部插槽（bottomBar + content）；
 * 分区页若自持顶栏（如 HomeScreen）应自建一份 state 覆盖本值，避免顶栏 effect
 * 嵌套进底栏 source 子树。
 *
 * ## 底部避让契约（P0 系统栏 insets 修复）
 *
 * 本 Scaffold 的 `contentWindowInsets` 是 `WindowInsets(0.dp)`（**有意设计**：分区页各自
 * 处理 insets，避免 2026-08-14 修过的「顶栏距状态栏空一段」），且 Scaffold **不会**给
 * content 自动加 padding——所以「内容不被底栏 / 系统导航栏遮住」完全依赖宿主把底部预留
 * 高度经 [PaddingValues] 下发给分区页：
 *
 * - 下发值 = `paddingValues.calculateBottomPadding()` = 底栏**实测总高**。底栏
 *   [AppBottomBar] 的玻璃容器吃 `WindowInsets.navigationBars`（见其 KDoc），因此该值
 *   天然含系统导航栏 inset：手势导航 ≈ 24dp、三键导航 ≈ 48dp
 *   （实测见 `MainTabPagerInsetsTest`）。
 * - 分区页必须把它落到**滚动容器的 `contentPadding`**（Home / Profile / Repos 三页同一契约），
 *   既保证末项能完整滚出底栏上沿，又保留内容物理穿过玻璃矩形的 backdrop blur 几何。
 *   **禁止**改成给内容层加 `navigationBarsPadding()` / `Modifier.padding(...)` 把内容整体
 *   顶上去——玻璃背后会变空，FEEDBACK #17「毛玻璃看不出效果」复发。
 * - ⚠️ 宿主 lambda **必须**接住这个形参并转成页面自己的 `bottomContentPadding`。
 *   丢形参（`reposPage = { ReposScreen(...) }`）不会编译失败、也不会被截图基线抓到，
 *   只会静默让整个分区少预留一个底栏高度（P0 根因，见 `MainActivity` 内注释）。
 *   新增分区页时同样必须接；确实不需要避让的页面要写明理由，不留静默空实现。
 *
 * @param selectedTab 当前选中分区键（MainTab.HOME / MainTab.REPOS / MainTab.PROFILE）
 * @param onTabSelected tab 点击回调（宿主无需处理，本组件内部已联动 pager；保留参数供外部感知）
 */
@Composable
fun MainTabPager(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    homePage: @Composable (PaddingValues) -> Unit = {},
    reposPage: @Composable (PaddingValues) -> Unit = {},
    profilePage: @Composable (PaddingValues) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tabIndex = tabIndexFor(selectedTab)
    val pagerState =
        rememberPagerState(initialPage = tabIndex) { TAB_COUNT }
    val scope = rememberCoroutineScope()

    // backdrop blur（issue #83）：底栏 hazeEffect 与分区内容侧 hazeSource 共享本 state
    val hazeState = rememberHazeState()
    // source 侧门禁与 GlassSurface 的 effect 侧同源判定（issue #83，防两侧漂移）；
    // 开关按"底栏"点位取（#167 / UI03），不再由宿主层层下传布尔
    val blurEnabled = LocalGlassSettings.current.enabledFor(GlassScope.BOTTOM_BAR)
    val useHazeSource = GlassRenderPolicy.shouldAttachHazeSource(blurEnabled)

    // 外部 tab 状态变化（如顶部头像切到我的）→ 联动 pager 滚动
    LaunchedEffect(tabIndex) {
        if (pagerState.currentPage != tabIndex) {
            pagerState.animateScrollToPage(tabIndex)
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            modifier = modifier,
            // 分区页各自处理 insets（Home/Profile 有 TopAppBar 自带 statusBars；
            // 容器不再叠加顶部 padding，否则出现「顶栏距状态栏空一段」——2026-08-14 真机走查修复）
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                // zIndex 保证底栏在 pager 内容之后绘制——Haze backdrop blur 要求
                // source 先于 effect 绘制，否则底栏每帧拿到空背景（真机无效果根因）
                Box(modifier = Modifier.zIndex(1f)) {
                    AppBottomBar(
                        selectedTab = selectedTab,
                        onTabSelected = { route ->
                            onTabSelected(route)
                            scope.launch {
                                pagerState.animateScrollToPage(tabIndexFor(route))
                            }
                        },
                        blurEnabled = blurEnabled,
                    )
                }
            },
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (useHazeSource) {
                                // 底栏玻璃的模糊源：三分区滚动内容（含各页自持顶栏背后的内容）
                                Modifier.hazeSource(hazeState)
                            } else {
                                Modifier
                            },
                        ),
            ) { page ->
                val pagePadding =
                    PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding(),
                    )
                when (page) {
                    0 -> homePage(pagePadding)
                    1 -> reposPage(pagePadding)
                    2 -> profilePage(pagePadding)
                }
            }
        }
    }
}

private fun tabIndexFor(route: String): Int =
    when (route) {
        MainTab.REPOS -> 1
        MainTab.PROFILE -> 2
        else -> 0
    }

private const val TAB_COUNT = 3
