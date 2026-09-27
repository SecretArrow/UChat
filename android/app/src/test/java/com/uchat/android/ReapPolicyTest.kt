package com.uchat.android

import com.uchat.android.linux.ReapPolicy
import com.uchat.android.linux.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the session reaper decision — the guard that auto-closes dead terminal tabs
 * (releasing their pty master FDs) while keeping a freshly-exited session visible long enough for
 * the user to read the exit banner.
 */
class ReapPolicyTest {

    @Test
    fun `running sessions are never reaped`() {
        assertFalse(
            ReapPolicy.shouldReap(
                state = SessionState.RUNNING,
                exitedAtMillis = 0L,
                nowMillis = 10_000L,
                graceMillis = 0L,
            )
        )
        assertFalse(
            ReapPolicy.shouldReap(
                state = SessionState.STARTING,
                exitedAtMillis = 0L,
                nowMillis = 10_000L,
                graceMillis = 0L,
            )
        )
    }

    @Test
    fun `exited session stays during grace window`() {
        val exitedAt = 1000L
        val grace = 60_000L
        // Just exited, halfway through the grace, one millisecond before the deadline.
        assertFalse(ReapPolicy.shouldReap(SessionState.EXITED, exitedAt, exitedAt, grace))
        assertFalse(
            ReapPolicy.shouldReap(SessionState.EXITED, exitedAt, exitedAt + grace - 1, grace)
        )
        // Exactly at the grace deadline the session is reaped.
        assertTrue(ReapPolicy.shouldReap(SessionState.EXITED, exitedAt, exitedAt + grace, grace))
        // Long after it, too.
        assertTrue(
            ReapPolicy.shouldReap(SessionState.EXITED, exitedAt, exitedAt + 10 * grace, grace)
        )
    }

    @Test
    fun `zero grace reaps immediately`() {
        assertTrue(ReapPolicy.shouldReap(SessionState.EXITED, 5_000L, 5_000L, 0L))
    }

    @Test
    fun `exited without a timestamp is kept (never reaped by accident)`() {
        assertFalse(ReapPolicy.shouldReap(SessionState.EXITED, 0L, 999_999L, 0L))
    }

    @Test
    fun `failed sessions are reaped immediately`() {
        assertTrue(
            ReapPolicy.shouldReap(
                state = SessionState.FAILED,
                exitedAtMillis = 0L,
                nowMillis = 1_000L,
                graceMillis = 60_000L,
            )
        )
    }
}
