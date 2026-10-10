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

    /** 收起（下拉容器展开态：箭头朝上） */
    val ExpandLess: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Expand_less,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Expand_less,
            rounded = MaterialSymbolsRounded.Expand_less,
            roundedFilled = MaterialSymbolsRoundedFilled.Expand_less,
        )
    }

    /** 展开（下拉容器收起态：箭头朝下） */
    val ExpandMore: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Expand_more,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Expand_more,
            rounded = MaterialSymbolsRounded.Expand_more,
            roundedFilled = MaterialSymbolsRoundedFilled.Expand_more,
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

    /** 语言（跟随系统 / English / 中文） */
    val Language: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Language,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Language,
            rounded = MaterialSymbolsRounded.Language,
            roundedFilled = MaterialSymbolsRoundedFilled.Language,
        )
    }

    /** 偏好设置（二级入口：主题/界面/通用） */
    val Tune: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Tune,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Tune,
            rounded = MaterialSymbolsRounded.Tune,
            roundedFilled = MaterialSymbolsRoundedFilled.Tune,
        )
    }

    /** 账号与开发者（登录方式 / PAT / 配额） */
    val AccountCircle: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Account_circle,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Account_circle,
            rounded = MaterialSymbolsRounded.Account_circle,
            roundedFilled = MaterialSymbolsRoundedFilled.Account_circle,
        )
    }

    /** 外观（配色与主题） */
    val Palette: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Palette,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Palette,
            rounded = MaterialSymbolsRounded.Palette,
            roundedFilled = MaterialSymbolsRoundedFilled.Palette,
        )
    }

    /** 界面与动效（圆角 / 动画 / 毛玻璃） */
    val MotionPhotosOn: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Motion_photos_on,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Motion_photos_on,
            rounded = MaterialSymbolsRounded.Motion_photos_on,
            roundedFilled = MaterialSymbolsRoundedFilled.Motion_photos_on,
        )
    }

    /** 编辑器（代码字体与换行） */
    val Code: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Code,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Code,
            rounded = MaterialSymbolsRounded.Code,
            roundedFilled = MaterialSymbolsRoundedFilled.Code,
        )
    }

    /** 高对比（无障碍配色） */
    val Contrast: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Contrast,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Contrast,
            rounded = MaterialSymbolsRounded.Contrast,
            roundedFilled = MaterialSymbolsRoundedFilled.Contrast,
        )
    }

    /** 纯黑（OLED） */
    val Brightness2: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Brightness_2,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Brightness_2,
            rounded = MaterialSymbolsRounded.Brightness_2,
            roundedFilled = MaterialSymbolsRoundedFilled.Brightness_2,
        )
    }

    /** 主色调（seed 色盘） */
    val FormatColorFill: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Format_color_fill,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Format_color_fill,
            rounded = MaterialSymbolsRounded.Format_color_fill,
            roundedFilled = MaterialSymbolsRoundedFilled.Format_color_fill,
        )
    }

    /** 图标风格（描边 / 圆润 / 实心） */
    val Shapes: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Shapes,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Shapes,
            rounded = MaterialSymbolsRounded.Shapes,
            roundedFilled = MaterialSymbolsRoundedFilled.Shapes,
        )
    }

    /** 毛玻璃效果 */
    val BlurOn: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Blur_on,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Blur_on,
            rounded = MaterialSymbolsRounded.Blur_on,
            roundedFilled = MaterialSymbolsRoundedFilled.Blur_on,
        )
    }

    /** 背景图 */
    val Image: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Image,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Image,
            rounded = MaterialSymbolsRounded.Image,
            roundedFilled = MaterialSymbolsRoundedFilled.Image,
        )
    }

    /** 圆角强度 */
    val RoundedCorner: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Rounded_corner,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Rounded_corner,
            rounded = MaterialSymbolsRounded.Rounded_corner,
            roundedFilled = MaterialSymbolsRoundedFilled.Rounded_corner,
        )
    }

    /** 代码行号 */
    val FormatListNumbered: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Format_list_numbered,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Format_list_numbered,
            rounded = MaterialSymbolsRounded.Format_list_numbered,
            roundedFilled = MaterialSymbolsRoundedFilled.Format_list_numbered,
        )
    }

    /** 个人访问令牌（PAT） */
    val VpnKey: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Vpn_key,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Vpn_key,
            rounded = MaterialSymbolsRounded.Vpn_key,
            roundedFilled = MaterialSymbolsRoundedFilled.Vpn_key,
        )
    }

    /** 剩余配额 */
    val DataUsage: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Data_usage,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Data_usage,
            rounded = MaterialSymbolsRounded.Data_usage,
            roundedFilled = MaterialSymbolsRoundedFilled.Data_usage,
        )
    }

    /** 列表入场动画 */
    val PlaylistPlay: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Playlist_play,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Playlist_play,
            rounded = MaterialSymbolsRounded.Playlist_play,
            roundedFilled = MaterialSymbolsRoundedFilled.Playlist_play,
        )
    }

    /** 底部弹层（毛玻璃点位） */
    val Layers: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Layers,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Layers,
            rounded = MaterialSymbolsRounded.Layers,
            roundedFilled = MaterialSymbolsRoundedFilled.Layers,
        )
    }
}
