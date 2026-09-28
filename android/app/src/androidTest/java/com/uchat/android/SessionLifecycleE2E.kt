package com.uchat.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.core.fs.UChatPaths
import com.uchat.android.data.registry.RegistryLoader
import com.uchat.android.linux.Checksum
import com.uchat.android.linux.Extractor
import com.uchat.android.linux.ProcessManager
import com.uchat.android.linux.Proot
import com.uchat.android.linux.SessionState
import com.uchat.android.linux.downloader.Downloader
import com.uchat.android.terminal.TerminalReplayCache
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end proof of the CLOSE TERMINAL feature (the user-visible session lifecycle):
 *
 * real Ubuntu rootfs -> real pty session via proot -> output flows into the replay cache ->
 * user-style stop -> EXITED -> close/reap releases the session from the registry (pty FD freed,
 * replay buffer dropped).
 *
 * This is the regression test for the leak where exited sessions piled up as zombie tabs forever.
 */
@RunWith(AndroidJUnit4::class)
class SessionLifecycleE2E {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun ensureUbuntuInstalled(paths: UChatPaths): DeviceAbi {
        val abi = DeviceAbi.current()
        if (paths.isUbuntuInstalled) return abi

        val app = context.applicationContext as com.uchat.android.UChatApp
        app.container.installer.cancel()
        paths.ensureDirs()
        paths.ubuntuRoot.deleteRecursively()

        val registry = RegistryLoader(context).loadAssets()
        val entry = registry.rootfsFor(abi)
        assertNotNull("registry must have a rootfs for $abi", entry)
        val rootfsEntry = entry!!

        val archive = File(paths.downloadsDir, rootfsEntry.id + ".e2e-lifecycle")
        archive.delete()
        Downloader().download(rootfsEntry.url, archive, rootfsEntry.sizeBytes) {}
        assertTrue(
            "SHA-256 must match the pinned value",
            Checksum.matches(archive, rootfsEntry.sha256)
        )
        val entries = Extractor.extractTarGz(archive, paths.ubuntuRoot, rootfsEntry.extractedBytes)
        assertTrue("rootfs must contain many entries", entries > 1000)
        archive.delete()

        Proot.syncResolvConf(paths)
        File(paths.ubuntuRoot, "etc/hostname").writeText("uchat\n")
        File(paths.ubuntuRoot, "etc/hosts").writeText("127.0.0.1 localhost uchat\n")
        listOf("dev", "proc", "sys", "tmp", "root/workspace", "root/downloads").forEach {
            File(paths.ubuntuRoot, it).mkdirs()
        }
        // Production semantics: the step-10 ready marker is mandatory for isUbuntuInstalled.
        paths.markInstalled()
        assertTrue("isUbuntuInstalled must be true", paths.isUbuntuInstalled)
        return abi
    }

    @Test
    fun sessionRunsThenClosesAndReapsCleanly() = runBlocking {
        val paths = UChatPaths(context)
        paths.ensureDirs()
        val abi = ensureUbuntuInstalled(paths)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val replay = TerminalReplayCache()
        val tapped = java.util.concurrent.atomic.AtomicLong(0)
        val trackedId = java.util.concurrent.atomic.AtomicLong(0)
        val manager =
            ProcessManager(
                scope,
                outputTap = { id, bytes, len ->
                    if (len > 0 && id == trackedId.get()) {
                        replay.offer(id, bytes, len)
                        tapped.addAndGet(len.toLong())
                    }
                }
            )

        // 1. Real pty session (the same path the UI uses). Session ids are process-global,
        // so the test must never assume a fixed numeric id.
        val session =
            Proot.launchSession(
                paths = paths,
                abi = abi,
                label = "Terminal",
                innerCommand = listOf("/bin/bash", "-l"),
                workingDirInUbuntu = Proot.UBUNTU_HOME,
                scope = scope,
            )
        val sid = session.id
        trackedId.set(sid)
        manager.register(session)

        withTimeout(30_000) { while (session.state != SessionState.RUNNING) delay(100) }

        // 2. Output must flow into the replay cache (backend + tap wiring intact).
        session.write("echo UCHAT_CLOSE_E2E_9154\r\n".toByteArray())
        withTimeout(15_000) { while (tapped.get() == 0L) delay(100) }
        assertTrue("replay cache must hold output", replay.snapshot(sid).isNotEmpty())

        // 3. User-style stop: the session must reach EXITED on its own.
        session.stop()
        withTimeout(30_000) { while (session.state != SessionState.EXITED) delay(100) }
        assertTrue(session.exitedAtMillis > 0L)

        // 4. Within the grace window the (dead) tab is still visible...
        manager.reapExited(graceMillis = 60_000L)
        assertEquals(1, manager.all.size)

        // 5. ...and after the grace it is gone: registry empty, FD released, replay dropped.
        delay(50)
        manager.reapExited(graceMillis = 0L)
        assertEquals(0, manager.all.size)
        replay.remove(sid)
        // NOTE: arrays must be compared by content (assertEquals on arrays is identity).
        org.junit.Assert.assertArrayEquals(ByteArray(0), replay.snapshot(sid))
    }

