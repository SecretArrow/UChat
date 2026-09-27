package com.uchat.android.linux.install

import androidx.annotation.StringRes
import com.uchat.android.R
import com.uchat.android.core.AppError
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.format.Format
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.core.log.Logs
import com.uchat.android.data.registry.AssetEntry
import com.uchat.android.data.registry.AssetRegistry
import com.uchat.android.linux.Checksum
import com.uchat.android.linux.Extractor
import com.uchat.android.linux.Proot
import com.uchat.android.linux.downloader.Downloader
import com.uchat.android.linux.downloader.Downloader.PausedException
import com.uchat.android.linux.exec.Shell
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Resolves the localized installer error strings (EN + ID) without hard-wiring an Android Context
 * into the installer. AppContainer wires `applicationContext::getString`; JVM tests (and any
 * construction site that omits it) fall back to the English literals below, so error text is never
 * silently empty.
 */
fun interface InstallerStrings {
    fun get(@StringRes resId: Int, vararg args: Any): String
}

/** English fallback mirroring res/values/strings.xml — used only when no provider is wired. */
val EnglishInstallerStrings: InstallerStrings = InstallerStrings { resId, _ ->
    when (resId) {
        R.string.installer_error_unsupported_title -> "Unsupported architecture"
        R.string.installer_error_unsupported_reason -> "Ubuntu 24.04 has no rootfs for %1$s."
        R.string.installer_error_network_title -> "Network unreachable"
        R.string.installer_error_network_reason ->
            "Cannot reach %1$s. Check the internet connection and try again."
        R.string.installer_error_storage_title -> "Not enough storage"
        R.string.installer_error_storage_reason -> "%1$s free, but at least %2$s is required."
        R.string.installer_error_checksum_title -> "Checksum mismatch"
        R.string.installer_error_checksum_reason ->
            "The downloaded %1$s does not match the pinned SHA-256 — the download is corrupted."
        R.string.installer_error_apt_title -> "apt install failed"
        R.string.installer_error_runtime_title -> "Runtime install failed"
        R.string.installer_error_opencode_title -> "OpenCode install failed"
        R.string.installer_error_claude_title -> "Claude Code install failed"
        R.string.installer_error_health_title -> "Health check failed"
        R.string.installer_error_marker_title -> "Could not finalize installation"
        R.string.installer_error_marker_reason ->
            "Writing the completion marker failed — storage may be full or unavailable."
        R.string.installer_error_generic_title -> "Installation failed"
        R.string.installer_error_generic_reason -> "Unexpected error: %1$s"
        else -> ""
    }
}

/**
 * The Ubuntu 24.04 installer: a resumable 10-step state machine (spec #4).
 *
 * Guarantees:
 * - every asset is shown to the user with its real file size BEFORE download
 * - every byte is verified against the pinned SHA-256 BEFORE extraction
 * - pause / resume / retry / cancel are supported at every step
 * - a failed step never silently continues
 * - `isUbuntuInstalled` becomes true ONLY at step 10, when the ready marker is written
 */
