package com.uchat.android.linux.install

import com.uchat.android.core.AppError

/** The 10 onboarding steps from the product spec (#4). */
enum class InstallStep(val id: Int, val isUserVisible: Boolean) {
    DOWNLOAD_ROOTFS(1, true),
    VERIFY_CHECKSUM(2, true),
    EXTRACT_ROOTFS(3, true),
    INITIALIZE_UBUNTU(4, true),
    INSTALL_ESSENTIALS(5, true),
    INSTALL_RUNTIMES(6, true),
    INSTALL_OPENCODE(7, true),
    INSTALL_CLAUDE(8, true),
    HEALTH_CHECK(9, true),
    READY(10, true),
    ;

    companion object {
        fun ordered(): List<InstallStep> = entries
    }
}

sealed class StepStatus {
    data object Pending : StepStatus()

    data object Running : StepStatus()

    data object Done : StepStatus()

    data class Failed(val error: AppError) : StepStatus()
}

/** Live progress of the whole installation, observed by the UI. */
data class InstallState(
    val currentStep: InstallStep? = null,
    val statuses: Map<InstallStep, StepStatus> =
        InstallStep.ordered().associateWith { StepStatus.Pending },
    val running: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val rootfsDownloadedBytes: Long = 0,
    val rootfsTotalBytes: Long = 0,
    val rootfsSpeedBps: Long = 0,
    val rootfsEtaSeconds: Long = 0,
    val logTail: List<String> = emptyList(),
    val fatal: AppError? = null,
)
