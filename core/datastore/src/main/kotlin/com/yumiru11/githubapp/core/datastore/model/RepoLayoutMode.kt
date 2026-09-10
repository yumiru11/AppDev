package com.yumiru11.githubapp.core.datastore.model

/**
 * 仓库列表布局模式（#166 / UI01，ui-design §3.2 用户拍板 B2-2）。
 *
 * 「网格 / 通栏两种布局，用户可切换（列表页右上角视图切换按钮）」——切换后持久化，
 * 下次进入保持用户上次选择。持久化为 name 字符串，未知值回退 [LIST]。
 */
enum class RepoLayoutMode {
    /** 通栏列表：信息密度高，描述可读两行（默认；与「我的」页仓库列表观感一致） */
    LIST,

    /** 网格：两列自适应，扫视快，适合以「有哪些仓库」为主的浏览 */
    GRID,
    ;

    /** 切换目标（右上角按钮单击即翻转，无需枚举分支） */
    fun toggled(): RepoLayoutMode = if (this == LIST) GRID else LIST
}
