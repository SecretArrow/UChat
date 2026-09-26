package com.uchat.android.ui.projects

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import com.uchat.android.core.fs.PathSafety
import com.uchat.android.data.db.ProjectEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Projects screen (spec #11). */
@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    onCreate: (String) -> Unit,
    onOpenTerminal: (ProjectEntity) -> Unit,
    onOpenOpenCode: (ProjectEntity) -> Unit,
    onOpenClaude: (ProjectEntity) -> Unit,
    onDelete: (ProjectEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.projects_title),
                style = MaterialTheme.typography.headlineMedium
            )
            TextButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.projects_new))
            }
        }

        if (projects.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.projects_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { project ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        project.pathInUbuntu,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.projects_last_opened,
                                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                                .withZone(ZoneId.systemDefault())
                                                .format(Instant.ofEpochMilli(project.lastOpenedAt)),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { onOpenTerminal(project) }) {
                                    Text(
                                        stringResource(R.string.projects_open_terminal),
                                        maxLines = 1
                                    )
                                }
                                TextButton(onClick = { onOpenOpenCode(project) }) {
                                    Text(
                                        stringResource(R.string.projects_open_opencode),
                                        maxLines = 1
                                    )
                                }
                                TextButton(onClick = { onOpenClaude(project) }) {
                                    Text(
                                        stringResource(R.string.projects_open_claude),
                                        maxLines = 1
                                    )
                                }
                                TextButton(onClick = { onDelete(project) }) {
                                    Text(stringResource(R.string.action_delete), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                onCreate(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val valid = PathSafety.isSafeName(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.projects_new)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.projects_name)) },
                    singleLine = true,
                    isError = name.isNotEmpty() && !valid,
                    supportingText = {
                        if (name.isNotEmpty() && !valid) {
                            Text(stringResource(R.string.projects_invalid_name))
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = valid) {
                Text(stringResource(R.string.projects_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
