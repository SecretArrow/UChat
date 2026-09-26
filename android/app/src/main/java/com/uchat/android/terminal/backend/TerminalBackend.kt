package com.uchat.android.terminal.backend

import com.uchat.android.linux.PtySession

/**
 * Layer 8 (session backend abstraction): the terminal UI never talks to a concrete process — it
 * talks to a [TerminalBackend]. Today UChat ships a local proot/PTY backend; an SSH backend can be
 * added later without touching the renderer, input, extra-keys or editor.
 */
interface TerminalBackend {
    val id: Long
    val label: String
    val isAlive: Boolean

    /** Register output/exit callbacks. [onOutput] is invoked on a background thread. */
    fun setCallbacks(onOutput: (ByteArray, Int) -> Unit, onExit: (Int) -> Unit)

    /** Remove a previously added output listener (no-op if unsupported). */
    fun removeOutputListener(listener: (ByteArray, Int) -> Unit) {}

    fun write(bytes: ByteArray)

    fun resize(rows: Int, cols: Int)

    /** Graceful stop (SIGHUP → SIGTERM escalation). */
    fun stop()

    /** Immediate SIGKILL. */
    fun kill()
}

/** Local Ubuntu (proot + pty) implementation of [TerminalBackend]. */
class PtyBackend(private val session: PtySession) : TerminalBackend {

    override val id: Long = session.id
    override val label: String = session.label
    override val isAlive: Boolean
        get() = session.state == com.uchat.android.linux.SessionState.RUNNING

    override fun setCallbacks(onOutput: (ByteArray, Int) -> Unit, onExit: (Int) -> Unit) {
        session.addOutputListener(onOutput)
        session.setExitListener(onExit)
    }

    override fun removeOutputListener(listener: (ByteArray, Int) -> Unit) {
        session.removeOutputListener(listener)
    }

    override fun write(bytes: ByteArray) = session.write(bytes)

    override fun resize(rows: Int, cols: Int) = session.resize(rows, cols)

    override fun stop() = session.stop()

    override fun kill() = session.kill()
}
