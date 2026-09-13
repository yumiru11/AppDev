package com.yumiru11.githubapp.core.markdown.webview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `MermaidRenderLog.line` 的**格式契约**测试。
 *
 * 为什么单独钉格式：CI（`.github/scripts/mermaid-render-verify.sh` + `mermaid-render-verify.yml`）
 * 用 grep 断言 logcat 行 `MermaidRender: engine=supported|blocked rendered=N failed=M`。
 * 格式一变，真机渲染证据就断了——但那种断裂在 JVM 单测里看不见，除非把格式本身当契约钉死。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MermaidRenderLogTest {
    @Test
    fun line_engineSupported_emitsStableGrepAnchor() {
        assertEquals(
            "engine=supported 行必须与 CI grep 锚点逐字符一致",
            "MermaidRender: engine=supported rendered=3 failed=1",
            MermaidRenderLog.line(3, 1, true),
        )
    }

    @Test
    fun line_engineBlocked_emitsBlockedFallbackAnchor() {
        assertEquals(
            "门禁拦下时必须报告 engine=blocked（API 30 负向腿的断言行）",
            "MermaidRender: engine=blocked rendered=0 failed=0",
            MermaidRenderLog.line(0, 0, false),
        )
    }

    @Test
    fun prefix_matchesCiGrepAnchor() {
        assertEquals("MermaidRender", MermaidRenderLog.PREFIX)
    }
}
