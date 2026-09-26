package com.uchat.android.data.repo

import android.content.Context
import com.uchat.android.core.crypto.KeystoreCrypto
import com.uchat.android.core.log.Logs

/**
 * Encrypted secret storage (spec #21).
 *
 * Secrets live in a dedicated prefs file as Keystore-encrypted base64 blobs. Nothing here is ever
 * logged, backed up (see backup_rules.xml) or exported.
 */
class SecretsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("uchat_secrets", Context.MODE_PRIVATE)

    fun put(key: String, value: CharArray) {
        prefs.edit().putString(key.lowercase(), KeystoreCrypto.encryptToBase64(value)).apply()
        Logs.app("secret saved: $key (value redacted)")
    }

    fun get(key: String): CharArray? {
        val blob = prefs.getString(key.lowercase(), null) ?: return null
        return try {
            KeystoreCrypto.decryptFromBase64(blob)
        } catch (e: Exception) {
            Logs.app("secret decryption failed for $key")
            null
        }
    }

    fun delete(key: String) {
        prefs.edit().remove(key.lowercase()).apply()
    }

    fun keys(): List<String> = prefs.all.keys.toList()

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val OPENCODE_API_KEY = "opencode.api_key"
        const val CLAUDE_API_KEY = "claude.api_key"
        const val GITHUB_TOKEN = "git.github_token"
    }
}
