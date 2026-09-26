package com.uchat.android

import com.uchat.android.terminal.keys.ExtraKey
import com.uchat.android.terminal.keys.ExtraKeyType
import com.uchat.android.terminal.keys.KeySequences
import com.uchat.android.terminal.keys.ModifierKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeySequencesTest {

    private fun key(
        type: ExtraKeyType,
        text: String = "",
        modifiers: List<ModifierKey> = emptyList(),
    ) = ExtraKey(id = "k", label = "K", type = type, text = text, modifiers = modifiers)

    // ---------------------------------------------------------------- text

    @Test
    fun `plain text encodes as UTF-8`() {
        assertArrayEquals("ls\n".toByteArray(), KeySequences.encodeText("ls\n"))
    }

    @Test
    fun `unicode text encodes as UTF-8`() {
        assertArrayEquals("héllo→".toByteArray(Charsets.UTF_8), KeySequences.encodeText("héllo→"))
    }

    @Test
    fun `alt prefixes escape byte`() {
        assertArrayEquals(
            byteArrayOf(0x1b, 'x'.code.toByte()),
            KeySequences.encodeText("x", setOf(ModifierKey.ALT)),
        )
    }

    @Test
    fun `ctrl letter maps to control code`() {
        assertArrayEquals(
            byteArrayOf(0x03),
            KeySequences.encodeText("c", setOf(ModifierKey.CTRL)),
        )
        assertArrayEquals(
            byteArrayOf(0x01),
            KeySequences.encodeText("A", setOf(ModifierKey.CTRL)),
        )
    }

    @Test
    fun `ctrl symbol mappings follow historical terminals`() {
        assertEquals(0x00, KeySequences.ctrlCode('2'))
        assertEquals(0x1b, KeySequences.ctrlCode('['))
        assertEquals(0x1f, KeySequences.ctrlCode('_'))
        assertEquals(0x7f, KeySequences.ctrlCode('?'))
        assertNull(KeySequences.ctrlCode('%'))
    }

    @Test
    fun `ctrl with multi char text falls back to plain text`() {
        assertArrayEquals(
            "ls".toByteArray(),
            KeySequences.encodeText("ls", setOf(ModifierKey.CTRL)),
        )
    }

    // ---------------------------------------------------------------- terminal keys

    @Test
    fun `arrows send CSI sequences`() {
        assertArrayEquals("\u001b[A".toByteArray(), KeySequences.encodeTerminalKey("UP"))
        assertArrayEquals("\u001b[B".toByteArray(), KeySequences.encodeTerminalKey("DOWN"))
        assertArrayEquals("\u001b[D".toByteArray(), KeySequences.encodeTerminalKey("LEFT"))
        assertArrayEquals("\u001b[C".toByteArray(), KeySequences.encodeTerminalKey("RIGHT"))
    }

    @Test
    fun `ctrl up sends xterm modifier parameter`() {
        assertArrayEquals(
            "\u001b[1;5A".toByteArray(),
            KeySequences.encodeTerminalKey("UP", setOf(ModifierKey.CTRL))
        )
    }

    @Test
    fun `ctrl alt up combines modifier parameters`() {
        assertArrayEquals(
            "\u001b[1;7A".toByteArray(),
            KeySequences.encodeTerminalKey("UP", setOf(ModifierKey.CTRL, ModifierKey.ALT)),
        )
    }

    @Test
    fun `navigation keys`() {
        assertArrayEquals("\u001b[H".toByteArray(), KeySequences.encodeTerminalKey("HOME"))
        assertArrayEquals("\u001b[F".toByteArray(), KeySequences.encodeTerminalKey("END"))
        assertArrayEquals("\u001b[5~".toByteArray(), KeySequences.encodeTerminalKey("PGUP"))
        assertArrayEquals("\u001b[6~".toByteArray(), KeySequences.encodeTerminalKey("PGDN"))
        assertArrayEquals("\u001b[3~".toByteArray(), KeySequences.encodeTerminalKey("DEL"))
    }

    @Test
    fun `control keys`() {
        assertArrayEquals(byteArrayOf(0x1b), KeySequences.encodeTerminalKey("ESC"))
        assertArrayEquals(byteArrayOf(0x09), KeySequences.encodeTerminalKey("TAB"))
        assertArrayEquals(byteArrayOf(0x0d), KeySequences.encodeTerminalKey("ENTER"))
        assertArrayEquals(byteArrayOf(0x7f), KeySequences.encodeTerminalKey("BACKSPACE"))
    }

    @Test
    fun `f keys use SS3 without modifiers`() {
        assertArrayEquals("\u001bOP".toByteArray(), KeySequences.encodeTerminalKey("F1"))
        assertArrayEquals("\u001b[15~".toByteArray(), KeySequences.encodeTerminalKey("F5"))
        assertArrayEquals("\u001b[24~".toByteArray(), KeySequences.encodeTerminalKey("F12"))
    }

    // ---------------------------------------------------------------- key types

    @Test
    fun `modifier key sends nothing`() {
        assertEquals(0, KeySequences.encode(key(ExtraKeyType.MODIFIER)).size)
    }

    @Test
    fun `command appends carriage return`() {
        assertArrayEquals(
            "git status\r".toByteArray(),
            KeySequences.encode(key(ExtraKeyType.COMMAND, "git status")),
        )
    }

    @Test
    fun `escape sequence type parses user escapes`() {
        assertArrayEquals(
            "\u001b[5~".toByteArray(),
            KeySequences.encode(key(ExtraKeyType.ESCAPE_SEQ, "\\e[5~")),
        )
        assertArrayEquals(
            "\u001b[1;5A".toByteArray(),
            KeySequences.encode(key(ExtraKeyType.ESCAPE_SEQ, "\\x1b[1;5A")),
        )
        assertArrayEquals(
            byteArrayOf(0x1b),
            KeySequences.encode(key(ExtraKeyType.ESCAPE_SEQ, "\\033")),
        )
    }

    @Test
    fun `combo ctrl c sends interrupt`() {
        val combo = key(ExtraKeyType.COMBO, "c", listOf(ModifierKey.CTRL))
        assertArrayEquals(byteArrayOf(0x03), KeySequences.encode(combo))
    }

    @Test
    fun `combo with terminal key base gets modifier parameter`() {
        val combo = key(ExtraKeyType.COMBO, "up", listOf(ModifierKey.CTRL))
        assertArrayEquals("\u001b[1;5A".toByteArray(), KeySequences.encode(combo))
    }

    @Test
    fun `sticky modifiers apply to text keys`() {
        val text = key(ExtraKeyType.TEXT, "c")
        assertArrayEquals(
            byteArrayOf(0x03),
            KeySequences.encode(text, setOf(ModifierKey.CTRL)),
        )
    }

    @Test
    fun `sticky modifiers apply to terminal keys`() {
        val up = key(ExtraKeyType.TERMINAL_KEY, "UP")
        assertArrayEquals(
            "\u001b[1;5A".toByteArray(),
            KeySequences.encode(up, setOf(ModifierKey.CTRL)),
        )
    }

    @Test
    fun `escape seq and command ignore sticky modifiers`() {
        val seq = key(ExtraKeyType.ESCAPE_SEQ, "\\e[5~")
        assertArrayEquals(
            "\u001b[5~".toByteArray(),
            KeySequences.encode(seq, setOf(ModifierKey.CTRL)),
        )
        val cmd = key(ExtraKeyType.COMMAND, "clear")
        assertArrayEquals(
            "clear\r".toByteArray(),
            KeySequences.encode(cmd, setOf(ModifierKey.CTRL))
        )
    }

    // ---------------------------------------------------------------- long press

    @Test
    fun `long press payload encodes`() {
        val k =
            ExtraKey(
                id = "k",
                label = "GIT",
                type = ExtraKeyType.TEXT,
                text = "git",
                longPressText = "git status",
            )
        assertArrayEquals("git status".toByteArray(), KeySequences.encodeLongPress(k))
        assertNull(KeySequences.encodeLongPress(key(ExtraKeyType.TEXT, "x")))
    }

    @Test
    fun `terminal key list contains expected names`() {
        listOf("ESC", "TAB", "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN", "F12")
            .forEach { name -> assert(name in KeySequences.TERMINAL_KEYS) }
    }
}
