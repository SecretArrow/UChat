package com.uchat.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.format.Format
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.core.settings.UChatSettings

/** Settings (spec #47 subset) with storage overview and reset. */
@Composable
fun SettingsScreen(
    settings: UChatSettings,
    paths: UChatPaths,
    ubuntuBytes: Long,
    workspaceBytes: Long,
    onFontSizeChange: (Int) -> Unit,
    onRestoreRebootChange: (Boolean) -> Unit,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onCleanup: () -> Unit,
    onReset: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_terminal),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.settings_font_size) + ": ${settings.terminalFontSize}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = settings.terminalFontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.toInt()) },
                    valueRange = 12f..24f,
                    steps = 11,
                )
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_background),
                    style = MaterialTheme.typography.titleMedium,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_restore_after_reboot),
                    checked = settings.restoreSessionsAfterReboot,
                    onChange = onRestoreRebootChange,
                )
                ToggleRow(
                    label = stringResource(R.string.settings_persistent_notification),
                    checked = settings.persistentNotification,
                    onChange = onPersistentNotificationChange,
                )
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_storage),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.storage_ubuntu) + ": " + Format.bytes(ubuntuBytes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.storage_workspace) +
                        ": " +
                        Format.bytes(workspaceBytes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.storage_free) +
                        ": " +
                        Format.bytes(paths.totalUsableBytes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onCleanup, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_cleanup))
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_security),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.tools_env_hint_secret),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.diagnostics_title))
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_reset),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.settings_reset_confirm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_reset))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
