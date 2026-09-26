package com.uchat.android.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id") suspend fun byId(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update suspend fun update(project: ProjectEntity)

    @Delete suspend fun delete(project: ProjectEntity)

    @Query("UPDATE projects SET lastOpenedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)
}

@Dao
interface ProcessDao {
    @Query("SELECT * FROM processes ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ProcessEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(process: ProcessEntity): Long

    @Update suspend fun update(process: ProcessEntity)

    @Query(
        "UPDATE processes SET exitedAt = :time, exitCode = :code WHERE sessionId = :sessionId AND exitedAt IS NULL"
    )
    suspend fun markExited(sessionId: Long, code: Int, time: Long)

    @Query("SELECT * FROM processes WHERE autoRestart = 1 AND exitedAt IS NOT NULL")
    suspend fun autoRestartCandidates(): List<ProcessEntity>

    @Delete suspend fun delete(process: ProcessEntity)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update suspend fun update(download: DownloadEntity)
}

@Dao
interface TerminalProfileDao {
    @Query("SELECT * FROM terminal_profiles ORDER BY name")
    fun observeAll(): Flow<List<TerminalProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: TerminalProfileEntity): Long
}

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY port") fun observeAll(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(servers: List<ServerEntity>)

    @Query("DELETE FROM servers") suspend fun clear()
}
