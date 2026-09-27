package com.uchat.android.terminal.input

import android.view.KeyEvent
import com.uchat.android.terminal.keys.KeySequences
import com.uchat.android.terminal.keys.ModifierKey

/**
 * Maps hardware/bluetooth keyboard [KeyEvent]s to the exact byte sequences a terminal expects.
 *
 * Pure decision logic (no I/O) so it is unit-testable on the JVM. The terminal view calls [encode]
 * from its key-event interceptor; a non-null result means "consumed" — the event must not reach the
 * IME text field.
 *
 * Honours terminal mode flags from the emulator:
 * - application cursor keys (DECCKM): arrows send SS3 instead of CSI
 * - application keypad (ESC =): Home/End/arrows send SS3 variants
 */
object KeyHandler {

    /** Input bundle so the mapper never touches android objects beyond keycodes. */
    data class KeyInfo(
        val keyCode: Int,
        val ctrl: Boolean,
        val alt: Boolean,
        val shift: Boolean,
        val meta: Boolean,
        val repeatCount: Int = 0,
    )

    fun fromEvent(
        keyCode: Int,
        ctrl: Boolean,
        alt: Boolean,
        shift: Boolean,
        meta: Boolean,
        repeatCount: Int = 0,
    ): KeyInfo = KeyInfo(keyCode, ctrl, alt, shift, meta, repeatCount)

    /**
     * Encode a key press. Returns the bytes to write to the pty, or null when the key is not
     * consumed (e.g. plain letters — those flow through the IME text field so every keyboard
     * layout, dead keys and IME composition keep working).
     */
    fun encode(key: KeyInfo, appCursor: Boolean, appKeypad: Boolean): ByteArray? {
        val mods = buildSet {
            if (key.ctrl) add(ModifierKey.CTRL)
            if (key.alt) add(ModifierKey.ALT)
            if (key.shift) add(ModifierKey.SHIFT)
            if (key.meta) add(ModifierKey.META)
        }
        val m = KeySequences.modifierParam(mods)
        val hasMods = mods.isNotEmpty()

        fun csi(final: Char): ByteArray =
            if (hasMods) "\u001b[1;$m$final".toByteArray()
            else if (appCursor) "\u001bO$final".toByteArray() else "\u001b[$final".toByteArray()

        fun tilde(num: String): ByteArray =
            if (hasMods) "\u001b[$num;$m~".toByteArray() else "\u001b[$num~".toByteArray()

        return when (key.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // Alt+Enter sends ESC CR (M-RET convention).
                if (key.alt)
                    byteArrayOf(KeySequences.ESC_BYTE.toByte(), KeySequences.CR_BYTE.toByte())
                else byteArrayOf(KeySequences.CR_BYTE.toByte())
            }
            KeyEvent.KEYCODE_NUMPAD_ENTER -> byteArrayOf(KeySequences.CR_BYTE.toByte())
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(KeySequences.ESC_BYTE.toByte())
            KeyEvent.KEYCODE_TAB ->
                if (key.shift && !key.ctrl && !key.alt) "\u001b[Z".toByteArray()
                else if (key.ctrl || key.alt) {
                    val payload = byteArrayOf(0x09)
                    if (key.alt) byteArrayOf(KeySequences.ESC_BYTE.toByte()) + payload else payload
                } else byteArrayOf(0x09)
            KeyEvent.KEYCODE_DEL -> {
                // Backspace: DEL 0x7f (CTRL+BS → 0x08, historical).
                if (key.ctrl) byteArrayOf(0x08) else byteArrayOf(0x7f)
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> tilde("3")
            KeyEvent.KEYCODE_INSERT -> tilde("2")
            KeyEvent.KEYCODE_DPAD_UP -> csi('A')
            KeyEvent.KEYCODE_DPAD_DOWN -> csi('B')
            KeyEvent.KEYCODE_DPAD_RIGHT -> csi('C')
            KeyEvent.KEYCODE_DPAD_LEFT -> csi('D')
            KeyEvent.KEYCODE_MOVE_HOME ->
                if (hasMods) "\u001b[1;${m}H".toByteArray()
                else if (appCursor || appKeypad) "\u001bOH".toByteArray()
                else "\u001b[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END ->
                if (hasMods) "\u001b[1;${m}F".toByteArray()
                else if (appCursor || appKeypad) "\u001bOF".toByteArray()
                else "\u001b[F".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP -> tilde("5")
            KeyEvent.KEYCODE_PAGE_DOWN -> tilde("6")
            KeyEvent.KEYCODE_F1 -> functionKey(1, m, hasMods)
            KeyEvent.KEYCODE_F2 -> functionKey(2, m, hasMods)
            KeyEvent.KEYCODE_F3 -> functionKey(3, m, hasMods)
            KeyEvent.KEYCODE_F4 -> functionKey(4, m, hasMods)
            KeyEvent.KEYCODE_F5 -> functionKey(5, m, hasMods)
            KeyEvent.KEYCODE_F6 -> functionKey(6, m, hasMods)
            KeyEvent.KEYCODE_F7 -> functionKey(7, m, hasMods)
            KeyEvent.KEYCODE_F8 -> functionKey(8, m, hasMods)
            KeyEvent.KEYCODE_F9 -> functionKey(9, m, hasMods)
            KeyEvent.KEYCODE_F10 -> functionKey(10, m, hasMods)
            KeyEvent.KEYCODE_F11 -> functionKey(11, m, hasMods)
            KeyEvent.KEYCODE_F12 -> functionKey(12, m, hasMods)
            KeyEvent.KEYCODE_SPACE ->
                // CTRL+Space → NUL (a genuinely used binding in shells).
                if (key.ctrl) byteArrayOf(0x00)
                else if (key.alt) byteArrayOf(KeySequences.ESC_BYTE.toByte(), 0x20) else null
            KeyEvent.KEYCODE_BREAK -> byteArrayOf(0x03)
            else -> encodeCtrlSymbol(key, m) ?: encodeLetter(key, mods)
        }
    }

