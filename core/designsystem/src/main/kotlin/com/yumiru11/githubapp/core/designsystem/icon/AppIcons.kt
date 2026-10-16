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
@Suppress("LargeClass") // 目录是数据清单：每条一行四变体映射，无逻辑可拆（2026-10 新增 77 个语义图标）
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

    /** 动态（首页分区） */
    val Timeline: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Timeline,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Timeline,
            rounded = MaterialSymbolsRounded.Timeline,
            roundedFilled = MaterialSymbolsRoundedFilled.Timeline,
        )
    }

    /** 议题（首页分区） */
    val Inbox: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Inbox,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Inbox,
            rounded = MaterialSymbolsRounded.Inbox,
            roundedFilled = MaterialSymbolsRoundedFilled.Inbox,
        )
    }

    /** 拉取请求 / 趋势（首页分区） */
    val TrendingUp: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Trending_up,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Trending_up,
            rounded = MaterialSymbolsRounded.Trending_up,
            roundedFilled = MaterialSymbolsRoundedFilled.Trending_up,
        )
    }

    /** 发现（首页分区） */
    val LocalFireDepartment: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Local_fire_department,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Local_fire_department,
            rounded = MaterialSymbolsRounded.Local_fire_department,
            roundedFilled = MaterialSymbolsRoundedFilled.Local_fire_department,
        )
    }

    /** 讨论（首页分区） */
    val Forum: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Forum,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Forum,
            rounded = MaterialSymbolsRounded.Forum,
            roundedFilled = MaterialSymbolsRoundedFilled.Forum,
        )
    }

    /** 搜索历史 / 提交历史 */
    val History: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.History,
            outlinedFilled = MaterialSymbolsOutlinedFilled.History,
            rounded = MaterialSymbolsRounded.History,
            roundedFilled = MaterialSymbolsRoundedFilled.History,
        )
    }

    /** 评论数 */
    val ChatBubble: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Chat_bubble,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Chat_bubble,
            rounded = MaterialSymbolsRounded.Chat_bubble,
            roundedFilled = MaterialSymbolsRoundedFilled.Chat_bubble,
        )
    }

    /** 已关闭 */
    val CheckCircle: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Check_circle,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Check_circle,
            rounded = MaterialSymbolsRounded.Check_circle,
            roundedFilled = MaterialSymbolsRoundedFilled.Check_circle,
        )
    }

    /** 打开 */
    val RadioButtonUnchecked: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Radio_button_unchecked,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Radio_button_unchecked,
            rounded = MaterialSymbolsRounded.Radio_button_unchecked,
            roundedFilled = MaterialSymbolsRoundedFilled.Radio_button_unchecked,
        )
    }

    /** 草稿 */
    val Drafts: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Drafts,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Drafts,
            rounded = MaterialSymbolsRounded.Drafts,
            roundedFilled = MaterialSymbolsRoundedFilled.Drafts,
        )
    }

    /** 复制 */
    val ContentCopy: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Content_copy,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Content_copy,
            rounded = MaterialSymbolsRounded.Content_copy,
            roundedFilled = MaterialSymbolsRoundedFilled.Content_copy,
        )
    }

    /** 浏览器打开 */
    val OpenInNew: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Open_in_new,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Open_in_new,
            rounded = MaterialSymbolsRounded.Open_in_new,
            roundedFilled = MaterialSymbolsRoundedFilled.Open_in_new,
        )
    }

    /** 更多操作 */
    val MoreVert: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.More_vert,
            outlinedFilled = MaterialSymbolsOutlinedFilled.More_vert,
            rounded = MaterialSymbolsRounded.More_vert,
            roundedFilled = MaterialSymbolsRoundedFilled.More_vert,
        )
    }

    /** 全部已读 */
    val DoneAll: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Done_all,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Done_all,
            rounded = MaterialSymbolsRounded.Done_all,
            roundedFilled = MaterialSymbolsRoundedFilled.Done_all,
        )
    }

    /** 同步 / 更新分支 */
    val Sync: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Sync,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Sync,
            rounded = MaterialSymbolsRounded.Sync,
            roundedFilled = MaterialSymbolsRoundedFilled.Sync,
        )
    }

    /** 评审通过 */
    val Verified: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Verified,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Verified,
            rounded = MaterialSymbolsRounded.Verified,
            roundedFilled = MaterialSymbolsRoundedFilled.Verified,
        )
    }

    /** 错误 / 变更文件 */
    val Error: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Error,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Error,
            rounded = MaterialSymbolsRounded.Error,
            roundedFilled = MaterialSymbolsRoundedFilled.Error,
        )
    }

    /** 关注者 */
    val Group: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Group,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Group,
            rounded = MaterialSymbolsRounded.Group,
            roundedFilled = MaterialSymbolsRoundedFilled.Group,
        )
    }

    /** README / 文件 */
    val Description: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Description,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Description,
            rounded = MaterialSymbolsRounded.Description,
            roundedFilled = MaterialSymbolsRoundedFilled.Description,
        )
    }

    /** 文件夹 */
    val Folder: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Folder,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Folder,
            rounded = MaterialSymbolsRounded.Folder,
            roundedFilled = MaterialSymbolsRoundedFilled.Folder,
        )
    }

    /** 编辑 */
    val Edit: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Edit,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Edit,
            rounded = MaterialSymbolsRounded.Edit,
            roundedFilled = MaterialSymbolsRoundedFilled.Edit,
        )
    }

    /** 关闭 */
    val Close: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Close,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Close,
            rounded = MaterialSymbolsRounded.Close,
            roundedFilled = MaterialSymbolsRoundedFilled.Close,
        )
    }

    /** 发送 / 提交评论 */
    val Send: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Send,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Send,
            rounded = MaterialSymbolsRounded.Send,
            roundedFilled = MaterialSymbolsRoundedFilled.Send,
        )
    }

    /** 无结果 */
    val SearchOff: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Search_off,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Search_off,
            rounded = MaterialSymbolsRounded.Search_off,
            roundedFilled = MaterialSymbolsRoundedFilled.Search_off,
        )
    }

    /** 网络错误 */
    val CloudOff: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Cloud_off,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Cloud_off,
            rounded = MaterialSymbolsRounded.Cloud_off,
            roundedFilled = MaterialSymbolsRoundedFilled.Cloud_off,
        )
    }

    /** 需要登录 */
    val Lock: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Lock,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Lock,
            rounded = MaterialSymbolsRounded.Lock,
            roundedFilled = MaterialSymbolsRoundedFilled.Lock,
        )
    }

    /** 下拉刷新 */
    val ArrowDownward: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Arrow_downward,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Arrow_downward,
            rounded = MaterialSymbolsRounded.Arrow_downward,
            roundedFilled = MaterialSymbolsRoundedFilled.Arrow_downward,
        )
    }

    /** 议题 */
    val BugReport: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Bug_report,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Bug_report,
            rounded = MaterialSymbolsRounded.Bug_report,
            roundedFilled = MaterialSymbolsRoundedFilled.Bug_report,
        )
    }

    /** 星标（空心=未标 / 实心=已标） */
    val Star: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Star,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Star,
            rounded = MaterialSymbolsRounded.Star,
            roundedFilled = MaterialSymbolsRoundedFilled.Star,
        )
    }

    /** 新建 */
    val Add: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Add,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Add,
            rounded = MaterialSymbolsRounded.Add,
            roundedFilled = MaterialSymbolsRoundedFilled.Add,
        )
    }

    /** 时间 / 计划 */
    val Schedule: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Schedule,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Schedule,
            rounded = MaterialSymbolsRounded.Schedule,
            roundedFilled = MaterialSymbolsRoundedFilled.Schedule,
        )
    }

    /** Release */
    val PushPin: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Push_pin,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Push_pin,
            rounded = MaterialSymbolsRounded.Push_pin,
            roundedFilled = MaterialSymbolsRoundedFilled.Push_pin,
        )
    }

    /** 筛选 */
    val FilterList: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Filter_list,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Filter_list,
            rounded = MaterialSymbolsRounded.Filter_list,
            roundedFilled = MaterialSymbolsRoundedFilled.Filter_list,
        )
    }

    /** 排序 */
    val Sort: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Sort,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Sort,
            rounded = MaterialSymbolsRounded.Sort,
            roundedFilled = MaterialSymbolsRoundedFilled.Sort,
        )
    }

    /** 删除 */
    val Delete: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Delete,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Delete,
            rounded = MaterialSymbolsRounded.Delete,
            roundedFilled = MaterialSymbolsRoundedFilled.Delete,
        )
    }

    /** 可见性 */
    val Visibility: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Visibility,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Visibility,
            rounded = MaterialSymbolsRounded.Visibility,
            roundedFilled = MaterialSymbolsRoundedFilled.Visibility,
        )
    }

    /** 赞 */
    val ThumbUp: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Thumb_up,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Thumb_up,
            rounded = MaterialSymbolsRounded.Thumb_up,
            roundedFilled = MaterialSymbolsRoundedFilled.Thumb_up,
        )
    }

    /** 提交 */
    val Commit: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Commit,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Commit,
            rounded = MaterialSymbolsRounded.Commit,
            roundedFilled = MaterialSymbolsRoundedFilled.Commit,
        )
    }

    /** 合并 */
    val CallMerge: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Call_merge,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Call_merge,
            rounded = MaterialSymbolsRounded.Call_merge,
            roundedFilled = MaterialSymbolsRoundedFilled.Call_merge,
        )
    }

    /** 拉取请求 */
    val MergeType: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Merge_type,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Merge_type,
            rounded = MaterialSymbolsRounded.Merge_type,
            roundedFilled = MaterialSymbolsRoundedFilled.Merge_type,
        )
    }

    /** 已关闭 / 取消 */
    val Cancel: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Cancel,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Cancel,
            rounded = MaterialSymbolsRounded.Cancel,
            roundedFilled = MaterialSymbolsRoundedFilled.Cancel,
        )
    }

    /** 更新时间 */
    val Update: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Update,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Update,
            rounded = MaterialSymbolsRounded.Update,
            roundedFilled = MaterialSymbolsRoundedFilled.Update,
        )
    }

    /** 刷新 */
    val Refresh: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Refresh,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Refresh,
            rounded = MaterialSymbolsRounded.Refresh,
            roundedFilled = MaterialSymbolsRoundedFilled.Refresh,
        )
    }

    /** 返回 */
    val ArrowBack: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Arrow_back,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Arrow_back,
            rounded = MaterialSymbolsRounded.Arrow_back,
            roundedFilled = MaterialSymbolsRoundedFilled.Arrow_back,
        )
    }

    /** 展开 */
    val KeyboardArrowDown: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Keyboard_arrow_down,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Keyboard_arrow_down,
            rounded = MaterialSymbolsRounded.Keyboard_arrow_down,
            roundedFilled = MaterialSymbolsRoundedFilled.Keyboard_arrow_down,
        )
    }

    /** 日期 */
    val CalendarToday: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Calendar_today,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Calendar_today,
            rounded = MaterialSymbolsRounded.Calendar_today,
            roundedFilled = MaterialSymbolsRoundedFilled.Calendar_today,
        )
    }

    /** 标签 */
    val Label: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Label,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Label,
            rounded = MaterialSymbolsRounded.Label,
            roundedFilled = MaterialSymbolsRoundedFilled.Label,
        )
    }

    /** 链接 */
    val Link: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Link,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Link,
            rounded = MaterialSymbolsRounded.Link,
            roundedFilled = MaterialSymbolsRoundedFilled.Link,
        )
    }

    /** 代码 */
    val Terminal: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Terminal,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Terminal,
            rounded = MaterialSymbolsRounded.Terminal,
            roundedFilled = MaterialSymbolsRoundedFilled.Terminal,
        )
    }

    /** 文件 */
    val Article: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Article,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Article,
            rounded = MaterialSymbolsRounded.Article,
            roundedFilled = MaterialSymbolsRoundedFilled.Article,
        )
    }

    /** 收藏 */
    val Bookmark: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Bookmark,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Bookmark,
            rounded = MaterialSymbolsRounded.Bookmark,
            roundedFilled = MaterialSymbolsRoundedFilled.Bookmark,
        )
    }

    /** 发布 */
    val RocketLaunch: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Rocket_launch,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Rocket_launch,
            rounded = MaterialSymbolsRounded.Rocket_launch,
            roundedFilled = MaterialSymbolsRoundedFilled.Rocket_launch,
        )
    }

    /** 公开仓库 */
    val Public: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Public,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Public,
            rounded = MaterialSymbolsRounded.Public,
            roundedFilled = MaterialSymbolsRoundedFilled.Public,
        )
    }

    /** 构建 / 检查 */
    val Build: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Build,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Build,
            rounded = MaterialSymbolsRounded.Build,
            roundedFilled = MaterialSymbolsRoundedFilled.Build,
        )
    }

    /** 检查通过 */
    val TaskAlt: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Task_alt,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Task_alt,
            rounded = MaterialSymbolsRounded.Task_alt,
            roundedFilled = MaterialSymbolsRoundedFilled.Task_alt,
        )
    }

    /** 等待中 */
    val Pending: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Pending,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Pending,
            rounded = MaterialSymbolsRounded.Pending,
            roundedFilled = MaterialSymbolsRoundedFilled.Pending,
        )
    }

    /** 权限 */
    val Shield: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Shield,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Shield,
            rounded = MaterialSymbolsRounded.Shield,
            roundedFilled = MaterialSymbolsRoundedFilled.Shield,
        )
    }

    /** 阻塞 */
    val Block: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Block,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Block,
            rounded = MaterialSymbolsRounded.Block,
            roundedFilled = MaterialSymbolsRoundedFilled.Block,
        )
    }

    /** 标记 */
    val Flag: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Flag,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Flag,
            rounded = MaterialSymbolsRounded.Flag,
            roundedFilled = MaterialSymbolsRoundedFilled.Flag,
        )
    }

    /** 下载 */
    val Download: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Download,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Download,
            rounded = MaterialSymbolsRounded.Download,
            roundedFilled = MaterialSymbolsRoundedFilled.Download,
        )
    }

    /** 下载文件 */
    val FileDownload: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.File_download,
            outlinedFilled = MaterialSymbolsOutlinedFilled.File_download,
            rounded = MaterialSymbolsRounded.File_download,
            roundedFilled = MaterialSymbolsRoundedFilled.File_download,
        )
    }

    /** 新建文件夹 */
    val CreateNewFolder: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Create_new_folder,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Create_new_folder,
            rounded = MaterialSymbolsRounded.Create_new_folder,
            roundedFilled = MaterialSymbolsRoundedFilled.Create_new_folder,
        )
    }

    /** 打开文件夹 */
    val FolderOpen: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Folder_open,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Folder_open,
            rounded = MaterialSymbolsRounded.Folder_open,
            roundedFilled = MaterialSymbolsRoundedFilled.Folder_open,
        )
    }

    /** 新建文件 */
    val NoteAdd: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Note_add,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Note_add,
            rounded = MaterialSymbolsRounded.Note_add,
            roundedFilled = MaterialSymbolsRoundedFilled.Note_add,
        )
    }

    /** 新建条目 */
    val PostAdd: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Post_add,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Post_add,
            rounded = MaterialSymbolsRounded.Post_add,
            roundedFilled = MaterialSymbolsRoundedFilled.Post_add,
        )
    }

    /** 评审 */
    val RateReview: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Rate_review,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Rate_review,
            rounded = MaterialSymbolsRounded.Rate_review,
            roundedFilled = MaterialSymbolsRoundedFilled.Rate_review,
        )
    }

    /** 回复 */
    val Reply: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Reply,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Reply,
            rounded = MaterialSymbolsRounded.Reply,
            roundedFilled = MaterialSymbolsRoundedFilled.Reply,
        )
    }

    /** 会话 */
    val Chat: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Chat,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Chat,
            rounded = MaterialSymbolsRounded.Chat,
            roundedFilled = MaterialSymbolsRoundedFilled.Chat,
        )
    }

    /** 警告 */
    val Warning: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Warning,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Warning,
            rounded = MaterialSymbolsRounded.Warning,
            roundedFilled = MaterialSymbolsRoundedFilled.Warning,
        )
    }

    /** 添加成员 */
    val PersonAdd: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Person_add,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Person_add,
            rounded = MaterialSymbolsRounded.Person_add,
            roundedFilled = MaterialSymbolsRoundedFilled.Person_add,
        )
    }

    /** 前进 */
    val ArrowForward: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Arrow_forward,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Arrow_forward,
            rounded = MaterialSymbolsRounded.Arrow_forward,
            roundedFilled = MaterialSymbolsRoundedFilled.Arrow_forward,
        )
    }

    /** 右侧箭头 */
    val ChevronRight: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Chevron_right,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Chevron_right,
            rounded = MaterialSymbolsRounded.Chevron_right,
            roundedFilled = MaterialSymbolsRoundedFilled.Chevron_right,
        )
    }

    /** 左侧箭头 */
    val ChevronLeft: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Chevron_left,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Chevron_left,
            rounded = MaterialSymbolsRounded.Chevron_left,
            roundedFilled = MaterialSymbolsRoundedFilled.Chevron_left,
        )
    }

    /** 趋势持平 */
    val TrendingFlat: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Trending_flat,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Trending_flat,
            rounded = MaterialSymbolsRounded.Trending_flat,
            roundedFilled = MaterialSymbolsRoundedFilled.Trending_flat,
        )
    }

    /** 半星 */
    val StarHalf: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Star_half,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Star_half,
            rounded = MaterialSymbolsRounded.Star_half,
            roundedFilled = MaterialSymbolsRoundedFilled.Star_half,
        )
    }

    /** 新发布 */
    val NewReleases: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.New_releases,
            outlinedFilled = MaterialSymbolsOutlinedFilled.New_releases,
            rounded = MaterialSymbolsRounded.New_releases,
            roundedFilled = MaterialSymbolsRoundedFilled.New_releases,
        )
    }

    /** 存储 */
    val Storage: AppIconSpec by lazy {
        AppIconSpec(
            outlined = MaterialSymbolsOutlined.Storage,
            outlinedFilled = MaterialSymbolsOutlinedFilled.Storage,
            rounded = MaterialSymbolsRounded.Storage,
            roundedFilled = MaterialSymbolsRoundedFilled.Storage,
        )
    }
}
