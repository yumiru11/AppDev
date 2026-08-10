package com.yumiru11.githubapp.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * 单测 Main Dispatcher 替换 Rule（kotlinx-coroutines-test）。
 *
 * 用法：
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * 默认 [UnconfinedTestDispatcher]（立即执行，适合多数 ViewModel/Flow 测试）；
 * 需要显式推进虚拟时间时改传 `StandardTestDispatcher()`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
