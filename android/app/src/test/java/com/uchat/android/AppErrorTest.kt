package com.uchat.android

import com.uchat.android.core.AppError
import com.uchat.android.core.arch.DeviceAbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppErrorTest {

    @Test
    fun redactsApiKeys() {
        val err =
            AppError(
                title = "OpenCode failed",
                reason = "request failed with Authorization: Bearer sk-abcdefghijklmnopqrstuvwx",
            )
        val text = err.diagnostics()
        assertFalse(text.contains("sk-abcdefghijklmnopqrstuvwx"))
        assertTrue(text.contains("[REDACTED]"))
    }

    @Test
    fun redactsGithubTokens() {
        val err =
            AppError(
                title = "clone failed",
                reason = "remote: Invalid token ghp_abcdefghij1234567890ABCD provided",
            )
        assertFalse(err.diagnostics().contains("ghp_abcdefghij"))
    }

    @Test
    fun includesTitleAndReason() {
        val text = AppError(title = "T", reason = "R").diagnostics()
        assertTrue(text.contains("T"))
        assertTrue(text.contains("R"))
    }
}

class DeviceAbiTest {

    @Test
    fun mapsAndroidAbis() {
        assertEquals(DeviceAbi.ARM64, DeviceAbi.fromAndroidAbi("arm64-v8a"))
        assertEquals(DeviceAbi.ARM32, DeviceAbi.fromAndroidAbi("armeabi-v7a"))
        assertEquals(DeviceAbi.X86_64, DeviceAbi.fromAndroidAbi("x86_64"))
        assertEquals(DeviceAbi.X86, DeviceAbi.fromAndroidAbi("x86"))
        assertEquals(null, DeviceAbi.fromAndroidAbi("mips"))
    }

    @Test
    fun linuxArchMapping() {
        assertEquals("aarch64", DeviceAbi.ARM64.linuxArch)
        assertEquals("armhf", DeviceAbi.ARM32.linuxArch)
        assertEquals("amd64", DeviceAbi.X86_64.linuxArch)
        assertEquals("i386", DeviceAbi.X86.linuxArch)
    }

    @Test
    fun x86IsNotUbuntuSupported() {
        assertFalse(DeviceAbi.X86.ubuntuSupported)
        assertTrue(DeviceAbi.ARM64.ubuntuSupported)
        assertTrue(DeviceAbi.ARM32.ubuntuSupported)
        assertTrue(DeviceAbi.X86_64.ubuntuSupported)
    }
}
