package com.uchat.android.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the auto-restart decision for crashed sessions (the "kok sering session exited" fix).
 *
 * Field report: `panic(main thread): Segmentation fault ... proot: vpid 1: terminated with signal 5
 * ... [session exited with code 255]` — a Bun panic inside opencode. The runtime fixes (stack
 * raise + JIT hardening) make this rare; this policy decides when the crash watchdog relaunches so
 * a single spontaneous death never strands the user.
 */
class SessionRestartPolicyTest {

    // ---- crash codes DO restart ----

    @Test
    fun `bun panic 255 restarts`() {
        assertTrue(
            SessionRestartPolicy.shouldAutoRestart(255, userInitiated = false, attemptsSoFar = 0)
        )
    }

    @Test
    fun `sigtrap 133 and segfault 139 restart`() {
        assertTrue(
            SessionRestartPolicy.shouldAutoRestart(133, userInitiated = false, attemptsSoFar = 0)
        )
        assertTrue(
            SessionRestartPolicy.shouldAutoRestart(139, userInitiated = false, attemptsSoFar = 0)
        )
    }

    @Test
    fun `generic non-zero exit restarts`() {
        assertTrue(
            SessionRestartPolicy.shouldAutoRestart(1, userInitiated = false, attemptsSoFar = 0)
        )
    }

    // ---- deliberate exits NEVER restart ----

    @Test
    fun `clean exit 0 never restarts`() {
        assertFalse(
            SessionRestartPolicy.shouldAutoRestart(0, userInitiated = false, attemptsSoFar = 0)
        )
    }

    @Test
    fun `android low-memory kill 137 never restarts`() {
        // Relaunching against the system's reclaim loop would burn battery and data.
        assertFalse(
            SessionRestartPolicy.shouldAutoRestart(137, userInitiated = false, attemptsSoFar = 0)
        )
    }

    @Test
    fun `sigterm 143 never restarts`() {
        assertFalse(
            SessionRestartPolicy.shouldAutoRestart(143, userInitiated = false, attemptsSoFar = 0)
        )
    }

    @Test
    fun `failed-to-start negative code never restarts`() {
        // Rootfs missing etc. — relaunching cannot fix the environment.
        assertFalse(
            SessionRestartPolicy.shouldAutoRestart(-1, userInitiated = false, attemptsSoFar = 0)
        )
    }

    @Test
    fun `user-initiated stop never restarts`() {
        // Tab closed, Processes screen stop, close-all.
        assertFalse(
            SessionRestartPolicy.shouldAutoRestart(255, userInitiated = true, attemptsSoFar = 0)
        )
    }

    // ---- attempt budget ----

    @Test
    fun `restarts stop after MAX_ATTEMPTS`() {
        for (attempts in 0 until SessionRestartPolicy.MAX_ATTEMPTS) {
            assertTrue(
                SessionRestartPolicy.shouldAutoRestart(
                    255,
                    userInitiated = false,
                    attemptsSoFar = attempts
                ),
            )
        }
        assertFalse(
            SessionRestartPolicy.shouldAutoRestart(
                255,
                userInitiated = false,
                attemptsSoFar = SessionRestartPolicy.MAX_ATTEMPTS,
            ),
        )
    }

    @Test
    fun `delay doubles per attempt and is capped`() {
        assertEquals(2_000L, SessionRestartPolicy.delayForAttempt(1))
        assertEquals(4_000L, SessionRestartPolicy.delayForAttempt(2))
        assertEquals(8_000L, SessionRestartPolicy.delayForAttempt(3))
        assertEquals(
            SessionRestartPolicy.MAX_DELAY_MILLIS,
            SessionRestartPolicy.delayForAttempt(50)
        )
        // Defensive: attempt numbers outside 1..5 must not produce negative/zero delays.
        assertTrue(SessionRestartPolicy.delayForAttempt(-3) > 0)
    }

    // ---- registry semantics ----

    @Test
    fun `registry counts consecutive crashes and resets after a stable run`() {
        val registry = SessionRestartRegistry()
        val key = "OpenCode|opencode"
        assertEquals(0, registry.attemptsFor(key))

        // Crash immediately 3 times in a row -> attempts 1,2,3.
        assertEquals(1, registry.recordCrash(key, ranForMillis = 1_000))
        assertEquals(2, registry.recordCrash(key, ranForMillis = 1_000))
        assertEquals(3, registry.recordCrash(key, ranForMillis = 1_000))
        assertEquals(3, registry.attemptsFor(key))

        // Worked for over a minute, then died once -> counter reset to a fresh incident.
        assertEquals(
            1,
            registry.recordCrash(key, ranForMillis = SessionRestartPolicy.STABLE_RUN_MILLIS + 1)
        )
    }

    @Test
    fun `registry forget clears the counter`() {
        val registry = SessionRestartRegistry()
        val key = "Claude|claude"
        registry.recordCrash(key, ranForMillis = 500)
        registry.forget(key)
        assertEquals(0, registry.attemptsFor(key))
    }

    @Test
    fun `registry keys are independent`() {
        val registry = SessionRestartRegistry()
        registry.recordCrash("OpenCode|opencode", ranForMillis = 0)
        assertEquals(0, registry.attemptsFor("Claude|claude"))
        assertEquals(1, registry.attemptsFor("OpenCode|opencode"))
    }
}
