package com.uchat.android.linux.exec

import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.linux.Proot
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a one-shot command run inside the Ubuntu environment. */
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val success: Boolean
        get() = exitCode == 0

    val combined: String
        get() = (stdout + "\n" + stderr).trim()
}

/**
 * Thrown when a running command is stopped because the user paused the installation.
 * This is not an error: the installer persists its resume state, and the next resume
 * re-runs the step from the beginning (install scripts self-heal an interrupted dpkg
 * state, so killing a script mid-flight is safe).
 */
class ProcessPausedException : Exception("process paused by user")

/**
 * One-shot, captured command execution inside Ubuntu (no pty).
 *
 * Used by the installer, tool verification, git status, port detection and diagnostics. Always
 * argument-array based (spec #64); commands are executed via `proot ... <cmd argv>` with a timeout.
 *
 * Pause support: when [exec] is given a [pauseRequested] predicate, a watchdog thread polls it
 * and terminates the process tree as soon as the user asks to pause. Without the predicate the
 * behavior is identical to previous releases.
 */
class Shell(private val paths: UChatPaths, private val abi: DeviceAbi) {

    suspend fun exec(
        command: List<String>,
        workingDir: String = Proot.UBUNTU_HOME,
        timeoutSeconds: Long = 300,
        env: Map<String, String> = emptyMap(),
        onLine: ((String) -> Unit)? = null,
        pauseRequested: (() -> Boolean)? = null,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val argv = Proot.argv(paths, abi, command)
            val process =
                ProcessBuilder(argv)
                    .directory(File(paths.filesDir, "/"))
                    .apply {
                        environment()
                            .putAll(
                                Proot.environment(paths, abi).associate {
                                    val idx = it.indexOf('=')
                                    it.substring(0, idx) to it.substring(idx + 1)
                                }
                            )
                        environment().putAll(env)
                    }
                    .start()

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outThread = Thread {
                process.inputStream.bufferedReader().forEachLine { line ->
                    stdout.appendLine(line)
                    onLine?.invoke(line)
                }
            }
            val errThread = Thread {
                process.errorStream.bufferedReader().forEachLine { line ->
                    stderr.appendLine(line)
                    onLine?.invoke(line)
                }
            }
            outThread.start()
            errThread.start()

            // Pause watchdog: poll the flag while the process runs; on pause, SIGTERM first so
            // apt/dpkg can flush, then SIGKILL after a short grace period. The install scripts
            // recover an interrupted dpkg database on the next resume, so the kill is safe.
            val paused = AtomicBoolean(false)
            val watchdog =
                pauseRequested?.let { check ->
                    Thread {
                            try {
                                while (!check() && process.isAlive) Thread.sleep(250)
                                if (process.isAlive) {
                                    paused.set(true)
                                    process.destroy()
                                    var waited = 0
                                    while (process.isAlive && waited < 4000) {
                                        Thread.sleep(200)
                                        waited += 200
                                    }
                                    if (process.isAlive) process.destroyForcibly()
                                }
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                        }
                        .apply {
                            isDaemon = true
                            start()
                        }
                }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            watchdog?.interrupt()
            if (paused.get()) {
                outThread.join(5000)
                errThread.join(5000)
                throw ProcessPausedException()
            }
            if (!finished) {
                process.destroyForcibly()
                return@withContext ExecResult(
                    -1,
                    stdout.toString(),
                    "timeout after ${timeoutSeconds}s"
                )
            }
            outThread.join(5000)
            errThread.join(5000)
            ExecResult(process.exitValue(), stdout.toString(), stderr.toString())
        }

    /** Runs a shell script from the UChat scripts bind (path is inside Ubuntu). */
    suspend fun runScript(
        scriptNameInUbuntu: String,
        timeoutSeconds: Long = 1800,
        onLine: ((String) -> Unit)? = null,
        pauseRequested: (() -> Boolean)? = null,
    ): ExecResult =
        exec(
            listOf("/bin/bash", "-c", "bash $scriptNameInUbuntu"),
            timeoutSeconds = timeoutSeconds,
            onLine = onLine,
            pauseRequested = pauseRequested,
        )
}
