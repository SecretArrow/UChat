package com.uchat.android

import com.uchat.android.data.registry.ToolRegistry
import com.uchat.android.linux.install.SCRIPTS
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrity net for the AI-tools install pipeline.
 *
 * The production incident: tool-registry.json referenced install-node.sh, but that script was never
 * added to the bundled assets — every "Install Node.js" tap ran `bash <missing file>` and failed
 * invisibly. These tests make that drift impossible:
 * 1. every registry installScript must exist in APK assets (ubuntu/)
 * 2. every script shipped in assets must be copied by ScriptInstaller
 * 3. the repo-root ubuntu/ sources must be byte-identical to the bundled assets (no drift)
 * 4. install-essentials must include gnupg (NodeSource/apt-key path) and install-node must exist
 */
class RegistryScriptsSyncTest {

    private val moduleDir: File = locateModuleDir()
    private val assetsUbuntu = File(moduleDir, "src/main/assets/ubuntu")
    private val repoUbuntu = locateRepoUbuntu()

    private fun locateModuleDir(): File {
        val cwd = File(System.getProperty("user.dir")!!)
        if (File(cwd, "src/main/assets/ubuntu").isDirectory) return cwd
        val nested = File(cwd, "android/app")
        if (File(nested, "src/main/assets/ubuntu").isDirectory) return nested
        // Gradle sometimes runs from the project root — walk up looking for the module dir.
        var dir: File? = cwd
        while (dir != null) {
            if (File(dir, "android/app/src/main/assets/ubuntu").isDirectory) {
                return File(dir, "android/app")
            }
            dir = dir.parentFile
        }
        error("cannot locate android/app module dir from ${cwd.absolutePath}")
    }

    private fun locateRepoUbuntu(): File? {
        var dir: File? = moduleDir
        while (dir != null) {
            val candidate = File(dir, "ubuntu")
            if (candidate.isDirectory && File(candidate, "install-node.sh").isFile) return candidate
            dir = dir.parentFile
        }
        return null // repo sources not present (artifact-only checkout) — sync test skipped
    }

    private fun loadRegistry(): ToolRegistry =
        Json { ignoreUnknownKeys = true }
            .decodeFromString(
                ToolRegistry.serializer(),
                File(moduleDir, "src/main/assets/registry/tool-registry.json").readText(),
            )

    @Test
    fun `every registry installScript exists in bundled assets`() {
        val registry = loadRegistry()
        assertTrue("registry must not be empty", registry.tools.isNotEmpty())
        registry.tools.forEach { tool ->
            val script = tool.installScript ?: return@forEach
            assertTrue(
                "tool '${tool.id}' references '$script' which is MISSING from assets/ubuntu — " +
                    "its Install button fails silently for users",
                File(assetsUbuntu, script).isFile,
            )
        }
    }

    @Test
    fun `every bundled script is copied by ScriptInstaller`() {
        assetsUbuntu
            .listFiles { f -> f.isFile && f.name.endsWith(".sh") }!!
            .forEach { f ->
                assertTrue(
                    "'${f.name}' is bundled in assets but not listed in ScriptInstaller — it will " +
                        "never reach /root/.uchat-scripts",
                    f.name in SCRIPTS,
                )
            }
    }

    @Test
    fun `repo ubuntu sources are byte-identical to bundled assets`() {
        val repo = repoUbuntu ?: return // sources unavailable in this checkout
        val repoScripts =
            repo.listFiles { f -> f.isFile && f.name.endsWith(".sh") }!!.map { it.name }
        val assetScripts =
            assetsUbuntu.listFiles { f -> f.isFile && f.name.endsWith(".sh") }!!.map { it.name }
        assertEquals(
            "script sets drifted between repo and assets",
            repoScripts.sorted(),
            assetScripts.sorted()
        )
        repoScripts.forEach { name ->
            val repoBytes = File(repo, name).readBytes()
            val assetBytes = File(assetsUbuntu, name).readBytes()
            assertTrue(
                "'$name' differs between repo ubuntu/ and assets/ubuntu — re-sync the assets",
                repoBytes.contentEquals(assetBytes),
            )
        }
    }

    @Test
    fun `node installer exists and essentials include gnupg`() {
        val nodeScript = File(assetsUbuntu, "install-node.sh").readText()
        assertTrue("node installer must handle arm64", nodeScript.contains("arm64"))
        assertTrue("node installer must handle x86_64", nodeScript.contains("x86_64"))
        assertTrue("node installer must pin a version", nodeScript.contains("NODE_VERSION="))

        val essentials = File(assetsUbuntu, "install-essentials.sh").readText()
        assertTrue(
            "essentials must install gnupg (needed by apt repo setups)",
            essentials.contains("gnupg"),
        )
    }
}
