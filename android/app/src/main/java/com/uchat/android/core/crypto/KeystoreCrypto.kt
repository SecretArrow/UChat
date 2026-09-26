package com.uchat.android.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed AES/GCM encryption for secrets (spec #21).
 *
 * Secrets (API keys, tokens, SSH passphrases) are encrypted with a key that never leaves the
 * hardware-backed keystore and stored as base64 blobs. Plaintext secrets are never written to
 * preferences, the database, logs or exports.
 */
object KeystoreCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "uchat_master_key"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private fun obtainKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plaintext: CharArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(String(plaintext).toByteArray(Charsets.UTF_8))
        return iv + encrypted
    }

    fun decrypt(blob: ByteArray): CharArray {
        require(blob.size > GCM_IV_BYTES) { "Invalid encrypted blob" }
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val data = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8).toCharArray()
    }

    fun encryptToBase64(plaintext: CharArray): String =
        Base64.encodeToString(encrypt(plaintext), Base64.NO_WRAP)

    fun decryptFromBase64(base64: String): CharArray =
        decrypt(Base64.decode(base64, Base64.NO_WRAP))
}
