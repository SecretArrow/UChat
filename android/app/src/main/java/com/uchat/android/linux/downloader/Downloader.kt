package com.uchat.android.linux.downloader

import com.uchat.android.core.log.Logs
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Snapshot of a running download, rendered by the installer UI. */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toDouble() / totalBytes).toFloat() else 0f
}

enum class DownloadControl {
    NONE,
    PAUSE,
    CANCEL
}

/**
 * Resumable HTTP downloader (spec #27, #67).
 * - Appends Range headers so a partially downloaded `.part` file continues where it left off
 *   (pause/resume/retry all reuse the same file).
 * - Verifies nothing by itself; checksum validation is a separate installer step so a corrupt
 *   download never reaches extraction.
 */
class Downloader(private val client: OkHttpClient = defaultClient()) {

    @Volatile
    var control: DownloadControl = DownloadControl.NONE
        private set

    fun pause() {
        control = DownloadControl.PAUSE
    }

    fun cancel() {
        control = DownloadControl.CANCEL
    }

    fun resetControl() {
        control = DownloadControl.NONE
    }

    /**
     * Downloads [url] into [target]. Resumes from an existing [target] `.part`. Calls [onProgress]
     * after every chunk with a [DownloadProgress] snapshot.
     *
     * @return the final file ([target] renamed from `.part`).
     * @throws PausedException when paused, [IOException] on network failure.
     */
    suspend fun download(
        url: String,
        target: File,
        expectedSizeBytes: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            control = DownloadControl.NONE
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            val alreadyHave = if (part.exists()) part.length() else 0L

            val builder = Request.Builder().url(url)
            if (alreadyHave > 0) {
                builder.header("Range", "bytes=$alreadyHave-")
                Logs.network("resuming download of ${target.name} from byte $alreadyHave")
            }

            val response =
                try {
                    client.newCall(builder.build()).execute()
                } catch (e: IOException) {
                    Logs.network("download failed: ${e.message}")
                    throw e
                }

            response.use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
                val body = resp.body ?: throw IOException("Empty body for $url")

                // Server may ignore Range; fall back to a fresh download then.
                val resumeFrom = if (resp.code == 206) alreadyHave else 0L
                val contentLength = body.contentLength()
                val total = if (contentLength > 0) resumeFrom + contentLength else expectedSizeBytes

                val sink =
                    if (resumeFrom > 0) {
                        java.io.FileOutputStream(part, true)
                    } else {
                        java.io.FileOutputStream(part, false)
                    }

                sink.use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = resumeFrom
                    var lastReport = System.currentTimeMillis()
                    var lastReportBytes = downloaded
                    var eta = 0L
                    var speed = 0L

                    while (true) {
                        if (control == DownloadControl.PAUSE) throw PausedException(downloaded)
                        if (control == DownloadControl.CANCEL) {
                            part.delete()
                            throw IOException("Download cancelled")
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n

                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 250) {
                            val dt = (now - lastReport).coerceAtLeast(1)
                            speed = ((downloaded - lastReportBytes) * 1000L) / dt
                            eta = if (speed > 0 && total > 0) (total - downloaded) / speed else 0L
                            lastReport = now
                            lastReportBytes = downloaded
                            onProgress(
                                DownloadProgress(
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    bytesPerSecond = speed,
                                    etaSeconds = eta,
                                ),
                            )
                        }
                    }
                    onProgress(
                        DownloadProgress(
                            downloadedBytes = downloaded,
                            totalBytes = if (total > 0) total else downloaded,
                            bytesPerSecond = speed,
                            etaSeconds = eta,
                        ),
                    )
                }

                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                Logs.network("download complete: ${target.name} (${target.length()} bytes)")
                target
            }
        }

    class PausedException(val downloadedBytes: Long) : Exception("paused")

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
    }
}