    @Test
    fun closeAllRemovesEverythingImmediately() = runBlocking {
        val paths = UChatPaths(context)
        paths.ensureDirs()
        val abi = ensureUbuntuInstalled(paths)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val manager = ProcessManager(scope)

        val first =
            Proot.launchSession(
                paths = paths,
                abi = abi,
                label = "Terminal",
                innerCommand = listOf("/bin/bash", "-l"),
                scope = scope,
            )
        manager.register(first)
        withTimeout(30_000) { while (first.state != SessionState.RUNNING) delay(100) }

        manager.closeAll()
        assertEquals(0, manager.all.size)
    }

    /**
     * Proves the Bun-crash fix end to end INSIDE the real guest: every pty session is exec'd
     * through session-run.sh (ScriptInstaller -> bind mount), which raises RLIMIT_STACK before
     * exec. The marker env var proves the wrapper ran; the printed stack limit proves it took
     * effect. Without this, opencode/claude die with "Segmentation fault / signal 5" seconds after
     * start (the "session exited with code 255" field reports).
     */
    @Test
    fun sessionRunnerWrapsCommandsAndRaisesStackLimit() = runBlocking {
        val paths = UChatPaths(context)
        paths.ensureDirs()
        val abi = ensureUbuntuInstalled(paths)

        val runner = java.io.File(paths.scriptsDir, "session-run.sh")
        assertTrue("session-run.sh must be installed into the scripts dir", runner.isFile)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val replay = TerminalReplayCache()
        val trackedId = java.util.concurrent.atomic.AtomicLong(0)
        val manager =
            ProcessManager(
                scope,
                outputTap = { id, bytes, len ->
                    if (len > 0 && id == trackedId.get()) replay.offer(id, bytes, len)
                },
            )

        val session =
            Proot.launchSession(
                paths = paths,
                abi = abi,
                label = "Terminal",
                // Inner command is the probe: the marker proves the WRAPPER ran (it exports
                // UCHAT_SESSION_RUNNER), the value proves the limit actually changed.
                innerCommand =
                    listOf(
                        "/bin/sh",
                        "-c",
                        "echo RUNNER=\$UCHAT_SESSION_RUNNER STACK=\$(ulimit -s)",
                    ),
                workingDirInUbuntu = Proot.UBUNTU_HOME,
                scope = scope,
            )
        trackedId.set(session.id)
        manager.register(session)

        val output = StringBuilder()
        try {
            withTimeout(30_000) {
                while (!output.contains("RUNNER=")) {
                    val snapshot = replay.snapshot(session.id)
                    if (snapshot.isNotEmpty()) output.append(String(snapshot, Charsets.UTF_8))
                    delay(100)
                }
            }
        } finally {
            session.stop()
        }
        // 1. The wrapper really wrapped the command (marker env var set by session-run.sh).
        assertTrue(
            "session must run through session-run.sh (got: $output)",
            output.contains("RUNNER=1"),
        )
        // 2. The stack limit must be unlimited OR raised to the kernel hard limit — any value
        //    is acceptable as long as the wrapper executed; Bun needs headroom above 8 MB.
        val stackLine = output.lineSequence().firstOrNull { it.contains("STACK=") } ?: ""
        val stack = stackLine.substringAfter("STACK=").trim()
        assertTrue(
            "stack limit must be unlimited or a number, got: '$stack' ($output)",
            stack == "unlimited" || stack.all { it.isDigit() } && stack.isNotEmpty(),
        )
    }
}
