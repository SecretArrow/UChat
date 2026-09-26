package com.uchat.android

import com.uchat.android.core.format.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun bytes_formatsIEC() {
        assertEquals("0 B", Format.bytes(0))
        assertEquals("999 B", Format.bytes(999))
        assertEquals("1.0 KB", Format.bytes(1024))
        assertEquals("1.5 KB", Format.bytes(1536))
        assertEquals("28.5 MB", Format.bytes(29936675))
        assertEquals("1.0 GB", Format.bytes(1024L * 1024L * 1024L))
    }

    @Test
    fun bytes_negativeShowsDash() {
        assertEquals("—", Format.bytes(-5))
    }

    @Test
    fun duration_formats() {
        assertEquals("00:42", Format.duration(42))
        assertEquals("01:02", Format.duration(62))
        assertEquals("1:02:05", Format.duration(3725))
    }

    @Test
    fun eta_isHuman() {
        assertEquals("34 sec", Format.eta(34))
        assertEquals("2 min 5s", Format.eta(125))
        assertEquals("1 h 5 min", Format.eta(3900))
    }

    @Test
    fun percent_clamps() {
        assertEquals("0%", Format.percent(-0.5f))
        assertEquals("50%", Format.percent(0.5f))
        assertEquals("100%", Format.percent(2f))
    }
}
