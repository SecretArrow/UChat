package com.uchat.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.data.repo.SecretsRepository
import kotlinx.coroutines.launch

/** One row in the secrets list. */
data class SecretEntry(val key: String, val title: String, val envVar: String)

/**
 * API keys & secrets manager (spec #21 finally wired up).
 *
 * Keys are stored encrypted with Android Keystore via [SecretsRepository], injected as environment
 * variables into every Ubuntu session (so `opencode` / `claude` / `gh` just work) and never logged,
 * backed up or exported. The value itself is never displayed again after saving.
 */
@Composable
fun SecretsScreen(
    repository: SecretsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val entries =
        listOf(
            SecretEntry(
                key = SecretsRepository.OPENCODE_API_KEY,
                title = stringResource(R.string.secrets_opencode),
                envVar = "OPENCODE_API_KEY",
            ),
            SecretEntry(
                key = SecretsRepository.CLAUDE_API_KEY,
                title = stringResource(R.string.secrets_claude),
                envVar = "ANTHROPIC_API_KEY",
            ),
            SecretEntry(
                key = SecretsRepository.GITHUB_TOKEN,
                title = stringResource(R.string.secrets_github),
                envVar = "GITHUB_TOKEN / GH_TOKEN",
            ),
        )

    // Bump to re-read configured state after add/remove.
    var revision by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<SecretEntry?>(null) }
    var removing by remember { mutableStateOf<SecretEntry?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            stringResource(R.string.secrets_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            stringResource(R.string.secrets_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        entries.forEach { entry ->
            val configured = remember(revision, entry.key) { repository.get(entry.key) != null }
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        entry.envVar,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            if (configured) R.string.secrets_status_set
                            else R.string.secrets_status_unset
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (configured) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { editing = entry }) {
                            Text(
                                stringResource(
                                    if (configured) R.string.action_update else R.string.action_save
                                )
                            )
                        }
                        if (configured) {
                            OutlinedButton(
                                onClick = { removing = entry },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }

    editing?.let { entry ->
        var value by remember(entry.key) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(entry.title) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.tools_env_hint_secret),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(stringResource(R.string.secrets_value_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repository.put(entry.key, value.trim().toCharArray())
                            value = ""
                            revision++
                            editing = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.secrets_delete_confirm)) },
            text = { Text(entry.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.delete(entry.key)
                            revision++
                            removing = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
