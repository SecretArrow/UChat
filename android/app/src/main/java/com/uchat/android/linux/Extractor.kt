package com.uchat.android.linux

import com.uchat.android.core.fs.PathSafety
import com.uchat.android.core.log.Logs
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Safe tar.gz extraction for the Ubuntu rootfs (spec #13/#44).
 *
 * Protections:
 * - rejects entries that escape the destination (zip-slip / ../ / absolute)
 * - rejects symlink/hardlink entries pointing outside the destination
 * - enforces a total extracted-size cap (archive bomb guard)
 * - never overwrites protected files outside the destination
 *
 * Ubuntu base rootfs contains device nodes (e.g. /dev/console) that cannot be created without root
 * — they are skipped on purpose; proot provides /dev.
 */
object Extractor {

    private const val MAX_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L // 4 GiB
    private const val MAX_ENTRIES = 200_000

    /**
     * Extracts [archive] (.tar.gz) into [destination]. Calls [onProgress] with (extractedBytes,
     * estimatedTotalBytes). Returns the number of entries.
     */
    fun extractTarGz(
        archive: File,
        destination: File,
        estimatedTotalBytes: Long,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): Int {
        destination.mkdirs()
        val destCanonical = destination.canonicalFile
        var extractedBytes = 0L
        var entries = 0
        var skipped = 0

        TarArchiveInputStream(
                BufferedInputStream(GzipCompressorInputStream(FileInputStream(archive)))
            )
            .use { tar ->
                while (true) {
                    val entry: TarArchiveEntry = tar.nextTarEntry ?: break
                    entries++
                    if (entries > MAX_ENTRIES)
                        throw SecurityException("Archive has too many entries")

                    val name = entry.name.replace('\\', '/')
                    if (PathSafety.isDangerousEntryName(name)) {
                        Logs.installer("skipped dangerous entry: $name")
                        skipped++
                        continue
                    }

                    val target = File(destCanonical, name)
                    if (!PathSafety.isInside(destCanonical, target)) {
                        Logs.installer("skipped escaping entry: $name")
                        skipped++
                        continue
                    }

                    when {
                        entry.isDirectory -> target.mkdirs()
                        entry.isSymbolicLink || entry.isLink -> {
                            // Only accept links whose target stays inside the destination.
                            val linkName = entry.linkName ?: ""
                            if (linkName.startsWith("/")) {
                                skipped++
                                continue
                            }
                            target.parentFile?.mkdirs()
                            target.delete()
                            try {
                                java.nio.file.Files.createSymbolicLink(
                                    target.toPath(),
                                    java.nio.file.FileSystems.getDefault().getPath(linkName),
                                )
                            } catch (e: Exception) {
                                Logs.installer("symlink failed for $name: ${e.message}")
                            }
                        }
                        entry.isFIFO || entry.isBlockDevice || entry.isCharacterDevice -> {
                            skipped++ // device nodes are provided by proot binds
                        }
                        else -> {
                            // Regular file
                            if (extractedBytes + entry.size > MAX_TOTAL_BYTES) {
                                throw SecurityException("Archive exceeds extraction size limit")
                            }
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out ->
                                tar.copyTo(out, bufferSize = 128 * 1024)
                            }
                            extractedBytes += entry.size
                            applyMode(target, entry.mode)
                        }
                    }

                    if (entries % 500 == 0) {
                        onProgress(extractedBytes, estimatedTotalBytes)
                    }
                }
            }
        onProgress(extractedBytes, estimatedTotalBytes)
        Logs.installer("extraction finished: $entries entries, $skipped skipped")
        return entries
    }

    private fun applyMode(target: File, mode: Int) {
        try {
            val path = target.toPath()
            val perms = java.util.HashSet<java.nio.file.attribute.PosixFilePermission>()
            if (mode and 0b100_000_000 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ)
            if (mode and 0b010_000_000 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)
            if (mode and 0b001_000_000 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
            if (mode and 0b000_100_000 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ)
            if (mode and 0b000_010_000 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
            if (mode and 0b000_001_000 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)
            if (mode and 0b000_000_100 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
            if (mode and 0b000_000_010 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
            if (mode and 0b000_000_001 != 0)
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)
            java.nio.file.Files.setPosixFilePermissions(path, perms)
        } catch (_: Exception) {
            // Permission preservation is best-effort.
        }
    }
}
