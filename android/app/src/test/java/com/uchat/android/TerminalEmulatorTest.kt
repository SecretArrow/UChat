package com.uchat.android

import com.uchat.android.terminal.emulator.TerminalBuffer
import com.uchat.android.terminal.emulator.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Behaviour tests for the native VT/xterm emulator core (layer 1 + 2). */
class TerminalEmulatorTest {

    private class Term(cols: Int = 20, rows: Int = 6, scrollback: Int = 100) {
        val buffer = TerminalBuffer(cols, rows, scrollback)
        val emulator = TerminalEmulator(buffer)
        var title: String? = null
        var responses = StringBuilder()
        var bell = 0

        init {
            emulator.callbacks.onTitle = { title = it }
            emulator.callbacks.onResponse = { b -> responses.append(String(b, Charsets.UTF_8)) }
            emulator.callbacks.onBell = { bell++ }
        }

        fun feed(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            emulator.feed(bytes, bytes.size)
        }

        fun screen(): List<String> =
            buffer.currentScreen.map { line ->
                line.chars.filter { it.code != 0 }.joinToString("").trimEnd(' ')
            }

        fun row(r: Int): String = screen()[r]
    }

    // ------------------------------------------------------------ plain text

    @Test
    fun `plain text lands at cursor`() {
        val t = Term()
        t.feed("hello")
        assertEquals("hello", t.row(0))
    }

    @Test
    fun `cr lf moves to next line column zero`() {
        val t = Term()
        t.feed("abc\r\ndef")
        assertEquals("abc", t.row(0))
        assertEquals("def", t.row(1))
    }

    @Test
    fun `bare lf keeps column`() {
        val t = Term()
        t.feed("abc\ndef")
        assertEquals("abc", t.row(0))
        assertEquals("   def", t.row(1))
    }

    @Test
    fun `backspace moves cursor left`() {
        val t = Term()
        t.feed("ab\u0008X")
        assertEquals("aX", t.row(0))
    }

    @Test
    fun `tab advances to stop`() {
        val t = Term()
        t.feed("a\tb")
        assertEquals("a       b", t.row(0))
    }

    @Test
    fun `bell callback fires`() {
        val t = Term()
        t.feed("x\u0007y")
        assertEquals(1, t.bell)
    }

    // ------------------------------------------------------------ wrapping & wide chars

    @Test
    fun `autowrap wraps to next line`() {
        val t = Term(cols = 5, rows = 3)
        t.feed("abcdef")
        assertEquals("abcde", t.row(0))
        assertEquals("f", t.row(1))
    }

    @Test
    fun `wide chars take two cells and align`() {
        val t = Term(cols = 8, rows = 2)
        t.feed("a世b")
        val line = t.buffer.currentScreen[0]
        assertEquals('a', line.chars[0])
        assertEquals('世', line.chars[1])
        assertEquals(0, line.chars[2].code) // spacer
        assertEquals('b', line.chars[3])
    }

    @Test
    fun `overwriting wide char cleans spacer`() {
        val t = Term(cols = 8, rows = 2)
        t.feed("世")
        t.feed("\u001b[1;1HX")
        val line = t.buffer.currentScreen[0]
        assertEquals('X', line.chars[0])
        assertEquals(' ', line.chars[1])
    }

    @Test
    fun `utf8 split across feeds decodes correctly`() {
        val t = Term()
        val bytes = "héllo世界".toByteArray(Charsets.UTF_8)
        t.emulator.feed(bytes, 2) // "h" + first byte of é
        val rest = bytes.copyOfRange(2, bytes.size)
        t.emulator.feed(rest, rest.size)
        assertTrue(t.row(0).startsWith("héllo世界"))
    }

    // ------------------------------------------------------------ scrolling

    @Test
    fun `scrolling past bottom pushes scrollback`() {
        val t = Term(cols = 10, rows = 3, scrollback = 100)
        t.feed("one\r\ntwo\r\nthree\r\nfour")
        assertEquals(1, t.buffer.scrollback.size)
        assertEquals("one", t.buffer.scrollback.first().chars.joinToString("").trimEnd())
        assertEquals("two", t.row(0))
        assertEquals("four", t.row(2))
    }

    @Test
    fun `scrollback is bounded`() {
        val t = Term(cols = 10, rows = 3, scrollback = 5)
        repeat(50) { t.feed("line $it\r\n") }
        assertTrue(t.buffer.scrollback.size <= 5)
    }

    @Test
    fun `alt screen never writes scrollback`() {
        val t = Term(cols = 10, rows = 3)
        t.feed("\u001b[?1049h")
        repeat(10) { t.feed("row $it\r\n") }
        assertEquals(0, t.buffer.scrollback.size)
        t.feed("\u001b[?1049l")
        assertEquals("", t.row(0))
    }

