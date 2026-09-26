package com.uchat.android.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.data.registry.ToolEntry

/** AI Tools hub (spec #71): install / launch / verify each registered tool. */
@Composable
fun ToolsScreen(
    tools: List<ToolEntry>,
    versions: Map<String, String>,
    busyTools: Set<String>,
    onCheck: (ToolEntry) -> Unit,
    onInstall: (ToolEntry) -> Unit,
    onLaunch: (ToolEntry) -> Unit,
    onConfigure: (ToolEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.headlineMedium)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(tools, key = { it.id }) { tool ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(tool.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.tools_category, tool.category),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val version = versions[tool.id]
                        val statusText =
                            when {
                                tool.id in busyTools -> stringResource(R.string.tools_checking)
                                version != null ->
                                    stringResource(R.string.tools_status_installed) +
                                        " · " +
                                        version
                                else -> stringResource(R.string.tools_status_missing)
                            }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (version != null) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (version == null && tool.installScript != null) {
                                Button(
                                    onClick = { onInstall(tool) },
                                    enabled = tool.id !in busyTools
                                ) {
                                    Text(stringResource(R.string.action_install), maxLines = 1)
                                }
                            }
                            if (version != null) {
                                OutlinedButton(onClick = { onLaunch(tool) }) {
                                    Text(stringResource(R.string.action_launch), maxLines = 1)
                                }
                            }
                            if (tool.supportsEnv) {
                                OutlinedButton(onClick = { onConfigure(tool) }) {
                                    Text(stringResource(R.string.action_configure), maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = { onCheck(tool) },
                                enabled = tool.id !in busyTools
                            ) {
                                Text(stringResource(R.string.update_check), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
