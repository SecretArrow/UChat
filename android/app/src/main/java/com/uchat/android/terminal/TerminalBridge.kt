package com.uchat.android.terminal

import android.webkit.JavascriptInterface

/**
 * Bridge between the xterm.js WebView and the native terminal stack.
 *
 * JS → Kotlin : onTerminalInput(str), onTerminalResize(cols, rows), onTerminalReady(),
 * onScrollStateChanged(atBottom) Kotlin → JS : window.UChatTerm.{write, reset, exit, resize,
 * setFontSize, setCursorBlink, scrollToBottom, paste, searchNext, searchPrev, clearSearch, clear,
 * selectAll, getSelection}
 *
 * Only the app's own asset page is loaded, so the interface exposure is safe.
 */
class TerminalBridge(private val owner: TerminalController) {

    @JavascriptInterface
    fun onTerminalInput(data: String) {
        val text = data.ifEmpty { "\n" }
        owner.sendBytes(text.toByteArray(Charsets.UTF_8))
    }

    @JavascriptInterface
    fun onTerminalResize(cols: Int, rows: Int) {
        owner.onTerminalResized(cols, rows)
    }

    @JavascriptInterface
    fun onTerminalReady() {
        owner.onWebTerminalReady()
    }

    @JavascriptInterface
    fun onScrollStateChanged(atBottom: Boolean) {
        owner.onScrollStateChanged(atBottom)
    }
}