    // ------------------------------------------------------------ SGR

    @Test
    fun `sgr sets foreground color`() {
        val t = Term()
        t.feed("\u001b[31mX")
        assertEquals(1, TerminalBuffer.Attr.fg(t.buffer.currentScreen[0].styles[0]))
    }

    @Test
    fun `sgr 256 color`() {
        val t = Term()
        t.feed("\u001b[38;5;196mX")
        assertEquals(196, TerminalBuffer.Attr.fg(t.buffer.currentScreen[0].styles[0]))
    }

    @Test
    fun `sgr rgb color packs direct rgb`() {
        val t = Term()
        t.feed("\u001b[38;2;250;10;30mX")
        val fg = TerminalBuffer.Attr.fg(t.buffer.currentScreen[0].styles[0])
        assertTrue(fg >= TerminalEmulator.DIRECT_RGB)
    }

    @Test
    fun `sgr bold brightens base color`() {
        // Rendering-level rule; verify flags here and brightening in resolveColor.
        val t = Term()
        t.feed("\u001b[1;31mX")
        val style = t.buffer.currentScreen[0].styles[0]
        assertEquals(TerminalBuffer.Attr.FLAG_BOLD, style and TerminalBuffer.Attr.FLAG_BOLD)
        assertEquals(1, TerminalBuffer.Attr.fg(style))
    }

    @Test
    fun `sgr reverse and reset`() {
        val t = Term()
        t.feed("\u001b[7mX\u001b[27mY")
        val flagsX = TerminalBuffer.Attr.flags(t.buffer.currentScreen[0].styles[0])
        val flagsY = TerminalBuffer.Attr.flags(t.buffer.currentScreen[0].styles[1])
        assertTrue(flagsX and TerminalBuffer.Attr.FLAG_REVERSE != 0)
        assertTrue(flagsY and TerminalBuffer.Attr.FLAG_REVERSE == 0)
    }

    @Test
    fun `sgr reset clears everything`() {
        val t = Term()
        t.feed("\u001b[1;4;31;44mX\u001b[0mY")
        assertEquals(0, t.buffer.currentScreen[1].styles[0])
    }

    // ------------------------------------------------------------ cursor & erasing

    @Test
    fun `cup positions cursor`() {
        val t = Term()
        t.feed("\u001b[3;5HX")
        assertEquals("    X", t.row(2))
    }

    @Test
    fun `cha sets column`() {
        val t = Term()
        t.feed("\u001b[8Ga")
        assertEquals("       a", t.row(0))
    }

    @Test
    fun `ed2 clears screen and homes nothing`() {
        val t = Term()
        t.feed("junk\r\nmore\u001b[2J")
        assertEquals("", t.row(0))
        assertEquals("", t.row(1))
    }

    @Test
    fun `el0 erases to end of line`() {
        val t = Term()
        t.feed("abcdef\u001b[1G\u001b[3CX\u001b[K")
        assertEquals("abcX", t.row(0))
    }

    @Test
    fun `dch deletes chars shifting left`() {
        val t = Term()
        t.feed("abcde\u001b[1G\u001b[2P")
        assertEquals("cde", t.row(0))
    }

    @Test
    fun `ich inserts blank chars`() {
        val t = Term()
        t.feed("abcde\u001b[1G\u001b[2@")
        assertEquals("  abcde", t.row(0))
    }

    @Test
    fun `ech erases chars in place`() {
        val t = Term()
        t.feed("abcde\u001b[1G\u001b[2X")
        assertEquals("  cde", t.row(0))
    }

    // ------------------------------------------------------------ margins & regions

    @Test
    fun `scroll region scrolls inside margins only`() {
        val t = Term(rows = 5)
        t.feed("TOP\r\n") // row 0 — outside the region, must never move
        t.feed("\u001b[2;4r\u001b[2;1H")
        t.feed("a\r\nb\r\nc\r\nd")
        assertEquals("TOP", t.row(0))
        assertEquals("b", t.row(1))
        assertEquals("c", t.row(2))
        assertEquals("d", t.row(3))
        assertEquals("", t.row(4))
    }

    @Test
    fun `il and dl inside region`() {
        val t = Term()
        t.feed("one\r\ntwo\r\nthree")
        t.feed("\u001b[2;1H\u001b[1L") // insert a blank line at row 2
        assertEquals("one", t.row(0))
        assertEquals("", t.row(1))
        assertEquals("two", t.row(2))
        t.feed("\u001b[2;1H\u001b[1M")
        assertEquals("two", t.row(1))
    }

