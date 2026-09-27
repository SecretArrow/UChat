package com.uchat.android.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uchat.android.terminal.keys.ModifierMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "uchat_settings")

/** Toolbar placement inside the terminal screen. */
enum class ToolbarPosition {
    TOP,
    BOTTOM
}

/** User-controlled behaviour of UChat (spec #47) + terminal UI customization. */
data class UChatSettings(
    val terminalFontSize: Int = 14,
    val restoreSessionsAfterReboot: Boolean = false,
    val persistentNotification: Boolean = true,
    val storageWarnThresholdGb: Int = 10,
    val showHiddenFiles: Boolean = false,
    // Terminal experience (extra keys + renderer)
    val terminalKeyHeightDp: Int = 46,
    val terminalKeyMinWidthDp: Int = 56,
    val terminalToolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val terminalToolbarVisible: Boolean = true,
    val terminalHaptics: Boolean = true,
    val terminalKeyRepeat: Boolean = true,
    val terminalModifierMode: ModifierMode = ModifierMode.MOMENTARY,
    val terminalCursorBlink: Boolean = true,
    val terminalScrollButton: Boolean = true,
    val terminalScrollbackLines: Int = 2000,
    val terminalKeepScreenOn: Boolean = false,
    // Grid geometry: fit-to-screen vs a fixed columns×rows grid (user requirement).
    val terminalFitScreen: Boolean = true,
    val terminalFixedCols: Int = 80,
    val terminalFixedRows: Int = 24,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_SIZE = intPreferencesKey("terminal_font_size")
        val RESTORE_REBOOT = booleanPreferencesKey("restore_after_reboot")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val STORAGE_THRESHOLD = intPreferencesKey("storage_warn_threshold_gb")
        val HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val KEY_HEIGHT = intPreferencesKey("terminal_key_height")
        val KEY_MIN_WIDTH = intPreferencesKey("terminal_key_min_width")
        val TOOLBAR_POSITION = stringPreferencesKey("terminal_toolbar_position")
        val TOOLBAR_VISIBLE = booleanPreferencesKey("terminal_toolbar_visible")
        val HAPTICS = booleanPreferencesKey("terminal_haptics")
        val KEY_REPEAT = booleanPreferencesKey("terminal_key_repeat")
        val MODIFIER_MODE = stringPreferencesKey("terminal_modifier_mode")
        val CURSOR_BLINK = booleanPreferencesKey("terminal_cursor_blink")
        val SCROLL_BUTTON = booleanPreferencesKey("terminal_scroll_button")
        val SCROLLBACK_LINES = intPreferencesKey("terminal_scrollback_lines")
        val KEEP_SCREEN_ON = booleanPreferencesKey("terminal_keep_screen_on")
        val FIT_SCREEN = booleanPreferencesKey("terminal_fit_screen")
        val FIXED_COLS = intPreferencesKey("terminal_fixed_cols")
        val FIXED_ROWS = intPreferencesKey("terminal_fixed_rows")
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
                terminalKeyHeightDp = p[Keys.KEY_HEIGHT] ?: 46,
                terminalKeyMinWidthDp = p[Keys.KEY_MIN_WIDTH] ?: 56,
                terminalToolbarPosition =
                    p[Keys.TOOLBAR_POSITION]?.let { saved ->
                        runCatching { ToolbarPosition.valueOf(saved) }.getOrNull()
                    } ?: ToolbarPosition.BOTTOM,
                terminalToolbarVisible = p[Keys.TOOLBAR_VISIBLE] ?: true,
                terminalHaptics = p[Keys.HAPTICS] ?: true,
                terminalKeyRepeat = p[Keys.KEY_REPEAT] ?: true,
                terminalModifierMode =
                    p[Keys.MODIFIER_MODE]?.let { saved ->
                        runCatching { ModifierMode.valueOf(saved) }.getOrNull()
                    } ?: ModifierMode.MOMENTARY,
                terminalCursorBlink = p[Keys.CURSOR_BLINK] ?: true,
                terminalScrollButton = p[Keys.SCROLL_BUTTON] ?: true,
                terminalScrollbackLines = p[Keys.SCROLLBACK_LINES] ?: 2000,
                terminalKeepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
                terminalFitScreen = p[Keys.FIT_SCREEN] ?: true,
                terminalFixedCols = p[Keys.FIXED_COLS] ?: 80,
                terminalFixedRows = p[Keys.FIXED_ROWS] ?: 24,
            )
        }

    suspend fun setTerminalFontSize(size: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size.coerceIn(8, 32) }
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

    suspend fun setTerminalKeyHeight(dp: Int) {
        context.dataStore.edit { it[Keys.KEY_HEIGHT] = dp.coerceIn(32, 72) }
    }

    suspend fun setTerminalKeyMinWidth(dp: Int) {
        context.dataStore.edit { it[Keys.KEY_MIN_WIDTH] = dp.coerceIn(36, 140) }
    }

    suspend fun setTerminalToolbarPosition(position: ToolbarPosition) {
        context.dataStore.edit { it[Keys.TOOLBAR_POSITION] = position.name }
    }

    suspend fun setTerminalToolbarVisible(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TOOLBAR_VISIBLE] = enabled }
    }

    suspend fun setTerminalHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setTerminalKeyRepeat(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEY_REPEAT] = enabled }
    }

    suspend fun setTerminalModifierMode(mode: ModifierMode) {
        context.dataStore.edit { it[Keys.MODIFIER_MODE] = mode.name }
    }

    suspend fun setTerminalCursorBlink(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CURSOR_BLINK] = enabled }
    }

    suspend fun setTerminalScrollButton(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCROLL_BUTTON] = enabled }
    }

    suspend fun setTerminalScrollbackLines(lines: Int) {
        context.dataStore.edit { it[Keys.SCROLLBACK_LINES] = lines.coerceIn(200, 10000) }
    }

    suspend fun setTerminalKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setTerminalFitScreen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FIT_SCREEN] = enabled }
    }

    suspend fun setTerminalFixedCols(cols: Int) {
        context.dataStore.edit { it[Keys.FIXED_COLS] = cols.coerceIn(20, 200) }
    }

    suspend fun setTerminalFixedRows(rows: Int) {
        context.dataStore.edit { it[Keys.FIXED_ROWS] = rows.coerceIn(10, 100) }
    }
}
