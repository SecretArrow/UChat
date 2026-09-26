package com.uchat.android.linux

import java.io.File
import java.security.MessageDigest

/** Streaming SHA-256 used by the installer verification step (spec #4). */
object Checksum {

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            var n = input.read(buffer)
            while (n >= 0) {
                md.update(buffer, 0, n)
                n = input.read(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expectedSha256: String): Boolean =
        sha256(file).equals(expectedSha256.lowercase(), ignoreCase = true)
}
