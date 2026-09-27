package com.uchat.android

import com.uchat.android.terminal.emulator.TerminalColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The terminal canvas palette must follow the app's dark/light theme switch. */
class TerminalColorsTest {

    @Test
    fun `light scheme swaps defaults and palette`() {
        val darkBg = TerminalColors.BG_DEFAULT
        val darkFg = TerminalColors.FG_DEFAULT
        val darkPalette = TerminalColors.PALETTE_16.clone()

        TerminalColors.applyLightScheme(true)
        try {
            assertNotEquals(darkBg, TerminalColors.BG_DEFAULT)
            assertNotEquals(darkFg, TerminalColors.FG_DEFAULT)
            // Light theme: near-white background (red channel high), near-black text (low).
            assertTrue(((TerminalColors.BG_DEFAULT ushr 16) and 0xFF) > 0xF0)
            assertTrue(((TerminalColors.FG_DEFAULT ushr 16) and 0xFF) < 0x30)
            assertNotEquals(darkPalette.toList(), TerminalColors.PALETTE_16.toList())
            // resolve() routes the default sentinels to the *active* scheme.
            assertEquals(
                TerminalColors.FG_DEFAULT,
                TerminalColors.resolve(TerminalColors.DEFAULT_FG),
            )
            assertEquals(
                TerminalColors.BG_DEFAULT,
                TerminalColors.resolve(TerminalColors.DEFAULT_BG),
            )
        } finally {
            TerminalColors.applyLightScheme(false)
        }
    }

    @Test
    fun `dark scheme restores original defaults`() {
        val darkBgBefore = TerminalColors.BG_DEFAULT
        TerminalColors.applyLightScheme(true)
        TerminalColors.applyLightScheme(false)
        assertEquals(darkBgBefore, TerminalColors.BG_DEFAULT)
        // ANSI palette entries resolve from the restored dark palette.
        assertEquals(0xFFE3567A.toInt(), TerminalColors.resolve(1))
    }

    @Test
    fun `cube colors are stable across scheme switches`() {
        val cube = TerminalColors.resolve(196)
        TerminalColors.applyLightScheme(true)
        TerminalColors.applyLightScheme(false)
        assertEquals(cube, TerminalColors.resolve(196))
    }
}
