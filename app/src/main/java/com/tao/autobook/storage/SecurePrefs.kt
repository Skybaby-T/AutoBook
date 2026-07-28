package com.tao.autobook.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 安全存储同步凭据。使用 EncryptedSharedPreferences (AES-256) 替代明文 SharedPreferences。
 */
class SecurePrefs(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "autobook_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getSyncCredentials(): Pair<String, String> =
        (prefs.getString("sync_user", "") ?: "") to
                (prefs.getString("sync_pass", "") ?: "")

    fun saveSyncCredentials(username: String, password: String) {
        prefs.edit()
            .putString("sync_user", username)
            .putString("sync_pass", password)
            .apply()
    }
}
