package com.tao.autobook.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ScreenshotStorage(private val context: Context, private val cryptoStore: CryptoStore = CryptoStore()) {
    private val dir: File get() = File(context.filesDir, "screenshots").also { it.mkdirs() }

    suspend fun saveEncryptedFromUri(uri: Uri): StoredScreenshot = withContext(Dispatchers.IO) {
        val bytes = openUriBytesWithRetry(uri)
            ?: error("无法读取截图")
        saveEncrypted(bytes)
    }

    /**
     * 小米/HyperOS 截图刚入库时 MediaStore 项常为 pending/trashed，
     * 只有 com.miui.screenshot 能读。这里优先等 pending 结束，再回退到真实文件路径。
     */
    private fun openUriBytesWithRetry(uri: Uri, maxAttempts: Int = 8): ByteArray? {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                context.contentResolver.openInputStream(uri)?.use { return it.readBytes() }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w(
                    "ScreenshotStorage",
                    "openUri attempt=${attempt + 1}/$maxAttempts failed: ${e.message}"
                )
            }
            // 回退：从 MediaStore 拿 _data / relative_path 直接读文件
            tryReadAbsolutePath(uri)?.let { path ->
                val file = java.io.File(path)
                if (file.exists() && file.canRead() && file.length() > 0) {
                    android.util.Log.i("ScreenshotStorage", "fallback file read: $path")
                    return file.readBytes()
                }
            }
            try {
                Thread.sleep(if (attempt < 3) 800L else 1500L)
            } catch (_: InterruptedException) {
            }
        }
        android.util.Log.e("ScreenshotStorage", "openUriBytesWithRetry exhausted: $uri", lastError)
        return null
    }

    private fun tryReadAbsolutePath(uri: Uri): String? {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.IS_PENDING
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val pendingIdx = c.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                if (pendingIdx >= 0) {
                    val pending = c.getInt(pendingIdx)
                    if (pending == 1) {
                        android.util.Log.i("ScreenshotStorage", "MediaStore item still pending")
                    }
                }
                val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                if (dataIdx >= 0) {
                    val data = c.getString(dataIdx)
                    if (!data.isNullOrBlank()) return data
                }
                val relIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val nameIdx = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val rel = if (relIdx >= 0) c.getString(relIdx).orEmpty() else ""
                val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
                if (rel.isNotBlank() && name.isNotBlank()) {
                    // 常见：DCIM/Screenshots/xxx.jpg
                    val candidates = listOf(
                        "/sdcard/$rel$name",
                        "/storage/emulated/0/$rel$name",
                        "/sdcard/DCIM/Screenshots/$name",
                        "/storage/emulated/0/DCIM/Screenshots/$name"
                    )
                    candidates.firstOrNull { java.io.File(it).exists() }
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.w("ScreenshotStorage", "tryReadAbsolutePath failed: ${e.message}")
            null
        }
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
