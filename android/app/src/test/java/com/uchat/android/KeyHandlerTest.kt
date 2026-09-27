package com.uchat.android

import android.view.KeyEvent
import com.uchat.android.terminal.input.KeyHandler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Key mapping tests: hardware/bluetooth keyboard events → exact terminal byte sequences. */
class KeyHandlerTest {

    private fun key(
        code: Int,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
    ): KeyHandler.KeyInfo = KeyHandler.fromEvent(code, ctrl, alt, shift, meta)

    private fun b(vararg ints: Int): ByteArray = ByteArray(ints.size) { ints[it].toByte() }

    @Test
    fun `enter sends CR`() {
        assertArrayEquals(b(0x0d), KeyHandler.encode(key(KeyEvent.KEYCODE_ENTER), false, false))
    }

    @Test
    fun `escape sends ESC`() {
        assertArrayEquals(b(0x1b), KeyHandler.encode(key(KeyEvent.KEYCODE_ESCAPE), false, false))
    }

    @Test
    fun `arrows send CSI in normal mode`() {
        assertArrayEquals(
            "\u001b[A".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DPAD_UP), false, false)
        )
        assertArrayEquals(
            "\u001b[B".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DPAD_DOWN), false, false)
        )
        assertArrayEquals(
            "\u001b[C".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DPAD_RIGHT), false, false)
        )
        assertArrayEquals(
            "\u001b[D".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DPAD_LEFT), false, false)
        )
    }

    @Test
    fun `arrows send SS3 in application cursor mode`() {
        assertArrayEquals(
            "\u001bOA".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DPAD_UP), true, false)
        )
    }

    @Test
    fun `ctrl up sends modified csi`() {
        assertArrayEquals(
            "\u001b[1;5A".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DPAD_UP, ctrl = true), false, false)
        )
    }

    @Test
    fun `backspace sends DEL`() {
        assertArrayEquals(b(0x7f), KeyHandler.encode(key(KeyEvent.KEYCODE_DEL), false, false))
    }

    @Test
    fun `ctrl backspace sends BS`() {
        assertArrayEquals(
            b(0x08),
            KeyHandler.encode(key(KeyEvent.KEYCODE_DEL, ctrl = true), false, false)
        )
    }

    @Test
    fun `ctrl letters send control chars`() {
        assertArrayEquals(
            b(0x03),
            KeyHandler.encode(key(KeyEvent.KEYCODE_C, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x04),
            KeyHandler.encode(key(KeyEvent.KEYCODE_D, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x1a),
            KeyHandler.encode(key(KeyEvent.KEYCODE_Z, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x0c),
            KeyHandler.encode(key(KeyEvent.KEYCODE_L, ctrl = true), false, false)
        )
    }

    @Test
    fun `ctrl alt letters send esc prefixed control chars`() {
        assertArrayEquals(
            b(0x1b, 0x03),
            KeyHandler.encode(key(KeyEvent.KEYCODE_C, ctrl = true, alt = true), false, false),
        )
    }

    @Test
    fun `plain letters are not consumed`() {
        assertNull(KeyHandler.encode(key(KeyEvent.KEYCODE_A), false, false))
        assertNull(KeyHandler.encode(key(KeyEvent.KEYCODE_Q, shift = true), false, false))
    }

    @Test
    fun `ctrl space sends NUL`() {
        assertArrayEquals(
            b(0x00),
            KeyHandler.encode(key(KeyEvent.KEYCODE_SPACE, ctrl = true), false, false)
        )
    }

    @Test
    fun `shift tab sends CSI Z`() {
        assertArrayEquals(
            "\u001b[Z".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_TAB, shift = true), false, false)
        )
    }

    @Test
    fun `page keys send tilde sequences`() {
        assertArrayEquals(
            "\u001b[5~".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_PAGE_UP), false, false)
        )
        assertArrayEquals(
            "\u001b[6~".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_PAGE_DOWN), false, false)
        )
        assertArrayEquals(
            "\u001b[3~".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_FORWARD_DEL), false, false)
        )
        assertArrayEquals(
            "\u001b[2~".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_INSERT), false, false)
        )
    }

    @Test
    fun `home end respect application mode`() {
        assertArrayEquals(
            "\u001b[H".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_MOVE_HOME), false, false)
        )
        assertArrayEquals(
            "\u001bOH".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_MOVE_HOME), true, false)
        )
        assertArrayEquals(
            "\u001b[F".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_MOVE_END), false, false)
        )
    }

    @Test
    fun `function keys map to xterm codes`() {
        assertArrayEquals(
            "\u001bOP".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_F1), false, false)
        )
        assertArrayEquals(
            "\u001b[15~".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_F5), false, false)
        )
        assertArrayEquals(
            "\u001b[24~".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_F12), false, false)
        )
        assertArrayEquals(
            "\u001b[1;5P".toByteArray(),
            KeyHandler.encode(key(KeyEvent.KEYCODE_F1, ctrl = true), false, false)
        )
    }

    @Test
    fun `ctrl punctuation historical bindings`() {
        assertArrayEquals(
            b(0x1b),
            KeyHandler.encode(key(KeyEvent.KEYCODE_LEFT_BRACKET, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x1c),
            KeyHandler.encode(key(KeyEvent.KEYCODE_BACKSLASH, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x1d),
            KeyHandler.encode(key(KeyEvent.KEYCODE_RIGHT_BRACKET, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x1f),
            KeyHandler.encode(key(KeyEvent.KEYCODE_SLASH, ctrl = true), false, false)
        )
        assertArrayEquals(
            b(0x7f),
            KeyHandler.encode(key(KeyEvent.KEYCODE_8, ctrl = true), false, false)
        )
    }

    @Test
    fun `modifier param math matches xterm`() {
        assertEquals(
            6,
            KeyHandler.encode(
                    key(KeyEvent.KEYCODE_DPAD_UP, ctrl = true, shift = true),
                    false,
                    false
                )!!
                .let { String(it) }
                .let { it[4] - '0' }
        )
    }

    @Test
    fun `unknown keys are not consumed`() {
        assertNull(KeyHandler.encode(key(KeyEvent.KEYCODE_MUTE), false, false))
        assertTrue(true)
    }

    @Test
    fun `meta key is exposed via encode path`() {
        // META alone on a non-letter key: not consumed.
        assertFalse(
            KeyHandler.encode(key(KeyEvent.KEYCODE_1, meta = true), false, false) != null,
        )
    }
}
