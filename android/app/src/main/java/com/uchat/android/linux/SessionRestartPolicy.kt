package com.uchat.android.linux

/**
 * Pure decision + bookkeeping for auto-restarting crashed sessions.
 *
 * WHY: opencode and Claude Code are Bun standalone binaries, and Bun 1.3.x on arm64 dies
 * spontaneously under proot with a segfault/SIGTRAP panic (exit code 255 / 133 — the field report
 * "kok sering session exited ... [session exited with code 255]"). The stack-limit and JIT fixes
 * make that rare; this policy makes the leftover crash harmless: the session is relaunched
 * automatically a limited number of times so the user keeps working.
 *
 * Deliberate exits are NEVER restarted:
 * - code 0 (user typed exit / quit the TUI)
 * - 137 (SIGKILL — Android low-memory kill; relaunching would fight the system's reclaim)
 * - 143 (SIGTERM — deliberate termination)
 * - negative (session failed to START — e.g. rootfs missing; relaunching cannot fix that)
 * - userInitiatedStop (tab closed, Processes screen stop, close-all)
 *
 * A session that ran longer than [STABLE_RUN_MILLIS] before dying counts as a fresh incident (its
 * attempt counter resets), so a tool that works for hours keeps its full retry budget.
 */
object SessionRestartPolicy {

    /** Maximum relaunches per incident window (a 4th crash shows the exit banner only). */
    const val MAX_ATTEMPTS = 3

    /** Delay before relaunch #1; doubles per attempt (2s, 4s, 8s). */
    const val BASE_DELAY_MILLIS = 2_000L

    const val MAX_DELAY_MILLIS = 30_000L

    /**
     * A run longer than this is "the tool worked, then died once" — reset the attempt counter
     * instead of treating it as yet another immediate crash-loop iteration.
     */
    const val STABLE_RUN_MILLIS = 60_000L

    fun shouldAutoRestart(exitCode: Int, userInitiated: Boolean, attemptsSoFar: Int): Boolean {
        if (userInitiated) return false
        if (attemptsSoFar >= MAX_ATTEMPTS) return false
        if (exitCode <= 0) return false // clean exit (0) or failed-to-start (negative)
        if (exitCode == 137 || exitCode == 143) return false // Android OOM kill / SIGTERM
        return true
    }

    /** Backoff for attempt number [attempt] (1-based): 2s, 4s, 8s, capped at [MAX_DELAY_MILLIS]. */
    fun delayForAttempt(attempt: Int): Long {
        val clamped = attempt.coerceIn(1, 5)
        return (BASE_DELAY_MILLIS shl (clamped - 1)).coerceAtMost(MAX_DELAY_MILLIS)
    }
}

/**
 * Per-session-key attempt counters backing [SessionRestartPolicy]. Keyed by `label|command` so a
 * relaunch (new session id) continues the same counter, while a clean exit forgets it. Not a
 * singleton: owner (AppContainer) controls the lifetime; tests get a fresh instance.
 */
class SessionRestartRegistry {

    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    fun attemptsFor(key: String): Int = attempts[key] ?: 0

    /**
     * Records one crash of [key] that ran for [ranForMillis] and returns the 1-based attempt number
     * to display. Runs longer than [SessionRestartPolicy.STABLE_RUN_MILLIS] reset first.
     */
    fun recordCrash(key: String, ranForMillis: Long): Int {
        val previous =
            if (ranForMillis >= SessionRestartPolicy.STABLE_RUN_MILLIS) 0 else attempts[key] ?: 0
        val next = previous + 1
        attempts[key] = next
        return next
    }

    /** Clears the counter (clean exit, user-initiated stop, or successful relaunch handover). */
    fun forget(key: String) {
        attempts.remove(key)
    }
}
