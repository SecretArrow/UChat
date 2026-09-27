package com.uchat.android

import com.uchat.android.terminal.TerminalController
import com.uchat.android.terminal.backend.TerminalBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Controller wiring tests: device reports emitted by the emulator (DSR/CPR, DA, focus reporting)
 * must reach the attached backend's write path — dropped replies make programs like vim hang
 * waiting for an answer. The emulator-level emission is covered by [TerminalEmulatorTest].
 */
class TerminalControllerTest {

    /** Records everything written; replays output through the registered listener on demand. */
    private class FakeBackend(override val id: Long = 1L) : TerminalBackend {
        override val label = "fake"
        override val isAlive = true

        val written = mutableListOf<ByteArray>()
        private var onOutput: ((ByteArray, Int) -> Unit)? = null

        override fun setCallbacks(onOutput: (ByteArray, Int) -> Unit, onExit: (Int) -> Unit) {
            this.onOutput = onOutput
        }

        override fun write(bytes: ByteArray) {
            written.add(bytes.copyOf())
        }

        override fun resize(rows: Int, cols: Int) {}

        override fun stop() {}

        override fun kill() {}

        /** Simulate the pty reader thread delivering host output to the controller. */
        fun emit(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            onOutput?.invoke(bytes, bytes.size)
        }

        fun joined(): String {
            if (written.isEmpty()) return ""
            return String(written.reduce { acc, bytes -> acc + bytes }, Charsets.UTF_8)
        }
    }

    @Test
    fun `cpr response reaches backend write path`() {
        val controller = TerminalController()
        val backend = FakeBackend()
        controller.attach(backend)
        backend.emit("\u001b[3;5H") // host moves the cursor to row 3, col 5
        backend.emit("\u001b[6n") // host asks for the cursor position (DSR 6)
        assertEquals("\u001b[3;5R", backend.joined())
    }

    @Test
    fun `primary da response reaches backend write path`() {
        val controller = TerminalController()
        val backend = FakeBackend()
        controller.attach(backend)
        backend.emit("\u001b[c")
        assertEquals("\u001b[?6c", backend.joined())
    }

    @Test
    fun `response with no backend attached is dropped silently`() {
        val controller = TerminalController()
        val bytes = "\u001b[6n".toByteArray(Charsets.UTF_8)
        controller.emulator.feed(bytes, bytes.size) // must not throw with backend == null
        val backend = FakeBackend()
        controller.attach(backend)
        assertEquals("", backend.joined())
    }

    @Test
    fun `focus report reaches backend when host requested reporting`() {
        val controller = TerminalController()
        val backend = FakeBackend()
        controller.attach(backend)
        backend.emit("\u001b[?1004h") // host enables focus reporting
        controller.emulator.reportFocus(true) // renderer reports focus gained
        assertTrue(backend.joined().contains("\u001b[I"))
    }
}
