/*
 * PROTOTYPE ONLY — do not reuse in production modules.
 * Loads the single shared README fixture used by both renderers.
 */
package com.yumiru11.githubapp.prototype.readmecomparison

import android.content.Context

/** Shared fixture loader for the README rendering comparison prototype. */
object PrototypeReadme {
    const val ASSET_PATH = "complex-readme.md"

    fun load(context: Context): String =
        context.assets
            .open(ASSET_PATH)
            .bufferedReader()
            .use { it.readText() }
}
