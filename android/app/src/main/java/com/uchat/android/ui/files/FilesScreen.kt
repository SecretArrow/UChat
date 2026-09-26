package com.uchat.android.ui.files

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * inside the proot environment. Archive and file actions run through the safe Shell layer.
 */
@Composable
fun FilesScreen(
    entries: List<FileEntry>,
    currentDir: String,
    loading: Boolean,
    onNavigate: (String) -> Unit,
    onNavigateParent: () -> Unit,
    onOpenTerminalHere: (String) -> Unit,
    onCompress: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.update_check), maxLines = 1)
            }
            TextButton(onClick = { onOpenTerminalHere(currentDir) }) {
                Text(stringResource(R.string.files_open_terminal_here), maxLines = 1)
            }
            TextButton(onClick = { onCompress(currentDir) }) {
                Text(stringResource(R.string.files_compress), maxLines = 1)
            }
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
                    Card(Modifier.fillMaxWidth()) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}
