package com.uchat.android

import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.linux.Proot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the proot environment contract.
 *
 * PROOT_NO_SECCOMP=1 was shipped here from v1.0.0 (proot-me era) and silently broke every dpkg
 * rename with "Function not implemented" on x86_64 emulators and modern Android (termux/proot#390):
 * with proot's seccomp filtering disabled, its SIGSYS rewrite path for old syscalls (rename ->
 * renameat) never runs, so apt/dpkg can never commit the package database. The InstallResilienceE2E
 * covers the real-device path; this test keeps the environment itself honest on the JVM.
 */
class ProotEnvironmentTest {

    private val env: Map<String, String> =
        Proot.environment(paths = null, abi = DeviceAbi.ARM64).associate {
            val idx = it.indexOf('=')
            it.substring(0, idx) to it.substring(idx + 1)
        }

    @Test
    fun `PROOT_NO_SECCOMP must never be set - it breaks dpkg rename with ENOSYS`() {
        assertFalse(
            "PROOT_NO_SECCOMP must not be set (termux/proot#390: rename -> ENOSYS under l2s)",
            env.containsKey("PROOT_NO_SECCOMP"),
        )
    }

    @Test
    fun `essential environment variables are always present`() {
        assertEquals("C.UTF-8", env["LANG"])
        assertEquals("/root", env["HOME"])
        assertEquals("xterm-256color", env["TERM"])
        assertTrue(env["PATH"]!!.contains("/usr/bin"))
    }

    @Test
    fun `TMPDIR points somewhere writable on Android`() {
        // No /tmp on Android: the env must either pin PROOT_TMP_DIR to a real path or fall back,
        // but TMPDIR for the GUEST always points at the in-rootfs /tmp.
        assertEquals("/tmp", env["TMPDIR"])
    }
}
