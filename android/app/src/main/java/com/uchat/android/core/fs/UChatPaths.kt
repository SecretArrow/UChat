package com.uchat.android.core.fs

import android.content.Context
import java.io.File

/**
 * Single source of truth for the UChat internal directory layout (spec #82). Everything lives
 * inside app-private storage; the optional shared directory is the app-specific external files dir,
 * which needs no permission.
 */
class UChatPaths(context: Context) {

    val filesDir: File = context.filesDir
    val ubuntuRoot: File
        get() = File(filesDir, "ubuntu")

    val runtimeDir: File
        get() = File(filesDir, "runtime")

    val workspaceDir: File
        get() = File(filesDir, "workspace")

    val projectsDir: File
        get() = File(filesDir, "workspace/projects")

    val downloadsDir: File
        get() = File(filesDir, "downloads")

    val archivesDir: File
        get() = File(filesDir, "archives")

    val backupsDir: File
        get() = File(filesDir, "backups")

    val logsDir: File
        get() = File(filesDir, "logs")

    val exportsDir: File
        get() = File(filesDir, "exports")

    val cacheDir: File = context.cacheDir
    val sharedDir: File? = context.getExternalFilesDir(null)
    /** Directory where the package manager extracts bundled native binaries (incl. proot). */
    val nativeLibDir: File? = context.applicationInfo?.nativeLibraryDir?.let { File(it) }

    val prootBinary: File
        get() = File(runtimeDir, "proot")

    /**
     * proot shipped inside the APK as libproot.so. The package manager extracts it into
     * nativeLibraryDir, which — unlike the writable data dir — is EXECUTABLE on Android 10+ for
     * apps targeting API 29+ (W^X policy). This is the only reliable way to exec proot.
     */
    val bundledProot: File?
        get() = nativeLibDir?.let { libDir -> File(libDir, "libproot.so").takeIf { it.isFile } }

    /**
     * The proot binary to exec: the bundled one when present, the downloaded fallback otherwise.
     */
    val effectiveProotBinary: File
        get() = bundledProot ?: prootBinary

    val scriptsDir: File
        get() = File(filesDir, "scripts")

    /**
     * Written by the installer ONLY after every step (1–10) has succeeded — step 10. A bare
     * /bin/bash is NOT proof of a finished install: the ubuntu-base tarball ships it, which once
     * made the dashboard appear right after extraction while steps 4–10 were still failing.
     */
    val readyMarker: File
        get() = File(ubuntuRoot, ".uchat-ready")

    val isUbuntuInstalled: Boolean
        get() {
            val marker = readyMarker
            if (!marker.isFile) {
                // Pre-marker (≤ v1.4.0) installs: promote the healthy ones lazily so existing
                // users are not thrown back into the wizard after upgrading. No-op for fresh
                // or half-broken rootfs trees.
                migrateLegacyInstall(ubuntuRoot, effectiveProotBinary, marker)
            }
            return isInstalled(ubuntuRoot, effectiveProotBinary, marker)
        }

    /**
     * The environment can EXECUTE proot commands (bash + proot present) but is not necessarily
     * fully installed yet. This is exactly the state the installer's own steps 5-9 run in — they
     * must never be gated on the step-10 ready marker. Regression: v1.5.0 gated Proot.argv on
     * isUbuntuInstalled, so step 5 crashed with "rootfs or proot binary is missing" on every real
     * device, and every retry re-downloaded the rootfs.
     */
    val isUbuntuBootstrapped: Boolean
        get() = isBootstrapped(ubuntuRoot, effectiveProotBinary)

    /** Marks the environment as fully installed (called by the installer at step 10). */
    fun markInstalled() {
        readyMarker.parentFile?.mkdirs()
        readyMarker.writeText(System.currentTimeMillis().toString())
    }

    /** Removes the ready marker (fresh full install, reset, corrupted environment). */
    fun unmarkInstalled() {
        readyMarker.delete()
    }

    /**
     * Persisted installer resume state: the id of the step a previous run failed at. Kept in a
     * plain file so a retry still skips completed work after the process dies (the installer's
     * in-memory [resumableStep] does not survive process death).
     */
    val installResumeFile: File
        get() = File(filesDir, "install-resume.txt")

