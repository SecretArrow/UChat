@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.uchat.android.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uchat.android.R
import com.uchat.android.terminal.keys.ExtraKey
import com.uchat.android.terminal.keys.ExtraKeyType
import com.uchat.android.terminal.keys.ExtraKeysStore
import com.uchat.android.terminal.keys.KeyLayout
import com.uchat.android.terminal.keys.KeySequences
import com.uchat.android.terminal.keys.ModifierKey
import com.uchat.android.terminal.keys.RepeatBehavior
import java.util.UUID

/**
 * Layer 6 UI: dedicated "Edit Extra Keys" interface.
 *
 * Add / edit / duplicate / delete keys, reorder with drag & drop, manage multiple layouts (create,
 * rename, duplicate, delete, reset, set as default).
 */
@Composable
fun ExtraKeysEditorScreen(
    store: ExtraKeysStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by store.state.collectAsState()
    val layout = state.activeLayout()

    var showKeyEditor by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ExtraKey?>(null) }
    var showLayouts by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val layoutId = layout?.id ?: return

    Scaffold(
        modifier = modifier,
        containerColor = TerminalPalette.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.layout_reset)) },
                            onClick = {
                                menuOpen = false
                                store.resetLayout(layoutId)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.layout_reset_all)) },
                            onClick = {
                                menuOpen = false
                                store.resetAll()
                            },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = TerminalPalette.Surface,
                        titleContentColor = TerminalPalette.Foreground,
                        navigationIconContentColor = TerminalPalette.Foreground,
                        actionIconContentColor = TerminalPalette.Foreground,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingKey = null
                    showKeyEditor = true
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.editor_add_key))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LayoutSelectorRow(
                layouts = state.layouts,
                activeId = state.activeLayoutId,
                onSelect = { store.selectLayout(it) },
                onManage = { showLayouts = true },
            )
            if (layout?.keys.isNullOrEmpty()) {
                EmptyKeysHint()
            } else if (layout != null) {
                DraggableKeyList(
                    layout = layout,
                    onMove = { from, to -> store.moveKey(layoutId, from, to) },
                    onEdit = {
                        editingKey = it
                        showKeyEditor = true
                    },
                    onDuplicate = { store.duplicateKey(layoutId, it.id) },
                    onDelete = { store.deleteKey(layoutId, it.id) },
                )
            }
        }
    }

    if (showKeyEditor) {
        ExtraKeyEditDialog(
            initial = editingKey,
            onSave = { key ->
                if (editingKey == null) store.addKey(layoutId, key)
                else store.updateKey(layoutId, key)
                showKeyEditor = false
            },
            onDismiss = { showKeyEditor = false },
        )
    }

    if (showLayouts) {
        LayoutManagerDialog(
            store = store,
            layouts = state.layouts,
            activeId = state.activeLayoutId,
            onDismiss = { showLayouts = false },
        )
    }
}

@Composable
private fun LayoutSelectorRow(
    layouts: List<KeyLayout>,
    activeId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        layouts.forEach { layout ->
            val active = layout.id == activeId
            Row(
                Modifier.padding(end = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) TerminalPalette.Accent else TerminalPalette.Surface)
                    .border(
                        1.dp,
                        if (active) TerminalPalette.Accent else TerminalPalette.Outline,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(layout.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    layout.name,
                    color = if (active) Color.White else TerminalPalette.ForegroundDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        IconButton(onClick = onManage) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.layouts_title),
                tint = TerminalPalette.ForegroundDim,
            )
        }
    }
}

@Composable
private fun EmptyKeysHint() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.editor_no_keys),
            color = TerminalPalette.Foreground,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.editor_no_keys_hint),
            color = TerminalPalette.ForegroundDim,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Vertical list with long-press drag & drop reordering (spec: drag keys to change position). */
