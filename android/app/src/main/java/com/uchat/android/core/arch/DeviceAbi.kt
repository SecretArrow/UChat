package com.uchat.android.core.arch

import android.os.Build
import com.uchat.android.core.format.Format

/**
 * Device CPU ABI mapping to the Linux userspace world.
 *
 * arm64-v8a → aarch64 (Ubuntu rootfs available) armeabi-v7a → armhf (Ubuntu rootfs available)
 * x86_64 → amd64 (Ubuntu rootfs available) x86 → i386 (Ubuntu 24.04 no longer ships i386 rootfs →
 * unsupported)
 */
enum class DeviceAbi(
    val androidAbi: String,
    val linuxArch: String,
    val ubuntuSupported: Boolean,
    val displayName: String,
) {
    ARM64("arm64-v8a", "aarch64", true, "ARM64 (aarch64)"),
    ARM32("armeabi-v7a", "armhf", true, "ARMv7 (armhf)"),
    X86_64("x86_64", "amd64", true, "x86_64 (amd64)"),
    X86("x86", "i386", false, "x86 (i386)"),
    ;

    val is64Bit: Boolean
        get() = this == ARM64 || this == X86_64

    companion object {
        /** Primary ABI of the running device, from the highest priority supported list. */
        fun current(): DeviceAbi {
            val supported = Build.SUPPORTED_ABIS ?: emptyArray()
            for (abi in supported) {
                fromAndroidAbi(abi)?.let {
                    return it
                }
            }
            return X86_64
        }

        fun fromAndroidAbi(name: String): DeviceAbi? = entries.firstOrNull { it.androidAbi == name }
    }
}

/** A short device/storage summary used on the onboarding screen and diagnostics. */
data class DeviceSummary(
    val abi: DeviceAbi,
    val availableBytes: Long,
    val totalBytes: Long,
    val model: String,
    val androidVersion: String,
    val ramMb: Long,
) {
    fun availableText(): String = Format.bytes(availableBytes)

    fun totalText(): String = Format.bytes(totalBytes)
}
