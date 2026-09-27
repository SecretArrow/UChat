package com.uchat.android.ui.terminal

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uchat.android.R
import com.uchat.android.core.settings.UChatSettings
import com.uchat.android.linux.PtySession
import com.uchat.android.linux.SessionState
import com.uchat.android.terminal.TerminalController
import com.uchat.android.terminal.TerminalReplayCache
import com.uchat.android.terminal.backend.PtyBackend
import com.uchat.android.terminal.keys.ExtraKey
import com.uchat.android.terminal.keys.ExtraKeysState
import com.uchat.android.terminal.keys.KeySequences
import com.uchat.android.terminal.keys.ModifierKey
import com.uchat.android.terminal.keys.ModifierStateHolder
import com.uchat.android.terminal.render.TerminalView

/**
 * Layer 4 UI: full-screen, terminal-first interface (dark, monospace, realtime).
 *
 * Composition of the separated terminal layers — all native:
 * - emulator: [com.uchat.android.terminal.emulator.TerminalEmulator] (VT/xterm parser)
 * - buffer: [com.uchat.android.terminal.emulator.TerminalBuffer] + bounded [TerminalReplayCache]
 * - renderer: [TerminalView] (Compose Canvas, no WebView)
 * - backend: [PtyBackend] — local proot shell today; SSH later without UI changes
 * - extra keys: [ExtraKeysToolbar] + editor + settings, all user customizable
 */
