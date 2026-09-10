package com.yumiru11.githubapp.core.designsystem.icon

/**
 * 常用图标目录（issue #168 / UI12）。
 *
 * 每个条目是一个 [AppIconSpec]（四档变体），消费方只引 [AppIcons]，**不直接引
 * com.composables**——图标依赖因此收敛在 core:designsystem 一处，feature 模块
 * 无需自带 icons-material-symbols-*（否则每个消费模块都要重复那套同名扩展 import 坑）。
 *
 * 新增图标：在四个 `MaterialSymbols*` 家族对象里各加一行，再在本目录补一个 `AppIconSpec`。
 * GitHub 专属语义（仓库/Issue/PR/合并…）仍走 [AppDevOcticons]（ui-design §1.2 红线：
 * GitHub 独有功能必须用 Octicons）。
 */
object AppIcons {
    /** 首页（底栏 Tab） */
    val Home: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Home,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Home,
            rounded = MaterialSymbolsRounded.Home,
            roundedFilled = MaterialSymbolsRoundedFilled.Home,
        )
    }

    /** 仓库（底栏 Tab）——GitHub 专属语义，Octicons repo（ui-design §5.3） */
    val Repo: AppIconSpec by lazy { AppIconSpec.single(AppDevOcticons.Repo) }

    /** 我的（底栏 Tab / 顶栏头像） */
    val Person: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Person,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Person,
            rounded = MaterialSymbolsRounded.Person,
            roundedFilled = MaterialSymbolsRoundedFilled.Person,
        )
    }

    /** 搜索（顶栏胶囊搜索框） */
    val Search: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Search,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Search,
            rounded = MaterialSymbolsRounded.Search,
            roundedFilled = MaterialSymbolsRoundedFilled.Search,
        )
    }

    /** 通知（顶栏铃铛） */
    val Notifications: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Notifications,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Notifications,
            rounded = MaterialSymbolsRounded.Notifications,
            roundedFilled = MaterialSymbolsRoundedFilled.Notifications,
        )
    }

    /** 信息（页面级占位/空态） */
    val Info: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Info,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Info,
            rounded = MaterialSymbolsRounded.Info,
            roundedFilled = MaterialSymbolsRoundedFilled.Info,
        )
    }

    /** 设置（设置页「图标风格」预览卡） */
    val Settings: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Settings,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Settings,
            rounded = MaterialSymbolsRounded.Settings,
            roundedFilled = MaterialSymbolsRoundedFilled.Settings,
        )
    }
}
