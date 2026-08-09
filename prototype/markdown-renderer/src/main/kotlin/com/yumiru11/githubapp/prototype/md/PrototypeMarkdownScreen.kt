// ===== PROTOTYPE（可抛弃，验证用）=====
// 组装浏览器：变体（Issue 正文 / 重型 GFM / README 式）× 亮/暗主题 × Material Symbols 图标条。
// 截图矩阵见 MarkdownRendererPrototypeTest（Roborazzi，Linux 免模拟器）。
package com.yumiru11.githubapp.prototype.md

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Home as OutlinedHome
import com.composables.icons.materialsymbols.outlined.Notifications as OutlinedNotifications
import com.composables.icons.materialsymbols.outlined.Search as OutlinedSearch
import com.composables.icons.materialsymbols.outlined.Settings as OutlinedSettings
import com.composables.icons.materialsymbols.outlined.Star as OutlinedStar
import com.composables.icons.materialsymbols.rounded.Home
import com.composables.icons.materialsymbols.rounded.Notifications
import com.composables.icons.materialsymbols.rounded.Search
import com.composables.icons.materialsymbols.rounded.Settings
import com.composables.icons.materialsymbols.rounded.Star
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
fun PrototypeMarkdownScreen(variant: MdVariant, darkTheme: Boolean) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "PROTOTYPE · ${variant.label} · ${if (darkTheme) "dark" else "light"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                IconStrip()
                Spacer(Modifier.height(12.dp))
                val state = rememberMarkdownState(
                    when (variant) {
                        MdVariant.A -> SAMPLE_A
                        MdVariant.B -> SAMPLE_B
                        MdVariant.C -> SAMPLE_C
                    },
                )
                Markdown(
                    state,
                    imageTransformer = Coil3ImageTransformerImpl,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** 图标条：Material Symbols（marella 同源）—— rounded / outlined 两套风格并置，验证主题着色与绘制 */
@Composable
private fun IconStrip() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val rounded = MaterialSymbols.Rounded
        Icon(rounded.Star, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(rounded.Home, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(rounded.Search, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(rounded.Notifications, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(rounded.Settings, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(12.dp))
        val outlined = MaterialSymbols.Outlined
        Icon(outlined.OutlinedStar, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(outlined.OutlinedHome, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(outlined.OutlinedSearch, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(outlined.OutlinedNotifications, contentDescription = null, modifier = Modifier.size(28.dp))
        Icon(outlined.OutlinedSettings, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}