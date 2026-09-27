package com.uchat.android

import com.uchat.android.terminal.emulator.WcWidth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Width tests — what keeps tables and progress bars aligned. */
class WcWidthTest {

    @Test
    fun `ascii is width one`() {
        assertEquals(1, WcWidth.width('a'.code))
        assertEquals(1, WcWidth.width('0'.code))
        assertEquals(1, WcWidth.width('~'.code))
    }

    @Test
    fun `cjk is width two`() {
        assertEquals(2, WcWidth.width('世'.code))
        assertEquals(2, WcWidth.width('界'.code))
        assertEquals(2, WcWidth.width(0x4e00))
        assertEquals(2, WcWidth.width(0x9fff))
    }

    @Test
    fun `hangul and kana are width two`() {
        assertEquals(2, WcWidth.width(0xac00))
        assertEquals(2, WcWidth.width(0x3042))
    }

    @Test
    fun `combining marks are zero`() {
        assertEquals(0, WcWidth.width(0x0301)) // combining acute
        assertEquals(0, WcWidth.width(0x200d)) // ZWJ
        assertEquals(0, WcWidth.width(0xfe0f)) // variation selector-16
    }

    @Test
    fun `emoji is width two`() {
        assertEquals(2, WcWidth.width(0x1f600)) // 😀
        assertEquals(2, WcWidth.width(0x1f525)) // 🔥
    }

    @Test
    fun `box drawing is width one`() {
        assertEquals(1, WcWidth.width(0x2500))
        assertEquals(1, WcWidth.width(0x2588))
        assertEquals(1, WcWidth.width(0x2593))
    }

    @Test
    fun `surrogate pairs measured once`() {
        assertEquals(2, WcWidth.widthOf("\uD83D\uDE00"))
        assertEquals(4, WcWidth.stringWidth("a😀b"))
    }

    @Test
    fun `string width sums`() {
        assertEquals(6, WcWidth.stringWidth("a世b界"))
        assertEquals(0, WcWidth.stringWidth(""))
        assertEquals(3, WcWidth.stringWidth("abc"))
    }
}
