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
import com.uchat.android.linux.exec.ProcessPausedException
import com.uchat.android.linux.exec.Shell
import com.uchat.android.linux.install.ScriptInstaller
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end resilience proof for the v1.6.0 field reports:
 *
 * 1. "E: dpkg was interrupted, you must manually run 'dpkg --configure -a'" — a run killed
 *    mid-apt (Android background kill / battery / network stall) wedged the package database
 *    so EVERY later retry failed. dpkg-recover.sh must heal exactly that state, offline.
 * 2. Pause during a running script step must actually stop the process (previously pause()
 *    only affected downloads — during apt the button did nothing), quickly and cleanly.
 *
 * Everything runs against the real rootfs through the real proot — no mocks.
 */
@RunWith(AndroidJUnit4::class)
class InstallResilienceE2E {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Real download -> checksum -> extract, skipped when a previous test already extracted. */
    private suspend fun ensureRootfs(paths: UChatPaths) {
        if (File(paths.ubuntuRoot, "bin/bash").isFile) return
        val registry = RegistryLoader(context).loadAssets()
        val abi = DeviceAbi.current()
        val entry = registry.rootfsFor(abi)
        assertNotNull("registry must have a rootfs for $abi", entry)
        val rootfsEntry = entry!!
        val archive = File(paths.downloadsDir, rootfsEntry.id + ".e2e-resilience")
        archive.delete()
        val downloaded =
            Downloader().download(rootfsEntry.url, archive, rootfsEntry.sizeBytes) {}
        assertTrue(Checksum.matches(downloaded, rootfsEntry.sha256))
        val entries =
            Extractor.extractTarGz(downloaded, paths.ubuntuRoot, rootfsEntry.extractedBytes)
        assertTrue("rootfs must contain many entries", entries > 1000)
        downloaded.delete()
        Proot.syncResolvConf(paths)
        File(paths.ubuntuRoot, "etc/hostname").writeText("uchat\n")
        File(paths.ubuntuRoot, "etc/hosts").writeText("127.0.0.1 localhost uchat\n")
        listOf("dev", "proc", "sys", "tmp", "root/workspace", "root/downloads").forEach {
            File(paths.ubuntuRoot, it).mkdirs()
        }
        ScriptInstaller.install(context, paths)
    }

    @Test
    fun dpkgRecoveryHealsInterruptedPackageState() = runBlocking {
        val app = context.applicationContext as com.uchat.android.UChatApp
        app.container.installer.cancel()
        val paths = UChatPaths(context)
        paths.ensureDirs()
        ensureRootfs(paths)

        val shell = Shell(paths, DeviceAbi.current())
        val dpkgUpdates = File(paths.ubuntuRoot, "var/lib/dpkg/updates")
        val lockFront = File(paths.ubuntuRoot, "var/lib/dpkg/lock-frontend")
        val lock = File(paths.ubuntuRoot, "var/lib/dpkg/lock")

        // --- Scenario A: journal file left behind (what apt reports as "dpkg was interrupted").
        dpkgUpdates.mkdirs()
        File(dpkgUpdates, "0001").writeText("")
        lockFront.writeText("")
        lock.writeText("")
        val healedA = shell.runScript("${Proot.UBUNTU_SCRIPTS}/dpkg-recover.sh")
        assertTrue(
            "recovery must succeed on an interrupted journal: ${healedA.combined.take(300)}",
            healedA.success,
        )
        assertEquals(
            "journal must be cleared after recovery",
            emptyList<String>(),
            dpkgUpdates.listFiles()?.map { it.name } ?: emptyList(),
        )
        assertFalse("stale lock-frontend must be removed", lockFront.exists())
        assertFalse("stale lock must be removed", lock.exists())

        // --- Scenario B: CORRUPT journal (parse error) — the fallback clear-and-retry path.
        File(dpkgUpdates, "0002").writeText("garbage-binary-journal-\u0000\u0001\u0002")
        val healedB = shell.runScript("${Proot.UBUNTU_SCRIPTS}/dpkg-recover.sh")
        assertTrue(
            "recovery must survive a corrupt journal: ${healedB.combined.take(300)}",
            healedB.success,
        )
        assertEquals(
            "corrupt journal must be cleared",
            emptyList<String>(),
            dpkgUpdates.listFiles()?.map { it.name } ?: emptyList(),
        )

        // --- After healing, dpkg must consider the database consistent (no pending actions).
        val audit = shell.exec(listOf("/usr/bin/dpkg", "--audit"), timeoutSeconds = 120)
        assertTrue(
            "dpkg --audit must report a clean database, got: ${audit.stdout.take(300)}",
            audit.success && audit.stdout.isBlank(),
        )

        // --- The production entrypoint: install-essentials.sh starts with the recovery hook.
        val essentials = File(paths.scriptsDir, "install-essentials.sh")
        val recover = File(paths.scriptsDir, "dpkg-recover.sh")
        assertTrue("install-essentials.sh must be deployed", essentials.isFile)
        assertTrue("dpkg-recover.sh must be deployed next to it", recover.isFile)
        assertTrue(
            "install-essentials.sh must invoke dpkg-recover.sh before apt",
            essentials.readText().contains("dpkg-recover.sh"),
        )
    }

    @Test
    fun shellPauseStopsARunningScriptQuickly() = runBlocking {
        val app = context.applicationContext as com.uchat.android.UChatApp
        app.container.installer.cancel()
        val paths = UChatPaths(context)
        paths.ensureDirs()
        ensureRootfs(paths)

        val shell = Shell(paths, DeviceAbi.current())
        val flag = AtomicBoolean(false)
        val timer =
            Thread {
                Thread.sleep(1500)
                flag.set(true)
            }
                .apply {
                    isDaemon = true
                    start()
                }

        val started = System.currentTimeMillis()
        try {
            shell.exec(
                listOf("/bin/bash", "-c", "echo ALIVE; sleep 120; echo NEVER"),
                timeoutSeconds = 60,
                pauseRequested = { flag.get() },
            )
            fail("exec must throw ProcessPausedException when pause is requested")
        } catch (expected: ProcessPausedException) {
            val elapsed = System.currentTimeMillis() - started
            assertTrue(
                "pause must stop the script within seconds (took ${elapsed}ms)",
                elapsed < 30_000,
            )
            assertTrue("pause must not wait for the 120s sleep", elapsed < 60_000)
        } finally {
            timer.interrupt()
        }

        // And the environment must still be perfectly usable right after a pause-kill.
        val after = shell.exec(listOf("/bin/echo", "STILL_OK"), timeoutSeconds = 60)
        assertTrue(after.success)
        assertTrue(after.stdout.contains("STILL_OK"))
    }
}
