package com.uchat.android.ui.tools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uchat.android.R
import com.uchat.android.core.AppError
import com.uchat.android.data.registry.ToolEntry

/**
 * AI Tools hub (spec #71): install / launch / verify each registered tool.
 *
 * Every install/check is VISIBLE: busy spinner, streamed log tail, structured error card with a
 * one-tap copy button. Nothing fails silently — that silence was the original "install does
 * nothing" complaint.
 */
@Composable
fun ToolsScreen(
    tools: List<ToolEntry>,
    toolStates: Map<String, ToolUiState>,
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
                ToolCard(
                    tool = tool,
                    state = ToolsController.stateOf(toolStates, tool.id),
                    onCheck = { onCheck(tool) },
                    onInstall = { onInstall(tool) },
                    onLaunch = { onLaunch(tool) },
                    onConfigure = { onConfigure(tool) },
                )
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolEntry,
    state: ToolUiState,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onLaunch: () -> Unit,
    onConfigure: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tool.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.tools_category, tool.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val statusText =
                when {
                    state.busy && state.version != null -> stringResource(R.string.tools_checking)
                    state.busy -> stringResource(R.string.tools_working)
                    state.version != null ->
                        stringResource(R.string.tools_status_installed) + " · " + state.version
                    else -> stringResource(R.string.tools_status_missing)
                }
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    when {
                        state.version != null -> MaterialTheme.colorScheme.tertiary
                        state.busy -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )

            if (state.logTail.isNotEmpty()) {
                LogTail(state.logTail)
            }

            state.error?.let { message ->
                ErrorCard(
                    error =
                        AppError(
                            title = stringResource(R.string.tools_install_failed),
                            reason = message
                        ),
                    onRetry = onInstall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.version == null && tool.installScript != null) {
                    Button(onClick = onInstall, enabled = !state.busy) {
                        Text(stringResource(R.string.action_install), maxLines = 1)
                    }
                }
                if (state.version != null) {
                    OutlinedButton(onClick = onLaunch) {
                        Text(stringResource(R.string.action_launch), maxLines = 1)
                    }
                }
                if (tool.supportsEnv) {
                    OutlinedButton(onClick = onConfigure) {
                        Text(stringResource(R.string.action_configure), maxLines = 1)
                    }
                }
                OutlinedButton(onClick = onCheck, enabled = !state.busy) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(stringResource(R.string.update_check), maxLines = 1)
                }
            }
        }
    }
}

/** Small monospace pane showing the last streamed installer lines. */
@Composable
private fun LogTail(lines: List<String>) {
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            lines.forEach { line ->
                Text(
                    line.take(120),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One-tap copy for a tool failure message (the user-visible part of the error). */
@Composable
private fun ErrorCard(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                error.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                error.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString("${error.title}\n${error.reason}"))
                        Toast.makeText(
                                context,
                                context.getString(R.string.action_copied),
                                Toast.LENGTH_SHORT
                            )
                            .show()
                    },
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(stringResource(R.string.action_copy_error))
                }
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

/** Small monospace pane showing the last streamed installer lines. */
