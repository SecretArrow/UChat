package com.uchat.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.uchat.android.core.settings.AppThemeMode
import com.uchat.android.terminal.emulator.TerminalColors
import com.uchat.android.ui.terminal.DarkTerminalScheme
import com.uchat.android.ui.terminal.LightTerminalScheme
import com.uchat.android.ui.terminal.LocalTerminalPalette
import com.uchat.android.ui.terminal.TerminalScheme

private val UChatRed = Color(0xFFE5484D)
private val UChatRedDark = Color(0xFFB33B3F)
private val InkBlack = Color(0xFF16181F)
private val InkSurface = Color(0xFF1E2029)
private val InkSurfaceVariant = Color(0xFF2A2D38)
private val PaperWhite = Color(0xFFF6F7FA)
private val PaperSurface = Color(0xFFEDEFF4)
private val PaperSurfaceVariant = Color(0xFFE2E5EC)

private val DarkColors =
    darkColorScheme(
        primary = UChatRed,
        onPrimary = Color.White,
        primaryContainer = UChatRedDark,
        secondary = Color(0xFF5CA7E4),
        tertiary = Color(0xFF4CC38A),
        background = InkBlack,
        onBackground = Color(0xFFE6E6E6),
        surface = InkSurface,
        onSurface = Color(0xFFE6E6E6),
        surfaceVariant = InkSurfaceVariant,
        onSurfaceVariant = Color(0xFFB4B4C0),
    )

private val LightColors =
    lightColorScheme(
        primary = UChatRedDark,
        onPrimary = Color.White,
        primaryContainer = UChatRed,
        secondary = Color(0xFF3D7FBF),
        tertiary = Color(0xFF2E9E63),
        background = PaperWhite,
        onBackground = Color(0xFF1B1F28),
        surface = PaperSurface,
        onSurface = Color(0xFF1B1F28),
        surfaceVariant = PaperSurfaceVariant,
        onSurfaceVariant = Color(0xFF5B6270),
    )

/**
 * Resolve the effective dark flag from the user's theme mode. Pure so it is unit-testable:
 * SYSTEM follows the OS, DARK/LIGHT force their mode regardless of the OS setting.
 */
fun resolveDarkTheme(mode: AppThemeMode, systemDark: Boolean): Boolean =
    when (mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

/**
 * The terminal chrome scheme that matches the resolved theme mode. The scheme instances live
 * next to the terminal UI (single source of truth for the role colors).
 */
private fun terminalScheme(dark: Boolean): TerminalScheme =
    if (dark) DarkTerminalScheme else LightTerminalScheme

/**
 * UChat theme: the user picks SYSTEM/DARK/LIGHT in Settings and the whole app follows —
 * Material colors, terminal chrome ([LocalTerminalPalette]) and the terminal canvas itself
 * ([TerminalColors.applyLightScheme]).
 *
 * Brand palettes are used in both modes (no Material You dynamic color): the theme switch
 * must look identical on every device, and the terminal identity is JuiceSSH-inspired.
 */
@Composable
fun UChatTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // Keep the terminal renderer's default colors in sync with the active mode. SideEffect
    // runs after every successful composition, so a theme switch lands before the next frame.
    SideEffect { TerminalColors.applyLightScheme(!darkTheme) }

    CompositionLocalProvider(LocalTerminalPalette provides terminalScheme(darkTheme)) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
