package com.uchat.android

import com.uchat.android.linux.exec.ExecResult
import com.uchat.android.ui.tools.ToolUiState
import com.uchat.android.ui.tools.ToolsController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the AI-tools hub state machine.
 *
 * The original implementation swallowed every failure into logcat: the user tapped Install, nothing
 * visibly happened, and the tool stayed "Not installed" forever. These tests pin the
 * observable-state contract: busy → streamed log → version OR error, never silence.
 */
class ToolsControllerTest {

    @Test
    fun `begin marks busy and clears previous error`() {
        var states = ToolsController.fail(ToolsController.initial(), "node", "boom")
        assertTrue(ToolsController.stateOf(states, "node").hasError)

        states = ToolsController.begin(states, "node")
        val state = ToolsController.stateOf(states, "node")
        assertTrue(state.busy)
        assertFalse(state.hasError)
    }

    @Test
    fun `succeed resolves version and clears busy and error`() {
        var states = ToolsController.begin(ToolsController.initial(), "opencode")
        states = ToolsController.appendLog(states, "opencode", "[uchat] installing")
        states = ToolsController.succeed(states, "opencode", "v1.2.3")

        val state = ToolsController.stateOf(states, "opencode")
        assertFalse(state.busy)
        assertEquals("v1.2.3", state.version)
        assertNull(state.error)
        assertTrue("log cleared on success", state.logTail.isEmpty())
    }

    @Test
    fun `fail keeps context and appends error line to log`() {
        var states = ToolsController.begin(ToolsController.initial(), "node")
        states = ToolsController.appendLog(states, "node", "[uchat] downloading tarball")

        states = ToolsController.fail(states, "node", "curl: (6) Could not resolve host")

        val state = ToolsController.stateOf(states, "node")
        assertFalse(state.busy)
        assertEquals("curl: (6) Could not resolve host", state.error)
        assertTrue(
            "error must be echoed into the log tail",
            state.logTail.last().contains("Could not resolve host"),
        )
    }

    @Test
    fun `fail with blank message falls back to unknown error`() {
        val states =
            ToolsController.fail(ToolsController.begin(ToolsController.initial(), "x"), "x", "   ")
        assertEquals("unknown error", ToolsController.stateOf(states, "x").error)
    }

    @Test
    fun `appendLog keeps only the tail`() {
        var states = ToolsController.initial()
        repeat(20) { i -> states = ToolsController.appendLog(states, "node", "line-$i") }
        val log = ToolsController.stateOf(states, "node").logTail
        assertEquals(ToolUiState.MAX_LOG_LINES, log.size)
        assertEquals("line-19", log.last())
        assertTrue("oldest lines must be dropped", log.none { it == "line-0" })
    }

    @Test
    fun `appendLog ignores blank lines`() {
        val before = ToolsController.begin(ToolsController.initial(), "node")
        val after = ToolsController.appendLog(before, "node", "   ")
        assertEquals(before, after)
    }

    @Test
    fun `absent clears state without error for check-only misses`() {
        var states = ToolsController.begin(ToolsController.initial(), "bun")
        states = ToolsController.appendLog(states, "bun", "not found")
        states = ToolsController.absent(states, "bun")

        val state = ToolsController.stateOf(states, "bun")
        assertFalse(state.busy)
        assertNull(state.error)
        assertNull(state.version)
    }

    @Test
    fun `anyBusy detects running work`() {
        val busy = ToolsController.begin(ToolsController.initial(), "node")
        assertTrue(ToolsController.anyBusy(busy))
        assertFalse(ToolsController.anyBusy(ToolsController.succeed(busy, "node", "v1")))
    }

    @Test
    fun `summarizeFailure extracts last stderr lines`() {
        val result =
            ExecResult(
                exitCode = 1,
                stdout = "",
                stderr = "E: Unable to locate package nodejs\nmore context\nfinal line\n",
            )
        val summary = ToolsController.summarizeFailure(result)
        assertTrue(summary!!.contains("more context"))
        assertTrue(summary.contains("final line"))
        assertTrue(summary.length <= 400)
    }

    @Test
    fun `summarizeFailure falls back to stdout then exit code`() {
        val stdoutOnly = ExecResult(1, stdout = "some useful output", stderr = "")
        assertTrue(ToolsController.summarizeFailure(stdoutOnly)!!.contains("useful output"))

        val empty = ExecResult(127, "", "")
        assertEquals("exit code 127", ToolsController.summarizeFailure(empty))
    }

    @Test
    fun `summarizeFailure returns null for success`() {
        assertNull(ToolsController.summarizeFailure(ExecResult(0, "ok", "")))
        assertNull(ToolsController.summarizeFailure(null))
    }
}
