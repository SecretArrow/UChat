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

    /** In-guest path of the session wrapper (see [wrapWithSessionRunner]). */
    const val SESSION_RUNNER = "$UBUNTU_SCRIPTS/session-run.sh"

    /**
     * Wraps [innerCommand] in the session runner script when it exists on disk.
     *
     * The runner raises RLIMIT_STACK inside the guest before exec — Bun standalone binaries
     * (opencode, Claude Code) deep-recurse JSC on the main thread and die with "Segmentation fault
     * / signal 5" seconds after start when stuck with Android's small app stack (opencode#35384,
     * the "sering session exited" crash report with exit code 255).
     *
     * Pure so the JVM unit tests can pin the argv shape: the restore-after-reboot path persists
     * `command.joinToString(" ")` and splits it back later, so no argv element may contain a space
     * — the runner path (bind mount) and tool names never do.
     */
    fun wrapWithSessionRunner(innerCommand: List<String>, runnerFile: File?): List<String> {
        if (innerCommand.isEmpty()) return innerCommand
        if (runnerFile == null || !runnerFile.isFile) return innerCommand
        return listOf(runnerFile.absolutePath) + innerCommand
    }

    fun binaryFor(paths: UChatPaths, abi: DeviceAbi): File = paths.effectiveProotBinary

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
        // BOOTSTRAP check (bash + proot), NOT the step-10 ready marker: the installer itself
        // runs scripts (steps 5-9) after extraction but before completion. Gating this on
        // isUbuntuInstalled made v1.5.0 crash at step 5 on every device ("rootfs or proot binary
        // is missing") and burned the user's data plan with rootfs re-downloads on each retry.
        require(paths.isUbuntuBootstrapped) {
            val missing = buildList {
                if (!File(paths.ubuntuRoot, "bin/bash").isFile)
                    add("/bin/bash (rootfs not extracted)")
                val proot = paths.effectiveProotBinary
                if (!proot.isFile || !proot.canExecute()) add("proot (${proot.absolutePath})")
            }
            if (missing.isEmpty()) {
                "Ubuntu environment is not bootstrapped yet (transient state check failed)"
            } else {
                "Ubuntu environment is not bootstrapped yet — missing: ${missing.joinToString(", ")}"
            }
        }
        val args =
            mutableListOf(
                paths.effectiveProotBinary.absolutePath,
                "--kill-on-exit",
                "-0",
                // dpkg backs up its status database with hardlink(status, status-old) —
                // Android's SELinux policy denies `link` to app domains, so every apt write
                // dies with "E: Sub-process /usr/bin/dpkg returned an error code (2)" (the
                // v1.6.0 field report). link2symlink transparently converts those hardlinks
                // to symlinks — the same solution Termux's proot-distro ships.
                "--link2symlink",
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
        paths: UChatPaths? = null,
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
                    "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/root/.local/bin:/root/.bun/bin:/root/.opencode/bin",
                // NOTE: PROOT_NO_SECCOMP must stay UNSET. It was set here from v1.0.0 (proot-me
                // era) and silently broke every dpkg rename on x86_64 emulators/modern Android:
                // with seccomp filtering disabled, proot's avoider + SIGSYS rewrite path for
                // old syscalls (rename -> renameat) does not run and rename() returns ENOSYS
                // ("error installing new file '/var/lib/dpkg/status': Function not implemented").
                // Confirmed upstream: termux/proot#390 — "should be used only for debugging
                // purposes; sometimes it creates more issues than it resolves". If a kernel
                // refuses proot's seccomp filter, proot already degrades gracefully to ptrace.
                // proot itself creates temp files on the HOST before starting the guest; Android
                // has no /tmp, so point it at the app cache dir whenever we know it.
                "PROOT_TMP_DIR" to (paths?.cacheDir?.absolutePath ?: "/tmp"),
                // ---- Bun/JSC stability under proot ("kok sering session exited", exit 255) ----
                // opencode and Claude Code are `bun build --compile` binaries; Bun 1.3.x on
                // arm64 dies with a segfault/SIGTRAP panic seconds after start (JSC corruption
                // family: opencode#34054/#33890, oven-sh/bun#32632). Bun reads BUN_JSC_* from
                // the real environment at startup — verified to work for compiled binaries too
                // — and every other program in the guest simply ignores them. Running on the
                // interpreter is slower but stable; a dead session is infinitely slower.
                "BUN_JSC_useJIT" to "0",
                "BUN_JSC_useFTLJIT" to "0",
                "BUN_JSC_useDFGJIT" to "0",
                "BUN_JSC_useBaselineJIT" to "0",
                "BUN_JSC_useLLInt" to "1",
                "BUN_JSC_useWasmIPInt" to "0",
                // Bun ≥ 1.4 honours this flag to skip epoll_pwait2, which Android seccomp blocks
                // with no ENOSYS fallback in some builds (oven-sh/bun#32489). No-op on 1.3.x,
                // vital the moment opencode/claude ship Bun 1.4+.
                "BUN_FEATURE_FLAG_DISABLE_EPOLL_PWAIT2" to "1",
                // Stop bun.report crash-report uploads and tool telemetry — each crash previously
                // streamed data over the user's metered mobile plan.
                "DO_NOT_TRACK" to "1",
                // Claude Code updater: never swap the binary mid-session (new builds have
                // historically been the crashy ones on Android).
                "DISABLE_AUTOUPDATER" to "1",
                "DISABLE_TELEMETRY" to "1",
            )
        // The bundled proot (Termux/NDK build) needs its companion libs (libtalloc.so,
        // libandroid-shmem.so) and its LOADER binaries, which live next to it in
        // nativeLibraryDir. Without PROOT_LOADER the guest execve fails with ENOENT.
        paths?.nativeLibDir?.let { libDir ->
            base["LD_LIBRARY_PATH"] = libDir.absolutePath
            val loader = File(libDir, "libprootloader.so")
            if (loader.isFile) base["PROOT_LOADER"] = loader.absolutePath
            val loader32 = File(libDir, "libprootloader32.so")
            if (loader32.isFile) base["PROOT_LOADER_32"] = loader32.absolutePath
        }
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
        // Wrap in the session runner (stack-limit raise) when it is installed. ScriptInstaller
        // copies it on every app start; the graceful fallback keeps direct-launch callers (E2E,
        // first frame after an update) working even if the copy has not happened yet.
        val wrapped = wrapWithSessionRunner(innerCommand, File(paths.scriptsDir, "session-run.sh"))
        if (wrapped !== innerCommand) {
            Logs.process("session '$label' wrapped by session-run.sh (RLIMIT_STACK raise)")
        }
        val argv = argv(paths, abi, wrapped)
        val handle =
            Pty.nativeOpenTerminal(
                argv.toTypedArray(),
                // Host-side cwd for the child BEFORE proot starts; /root does not exist on the
                // host. proot's own -w sets the cwd inside the guest.
                paths.filesDir.absolutePath,
                environment(paths, abi, extraEnv).toTypedArray(),
                24,
                80,
            )
        val session =
            PtySession(
                id = PtySession.nextId(),
                label = label,
                command = wrapped,
                workingDirectory = workingDirInUbuntu,
                handle = handle,
                scope = scope,
            )
        session.start()
        return session
    }

    /**
     * Copies DNS config into the rootfs so apt/curl resolve names (spec #27).
     *
     * Android itself has NO /etc/resolv.conf (DNS lives in netd), so when the host file is missing
     * or has no nameserver we write public resolvers — otherwise apt fails instantly and the
     * install dies at the "essentials" step with no visible cause.
     */
    fun syncResolvConf(paths: UChatPaths) {
        try {
            val dst = File(paths.ubuntuRoot, "etc/resolv.conf")
            dst.parentFile?.mkdirs()
            var content =
                try {
                    File("/etc/resolv.conf").takeIf { it.isFile }?.readText() ?: ""
                } catch (_: Exception) {
                    ""
                }
            if (!content.contains("nameserver")) {
                content = "nameserver 1.1.1.1\nnameserver 8.8.8.8\nnameserver 9.9.9.9\n"
            }
            dst.writeText(content)
        } catch (e: Exception) {
            Logs.network("resolv.conf sync failed: ${e.message}")
        }
    }
}
