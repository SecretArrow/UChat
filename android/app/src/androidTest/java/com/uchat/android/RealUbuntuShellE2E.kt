package com.uchat.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.data.registry.RegistryLoader
import com.uchat.android.linux.Checksum
import com.uchat.android.linux.Extractor
import com.uchat.android.linux.Proot
import com.uchat.android.linux.downloader.Downloader
import com.uchat.android.linux.exec.Shell
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * THE definitive end-to-end proof that "installing Ubuntu" works: this test exercises the app's
 * real pipeline on the emulator —
 *
 * real download (OkHttp, resumable path) -> real SHA-256 verification -> real tar.gz extraction ->
 * real proot exec (bundled libproot.so) -> real bash INSIDE the Ubuntu rootfs.
 *
 * The long package-install steps (apt/runtimes/opencode/claude) are intentionally excluded: they
 * take tens of minutes and are exercised on real usage. Everything here is production code, no
 * mocks.
 */
@RunWith(AndroidJUnit4::class)
class RealUbuntuShellE2E {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun downloadsVerifiesExtractsAndExecutesRealBash() = runBlocking {
        val app = context.applicationContext as com.uchat.android.UChatApp
        // Stop any installer the wizard e2e left running, then take over the layout.
        app.container.installer.cancel()

        val paths = UChatPaths(context)
        paths.ensureDirs()
        paths.ubuntuRoot.deleteRecursively()

        val registry = RegistryLoader(context).loadAssets()
        val abi = DeviceAbi.current()
        val entry = registry.rootfsFor(abi)
        assertNotNull("registry must have a rootfs for $abi", entry)
        val rootfsEntry = entry!!

        // 1. Real download (resumable-capable OkHttp downloader used by the installer).
        val archive = File(paths.downloadsDir, rootfsEntry.id + ".e2e")
        archive.delete()
        val downloaded = Downloader().download(rootfsEntry.url, archive, rootfsEntry.sizeBytes) {}
        assertTrue(
            "downloaded size must match registry",
            downloaded.length() == rootfsEntry.sizeBytes,
        )

        // 2. Real checksum verification (the step that used to kill every arm64 install).
        assertTrue(
            "SHA-256 must match the pinned value",
            Checksum.matches(downloaded, rootfsEntry.sha256),
        )

        // 3. Real safe extraction.
        val entries =
            Extractor.extractTarGz(downloaded, paths.ubuntuRoot, rootfsEntry.extractedBytes)
        assertTrue("rootfs must contain many entries", entries > 1000)
        downloaded.delete()

        // 4. Minimal environment initialization (same as the installer's initialize step).
        Proot.syncResolvConf(paths)
        File(paths.ubuntuRoot, "etc/hostname").writeText("uchat\n")
        File(paths.ubuntuRoot, "etc/hosts").writeText("127.0.0.1 localhost uchat\n")
        listOf("dev", "proc", "sys", "tmp", "root/workspace", "root/downloads").forEach {
            File(paths.ubuntuRoot, it).mkdirs()
        }

        // The app must now consider Ubuntu installed (bash + proot in place).
        assertTrue("isUbuntuInstalled must be true", paths.isUbuntuInstalled)

        // 5. Real shell execution INSIDE the Ubuntu rootfs through the bundled proot.
        val shell = Shell(paths, abi)
        val echo = shell.exec(listOf("/bin/echo", "UCHAT_ECHO_OK_9154"), timeoutSeconds = 120)
        assertTrue(
            "echo failed rc=${echo.exitCode}: ${echo.combined.take(400)}",
            echo.success && echo.stdout.contains("UCHAT_ECHO_OK_9154"),
        )

        val bash = shell.exec(listOf("/bin/bash", "-c", "echo UCHAT_BASH_OK && uname -m && id -u"))
        assertTrue(
            "bash failed rc=${bash.exitCode}: ${bash.combined.take(400)}",
            bash.success && bash.stdout.contains("UCHAT_BASH_OK"),
        )
        assertEquals("fake root inside proot", "0", bash.stdout.trim().lineSequence().last())
    }
}