    @Test
    fun `ri at top scrolls down`() {
        val t = Term()
        t.feed("top")
        t.feed("\u001b[1;1H\u001bM")
        assertEquals("", t.row(0))
        assertEquals("top", t.row(1))
    }

    // ------------------------------------------------------------ modes

    @Test
    fun `decset 25 hides cursor`() {
        val t = Term()
        t.feed("\u001b[?25l")
        assertFalse(t.emulator.cursorVisible)
        t.feed("\u001b[?25h")
        assertTrue(t.emulator.cursorVisible)
    }

    @Test
    fun `decckm toggles application cursor keys`() {
        val t = Term()
        t.feed("\u001b[?1h")
        assertTrue(t.emulator.appCursor)
        t.feed("\u001b[?1l")
        assertFalse(t.emulator.appCursor)
    }

    @Test
    fun `bracketed paste mode tracked`() {
        val t = Term()
        t.feed("\u001b[?2004h")
        assertTrue(t.emulator.bracketedPaste)
        t.feed("\u001b[?2004l")
        assertFalse(t.emulator.bracketedPaste)
    }

    @Test
    fun `origin mode confines cursor`() {
        val t = Term(rows = 6)
        t.feed("\u001b[3;6r\u001b[?6h\u001b[1;1HX")
        assertEquals("X", t.row(2)) // row 1 in origin = absolute row 3 → index 2
    }

    @Test
    fun `insert mode shifts content`() {
        val t = Term()
        t.feed("abcdef\u001b[1;1H\u001b[4hXY\u001b[4l")
        assertEquals("XYabcdef", t.row(0))
    }

    // ------------------------------------------------------------ device reports

    @Test
    fun `dsr 6 reports cursor position`() {
        val t = Term()
        t.feed("\u001b[2;3H\u001b[6n")
        assertEquals("\u001b[2;3R", t.responses.toString())
    }

    @Test
    fun `dsr 5 reports ok`() {
        val t = Term()
        t.feed("\u001b[5n")
        assertEquals("\u001b[0n", t.responses.toString())
    }

    @Test
    fun `da reports terminal class`() {
        val t = Term()
        t.feed("\u001b[c")
        assertTrue(t.responses.toString().startsWith("\u001b[?"))
    }

    // ------------------------------------------------------------ OSC

    @Test
    fun `osc 0 sets title`() {
        val t = Term()
        t.feed("\u001b]0;my title\u0007")
        assertEquals("my title", t.title)
    }

    @Test
    fun `osc terminated by st`() {
        val t = Term()
        t.feed("\u001b]2;title via ST\u001b\\")
        assertEquals("title via ST", t.title)
    }

    // ------------------------------------------------------------ misc escapes

    @Test
    fun `decaln fills screen`() {
        val t = Term()
        t.feed("\u001b#8")
        assertTrue(t.row(0).startsWith("EEEE"))
    }

    @Test
    fun `save restore cursor`() {
        val t = Term()
        t.feed("\u001b[3;4H\u001b[shello\r\nworld\u001b[uX")
        // restore returns to row 2 col 3; X overwrites the 'h' of hello
        assertEquals("   Xello", t.row(2))
    }

    @Test
    fun `full reset restores defaults`() {
        val t = Term()
        t.feed("\u001b[?1h\u001b[31m\u001bc")
        assertFalse(t.emulator.appCursor)
        assertEquals(0, t.buffer.currentScreen[0].styles[0])
    }

    @Test
    fun `invalid utf8 becomes replacement char`() {
        val t = Term()
        t.emulator.feed(byteArrayOf(0x61, 0xff.toByte(), 0x62), 3)
        assertEquals("a\ufffdb", t.row(0))
    }

    @Test
    fun `resize keeps content`() {
        val t = Term(cols = 10, rows = 4)
        t.feed("hello")
        t.emulator.resize(20, 5)
        assertEquals("hello", t.row(0))
        assertEquals(20, t.buffer.cols)
    }

    @Test
    fun `dcs sequences are swallowed`() {
        val t = Term()
        t.feed("\u001bP1\$r\u001b\\after")
        assertEquals("after", t.row(0))
    }

    @Test
    fun `selection text streams between anchor and focus`() {
        val t = Term(cols = 10, rows = 4)
        t.feed("abcdef\r\nghijkl")
        val text = t.buffer.selectionText(0, 1, 1, 3)
        assertEquals("bcdef\nghij", text)
    }

    @Test
    fun `cnl cpl move with column reset`() {
        val t = Term()
        t.feed("abc\u001b[1EX")
        // cursor was col 3 after abc, CNL → row 1 col 0 → X at start of row 1
        assertEquals("X", t.row(1))
    }

    @Test
    fun `null search state`() {
        val t = Term()
        assertNull(t.buffer.lineAt(-1))
    }
}
