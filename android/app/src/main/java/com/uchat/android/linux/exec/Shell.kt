package com.uchat.android.linux.exec

import android.os.Process as AndroidProcess
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.linux.Proot
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
 * Thrown when a running command is stopped because the user paused the installation. This is not an
 * error: the installer persists its resume state, and the next resume re-runs the step from the
 * beginning (install scripts self-heal an interrupted dpkg state, so killing a script mid-flight is
 * safe).
 */
class ProcessPausedException : Exception("process paused by user")

/**
 * One-shot, captured command execution inside Ubuntu (no pty).
 *
 * Used by the installer, tool verification, git status, port detection and diagnostics. Always
 * argument-array based (spec #64); commands are executed via `proot ... <cmd argv>` with a timeout.
 *
 * Pause support: when [exec] is given a [pauseRequested] predicate, a watchdog thread polls it and
 * stops the process tree as soon as the user asks to pause. Without the predicate the behavior is
 * identical to previous releases.
 */
class Shell(private val paths: UChatPaths, private val abi: DeviceAbi) {

    suspend fun exec(
        command: List<String>,
        workingDir: String = Proot.UBUNTU_HOME,
        timeoutSeconds: Long = 300,
        env: Map<String, String> = emptyMap(),
        pauseRequested: (() -> Boolean)? = null,
        onLine: ((String) -> Unit)? = null,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val argv = Proot.argv(paths, abi, command)
            // The child is spawned through /system/bin/sh so it reports its own pid into a file
            // just before `exec`-ing proot (exec keeps the pid). Java has no portable pid accessor
            // at minSdk 26, and pause needs proot's pid to signal it directly.
            val pidFile = File(paths.filesDir, "uchat-exec-${System.nanoTime()}.pid")
            val wrappedArgv =
                mutableListOf(
                        "/system/bin/sh",
                        "-c",
                        "echo \$\$ > '${pidFile.absolutePath}'; exec \"\$@\"",
                        "uchat",
                    )
                    .apply { addAll(argv) }
            val process =
                ProcessBuilder(wrappedArgv)
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

            var prootPid = 0
            run {
                var attempts = 0
                while (prootPid == 0 && attempts < 20) {
                    prootPid = pidFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: 0
                    if (prootPid == 0) Thread.sleep(50)
                    attempts++
                }
            }
            pidFile.delete()

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            // Daemon reader threads that NEVER let exceptions escape: when a process is killed
            // on pause its pipe can be closed under the reader (InterruptedIOException) — an
            // uncaught exception here would crash the whole app process (seen on CI).
            val outThread = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        stdout.appendLine(line)
                        onLine?.invoke(line)
                    }
                } catch (_: Exception) {
                    // stream torn down — whatever was read is already captured
                }
            }
            val errThread = Thread {
                try {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        stderr.appendLine(line)
                        onLine?.invoke(line)
                    }
                } catch (_: Exception) {}
            }
            outThread.isDaemon = true
            errThread.isDaemon = true
            outThread.start()
            errThread.start()

            // Single waitpid consumer: ONLY the reaper waits on the child. Process.isAlive()
            // uses WNOHANG and can reap the zombie first, after which waitFor() blocks forever
            // (that exact race burned the first CI round — a pause took the full 60s timeout).
            val exited = CountDownLatch(1)
            val exitCode = AtomicInteger(Int.MIN_VALUE)
            val reaper = Thread {
                try {
                    exitCode.set(process.waitFor())
                } catch (_: InterruptedException) {}
                exited.countDown()
            }
            reaper.isDaemon = true
            reaper.start()

            // Pause watchdog: poll the flag while the process runs; on pause, SIGQUIT proot —
            // it IGNORES SIGTERM entirely (measured), but its SIGQUIT path kills the whole
            // tracee tree (bash/apt/dpkg) and exits, so nothing keeps draining data after a
            // pause and no pipe-holding orphans are left behind. SIGKILL is the last resort.
            val paused = AtomicBoolean(false)
            val watchdog =
                pauseRequested?.let { check ->
                    Thread {
                            try {
                                var stopped = false
                                while (!check()) {
                                    if (exited.await(250, TimeUnit.MILLISECONDS)) {
                                        stopped = true
                                        break
                                    }
                                }
                                if (!stopped && !exited.await(0, TimeUnit.MILLISECONDS)) {
                                    paused.set(true)
                                    if (prootPid > 0) {
                                        AndroidProcess.sendSignal(prootPid, 3) // SIGQUIT
                                    }
                                    process.destroy()
                                    var waited = 0
                                    while (
                                        waited < 4000 && !exited.await(200, TimeUnit.MILLISECONDS)
                                    ) {
                                        waited += 200
                                    }
                                    if (!exited.await(0, TimeUnit.MILLISECONDS)) {
                                        process.destroyForcibly()
                                    }
                                }
                            } catch (_: InterruptedException) {}
                        }
                        .apply {
                            isDaemon = true
                            start()
                        }
                }

            val finished = exited.await(timeoutSeconds, TimeUnit.SECONDS)
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
            val code = exitCode.get().let { if (it == Int.MIN_VALUE) -1 else it }
            ExecResult(code, stdout.toString(), stderr.toString())
        }

    /**
     * Runs a shell script from the UChat scripts bind (path is inside Ubuntu). [onLine] stays the
     * LAST parameter so existing trailing-lambda call sites keep compiling.
     */
    suspend fun runScript(
        scriptNameInUbuntu: String,
        timeoutSeconds: Long = 1800,
        pauseRequested: (() -> Boolean)? = null,
        onLine: ((String) -> Unit)? = null,
    ): ExecResult =
        exec(
            listOf("/bin/bash", "-c", "bash $scriptNameInUbuntu"),
            timeoutSeconds = timeoutSeconds,
            onLine = onLine,
            pauseRequested = pauseRequested,
        )
}
