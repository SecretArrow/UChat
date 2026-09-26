package com.uchat.android.terminal

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.uchat.android.core.log.Logs
import com.uchat.android.terminal.backend.TerminalBackend
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Layer 3/8 wiring: connects one [TerminalBackend] to one xterm.js [WebView].
 *
 * Separation of concerns:
 * - The backend produces raw bytes (local proot pty today, SSH later — same contract).
 * - The replay cache ([TerminalReplayCache]) is the bounded scrollback buffer on the native side;
 *   it is fed by ProcessManager even while no screen is attached.
 * - This controller batches output and paints it into the renderer (xterm.js) every 16 ms.
 * - Input latency: extra-key bytes are written straight to the backend — they never detour through
 *   the JS bridge.
 */
class TerminalController(
    private val webViewProvider: () -> WebView?,
    private val replayCache: TerminalReplayCache = TerminalReplayCache(),
) {

    var backend: TerminalBackend? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val pendingOutput = ConcurrentLinkedQueue<String>()
    private val json = Json

    private val _atBottom = MutableStateFlow(true)
    val atBottom: StateFlow<Boolean> = _atBottom.asStateFlow()

    @Volatile private var webReady = false
    @Volatile private var pendingCols = 80
    @Volatile private var pendingRows = 24
    private var outputListener: ((ByteArray, Int) -> Unit)? = null
    private var renderedSessionId: Long = -1

    private val flushRunnable =
        object : Runnable {
            override fun run() {
                flush()
                handler.postDelayed(this, FLUSH_INTERVAL_MS)
            }
        }

    /** Attach a backend (session) to this renderer. Safe to call repeatedly on session switch. */
    fun attach(target: TerminalBackend) {
        val previous = backend
        if (previous === target) return
        outputListener?.let { previous?.removeOutputListener(it) }
        backend = target
        webReady = webReady && renderedSessionId == target.id
        val listener: (ByteArray, Int) -> Unit = { bytes, len ->
            replayCache.offer(target.id, bytes, len)
            pendingOutput.add(String(bytes, 0, len, Charsets.UTF_8))
        }
        outputListener = listener
        target.setCallbacks(
            onOutput = listener,
            onExit = { code ->
                Logs.process("terminal session exited: $code")
                handler.post { pushExit(code) }
            }
        )
        // Session switch in a live WebView: clear and replay the target's recent output.
        if (webReady) renderReplay(target.id)
        handler.removeCallbacks(flushRunnable)
        handler.post(flushRunnable)
    }

    fun detach() {
        outputListener?.let { backend?.removeOutputListener(it) }
        outputListener = null
        handler.removeCallbacks(flushRunnable)
        backend = null
    }

    fun onWebTerminalReady() {
        webReady = true
        sendResize(pendingCols, pendingRows)
        backend?.let { renderReplay(it.id) }
    }

    fun onTerminalResized(cols: Int, rows: Int) {
        pendingCols = cols.coerceIn(20, 500)
        pendingRows = rows.coerceIn(5, 300)
        backend?.resize(pendingRows, pendingCols)
    }

    fun onScrollStateChanged(atBottom: Boolean) {
        _atBottom.value = atBottom
    }

    fun requestedSize(): Pair<Int, Int> = pendingCols to pendingRows

    /** Fast path: send raw bytes (extra keys, paste) directly to the backend. */
    fun sendBytes(bytes: ByteArray) {
        if (bytes.isNotEmpty()) backend?.write(bytes)
    }

    fun requestScrollToBottom() {
        evaluate("window.UChatTerm && window.UChatTerm.scrollToBottom()")
    }

    fun setFontSize(px: Int) {
        evaluate("window.UChatTerm && window.UChatTerm.setFontSize($px)")
    }

    fun setCursorBlink(enabled: Boolean) {
        evaluate("window.UChatTerm && window.UChatTerm.setCursorBlink($enabled)")
    }

    fun clearTerminal() {
        evaluate("window.UChatTerm && window.UChatTerm.clear()")
        backend?.let { replayCache.clear(it.id) }
    }

    fun searchNext(query: String) {
        val payload = json.encodeToString(String.serializer(), query)
        evaluate("window.UChatTerm && window.UChatTerm.searchNext($payload)")
    }

    /** Paste through the renderer so bracketed-paste mode is respected. */
    fun paste(text: String) {
        if (text.isEmpty()) return
        val payload = json.encodeToString(String.serializer(), text)
        evaluate("window.UChatTerm && window.UChatTerm.paste($payload)")
    }

    private fun renderReplay(sessionId: Long) {
        renderedSessionId = sessionId
        pendingOutput.clear()
        val replay = replayCache.snapshot(sessionId)
        evaluate("window.UChatTerm && window.UChatTerm.reset()")
        if (replay.isNotEmpty()) {
            val payload = json.encodeToString(String.serializer(), String(replay, Charsets.UTF_8))
            evaluate("window.UChatTerm && window.UChatTerm.write($payload)")
        }
    }

    private fun flush() {
        if (pendingOutput.isEmpty()) return
        val sb = StringBuilder()
        while (true) {
            val chunk = pendingOutput.poll() ?: break
            sb.append(chunk)
            if (sb.length > MAX_FLUSH_CHARS) break
        }
        val payload = json.encodeToString(String.serializer(), sb.toString())
        val webView = webViewProvider() ?: return
        handler.post {
            webView.evaluateJavascript("window.UChatTerm && window.UChatTerm.write($payload)", null)
        }
    }

    private fun pushExit(code: Int) {
        evaluate("window.UChatTerm && window.UChatTerm.exit($code)")
    }

    private fun sendResize(cols: Int, rows: Int) {
        evaluate("window.UChatTerm && window.UChatTerm.resize($cols, $rows)")
    }

    private fun evaluate(js: String) {
        val webView = webViewProvider() ?: return
        handler.post { webView.evaluateJavascript(js, null) }
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 16L
        private const val MAX_FLUSH_CHARS = 128 * 1024
    }
}
