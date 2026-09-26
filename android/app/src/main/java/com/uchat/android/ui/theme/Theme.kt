package com.uchat.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val UChatRed = Color(0xFFE5484D)
private val UChatRedDark = Color(0xFFB33B3F)
private val InkBlack = Color(0xFF16181F)
private val InkSurface = Color(0xFF1E2029)
private val InkSurfaceVariant = Color(0xFF2A2D38)

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
        secondary = Color(0xFF3D7FBF),
        tertiary = Color(0xFF2E9E63),
    )

@Composable
fun UChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
