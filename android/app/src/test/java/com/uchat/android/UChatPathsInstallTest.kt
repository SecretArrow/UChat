package com.uchat.android

import com.uchat.android.core.fs.UChatPaths
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the install decision + legacy migration logic ([UChatPaths.isInstalled] /
 * [UChatPaths.migrateLegacyInstall]) — pure `File`-based companions, no Android context needed.
 *
 * Regression context: isUbuntuInstalled used to return true as soon as rootfs/bin/bash existed, but
 * the ubuntu-base tarball SHIPS /bin/bash — so the dashboard replaced the wizard right after
 * extraction (step 3) while steps 4–10 were still running. The ready marker is now mandatory.
 */
class UChatPathsInstallTest {

    private fun newRootfs(): File = Files.createTempDirectory("uchat-rootfs").toFile()

    private fun File.touch(relativePath: String): File {
        val f = File(this, relativePath)
        f.parentFile?.mkdirs()
        f.writeText("x")
        return f
    }

    private fun executableProot(): File {
        val f = File.createTempFile("proot", ".bin")
        f.writeText("!/bin/sh\n")
        assertTrue(f.setExecutable(true))
        return f
    }

    @Test
    fun `isInstalled requires marker AND bash AND executable proot`() {
        val root = newRootfs()
        val proot = executableProot()
        val marker = File(root, ".uchat-ready")

        // Nothing present yet.
        assertFalse(UChatPaths.isInstalled(root, proot, marker))

        // Marker only — bash missing.
        marker.writeText("1")
        assertFalse(UChatPaths.isInstalled(root, proot, marker))

        // Marker + bash — proot missing (null).
        root.touch("bin/bash")
        assertFalse(UChatPaths.isInstalled(root, null, marker))

        // All three — installed.
        assertTrue(UChatPaths.isInstalled(root, proot, marker))

        // Non-executable proot breaks the decision (Android W^X regression guard).
        val prootNoExec = File.createTempFile("proot", ".noexec")
        prootNoExec.writeText("x")
        assertFalse(UChatPaths.isInstalled(root, prootNoExec, marker))
    }

    @Test
    fun `migrateLegacyInstall promotes healthy legacy rootfs and writes the marker`() {
        val root = newRootfs()
        root.touch("bin/bash")
        root.touch("usr/bin/git") // installed by install-essentials.sh (step 5)
        val proot = executableProot()
        val marker = File(root, ".uchat-ready")

        assertTrue(UChatPaths.migrateLegacyInstall(root, proot, marker))
        assertTrue("migration must persist the ready marker", marker.isFile)
        // A migrated install counts as installed from now on.
        assertTrue(UChatPaths.isInstalled(root, proot, marker))
    }

    @Test
    fun `migrateLegacyInstall rejects half-installed rootfs without git`() {
        val root = newRootfs()
        root.touch("bin/bash")
        val proot = executableProot()
        val marker = File(root, ".uchat-ready")

        // bash ships with the tarball — without git (essentials) the install is broken.
        assertFalse(UChatPaths.migrateLegacyInstall(root, proot, marker))
        assertFalse("no marker may be written for a broken half-install", marker.isFile)
        assertFalse(UChatPaths.isInstalled(root, proot, marker))
    }

    @Test
    fun `migrateLegacyInstall fails without a usable proot`() {
        val root = newRootfs()
        root.touch("bin/bash")
        root.touch("usr/bin/git")
        val marker = File(root, ".uchat-ready")

        assertFalse(UChatPaths.migrateLegacyInstall(root, null, marker))
        assertFalse(marker.isFile)
    }

    @Test
    fun `migrateLegacyInstall is a no-op when the marker already exists`() {
        val root = newRootfs()
        root.touch("bin/bash")
        root.touch("usr/bin/git")
        val proot = executableProot()
        val marker = File(root, ".uchat-ready")
        marker.writeText("1700000000000")

        assertFalse(UChatPaths.migrateLegacyInstall(root, proot, marker))
        assertTrue(
            "existing marker content must be preserved",
            marker.readText() == "1700000000000",
        )
    }

    // ---- isBootstrapped: bash + proot WITHOUT the marker (installer steps 5-9 state) ----

    @Test
    fun `isBootstrapped is true with bash and executable proot and NO marker`() {
        val root = newRootfs()
        root.touch("bin/bash")
        assertTrue(
            "bootstrap must not require the step-10 ready marker",
            UChatPaths.isBootstrapped(root, executableProot()),
        )
    }

    @Test
    fun `isBootstrapped is false without bash even when proot is valid`() {
        val root = newRootfs()
        assertFalse(UChatPaths.isBootstrapped(root, executableProot()))
    }

    @Test
    fun `isBootstrapped is false with null or non-executable proot`() {
        val root = newRootfs()
        root.touch("bin/bash")
        assertFalse(UChatPaths.isBootstrapped(root, null))

        val inert = File.createTempFile("proot", ".bin")
        inert.writeText("x")
        assertFalse(UChatPaths.isBootstrapped(root, inert))
    }

    @Test
    fun `isBootstrapped ignores the marker in both directions`() {
        val root = newRootfs()
        root.touch("bin/bash")
        val proot = executableProot()
        val marker = File(root, ".uchat-ready")
        marker.writeText("1700000000000")
        assertTrue("marker must not be required", UChatPaths.isBootstrapped(root, proot))
        marker.delete()
        assertTrue("marker must not help either", UChatPaths.isBootstrapped(root, proot))
    }
}
