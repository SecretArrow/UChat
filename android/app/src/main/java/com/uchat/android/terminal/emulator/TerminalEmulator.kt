package com.uchat.android.terminal.emulator

/**
 * Layer 1 — the terminal emulator: a real VT100/VT220/xterm state machine.
 *
 * Supports the escape-sequence surface a modern interactive shell needs:
 * - C0 controls (BS, HT, LF, CR, BEL, SUB, ESC), UTF-8 input with incremental decoding
 * - CSI: cursor movement (CUP/CUU/CUD/CUF/CUB/CNL/CPL/CHA/VPA/HPR/VPR), ED/EL, IL/DL, ICH/DCH/ECH,
 *   SU/SD, DECSTBM margins, SCOSC/SCORC, SGR (16 + 256 + RGB colors, bold, dim, italic, underline,
 *   blink, reverse, conceal, strike), DECSET/DECRST modes, DSR/CPR and DA device reports
 * - OSC: window title, palette set/reset (4/104), default colors (10/11), clipboard (52)
 * - Modes: DECAWM autowrap, DECOM origin, IRM insert, DECCKM application cursor keys, DECTCEM
 *   cursor visibility, cursor blink, alternate screen (47/1047/1049), bracketed paste (2004), focus
 *   reporting (1004), mouse-mode tracking flags, LNM newline mode
 *
 * The emulator owns no I/O: it feeds bytes in ([feed]), exposes the [buffer] for rendering and
 * reports terminal→host traffic through [Callbacks.onResponse] (DSR/DA replies).
 */
