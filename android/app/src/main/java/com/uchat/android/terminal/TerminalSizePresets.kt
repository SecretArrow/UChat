package com.uchat.android.terminal

/**
 * Pure model of the terminal size presets offered in Terminal settings + the in-terminal "Size &
 * zoom" dialog.
 *
 * Fixed sizes are useful on phones: opencode/claude TUIs render much better with a predictable
 * 120×40 grid than with whatever the tiny portrait viewport happens to produce, and the renderer
 * scales a fixed grid down to stay fully visible (GridTransform.solve).
 */
data class TerminalSizePreset(val cols: Int, val rows: Int) {
    val label: String
        get() = "$cols×$rows"
}

object TerminalSizePresets {

    /** All bounds line up with [TerminalController] clamps (MIN/MAX cols/rows). */
    val ALL: List<TerminalSizePreset> =
        listOf(
            TerminalSizePreset(80, 24),
            TerminalSizePreset(96, 28),
            TerminalSizePreset(105, 34),
            TerminalSizePreset(120, 40),
            TerminalSizePreset(132, 43),
            TerminalSizePreset(150, 50),
            TerminalSizePreset(180, 60),
            TerminalSizePreset(210, 70),
            TerminalSizePreset(240, 80),
            TerminalSizePreset(300, 94),
        )

    /** The preset currently active in fixed mode, or null when fit-screen is on / custom size. */
    fun matching(fitScreen: Boolean, cols: Int, rows: Int): TerminalSizePreset? =
        if (fitScreen) null else ALL.firstOrNull { it.cols == cols && it.rows == rows }
}
