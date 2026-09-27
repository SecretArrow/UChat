package com.uchat.android.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.format.Format

/** One parsed row of the workspace listing. */
data class FileEntry(
    val name: String,
    val isDir: Boolean,
    val isLink: Boolean,
    val sizeBytes: Long,
    val mode: String,
)

/**
 * File manager over the Ubuntu filesystem (spec #12) — real listings from `find -printf`, executed
 * inside the proot environment. Every action (create, rename, delete, edit, extract, share,
 * compress) runs through the safe Shell layer guarded by [com.uchat.android.core.fs.FileOps].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    entries: List<FileEntry>,
    currentDir: String,
    loading: Boolean,
    showHidden: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateParent: () -> Unit,
    onOpenTerminalHere: (String) -> Unit,
    onCompress: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onCreateFile: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRename: (FileEntry, String) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onCopyPath: (String) -> Unit,
    onExtract: (FileEntry) -> Unit,
    onEdit: (FileEntry) -> Unit,
    onShare: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var createDialog by remember { mutableStateOf<CreativeKind?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var menuTarget by remember { mutableStateOf<FileEntry?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateParent) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.files_up)
                )
            }
            Text(
                currentDir,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleHidden) {
                Icon(
                    Icons.Filled.HideSource,
                    contentDescription = stringResource(R.string.files_hidden),
                    tint =
                        if (showHidden) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeaderMenu(
                onNewFile = { createDialog = CreativeKind.FILE },
                onNewFolder = { createDialog = CreativeKind.FOLDER },
                onOpenTerminalHere = { onOpenTerminalHere(currentDir) },
                onCompress = { onCompress(currentDir) },
                onRefresh = onRefresh,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (loading) {
            Text(
                stringResource(R.string.tools_checking),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (entries.isEmpty()) {
            Text(
                stringResource(R.string.files_empty_dir),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.name }) { entry ->
                    Card(
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (entry.isDir) onNavigate(entry.name) },
                                onLongClick = { menuTarget = entry },
                            )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (entry.isDir) Icons.Default.Folder
                                else Icons.Default.Description,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${entry.mode} · " +
                                        if (entry.isDir) "dir"
                                        else
                                            Format.bytes(entry.sizeBytes) +
                                                if (entry.isLink) " · link" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (entry.isDir) {
                                TextButton(onClick = { onNavigate(entry.name) }) {
                                    Text(stringResource(R.string.action_open))
                                }
                            } else {
                                TextButton(onClick = { menuTarget = entry }) {
                                    Text(stringResource(R.string.files_actions))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    createDialog?.let { kind ->
        NameDialog(
            title =
                stringResource(
                    if (kind == CreativeKind.FILE) R.string.files_new_file
                    else R.string.files_new_folder
                ),
            confirmLabel = stringResource(R.string.projects_create),
            onConfirm = { name ->
                if (kind == CreativeKind.FILE) onCreateFile(name) else onCreateFolder(name)
                createDialog = null
            },
            onDismiss = { createDialog = null },
        )
    }

    renameTarget?.let { entry ->
        NameDialog(
            title = stringResource(R.string.action_rename),
            initial = entry.name,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { name ->
                onRename(entry, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.files_delete_confirm, entry.name)) },
            text = {
                Text(
                    stringResource(R.string.files_delete_irreversible),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(entry)
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    menuTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text(entry.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (entry.isDir) {
                        ActionRow(stringResource(R.string.action_open)) {
                            onNavigate(entry.name)
                            menuTarget = null
                        }
                    }
                    if (!entry.isDir && com.uchat.android.core.fs.FileOps.isTextFile(entry.name)) {
                        ActionRow(stringResource(R.string.files_edit)) {
                            onEdit(entry)
                            menuTarget = null
                        }
                    }
                    if (!entry.isDir) {
                        ActionRow(stringResource(R.string.files_share)) {
                            onShare(entry)
                            menuTarget = null
                        }
                    }
                    if (!entry.isDir && com.uchat.android.core.fs.FileOps.isArchive(entry.name)) {
                        ActionRow(stringResource(R.string.files_extract)) {
                            onExtract(entry)
                            menuTarget = null
                        }
                    }
                    ActionRow(stringResource(R.string.action_rename)) {
                        renameTarget = entry
                        menuTarget = null
                    }
                    ActionRow(stringResource(R.string.files_copy_path)) {
                        onCopyPath(joinPath(currentDir, entry.name))
                        menuTarget = null
                    }
                    ActionRow(stringResource(R.string.action_delete)) {
                        deleteTarget = entry
                        menuTarget = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuTarget = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

private enum class CreativeKind {
    FILE,
    FOLDER
}

private fun joinPath(dir: String, name: String): String = if (dir == "/") "/$name" else "$dir/$name"

@Composable
private fun HeaderMenu(
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenTerminalHere: () -> Unit,
    onCompress: () -> Unit,
    onRefresh: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.MoreVert,
            contentDescription = null,
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_new_file)) },
            leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
            onClick = {
                open = false
                onNewFile()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_new_folder)) },
            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
            onClick = {
                open = false
                onNewFolder()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_compress)) },
            onClick = {
                open = false
                onCompress()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.files_open_terminal_here)) },
            onClick = {
                open = false
                onOpenTerminalHere()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.update_check)) },
            onClick = {
                open = false
                onRefresh()
            },
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val valid =
        name.trim().isNotEmpty() && com.uchat.android.core.fs.FileOps.isAcceptableName(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.files_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
