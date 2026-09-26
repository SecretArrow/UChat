package com.uchat.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user project living in the UChat workspace (spec #11). */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pathInUbuntu: String,
    val lastOpenedAt: Long = 0,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Metadata for terminal sessions/processes (spec #10, #46). */
@Entity(tableName = "processes")
data class ProcessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val label: String,
    val command: String,
    val workingDirectory: String,
    val pid: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val exitedAt: Long? = null,
    val exitCode: Int? = null,
    val autoRestart: Boolean = false,
)

/** Download history for the download manager UI. */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: String,
    val url: String,
    val targetPath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val startedAt: Long = System.currentTimeMillis(),
)

/** Terminal profiles (font size, theme per session kind). */
@Entity(tableName = "terminal_profiles")
data class TerminalProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fontSize: Int = 14,
    val command: String = "",
)

/** Known local development servers (spec #28). */
@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val port: Int,
    val pid: Long,
    val command: String,
    val projectPath: String = "",
    val lastSeenAt: Long = System.currentTimeMillis(),
)
