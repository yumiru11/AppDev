/*
 * PROTOTYPE ONLY — resolves the shared fixture image from app assets so the B screenshot
 * has no network dependency. Do not reuse in production.
 */
package com.yumiru11.githubapp.prototype.readmecomparison

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/** Maps `assets/<file>` links in the shared fixture to prototype module assets. */
class AssetMarkdownImageTransformer(
    private val context: Context,
) : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        if (!link.startsWith("assets/")) {
            return Coil3ImageTransformerImpl.transform(link)
        }
        val bitmap =
            remember(link) {
                context.assets.open(link).use { BitmapFactory.decodeStream(it) }
            } ?: return Coil3ImageTransformerImpl.transform(link)
        return ImageData(painter = BitmapPainter(bitmap.asImageBitmap()))
    }
}
