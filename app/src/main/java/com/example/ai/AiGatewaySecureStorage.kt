package com.example.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local storage for the OpenAI-compatible AI gateway configuration.
 * API keys are encrypted at rest with an Android Keystore AES-256-GCM key and stored in
 * [PREFS_NAME], which is explicitly excluded from cloud backups and device transfers.
 */
class AiGatewaySecureStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): AiGatewayConfig {
        val baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty()
        val modelId = prefs.getString(KEY_MODEL_ID, "").orEmpty()
        val encryptedApiKey = prefs.getString(KEY_API_KEY_ENC, null)
        val apiKey = if (!encryptedApiKey.isNullOrBlank()) {
            decryptApiKey(encryptedApiKey)
        } else {
            ""
        }
        return AiGatewayConfig(
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId
        )
    }

    fun saveConfig(config: AiGatewayConfig) {
        val cleanBaseUrl = config.baseUrl.trim()
        val cleanModelId = config.modelId.trim()
        val cleanApiKey = config.apiKey.trim()

        val editor = prefs.edit()
            .putString(KEY_BASE_URL, cleanBaseUrl)
            .putString(KEY_MODEL_ID, cleanModelId)

        if (cleanApiKey.isEmpty()) {
            editor.remove(KEY_API_KEY_ENC)
        } else {
            val encrypted = encryptApiKey(cleanApiKey)
            if (encrypted != null) {
                editor.putString(KEY_API_KEY_ENC, encrypted)
            }
        }
        editor.apply()
    }

    fun loadFetchedModels(): List<String> {
        val raw = prefs.getString(KEY_FETCHED_MODELS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveFetchedModels(models: List<String>) {
        val arr = JSONArray()
        models.take(MAX_STORED_MODELS).forEach { arr.put(it) }
        prefs.edit()
            .putString(KEY_FETCHED_MODELS, arr.toString())
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

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
            keyGenerator.generateKey()
        }.getOrNull()
    }

    private fun encryptApiKey(plainText: String): String? {
        val key = getOrCreateSecretKey() ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decryptApiKey(encoded: String): String {
        val key = getOrCreateSecretKey() ?: return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < 2) return@runCatching ""
            val ivLength = combined[0].toInt() and 0xFF
            if (ivLength <= 0 || combined.size <= 1 + ivLength) return@runCatching ""
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    companion object {
        const val PREFS_NAME = "ai_gateway_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "gps_hud_ai_gateway_aes_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val MAX_STORED_MODELS = 200

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL_ID = "model_id"
        private const val KEY_API_KEY_ENC = "api_key_enc"
        private const val KEY_FETCHED_MODELS = "fetched_models"
    }
}
