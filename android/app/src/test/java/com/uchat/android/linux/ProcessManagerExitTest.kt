package com.uchat.android.linux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the app-scoped exit journal: [ProcessManager] must observe EVERY exit — even
 * when no terminal screen is attached — so the "[session exited …]" banner can be appended to the
 * replay cache. Previously the single-slot exit listener was overwritten by whichever screen
 * attached last, so off-screen deaths vanished without a trace.
 */
class ProcessManagerExitTest {

    private class Recorder {
        val events = mutableListOf<Triple<Long, String, Int>>()

        fun onExit(sessionId: Long, label: String, code: Int) {
            events.add(Triple(sessionId, label, code))
        }
    }

    @Test
    fun exitTapFiresWhenSessionFailsToStart() {
        val recorder = Recorder()
        val manager =
            ProcessManager(
                CoroutineScope(Dispatchers.Unconfined),
                exitTap = recorder::onExit,
            )
        // handle = 0 → start() flips straight to FAILED and dispatches the exit.
        val session =
            PtySession(
                1L,
                "Terminal",
                listOf("/bin/bash"),
                "/root",
                0L,
                CoroutineScope(Dispatchers.Unconfined)
            )
        manager.register(session)
        session.start()
        assertEquals(listOf(Triple(1L, "Terminal", -1)), recorder.events)
    }

    @Test
    fun appScopedExitListenerSurvivesScreenAttach() {
        val recorder = Recorder()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val manager = ProcessManager(scope, exitTap = recorder::onExit)
        val session = PtySession(2L, "OpenCode", listOf("opencode"), "/root", 0L, scope)
        manager.register(session)
        // A terminal screen attaches afterwards: the single-slot listener is replaced...
        var screenNotified = false
        session.setExitListener { _ -> screenNotified = true }
        // ...but the app-scoped listener registered at register() time must still be in place.
        session.start()
        assertEquals(listOf(Triple(2L, "OpenCode", -1)), recorder.events)
        assertEquals(true, screenNotified)
    }

    @Test
    fun exitTapCanReceiveBothListenersIndependently() {
        val codes1 = mutableListOf<Int>()
        val codes2 = mutableListOf<Int>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val session = PtySession(3L, "Claude", listOf("claude"), "/root", 0L, scope)
        session.addExitListener { codes1.add(it) }
        session.addExitListener { codes2.add(it) }
        session.start()
        assertEquals(listOf(-1), codes1)
        assertEquals(listOf(-1), codes2)
    }
}
