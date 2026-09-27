package com.uchat.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the back-navigation policy (user requirement: "tombol back tidak boleh keluar app
 * kecuali di main activity"). Overlays and tabs are popped first; the HOME root asks for a
 * double-press before the app actually exits.
 */
class BackNavTest {

    @Test
    fun `overlay open - back closes the overlay`() {
        assertEquals(
            com.uchat.android.ui.BackAction.CLOSE_OVERLAY,
            com.uchat.android.ui.resolveBackAction(hasOverlay = true, notAtHome = false),
        )
        assertEquals(
            com.uchat.android.ui.BackAction.CLOSE_OVERLAY,
            com.uchat.android.ui.resolveBackAction(hasOverlay = true, notAtHome = true),
        )
    }

    @Test
    fun `no overlay off-home - back goes to HOME`() {
        assertEquals(
            com.uchat.android.ui.BackAction.GO_HOME,
            com.uchat.android.ui.resolveBackAction(hasOverlay = false, notAtHome = true),
        )
    }

    @Test
    fun `root (HOME no overlay) - back confirms exit`() {
        assertEquals(
            com.uchat.android.ui.BackAction.CONFIRM_EXIT,
            com.uchat.android.ui.resolveBackAction(hasOverlay = false, notAtHome = false),
        )
    }
}
