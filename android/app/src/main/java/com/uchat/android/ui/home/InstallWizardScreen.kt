package com.uchat.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.format.Format
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.data.registry.AssetRegistry
import com.uchat.android.linux.install.InstallState
import com.uchat.android.linux.install.InstallStep
import com.uchat.android.linux.install.StepStatus
import com.uchat.android.ui.components.ErrorCard
import com.uchat.android.ui.components.ProgressCard

/**
 * Onboarding wizard (spec #4, #40, #60, #69).
 *
 * Before anything downloads, the user sees:
 * - the detected ABI and whether Ubuntu supports it
 * - the exact pinned file sizes (rootfs + proot) from the registry
 * - required vs available storage and the install can be paused/resumed/retried at any step, with
 *   copyable errors when anything fails.
 */
@Composable
fun InstallWizardScreen(
    abi: DeviceAbi,
    registry: AssetRegistry,
    paths: UChatPaths,
    state: InstallState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: (DeviceAbi) -> Unit,
    onCancel: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requirement = registry.installRequirementFor(abi)
    val rootfsEntry = registry.rootfsFor(abi)
    val availableBytes = paths.totalUsableBytes()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.install_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            stringResource(R.string.install_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- Pre-install facts: sizes BEFORE download (the core ask) ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.install_detected_arch, abi.displayName),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        R.string.install_available_storage,
                        Format.bytes(availableBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (rootfsEntry != null) {
                    Text(
                        stringResource(
                            R.string.install_size_hint,
                            Format.bytes(rootfsEntry.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (requirement != null) {
                    Text(
                        stringResource(
                            R.string.install_required_storage,
                            Format.bytes(requirement.minFreeBytes),
                            Format.bytes(requirement.downloadBytes),
                            Format.bytes(requirement.extractedBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (availableBytes < requirement.minFreeBytes) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    if (availableBytes < requirement.minFreeBytes) {
                        Text(
                            stringResource(
                                R.string.install_insufficient_storage,
                                Format.bytes(requirement.minFreeBytes - availableBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.install_unsupported_arch, abi.androidAbi),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ---- Live download progress ----
        if (state.currentStep == InstallStep.DOWNLOAD_ROOTFS && state.rootfsTotalBytes > 0) {
            ProgressCard(
                title = stringResource(R.string.install_step_download),
                downloadedBytes = state.rootfsDownloadedBytes,
                totalBytes = state.rootfsTotalBytes,
                speedBps = state.rootfsSpeedBps,
                etaSeconds = state.rootfsEtaSeconds,
                speedText =
                    stringResource(R.string.install_speed, Format.speed(state.rootfsSpeedBps)),
                etaText = stringResource(R.string.install_eta, Format.eta(state.rootfsEtaSeconds)),
                bytesText =
                    stringResource(
                        R.string.install_progress_bytes,
                        Format.bytes(state.rootfsDownloadedBytes),
                        Format.bytes(state.rootfsTotalBytes),
                    ),
            )
        }

        // ---- Step list ----
        InstallStep.ordered().forEach { step ->
            val status = state.statuses[step] ?: StepStatus.Pending
            StepRow(step, status)
        }

        // ---- Log tail ----
        if (state.logTail.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    state.logTail.takeLast(5).forEach { line ->
                        Text(
                            line,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }

        state.fatal?.let { err ->
            ErrorCard(
                error = err,
                onRetry = { onResume(abi) },
            )
        }

        // ---- Controls ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.running && !state.finished) {
                Button(
                    onClick = onStart,
                    enabled =
                        requirement != null &&
                            availableBytes >= (requirement?.minFreeBytes ?: Long.MAX_VALUE),
                ) {
                    Text(stringResource(R.string.install_start))
                }
                if (state.paused) {
                    OutlinedButton(onClick = { onResume(abi) }) {
                        Text(stringResource(R.string.action_resume))
                    }
                }
                if (state.currentStep != null || state.paused) {
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
            if (state.running) {
                OutlinedButton(onClick = onPause) { Text(stringResource(R.string.action_pause)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            }
            if (state.finished) {
                Text(
                    stringResource(R.string.install_completed),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Button(onClick = onReady) { Text(stringResource(R.string.action_done)) }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepRow(step: InstallStep, status: StepStatus) {
    val label =
        when (step) {
            InstallStep.DOWNLOAD_ROOTFS -> stringResource(R.string.install_step_download)
            InstallStep.VERIFY_CHECKSUM -> stringResource(R.string.install_step_verify)
            InstallStep.EXTRACT_ROOTFS -> stringResource(R.string.install_step_extract)
            InstallStep.INITIALIZE_UBUNTU -> stringResource(R.string.install_step_init)
            InstallStep.INSTALL_ESSENTIALS -> stringResource(R.string.install_step_apt)
            InstallStep.INSTALL_RUNTIMES -> stringResource(R.string.install_step_runtimes)
            InstallStep.INSTALL_OPENCODE -> stringResource(R.string.install_step_opencode)
            InstallStep.INSTALL_CLAUDE -> stringResource(R.string.install_step_claude)
            InstallStep.HEALTH_CHECK -> stringResource(R.string.install_step_health)
            InstallStep.READY -> stringResource(R.string.install_step_ready)
        }
    val statusLabel =
        when (status) {
            is StepStatus.Pending -> stringResource(R.string.install_step_status_pending)
            is StepStatus.Running -> stringResource(R.string.install_step_status_running)
            is StepStatus.Done -> stringResource(R.string.install_step_status_done)
            is StepStatus.Failed -> stringResource(R.string.install_step_status_failed)
        }
    val color =
        when (status) {
            is StepStatus.Done -> MaterialTheme.colorScheme.tertiary
            is StepStatus.Running -> MaterialTheme.colorScheme.primary
            is StepStatus.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                "${step.id}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (status is StepStatus.Running) {
            LinearProgressIndicator(Modifier.width(48.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
