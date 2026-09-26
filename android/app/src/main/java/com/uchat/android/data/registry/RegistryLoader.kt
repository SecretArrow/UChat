package com.uchat.android.data.registry

import android.content.Context
import com.uchat.android.core.log.Logs

/** Loads the versioned registries bundled in APK assets (spec #58, #70). */
class RegistryLoader(private val context: Context) {

    fun loadAssets(): AssetRegistry = read("registry/asset-registry.json", AssetRegistry::fromJson)

    fun loadTools(): ToolRegistry = read("registry/tool-registry.json", ToolRegistry::fromJson)

    private fun <T> read(path: String, parse: (String) -> T): T {
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        return parse(text).also { Logs.app("loaded $path") }
    }
}
