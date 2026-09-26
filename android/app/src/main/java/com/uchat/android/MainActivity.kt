package com.uchat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uchat.android.di.AppContainer
import com.uchat.android.ui.AppRoot
import com.uchat.android.ui.theme.UChatTheme

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
        setContent { UChatTheme { AppRoot(container) } }
    }
}
