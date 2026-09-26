package com.uchat.android.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.BuildConfig
import com.uchat.android.R

/** "More" entry point: settings, diagnostics, processes, tools, servers. */
@Composable
fun MoreScreen(
    onOpenProcesses: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.headlineMedium)
        MoreItem(stringResource(R.string.processes_title), onOpenProcesses)
        MoreItem(stringResource(R.string.tools_title), onOpenTools)
        MoreItem(stringResource(R.string.servers_title), onOpenServers)
        MoreItem(stringResource(R.string.settings_title), onOpenSettings)
        MoreItem(stringResource(R.string.diagnostics_title), onOpenDiagnostics)

        Spacer(Modifier.height(8.dp))
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("UChat", style = MaterialTheme.typography.titleMedium)
                Text(
                    BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreItem(label: String, onClick: () -> Unit) {
    Card(Modifier.padding(0.dp)) {
        TextButton(onClick = onClick) { Text(label, style = MaterialTheme.typography.titleSmall) }
    }
}
