package com.uchat.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities =
        [
            ProjectEntity::class,
            ProcessEntity::class,
            DownloadEntity::class,
            TerminalProfileEntity::class,
            ServerEntity::class,
        ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    abstract fun processDao(): ProcessDao

    abstract fun downloadDao(): DownloadDao

    abstract fun terminalProfileDao(): TerminalProfileDao

    abstract fun serverDao(): ServerDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "uchat.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