    private fun functionKey(number: Int, m: Int, hasMods: Boolean): ByteArray? =
        when (number) {
            1,
            2,
            3,
            4 ->
                if (!hasMods) "\u001bO${'P' + number - 1}".toByteArray()
                else "\u001b[1;$m${'P' + number - 1}".toByteArray()
            else -> {
                val codes =
                    mapOf(
                        5 to "15",
                        6 to "17",
                        7 to "18",
                        8 to "19",
                        9 to "20",
                        10 to "21",
                        11 to "23",
                        12 to "24"
                    )
                codes[number]?.let { num ->
                    if (hasMods) "\u001b[$num;$m~".toByteArray() else "\u001b[$num~".toByteArray()
                }
            }
        }

    /** Historical CTRL bindings on punctuation. */
    private fun encodeCtrlSymbol(key: KeyInfo, m: Int): ByteArray? {
        if (!key.ctrl) return null
        val code =
            when (key.keyCode) {
                KeyEvent.KEYCODE_AT,
                KeyEvent.KEYCODE_2 -> 0x00
                KeyEvent.KEYCODE_LEFT_BRACKET,
                KeyEvent.KEYCODE_3 -> 0x1b
                KeyEvent.KEYCODE_BACKSLASH,
                KeyEvent.KEYCODE_4 -> 0x1c
                KeyEvent.KEYCODE_RIGHT_BRACKET,
                KeyEvent.KEYCODE_5 -> 0x1d
                KeyEvent.KEYCODE_6 -> 0x1e
                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_7,
                KeyEvent.KEYCODE_MINUS -> 0x1f
                KeyEvent.KEYCODE_8 -> 0x7f
                else -> return null
            }
        val payload = byteArrayOf(code.toByte())
        return if (key.alt) byteArrayOf(KeySequences.ESC_BYTE.toByte()) + payload else payload
    }

    /** CTRL/ALT-modified letters (A-Z). Plain letters return null → IME path handles them. */
    private fun encodeLetter(key: KeyInfo, mods: Set<ModifierKey>): ByteArray? {
        val isLetter = key.keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
        if (!isLetter) return null
        if (ModifierKey.CTRL !in mods && ModifierKey.ALT !in mods) return null
        val letter = 'a' + (key.keyCode - KeyEvent.KEYCODE_A)
        val bytes = mutableListOf<Byte>()
        if (ModifierKey.CTRL in mods) {
            KeySequences.ctrlCode(letter)?.let { bytes.add(it.toByte()) }
        } else {
            bytes.addAll(letter.toString().toByteArray(Charsets.UTF_8).map { it.toByte() })
        }
        if (ModifierKey.ALT in mods) bytes.add(0, KeySequences.ESC_BYTE.toByte())
        return bytes.toByteArray()
    }
}
