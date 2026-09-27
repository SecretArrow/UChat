package com.uchat.android

import com.uchat.android.core.settings.AppThemeMode
import com.uchat.android.ui.theme.resolveDarkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's SYSTEM/DARK/LIGHT pick resolves deterministically against the OS setting. */
class ThemeModeTest {

    @Test
    fun `system mode follows the os setting`() {
        assertFalse(resolveDarkTheme(AppThemeMode.SYSTEM, systemDark = false))
        assertTrue(resolveDarkTheme(AppThemeMode.SYSTEM, systemDark = true))
    }

    @Test
    fun `dark mode forces dark regardless of os`() {
        assertTrue(resolveDarkTheme(AppThemeMode.DARK, systemDark = false))
        assertTrue(resolveDarkTheme(AppThemeMode.DARK, systemDark = true))
    }

    @Test
    fun `light mode forces light regardless of os`() {
        assertFalse(resolveDarkTheme(AppThemeMode.LIGHT, systemDark = false))
        assertFalse(resolveDarkTheme(AppThemeMode.LIGHT, systemDark = true))
    }
}