@Composable
private fun DraggableKeyList(
    layout: KeyLayout,
    onMove: (Int, Int) -> Unit,
    onEdit: (ExtraKey) -> Unit,
    onDuplicate: (ExtraKey) -> Unit,
    onDelete: (ExtraKey) -> Unit,
) {
    val keys = layout.keys
    val rowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    var draggingIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(keys, key = { _, k -> k.id }) { index, key ->
            var accum by remember(key.id) { mutableStateOf(0f) }
            KeyRow(
                key = key,
                dragging = draggingIndex == index,
                onDragStart = {
                    draggingIndex = index
                    accum = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDrag = { delta ->
                    accum += delta
                    val steps = (accum / rowHeightPx).toInt()
                    if (steps != 0) {
                        val target = (index + steps).coerceIn(0, keys.size - 1)
                        if (target != index) {
                            onMove(index, target)
                            draggingIndex = target
                        }
                        accum -= steps * rowHeightPx
                    }
                },
                onDragEnd = { draggingIndex = null },
                onEdit = { onEdit(key) },
                onDuplicate = { onDuplicate(key) },
                onDelete = { onDelete(key) },
            )
        }
        item {
            Text(
                stringResource(R.string.editor_drag_hint),
                color = TerminalPalette.ForegroundDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun KeyRow(
    key: ExtraKey,
    dragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .height(56.dp)
            .background(if (dragging) TerminalPalette.SurfaceRaised else TerminalPalette.Surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.editor_drag_hint),
            tint = TerminalPalette.ForegroundDim,
            modifier =
                Modifier.pointerInput(key.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        )
                    }
                    .padding(8.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                key.label,
                color = TerminalPalette.Foreground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
            )
            Text(
                describeKey(key),
                color = TerminalPalette.ForegroundDim,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = TerminalPalette.ForegroundDim)
        }
        IconButton(onClick = onDuplicate) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.action_duplicate),
                tint = TerminalPalette.ForegroundDim,
            )
        }
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = TerminalPalette.Accent,
            )
        }
    }
}

private fun describeKey(key: ExtraKey): String =
    when (key.type) {
        ExtraKeyType.MODIFIER -> "modifier"
        ExtraKeyType.TERMINAL_KEY -> key.text
        ExtraKeyType.COMBO -> key.modifiers.joinToString("+") { it.name } + " + " + key.text
        ExtraKeyType.ESCAPE_SEQ -> key.text
        ExtraKeyType.COMMAND -> "\"${key.text}\" ⏎"
        ExtraKeyType.TEXT -> "\"${key.text}\""
    } + if (key.longPressText != null) " · ⏱" else ""

