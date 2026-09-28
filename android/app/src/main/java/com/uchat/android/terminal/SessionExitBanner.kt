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

    /**
     * Yellow ANSI note written AFTER [format] when the crash watchdog has decided to relaunch the
     * session (see [com.uchat.android.linux.SessionRestartPolicy]). Yellow — not the dim grey of
     * the exit banner — because this one is actionable news, not an obituary.
     */
    fun formatRestartNote(label: String, attempt: Int, delayMillis: Long): ByteArray {
        val text =
            "\r\n\u001b[33m[↻ $label crashed — auto-restart $attempt in ${delayMillis / 1000}s / " +
                "restart otomatis ${delayMillis / 1000}s]\u001b[0m\r\n"
        return text.toByteArray(Charsets.UTF_8)
    }

    /**
     * Cyan ANSI note written AFTER [format] when the restart budget is exhausted (all
     * [com.uchat.android.linux.SessionRestartPolicy.MAX_ATTEMPTS] relaunches crashed again). Tells
     * the user what actually helps instead of leaving a dead tab and a mystery — the field report
     * was "[session exited with code 255]" with zero guidance.
     */
    fun formatGaveUpNote(label: String): ByteArray {
        val text =
            "\r\n\u001b[36m[?] $label keeps crashing / terus crash. Coba: tutup tab ini lalu " +
                "buka lagi · restart app · restart ponsel. Tips: close this tab and reopen · " +
                "restart the app · reboot the phone]\u001b[0m\r\n"
        return text.toByteArray(Charsets.UTF_8)
    }
}
