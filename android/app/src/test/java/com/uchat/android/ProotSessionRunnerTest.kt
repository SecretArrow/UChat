package com.uchat.android

import com.uchat.android.linux.Proot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the session-runner wrapping (the RLIMIT_STACK raise for Bun-based tools).
 *
 * Constraint that makes this pure: the restore-after-reboot path persists `command.joinToString("
 * ")` and re-parses with `split(" ")`, so no argv element may contain a space. The wrapper prepends
 * exactly ONE argv element — the GUEST bind path of the script.
 *
 * REGRESSION (v1.9.0): the wrapper was exec'd through its HOST path
 * (`/data/user/0/.../files/scripts/session-run.sh`). proot resolves the initial exec path inside
 * the guest root where no /data exists, so every session died instantly: proot error:
 * '/data/user/0/...' not found (root = ..., $PATH=(null)) → exit code 1 (field report with
 * screenshot, "perbaiki segalanya"). These tests pin the guest path forever.
 */
class ProotSessionRunnerTest {

    private fun tempRunner(): File {
        val dir = java.nio.file.Files.createTempDirectory("uchat-runner").toFile()
        return File(dir, "session-run.sh").apply { writeText("#!/usr/bin/env bash\nexec \"$@\"\n") }
    }

    @Test
    fun `wraps the command with the GUEST bind path when the script exists`() {
        val runner = tempRunner()
        val wrapped = Proot.wrapWithSessionRunner(listOf("opencode"), runnerFile = runner)
        assertEquals(listOf(Proot.SESSION_RUNNER, "opencode"), wrapped)
        runner.parentFile?.deleteRecursively()
    }

    @Test
    fun `host script path never leaks into the guest argv`() {
        // proot can only execute paths that exist INSIDE the guest; the host path of the
        // bind-mounted script must stay a host-side existence probe, never an argv element.
        val runner = tempRunner()
        val command = listOf("/bin/bash", "-l")
        val wrapped = Proot.wrapWithSessionRunner(command, runnerFile = runner)
        assertTrue(wrapped.none { it.startsWith(runner.parentFile!!.absolutePath) })
        assertTrue(wrapped.none { it.contains("/data/") })
        assertEquals(Proot.SESSION_RUNNER, wrapped.first())
        runner.parentFile?.deleteRecursively()
    }

    @Test
    fun `keeps every argument in order after the runner`() {
        val runner = tempRunner()
        val command = listOf("/bin/bash", "-l")
        assertEquals(
            listOf(Proot.SESSION_RUNNER, "/bin/bash", "-l"),
            Proot.wrapWithSessionRunner(command, runnerFile = runner),
        )
        runner.parentFile?.deleteRecursively()
    }

    @Test
    fun `falls back to the bare command when the script is missing`() {
        // E2E / first-frame-after-update resilience: a missing wrapper must degrade to the
        // old behaviour, never to a failed launch.
        val missing = File("/nonexistent/uchat/session-run.sh")
        val command = listOf("opencode")
        assertEquals(command, Proot.wrapWithSessionRunner(command, runnerFile = missing))
        assertEquals(command, Proot.wrapWithSessionRunner(command, runnerFile = null))
    }

    @Test
    fun `empty command is returned untouched`() {
        assertEquals(
            emptyList<String>(),
            Proot.wrapWithSessionRunner(emptyList(), runnerFile = null)
        )
    }

    @Test
    fun `runner path is the bind-mounted scripts dir and contains no spaces`() {
        // The joinToString/split(" ") round-trip breaks on spaces — pin the path shape.
        assertEquals("/root/.uchat-scripts/session-run.sh", Proot.SESSION_RUNNER)
        assertFalse(Proot.SESSION_RUNNER.contains(" "))
        assertFalse(Proot.SESSION_RUNNER.contains("/data/"))
    }
}
