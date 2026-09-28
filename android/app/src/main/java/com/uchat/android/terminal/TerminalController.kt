package com.uchat.android.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.uchat.android.core.log.Logs
import com.uchat.android.terminal.backend.TerminalBackend
import com.uchat.android.terminal.emulator.TerminalBuffer
import com.uchat.android.terminal.emulator.TerminalEmulator

/**
 * Layer 3/8 wiring — connects one [TerminalBackend] (pty process, SSH later) to the native terminal
 * emulator ([TerminalEmulator] + [TerminalBuffer]).
 *
 * There is no WebView in this pipeline anymore:
 * ```
 * pty bytes ──► TerminalEmulator.feed ──► TerminalBuffer (cells + scrollback)
 *                                             ▲
 * renderer (Compose Canvas) reads cells ──────┘
 * keyboard / extra keys ──► write(bytes) ──► pty
 * ```
 * - Output is parsed on the pty reader thread; every feed bumps [renderTick], a Compose snapshot
 *   state the renderer reads — Compose coalesces bursts to the frame budget automatically.
 * - Session switching replays the bounded [TerminalReplayCache] through the emulator, so history is
 *   rendered (colors included) exactly like live output.
 * - While the user reads scrollback (not at bottom) the renderer anchors the viewport; new output
 *   never yanks them to the bottom (JuiceSSH behaviour).
 */
