package com.yumiru11.githubapp.core.navigation

/**
 * 编辑器初始内容传递（T21 文件查看器 → 编辑器入口）。
 *
 * 大文档内容不适合经 Navigation route 参数传递（URL 编码膨胀/路径段冲突），
 * 故用进程内单例暂存：入口（feature:repo FileViewerScreen）写入 → 导航到
 * [AppRoute.EDITOR] → AppNavHost 读取后传给编辑器屏幕。
 *
 * 单例生命周期 = 进程；编辑器屏幕消费后不主动清空（重复进入编辑器时
 * 以最近一次写入为准，语义正确）。
 */
object EditorContentHolder {
    /** 待编辑器消费的初始内容。 */
    var initialContent: String = ""
}
