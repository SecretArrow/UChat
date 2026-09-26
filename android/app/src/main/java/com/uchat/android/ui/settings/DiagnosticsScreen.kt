package com.uchat.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.AppError
import com.uchat.android.core.arch.DeviceSummary
import com.uchat.android.core.format.Format
import com.uchat.android.ui.components.ErrorCard

/**
 * Diagnostics screen (spec #42): device + environment facts, health check, copy/export actions.
 * Never includes secrets (see [buildDiagnostics]).
 */
@Composable
fun DiagnosticsScreen(
    device: DeviceSummary?,
    toolVersions: Map<String, String>,
    uchatVersion: String,
    onRunHealthCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var lastError by remember { mutableStateOf<AppError?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text(
            stringResource(R.string.diagnostics_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                val stat = StatFs("/data")
                val avail = Format.bytes(stat.availableBytes)
                Text(
                    stringResource(R.string.diagnostics_android_version) +
                        ": " +
                        Build.VERSION.RELEASE,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.diagnostics_device) + ": " + Build.MODEL,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.diagnostics_cpu_abi) +
                        ": " +
                        (device?.abi?.displayName ?: Build.SUPPORTED_ABIS.firstOrNull()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.diagnostics_storage) + ": " + avail,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.diagnostics_uchat_version) + ": " + uchatVersion,
                    style = MaterialTheme.typography.bodyMedium,
                )
                toolVersions.forEach { (name, version) ->
                    Text("$name: $version", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        lastError?.let {
            ErrorCard(
                error = it,
                onRetry = onRunHealthCheck,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Button(onClick = onRunHealthCheck, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.diagnostics_run))
        }
        OutlinedButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(
                    ClipData.newPlainText(
                        "UChat diagnostics",
                        buildDiagnostics(device, toolVersions, uchatVersion)
                    )
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.diagnostics_copy))
        }
        Text(
            stringResource(R.string.diagnostics_no_secrets),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun buildDiagnostics(
    device: DeviceSummary?,
    tools: Map<String, String>,
    version: String
): String = buildString {
    appendLine("UChat diagnostics")
    appendLine("android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")")
    appendLine("device: " + Build.MANUFACTURER + " " + Build.MODEL)
    appendLine("abi: " + (device?.abi?.androidAbi ?: Build.SUPPORTED_ABIS.joinToString(",")))
    appendLine("uchat: " + version)
    tools.forEach { (k, v) -> appendLine("$k: $v") }
}
