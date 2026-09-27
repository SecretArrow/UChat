package com.uchat.android.terminal.emulator

/**
 * Layer 2 — the terminal buffer: a real cell grid (char + style per cell), scroll region, tab
 * stops, cursor management and a bounded scrollback history.
 *
 * The buffer knows nothing about parsing (see [TerminalEmulator]) or drawing (see the render
 * package). It is pure Kotlin so every behaviour is unit-testable on the JVM.
 *
 * All mutating entry points are guarded by [lock]; the renderer draws under the same lock, which
 * makes lock-free double-buffering unnecessary at mobile terminal data rates.
 */
class TerminalBuffer(cols: Int, rows: Int, var scrollbackMax: Int = DEFAULT_SCROLLBACK) {

    /** Packed cell style helpers (fg 9 bits | bg 9 bits << 9 | flags << 18). */
    object Attr {
        const val FLAG_BOLD = 1
        const val FLAG_DIM = 2
        const val FLAG_ITALIC = 4
        const val FLAG_UNDERLINE = 8
        const val FLAG_BLINK = 16
        const val FLAG_REVERSE = 32
        const val FLAG_CONCEAL = 64
        const val FLAG_STRIKE = 128

        fun pack(fg: Int, bg: Int, flags: Int): Int = fg or (bg shl 9) or (flags shl 18)

        fun fg(p: Int): Int = p and 0x1FF

        fun bg(p: Int): Int = (p shr 9) and 0x1FF

        fun flags(p: Int): Int = (p shr 18) and 0x1FF
    }

    /**
     * One horizontal line of cells. Second half of a wide char is `0` (BMP) or the low surrogate
     * (emoji/astral), both skipped by the renderer and text extraction.
     */
    class TerminalLine(val chars: CharArray, val styles: IntArray) {
        constructor(cols: Int) : this(CharArray(cols) { ' ' }, IntArray(cols))

        constructor(cols: Int, styles: IntArray) : this(CharArray(cols) { ' ' }, styles)
    }

    val lock = Any()

    var cols: Int = cols
        private set

    var rows: Int = rows
        private set

    private var mainLines = Array(rows) { TerminalLine(cols) }
    private var altLines = Array(rows) { TerminalLine(cols) }
    val scrollback = ArrayDeque<TerminalLine>()

    var usingAlt = false
        private set

    var cursorCol = 0
        private set

    var cursorRow = 0
        private set

    /** Set when the cursor wrote the last column and DECAWM is on; next char wraps first. */
    var pendingWrap = false
        private set

    var scrollTop = 0
        private set

    var scrollBottom = 0
        private set

    var tabStops = BooleanArray(cols)

    // Modes the buffer needs to implement its own behaviour.
    var autowrap = true
    var insertMode = false
    var originMode = false

    /** Saved cursor slots (per screen): col, row, style. Managed by the emulator. */
    var savedMain = SavedCursor()
    var savedAlt = SavedCursor()

    class SavedCursor(var col: Int = 0, var row: Int = 0, var style: Int = 0)

    /** Monotonic counter bumped on every visual change; the renderer watches it. */
    var generation: Long = 0
        private set

    init {
        scrollBottom = rows - 1
        resetTabs()
    }

    private fun resetTabs() {
        tabStops = BooleanArray(cols) { it > 0 && it % 8 == 0 }
    }

    private fun bump() {
        generation++
    }

    /** Public bump for state that changes visuals outside of cell writes (palette, etc). */
    fun generationBump() {
        bump()
    }

    val currentScreen: Array<TerminalLine>
        get() = if (usingAlt) altLines else mainLines

    /** Total history length available for scrolling (scrollback + live screen). */
    val totalLines: Int
        get() = scrollback.size + rows

    /**
     * Resolve a viewport line index (0 = oldest scrollback line) to its line, or null when out of
     * range. Live screen lines start after [scrollback.size].
     */
    fun lineAt(viewIndex: Int): TerminalLine? {
        val sb = scrollback.size
        return when {
            viewIndex < 0 -> null
            viewIndex < sb -> scrollback.elementAt(viewIndex)
            viewIndex < sb + rows -> currentScreen[viewIndex - sb]
            else -> null
        }
    }

