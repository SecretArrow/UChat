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

    // ---- Bun/JSC hardening ("kok sering session exited", exit code 255) ----
    // opencode and Claude Code are Bun standalone binaries; Bun 1.3.x on arm64 dies with a
    // segfault/SIGTRAP panic under proot (JSC corruption family: opencode#34054/#33890,
    // oven-sh/bun#32632). The confirmed mitigation is running on the interpreter plus
    // future-proofing flags. If one of these keys drifts, sessions crash again in the field.

    @Test
    fun `Bun JIT tiers are disabled and LLInt is forced on`() {
        assertEquals("0", env["BUN_JSC_useJIT"])
        assertEquals("0", env["BUN_JSC_useFTLJIT"])
        assertEquals("0", env["BUN_JSC_useDFGJIT"])
        assertEquals("0", env["BUN_JSC_useBaselineJIT"])
        assertEquals("1", env["BUN_JSC_useLLInt"])
        // Wasm IPInt panic signature from opencode#34054 (suggested by the opencode team).
        assertEquals("0", env["BUN_JSC_useWasmIPInt"])
    }

    @Test
    fun `Bun epoll_pwait2 escape hatch is set for Bun 1_4 plus binaries`() {
        // No-op on Bun 1.3.x, vital on 1.4+ (oven-sh/bun#32489: Android seccomp blocks
        // epoll_pwait2 with no ENOSYS fallback in some builds).
        assertEquals("1", env["BUN_FEATURE_FLAG_DISABLE_EPOLL_PWAIT2"])
    }

    @Test
    fun `crash-report uploads and telemetry are off - metered mobile data`() {
        assertEquals("1", env["DO_NOT_TRACK"])
        assertEquals("1", env["DISABLE_TELEMETRY"])
    }

    @Test
    fun `claude auto-updater is disabled so the binary never swaps mid-session`() {
        assertEquals("1", env["DISABLE_AUTOUPDATER"])
    }
}
