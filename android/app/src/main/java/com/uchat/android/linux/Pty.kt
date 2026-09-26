package com.uchat.android.linux

/**
 * JNI bridge to the native PTY implementation (src/main/cpp/pty.c).
 *
 * One [PtySession] owns exactly one handle. All handles are opaque jlong values produced by
 * [nativeOpenTerminal] and must be released with [nativeClose] to avoid leaking the master fd.
 */
object Pty {

    init {
        System.loadLibrary("uchat_pty")
    }

    /**
     * Forks a child attached to a fresh pty running [cmd] (execve argv, no shell), in [cwd], with
     * [env] entries of the form "KEY=VALUE". Returns the session handle, or 0 on failure (see
     * [nativeLastError]).
     */
    external fun nativeOpenTerminal(
        cmd: Array<String>,
        cwd: String,
        env: Array<String>,
        rows: Int,
        cols: Int,
    ): Long

    /** Blocking read into [buffer]; returns bytes read or -1 on EOF/child exit. */
    external fun nativeRead(handle: Long, buffer: ByteArray): Int

    external fun nativeWrite(handle: Long, buffer: ByteArray, len: Int)

    external fun nativeResize(handle: Long, rows: Int, cols: Int)

    /** Sends [signal] (POSIX number) to the child process group. */
    external fun nativeSignal(handle: Long, signal: Int)

    /** Blocking waitpid; returns exit code (or 128+signal). */
    external fun nativeWaitFor(handle: Long): Int

    /** Child PID of the session (0 when the handle is dead). */
    external fun nativeGetPid(handle: Long): Long

    /** Closes the master fd and frees the native handle. */
    external fun nativeClose(handle: Long)

    external fun nativeLastError(): String

    const val SIG_TERM: Int = 15
    const val SIG_KILL: Int = 9
    const val SIG_INT: Int = 2
    const val SIG_HUP: Int = 1
}
