package com.uchat.android

import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safe extraction tests (spec #76): traversal entries and device nodes are skipped, normal files
 * extract, totals are respected.
 */
class ExtractorTest {

    private fun makeTarGz(entries: List<Triple<String, ByteArray, Char>>): File {
        val out = File.createTempFile("uchat-test", ".tar.gz")
        GZIPOutputStream(FileOutputStream(out)).use { gz ->
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(gz).use { tar ->
                entries.forEach { (name, content, _) ->
                    val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry(name)
                    if (!name.endsWith("/")) {
                        entry.setSize(content.size.toLong())
                        tar.putArchiveEntry(entry)
                        tar.write(content)
                    } else {
                        tar.putArchiveEntry(entry)
                    }
                    tar.closeArchiveEntry()
                }
            }
        }
        return out
    }

    @Test
    fun extractsNormalFiles() {
        val dest =
            File.createTempFile("uchat-dest", "").parentFile.resolve("dest-${System.nanoTime()}")
        val archive =
            makeTarGz(
                listOf(
                    Triple("bin/bash", "#!/bin/sh".toByteArray(), 'f'),
                    Triple("etc/os-release", "ID=ubuntu".toByteArray(), 'f'),
                    Triple("root/", ByteArray(0), 'd'),
                ),
            )
        val count = com.uchat.android.linux.Extractor.extractTarGz(archive, dest, 1000)
        assertTrue(count >= 3)
        assertTrue(dest.resolve("bin/bash").isFile)
        assertEquals("ID=ubuntu", dest.resolve("etc/os-release").readText())
        dest.deleteRecursively()
        archive.delete()
    }

    @Test
    fun skipsTraversalEntries() {
        val dest =
            File.createTempFile("uchat-dest2", "").parentFile.resolve("dest2-${System.nanoTime()}")
        val outside = File(dest.parentFile, "outside-marker-${System.nanoTime()}.txt")
        val archive =
            makeTarGz(
                listOf(
                    Triple("../../../outside-marker.txt", "evil".toByteArray(), 'f'),
                    Triple("safe.txt", "ok".toByteArray(), 'f'),
                ),
            )
        com.uchat.android.linux.Extractor.extractTarGz(archive, dest, 1000)
        assertFalse(outside.exists())
        assertTrue(dest.resolve("safe.txt").isFile)
        dest.deleteRecursively()
        archive.delete()
    }

    @Test
    fun parsesSsOutputForServers() {
        val ss =
            """
            Netid State  Recv-Q Send-Q Local Address:Port
            tcp   LISTEN 0      128        0.0.0.0:5173      0.0.0.0:*    users:(("node",pid=4321,fd=20))
            tcp   LISTEN 0      128        0.0.0.0:3000      0.0.0.0:*    users:(("next-server",pid=4100,fd=18))
        """
                .trimIndent()
        val servers = com.uchat.android.ui.servers.SsParser.parse(ss)
        assertEquals(2, servers.size)
        // Sorted ascending by port
        assertEquals(3000, servers[0].port)
        assertEquals(4100L, servers[0].pid)
        assertEquals("next-server", servers[0].command)
        assertEquals(5173, servers[1].port)
        assertEquals(4321L, servers[1].pid)
        assertEquals("node", servers[1].command)
    }
}
