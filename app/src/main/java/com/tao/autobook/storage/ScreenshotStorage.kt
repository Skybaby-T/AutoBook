package com.tao.autobook.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ScreenshotStorage(private val context: Context, private val cryptoStore: CryptoStore = CryptoStore()) {
    private val dir: File get() = File(context.filesDir, "screenshots").also { it.mkdirs() }

    suspend fun saveEncryptedFromUri(uri: Uri): StoredScreenshot = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取截图")
        saveEncrypted(bytes)
    }

    suspend fun saveEncryptedBitmap(bitmap: Bitmap): StoredScreenshot = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        saveEncrypted(output.toByteArray())
    }

    suspend fun loadBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val plain = cryptoStore.decryptBytes(file.readBytes())
        BitmapFactory.decodeByteArray(plain, 0, plain.size)
    }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    fun delete(path: String) {
        File(path).delete()
    }

    private fun saveEncrypted(bytes: ByteArray): StoredScreenshot {
        val file = File(dir, "shot-${System.currentTimeMillis()}.bin")
        file.writeBytes(cryptoStore.encryptBytes(bytes))
        return StoredScreenshot(file.absolutePath, bytes.size.toLong())
    }
}

data class StoredScreenshot(val encryptedPath: String, val originalBytes: Long)
