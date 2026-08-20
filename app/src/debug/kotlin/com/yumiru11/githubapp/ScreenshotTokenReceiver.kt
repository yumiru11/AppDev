/*
 * DEBUG ONLY — 截图登录入口（CI screenshot-login 管线，无 UI）。
 *
 * 调用方式（ci.yml screenshots job 注入 SCREENSHOT_TOKEN 机密后由 screenshots.sh 执行）：
 *   adb shell am start -n com.yumiru11.githubapp/com.yumiru11.githubapp.ScreenshotTokenReceiver \
 *     -e pat <SCREENSHOT_TOKEN>
 * 接收只读 PAT，加密存入 [TokenStorage]（EncryptedSharedPreferences，ADR-0002），
 * 使模拟器内 app 进入开发者模式（fine-grained PAT → REST-only 降级），Star 按钮 /
 * 评论框 / PR 操作等鉴权 UI 在后续截图可见。存完即 finish，不绘制任何界面。
 *
 * 仅 debug 变体存在（app/src/debug AndroidManifest 才声明）；release 不受影响。
 */
package com.yumiru11.githubapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.yumiru11.githubapp.core.githubauth.token.SessionData
import com.yumiru11.githubapp.core.githubauth.token.TokenStorage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScreenshotTokenReceiver : ComponentActivity() {
    @Inject lateinit var tokenStorage: TokenStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pat = intent?.getStringExtra("pat")
        // 只读 fine-grained PAT：开发者模式，REST-only 降级（ADR-0002 §token 安全红线）
        if (!pat.isNullOrBlank()) {
            tokenStorage.saveSession(SessionData(pat = pat, isRestOnly = true))
        }
        // 无界面接收器：存完即退出（不会绘制，Theme.NoDisplay 防闪）
        finish()
    }
}