class TerminalController(
    val replayCache: TerminalReplayCache = TerminalReplayCache(),
) {

    val buffer = TerminalBuffer(80, 24, TerminalBuffer.DEFAULT_SCROLLBACK)
    val emulator = TerminalEmulator(buffer)

    var backend: TerminalBackend? = null
        private set

    /** Bumped after every visual change; the renderer reads it inside its draw pass. */
    var renderTick by mutableStateOf(0L)
        private set

    private val _atBottom = mutableStateOf(true)

    /** Whether the viewport is at the live bottom (drives the scroll-to-bottom button). */
    val atBottom: Boolean
        get() = _atBottom.value

    /** Viewport bookkeeping, written by the renderer. */
    fun reportViewport(atBottom: Boolean) {
        _atBottom.value = atBottom
    }

    /** Incremented when the UI asks for a scroll to the live bottom; the renderer observes it. */
    var scrollRequests by mutableStateOf(0)
        private set

    /** Incremented when the UI asks for the soft keyboard; the renderer observes it. */
    var keyboardRequests by mutableStateOf(0)
        private set

    fun requestKeyboard() {
        keyboardRequests++
    }

    /** Search result in absolute viewport coordinates (null = no hit), and the query highlight. */
    var searchHit by mutableStateOf<Int?>(null)
        private set

    /** Text currently selected in the terminal (set by the renderer). */
    var selectionText by mutableStateOf<String?>(null)
        private set

    fun reportSelection(text: String?) {
        selectionText = text
    }

    private var outputListener: ((ByteArray, Int) -> Unit)? = null

    init {
        emulator.callbacks.onTitle = { /* window title reserved for the tab row */}
        // Device reports (DSR/CPR, DA, CSI 18 t, focus in/out, OSC color queries) must reach
        // the host — programs like vim and fish block waiting for them. Route replies through
        // the same write path as user input; with no backend attached a reply is dropped.
        emulator.callbacks.onResponse = { data -> write(data) }
        emulator.callbacks.onBell = {}
        emulator.callbacks.onClipboard = { text -> selectionText = text }
    }

    /**
     * Attach a backend to this emulator. Feeds the session's replay through the parser so switching
     * sessions restores colors and cursor position, not just plain text.
     */
    fun attach(target: TerminalBackend) {
        if (backend === target) return
        outputListener?.let { backend?.removeOutputListener(it) }
        backend = target
        emulator.fullReset()
        val listener: (ByteArray, Int) -> Unit = { bytes, len ->
            emulator.feed(bytes, len)
            renderTick = buffer.generation
        }
        outputListener = listener
        target.setCallbacks(
            onOutput = listener,
            onExit = { code ->
                Logs.process("terminal session exited: $code")
                // Only annotate the screen while this backend is still the attached one; the
                // exit listener slot is single per session, so a stale closure must stay silent.
                if (backend === target) {
                    writeExitMessage(code)
                }
            },
        )
        val replay = replayCache.snapshot(target.id)
        if (replay.isNotEmpty()) {
            emulator.feed(replay, replay.size)
        }
        renderTick = buffer.generation
    }

    fun detach() {
        outputListener?.let { backend?.removeOutputListener(it) }
        outputListener = null
        backend = null
    }

    /** The renderer calls this once its grid metrics are known (also on size changes). */
    fun onGridViewport(cols: Int, rows: Int) {
        val c = cols.coerceIn(MIN_COLS, MAX_COLS)
        val r = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        if (c == buffer.cols && r == buffer.rows) return
        synchronized(buffer.lock) { emulator.resize(c, r) }
        backend?.resize(r, c)
        renderTick = buffer.generation
    }

    fun currentCols(): Int = buffer.cols

    fun currentRows(): Int = buffer.rows

    /** Fast path: raw bytes straight to the backend (extra keys, hardware keys). */
    fun write(bytes: ByteArray) {
        if (bytes.isNotEmpty()) backend?.write(bytes)
    }

    fun writeText(text: String) = write(text.toByteArray(Charsets.UTF_8))

    /** Paste respecting the host's bracketed-paste mode (DECSET 2004). */
    fun paste(text: String) {
        if (text.isEmpty()) return
        val payload = if (emulator.bracketedPaste) "\u001b[200~$text\u001b[201~" else text
        writeText(payload)
    }

    fun clearTerminal() {
        synchronized(buffer.lock) { buffer.clearScreenIncludingScrollback() }
        renderTick = buffer.generation
    }

    fun requestScrollToBottom() {
        scrollRequests++
    }

    /**
     * Force the renderer to repaint without touching the buffer — used when a global visual input
     * changes outside the emulator (e.g. the app theme switches the terminal palette). Only change
     * detection matters (draw reads it inside the draw pass), so a plain bump of the private
     * counter is enough.
     */
    fun requestRedraw() {
        renderTick = buffer.generation + (++extraTicks)
    }

    private var extraTicks = 0L

    /**
     * Search the visible history for [query]. [forward] = towards newer output. Returns true on a
     * hit and stores the absolute row in [searchHit] for the renderer to scroll to and highlight.
     */
    fun searchNext(query: String, forward: Boolean): Boolean {
        if (query.isEmpty()) return false
        synchronized(buffer.lock) {
            val total = buffer.totalLines
            if (total == 0) return false
            val start = searchHit ?: (total - 1)
            val rows = buffer.textRows(0, total - 1)
            val lower = query.lowercase()
            val n = rows.size
            for (step in 1..n) {
                val idx = if (forward) (start + step) % n else ((start - step) % n + n) % n
                if (rows[idx].lowercase().contains(lower)) {
                    searchHit = idx
                    renderTick = buffer.generation
                    return true
                }
            }
            return false
        }
    }

    fun clearSearch() {
        searchHit = null
        renderTick = buffer.generation
    }

    /** Viewport bounds for the renderer: max scroll offset in lines. */
    fun maxScrollLines(): Int = (buffer.totalLines - buffer.rows).coerceAtLeast(0)

    /** Plain text of the live screen (used by the Copy fallback). */
    fun screenText(): String {
        synchronized(buffer.lock) {
            val start = buffer.absoluteRowOf(0)
            return buffer.textRows(start, buffer.totalLines - 1).joinToString("\n")
        }
    }

    private fun writeExitMessage(code: Int) {
        synchronized(buffer.lock) {
            emulator.writeText(
                "\r\n\u001b[90m[session exited with code $code — sesi berakhir · tap + for a " +
                    "new session / ketuk + untuk sesi baru]\u001b[0m\r\n"
            )
        }
        renderTick = buffer.generation
    }

    companion object {
        const val MIN_COLS = 20
        const val MAX_COLS = 300
        const val MIN_ROWS = 5
        const val MAX_ROWS = 200
    }
}
