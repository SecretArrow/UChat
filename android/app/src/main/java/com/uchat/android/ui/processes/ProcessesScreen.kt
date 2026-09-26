package com.uchat.android.ui.processes

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.format.Format
import com.uchat.android.linux.PtySession
import com.uchat.android.linux.SessionState

/** Process manager (spec #10): live sessions with pid, runtime and actions. */
@Composable
fun ProcessesScreen(
    sessions: List<PtySession>,
    onAttach: (PtySession) -> Unit,
    onStop: (PtySession) -> Unit,
    onKill: (PtySession) -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.processes_title),
                style = MaterialTheme.typography.headlineMedium
            )
            if (sessions.any { it.state == SessionState.RUNNING }) {
                Button(onClick = onStopAll) { Text(stringResource(R.string.processes_stop_all)) }
            }
        }

        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.processes_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(session.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "PID ${session.pid} · ${session.workingDirectory}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    R.string.processes_runtime,
                                    Format.duration(
                                        (System.currentTimeMillis() - session.startedAtMillis) /
                                            1000
                                    ),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val stateText =
                                when (session.state) {
                                    SessionState.RUNNING ->
                                        stringResource(R.string.processes_running)
                                    SessionState.EXITED ->
                                        stringResource(
                                            R.string.processes_exited,
                                            session.exitCode ?: -1
                                        )
                                    else -> stringResource(R.string.install_step_status_pending)
                                }
                            Text(
                                stateText,
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (session.state == SessionState.RUNNING) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Row {
                                TextButton(onClick = { onAttach(session) }) {
                                    Text(stringResource(R.string.action_open), maxLines = 1)
                                }
                                TextButton(onClick = { onStop(session) }) {
                                    Text(stringResource(R.string.action_stop), maxLines = 1)
                                }
                                TextButton(onClick = { onKill(session) }) {
                                    Text(stringResource(R.string.action_kill), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
