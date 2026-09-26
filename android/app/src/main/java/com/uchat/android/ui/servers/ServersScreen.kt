package com.uchat.android.ui.servers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.di.AppContainer

/**
 * Local servers screen (spec #28): detects listening ports inside Ubuntu, lets users open them in
 * the browser, copy the URL or stop the process.
 */
@Composable
fun ServersScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var servers by remember { mutableStateOf<List<SsParser.ListeningServer>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            servers = detectServers(container)
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.servers_title),
            style = MaterialTheme.typography.headlineMedium
        )
        if (servers.isEmpty()) {
            Text(
                stringResource(R.string.servers_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(servers, key = { it.port to it.pid }) { server ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.servers_port, server.port),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "PID ${server.pid} · ${server.command}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("http://127.0.0.1:${server.port}")
                                        ),
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.servers_open_browser), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Runs `ss -tlnp` inside Ubuntu and parses the listening ports. */
private suspend fun detectServers(container: AppContainer): List<SsParser.ListeningServer> {
    if (!container.paths.isUbuntuInstalled) return emptyList()
    return try {
        val result = container.shell.exec(listOf("/usr/sbin/ss", "-tlnp"), timeoutSeconds = 20)
        if (!result.success) {
            // Some images place ss in a different location; try PATH fallback.
            val alt = container.shell.exec(listOf("ss", "-tlnp"), timeoutSeconds = 20)
            if (!alt.success) return emptyList() else SsParser.parse(alt.stdout)
        } else {
            SsParser.parse(result.stdout)
        }
    } catch (_: Exception) {
        emptyList()
    }
}