    fun absoluteRowOf(screenRow: Int): Int = scrollback.size + screenRow

    // ------------------------------------------------------------------ writing

    /** Write one code point (already UTF-16 encoded in [str]) at the cursor. */
    fun putChar(str: String, style: Int) {
        if (str.isEmpty()) return
        if (cursorCol >= cols) cursorCol = cols - 1 // defensive: pending wrap turned off mid-way
        if (pendingWrap && autowrap) {
            // Wrap: like an index() — scroll if at the bottom margin.
            pendingWrap = false
            cursorCol = 0
            index()
        }
        val width = WcWidth.widthOf(str)
        if (width == 0) {
            // Combining mark: cells hold single chars, so sequences that need re-composition are
            // dropped. NFC text (what shells emit) renders correctly; this only degrades the rare
            // decomposed case instead of corrupting the base glyph.
            bump()
            return
        }
        if (width == 2 && cursorCol >= cols - 1) {
            // A wide char needs two cells: wrap first when possible.
            if (autowrap) {
                pendingWrap = false
                cursorCol = 0
                index()
            } else {
                cursorCol = cols - 2.coerceAtLeast(0)
            }
        }
        if (insertMode) {
            // IRM: shift existing content right by the width of the inserted char, then write.
            val line0 = currentScreen[cursorRow]
            val from = cursorCol + width
            if (from < cols) {
                val n = cols - from
                System.arraycopy(line0.chars, cursorCol, line0.chars, from, n)
                System.arraycopy(line0.styles, cursorCol, line0.styles, from, n)
            }
        }
        val line = currentScreen[cursorRow]
        // Overwriting the right half of a wide char: clean the orphaned left half.
        if (cursorCol > 0 && isContinuationCell(line.chars[cursorCol])) {
            line.chars[cursorCol - 1] = ' '
        }
        line.chars[cursorCol] = str[0]
        line.styles[cursorCol] = style
        if (width == 2 && cursorCol + 1 < cols) {
            // Keep the full surrogate pair in the continuation cell when present.
            line.chars[cursorCol + 1] = if (str.length > 1) str[1] else 0.toChar()
            line.styles[cursorCol + 1] = style
        } else if (width == 1) {
            // Overwriting the left half of a wide char: clear the orphaned continuation.
            if (cursorCol + 1 < cols && isContinuationCell(line.chars[cursorCol + 1])) {
                line.chars[cursorCol + 1] = ' '
                line.styles[cursorCol + 1] = style
            }
        }
        cursorCol += width
        if (cursorCol >= cols) {
            if (autowrap) {
                cursorCol = cols
                pendingWrap = true
            } else {
                cursorCol = cols - 1
            }
        }
        bump()
    }

    // ------------------------------------------------------------------ cursor moves

    /** Move the cursor to an absolute cell, clamped to margins in origin mode. */
    fun moveCursor(col: Int, row: Int) {
        pendingWrap = false
        val r =
            if (originMode) (row + scrollTop).coerceIn(scrollTop, scrollBottom)
            else row.coerceIn(0, rows - 1)
        cursorRow = r
        cursorCol = col.coerceIn(0, cols - 1)
        bump()
    }

    fun cursorForward(n: Int) {
        pendingWrap = false
        cursorCol = (cursorCol + n.coerceAtLeast(1)).coerceAtMost(cols - 1)
        bump()
    }

    fun cursorBackward(n: Int) {
        pendingWrap = false
        cursorCol = (cursorCol - n.coerceAtLeast(1)).coerceIn(0, cols - 1)
        bump()
    }

    fun cursorUp(n: Int) {
        pendingWrap = false
        val top = if (originMode) scrollTop else 0
        cursorRow = (cursorRow - n.coerceAtLeast(1)).coerceIn(top, rows - 1)
        bump()
    }

    fun cursorDown(n: Int) {
        pendingWrap = false
        val bottom = if (originMode) scrollBottom else rows - 1
        cursorRow = (cursorRow + n.coerceAtLeast(1)).coerceIn(0, bottom)
        bump()
    }

