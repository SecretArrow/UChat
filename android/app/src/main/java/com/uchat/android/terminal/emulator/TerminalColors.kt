package com.uchat.android.terminal.emulator

/**
 * Terminal color system: the 16-color ANSI palette, the 256-color xterm cube and default
 * foreground/background. Colors are stored as packed ARGB ints for fast rendering.
 *
 * The defaults (bg/fg/cursor/selection + the 16-color palette) are scheme-aware so the whole
 * terminal follows the app's dark/light theme ([applyLightScheme]). The renderer and the
 * emulator only ever read these vars at draw time, so switching scheme takes effect on the
 * next frame without touching the buffer.
 */
object TerminalColors {

    const val DEFAULT_FG = 256
    const val DEFAULT_BG = 257

    /** JuiceSSH-inspired dark palette (0..15). */
    private val DARK_PALETTE_16 =
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

    /**
     * Light palette: hues kept, luminance rebalanced so every ANSI color stays readable on a
     * near-white background ("white"/bright white become mid-grays instead of invisible white).
     */
    private val LIGHT_PALETTE_16 =
        intArrayOf(
            0xFF242933.toInt(), // 0 black
            0xFFC93A5B.toInt(), // 1 red
            0xFF1F9E77.toInt(), // 2 green
            0xFFB07A1F.toInt(), // 3 yellow
            0xFF3D6FBF.toInt(), // 4 blue
            0xFF9A4FC6.toInt(), // 5 magenta
            0xFF1F8FA6.toInt(), // 6 cyan
            0xFF5B6270.toInt(), // 7 white
            0xFF565E6E.toInt(), // 8 bright black
            0xFFD94164.toInt(), // 9 bright red
            0xFF12855E.toInt(), // 10 bright green
            0xFF96660D.toInt(), // 11 bright yellow
            0xFF2E62D9.toInt(), // 12 bright blue
            0xFF8B3DD9.toInt(), // 13 bright magenta
            0xFF127896.toInt(), // 14 bright cyan
            0xFF3B4051.toInt(), // 15 bright white
        )

    @Volatile var PALETTE_16: IntArray = DARK_PALETTE_16
        private set

    @Volatile var CURSOR_COLOR: Int = DARK_CURSOR_COLOR
        private set

    @Volatile var SELECTION_COLOR: Int = DARK_SELECTION_COLOR
        private set

    /** Default screen colors — scheme-aware (see [applyLightScheme]). */
    @Volatile var BG_DEFAULT: Int = DARK_BG_DEFAULT
        private set

    @Volatile var FG_DEFAULT: Int = DARK_FG_DEFAULT
        private set

    @Volatile var BG_ALT_DEFAULT: Int = DARK_BG_DEFAULT
        private set

    private const val DARK_CURSOR_COLOR = 0xFF7DEBC2.toInt()
    private const val DARK_SELECTION_COLOR = 0xFF2C4A66.toInt()
    private const val DARK_BG_DEFAULT = 0xFF0C0E14.toInt()
    private const val DARK_FG_DEFAULT = 0xFFE6E9F0.toInt()

    private const val LIGHT_CURSOR_COLOR = 0xFF0F8A62.toInt()
    private const val LIGHT_SELECTION_COLOR = 0xFFBFD9F2.toInt()
    private const val LIGHT_BG_DEFAULT = 0xFFF7F8FA.toInt()
    private const val LIGHT_FG_DEFAULT = 0xFF1C2030.toInt()

    /**
     * Switch the default screen colors + 16-color palette between the dark and light terminal
     * scheme. Called from composition whenever the app theme changes; safe to call repeatedly.
     */
    fun applyLightScheme(light: Boolean) {
        if (light) {
            PALETTE_16 = LIGHT_PALETTE_16
            CURSOR_COLOR = LIGHT_CURSOR_COLOR
            SELECTION_COLOR = LIGHT_SELECTION_COLOR
            BG_DEFAULT = LIGHT_BG_DEFAULT
            FG_DEFAULT = LIGHT_FG_DEFAULT
            BG_ALT_DEFAULT = LIGHT_BG_DEFAULT
        } else {
            PALETTE_16 = DARK_PALETTE_16
            CURSOR_COLOR = DARK_CURSOR_COLOR
            SELECTION_COLOR = DARK_SELECTION_COLOR
            BG_DEFAULT = DARK_BG_DEFAULT
            FG_DEFAULT = DARK_FG_DEFAULT
            BG_ALT_DEFAULT = DARK_BG_DEFAULT
        }
    }

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
