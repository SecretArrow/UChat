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
import com.uchat.android.linux.install.ScriptInstaller
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * THE end-to-end proof for the user's complaint "kok masih ga bisa install ai tools seperti
 * nodejs": exercises the EXACT production tool-install path on the emulator —
 *
 * real rootfs (download -> SHA-256 -> extract) -> ScriptInstaller copies scripts into the proot
 * bind dir -> Shell.runScript(install-essentials.sh) -> Shell.runScript(install-node.sh) -> `node
 * --version` really resolves inside Ubuntu.
 *
 * No mocks anywhere. This is the same code path the AI Tools hub drives.
 */
@RunWith(AndroidJUnit4::class)
class RealToolInstallE2E {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installsNodeJsThroughRealProductionPath() = runBlocking {
        val app = context.applicationContext as com.uchat.android.UChatApp
        app.container.installer.cancel()

        val paths = UChatPaths(context)
        paths.ensureDirs()
        paths.ubuntuRoot.deleteRecursively()

        val registry = RegistryLoader(context).loadAssets()
        val abi = DeviceAbi.current()
        val rootfsEntry = registry.rootfsFor(abi)
        assertNotNull("registry must have a rootfs for $abi", rootfsEntry)
        val entry = rootfsEntry!!

        // 1-3. Real download -> checksum -> extraction (same as RealUbuntuShellE2E).
        val archive = File(paths.downloadsDir, entry.id + ".e2e-tools")
        archive.delete()
        val downloaded = Downloader().download(entry.url, archive, entry.sizeBytes) {}
        assertEquals(entry.sizeBytes, downloaded.length())
        assertTrue("SHA-256 must match", Checksum.matches(downloaded, entry.sha256))
        val entries = Extractor.extractTarGz(downloaded, paths.ubuntuRoot, entry.extractedBytes)
        assertTrue(entries > 1000)
        downloaded.delete()

        // 4. Environment init identical to the installer's initialize step.
        Proot.syncResolvConf(paths)
        File(paths.ubuntuRoot, "etc/hostname").writeText("uchat\n")
        File(paths.ubuntuRoot, "etc/hosts").writeText("127.0.0.1 localhost uchat\n")
        listOf("dev", "proc", "sys", "tmp", "root/workspace", "root/downloads").forEach {
            File(paths.ubuntuRoot, it).mkdirs()
        }
        assertTrue(paths.isUbuntuInstalled)

        // 5. Production script deployment (same call AppContainer makes at startup).
        ScriptInstaller.install(context, paths)
        val bindDir = paths.scriptsDir
        assertTrue(
            "install-node.sh must be deployed into the proot bind dir",
            File(bindDir, "install-node.sh").isFile,
        )

        val shell = Shell(paths, abi)

        // 6. Essentials first — exactly like UbuntuInstaller step 5 (curl/xz needed by node).
        val essentials = shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-essentials.sh") {}
        assertTrue(
            "essentials failed rc=${essentials.exitCode}: ${essentials.combined.take(600)}",
            essentials.success,
        )

        // 7. THE FIX UNDER TEST: real Node.js install (tarball route, NodeSource fallback).
        val nodeInstall = shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-node.sh") {}
        assertTrue(
            "node install failed rc=${nodeInstall.exitCode}: ${nodeInstall.combined.take(600)}",
            nodeInstall.success,
        )

        // 8. Fresh shell must resolve node + npm off PATH (symlinks into /usr/local/bin).
        val nodeVersion = shell.exec(listOf("node", "--version"), timeoutSeconds = 60)
        assertTrue(
            "node --version failed rc=${nodeVersion.exitCode}: ${nodeVersion.combined.take(400)}",
            nodeVersion.success,
        )
        assertTrue(
            "expected Node v22.x, got: ${nodeVersion.stdout.trim()}",
            nodeVersion.stdout.trim().startsWith("v22."),
        )
        val npmVersion = shell.exec(listOf("npm", "--version"), timeoutSeconds = 60)
        assertTrue("npm --version failed: ${npmVersion.combined.take(400)}", npmVersion.success)

        // 9. Idempotency: a second run must exit 0 immediately ("already present").
        val rerun = shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-node.sh") {}
        assertTrue("re-install must be a no-op success", rerun.success)
    }
}
