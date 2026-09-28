package com.uchat.android.terminal

import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExitBannerTest {

    @Test
    fun bannerContainsLabelAndCode() {
        val text = String(SessionExitBanner.format("OpenCode", 0), Charsets.UTF_8)
        assertTrue("OpenCode" in text)
        assertTrue("code 0" in text)
    }

    @Test
    fun bannerExplainsAndroidLowMemoryKill() {
        val text = String(SessionExitBanner.format("Terminal", 137), Charsets.UTF_8)
        assertTrue("137" in text)
        // Bilingual explanation so Indonesian users immediately understand exit 137.
        assertTrue("Android" in text)
        assertTrue("dihentikan Android" in text)
    }

    @Test
    fun bannerExplainsFailureToStart() {
        val text = String(SessionExitBanner.format("Claude", -1), Charsets.UTF_8)
        assertTrue("gagal dimulai" in text)
    }

    @Test
    fun bannerHasCarriageReturnFraming() {
        val text = String(SessionExitBanner.format("Terminal", 1), Charsets.UTF_8)
        // Opens on a fresh line and closes with a trailing newline (after the ANSI reset).
        assertTrue(text.startsWith("\r\n"))
        assertTrue(text.endsWith("\r\n"))
        assertTrue("[Terminal" in text)
    }
}
