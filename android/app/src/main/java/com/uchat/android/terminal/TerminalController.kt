package com.uchat.android.terminal

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.uchat.android.core.log.Logs
import com.uchat.android.linux.PtySession
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Wires one [PtySession] to one xterm.js [WebView].
 *
 * Output batching: pty chunks are queued and flushed to the WebView every 16 ms on the main thread
 * — smooth rendering without flooding the JS bridge.
 */
class TerminalController(
    private val webViewProvider: () -> WebView?,
) {
    var session: PtySession? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val pendingOutput = ConcurrentLinkedQueue<String>()
    @Volatile private var webReady = false
    @Volatile private var pendingCols = 80
    @Volatile private var pendingRows = 24

    private val flushRunnable =
        object : Runnable {
            override fun run() {
                flush()
                handler.postDelayed(this, 16)
            }
        }

    fun attach(session: PtySession) {
        this.session = session
        webReady = false
        session.setCallbacks(
            onOutput = { bytes, len ->
                val text = String(bytes, 0, len, Charsets.UTF_8)
                pendingOutput.add(text)
            },
            onExit = { code ->
                Logs.process("terminal session exited: $code")
                handler.post { pushExit(code) }
            },
        )
        handler.removeCallbacks(flushRunnable)
        handler.post(flushRunnable)
    }

    fun detach() {
        handler.removeCallbacks(flushRunnable)
        session = null
    }

    fun onWebTerminalReady() {
        webReady = true
        // Push the initial size as soon as xterm is up.
        sendResize(pendingCols, pendingRows)
    }

    fun onTerminalResized(cols: Int, rows: Int) {
        pendingCols = cols.coerceIn(20, 500)
        pendingRows = rows.coerceIn(5, 300)
        session?.resize(pendingRows, pendingCols)
    }

    fun requestedSize(): Pair<Int, Int> = pendingCols to pendingRows

    private fun flush() {
        if (pendingOutput.isEmpty()) return
        val sb = StringBuilder()
        while (true) {
            val chunk = pendingOutput.poll() ?: break
            sb.append(chunk)
            if (sb.length > 128 * 1024) break
        }
        val payload = Json.encodeToString(String.serializer(), sb.toString())
        val webView = webViewProvider() ?: return
        handler.post {
            webView.evaluateJavascript("window.UChatTerm && window.UChatTerm.write($payload)", null)
        }
    }

    private fun pushExit(code: Int) {
        val webView = webViewProvider() ?: return
        webView.evaluateJavascript("window.UChatTerm && window.UChatTerm.exit($code)", null)
    }

    private fun sendResize(cols: Int, rows: Int) {
        val webView = webViewProvider() ?: return
        handler.post {
            webView.evaluateJavascript(
                "window.UChatTerm && window.UChatTerm.resize($cols, $rows)",
                null
            )
        }
    }
}
