package com.uchat.android

import com.uchat.android.core.arch.DeviceAbi
import com.uchat.android.data.registry.AssetRegistry
import com.uchat.android.data.registry.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryTest {

    private val assetJson =
        """
    {
      "schemaVersion": 1,
      "updatedAt": "2026-01-01",
      "rootfs": [
        {
          "id": "ubuntu-24.04-arm64",
          "name": "Ubuntu 24.04",
          "arch": "arm64",
          "androidAbi": "arm64-v8a",
          "url": "https://example.com/ubuntu.tar.gz",
          "sha256": "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2",
          "sizeBytes": 29936675,
          "extractedBytes": 86000000,
          "minFreeBytes": 1500000000
        }
      ],
      "proot": [
        {
          "id": "proot-arm64",
          "name": "proot",
          "arch": "arm64",
          "androidAbi": "arm64-v8a",
          "url": "https://example.com/proot",
          "sha256": "fa10b1a7818c2f5b1dcb5834450570c368c9ecf66d31521509621b95c4538a45",
          "sizeBytes": 1479432,
          "extractedBytes": 1479432,
          "minFreeBytes": 10485760
        }
      ]
    }
    """
            .trimIndent()

    @Test
    fun parsesAssetRegistry() {
        val reg = AssetRegistry.fromJson(assetJson)
        assertEquals(1, reg.rootfs.size)
        assertEquals(1, reg.proot.size)
        val abi = DeviceAbi.ARM64
        assertNotNull(reg.rootfsFor(abi))
        assertNotNull(reg.prootFor(abi))
        assertNull(reg.rootfsFor(DeviceAbi.X86))
    }

    @Test
    fun installRequirementAddsUp() {
        val reg = AssetRegistry.fromJson(assetJson)
        val req = reg.installRequirementFor(DeviceAbi.ARM64)
        assertNotNull(req)
        assertEquals(29936675L + 1479432L, req!!.downloadBytes)
        assertTrue(req.minFreeBytes >= req.downloadBytes + req.extractedBytes)
    }

    @Test
    fun parsesToolRegistry() {
        val tools =
            ToolRegistry.fromJson(
                """
            { "schemaVersion": 1, "tools": [
                { "id": "opencode", "name": "OpenCode", "category": "AI Coding",
                  "executable": "opencode", "verifyCommand": "opencode --version" }
            ] }
            """
                    .trimIndent(),
            )
        assertEquals(1, tools.tools.size)
        assertEquals("opencode", tools.tools[0].executable)
    }
}