// ---------------------------------------------------------------- key editor dialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraKeyEditDialog(
    initial: ExtraKey?,
    onSave: (ExtraKey) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initial?.type ?: ExtraKeyType.TEXT) }
    var label by remember { mutableStateOf(TextFieldValue(initial?.label ?: "")) }
    var text by remember { mutableStateOf(TextFieldValue(initial?.text ?: "")) }
    var longPress by remember { mutableStateOf(TextFieldValue(initial?.longPressText ?: "")) }
    var haptic by remember { mutableStateOf(initial?.haptic ?: true) }
    var repeat by remember { mutableStateOf(initial?.repeat == RepeatBehavior.HOLD) }
    var selectedMods by remember { mutableStateOf(initial?.modifiers?.toSet() ?: emptySet()) }

    val valueRequired = type != ExtraKeyType.MODIFIER
    val canSave = label.text.isNotBlank() && (!valueRequired || text.text.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.editor_add_key else R.string.editor_title
                )
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.editor_key_type),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow {
                    ExtraKeyType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(typeLabel(t), fontSize = 12.sp) },
                            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.editor_key_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == ExtraKeyType.MODIFIER) {
                    Text(
                        stringResource(R.string.editor_modifiers),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow {
                        ModifierKey.entries.forEach { mod ->
                            FilterChip(
                                selected = mod in selectedMods,
                                onClick = {
                                    selectedMods =
                                        if (mod in selectedMods) selectedMods - mod
                                        else selectedMods + mod
                                },
                                label = { Text(mod.name, fontSize = 12.sp) },
                                modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.editor_key_value)) },
                        placeholder = { Text(valueHint(type), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (type == ExtraKeyType.TERMINAL_KEY || type == ExtraKeyType.COMBO) {
                        TerminalKeySuggestionRow { suggestion -> text = TextFieldValue(suggestion) }
                    }
                }
                OutlinedTextField(
                    value = longPress,
                    onValueChange = { longPress = it },
                    label = { Text(stringResource(R.string.editor_long_press)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.editor_haptic),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = haptic, onCheckedChange = { haptic = it })
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.editor_repeat),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = repeat, onCheckedChange = { repeat = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        ExtraKey(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            label = label.text.trim(),
                            type = type,
                            text = text.text,
                            modifiers = selectedMods.toList(),
                            longPressText = longPress.text.takeIf { it.isNotBlank() },
                            repeat = if (repeat) RepeatBehavior.HOLD else RepeatBehavior.NONE,
                            haptic = haptic,
                        )
                    )
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Quick suggestions for canonical terminal key names. */
@Composable
private fun TerminalKeySuggestionRow(onPick: (String) -> Unit) {
    val suggestions =
        listOf("UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN", "DEL", "F5")
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
    ) {
        suggestions.forEach { name ->
            Text(
                KeySequences.displayLabel(name),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickableNoIndication { onPick(name) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

// ---------------------------------------------------------------- layout manager dialog

@Composable
private fun LayoutManagerDialog(
    store: ExtraKeysStore,
    layouts: List<KeyLayout>,
    activeId: String,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<KeyLayout?>(null) }
    var nameInput by remember { mutableStateOf(TextFieldValue("")) }
    var deleteCandidate by remember { mutableStateOf<KeyLayout?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layouts_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                layouts.forEach { layout ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = layout.id == activeId,
                            onClick = { store.setDefaultLayout(layout.id) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(layout.name, fontWeight = FontWeight.Medium)
                            if (layout.builtIn) {
                                Text(
                                    stringResource(R.string.layout_built_in),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        LayoutMenu(
                            layout = layout,
                            onSetDefault = { store.setDefaultLayout(layout.id) },
                            onRename = {
                                renaming = layout
                                nameInput = TextFieldValue(layout.name)
                            },
                            onDuplicate = { store.duplicateLayout(layout.id) },
                            onReset = { store.resetLayout(layout.id) },
                            onDelete = { deleteCandidate = layout },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        creating = true
                        nameInput = TextFieldValue("")
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.layout_create))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )

    if (creating || renaming != null) {
        AlertDialog(
            onDismissRequest = {
                creating = false
                renaming = null
            },
            title = {
                Text(
                    stringResource(if (creating) R.string.layout_create else R.string.layout_rename)
                )
            },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.layout_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    enabled = nameInput.text.isNotBlank(),
                    onClick = {
                        if (creating) store.createLayout(nameInput.text.trim())
                        else renaming?.let { store.renameLayout(it.id, nameInput.text.trim()) }
                        creating = false
                        renaming = null
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creating = false
                        renaming = null
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deleteCandidate?.let { layout ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.layout_delete)) },
            text = {
                Text(
                    if (layout.builtIn) stringResource(R.string.layout_delete_blocked)
                    else "\"" + layout.name + "\"?"
                )
            },
            confirmButton = {
                Button(
                    enabled = !layout.builtIn,
                    onClick = {
                        store.deleteLayout(layout.id)
                        deleteCandidate = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LayoutMenu(
    layout: KeyLayout,
    onSetDefault: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_set_default)) },
                onClick = {
                    open = false
                    onSetDefault()
                },
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_rename)) },
                onClick = {
                    open = false
                    onRename()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_duplicate)) },
                onClick = {
                    open = false
                    onDuplicate()
                },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_reset)) },
                onClick = {
                    open = false
                    onReset()
                },
                leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            )
            if (!layout.builtIn) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.layout_delete)) },
                    onClick = {
                        open = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                )
            }
        }
    }
}

private fun typeLabel(type: ExtraKeyType): String =
    when (type) {
        ExtraKeyType.TEXT -> "Text"
        ExtraKeyType.TERMINAL_KEY -> "Key"
        ExtraKeyType.MODIFIER -> "Modifier"
        ExtraKeyType.COMBO -> "Combo"
        ExtraKeyType.ESCAPE_SEQ -> "Escape"
        ExtraKeyType.COMMAND -> "Command"
    }

private fun valueHint(type: ExtraKeyType): String =
    when (type) {
        ExtraKeyType.TEXT -> "&&  ·  ~  ·  npm run dev"
        ExtraKeyType.TERMINAL_KEY -> "UP, PGUP, F5, …"
        ExtraKeyType.MODIFIER -> ""
        ExtraKeyType.COMBO -> "c, UP, …"
        ExtraKeyType.ESCAPE_SEQ -> "\\e[5~  ·  \\x1b[1;5A"
        ExtraKeyType.COMMAND -> "git status"
    }
