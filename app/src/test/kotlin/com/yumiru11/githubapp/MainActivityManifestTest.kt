package com.yumiru11.githubapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.yumiru11.githubapp.core.githubauth.auth.OAuthConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Manifest 回调 filter 断言（T4 Wave2，ADR-0001）。
 *
 * Robolectric 读合并后 manifest：oauth-callback URI（自定义 scheme
 * `com.yumiru11.githubapp://oauth-callback`）应解析到 MainActivity——
 * MainActivity 的 filter 带 host 约束，比 AppAuth 库的 scheme-only
 * RedirectUriReceiverActivity 更具体，回调直达 MainActivity。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityManifestTest {
    @Test
    fun mainActivity_intentFilter_resolvesOauthCallbackUri() {
        val context = RuntimeEnvironment.getApplication() as Context
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(OAuthConfig.REDIRECT_URI))

        val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_ALL)

        assertNotNull("oauth-callback URI 应解析到某个 Activity", resolveInfo)
        assertEquals(
            "oauth-callback URI 应解析到 MainActivity（host 约束 filter 优先于 AppAuth scheme-only）",
            MainActivity::class.java.name,
            resolveInfo?.activityInfo?.name,
        )
    }
}
