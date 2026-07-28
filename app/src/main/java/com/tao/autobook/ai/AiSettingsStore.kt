package com.tao.autobook.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tao.autobook.storage.CryptoStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiDataStore by preferencesDataStore(name = "ai_recognition_settings")

class AiSettingsStore(
    private val context: Context,
    private val cryptoStore: CryptoStore = CryptoStore()
) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val apiUrl = stringPreferencesKey("api_url")
        val model = stringPreferencesKey("model")
        val encryptedApiKey = stringPreferencesKey("encrypted_api_key")
        val timeoutSeconds = intPreferencesKey("timeout_seconds")
    }

    val settings: Flow<AiRecognitionSettings> = context.aiDataStore.data.map { prefs ->
            AiRecognitionSettings(
                enabled = prefs[Keys.enabled] ?: false,
                apiUrl = prefs[Keys.apiUrl].orEmpty(),
                model = prefs[Keys.model].orEmpty(),
                apiKeySet = !prefs[Keys.encryptedApiKey].isNullOrBlank(),
                // 截图识别默认 60s，避免传图经常 timeout 掉到本地 OCR 乱金额
                timeoutSeconds = (prefs[Keys.timeoutSeconds] ?: 60).coerceIn(8, 90)
            )
        }

    suspend fun save(settings: AiRecognitionSettings, plainApiKey: String?) {
        context.aiDataStore.edit { prefs ->
            prefs[Keys.enabled] = settings.enabled
            prefs[Keys.apiUrl] = settings.apiUrl.trim()
            prefs[Keys.model] = settings.model.trim()
            prefs[Keys.timeoutSeconds] = settings.timeoutSeconds.coerceIn(8, 90)
            if (plainApiKey != null) {
                if (plainApiKey.isBlank()) prefs.remove(Keys.encryptedApiKey)
                else prefs[Keys.encryptedApiKey] = cryptoStore.encryptToString(plainApiKey.trim())
            }
        }
    }

    suspend fun loadConfig(): AiRecognitionConfig {
        val prefs = context.aiDataStore.data.first()
        val encryptedKey = prefs[Keys.encryptedApiKey].orEmpty()
        val key = if (encryptedKey.isBlank()) "" else runCatching { cryptoStore.decryptFromString(encryptedKey) }.getOrDefault("")
        android.util.Log.d("AiSettings", "loadConfig: enabled=${prefs[Keys.enabled]}, url=${prefs[Keys.apiUrl]?.take(30)}, model=${prefs[Keys.model]}, keyLen=${key.length}")
        return AiRecognitionConfig(
                    enabled = prefs[Keys.enabled] ?: false,
                    apiUrl = prefs[Keys.apiUrl].orEmpty(),
                    model = prefs[Keys.model].orEmpty(),
                    apiKey = key,
                    timeoutSeconds = (prefs[Keys.timeoutSeconds] ?: 60).coerceIn(8, 90)
                )
    }
}