    fun carriageReturn() {
        pendingWrap = false
        cursorCol = 0
    }

    fun backspace() {
        pendingWrap = false
        if (cursorCol > 0) cursorCol--
    }

    /** LF / IND: advance one row, scrolling the region when at its bottom. */
    fun index() {
        pendingWrap = false
        if (cursorRow == scrollBottom) {
            scrollRegionUp(1)
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
        bump()
    }

    /** RI: reverse line feed. */
    fun reverseIndex() {
        pendingWrap = false
        if (cursorRow == scrollTop) {
            scrollRegionDown(1)
        } else if (cursorRow > 0) {
            cursorRow--
        }
        bump()
    }

    fun setCursorCol(col: Int) {
        pendingWrap = false
        cursorCol = col.coerceIn(0, cols - 1)
        bump()
    }

    /** VPA — respects origin mode. */
    fun setCursorRow(row: Int) {
        pendingWrap = false
        cursorRow =
            if (originMode) (row + scrollTop).coerceIn(scrollTop, scrollBottom)
            else row.coerceIn(0, rows - 1)
        bump()
    }

    fun cursorReport(): Pair<Int, Int> =
        1 + (if (cursorCol >= cols) cols - 1 else cursorCol) to 1 + cursorRow

    // ------------------------------------------------------------------ tabs

    fun tabForward() {
        pendingWrap = false
        if (cursorCol >= cols) return
        var c = cursorCol + 1
        while (c < cols && !tabStops[c]) c++
        cursorCol = c.coerceAtMost(cols - 1)
        bump()
    }

    fun tabBackward() {
        pendingWrap = false
        var c = cursorCol - 1
        while (c > 0 && !tabStops[c]) c--
        cursorCol = c.coerceAtLeast(0)
        bump()
    }

    fun setTabStop() {
        if (cursorCol in tabStops.indices) tabStops[cursorCol] = true
    }

    fun clearTabStop(mode: Int) {
        when (mode) {
            0 -> if (cursorCol in tabStops.indices) tabStops[cursorCol] = false
            3 -> BooleanArray(tabStops.size).copyInto(tabStops)
        }
    }

    // ------------------------------------------------------------------ scrolling

    /**
     * Scroll the active region up by [n] lines. When the full screen scrolls in the main buffer,
     * pushed lines go to scrollback.
     */
    fun scrollRegionUp(n: Int) {
        val count = n.coerceIn(1, scrollBottom - scrollTop + 1)
        val lines = currentScreen
        val fullScreen = scrollTop == 0 && scrollBottom == rows - 1
        for (i in 0 until count) {
            val removed = lines[scrollTop]
            for (r in scrollTop until scrollBottom) {
                lines[r] = lines[r + 1]
            }
            lines[scrollBottom] = TerminalLine(cols)
            if (fullScreen && !usingAlt) {
                scrollback.addLast(removed)
                while (scrollback.size > scrollbackMax) scrollback.removeFirst()
            }
        }
        bump()
    }

    /** Scroll the active region down by [n] lines (leaves room at the top). */
    fun scrollRegionDown(n: Int) {
        val count = n.coerceIn(1, scrollBottom - scrollTop + 1)
        val lines = currentScreen
        for (i in 0 until count) {
            for (r in scrollBottom downTo scrollTop + 1) {
                lines[r] = lines[r - 1]
            }
            lines[scrollTop] = TerminalLine(cols)
        }
        bump()
    }

    // ------------------------------------------------------------------ editing

    fun insertLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        val count = n.coerceIn(1, scrollBottom - cursorRow + 1)
        val lines = currentScreen
        for (i in 0 until count) {
            for (r in scrollBottom downTo cursorRow + 1) {
                lines[r] = lines[r - 1]
            }
            lines[cursorRow] = TerminalLine(cols)
        }
        bump()
    }

