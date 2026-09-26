package com.uchat.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.format.Format
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.linux.install.InstallState
import com.uchat.android.ui.components.StatCard

/** Home dashboard (spec #36) — or the install wizard when Ubuntu is missing. */
@Composable
fun HomeScreen(
    paths: UChatPaths,
    installState: InstallState,
    runningProcesses: Int,
    projectCount: Int,
    networkConnected: Boolean,
    onOpenTerminal: () -> Unit,
    onLaunchOpenCode: () -> Unit,
    onLaunchClaude: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenFiles: () -> Unit,
    onInstallStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!paths.isUbuntuInstalled && !installState.running && !installState.finished) {
        InstallEntryScreen(
            paths = paths,
            installState = installState,
            onInstallStart = onInstallStart,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium)

        // Ubuntu status
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.home_status_ubuntu),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text =
                        when {
                            installState.running ->
                                stringResource(R.string.install_step_status_running)
                            paths.isUbuntuInstalled -> stringResource(R.string.home_status_running)
                            else -> stringResource(R.string.home_status_not_installed)
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (paths.isUbuntuInstalled) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = stringResource(R.string.home_storage),
                value = Format.bytes(paths.totalUsableBytes()),
                subtitle =
                    stringResource(
                        R.string.home_free_of,
                        Format.bytes(paths.totalUsableBytes()),
                        Format.bytes(paths.totalCapacityBytes()),
                    ),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = stringResource(R.string.home_network),
                value =
                    if (networkConnected) {
                        stringResource(R.string.home_network_connected)
                    } else {
                        stringResource(R.string.home_network_offline)
                    },
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                title = stringResource(R.string.home_processes),
                value = runningProcesses.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                title = stringResource(R.string.home_projects),
                value = projectCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenTerminal, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_open_terminal), maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onLaunchOpenCode, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_opencode), maxLines = 1)
            }
            OutlinedButton(onClick = onLaunchClaude, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.home_claude), maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onOpenProjects, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_projects), maxLines = 1)
            }
            OutlinedButton(onClick = onOpenFiles, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_files), maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** Compact welcome/install entry with the size preview summary. */
@Composable
private fun InstallEntryScreen(
    paths: UChatPaths,
    installState: InstallState,
    onInstallStart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.install_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            stringResource(R.string.install_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (installState.fatal != null || installState.paused) {
            Text(
                stringResource(R.string.install_resume_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onInstallStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.install_start))
        }
    }
}
