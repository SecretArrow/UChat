package com.uchat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.uchat.android.core.settings.UChatSettings
import com.uchat.android.di.AppContainer
import com.uchat.android.ui.AppRoot
import com.uchat.android.ui.theme.UChatTheme
import com.uchat.android.ui.theme.resolveDarkTheme

/**
 * Single-activity Compose host (spec #24): Linux processes are never tied to this Activity — they
 * live in [AppContainer] and the foreground service, so rotation, backgrounding and recreation
 * never kill OpenCode or Claude.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as UChatApp).container
        setContent {
            // The user's SYSTEM/DARK/LIGHT pick drives the whole app, terminal canvas included.
            val settings by
                container.settingsRepository.settings.collectAsState(initial = UChatSettings())
            val darkTheme = resolveDarkTheme(settings.appThemeMode, isSystemInDarkTheme())
            // Status/navigation bar icons must contrast with the *chosen* theme, not the OS
            // one — otherwise forcing Light while the system is dark leaves white icons on a
            // white screen.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle =
                        if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb())
                        else
                            SystemBarStyle.light(
                                Color.Transparent.toArgb(),
                                Color.Transparent.toArgb(),
                            ),
                    navigationBarStyle =
                        if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb())
                        else
                            SystemBarStyle.light(
                                Color.Transparent.toArgb(),
                                Color.Transparent.toArgb(),
                            ),
                )
            }
            UChatTheme(themeMode = settings.appThemeMode) { AppRoot(container) }
        }
    }
}
