@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.uchat.android.ui.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uchat.android.R
import com.uchat.android.core.settings.SettingsRepository
import com.uchat.android.core.settings.ToolbarPosition
import com.uchat.android.core.settings.UChatSettings
import com.uchat.android.terminal.TerminalSizePresets
import com.uchat.android.terminal.keys.ModifierMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Layer 7 UI: terminal appearance & input behaviour — font size, key size, toolbar position and
 * visibility, haptics, key repeat, modifier behaviour, cursor blink, scroll-to-bottom button.
 */
@Composable
fun TerminalSettingsScreen(
    settings: UChatSettings,
    repository: SettingsRepository,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = TerminalPalette.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tset_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = TerminalPalette.Surface,
                        titleContentColor = TerminalPalette.Foreground,
                        navigationIconContentColor = TerminalPalette.Foreground,
                    ),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.tset_title))
            // Font size
            SliderSetting(
                label = stringResource(R.string.tset_font_size),
                value = settings.terminalFontSize.toFloat(),
                range = 8f..32f,
                steps = 11,
                display = { "${it.toInt()} sp" },
                onChange = { scope.launch { repository.setTerminalFontSize(it.toInt()) } },
            )
            // Key height
            SliderSetting(
                label = stringResource(R.string.tset_key_height),
                value = settings.terminalKeyHeightDp.toFloat(),
                range = 32f..72f,
                steps = 9,
                display = { "${it.toInt()} dp" },
                onChange = { scope.launch { repository.setTerminalKeyHeight(it.toInt()) } },
            )
            // Key min width
            SliderSetting(
                label = stringResource(R.string.tset_key_width),
                value = settings.terminalKeyMinWidthDp.toFloat(),
                range = 36f..140f,
                steps = 25,
                display = { "${it.toInt()} dp" },
                onChange = { scope.launch { repository.setTerminalKeyMinWidth(it.toInt()) } },
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.tset_toolbar_position))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                FilterChip(
                    selected = settings.terminalToolbarPosition == ToolbarPosition.TOP,
                    onClick = {
                        scope.launch { repository.setTerminalToolbarPosition(ToolbarPosition.TOP) }
                    },
                    label = { Text(stringResource(R.string.tset_position_top)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = settings.terminalToolbarPosition == ToolbarPosition.BOTTOM,
                    onClick = {
                        scope.launch {
                            repository.setTerminalToolbarPosition(ToolbarPosition.BOTTOM)
                        }
                    },
                    label = { Text(stringResource(R.string.tset_position_bottom)) },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.tset_modifier_mode))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                ModifierMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.terminalModifierMode == mode,
                        onClick = { scope.launch { repository.setTerminalModifierMode(mode) } },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ModifierMode.MOMENTARY -> R.string.tset_mode_momentary
                                        ModifierMode.LATCH -> R.string.tset_mode_latch
                                        ModifierMode.LOCK -> R.string.tset_mode_lock
                                    }
                                )
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.tset_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = TerminalPalette.ForegroundDim,
            )

            Spacer(Modifier.height(16.dp))
            SwitchSetting(
                label = stringResource(R.string.tset_toolbar_visible),
                checked = settings.terminalToolbarVisible,
                onChange = { scope.launch { repository.setTerminalToolbarVisible(it) } },
            )
            SwitchSetting(
                label = stringResource(R.string.tset_haptics),
                checked = settings.terminalHaptics,
                onChange = { scope.launch { repository.setTerminalHaptics(it) } },
            )
            SwitchSetting(
                label = stringResource(R.string.tset_repeat),
                checked = settings.terminalKeyRepeat,
                onChange = { scope.launch { repository.setTerminalKeyRepeat(it) } },
            )
            SwitchSetting(
                label = stringResource(R.string.tset_cursor_blink),
                checked = settings.terminalCursorBlink,
                onChange = { scope.launch { repository.setTerminalCursorBlink(it) } },
            )
            SwitchSetting(
                label = stringResource(R.string.tset_scroll_button),
                checked = settings.terminalScrollButton,
                onChange = { scope.launch { repository.setTerminalScrollButton(it) } },
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.tset_grid_title))
            Text(
                stringResource(R.string.tset_presets_hint),
                style = MaterialTheme.typography.bodySmall,
                color = TerminalPalette.ForegroundDim,
            )
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                FilterChip(
                    selected = settings.terminalFitScreen,
                    onClick = { scope.launch { repository.setTerminalFitScreen(true) } },
                    label = { Text(stringResource(R.string.tset_preset_auto)) },
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
                TerminalSizePresets.ALL.forEach { preset ->
                    FilterChip(
                        selected =
                            TerminalSizePresets.matching(
                                settings.terminalFitScreen,
                                settings.terminalFixedCols,
                                settings.terminalFixedRows,
                            ) == preset,
                        onClick = {
                            scope.launch {
                                repository.setTerminalFitScreen(false)
                                repository.setTerminalFixedCols(preset.cols)
                                repository.setTerminalFixedRows(preset.rows)
                            }
                        },
                        label = { Text(preset.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                    )
                }
            }
            SwitchSetting(
                label = stringResource(R.string.tset_fit_screen),
                checked = settings.terminalFitScreen,
                onChange = { scope.launch { repository.setTerminalFitScreen(it) } },
            )
            Text(
                stringResource(R.string.tset_fit_screen_desc),
                style = MaterialTheme.typography.bodySmall,
                color = TerminalPalette.ForegroundDim,
            )
            if (!settings.terminalFitScreen) {
                SliderSetting(
                    label = stringResource(R.string.tset_width_cols),
                    value = settings.terminalFixedCols.toFloat(),
                    range = 20f..300f,
                    steps = 55,
                    display = { "${it.toInt()} cols" },
                    onChange = { scope.launch { repository.setTerminalFixedCols(it.toInt()) } },
                )
                SliderSetting(
                    label = stringResource(R.string.tset_height_rows),
                    value = settings.terminalFixedRows.toFloat(),
                    range = 10f..200f,
                    steps = 94,
                    display = { "${it.toInt()} rows" },
                    onChange = { scope.launch { repository.setTerminalFixedRows(it.toInt()) } },
                )
                Text(
                    stringResource(R.string.tset_fixed_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TerminalPalette.ForegroundDim,
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.tset_scrollback))
            SliderSetting(
                label = stringResource(R.string.tset_scrollback),
                value = settings.terminalScrollbackLines.toFloat(),
                range = 200f..10000f,
                steps = 48,
                display = { "${(it.toInt() / 100) * 100} lines" },
                onChange = {
                    scope.launch { repository.setTerminalScrollbackLines((it.toInt() / 100) * 100) }
                },
            )
            SwitchSetting(
                label = stringResource(R.string.tset_keep_screen_on),
                checked = settings.terminalKeepScreenOn,
                onChange = { scope.launch { repository.setTerminalKeepScreenOn(it) } },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = TerminalPalette.Foreground,
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = TerminalPalette.Foreground)
            Text(display(value), color = TerminalPalette.ForegroundDim)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = TerminalPalette.Foreground)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
