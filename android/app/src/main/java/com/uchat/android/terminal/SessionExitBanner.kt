package com.uchat.android.terminal

/**
 * Pure formatter for the "[session exited …]" banner that is appended to a session's replay cache
 * when it dies while no terminal screen is attached.
 *
 * Why this exists: previously the exit banner was only written by the ATTACHED terminal screen, so
 * a session that died while the user was on Home/Files left zero explanation — the user came back
 * to a grey dot (and after the 60s reaper, to nothing at all) and perceived it as "the app exits
 * sessions randomly". The banner now always lands in the replay cache, so the next attach shows
 * what happened.
 */
object SessionExitBanner {

    /** Dim grey ANSI banner appended after the last real output of the session. */
    fun format(label: String, exitCode: Int): ByteArray {
        val reason =
            when {
                exitCode < 0 -> "failed to start / gagal dimulai"
                exitCode == 137 ->
                    "killed by Android (low memory) / dihentikan Android (memori penuh)"
                exitCode == 143 -> "terminated / dihentikan"
                else -> "exited / berakhir"
            }
        val text =
            "\r\n\u001b[90m[$label $reason — code $exitCode · tap + for a new session / ketuk + " +
                "untuk sesi baru]\u001b[0m\r\n"
        return text.toByteArray(Charsets.UTF_8)
    }
}
