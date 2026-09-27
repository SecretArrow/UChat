package com.uchat.android.ui.tools

import com.uchat.android.data.registry.ToolEntry

/**
 * Per-tool UI state for the AI Tools hub.
 *
 * The previous implementation swallowed every install/check failure into logcat, so the user tapped
 * "Install" and nothing appeared to happen. This state machine makes every transition observable:
 * busy flag, streamed log tail, structured error and the resolved version.
 */
data class ToolUiState(
    val busy: Boolean = false,
    val version: String? = null,
    val logTail: List<String> = emptyList(),
    val error: String? = null,
) {
    val hasError: Boolean
        get() = error != null

    companion object {
        /** Keeps the log pane small and cheap to recompose. */
        const val MAX_LOG_LINES = 8
    }
}

/** Immutable reducer over the tools state map — pure, unit-testable, no Android imports. */
object ToolsController {

    const val MAX_LOG_LINES = ToolUiState.MAX_LOG_LINES

    fun initial(): Map<String, ToolUiState> = emptyMap()

    fun stateOf(states: Map<String, ToolUiState>, id: String): ToolUiState =
        states[id] ?: ToolUiState()

    /** Marks a tool busy and clears any previous error/log (start of check or install). */
    fun begin(states: Map<String, ToolUiState>, id: String): Map<String, ToolUiState> =
        states + (id to ToolUiState(busy = true))

    /** Marks the tool idle-with-error, preserving the log tail for context. */
    fun fail(
        states: Map<String, ToolUiState>,
        id: String,
        message: String
    ): Map<String, ToolUiState> {
        val current = stateOf(states, id)
        val reason = message.trim().ifEmpty { "unknown error" }
        return states +
            (id to
                current.copy(
                    busy = false,
                    error = reason,
                    logTail = (current.logTail + "[error] $reason").takeLast(MAX_LOG_LINES),
                ))
    }

    /** Appends one streamed output line (installer progress) to the tool's log tail. */
    fun appendLog(
        states: Map<String, ToolUiState>,
        id: String,
        line: String,
    ): Map<String, ToolUiState> {
        if (line.isBlank()) return states
        val current = stateOf(states, id)
        return states +
            (id to current.copy(logTail = (current.logTail + line).takeLast(MAX_LOG_LINES)))
    }

    /** Marks the tool installed (version resolved) and clears busy/error. */
    fun succeed(
        states: Map<String, ToolUiState>,
        id: String,
        version: String,
    ): Map<String, ToolUiState> {
        val current = stateOf(states, id)
        return states +
            (id to
                current.copy(busy = false, error = null, version = version, logTail = emptyList()))
    }

    /** Marks the check finished without a version (tool absent) — idle, no error. */
    fun absent(states: Map<String, ToolUiState>, id: String): Map<String, ToolUiState> {
        val current = stateOf(states, id)
        return states +
            (id to current.copy(busy = false, error = null, version = null, logTail = emptyList()))
    }

    /** Returns true when any of the given tools is currently busy. */
    fun anyBusy(states: Map<String, ToolUiState>): Boolean = states.values.any { it.busy }

    /** Extracts the most user-relevant lines from an install/check failure. */
    fun summarizeFailure(result: com.uchat.android.linux.exec.ExecResult?): String? {
        if (result == null) return null
        if (result.exitCode == 0) return null
        val source = result.stderr.lineSequence().filter { it.isNotBlank() }.toList()
        val picked =
            when {
                source.isNotEmpty() -> source.takeLast(3)
                else -> result.stdout.lineSequence().filter { it.isNotBlank() }.toList().takeLast(3)
            }
        val text = picked.joinToString(" | ").take(400)
        return if (text.isEmpty()) "exit code ${result.exitCode}" else text
    }

    /** Tools the hub should probe when it becomes visible (all registered entries). */
    fun autoCheckList(tools: List<ToolEntry>): List<ToolEntry> = tools
}
