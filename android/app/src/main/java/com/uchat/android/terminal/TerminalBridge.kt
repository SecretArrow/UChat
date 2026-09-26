package com.uchat.android.terminal

import android.webkit.JavascriptInterface
import com.uchat.android.linux.PtySession

/**
 * Bridge between the xterm.js WebView and a [PtySession].
 *
 * JS → Kotlin : onTerminalInput(str), onTerminalResize(cols, rows) Kotlin → JS :
 * window.UChatTerm.write(jsonString)
 *
 * Only the app's own asset page is loaded, so the interface exposure is safe.
 */
class TerminalBridge(private val owner: TerminalController) {

    @JavascriptInterface
    fun onTerminalInput(data: String) {
        val text = data.ifEmpty { "\n" }
        owner.session?.write(text)
    }

    @JavascriptInterface
    fun onTerminalResize(cols: Int, rows: Int) {
        owner.onTerminalResized(cols, rows)
    }

    @JavascriptInterface
    fun onTerminalReady() {
        owner.onWebTerminalReady()
    }
}