class UbuntuInstaller(
    private val paths: UChatPaths,
    private val registry: AssetRegistry,
    private val scope: CoroutineScope,
    private val bundledProot: () -> File? = { null },
    private val strings: InstallerStrings = EnglishInstallerStrings,
) {

    private val downloader = Downloader()
    private val stateInternal = MutableStateFlow(InstallState())
    val state: StateFlow<InstallState> = stateInternal

    private var job: Job? = null
    private var resumableStep: InstallStep? = null

    val isRunning: Boolean
        get() = stateInternal.value.running

    fun updateLogTail(lines: List<String>) {
        stateInternal.value = stateInternal.value.copy(logTail = lines.takeLast(6))
    }

    /** Starts or resumes the installation from the first unfinished step. */
    fun start(abi: DeviceAbi) {
        if (isRunning) return
        job = scope.launch(Dispatchers.IO) { runInstall(abi) }
    }

    fun pause() {
        downloader.pause()
        stateInternal.value = stateInternal.value.copy(paused = true)
    }

    fun resume(abi: DeviceAbi) {
        stateInternal.value = stateInternal.value.copy(paused = false)
        start(abi)
    }

    fun cancel() {
        downloader.cancel()
        job?.cancel()
        stateInternal.value = stateInternal.value.copy(running = false, currentStep = null)
    }

    fun reset() {
        cancel()
        stateInternal.value = InstallState()
        resumableStep = null
    }

    // ------------------------------------------------------------------ //

    private fun setStepStatus(step: InstallStep, status: StepStatus) {
        val current = stateInternal.value
        stateInternal.value =
            current.copy(
                statuses = current.statuses + (step to status),
                currentStep = if (status is StepStatus.Running) step else current.currentStep,
            )
    }

    private fun appendLog(line: String) {
        Logs.installer(line)
        val current = stateInternal.value
        updateLogTail(current.logTail + line)
    }

    private suspend fun runInstall(abi: DeviceAbi) {
        val bundled = bundledProot()
        val requirement = registry.installRequirementFor(abi, prootBundled = bundled != null)
        val rootfsEntry = registry.rootfsFor(abi)
        val prootEntry = registry.prootFor(abi)

        if (rootfsEntry == null || requirement == null || (prootEntry == null && bundled == null)) {
            fail(
                InstallStep.DOWNLOAD_ROOTFS,
                AppError(
                    title = strings.get(R.string.installer_error_unsupported_title),
                    reason =
                        strings.get(R.string.installer_error_unsupported_reason, abi.androidAbi),
                ),
            )
            return
        }

        stateInternal.value = stateInternal.value.copy(running = true, paused = false, fatal = null)

        try {
            // Step 1 — download rootfs (resumable, size-verified)
            if (resumableStep == null)
                setStepStatus(InstallStep.DOWNLOAD_ROOTFS, StepStatus.Running)
            appendLog("rootfs: ${rootfsEntry.name}")

            // Pre-flight checks so failures surface in seconds, not after a 30 MB download.
            if (!probeUrl(rootfsEntry.url)) {
                fail(
                    InstallStep.DOWNLOAD_ROOTFS,
                    AppError(
                        title = strings.get(R.string.installer_error_network_title),
                        reason =
                            strings.get(R.string.installer_error_network_reason, rootfsEntry.url),
                    ),
                )
                return
            }
            val usable = paths.totalUsableBytes()
            if (usable < requirement.minFreeBytes) {
                fail(
                    InstallStep.DOWNLOAD_ROOTFS,
                    AppError(
                        title = strings.get(R.string.installer_error_storage_title),
                        reason =
                            strings.get(
                                R.string.installer_error_storage_reason,
                                Format.bytes(usable),
                                Format.bytes(requirement.minFreeBytes),
                            ),
                    ),
                )
                return
            }

            val rootfsFile =
                downloadAsset(rootfsEntry) { p ->
                    stateInternal.value =
                        stateInternal.value.copy(
                            rootfsDownloadedBytes = p.downloadedBytes,
                            rootfsTotalBytes = p.totalBytes,
                            rootfsSpeedBps = p.bytesPerSecond,
                            rootfsEtaSeconds = p.etaSeconds,
                        )
                }
            setStepStatus(InstallStep.DOWNLOAD_ROOTFS, StepStatus.Done)

            // Step 2 — verify checksums before anything touches disk layout
            setStepStatus(InstallStep.VERIFY_CHECKSUM, StepStatus.Running)
            appendLog("verifying ${rootfsEntry.id}")
            if (!Checksum.matches(rootfsFile, rootfsEntry.sha256)) {
                rootfsFile.delete()
                fail(
                    InstallStep.VERIFY_CHECKSUM,
                    AppError(
                        title = strings.get(R.string.installer_error_checksum_title),
                        reason = strings.get(R.string.installer_error_checksum_reason, "rootfs"),
                    ),
                )
                return
            }
            if (bundled == null) {
                val prootFile = downloadAsset(prootEntry!!)
                if (!Checksum.matches(prootFile, prootEntry.sha256)) {
                    prootFile.delete()
                    fail(
                        InstallStep.VERIFY_CHECKSUM,
                        AppError(
                            title = strings.get(R.string.installer_error_checksum_title),
                            reason = strings.get(R.string.installer_error_checksum_reason, "proot"),
                        ),
                    )
                    return
                }
                paths.runtimeDir.mkdirs()
                prootFile.copyTo(paths.prootBinary, overwrite = true)
                prootFile.delete()
                paths.prootBinary.setExecutable(true, false)
            } else {
                appendLog("proot bundled in APK — no download needed")
            }
            setStepStatus(InstallStep.VERIFY_CHECKSUM, StepStatus.Done)

            // Step 3 — extract rootfs (safe extractor)
            setStepStatus(InstallStep.EXTRACT_ROOTFS, StepStatus.Running)
            appendLog("extracting ${rootfsFile.name}")
            // A fresh full install re-extracts the rootfs: drop the old ready marker up-front
            // (it lives inside ubuntuRoot, so deleteRecursively removes it anyway — the explicit
            // call is for clarity and lets us log the transition).
            val hadMarker = paths.readyMarker.exists()
            if (resumableStep == null) {
                paths.unmarkInstalled()
                if (hadMarker) appendLog("fresh install — previous marker cleared")
            }
            if (paths.ubuntuRoot.exists()) {
                paths.ubuntuRoot.deleteRecursively()
            }
            Extractor.extractTarGz(
                archive = rootfsFile,
                destination = paths.ubuntuRoot,
                estimatedTotalBytes = rootfsEntry.extractedBytes,
            ) { done, total ->
                appendLog("extracted ${done}/${total} bytes")
            }
            rootfsFile.delete()
            setStepStatus(InstallStep.EXTRACT_ROOTFS, StepStatus.Done)

            paths.ensureDirs()

            // Step 4 — initialize Ubuntu (resolv.conf, hostname, dirs)
            setStepStatus(InstallStep.INITIALIZE_UBUNTU, StepStatus.Running)
            initializeUbuntu(paths)
            setStepStatus(InstallStep.INITIALIZE_UBUNTU, StepStatus.Done)

            val shell = Shell(paths, abi)

            // Step 5 — essential packages
            setStepStatus(InstallStep.INSTALL_ESSENTIALS, StepStatus.Running)
            val essentials =
                shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-essentials.sh") { line ->
                    appendLog(line)
                }
            if (!essentials.success) {
                fail(
                    InstallStep.INSTALL_ESSENTIALS,
                    AppError(
                        title = strings.get(R.string.installer_error_apt_title),
                        reason = essentials.stderr.take(400),
                        step = "install-essentials"
                    ),
                )
                return
            }
            setStepStatus(InstallStep.INSTALL_ESSENTIALS, StepStatus.Done)

            // Step 6 — runtimes (Node.js 22 + Python)
            setStepStatus(InstallStep.INSTALL_RUNTIMES, StepStatus.Running)
            val runtimes =
                shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-runtimes.sh") { line ->
                    appendLog(line)
                }
            if (!runtimes.success) {
                fail(
                    InstallStep.INSTALL_RUNTIMES,
                    AppError(
                        title = strings.get(R.string.installer_error_runtime_title),
                        reason = runtimes.stderr.take(400),
                        step = "install-runtimes"
                    ),
                )
                return
            }
            setStepStatus(InstallStep.INSTALL_RUNTIMES, StepStatus.Done)

            // Step 7 — OpenCode
            setStepStatus(InstallStep.INSTALL_OPENCODE, StepStatus.Running)
            val opencode =
                shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-opencode.sh") { line ->
                    appendLog(line)
                }
            if (!opencode.success) {
                fail(
                    InstallStep.INSTALL_OPENCODE,
                    AppError(
                        title = strings.get(R.string.installer_error_opencode_title),
                        reason = opencode.stderr.take(400),
                        step = "install-opencode"
                    ),
                )
                return
            }
            setStepStatus(InstallStep.INSTALL_OPENCODE, StepStatus.Done)

            // Step 8 — Claude Code
            setStepStatus(InstallStep.INSTALL_CLAUDE, StepStatus.Running)
            val claude =
                shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-claude.sh") { line ->
                    appendLog(line)
                }
            if (!claude.success) {
                fail(
                    InstallStep.INSTALL_CLAUDE,
                    AppError(
                        title = strings.get(R.string.installer_error_claude_title),
                        reason = claude.stderr.take(400),
                        step = "install-claude"
                    ),
                )
                return
            }
            setStepStatus(InstallStep.INSTALL_CLAUDE, StepStatus.Done)

            // Step 9 — health check
            setStepStatus(InstallStep.HEALTH_CHECK, StepStatus.Running)
            val health = shell.exec(listOf("/bin/bash", "${Proot.UBUNTU_SCRIPTS}/healthcheck.sh"))
            if (!health.success) {
                fail(
                    InstallStep.HEALTH_CHECK,
                    AppError(
                        title = strings.get(R.string.installer_error_health_title),
                        reason = health.combined.take(400),
                        step = "healthcheck"
                    ),
                )
                return
            }
            setStepStatus(InstallStep.HEALTH_CHECK, StepStatus.Done)

            // Step 10 — ready: persist the completion marker BEFORE declaring success. From now
            // on (and only now) does isUbuntuInstalled become true — the dashboard must not
            // replace the wizard a single step earlier.
            setStepStatus(InstallStep.READY, StepStatus.Running)
            try {
                paths.markInstalled()
            } catch (e: Exception) {
                fail(
                    InstallStep.READY,
                    AppError(
                        title = strings.get(R.string.installer_error_marker_title),
                        reason = strings.get(R.string.installer_error_marker_reason),
                        cause = e,
                    ),
                )
                return
            }
            setStepStatus(InstallStep.READY, StepStatus.Done)
            resumableStep = null
            stateInternal.value =
                stateInternal.value.copy(running = false, finished = true, currentStep = null)
            appendLog("installation finished successfully")
        } catch (e: PausedException) {
            appendLog("paused at ${e.downloadedBytes} bytes — state kept for resume")
            stateInternal.value = stateInternal.value.copy(running = false, paused = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            stateInternal.value = stateInternal.value.copy(running = false)
        } catch (e: Exception) {
            fail(
                stateInternal.value.currentStep ?: InstallStep.DOWNLOAD_ROOTFS,
                AppError(
                    title = strings.get(R.string.installer_error_generic_title),
                    reason =
                        strings.get(
                            R.string.installer_error_generic_reason,
                            e.message ?: e.javaClass.simpleName,
                        ),
                    cause = e,
                ),
            )
        }
    }

    private fun fail(step: InstallStep, error: AppError) {
        Logs.installer("FAILED at $step: ${error.reason}")
        setStepStatus(step, StepStatus.Failed(error.redacted))
        resumableStep = step
        stateInternal.value = stateInternal.value.copy(running = false, fatal = error.redacted)
    }

    private suspend fun downloadAsset(
        entry: AssetEntry,
        onProgress: (com.uchat.android.linux.downloader.DownloadProgress) -> Unit = {}
    ): File {
        val target = File(paths.downloadsDir, entry.id + ".download")
        return downloader.download(
            url = entry.url,
            target = target,
            expectedSizeBytes = entry.sizeBytes,
            onProgress = onProgress,
        )
    }

    /** Remote HEAD check so we can fail fast with a clear message (spec #27). */
    fun probeUrl(url: String): Boolean =
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.responseCode in 200..399
        } catch (e: Exception) {
            Logs.network("probe failed for $url: ${e.message}")
            false
        }

    private fun initializeUbuntu(paths: UChatPaths) {
        val etc = File(paths.ubuntuRoot, "etc")
        etc.mkdirs()
        Proot.syncResolvConf(paths)
        File(etc, "hostname").writeText("uchat\n")
        File(etc, "hosts").writeText("127.0.0.1 localhost uchat\n")
        // Ensure required mountpoint dirs exist (proot binds populate them).
        listOf("dev", "proc", "sys", "tmp", "root/workspace", "root/downloads").forEach {
            File(paths.ubuntuRoot, it).mkdirs()
        }
        File(paths.ubuntuRoot, "tmp").setReadable(true, false)
        File(paths.ubuntuRoot, "tmp").setWritable(true, false)
    }
}
