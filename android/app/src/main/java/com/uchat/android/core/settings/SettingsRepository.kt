package com.uchat.android.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "uchat_settings")

/** User-controlled behaviour of UChat (spec #47). */
data class UChatSettings(
    val terminalFontSize: Int = 14,
    val restoreSessionsAfterReboot: Boolean = false,
    val persistentNotification: Boolean = true,
    val storageWarnThresholdGb: Int = 10,
    val showHiddenFiles: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_SIZE = intPreferencesKey("terminal_font_size")
        val RESTORE_REBOOT = booleanPreferencesKey("restore_after_reboot")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val STORAGE_THRESHOLD = intPreferencesKey("storage_warn_threshold_gb")
        val HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val FONT_SCALE = floatPreferencesKey("unused_placeholder")
    }

    val settings: Flow<UChatSettings> =
        context.dataStore.data.map { p ->
            UChatSettings(
                terminalFontSize = p[Keys.FONT_SIZE] ?: 14,
                restoreSessionsAfterReboot = p[Keys.RESTORE_REBOOT] ?: false,
                persistentNotification = p[Keys.PERSISTENT_NOTIFICATION] ?: true,
                storageWarnThresholdGb = p[Keys.STORAGE_THRESHOLD] ?: 10,
                showHiddenFiles = p[Keys.HIDDEN_FILES] ?: false,
            )
        }

    suspend fun setTerminalFontSize(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size.coerceIn(12, 24) }
    }

    suspend fun setRestoreAfterReboot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESTORE_REBOOT] = enabled }
    }

    suspend fun setPersistentNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERSISTENT_NOTIFICATION] = enabled }
    }

    suspend fun setStorageWarnThreshold(gb: Int) {
        context.dataStore.edit { it[Keys.STORAGE_THRESHOLD] = gb.coerceIn(1, 100) }
    }

    suspend fun setShowHiddenFiles(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIDDEN_FILES] = enabled }
    }
}
