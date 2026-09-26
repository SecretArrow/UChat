package com.uchat.android.terminal.keys

/**
 * Layer 1 support: turns extra keys + modifier state into the exact byte sequences a real terminal
 * expects. Pure Kotlin (no Android imports) so it is fully unit-testable.
 *
 * Encoding follows xterm conventions:
 * - Arrows/Home/End: CSI 1;<m>A..H (no modifiers → CSI A etc.)
 * - PgUp/PgDn/Ins/Del/F5+: CSI <n>;<m>~
 * - F1..F4: SS3 P..S (with modifiers: CSI 1;<m>P..S)
 * - CTRL+letter: 0x01..0x1A, CTRL+@ → NUL, CTRL+? → DEL, CTRL+space → NUL, historical digit forms
 * - ALT: ESC prefix before the payload
 * - Modifier parameter m = 1 + shift(1) + alt(2) + ctrl(4)
 */
object KeySequences {

    const val ESC_BYTE = 0x1b
    const val CR_BYTE = 0x0d

    /** Canonical terminal key names accepted by [ExtraKeyType.TERMINAL_KEY] and COMBO bases. */
    val TERMINAL_KEYS: List<String> =
        listOf(
            "ESC",
            "TAB",
            "ENTER",
            "BACKSPACE",
            "SPACE",
            "UP",
            "DOWN",
            "LEFT",
            "RIGHT",
            "HOME",
            "END",
            "PGUP",
            "PGDN",
            "INS",
            "DEL",
            "F1",
            "F2",
            "F3",
            "F4",
            "F5",
            "F6",
            "F7",
            "F8",
            "F9",
            "F10",
            "F11",
            "F12",
        )

    /** Friendly display label for a canonical terminal key name. */
    fun displayLabel(keyName: String): String =
        when (keyName) {
            "ESC" -> "Esc"
            "TAB" -> "Tab"
            "ENTER" -> "⏎"
            "BACKSPACE" -> "⌫"
            "SPACE" -> "Space"
            "UP" -> "↑"
            "DOWN" -> "↓"
            "LEFT" -> "←"
            "RIGHT" -> "→"
            "HOME" -> "Home"
            "END" -> "End"
            "PGUP" -> "PgUp"
            "PGDN" -> "PgDn"
            "INS" -> "Ins"
            "DEL" -> "Del"
            else -> keyName
        }

    /** Encode [key] with the currently sticky [activeModifiers] applied. */
    fun encode(key: ExtraKey, activeModifiers: Set<ModifierKey> = emptySet()): ByteArray {
        val mods: Set<ModifierKey> =
            if (
                key.type == ExtraKeyType.MODIFIER ||
                    key.type == ExtraKeyType.ESCAPE_SEQ ||
                    key.type == ExtraKeyType.COMMAND
            ) {
                // Explicit payload types ignore sticky modifiers.
                if (key.type == ExtraKeyType.COMBO) key.modifiers.toSet() else emptySet()
            } else {
                (key.modifiers + activeModifiers).toSet()
            }
        return when (key.type) {
            ExtraKeyType.MODIFIER -> ByteArray(0)
            ExtraKeyType.COMMAND -> encodeText(key.text, emptySet()) + byteArrayOf(CR_BYTE.toByte())
            ExtraKeyType.ESCAPE_SEQ -> parseEscapeSequence(key.text)
            ExtraKeyType.TERMINAL_KEY -> encodeTerminalKey(key.text, mods)
            ExtraKeyType.COMBO -> encodeComboBase(key.text, mods)
            ExtraKeyType.TEXT -> encodeText(key.text, mods)
        }
    }

    /** Encode the long-press payload of a key, if any. */
    fun encodeLongPress(key: ExtraKey): ByteArray? {
        val raw = key.longPressText ?: return null
        return when (key.type) {
            ExtraKeyType.ESCAPE_SEQ -> parseEscapeSequence(raw)
            ExtraKeyType.COMMAND -> encodeText(raw, emptySet()) + byteArrayOf(CR_BYTE.toByte())
            else -> encodeText(raw, emptySet())
        }
    }

    /**
     * Encode [text] with [mods] applied. CTRL applies only to single-character payloads (standard
     * terminal behaviour); ALT prefixes ESC regardless of length.
     */
    fun encodeText(text: String, mods: Set<ModifierKey> = emptySet()): ByteArray {
        if (text.isEmpty()) return ByteArray(0)
        val bytes = mutableListOf<Byte>()
        if (ModifierKey.ALT in mods) bytes.add(ESC_BYTE.toByte())
        if (ModifierKey.CTRL in mods && text.length == 1) {
            ctrlCode(text[0])?.let { code ->
                bytes.add(code.toByte())
                return bytes.toByteArray()
            }
        }
        bytes.addAll(text.toByteArray(Charsets.UTF_8).map { it.toByte() })
        return bytes.toByteArray()
    }

    /** Historical CTRL mapping: a-z → 1..26, plus the classic control chars on symbols. */
    fun ctrlCode(c: Char): Int? {
        val lower = c.lowercaseChar()
        return when {
            lower in 'a'..'z' -> lower.code - 'a'.code + 1
            c == '2' || c == '@' || c == ' ' -> 0x00
            c == '3' || c == '[' -> 0x1b
            c == '4' || c == '\\' -> 0x1c
            c == '5' || c == ']' -> 0x1d
            c == '6' || c == '^' -> 0x1e
            c == '7' || c == '/' || c == '_' -> 0x1f
            c == '8' || c == '?' -> 0x7f
            else -> null
        }
    }