class TerminalEmulator(
    val buffer: TerminalBuffer,
    val callbacks: Callbacks = Callbacks(),
) {

    class Callbacks(
        var onTitle: (String) -> Unit = {},
        var onResponse: (ByteArray) -> Unit = {},
        var onBell: () -> Unit = {},
        var onClipboard: (String) -> Unit = {},
    )

    private enum class State {
        GROUND,
        ESC,
        ESC_CHARSET,
        CSI,
        OSC,
        OSC_ESC,
        IGNORE_UNTIL_ST,
        DCS_ESC,
    }

    private var state = State.GROUND

    // SGR state
    private var curFg = TerminalColors.DEFAULT_FG
    private var curBg = TerminalColors.DEFAULT_BG
    private var curFlags = 0

    // Modes
    var appCursor = false
        private set

    var appKeypad = false
        private set

    var bracketedPaste = false
        private set

    var cursorVisible = true
        private set

    var cursorBlinking = true
        private set

    var focusReporting = false
        private set

    var newlineMode = false
        private set

    var mouseMode = 0
        private set

    /** Palette overrides written by OSC 4 (index → ARGB, null = default palette). */
    var paletteOverride: IntArray? = null
        private set

    // UTF-8 incremental decoder
    private var utf8Remaining = 0
    private var utf8Value = 0

    // ESC state
    private var escLead = 0.toChar()

    // CSI state
    private var csiPrivate = 0.toChar()
    private var csiIntermediates = StringBuilder()
    private val csiParams = IntArray(MAX_PARAMS)
    private var csiParamCount = 0
    private var csiCurrentParam = 0
    private var csiHasDigit = false
    private var csiDiscardSub = false

    // OSC state
    private val oscBuffer = StringBuilder()

    private var lastWritten: String? = null

    // ------------------------------------------------------------------ input

    /** Feed raw bytes from the pty (UTF-8). Thread-safe via the buffer lock. */
    fun feed(bytes: ByteArray, length: Int = bytes.size) {
        synchronized(buffer.lock) {
            for (i in 0 until length) {
                decodeByte(bytes[i].toInt() and 0xff)
            }
        }
    }

    /** Feed already-decoded text (exit messages, local echoes). */
    fun writeText(text: String) {
        synchronized(buffer.lock) { for (c in text) processChar(c) }
    }

    private fun decodeByte(b: Int) {
        when {
            utf8Remaining > 0 -> {
                if (b in 0x80..0xbf) {
                    utf8Value = (utf8Value shl 6) or (b and 0x3f)
                    utf8Remaining--
                    if (utf8Remaining == 0) {
                        val cp = utf8Value
                        utf8Value = 0
                        emitCodePoint(cp)
                    }
                } else {
                    // Invalid continuation: replacement + reprocess this byte.
                    utf8Remaining = 0
                    utf8Value = 0
                    emitCodePoint(0xFFFD)
                    decodeByte(b)
                }
            }
            b < 0x80 -> processChar(b.toChar())
            b in 0xc2..0xdf -> {
                utf8Value = b and 0x1f
                utf8Remaining = 1
            }
            b in 0xe0..0xef -> {
                utf8Value = b and 0x0f
                utf8Remaining = 2
            }
            b in 0xf0..0xf4 -> {
                utf8Value = b and 0x07
                utf8Remaining = 3
            }
            else -> processChar(0xFFFD.toChar())
        }
    }

    private fun emitCodePoint(cp: Int) {
        if (cp <= 0xFFFF) {
            processChar(cp.toChar())
        } else {
            val pair = Character.toChars(cp)
            processString(String(pair))
        }
    }

    private fun processChar(c: Char) {
        when (state) {
            State.GROUND -> groundChar(c)
            State.ESC -> escChar(c)
            State.ESC_CHARSET -> escCharsetChar(c)
            State.CSI -> csiChar(c)
            State.OSC -> oscChar(c)
            State.OSC_ESC ->
                if (c == '\\') {
                    state = State.GROUND
                    oscDispatch()
                } else {
                    // Not a proper ST; treat ESC as a new sequence and drop the OSC payload.
                    state = State.GROUND
                    escChar(c)
                }
            State.IGNORE_UNTIL_ST -> {
                // DCS/SOS/PM/APC: swallow everything until a proper ST (ESC \) or BEL.
                if (c == 0x1b.toChar()) state = State.DCS_ESC
                else if (c.code == 0x07) state = State.GROUND
            }
            State.DCS_ESC -> state = if (c == '\\') State.GROUND else State.IGNORE_UNTIL_ST
        }
    }

    private fun processString(str: String) {
        if (state != State.GROUND) {
            for (c in str) processChar(c)
            return
        }
        buffer.putChar(str, style())
        lastWritten = str
    }

    private fun style(): Int = TerminalBuffer.Attr.pack(curFg, curBg, curFlags)

    // ------------------------------------------------------------------ ground state

    private fun groundChar(c: Char) {
        when (c.code) {
            0x07 -> callbacks.onBell()
            0x08 -> buffer.backspace()
            0x09 -> buffer.tabForward()
            0x0a,
            0x0b,
            0x0c -> {
                if (newlineMode) buffer.carriageReturn()
                buffer.index()
            }
            0x0d -> buffer.carriageReturn()
            0x0e,
            0x0f -> {} // SO/SI charsets are ignored
            0x1a -> {} // SUB cancels nothing in ground state
            0x1b -> state = State.ESC
            else -> if (c.code >= 0x20 && c.code != 0x7f) processString(c.toString())
        }
    }

    // ------------------------------------------------------------------ ESC state

    private fun escChar(c: Char) {
        state = State.GROUND
        when (c) {
            '[' -> beginCsi()
            ']' -> {
                oscBuffer.setLength(0)
                state = State.OSC
            }
            'P',
            'X',
            '^',
            '_' -> state = State.IGNORE_UNTIL_ST
            '7' -> saveCursor()
            '8' -> restoreCursor()
            'D' -> buffer.index()
            'E' -> {
                buffer.carriageReturn()
                buffer.index()
            }
            'M' -> buffer.reverseIndex()
            'c' -> fullReset()
            '=' -> appKeypad = true
            '>' -> appKeypad = false
            '(',
            ')',
            '*',
            '+' -> {
                escLead = c
                state = State.ESC_CHARSET
            }
            '#' -> {
                escLead = c
                state = State.ESC_CHARSET
            }
            '\\' -> {} // stray ST
            else -> {} // unsupported escape — drop
        }
    }

    private fun escCharsetChar(c: Char) {
        state = State.GROUND
        if (escLead == '#' && c == '8') {
            // DECALN — screen alignment test.
            buffer.fillScreen('E')
        }
        // Charset designations (ESC ( B etc.) are ignored — UTF-8 only.
    }

    private fun saveCursor() {
        val slot = if (buffer.usingAlt) buffer.savedAlt else buffer.savedMain
        slot.col = buffer.cursorCol
        slot.row = buffer.cursorRow
        slot.style = style()
    }

    private fun restoreCursor() {
        val slot = if (buffer.usingAlt) buffer.savedAlt else buffer.savedMain
        buffer.moveCursor(slot.col, slot.row)
        curFg = TerminalBuffer.Attr.fg(slot.style)
        curBg = TerminalBuffer.Attr.bg(slot.style)
        curFlags = TerminalBuffer.Attr.flags(slot.style)
        buffer.currentBlankBg = curBg
    }

    private fun beginCsi() {
        csiPrivate = 0.toChar()
        csiIntermediates.setLength(0)
        csiParamCount = 0
        csiCurrentParam = 0
        csiHasDigit = false
        csiDiscardSub = false
        state = State.CSI
    }

    // ------------------------------------------------------------------ CSI state

    private fun csiChar(c: Char) {
        when {
            c in '0'..'9' ->
                if (!csiDiscardSub) {
                    csiCurrentParam = csiCurrentParam * 10 + (c - '0')
                    csiHasDigit = true
                }
            c == ':' -> csiDiscardSub = true
            c == ';' -> {
                pushCsiParam()
                csiDiscardSub = false
            }
            c in '<'..'?' ->
                if (csiParamCount == 0 && !csiHasDigit && csiPrivate == 0.toChar()) {
                    csiPrivate = c
                }
            c in ' '..'/' -> csiIntermediates.append(c)
            c in '@'..'~' -> {
                pushCsiParam()
                state = State.GROUND
                csiDispatch(c)
            }
            c == 0x1b.toChar() -> state = State.ESC
            else -> state = State.GROUND
        }
    }

    private fun pushCsiParam() {
        if (csiParamCount < MAX_PARAMS) {
            csiParams[csiParamCount] = if (csiHasDigit) csiCurrentParam else -1
            csiParamCount++
        }
        csiCurrentParam = 0
        csiHasDigit = false
    }

    /** Parameter with default; -1 stored for empty params. */
    private fun param(i: Int, default: Int): Int =
        if (i < csiParamCount && csiParams[i] >= 0) csiParams[i] else default

    private fun paramOr1(i: Int): Int = param(i, 1).coerceAtLeast(1)

    private fun csiDispatch(final: Char) {
        val hasPrivate = csiPrivate != 0.toChar()
        if (hasPrivate) {
            when (csiPrivate) {
                '?' ->
                    when (final) {
                        'h' -> for (i in 0 until csiParamCount) decSet(csiParams[i])
                        'l' -> for (i in 0 until csiParamCount) decReset(csiParams[i])
                        'c' -> callbacks.onResponse("\u001b[?62;1;6c".toByteArray(Charsets.UTF_8))
                        'n' -> {} // DECRQM not supported
                        's' -> {} // 2026 synchronized output — ignore
                        else -> {}
                    }
                '>' ->
                    if (final == 'c') {
                        callbacks.onResponse("\u001b[>0;276;0c".toByteArray(Charsets.UTF_8))
                    }
                else -> {}
            }
            return
        }
        if (csiIntermediates.isNotEmpty()) {
            when (final) {
                'q' -> {
                    // DECSCUSR: CSI Ps SP q — 0/1 default (blinking block), 2 blink block,
                    // 3 steady block, 4 blink underline, 5 steady underline, 6 blink bar,
                    // 7 steady bar. Steady shapes must never blink (renderer reads
                    // [cursorShapeBlinks]); mis-mapping here made steady-block apps show an
                    // underline cursor, which reads as "the cursor sits in the wrong place".
                    val shape = param(0, 0)
                    cursorShape =
                        when {
                            csiIntermediates.contains(' ') && shape in 2..7 -> shape
                            else -> 0
                        }
                }
                else -> {}
            }
            return
        }
        when (final) {
            '@' -> buffer.insertChars(paramOr1(0))
            'A' -> buffer.cursorUp(paramOr1(0))
            'B' -> buffer.cursorDown(paramOr1(0))
            'b' -> repeatLastChar(paramOr1(0))
            'C' -> buffer.cursorForward(paramOr1(0))
            'D' -> buffer.cursorBackward(paramOr1(0))
            'c' -> callbacks.onResponse("\u001b[?6c".toByteArray(Charsets.UTF_8))
            'd' -> buffer.setCursorRow(param(0, 1) - 1)
            'E' -> {
                buffer.cursorDown(paramOr1(0))
                buffer.setCursorCol(0)
            }
            'F' -> {
                buffer.cursorUp(paramOr1(0))
                buffer.setCursorCol(0)
            }
            'G' -> buffer.setCursorCol(param(0, 1) - 1)
            'H',
            'f' -> buffer.moveCursor(param(1, 1) - 1, param(0, 1) - 1)
            'I' -> repeat(paramOr1(0)) { buffer.tabForward() }
            'J' -> buffer.eraseDisplay(param(0, 0))
            'K' -> buffer.eraseLine(param(0, 0))
            'L' -> buffer.insertLines(paramOr1(0))
            'M' -> buffer.deleteLines(paramOr1(0))
            'm' -> sgr()
            'n' ->
                when (param(0, 0)) {
                    5 -> callbacks.onResponse("\u001b[0n".toByteArray(Charsets.UTF_8))
                    6 -> {
                        val (col, row) = buffer.cursorReport()
                        callbacks.onResponse("\u001b[$row;${col}R".toByteArray(Charsets.UTF_8))
                    }
                    else -> {}
                }
            'P' -> buffer.deleteChars(paramOr1(0))
            'r' -> buffer.setMargins(param(0, 1) - 1, param(1, buffer.rows) - 1)
            'S' -> buffer.scrollRegionUp(paramOr1(0))
            'T' ->
                if (csiParamCount <= 1) {
                    buffer.scrollRegionDown(paramOr1(0))
                }
            's' -> saveCursor()
            't' ->
                if (param(0, 0) == 18) {
                    callbacks.onResponse(
                        "\u001b[8;${buffer.rows};${buffer.cols}t".toByteArray(Charsets.UTF_8)
                    )
                }
            'u' -> restoreCursor()
            'X' -> buffer.eraseChars(paramOr1(0))
            'Z' -> repeat(paramOr1(0)) { buffer.tabBackward() }
            '`' -> buffer.setCursorCol(param(0, 1) - 1)
            'a' -> buffer.cursorForward(paramOr1(0))
            'e' -> buffer.cursorDown(paramOr1(0))
            'g' -> buffer.clearTabStop(param(0, 0))
            'h' ->
                for (i in 0 until csiParamCount) {
                    when (param(i, 0)) {
                        4 -> buffer.insertMode = true
                        20 -> newlineMode = true
                    }
                }
            'l' ->
                for (i in 0 until csiParamCount) {
                    when (param(i, 0)) {
                        4 -> buffer.insertMode = false
                        20 -> newlineMode = false
                    }
                }
            else -> {} // unsupported — ignored
        }
    }

    /**
     * DECSCUSR cursor shape: 0/1 default (blinking block), 2 blink block, 3 steady block,
     * 4 blink underline, 5 steady underline, 6 blink bar, 7 steady bar.
     */
    var cursorShape: Int = 0
        private set

    /**
     * Whether the active DECSCUSR shape is a blinking variant. Steady shapes (3/5/7) never
     * blink — ignoring this made the cursor visibly jump/hide under apps like Neovim, fish
     * and tmux that request a steady cursor.
     */
    fun cursorShapeBlinks(): Boolean =
        when (cursorShape) {
            3, 5, 7 -> false
            else -> true
        }

    private fun repeatLastChar(n: Int) {
        val last = lastWritten ?: return
        repeat(n.coerceAtMost(buffer.cols)) { buffer.putChar(last, style()) }
    }

    // ------------------------------------------------------------------ modes

    private fun decSet(mode: Int) {
        when (mode) {
            1 -> appCursor = true
            3 -> {
                buffer.clearScreenIncludingScrollback()
                buffer.resetMargins()
            }
            6 -> {
                buffer.originMode = true
                buffer.moveCursor(0, 0)
            }
            7 -> buffer.autowrap = true
            12 -> cursorBlinking = false
            25 -> cursorVisible = true
            45 -> {} // reverse wraparound unsupported
            47 -> buffer.switchToAlt(clear = false)
            66 -> appKeypad = true
            in 1000..1003 -> mouseMode = mode
            1004 -> focusReporting = true
            1005,
            1006,
            1015 -> mouseMode = mode
            1047 -> buffer.switchToAlt(clear = true)
            1048 -> saveCursor()
            1049 -> {
                saveCursor()
                buffer.switchToAlt(clear = true)
            }
            2004 -> bracketedPaste = true
        }
    }

    private fun decReset(mode: Int) {
        when (mode) {
            1 -> appCursor = false
            6 -> {
                buffer.originMode = false
                buffer.moveCursor(0, 0)
            }
            7 -> buffer.autowrap = false
            12 -> cursorBlinking = true
            25 -> cursorVisible = false
            47,
            1047 -> buffer.switchToMain()
            66 -> appKeypad = false
            in 1000..1003 -> mouseMode = 0
            1004 -> focusReporting = false
            1005,
            1006,
            1015 -> mouseMode = 0
            1048 -> restoreCursor()
            1049 -> {
                buffer.switchToMain()
                restoreCursor()
            }
            2004 -> bracketedPaste = false
        }
    }

    // ------------------------------------------------------------------ SGR

    private fun sgr() {
        if (csiParamCount == 0) {
            resetStyle()
            return
        }
        var i = 0
        while (i < csiParamCount) {
            when (val p = csiParams[i]) {
                0 -> resetStyle()
                1 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_BOLD
                2 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_DIM
                3 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_ITALIC
                4 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_UNDERLINE
                5,
                6 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_BLINK
                7 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_REVERSE
                8 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_CONCEAL
                9 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_STRIKE
                21 -> curFlags = curFlags or TerminalBuffer.Attr.FLAG_UNDERLINE
                22 ->
                    curFlags =
                        curFlags and
                            (TerminalBuffer.Attr.FLAG_BOLD or TerminalBuffer.Attr.FLAG_DIM).inv()
                23 -> curFlags = curFlags and TerminalBuffer.Attr.FLAG_ITALIC.inv()
                24 -> curFlags = curFlags and TerminalBuffer.Attr.FLAG_UNDERLINE.inv()
                25 -> curFlags = curFlags and TerminalBuffer.Attr.FLAG_BLINK.inv()
                27 -> curFlags = curFlags and TerminalBuffer.Attr.FLAG_REVERSE.inv()
                28 -> curFlags = curFlags and TerminalBuffer.Attr.FLAG_CONCEAL.inv()
                29 -> curFlags = curFlags and TerminalBuffer.Attr.FLAG_STRIKE.inv()
                in 30..37 -> curFg = p - 30
                38,
                48 -> {
                    val color =
                        parseExtColor(
                            i,
                            if (p == 38) TerminalColors.DEFAULT_FG else TerminalColors.DEFAULT_BG
                        )
                    val consumed = color.second
                    if (p == 38) curFg = color.first else curBg = color.first
                    i += consumed
                }
                39 -> curFg = TerminalColors.DEFAULT_FG
                in 40..47 -> curBg = p - 40
                49 -> curBg = TerminalColors.DEFAULT_BG
                in 90..97 -> curFg = p - 90 + 8
                in 100..107 -> curBg = p - 100 + 8
                else -> {} // unknown — ignored
            }
            i++
        }
        buffer.currentBlankBg = curBg
    }

    /**
     * Parse 38/48 extended color at index [i]. Returns the color plus how many extra params were
     * consumed. Handles `38;5;n`, `38;2;r;g;b` (and the colon form flattened by the parser).
     */
    private fun parseExtColor(i: Int, default: Int): Pair<Int, Int> {
        val kind = csiParams.getOrNull(i + 1) ?: return default to 0
        return when {
            kind == 5 -> {
                val n = csiParams.getOrNull(i + 2) ?: -1
                (if (n in 0..255) n else default) to 2
            }
            kind == 2 -> {
                fun comp(idx: Int): Int = csiParams.getOrNull(idx)?.coerceIn(0, 255) ?: 0
                val r = comp(i + 2)
                val g = comp(i + 3)
                val b = comp(i + 4)
                rgbColor(r, g, b) to 4
            }
            else -> default to 0
        }
    }

    private fun rgbColor(r: Int, g: Int, b: Int): Int {
        // Approximate the RGB color inside the 6x6x6 cube for palette consistency; fall back to
        // exact rendering for saturated colors by using a direct-ARGB slot beyond the cube.
        val cubeR = ((r * 5) / 255)
        val cubeG = ((g * 5) / 255)
        val cubeB = ((b * 5) / 255)
        val steps = intArrayOf(0, 95, 135, 175, 215, 255)
        if (steps[cubeR] == r && steps[cubeG] == g && steps[cubeB] == b) {
            return 16 + 36 * cubeR + 6 * cubeG + cubeB
        }
        // Non-cube colors: store in the high sentinel range 300.. which resolve() maps.
        return DIRECT_RGB or ((r / 16) shl 6) or ((g / 16) shl 3) or (b / 16)
    }

    private fun resetStyle() {
        curFg = TerminalColors.DEFAULT_FG
        curBg = TerminalColors.DEFAULT_BG
        curFlags = 0
        buffer.currentBlankBg = curBg
    }

    // ------------------------------------------------------------------ OSC

    private fun oscChar(c: Char) {
        when (c.code) {
            0x07 -> {
                state = State.GROUND
                oscDispatch()
            }
            0x1b -> state = State.OSC_ESC
            else -> {
                if (oscBuffer.length < OSC_MAX) oscBuffer.append(c)
            }
        }
    }

    private fun oscDispatch() {
        val payload = oscBuffer.toString()
        oscBuffer.setLength(0)
        val sep = payload.indexOf(';')
        val numStr = if (sep >= 0) payload.substring(0, sep) else payload
        val number = numStr.toIntOrNull() ?: return
        val body = if (sep >= 0) payload.substring(sep + 1) else ""
        when (number) {
            0,
            1,
            2 -> callbacks.onTitle(body.ifEmpty { "UChat" })
            4 -> setPalette(body)
            104 -> resetPalette(body)
            10 -> {
                if (body == "?") {
                    callbacks.onResponse(
                        "\u001b]10;rgb:90/91/94\u001b\\".toByteArray(Charsets.UTF_8)
                    )
                }
            }
            11 -> {
                if (body == "?") {
                    callbacks.onResponse(
                        "\u001b]11;rgb:0c/0e/14\u001b\\".toByteArray(Charsets.UTF_8)
                    )
                }
            }
            52 -> {
                val semi = body.indexOf(';')
                val data = if (semi >= 0) body.substring(semi + 1) else ""
                if (data.startsWith("=") && data.length > 1) {
                    runCatching {
                        val decoded =
                            String(
                                java.util.Base64.getMimeDecoder().decode(data.substring(1)),
                                Charsets.UTF_8,
                            )
                        callbacks.onClipboard(decoded)
                    }
                }
            }
            else -> {} // 133 shell marks, 8 hyperlinks, etc. — ignored
        }
    }

    private fun setPalette(body: String) {
        val parts = body.split(';')
        if (paletteOverride == null) paletteOverride = IntArray(256)
        var i = 0
        while (i + 1 < parts.size) {
            val idx = parts[i].trim().toIntOrNull()
            val spec = parts[i + 1].trim()
            if (idx != null && idx in 0..255) {
                val color = parseColorSpec(spec)
                if (color != null) paletteOverride!![idx] = color
            }
            i += 2
        }
        buffer.generationBump()
    }

    private fun resetPalette(body: String) {
        val ov = paletteOverride ?: return
        val idx = body.trim().toIntOrNull()
        if (idx == null || idx !in 0..255) {
            paletteOverride = null
        } else {
            // Reset to default palette value: mark with 0 alpha sentinel meaning "unset".
            ov[idx] = 0
        }
        buffer.generationBump()
    }

    /** Parse `rgb:RR/GG/BB`, `#RRGGBB` or `#RGB` color specs. */
    private fun parseColorSpec(spec: String): Int? {
        val s = spec.lowercase()
        return when {
            s.startsWith("rgb:") -> {
                val comps = s.substring(4).split('/')
                if (comps.size != 3) return null
                val r = comps[0].take(2).toIntOrNull(16) ?: return null
                val g = comps[1].take(2).toIntOrNull(16) ?: return null
                val b = comps[2].take(2).toIntOrNull(16) ?: return null
                (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
            s.startsWith("#") -> {
                val hex = s.substring(1)
                when (hex.length) {
                    3 -> {
                        val r = hex[0].digitToIntOrNull(16) ?: return null
                        val g = hex[1].digitToIntOrNull(16) ?: return null
                        val b = hex[2].digitToIntOrNull(16) ?: return null
                        (0xFF000000.toInt()) or (r * 17 shl 16) or (g * 17 shl 8) or b * 17
                    }
                    6 -> hex.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
                    else -> null
                }
            }
            else -> null
        }
    }

    // ------------------------------------------------------------------ host interaction

    /** Reply for focus in/out when the app requested focus reporting (DECSET 1004). */
    fun reportFocus(focused: Boolean) {
        if (focusReporting) {
            callbacks.onResponse(
                if (focused) "\u001b[I".toByteArray() else "\u001b[O".toByteArray()
            )
        }
    }

    fun fullReset() {
        buffer.reset()
        resetStyle()
        state = State.GROUND
        appCursor = false
        appKeypad = false
        bracketedPaste = false
        cursorVisible = true
        cursorBlinking = true
        focusReporting = false
        newlineMode = false
        mouseMode = 0
        cursorShape = 0
        paletteOverride = null
        utf8Remaining = 0
        utf8Value = 0
        lastWritten = null
    }

    fun resize(cols: Int, rows: Int) = buffer.resize(cols, rows)

    companion object {
        private const val MAX_PARAMS = 32
        private const val OSC_MAX = 4096

        /** Sentinel for direct RGB colors packed by [rgbColor]. */
        const val DIRECT_RGB = 300
    }
}
