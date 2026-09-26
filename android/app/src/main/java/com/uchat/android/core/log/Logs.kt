package com.uchat.android.core.log

import java.io.File
import java.time.Instant
import java.util.ArrayDeque

/**
 * Structured in-memory + file logging with rotation (spec #43).
 *
 * Separate buckets: app, installer, process, network. Every bucket is a bounded ring buffer (no
 * unbounded growth) plus a size-rotated file.
 */
object Logs {

    enum class Bucket(val fileName: String) {
        APP("uchat-app.log"),
        INSTALLER("uchat-installer.log"),
        PROCESS("uchat-process.log"),
        NETWORK("uchat-network.log"),
    }

    private const val MAX_MEMORY_LINES = 500
    private const val MAX_FILE_BYTES = 512L * 1024L // 512 KB per bucket

    private val buffers = Bucket.entries.associateWith { ArrayDeque<String>() }
    private var logsDir: File? = null

    fun init(dir: File) {
        logsDir = dir.apply { mkdirs() }
    }

    @Synchronized
    fun append(bucket: Bucket, line: String) {
        val stamped = "${Instant.now()} [$bucket] ${line.take(2000)}"
        val deque = buffers.getValue(bucket)
        synchronized(deque) {
            deque.addLast(stamped)
            while (deque.size > MAX_MEMORY_LINES) deque.removeFirst()
        }
        writeToDisk(bucket, stamped)
    }

    fun app(line: String) = append(Bucket.APP, line)

    fun installer(line: String) = append(Bucket.INSTALLER, line)

    fun process(line: String) = append(Bucket.PROCESS, line)

    fun network(line: String) = append(Bucket.NETWORK, line)

    fun recent(bucket: Bucket, maxLines: Int = 200): List<String> {
        val deque = buffers.getValue(bucket)
        synchronized(deque) {
            return deque.toList().takeLast(maxLines)
        }
    }

    private fun writeToDisk(bucket: Bucket, line: String) {
        val dir = logsDir ?: return
        try {
            val file = File(dir, bucket.fileName)
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val rotated = File(dir, bucket.fileName + ".1")
                rotated.delete()
                file.renameTo(rotated)
            }
            file.appendText(line + "\n")
        } catch (_: Exception) {
            // Logging must never crash the app.
        }
    }
}
