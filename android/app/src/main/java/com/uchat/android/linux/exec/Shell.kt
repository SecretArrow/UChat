package com.uchat.android.linux.exec

import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.linux.Proot
import java.io.File
import java.util.concurrent.TimeUnit
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
 * One-shot, captured command execution inside Ubuntu (no pty).
 *
 * Used by the installer, tool verification, git status, port detection and diagnostics. Always
 * argument-array based (spec #64); commands are executed via `proot ... <cmd argv>` with a timeout.
 */
class Shell(private val paths: UChatPaths, private val abi: DeviceAbi) {

    suspend fun exec(
        command: List<String>,
        workingDir: String = Proot.UBUNTU_HOME,
        timeoutSeconds: Long = 300,
        env: Map<String, String> = emptyMap(),
        onLine: ((String) -> Unit)? = null,
    ): ExecResult =
        withContext(Dispatchers.IO) {
            val argv = Proot.argv(paths, abi, command)
            val process =
                ProcessBuilder(argv)
                    .directory(File(paths.filesDir, "/"))
                    .apply {
                        environment()
                            .putAll(
                                Proot.environment(abi).associate {
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

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
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
    ): ExecResult =
        exec(
            listOf("/bin/bash", "-c", "bash $scriptNameInUbuntu"),
            timeoutSeconds = timeoutSeconds,
            onLine = onLine,
        )
}