    /** Encode a canonical terminal key name with optional modifiers. */
    fun encodeTerminalKey(keyName: String, mods: Set<ModifierKey> = emptySet()): ByteArray {
        val hasMods = mods.isNotEmpty()
        val m = modifierParam(mods)
        val seq = StringBuilder("\u001b[")
        when (keyName.uppercase()) {
            "ESC" -> return byteArrayOf(ESC_BYTE.toByte())
            "TAB" -> return byteArrayOf(0x09)
            "ENTER" -> return byteArrayOf(CR_BYTE.toByte())
            "BACKSPACE" -> return byteArrayOf(0x7f)
            "SPACE" -> return byteArrayOf(0x20)
            "UP" -> appendParam(seq, "A", m, hasMods)
            "DOWN" -> appendParam(seq, "B", m, hasMods)
            "RIGHT" -> appendParam(seq, "C", m, hasMods)
            "LEFT" -> appendParam(seq, "D", m, hasMods)
            "HOME" -> appendParam(seq, "H", m, hasMods)
            "END" -> appendParam(seq, "F", m, hasMods)
            "PGUP" -> appendTilde(seq, "5", m, hasMods)
            "PGDN" -> appendTilde(seq, "6", m, hasMods)
            "INS" -> appendTilde(seq, "2", m, hasMods)
            "DEL" -> appendTilde(seq, "3", m, hasMods)
            "F1" -> return functionKey('P', m, hasMods)
            "F2" -> return functionKey('Q', m, hasMods)
            "F3" -> return functionKey('R', m, hasMods)
            "F4" -> return functionKey('S', m, hasMods)
            "F5" -> appendTilde(seq, "15", m, hasMods)
            "F6" -> appendTilde(seq, "17", m, hasMods)
            "F7" -> appendTilde(seq, "18", m, hasMods)
            "F8" -> appendTilde(seq, "19", m, hasMods)
            "F9" -> appendTilde(seq, "20", m, hasMods)
            "F10" -> appendTilde(seq, "21", m, hasMods)
            "F11" -> appendTilde(seq, "23", m, hasMods)
            "F12" -> appendTilde(seq, "24", m, hasMods)
            else -> return encodeText(keyName, mods)
        }
        return seq.toString().toByteArray(Charsets.UTF_8)
    }

    private fun appendParam(seq: StringBuilder, final: String, m: Int, hasMods: Boolean) {
        if (hasMods) seq.append("1;").append(m)
        seq.append(final)
    }

    private fun appendTilde(seq: StringBuilder, number: String, m: Int, hasMods: Boolean) {
        seq.append(number)
        if (hasMods) seq.append(';').append(m)
        seq.append('~')
    }

    private fun functionKey(ss3: Char, m: Int, hasMods: Boolean): ByteArray =
        if (!hasMods) {
            "\u001bO$ss3".toByteArray(Charsets.UTF_8)
        } else {
            "\u001b[1;$m$ss3".toByteArray(Charsets.UTF_8)
        }

    /** xterm modifier parameter: 1 + shift(1) + alt(2) + ctrl(4). */
    fun modifierParam(mods: Set<ModifierKey>): Int =
        1 +
            (if (ModifierKey.SHIFT in mods) 1 else 0) +
            (if (ModifierKey.ALT in mods) 2 else 0) +
            (if (ModifierKey.CTRL in mods) 4 else 0)

    /** COMBO base: the text may be a terminal key name or a character. */
    private fun encodeComboBase(text: String, mods: Set<ModifierKey>): ByteArray {
        val base = text.trim()
        if (base.isEmpty()) return ByteArray(0)
        return if (base.uppercase() in TERMINAL_KEYS && base.length > 1) {
            encodeTerminalKey(base.uppercase(), mods)
        } else {
            encodeText(base, mods)
        }
    }

    /**
     * Parse a user-written escape sequence into raw bytes. Supported escapes: \e \E \033 (octal)
     * \xHH \uXXXX \n \r \t \0 \\ — everything else is copied literally as UTF-8.
     */
    fun parseEscapeSequence(raw: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\') {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
                continue
            }
            if (i + 1 >= raw.length) {
                out.write('\\'.code)
                break
            }
            when (val n = raw[i + 1]) {
                'e',
                'E' -> {
                    out.write(ESC_BYTE)
                    i += 2
                }
                'n' -> {
                    out.write(0x0a)
                    i += 2
                }
                'r' -> {
                    out.write(0x0d)
                    i += 2
                }
                't' -> {
                    out.write(0x09)
                    i += 2
                }
                '0' -> {
                    // Octal escape: \0 → NUL, \033 → ESC (up to 3 octal digits).
                    val oct = raw.drop(i + 1).takeWhile { it in '0'..'7' }.take(3)
                    out.write(oct.toInt(8).and(0xff))
                    i += 1 + oct.length
                }
                '\\' -> {
                    out.write('\\'.code)
                    i += 2
                }
                'x',
                'X' -> {
                    val hex = raw.drop(i + 2).takeWhile { it.isHexDigit() }
                    if (hex.isEmpty()) {
                        out.write(n.code)
                        i += 2
                    } else {
                        out.write(hex.toInt(16).and(0xff))
                        i += 2 + hex.length
                    }
                }
                'u' -> {
                    val hex = raw.drop(i + 2).takeWhile { it.isHexDigit() }
                    if (hex.length >= 4) {
                        val cp = hex.take(4).toInt(16)
                        out.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                        i += 2 + 4
                    } else {
                        out.write(n.code)
                        i += 2
                    }
                }
                in '1'..'9' -> {
                    // Defensive: plain digits after backslash are kept literally.
                    out.write(n.code)
                    i += 2
                }
                else -> {
                    out.write(n.code)
                    i += 2
                }
            }
        }
        return out.toByteArray()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
