package com.uchat.android

import androidx.compose.ui.test.assertCountIsAtLeast
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
        val welcome =
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.install_title),
            )
        val dashboard =
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.home_title),
            )
        val more =
            composeRule.onAllNodesWithText(
                composeRule.activity.getString(R.string.nav_more),
            )
        // At least one of the primary surfaces must exist.
        val found =
            try {
                welcome.assertCountIsAtLeast(1)
                true
            } catch (_: AssertionError) {
                false
            } ||
                try {
                    dashboard.assertCountIsAtLeast(1)
                    true
                } catch (_: AssertionError) {
                    false
                } ||
                try {
                    more.assertCountIsAtLeast(1)
                    true
                } catch (_: AssertionError) {
                    false
                }
        if (!found) {
            throw AssertionError("Neither welcome, dashboard nor navigation rendered")
        }
    }
}
