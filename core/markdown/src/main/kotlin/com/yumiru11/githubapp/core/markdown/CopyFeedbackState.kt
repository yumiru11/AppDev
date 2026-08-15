package com.yumiru11.githubapp.core.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 复制按钮反馈状态：`copied=true` 维持 [FEEDBACK_DURATION_MILLIS] 后自动复位。 */
@Stable
class CopyFeedbackState(
    private val scope: CoroutineScope,
) {
    var copied by mutableStateOf(false)
        private set

    fun markCopied() {
        if (copied) return
        copied = true
        scope.launch {
            delay(FEEDBACK_DURATION_MILLIS)
            copied = false
        }
    }

    companion object {
        const val FEEDBACK_DURATION_MILLIS = 1_500L
    }
}

@Composable
fun rememberCopyFeedbackState(): CopyFeedbackState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CopyFeedbackState(scope) }
}
