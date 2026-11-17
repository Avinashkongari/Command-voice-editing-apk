package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM credential vault for AI Provider API keys.
 * API keys are encrypted at rest and never written to plaintext preferences, Room DB, or logs.
 */
class KeystoreCredentialManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveApiKey(profileId: String, rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        if (trimmed.isEmpty()) return
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))

        val payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        prefs.edit()
            .putString(keyPref(profileId), payload)
            .putString(maskPref(profileId), maskKey(trimmed))
            .apply()
    }

    fun getApiKey(profileId: String): String? {
        val payload = prefs.getString(keyPref(profileId), null) ?: return null
        val parts = payload.split(":")
        if (parts.size != 2) return null
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun hasApiKey(profileId: String): Boolean = prefs.contains(keyPref(profileId))

    fun getMaskedPreview(profileId: String): String =
        prefs.getString(maskPref(profileId), "") ?: ""

    fun deleteApiKey(profileId: String) {
        prefs.edit()
            .remove(keyPref(profileId))
            .remove(maskPref(profileId))
            .apply()
    }

    private fun maskKey(raw: String): String {
        return if (raw.length <= 8) {
            "••••••••"
        } else {
            raw.take(4) + "••••••••" + raw.takeLast(4)
        }
    }

    private fun keyPref(profileId: String) = "enc_key_$profileId"
    private fun maskPref(profileId: String) = "mask_key_$profileId"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "voxcut_ai_api_key_master_aes256"
        private const val PREFS_NAME = "voxcut_secure_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
