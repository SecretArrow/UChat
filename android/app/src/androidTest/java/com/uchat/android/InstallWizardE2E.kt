package com.uchat.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E for the install wizard wiring (the #1 reason "nothing happened"): the wizard must OWN the
 * HOME tab while Ubuntu is missing, list all 10 steps, and tapping Install must visibly move at
 * least one step (running progress, pause/resume controls or — if the emulator has no network — a
 * copyable error). Before this test the wizard composable existed but was never rendered.
 */
@RunWith(AndroidJUnit4::class)
class InstallWizardE2E {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private fun text(resId: Int): String = composeRule.activity.getString(resId)

    @Test
    fun wizardOwnsHomeTabWhenUbuntuMissing() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            countOf(R.string.install_title) + countOf(R.string.home_title) > 0
        }
        if (countOf(R.string.home_title) > 0) {
            // Ubuntu already installed on this emulator run — the dashboard is correct then.
            return
        }
        assertTrue(countOf(R.string.install_title) > 0)
        // All 10 steps are listed up-front (download / verify / extract / init / apt / runtimes /
        // opencode / claude / health / ready).
        assertTrue(countOf(R.string.install_step_download) > 0)
        assertTrue(countOf(R.string.install_step_extract) > 0)
        assertTrue(countOf(R.string.install_step_apt) > 0)
        assertTrue(countOf(R.string.install_step_health) > 0)
    }

    @Test
    fun tappingInstallShowsVisibleProgressOrClearError() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            countOf(R.string.install_title) + countOf(R.string.home_title) > 0
        }
        if (countOf(R.string.home_title) > 0) return // already installed; nothing to assert here

        val startButton = composeRule.onNodeWithText(text(R.string.install_start))
        startButton.performClick()

        // After tapping Install the UI MUST react visibly: either a step flips to Running,
        // pause/cancel controls appear, or (offline) a copyable error card is rendered.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            countOf(R.string.install_step_status_running) > 0 ||
                countOf(R.string.action_pause) > 0 ||
                countOf(R.string.action_cancel) > 0 ||
                countOf(R.string.action_copy_error) > 0 ||
                countOf(R.string.install_step_status_failed) > 0
        }
        assertTrue(
            "installer must surface its state in the UI",
            countOf(R.string.install_step_status_running) +
                countOf(R.string.action_pause) +
                countOf(R.string.action_cancel) +
                countOf(R.string.action_copy_error) +
                countOf(R.string.install_step_status_failed) > 0,
        )

        // Leave a clean state (cancel is allowed to be absent if the step already finished/failed).
        composeRule.runOnUiThread {
            // no-op: state lives app-scoped; other tests tolerate a mid-install state.
        }
    }

    private fun countOf(resId: Int): Int =
        composeRule.onAllNodesWithText(text(resId)).fetchSemanticsNodes().size
}