@Composable
fun TerminalScreenView(
    sessions: List<PtySession>,
    activeSession: PtySession?,
    settings: UChatSettings,
    extraKeysState: ExtraKeysState,
    replayCache: TerminalReplayCache,
    onSelectSession: (Long) -> Unit,
    onCreateSession: () -> Unit,
    onStopSession: (PtySession) -> Unit,
    onCloseSession: (PtySession) -> Unit,
    onCloseAllSessions: () -> Unit,
    onSelectLayout: (String) -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val controller = remember { TerminalController(replayCache) }

    // Extra-key sticky modifiers (screen-scoped state + UI snapshot for recomposition).
    val modifierState = remember { ModifierStateHolder() }
    var modifierUiState by remember {
        mutableStateOf(ModifierKey.entries.associateWith { ModifierStateHolder.State.OFF })
    }
    fun refreshModifiers() {
        modifierUiState = ModifierKey.entries.associateWith { modifierState.stateOf(it) }
    }
    LaunchedEffect(settings.terminalModifierMode) {
        modifierState.mode = settings.terminalModifierMode
        refreshModifiers()
    }

    fun handleKeyTap(key: ExtraKey) {
        if (activeSession == null) return
        val mods = modifierState.consume()
        controller.write(KeySequences.encode(key, mods))
        refreshModifiers()
    }

    fun handleKeyLongPress(key: ExtraKey) {
        if (activeSession == null) return
        KeySequences.encodeLongPress(key)?.let { controller.write(it) }
        refreshModifiers()
    }

    LaunchedEffect(activeSession?.id) {
        if (activeSession != null) {
            controller.attach(PtyBackend(activeSession))
        }
    }
    DisposableEffect(Unit) { onDispose { controller.detach() } }
    LaunchedEffect(settings.terminalScrollbackLines) {
        controller.buffer.scrollbackMax = settings.terminalScrollbackLines
    }
    // The terminal canvas reads its palette at draw time (TerminalColors), which is invisible
    // to Compose — nudge one repaint whenever the dark/light scheme flips so the whole window
    // recolors immediately instead of waiting for the next output burst.
    LaunchedEffect(TerminalPalette.isLight) { controller.requestRedraw() }

    val atBottom = controller.atBottom
    var layoutMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }

    val toolbar =
        @Composable {
            if (
                settings.terminalToolbarVisible &&
                    extraKeysState.toolbarEnabled &&
                    activeSession != null
            ) {
                ExtraKeysToolbar(
                    layout = extraKeysState.activeLayout(),
                    keyHeightDp = settings.terminalKeyHeightDp,
                    keyMinWidthDp = settings.terminalKeyMinWidthDp,
                    hapticsEnabled = settings.terminalHaptics,
                    repeatEnabled = settings.terminalKeyRepeat,
                    modifierStates = modifierUiState,
                    onKeyTap = ::handleKeyTap,
                    onKeyLongPress = ::handleKeyLongPress,
                    onModifierTap = { mod ->
                        modifierState.tap(mod)
                        refreshModifiers()
                    },
                    onModifierLockToggle = { mod ->
                        modifierState.lockToggle(mod)
                        refreshModifiers()
                    },
                    onSwitchLayout = { layoutMenuOpen = true },
                    onEditKeys = onOpenEditor,
                )
            }
        }

    Column(
        modifier.fillMaxSize().imePadding().background(TerminalPalette.Background),
    ) {
        if (
            settings.terminalToolbarPosition == com.uchat.android.core.settings.ToolbarPosition.TOP
        ) {
            toolbar()
        }

        if (sessions.isNotEmpty()) {
            SessionTabsRow(
                sessions = sessions,
                activeSession = activeSession,
                onSelect = onSelectSession,
                onStop = onStopSession,
                onClose = onCloseSession,
                onCreate = onCreateSession,
                overflow = {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = TerminalPalette.ForegroundDim,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_paste)) },
                            leadingIcon = {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                            },
                            onClick = {
                                overflowOpen = false
                                controller.paste(clipboard.getText()?.toString().orEmpty())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_copy)) },
                            leadingIcon = {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            },
                            onClick = {
                                overflowOpen = false
                                val text = controller.selectionText ?: controller.screenText()
                                if (text.isNotEmpty()) {
                                    clipboard.setText(AnnotatedString(text))
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_keyboard)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Keyboard, contentDescription = null)
                            },
                            onClick = {
                                overflowOpen = false
                                controller.requestKeyboard()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_find)) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            onClick = {
                                overflowOpen = false
                                showFind = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_clear)) },
                            onClick = {
                                overflowOpen = false
                                controller.clearTerminal()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_new_session)) },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                overflowOpen = false
                                onCreateSession()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_close_all)) },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = {
                                overflowOpen = false
                                onCloseAllSessions()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_editor)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Keyboard, contentDescription = null)
                            },
                            onClick = {
                                overflowOpen = false
                                onOpenEditor()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.terminal_settings)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            onClick = {
                                overflowOpen = false
                                onOpenSettings()
                            },
                        )
                    }
                },
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (activeSession == null) {
                EmptyTerminalState(onCreateSession)
            } else {
                TerminalView(
                    controller = controller,
                    fontSizeSp = settings.terminalFontSize,
                    cursorBlinkEnabled = settings.terminalCursorBlink,
                    fitScreen = settings.terminalFitScreen,
                    fixedCols = settings.terminalFixedCols,
                    fixedRows = settings.terminalFixedRows,
                    modifier = Modifier.fillMaxSize(),
                )
                // Quick scroll-to-bottom — only while the user is reading history (spec).
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && settings.terminalScrollButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    IconButton(
                        onClick = { controller.requestScrollToBottom() },
                        modifier =
                            Modifier.size(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(TerminalPalette.SurfaceRaised)
                                .border(1.dp, TerminalPalette.Outline, RoundedCornerShape(22.dp)),
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(R.string.terminal_scroll_bottom),
                            tint = TerminalPalette.Foreground,
                        )
                    }
                }
            }
        }

        if (
            settings.terminalToolbarPosition ==
                com.uchat.android.core.settings.ToolbarPosition.BOTTOM
        ) {
            toolbar()
        }
    }

    if (layoutMenuOpen) {
        AlertDialog(
            onDismissRequest = { layoutMenuOpen = false },
            title = { Text(stringResource(R.string.layout_switch)) },
            text = {
                Column {
                    extraKeysState.layouts.forEach { layout ->
                        TextButton(
                            onClick = {
                                onSelectLayout(layout.id)
                                layoutMenuOpen = false
                            }
                        ) {
                            Text(
                                (if (layout.id == extraKeysState.activeLayoutId) "● " else "○ ") +
                                    layout.name,
                                color = TerminalPalette.Foreground,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        layoutMenuOpen = false
                        onOpenEditor()
                    }
                ) {
                    Text(stringResource(R.string.terminal_editor))
                }
            },
            dismissButton = {
                TextButton(onClick = { layoutMenuOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showFind) {
        FindDialog(
            onFind = { query, forward -> controller.searchNext(query, forward) },
            onDismiss = {
                controller.clearSearch()
                showFind = false
            },
        )
    }
}

@Composable
private fun SessionTabsRow(
    sessions: List<PtySession>,
    activeSession: PtySession?,
    onSelect: (Long) -> Unit,
    onStop: (PtySession) -> Unit,
    onClose: (PtySession) -> Unit,
    onCreate: () -> Unit,
    overflow: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(TerminalPalette.Surface).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessions.forEach { session ->
                val active = session.id == activeSession?.id
                val running = session.state == SessionState.RUNNING
                Row(
                    Modifier.padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) TerminalPalette.SurfaceRaised else TerminalPalette.Surface
                        )
                        .clickable { onSelect(session.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SessionStatusDot(running)
                    Text(
                        session.label,
                        color = TerminalPalette.Foreground,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                    )
                    IconButton(
                        onClick = { if (running) onStop(session) else onClose(session) },
                        modifier = Modifier.size(22.dp).padding(start = 2.dp),
                    ) {
                        Icon(
                            if (running) Icons.Filled.StopCircle else Icons.Filled.Close,
                            contentDescription =
                                stringResource(
                                    if (running) R.string.terminal_stop_session
                                    else R.string.terminal_close_session
                                ),
                            tint = TerminalPalette.ForegroundDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onCreate, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.terminal_new_tab),
                    tint = TerminalPalette.Foreground,
                )
            }
        }
        overflow()
    }
}

@Composable
private fun EmptyTerminalState(onCreateSession: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Keyboard,
            contentDescription = null,
            tint = TerminalPalette.ForegroundDim,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.terminal_no_sessions),
            color = TerminalPalette.Foreground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.terminal_no_sessions_hint),
            color = TerminalPalette.ForegroundDim,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(onClick = onCreateSession, modifier = Modifier.padding(top = 20.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                stringResource(R.string.terminal_new_session),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun FindDialog(
    onFind: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.terminal_find)) },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.terminal_find_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = query.text.isNotEmpty(),
                    onClick = { onFind(query.text, false) },
                ) {
                    Text(stringResource(R.string.terminal_find_prev))
                }
                TextButton(
                    enabled = query.text.isNotEmpty(),
                    onClick = { onFind(query.text, true) },
                ) {
                    Text(stringResource(R.string.terminal_find_next))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
