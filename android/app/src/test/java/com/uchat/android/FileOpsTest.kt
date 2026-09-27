package com.uchat.android

import com.uchat.android.core.fs.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the Files-tab command builders and their safety guards. */
class FileOpsTest {

    @Test
    fun `delete guard blocks protected paths`() {
        assertTrue(FileOps.isDeletable("/root/workspace/projects/app/main.kt"))
        assertTrue(FileOps.isDeletable("/root/downloads/archive.tar.gz"))
        assertFalse(FileOps.isDeletable("/"))
        assertFalse(FileOps.isDeletable("/root"))
        assertFalse(FileOps.isDeletable("/usr"))
        assertFalse(FileOps.isDeletable("/etc"))
        assertFalse(FileOps.isDeletable("/usr/bin"))
        assertFalse(FileOps.isDeletable("/var/lib/apt"))
        assertFalse(FileOps.isDeletable("/root/workspace"))
        assertFalse(FileOps.isDeletable("/root/downloads"))
        assertFalse(FileOps.isDeletable("/root/.uchat-scripts"))
        assertFalse(FileOps.isDeletable(""))
        assertFalse(FileOps.isDeletable("/root/workspace/../.."))
    }

    @Test
    fun `trailing slash does not bypass the guard`() {
        assertFalse(FileOps.isDeletable("/root/"))
        assertFalse(FileOps.isDeletable("/usr/bin/"))
    }

    @Test
    fun `single quotes are escaped correctly`() {
        // POSIX single-quote escaping: close quote, escaped quote, reopen.
        assertEquals("'it'\\''s'", FileOps.quote("it's"))
        assertEquals("''", FileOps.quote(""))
    }

    @Test
    fun `commands embed the quoted path`() {
        val rename = FileOps.rename("/a/b", "/a/c")
        assertTrue(rename.contains("mv -n"))
        assertTrue(rename.contains("'/a/b'"))
        assertTrue(rename.contains("'/a/c'"))
        assertTrue(FileOps.delete("/tmp/x").startsWith("rm -rf -- "))
        assertTrue(FileOps.mkdir("/tmp/y").startsWith("mkdir -p -- "))
        assertTrue(FileOps.touch("/tmp/z").startsWith("touch -- "))
        assertTrue(FileOps.extractTarGz("/a.tgz", "/dst").contains("tar -xzf"))
        assertTrue(FileOps.extractTar("/a.tar", "/dst").contains("tar -xf"))
    }

    @Test
    fun `writeBase64 pipes through base64 -d`() {
        val cmd = FileOps.writeBase64("/root/f.txt", "aGVsbG8=")
        assertTrue(cmd.contains("base64 -d"))
        assertTrue(cmd.contains("'aGVsbG8='"))
        assertTrue(cmd.endsWith("> '/root/f.txt'"))
    }

    @Test
    fun `name validation`() {
        assertTrue(FileOps.isAcceptableName("main.kt"))
        assertTrue(FileOps.isAcceptableName(".env"))
        assertTrue(FileOps.isAcceptableName("my folder"))
        assertFalse(FileOps.isAcceptableName(""))
        assertFalse(FileOps.isAcceptableName("  "))
        assertFalse(FileOps.isAcceptableName(".."))
        assertFalse(FileOps.isAcceptableName("."))
        assertFalse(FileOps.isAcceptableName("a/b"))
        assertFalse(FileOps.isAcceptableName("a\u0000b"))
    }

    @Test
    fun `archive detection`() {
        assertTrue(FileOps.isArchive("dist.tar.gz"))
        assertTrue(FileOps.isArchive("dist.tgz"))
        assertTrue(FileOps.isArchive("dist.tar"))
        assertFalse(FileOps.isArchive("notes.txt"))
        assertFalse(FileOps.isArchive("dist.zip"))
    }

    @Test
    fun `text file detection`() {
        assertTrue(FileOps.isTextFile("notes.txt"))
        assertTrue(FileOps.isTextFile("Main.kt"))
        assertTrue(FileOps.isTextFile("app.py"))
        assertTrue(FileOps.isTextFile(".gitignore"))
        assertTrue(FileOps.isTextFile(".env"))
        assertFalse(FileOps.isTextFile("image.png"))
        assertFalse(FileOps.isTextFile("app.apk"))
        assertFalse(FileOps.isTextFile("libfoo.so"))
    }
}
