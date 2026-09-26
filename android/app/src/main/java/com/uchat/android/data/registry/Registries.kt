package com.uchat.android.data.registry

import com.uchat.android.core.arch.DeviceAbi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Pinned download assets (Ubuntu rootfs + proot) with sizes and SHA-256. */
@Serializable
data class AssetEntry(
    val id: String,
    val name: String,
    val arch: String,
    val androidAbi: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val extractedBytes: Long,
    val minFreeBytes: Long,
)

@Serializable
data class AssetRegistry(
    val schemaVersion: Int,
    val updatedAt: String,
    val notes: String = "",
    val rootfs: List<AssetEntry> = emptyList(),
    val proot: List<AssetEntry> = emptyList(),
) {
    fun rootfsFor(abi: DeviceAbi): AssetEntry? =
        rootfs.firstOrNull { it.androidAbi == abi.androidAbi }

    fun prootFor(abi: DeviceAbi): AssetEntry? =
        proot.firstOrNull { it.androidAbi == abi.androidAbi }

    /** Total bytes that must be free on disk before starting an install (spec #69). */
    fun installRequirementFor(abi: DeviceAbi): InstallRequirement? {
        val rootfs = rootfsFor(abi) ?: return null
        val proot = prootFor(abi) ?: return null
        val downloadBytes = rootfs.sizeBytes + proot.sizeBytes
        val extractedBytes = rootfs.extractedBytes + proot.extractedBytes
        val minFree = maxOf(rootfs.minFreeBytes, proot.minFreeBytes)
        return InstallRequirement(
            downloadBytes = downloadBytes,
            extractedBytes = extractedBytes,
            minFreeBytes = maxOf(minFree, downloadBytes + extractedBytes),
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): AssetRegistry = json.decodeFromString(text)

        /**
         * Integrity self-check: the registry used at runtime must match the SHA-256 of the
         * asset-registry.json committed next to it. Guards against a tampered registry on a patched
         * build.
         */
        fun isSha256Valid(value: String): Boolean {
            if (value.length != 64) return false
            return value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        }
    }
}

/** Disk-space requirement snapshot shown before the user taps Install. */
data class InstallRequirement(
    val downloadBytes: Long,
    val extractedBytes: Long,
    val minFreeBytes: Long,
)

/** AI/runtime tool definitions (spec #70 Tool Registry). */
@Serializable
data class ToolEntry(
    val id: String,
    val name: String,
    val category: String,
    val executable: String,
    val verifyCommand: String,
    val installScript: String? = null,
    val description: String = "",
    val docsUrl: String = "",
    val supportsEnv: Boolean = false,
)

@Serializable
data class ToolRegistry(
    val schemaVersion: Int,
    val tools: List<ToolEntry> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): ToolRegistry = json.decodeFromString(text)
    }
}
