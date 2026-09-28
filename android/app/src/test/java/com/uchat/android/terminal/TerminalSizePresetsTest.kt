package com.uchat.android.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSizePresetsTest {

    @Test
    fun allPresetsWithinControllerClamps() {
        TerminalSizePresets.ALL.forEach { preset ->
            assertTrue(preset.cols in TerminalController.MIN_COLS..TerminalController.MAX_COLS)
            assertTrue(preset.rows in TerminalController.MIN_ROWS..TerminalController.MAX_ROWS)
        }
    }

    @Test
    fun presetsAreUnique() {
        assertEquals(
            TerminalSizePresets.ALL.map { it.label }.distinct().size,
            TerminalSizePresets.ALL.size,
        )
    }

    @Test
    fun labelsUseXSeparator() {
        assertEquals("80×24", TerminalSizePresets.ALL.first().label)
        assertEquals("300×94", TerminalSizePresets.ALL.last().label)
    }

    @Test
    fun matchingReturnsNullInFitMode() {
        assertNull(TerminalSizePresets.matching(fitScreen = true, cols = 80, rows = 24))
    }

    @Test
    fun matchingFindsExactPreset() {
        assertEquals(
            TerminalSizePreset(120, 40),
            TerminalSizePresets.matching(fitScreen = false, cols = 120, rows = 40),
        )
    }

    @Test
    fun matchingReturnsNullForCustomSizes() {
        assertNull(TerminalSizePresets.matching(fitScreen = false, cols = 137, rows = 41))
    }
}
