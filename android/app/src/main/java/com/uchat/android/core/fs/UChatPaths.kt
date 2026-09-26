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

    val prootBinary: File
        get() = File(runtimeDir, "proot")

    val scriptsDir: File
        get() = File(filesDir, "scripts")

    val isUbuntuInstalled: Boolean
        get() = File(ubuntuRoot, "bin/bash").isFile && prootBinary.isFile

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
