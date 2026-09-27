package com.uchat.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uchat.android.linux.Pty
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ON-DEVICE proof that the Linux execution layer actually works (the "nothing happens" killer).
 *
 * Android 10+ forbids apps targeting API 29+ from exec()ing binaries stored in the writable data
 * directory. proot therefore ships as libproot.so inside the APK, extracted by the package manager
 * into nativeLibraryDir where exec() is allowed. These tests fail on the previous design because
 * the executable never lived in an executable location.
 */
@RunWith(AndroidJUnit4::class)
class ProotExecE2E {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledProotIsPresentAndExecutable() {
        val libDir = context.applicationInfo.nativeLibraryDir
        assertNotNull("nativeLibraryDir must exist", libDir)
        val proot = File(libDir, "libproot.so")
        val contents = libDir?.let { File(it).list()?.joinToString(", ") }
        assertTrue(
            "libproot.so must be bundled in the APK and extracted to $libDir " +
                "(dir contents: [$contents])",
            proot.isFile,
        )
        assertTrue("libproot.so must have the exec bit", proot.canExecute())
        assertTrue(
            "libproot.so must be a real binary (>100KB), got ${proot.length()} bytes",
            proot.length() > 100_000,
        )
        // proot's companion libs and its loader must be extracted next to it.
        assertTrue(
            "libtalloc.so must be bundled next to proot",
            File(libDir, "libtalloc.so").isFile,
        )
        assertTrue(
            "libandroid-shmem.so must be bundled next to proot",
            File(libDir, "libandroid-shmem.so").isFile,
        )
        assertTrue(
            "libprootloader.so must be bundled (guest execve needs it)",
            File(libDir, "libprootloader.so").isFile,
        )
    }

    /** Host-side env every proot invocation needs on Android (mirrors Proot.environment). */
    private fun prootEnv(process: ProcessBuilder) {
        val libDir = context.applicationInfo.nativeLibraryDir
        process.environment()["LD_LIBRARY_PATH"] = libDir
        process.environment()["PROOT_TMP_DIR"] = context.cacheDir.absolutePath
        // PROOT_NO_SECCOMP must stay unset — with it, rename() returns ENOSYS on
        // x86_64 emulators/modern Android (termux/proot#390) and dpkg cannot commit.
        process.environment()["PROOT_LOADER"] = "$libDir/libprootloader.so"
    }

    @Test
    fun prootReallyExecutesFromNativeLibraryDir() {
        val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        val process =
            ProcessBuilder(proot.absolutePath, "--version")
                .apply { prootEnv(this) }
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("proot --version must exit 0", process.waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        assertTrue(
            "expected 'proot' in --version output, got: $output",
            output.contains("proot", ignoreCase = true),
        )
    }

    @Test
    fun prootRunsItsOwnHelpWithoutRootfs() {
        // Exercises more than a single flag: proot must initialise its loader/heap on-device.
        val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        val process =
            ProcessBuilder(proot.absolutePath, "--help")
                .apply { prootEnv(this) }
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        assertTrue(output.contains("usage", ignoreCase = true))
    }

    @Test
    fun ptySessionExecutesAndStreamsRealOutput() = runBlocking {
        val marker = "UCHAT_PTY_OK_${System.currentTimeMillis() % 1_000_000}"
        val handle =
            Pty.nativeOpenTerminal(
                arrayOf("/system/bin/sh", "-c", "echo $marker"),
                context.filesDir.absolutePath,
                arrayOf("PATH=/system/bin:/system/xbin"),
                24,
                80,
            )
        assertTrue("nativeOpenTerminal must return a valid handle", handle != 0L)

        val collected = StringBuilder()
        val buffer = ByteArray(4096)
        try {
            withTimeout(20_000) {
                withContext(Dispatchers.IO) {
                    while (!collected.contains(marker)) {
                        val n = Pty.nativeRead(handle, buffer)
                        if (n <= 0) break
                        collected.append(String(buffer, 0, n))
                    }
                }
            }
        } finally {
            Pty.nativeClose(handle)
        }
        assertTrue(
            "PTY output must contain the marker, got: '${collected}'",
            collected.contains(marker),
        )
    }

    @Test
    fun ptyHandlesTwoConcurrentSessions() = runBlocking {
        val results = mutableListOf<String>()
        val jobs =
            (1..2).map { i ->
                launch(Dispatchers.IO) {
                    val marker = "UCHAT_CONCURRENT_$i"
                    val handle =
                        Pty.nativeOpenTerminal(
                            arrayOf("/system/bin/sh", "-c", "echo $marker; sleep 0.2"),
                            context.filesDir.absolutePath,
                            arrayOf("PATH=/system/bin:/system/xbin"),
                            24,
                            80,
                        )
                    val sb = StringBuilder()
                    val buffer = ByteArray(4096)
                    while (!sb.contains(marker)) {
                        val n = Pty.nativeRead(handle, buffer)
                        if (n <= 0) break
                        sb.append(String(buffer, 0, n))
                    }
                    Pty.nativeClose(handle)
                    synchronized(results) { results.add(sb.toString()) }
                }
            }
        jobs.joinAll()
        assertEquals(2, results.size)
        assertTrue(results[0].contains("UCHAT_CONCURRENT_"))
        assertTrue(results[1].contains("UCHAT_CONCURRENT_"))
    }
}
