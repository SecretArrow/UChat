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

    // ---- crash-watchdog restart note ----

    @Test
    fun restartNoteMentionsLabelAttemptAndSeconds() {
        val text = String(SessionExitBanner.formatRestartNote("OpenCode", 1, 2_000), Charsets.UTF_8)
        assertTrue("OpenCode" in text)
        assertTrue("auto-restart 1" in text)
        assertTrue("2s" in text)
        assertTrue("restart otomatis" in text)
    }

    @Test
    fun restartNoteIsYellowAndFramedLikeTheExitBanner() {
        val text = String(SessionExitBanner.formatRestartNote("Claude", 2, 4_000), Charsets.UTF_8)
        assertTrue(text.startsWith("\r\n"))
        assertTrue(text.endsWith("\r\n"))
        // Yellow foreground (actionable news) vs the dim grey exit banner.
        assertTrue("\u001b[33m" in text)
        assertTrue("\u001b[0m" in text)
    }
}
