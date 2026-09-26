package com.uchat.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator e2e smoke test (spec #77): the app must launch and render the onboarding welcome text or
 * the dashboard — and never crash.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchE2E {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsWelcomeOrDashboard() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            countOf(R.string.install_title) +
                countOf(R.string.home_title) +
                countOf(R.string.nav_more) > 0
        }
    }

    private fun countOf(resId: Int): Int =
        composeRule
            .onAllNodesWithText(composeRule.activity.getString(resId))
            .fetchSemanticsNodes()
            .size
}