    fun deleteLines(n: Int) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return
        val count = n.coerceIn(1, scrollBottom - cursorRow + 1)
        val lines = currentScreen
        for (i in 0 until count) {
            for (r in cursorRow until scrollBottom) {
                lines[r] = lines[r + 1]
            }
            lines[scrollBottom] = TerminalLine(cols)
        }
        bump()
    }

    fun insertChars(n: Int) {
        val count = n.coerceIn(1, cols - cursorCol)
        val line = currentScreen[cursorRow]
        for (i in 0 until count) {
            for (c in cols - 1 downTo cursorCol + 1) {
                line.chars[c] = line.chars[c - 1]
                line.styles[c] = line.styles[c - 1]
            }
            line.chars[cursorCol] = ' '
            line.styles[cursorCol] = blankStyle()
        }
        bump()
    }

    fun deleteChars(n: Int) {
        val count = n.coerceIn(1, cols - cursorCol)
        val line = currentScreen[cursorRow]
        for (i in 0 until count) {
            for (c in cursorCol until cols - 1) {
                line.chars[c] = line.chars[c + 1]
                line.styles[c] = line.styles[c + 1]
            }
            line.chars[cols - 1] = ' '
            line.styles[cols - 1] = blankStyle()
        }
        bump()
    }

    fun eraseChars(n: Int) {
        val count = n.coerceIn(1, cols - cursorCol)
        val line = currentScreen[cursorRow]
        for (c in cursorCol until cursorCol + count) {
            line.chars[c] = ' '
            line.styles[c] = blankStyle()
        }
        bump()
    }

    private fun blankStyle(): Int = Attr.pack(TerminalColors.DEFAULT_FG, currentBlankBg, 0)

    /** Background color erase: set by the emulator to the active SGR background. */
    var currentBlankBg: Int = TerminalColors.DEFAULT_BG

    fun eraseLine(mode: Int) {
        val line = currentScreen[cursorRow]
        when (mode) {
            0 ->
                for (c in cursorCol until cols) {
                    line.chars[c] = ' '
                    line.styles[c] = blankStyle()
                }
            1 ->
                for (c in 0..cursorCol.coerceAtMost(cols - 1)) {
                    line.chars[c] = ' '
                    line.styles[c] = blankStyle()
                }
            2 ->
                for (c in 0 until cols) {
                    line.chars[c] = ' '
                    line.styles[c] = blankStyle()
                }
        }
        bump()
    }

    fun eraseDisplay(mode: Int) {
        val lines = currentScreen
        when (mode) {
            0 -> {
                eraseLine(0)
                for (r in cursorRow + 1 until rows) lines[r] = TerminalLine(cols, blankAll())
            }
            1 -> {
                eraseLine(1)
                for (r in 0 until cursorRow) lines[r] = TerminalLine(cols, blankAll())
            }
            2 -> for (r in 0 until rows) lines[r] = TerminalLine(cols, blankAll())
            3 -> {
                for (r in 0 until rows) lines[r] = TerminalLine(cols, blankAll())
                scrollback.clear()
            }
        }
        bump()
    }

    private fun blankAll(): IntArray = IntArray(cols) { blankStyle() }

    /** True when a cell is the continuation half of a double-width character. */
    fun isContinuationCell(ch: Char): Boolean = ch.code == 0 || Character.isLowSurrogate(ch)

    // ------------------------------------------------------------------ screens & modes

    fun switchToAlt(clear: Boolean) {
        if (usingAlt) return
        usingAlt = true
        if (clear) {
            for (r in 0 until rows) altLines[r] = TerminalLine(cols, IntArray(cols))
        }
        pendingWrap = false
        bump()
    }

    fun switchToMain() {
        if (!usingAlt) return
        usingAlt = false
        pendingWrap = false
        bump()
    }

    fun setMargins(top: Int, bottom: Int) {
        val t = top.coerceIn(0, rows - 1)
        val b = bottom.coerceIn(0, rows - 1)
        if (t < b) {
            scrollTop = t
            scrollBottom = b
        } else {
            scrollTop = 0
            scrollBottom = rows - 1
        }
        moveCursor(0, 0)
    }

    fun clearScreenIncludingScrollback() {
        scrollback.clear()
        eraseDisplay(2)
        moveCursor(0, 0)
    }

    /** Reset margins to the full screen (used by DECCOLM and DECALN). */
    fun resetMargins() {
        scrollTop = 0
        scrollBottom = rows - 1
    }

    /** DECALN: fill the screen with [ch] using the default style. */
    fun fillScreen(ch: Char) {
        val lines = currentScreen
        for (r in 0 until rows) {
            val line = lines[r]
            for (c in 0 until cols) {
                line.chars[c] = ch
                line.styles[c] = blankStyle()
            }
        }
        bump()
    }

    // ------------------------------------------------------------------ resize

    /**
     * Resize the grid. Content is preserved by clamping/padding (no reflow); scrollback lines keep
     * their own width and are simply rendered wider/narrower. Simple, predictable, and matches the
     * behaviour most mobile terminals had for years.
     */
    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        cols = newCols.coerceAtLeast(2)
        rows = newRows.coerceAtLeast(1)
        mainLines =
            Array(rows) { r ->
                if (r < mainLines.size) padLine(mainLines[r]) else TerminalLine(cols)
            }
        altLines =
            Array(rows) { r -> if (r < altLines.size) padLine(altLines[r]) else TerminalLine(cols) }
        cursorCol = cursorCol.coerceIn(0, cols - 1)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        scrollTop = 0
        scrollBottom = rows - 1
        resetTabs()
        pendingWrap = false
        bump()
    }

    private fun padLine(line: TerminalLine): TerminalLine {
        if (line.chars.size == cols) return line
        val chars = CharArray(cols) { c -> if (c < line.chars.size) line.chars[c] else ' ' }
        val styles = IntArray(cols) { c -> if (c < line.styles.size) line.styles[c] else 0 }
        return TerminalLine(chars, styles)
    }

    /** Full reset (RIS). */
    fun reset() {
        scrollback.clear()
        mainLines = Array(rows) { TerminalLine(cols) }
        altLines = Array(rows) { TerminalLine(cols) }
        usingAlt = false
        cursorCol = 0
        cursorRow = 0
        pendingWrap = false
        scrollTop = 0
        scrollBottom = rows - 1
        resetTabs()
        autowrap = true
        insertMode = false
        originMode = false
        savedMain = SavedCursor()
        savedAlt = SavedCursor()
        bump()
    }

    // ------------------------------------------------------------------ text extraction

    /** Plain text of a viewport range (rows [startRow, endRow], used for copy/search). */
    fun textRows(startRow: Int, endRow: Int): List<String> {
        val out = ArrayList<String>()
        for (i in startRow..endRow.coerceAtMost(totalLines - 1)) {
            val line = lineAt(i) ?: break
            out.add(lineText(line))
        }
        return out
    }

    private fun lineText(line: TerminalLine): String {
        val sb = StringBuilder(line.chars.size)
        for (c in line.chars) {
            if (!isContinuationCell(c)) sb.append(c)
        }
        return sb.toString().trimEnd(' ')
    }

    /** Text of a rectangular selection in viewport coordinates. */
    fun selectionText(
        anchorRow: Int,
        anchorCol: Int,
        focusRow: Int,
        focusCol: Int,
    ): String {
        val (r1, c1) = if (anchorRow <= focusRow) anchorRow to anchorCol else focusRow to focusCol
        val (r2, c2) = if (anchorRow <= focusRow) focusRow to focusCol else anchorRow to anchorCol
        val out = StringBuilder()
        for (i in r1..r2.coerceAtMost(totalLines - 1)) {
            val line = lineAt(i) ?: break
            val size = line.chars.size
            val from = if (i == r1) c1.coerceIn(0, size - 1) else 0
            val to = if (i == r2) c2.coerceIn(0, size - 1) else size - 1
            for (c in from..to) {
                val ch = line.chars[c]
                if (!isContinuationCell(ch)) out.append(ch)
            }
            if (i != r2) out.append('\n')
        }
        // Rows are stored space-padded to the grid width — trim trailing padding on copy.
        return out.toString().replace(Regex(" +\n"), "\n").trimEnd(' ')
    }

    companion object {
        const val DEFAULT_SCROLLBACK = 2000
        const val MAX_SCROLLBACK = 10000
    }
}
