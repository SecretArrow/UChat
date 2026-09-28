package com.uchat.android

import com.uchat.android.linux.Proot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pins the session-runner wrapping (the RLIMIT_STACK raise for Bun-based tools).
 *
 * Constraint that makes this pure: the restore-after-reboot path persists `command.joinToString("
 * ")` and re-parses with `split(" ")`, so no argv element may contain a space. The wrapper prepends
 * exactly ONE argv element — the bind-mounted script path.
 */
class ProotSessionRunnerTest {

    @Test
    fun `wraps the command with the runner path when the script exists`() {
        val dir = java.nio.file.Files.createTempDirectory("uchat-runner").toFile()
        val runner =
            File(dir, "session-run.sh").apply { writeText("#!/usr/bin/env bash\nexec \"$@\"\n") }
        val wrapped = Proot.wrapWithSessionRunner(listOf("opencode"), runnerFile = runner)
        assertEquals(listOf(runner.absolutePath, "opencode"), wrapped)
        dir.deleteRecursively()
    }

    @Test
    fun `keeps every argument in order after the runner`() {
        val dir = java.nio.file.Files.createTempDirectory("uchat-runner").toFile()
        val runner = File(dir, "session-run.sh").apply { writeText("#!/usr/bin/env bash\n") }
        val command = listOf("/bin/bash", "-l")
        assertEquals(
            listOf(runner.absolutePath, "/bin/bash", "-l"),
            Proot.wrapWithSessionRunner(command, runnerFile = runner),
        )
        dir.deleteRecursively()
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
    }
}
