package com.uchat.android.terminal

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Layer 2 (terminal buffer, native side): bounded per-session replay of recent output.
 *
 * While a terminal screen is not attached the backend keeps producing output; the replay cache is
 * the component that makes sure the user never loses context: switching back to a session replays
 * the last [maxBytesPerSession] bytes instead of showing a blank screen.
 *
 * Memory stays bounded: 256 KiB per session, oldest chunks evicted first (spec #33).
 */
class TerminalReplayCache(private val maxBytesPerSession: Int = DEFAULT_MAX_BYTES) {

    private val buffers = ConcurrentHashMap<Long, ReplayBuffer>()

    fun offer(sessionId: Long, bytes: ByteArray, length: Int) {
        if (length <= 0) return
        buffers.getOrPut(sessionId) { ReplayBuffer(maxBytesPerSession) }.offer(bytes, length)
    }

    fun snapshot(sessionId: Long): ByteArray = buffers[sessionId]?.snapshot() ?: ByteArray(0)

    fun clear(sessionId: Long) {
        buffers[sessionId]?.clear()
    }

    fun remove(sessionId: Long) {
        buffers.remove(sessionId)
    }

    /** Fixed-capacity byte ring built from chunks. */
    class ReplayBuffer(private val maxBytes: Int) {
        private val chunks = ArrayDeque<ByteArray>()
        private var total = 0

        @Synchronized
        fun offer(bytes: ByteArray, length: Int) {
            val copy = bytes.copyOf(length)
            chunks.addLast(copy)
            total += length
            while (total > maxBytes && chunks.size > 1) {
                total -= chunks.removeFirst().size
            }
        }

        @Synchronized
        fun snapshot(): ByteArray {
            val out = ByteArray(total)
            var pos = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, out, pos, chunk.size)
                pos += chunk.size
            }
            return out
        }

        @Synchronized
        fun clear() {
            chunks.clear()
            total = 0
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 256 * 1024
    }
}
