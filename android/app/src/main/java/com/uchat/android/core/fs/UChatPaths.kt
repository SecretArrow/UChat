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
    private val nativeLibDir: File? = context.applicationInfo?.nativeLibraryDir?.let { File(it) }

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

    val isUbuntuInstalled: Boolean
        get() =
            File(ubuntuRoot, "bin/bash").isFile &&
                effectiveProotBinary.let { it.isFile && it.canExecute() }

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
}
