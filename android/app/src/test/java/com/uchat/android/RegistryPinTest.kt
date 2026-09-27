package com.uchat.android

import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.data.registry.AssetRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the REAL download metadata shipped in assets/registry/asset-registry.json.
 *
 * Regression guard: the arm64 rootfs SHA-256 was once a placeholder that never matched the actual
 * Canonical artifact, so every install on real phones died at the checksum step. These values are
 * verified against cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/SHA256SUMS and the
 * proot-me v5.3.0 release assets; changing them requires re-verification upstream.
 */
class RegistryPinTest {

    private val registryFile = File("src/main/assets/registry/asset-registry.json")

    private val registry: AssetRegistry = AssetRegistry.fromJson(registryFile.readText())

    @Test
    fun registryAssetFileExistsAndParses() {
        assertTrue(
            "asset-registry.json missing at ${registryFile.absolutePath}",
            registryFile.isFile,
        )
        assertEquals(3, registry.rootfs.size)
        assertEquals(3, registry.proot.size)
    }

    @Test
    fun everySha256IsWellFormed() {
        (registry.rootfs + registry.proot).forEach { entry ->
            assertEquals("sha256 length of ${entry.id}", 64, entry.sha256.length)
            assertTrue(
                "sha256 of ${entry.id} must be hex",
                entry.sha256.all { it.isDigit() || it in 'a'..'f' },
            )
        }
    }

    @Test
    fun everyUrlIsHttps() {
        (registry.rootfs + registry.proot).forEach { entry ->
            assertTrue("url of ${entry.id} must be https", entry.url.startsWith("https://"))
        }
    }

    @Test
    fun arm64RootfsSha256MatchesCanonicalArtifact() {
        val entry = registry.rootfsFor(DeviceAbi.ARM64)
        assertNotNull(entry)
        assertEquals(
            "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2",
            entry!!.sha256,
        )
        assertEquals(29936675L, entry.sizeBytes)
    }

    @Test
    fun armhfRootfsSha256MatchesCanonicalArtifact() {
        val entry = registry.rootfsFor(DeviceAbi.ARM32)
        assertNotNull(entry)
        assertEquals(
            "4fcee4d278f1c5232e085a021a85e4c6cef3853557a88d98ff380b5e5d5841bb",
            entry!!.sha256,
        )
        assertEquals(27120839L, entry.sizeBytes)
    }

    @Test
    fun amd64RootfsSha256MatchesCanonicalArtifact() {
        val entry = registry.rootfsFor(DeviceAbi.X86_64)
        assertNotNull(entry)
        assertEquals(
            "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86",
            entry!!.sha256,
        )
        assertEquals(30028293L, entry.sizeBytes)
    }

    @Test
    fun prootSha256MatchesReleaseArtifacts() {
        assertEquals(
            "fa10b1a7818c2f5b1dcb5834450570c368c9ecf66d31521509621b95c4538a45",
            registry.prootFor(DeviceAbi.ARM64)!!.sha256,
        )
        assertEquals(
            "bf186a37c7a19621e5bf3cfdf6bce54bfa2e220f91eb7196318e699ac174cc69",
            registry.prootFor(DeviceAbi.ARM32)!!.sha256,
        )
        assertEquals(
            "d1eb20cb201e6df08d707023efb000623ff7c10d6574839d7bb42d0adba6b4da",
            registry.prootFor(DeviceAbi.X86_64)!!.sha256,
        )
    }

    @Test
    fun requirementWithBundledProotShrinksToRootfsOnly() {
        val abi = DeviceAbi.ARM64
        val full = registry.installRequirementFor(abi, prootBundled = false)!!
        val bundled = registry.installRequirementFor(abi, prootBundled = true)!!
        assertEquals(full.downloadBytes - registry.prootFor(abi)!!.sizeBytes, bundled.downloadBytes)
        assertEquals(
            full.extractedBytes - registry.prootFor(abi)!!.extractedBytes,
            bundled.extractedBytes,
        )
        assertTrue(bundled.minFreeBytes >= bundled.downloadBytes + bundled.extractedBytes)
    }

    @Test
    fun x86RemainsUnsupportedBecauseNobleHasNoI386Rootfs() {
        assertEquals(null, registry.rootfsFor(DeviceAbi.X86))
    }
}