    /** Persists the failed step id (called by the installer on every failure). */
    fun saveInstallResume(stepId: Int) {
        try {
            installResumeFile.writeText(stepId.toString())
        } catch (_: Exception) {
            // Resume persistence is an optimization — never fail the installer over it.
        }
    }

    /** Clears the persisted resume state (installer success or explicit reset). */
    fun clearInstallResume() {
        try {
            installResumeFile.delete()
        } catch (_: Exception) {}
    }

    /**
     * Removes leftover link2symlink bookkeeping from the dpkg database. proot's
     * `--link2symlink` extension turns dpkg's hardlink(status, status-old) into symlinks and
     * tracks them in `.l2s.*` files that are HIDDEN inside the guest but live as real files
     * here on the host. A run killed mid-dpkg-write can leave them behind, and stale state
     * makes the NEXT dpkg backup fail ("Operation not permitted"). The guest cannot see or
     * delete these files — only the app (host side) can.
     */
    fun cleanDpkgLinkDebris() {
        try {
            val dpkgDir = File(ubuntuRoot, "var/lib/dpkg")
            if (!dpkgDir.isDirectory) return
            dpkgDir.listFiles { f -> f.isFile }?.forEach { f ->
                if (f.name.startsWith(".l2s.") || f.name == "status-new") {
                    f.delete()
                }
            }
        } catch (_: Exception) {
            // Best-effort hygiene — never fail the installer over it.
        }
    }

    /** Reads the persisted failed step id, or null when absent/corrupt. */
    fun loadInstallResume(): Int? =
        try {
            installResumeFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        } catch (_: Exception) {
            null
        }

    /** Creates the whole layout; safe to call repeatedly. */
    fun ensureDirs() {
        listOf(
                ubuntuRoot,
                runtimeDir,
                workspaceDir,
                projectsDir,
                downloadsDir,
                archivesDir,
                backupsDir,
                logsDir,
                exportsDir,
                scriptsDir,
            )
            .forEach { it.mkdirs() }
        sharedDir?.mkdirs()
    }

    fun totalUsableBytes(): Long = filesDir.usableSpace

    fun totalCapacityBytes(): Long = filesDir.totalSpace

    companion object {
        /**
         * Pure bootstrap decision (unit-testable, no Android context): /bin/bash is present AND an
         * executable proot binary is available. Deliberately marker-free — the installer runs proot
         * commands between extraction (step 3) and completion (step 10).
         */
        fun isBootstrapped(ubuntuRoot: File, proot: File?): Boolean =
            File(ubuntuRoot, "bin/bash").isFile &&
                proot != null &&
                proot.isFile &&
                proot.canExecute()

        /**
         * Pure install decision (unit-testable, no Android context): the environment counts as
         * installed only when the step-10 ready marker exists, /bin/bash is present AND an
         * executable proot binary is available.
         */
        fun isInstalled(ubuntuRoot: File, proot: File?, marker: File): Boolean =
            marker.isFile &&
                File(ubuntuRoot, "bin/bash").isFile &&
                proot != null &&
                proot.isFile &&
                proot.canExecute()

        /**
         * One-time migration of pre-marker (≤ v1.4.0) installs: those rootfs trees already carry
         * apt-installed tooling. `/usr/bin/git` is installed by install-essentials.sh (step 5), so
         * git + bash + proot proves steps 1–5 finished and the install is healthy → write the
         * marker and return true. bash WITHOUT git is a broken half-install → return false so the
         * wizard reappears and a reinstall repairs it. Never throws; write failures simply report
         * "not migrated".
         */
        fun migrateLegacyInstall(ubuntuRoot: File, proot: File?, marker: File): Boolean {
            if (marker.isFile) return false
            val prootOk = proot != null && proot.isFile && proot.canExecute()
            val healthy =
                File(ubuntuRoot, "bin/bash").isFile &&
                    File(ubuntuRoot, "usr/bin/git").isFile &&
                    prootOk
            if (!healthy) return false
            return try {
                marker.parentFile?.mkdirs()
                marker.writeText(System.currentTimeMillis().toString())
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
