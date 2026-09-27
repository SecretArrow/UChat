package com.uchat.android.terminal.emulator

/**
 * Terminal color system: the 16-color ANSI palette, the 256-color xterm cube and default
 * foreground/background. Colors are stored as packed ARGB ints for fast rendering.
 */
object TerminalColors {

    const val DEFAULT_FG = 256
    const val DEFAULT_BG = 257

    /** JuiceSSH-inspired dark theme palette (0..15). */
    val PALETTE_16 =
        intArrayOf(
            0xFF14161E.toInt(), // 0 black
            0xFFE3567A.toInt(), // 1 red
            0xFF4FE0B0.toInt(), // 2 green
            0xFFF4B65A.toInt(), // 3 yellow
            0xFF6FA8F5.toInt(), // 4 blue
            0xFFC68AF0.toInt(), // 5 magenta
            0xFF5AD4E6.toInt(), // 6 cyan
            0xFFE6E9F0.toInt(), // 7 white
            0xFF3B4051.toInt(), // 8 bright black
            0xFFFF7B93.toInt(), // 9 bright red
            0xFF7DEBC2.toInt(), // 10 bright green
            0xFFFFCD7A.toInt(), // 11 bright yellow
            0xFF93C0FF.toInt(), // 12 bright blue
            0xFFDCAFFF.toInt(), // 13 bright magenta
            0xFF8AE4F2.toInt(), // 14 bright cyan
            0xFFF7F9FC.toInt(), // 15 bright white
        )

    const val CURSOR_COLOR = 0xFF7DEBC2.toInt()
    const val SELECTION_COLOR = 0xFF2C4A66.toInt()

    /** Default screen colors. */
    const val BG_DEFAULT = 0xFF0C0E14.toInt()
    const val FG_DEFAULT = 0xFFE6E9F0.toInt()
    const val BG_ALT_DEFAULT = 0xFF0C0E14.toInt()

    private val cubeColors = IntArray(240)

    init {
        // xterm 256-color: entries 16..231 are the 6x6x6 cube, 232..255 grayscale.
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        var i = 0
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    cubeColors[i++] = argb(steps[r], steps[g], steps[b])
                }
            }
        }
        for (g in 0..23) {
            val v = if (g == 0) 8 else g * 10 + 8
            cubeColors[i++] = argb(v, v, v)
        }
    }

    /** Resolve a palette index (0..255) or the default fg/bg sentinel to ARGB. */
    fun resolve(index: Int): Int =
        when {
            index == DEFAULT_FG -> FG_DEFAULT
            index == DEFAULT_BG -> BG_DEFAULT
            index < 16 -> PALETTE_16[index]
            else -> cubeColors[(index - 16).coerceIn(0, cubeColors.size - 1)]
        }

    private fun argb(r: Int, g: Int, b: Int): Int =
        0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}
