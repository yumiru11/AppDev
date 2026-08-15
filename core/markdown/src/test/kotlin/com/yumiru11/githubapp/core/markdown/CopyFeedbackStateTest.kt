@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // advanceTimeBy/runCurrent 属测试调度器 API

package com.yumiru11.githubapp.core.markdown

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyFeedbackStateTest {
    @Test
    fun markCopied_copiesImmediately_thenResetsAfterDuration() =
        runTest {
            val state = CopyFeedbackState(this)

            state.markCopied()

            assertTrue(state.copied)
            testScheduler.advanceTimeBy(CopyFeedbackState.FEEDBACK_DURATION_MILLIS)
            testScheduler.runCurrent()
            assertFalse(state.copied)
        }

    @Test
    fun markCopied_whileCopied_doesNotStackResetJobs() =
        runTest {
            val state = CopyFeedbackState(this)

            state.markCopied()
            state.markCopied()

            assertTrue(state.copied)
            testScheduler.advanceTimeBy(CopyFeedbackState.FEEDBACK_DURATION_MILLIS)
            testScheduler.runCurrent()
            assertFalse(state.copied)
        }
}
