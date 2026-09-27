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
 * nodejs": exercises the EXACT production tool-install path —
 *
 * real rootfs (download -> SHA-256 -> extract) -> ScriptInstaller deploys scripts into the proot
 * bind dir -> Node.js tarball staged into the Downloads bind (checksum-verified against nodejs.org
 * SHASUMS256.txt) -> Shell.runScript(install-node.sh) -> `node --version` really resolves inside
 * Ubuntu.
 *
 * The tarball is staged from the HOST side because guest-side networking (apt/curl inside proot) is
 * not guaranteed on CI emulators — the app supports exactly this offline route for users with flaky
 * connectivity. On real devices the same script transparently uses curl. No mocks anywhere: this is
 * the same code path the AI Tools hub drives.
 */
@RunWith(AndroidJUnit4::class)
class RealToolInstallE2E {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun nodeArchFor(abi: DeviceAbi): String =
        when (abi.androidAbi) {
            "arm64-v8a" -> "arm64"
            "x86_64" -> "x64"
            "armeabi-v7a" -> "armv7l"
            "x86" -> "x86"
            else -> error("unsupported ABI ${abi.androidAbi}")
        }

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
        // Production semantics: isUbuntuInstalled requires the step-10 ready marker.
        paths.markInstalled()
        assertTrue(paths.isUbuntuInstalled)

        // 5. Production script deployment (same call AppContainer makes at startup).
        ScriptInstaller.install(context, paths)
        assertTrue(
            "install-node.sh must be deployed into the proot bind dir",
            File(paths.scriptsDir, "install-node.sh").isFile,
        )

        val shell = Shell(paths, abi)

        // 6. Essentials exactly like UbuntuInstaller step 5. Guest networking (apt) is an
        // environment capability, not an app defect — a failure here is logged, not fatal,
        // because the node tarball route does not depend on guest networking.
        val essentials = shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-essentials.sh") {}
        println("[e2e] essentials rc=${essentials.exitCode} — diagnostics if failed:")
        println(essentials.combined.take(1200))

        // 7. Stage the Node.js tarball from the HOST side, checksum-verified against the
        // official SHASUMS256.txt, into the bind-mounted Downloads dir.
        // .tar.gz on purpose: gzip is guaranteed in every Ubuntu rootfs, while xz-utils is
        // missing from ubuntu-base (the first CI run proved tar -xJf dies without xz).
        val nodeArch = nodeArchFor(abi)
        val nodeVersion = "v22.14.0"
        val tarballName = "node-${nodeVersion}-linux-${nodeArch}.tar.gz"
        val tarballUrl = "https://nodejs.org/dist/${nodeVersion}/$tarballName"
        val shasums = File(paths.downloadsDir, "SHASUMS256.txt")
        shasums.delete()
        val sumsBytes =
            Downloader().download(
                "https://nodejs.org/dist/${nodeVersion}/SHASUMS256.txt",
                shasums,
                0,
            ) {}
        val expectedSha =
            sumsBytes
                .readLines()
                .firstOrNull { it.endsWith("  $tarballName") }
                ?.split(" ")
                ?.firstOrNull()
        assertNotNull("SHASUMS256.txt must contain $tarballName", expectedSha)

        val stagedTarball = File(paths.downloadsDir, tarballName)
        stagedTarball.delete()
        val nodeFile = Downloader().download(tarballUrl, stagedTarball, 0) {}
        assertTrue(
            "staged tarball must match official SHA-256",
            Checksum.matches(nodeFile, expectedSha!!)
        )

        // 8. THE FIX UNDER TEST: real Node.js install through the production script.
        val nodeInstall = shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-node.sh") {}
        if (!nodeInstall.success) {
            // Full diagnostics for the next CI iteration: sizes, guest disk, complete output.
            println("[e2e] staged tarball bytes=" + stagedTarball.length())
            println("[e2e] guest free bytes=" + paths.filesDir.usableSpace)
            println("[e2e] install-node.sh full output:")
            println(nodeInstall.combined.take(4000))
        }
        assertTrue(
            "node install failed rc=${nodeInstall.exitCode}: ${nodeInstall.combined.take(800)}",
            nodeInstall.success,
        )

        // 9. Fresh exec must resolve node + npm off PATH (symlinks into /usr/local/bin).
        val nodeVersionOut = shell.exec(listOf("node", "--version"), timeoutSeconds = 60)
        assertTrue(
            "node --version failed rc=${nodeVersionOut.exitCode}: ${nodeVersionOut.combined.take(400)}",
            nodeVersionOut.success,
        )
        assertTrue(
            "expected Node ${nodeVersion}, got: ${nodeVersionOut.stdout.trim()}",
            nodeVersionOut.stdout.trim().startsWith(nodeVersion.substring(0, 4)),
        )
        val npmVersion = shell.exec(listOf("npm", "--version"), timeoutSeconds = 60)
        assertTrue("npm --version failed: ${npmVersion.combined.take(400)}", npmVersion.success)

        // 10. Idempotency: a second run must exit 0 immediately ("already present").
        val rerun = shell.runScript("${Proot.UBUNTU_SCRIPTS}/install-node.sh") {}
        assertTrue("re-install must be a no-op success", rerun.success)
    }
}
