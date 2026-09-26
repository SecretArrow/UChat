package com.uchat.android.linux

import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.core.log.Logs
import java.io.File

/**
 * Builds the proot command line that turns the downloaded Ubuntu rootfs into a working rootless
 * userspace.
 *
 * Security rules (spec #44):
 * - Only argument arrays are used; no shell string concatenation.
 * - All bind sources are UChat-owned directories.
 * - The child is always `execve`d, never `sh -c`.
 */
object Proot {

    /** Absolute paths inside the Ubuntu environment. */
    const val UBUNTU_WORKSPACE = "/root/workspace"
    const val UBUNTU_DOWNLOADS = "/root/downloads"
    const val UBUNTU_SHARED = "/root/shared"
    const val UBUNTU_SCRIPTS = "/root/.uchat-scripts"
    const val UBUNTU_HOME = "/root"

    fun binaryFor(paths: UChatPaths, abi: DeviceAbi): File = paths.prootBinary

    /**
     * argv to run [innerCommand] inside the Ubuntu environment. [innerCommand] is an execve argv
     * array (never a shell string).
     */
    fun argv(
        paths: UChatPaths,
        abi: DeviceAbi,
        innerCommand: List<String>,
        withWorkspace: Boolean = true,
    ): List<String> {
        require(paths.isUbuntuInstalled) { "Ubuntu rootfs or proot binary is missing" }
        val args =
            mutableListOf(
                paths.prootBinary.absolutePath,
                "--kill-on-exit",
                "-0",
                "-w",
                UBUNTU_HOME,
                "-r",
                paths.ubuntuRoot.absolutePath,
                "-b",
                "/dev",
                "-b",
                "/proc",
                "-b",
                "/sys",
                "-b",
                "${paths.scriptsDir.absolutePath}:$UBUNTU_SCRIPTS",
            )
        if (withWorkspace) {
            args += listOf("-b", "${paths.workspaceDir.absolutePath}:$UBUNTU_WORKSPACE")
            args += listOf("-b", "${paths.downloadsDir.absolutePath}:$UBUNTU_DOWNLOADS")
            paths.sharedDir?.let { shared ->
                if (shared.exists()) args += listOf("-b", "${shared.absolutePath}:$UBUNTU_SHARED")
            }
        }
        args += innerCommand
        return args
    }

    /** Environment for every process started inside Ubuntu. */
    fun environment(
        abi: DeviceAbi,
        extra: Map<String, String> = emptyMap(),
    ): List<String> {
        val base =
            linkedMapOf(
                "TERM" to "xterm-256color",
                "LANG" to "C.UTF-8",
                "HOME" to UBUNTU_HOME,
                "SHELL" to "/bin/bash",
                "TMPDIR" to "/tmp",
                "PATH" to
                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.local/bin:/root/.bun/bin",
                "PROOT_NO_SECCOMP" to "1",
                "PROOT_TMP_DIR" to "/tmp",
            )
        base.putAll(extra)
        return base.map { (k, v) -> "$k=$v" }
    }

    /** Launches a long-lived pty session (interactive shell, opencode, claude...). */
    fun launchSession(
        paths: UChatPaths,
        abi: DeviceAbi,
        label: String,
        innerCommand: List<String>,
        workingDirInUbuntu: String = UBUNTU_HOME,
        extraEnv: Map<String, String> = emptyMap(),
        scope: kotlinx.coroutines.CoroutineScope,
    ): PtySession {
        val argv = argv(paths, abi, innerCommand)
        val handle =
            Pty.nativeOpenTerminal(
                argv.toTypedArray(),
                workingDirInUbuntu,
                environment(abi, extraEnv).toTypedArray(),
                24,
                80,
            )
        val session =
            PtySession(
                id = PtySession.nextId(),
                label = label,
                command = innerCommand,
                workingDirectory = workingDirInUbuntu,
                handle = handle,
                scope = scope,
            )
        session.start()
        return session
    }

    /** Copies DNS config into the rootfs so apt/curl resolve names (spec #27). */
    fun syncResolvConf(paths: UChatPaths) {
        try {
            val src = File("/etc/resolv.conf")
            val dst = File(paths.ubuntuRoot, "etc/resolv.conf")
            if (src.isFile && dst.parentFile != null) {
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
            }
        } catch (e: Exception) {
            Logs.network("resolv.conf sync failed: ${e.message}")
        }
    }
}
