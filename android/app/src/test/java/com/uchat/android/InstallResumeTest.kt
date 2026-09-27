package com.uchat.android

import com.uchat.android.linux.install.InstallResume
import com.uchat.android.linux.install.InstallStep
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resume-plan tests: retries must NEVER repeat completed installer work. Regression context —
 * v1.5.0 re-downloaded the whole rootfs on every retry, even when the failure was at step 5+
 * and the rootfs was already extracted, burning the user's metered data plan.
 */
class InstallResumeTest {

    private val fresh = InstallResume.Decision(false, false, InstallStep.DOWNLOAD_ROOTFS)

    private fun decide(
        step: InstallStep?,
        archive: Boolean = false,
        bash: Boolean = false,
    ) = InstallResume.decide(step, archiveExists = archive, rootfsHasBash = bash)

    @Test
    fun `no previous failure always starts a fresh pipeline`() {
        assertEquals(fresh, decide(null))
        assertEquals(fresh, decide(null, archive = true))
        assertEquals(fresh, decide(null, bash = true))
    }

    @Test
    fun `failure at apt steps or later skips the archive pipeline when rootfs exists`() {
        listOf(
            InstallStep.INITIALIZE_UBUNTU,
            InstallStep.INSTALL_ESSENTIALS,
            InstallStep.INSTALL_RUNTIMES,
            InstallStep.INSTALL_OPENCODE,
            InstallStep.INSTALL_CLAUDE,
            InstallStep.HEALTH_CHECK,
        ).forEach { step ->
            val d = decide(step, archive = false, bash = true)
            assertTrue("step ${step.id} should skip the archive", d.skipArchive)
            assertFalse(d.reuseArchive)
            assertEquals(step, d.fromStep)
        }
    }

    @Test
    fun `failure at apt steps with broken rootfs falls back to full reinstall`() {
        val d = decide(InstallStep.INSTALL_ESSENTIALS, archive = false, bash = false)
        assertFalse(d.skipArchive)
        assertFalse(d.reuseArchive)
        assertEquals(InstallStep.DOWNLOAD_ROOTFS, d.fromStep)
    }

    @Test
    fun `failure at verify or extract reuses an intact archive`() {
        val verify = decide(InstallStep.VERIFY_CHECKSUM, archive = true, bash = false)
        assertFalse(verify.skipArchive)
        assertTrue(verify.reuseArchive)
        assertEquals(InstallStep.VERIFY_CHECKSUM, verify.fromStep)

        val extract = decide(InstallStep.EXTRACT_ROOTFS, archive = true, bash = false)
        assertFalse(extract.skipArchive)
        assertTrue(extract.reuseArchive)
    }

    @Test
    fun `failure at verify or extract without archive re-downloads`() {
        val d = decide(InstallStep.EXTRACT_ROOTFS, archive = false, bash = false)
        assertFalse(d.skipArchive)
        assertFalse(d.reuseArchive)
        assertEquals(InstallStep.DOWNLOAD_ROOTFS, d.fromStep)
    }

    @Test
    fun `failure at download always re-downloads`() {
        val d = decide(InstallStep.DOWNLOAD_ROOTFS, archive = true, bash = false)
        // Step 1 never skips or reuses — the Downloader itself resumes partial bytes.
        assertFalse(d.skipArchive)
        assertFalse(d.reuseArchive)
        assertEquals(InstallStep.DOWNLOAD_ROOTFS, d.fromStep)
    }

    @Test
    fun `skipArchive takes precedence over reuseArchive`() {
        val d = decide(InstallStep.HEALTH_CHECK, archive = true, bash = true)
        assertTrue(d.skipArchive)
        assertFalse(d.reuseArchive)
    }
}
