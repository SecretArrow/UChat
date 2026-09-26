package com.uchat.android.ui.terminal

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uchat.android.R
import com.uchat.android.assets.TerminalAssets
import com.uchat.android.core.format.Format
import com.uchat.android.linux.PtySession
import com.uchat.android.linux.SessionState
import com.uchat.android.terminal.TerminalBridge
import com.uchat.android.terminal.TerminalController

/**
 * Mobile-first terminal (spec #8, #37): real pty + xterm.js rendering, extra keys toolbar,
 * copy/paste, session status.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreenView(
    session: PtySession?,
    fontSize: Int,
    onCreateSession: () -> Unit,
    onKillSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val controller = remember { TerminalController { webViewRef.value } }

    // (Re)attach when the active session changes.
    LaunchedEffect(session?.id) {
        if (session != null && controller.session?.id != session.id) {
            controller.attach(session)
        }
    }

    DisposableEffect(Unit) { onDispose { controller.detach() } }

    LaunchedEffect(fontSize) {
        webViewRef.value?.evaluateJavascript(
            "window.UChatTerm && window.UChatTerm.setFontSize($fontSize)",
            null,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (session == null) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.processes_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onCreateSession, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.terminal_new_session))
                }
            }
        } else {
            // Status strip
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text =
                        "${session.label} · " +
                            Format.duration(
                                (System.currentTimeMillis() - session.startedAtMillis) / 1000
                            ) +
                            " · " +
                            if (session.state == SessionState.RUNNING) {
                                stringResource(R.string.processes_running)
                            } else {
                                stringResource(R.string.processes_exited, session.exitCode ?: -1)
                            },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        settings.loadWithOverviewMode = true
                        addJavascriptInterface(TerminalBridge(controller), "UChatTerminal")
                        webViewClient = WebViewClient()
                        loadUrl(TerminalAssets.PAGE)
                        webViewRef.value = this
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            // Extra keys toolbar (spec #37)
            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                ExtraKey(stringResource(R.string.terminal_key_esc)) { session.write("\u001b") }
                ExtraKey(stringResource(R.string.terminal_key_tab)) { session.write("\t") }
                ExtraKey("CTRL") { session.write("\u0012") }
                ExtraKey("←") { session.write("\u001b[D") }
                ExtraKey("↑") { session.write("\u001b[A") }
                ExtraKey("↓") { session.write("\u001b[B") }
                ExtraKey("→") { session.write("\u001b[C") }
                ExtraKey("/") { session.write("/") }
                ExtraKey("~") { session.write("~") }
                ExtraKey("|") { session.write("|") }
                ExtraKey("-") { session.write("-") }
                ExtraKey("$") { session.write("$") }
                ExtraKey(".") { session.write(".") }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val sel = clipboard.getText()?.toString().orEmpty()
                        if (sel.isNotEmpty()) session.write(sel)
                    }
                ) {
                    Text(stringResource(R.string.terminal_paste), maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        webViewRef.value?.evaluateJavascript(
                            "(window.UChatTerm ? window.UChatTerm.getSelection() : '')",
                        ) { sel ->
                            val text = sel?.trim('"') ?: ""
                            if (text.isNotEmpty()) {
                                val cm =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("UChat selection", text))
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.terminal_copy), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onKillSession,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.terminal_kill_session), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ExtraKey(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(end = 6.dp).height(44.dp),
    ) {
        Text(label)
    }
}
