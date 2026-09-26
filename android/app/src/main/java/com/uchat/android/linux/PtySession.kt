package com.uchat.android.linux

import com.uchat.android.core.log.Logs
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Lifecycle state of a [PtySession]. */
enum class SessionState {
    STARTING,
    RUNNING,
    EXITED,
    FAILED
}

/**
 * A real, independent terminal session: one child process inside one pty.
 *
 * Output arrives on a dedicated reader thread through [onOutput] (raw UTF-8 bytes). [onExit] fires
 * exactly once. Sessions survive navigation and Activity recreation because they are owned by the
 * app-scoped [ProcessManager], not by the UI.
 */
class PtySession(
    val id: Long,
    val label: String,
    val command: List<String>,
    val workingDirectory: String,
    handle: Long,
    private val scope: CoroutineScope,
) : Closeable {

    @Volatile
    var state: SessionState = SessionState.STARTING
        private set

    @Volatile
    var exitCode: Int? = null
        private set

    val pid: Long
        get() = nativePid

    val startedAtMillis: Long = System.currentTimeMillis()

    private val outputListeners =
        java.util.concurrent.CopyOnWriteArrayList<(ByteArray, Int) -> Unit>()

    @Volatile private var onExit: ((Int) -> Unit)? = null

    private var nativePid: Long = 0
    private var handle: Long = handle
    private var readerJob: Job? = null
    private val readBuffer = ByteArray(32 * 1024)

    /** Many listeners: ProcessManager (replay cache) + the attached terminal screen. */
    fun addOutputListener(listener: (ByteArray, Int) -> Unit) {
        outputListeners.add(listener)
    }

    fun removeOutputListener(listener: (ByteArray, Int) -> Unit) {
        outputListeners.remove(listener)
    }

    /** The exit listener is single-slot: the attached terminal screen owns it. */
    fun setExitListener(onExit: (Int) -> Unit) {
        this.onExit = onExit
    }

    /** Compatibility helper: registers one output listener plus the exit listener. */
    fun setCallbacks(onOutput: (ByteArray, Int) -> Unit, onExit: (Int) -> Unit) {
        addOutputListener(onOutput)
        setExitListener(onExit)
    }

    fun start() {
        if (handle == 0L) {
            state = SessionState.FAILED
            onExit?.invoke(-1)
            return
        }
        state = SessionState.RUNNING
        nativePid = Pty.nativeGetPid(handle)
        Logs.process("session #$id '$label' started (pid $nativePid): ${command.joinToString(" ")}")
        readerJob =
            scope.launch(Dispatchers.IO) {
                while (state == SessionState.RUNNING) {
                    val n = Pty.nativeRead(handle, readBuffer)
                    if (n <= 0) break
                    for (listener in outputListeners) listener(readBuffer, n)
                }
                val code = Pty.nativeWaitFor(handle)
                exitCode = code
                state = SessionState.EXITED
                Logs.process("session #$id '$label' exited with $code")
                onExit?.invoke(code)
            }
    }

    fun write(bytes: ByteArray) {
        if (handle != 0L && state == SessionState.RUNNING) {
            Pty.nativeWrite(handle, bytes, bytes.size)
        }
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun resize(rows: Int, cols: Int) {
        if (handle != 0L) Pty.nativeResize(handle, rows, cols)
    }

    fun signal(sig: Int) {
        if (handle != 0L) Pty.nativeSignal(handle, sig)
    }

    /** Graceful stop: SIGHUP → SIGTERM → SIGKILL escalation. */
    fun stop() {
        if (state == SessionState.RUNNING) {
            signal(Pty.SIG_HUP)
            signal(Pty.SIG_TERM)
            scope.launch(Dispatchers.IO) {
                Thread.sleep(1500)
                if (state == SessionState.RUNNING) signal(Pty.SIG_KILL)
            }
        }
    }

    fun kill() {
        if (state == SessionState.RUNNING) signal(Pty.SIG_KILL)
    }

    override fun close() {
        stop()
        scope.launch(Dispatchers.IO) {
            Thread.sleep(300)
            if (handle != 0L) {
                Pty.nativeClose(handle)
                handle = 0L
            }
        }
    }

    companion object {
        private val counter = AtomicLong(0)

        fun nextId(): Long = counter.incrementAndGet()
    }
}

/**
 * App-scoped registry of every live session. This is what makes processes survive UI navigation,
 * Activity recreation and app backgrounding: sessions are attached to the application + foreground
 * service, never to the Activity.
 *
 * [outputTap] (optional) receives every output chunk of every session — wired to the terminal
 * replay cache so detached sessions keep a bounded scrollback (layer 2 buffer).
 */
class ProcessManager(
    private val scope: CoroutineScope,
    private val outputTap: ((sessionId: Long, bytes: ByteArray, length: Int) -> Unit)? = null,
) {

    private val sessions = ConcurrentHashMap<Long, PtySession>()

    val all: List<PtySession>
        get() = sessions.values.sortedBy { it.id }

    fun runningCount(): Int = sessions.values.count { it.state == SessionState.RUNNING }

    fun get(id: Long): PtySession? = sessions[id]

    fun register(session: PtySession) {
        sessions[session.id] = session
        val tap = outputTap
        if (tap != null) {
            session.addOutputListener { bytes, len -> tap(session.id, bytes, len) }
        }
        session.setExitListener { /* UI attaches its own exit listener when visible */}
    }

    fun remove(id: Long) {
        sessions.remove(id)?.close()
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        Logs.process("stopAll requested (${sessions.size} sessions)")
    }

    /** Removes exited sessions from the registry, keeping their metadata in DB. */
    fun reapExited() {
        sessions.values.filter { it.state == SessionState.EXITED }.forEach { remove(it.id) }
    }
}
