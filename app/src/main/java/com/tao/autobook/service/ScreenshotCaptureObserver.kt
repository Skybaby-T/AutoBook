package com.tao.autobook.service

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import java.io.File
import android.content.ContentValues
import android.content.Intent
import android.provider.Settings
import android.os.Environment
import android.os.Build
import com.tao.autobook.AutoBookApplication
import com.tao.autobook.notify.AutoBookNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 监听系统相册新增截图：用户截图后自动识别记账，并把该截图作为凭证。
 *
 * 触发条件：
 * 1. App 进程存活，Application.onCreate 已 registerContentObserver
 * 2. MediaStore 图片库有新增/更新（系统截图入库）
 * 3. 文件名或路径像截图（Screenshot/截图/DCIM/Screenshots 等）
 * 4. 2 分钟内新图，且体积 >= 8KB
 * 5. 同一图片 10 分钟内不重复处理
 */
class ScreenshotCaptureObserver(
    private val context: Context,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "ScreenshotObserver"
        private const val DEDUPE_WINDOW_MS = 10 * 60 * 1000L
        private const val PROCESS_DELAY_MS = 2500L
        private const val RETRY_DELAY_MS = 3500L
        private const val MIN_IMAGE_BYTES = 8 * 1024L
        private const val MAX_AGE_MS = 5 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val processed = ConcurrentHashMap<String, Long>()
    private var observer: ContentObserver? = null
    private var pendingJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.i(TAG, "onChange selfChange=$selfChange uri=$uri")
                scheduleScan(uri)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
                Log.i(TAG, "onChange flags=$flags uri=$uri")
                scheduleScan(uri)
            }
        }
        observer = obs
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                obs
            )
            // 部分机型截图会先写到 internal/files 再同步 external
            runCatching {
                context.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.getContentUri("external"),
                    true,
                    obs
                )
            }
            Log.i(TAG, "截图监听已启动")
        } catch (e: Exception) {
            Log.e(TAG, "注册截图监听失败", e)
            started = false
        }
    }

    fun stop() {
        observer?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
        }
        observer = null
        pendingJob?.cancel()
        pendingJob = null
        started = false
    }

    private fun scheduleScan(uri: Uri?) {
        // 不立刻 cancel 掉正在识别的任务；只合并短时间内的扫库
        pendingJob?.cancel()
        pendingJob = appScope.launch {
            // 等系统写完文件；小米/HyperOS 截图常先 pending 再可见
            delay(PROCESS_DELAY_MS)
            // size=0 时再多等一轮，避免读到 .pending 文件
            val first = runCatching { processLatestScreenshot(uri) }
                .onFailure { Log.e(TAG, "处理截图失败(第1次)", it) }
            val ok = first.isSuccess
            if (!ok) {
                delay(RETRY_DELAY_MS)
                runCatching { processLatestScreenshot(uri) }
                    .onFailure { Log.e(TAG, "处理截图失败(第2次)", it) }
            } else {
                // 即便成功返回，也可能是 size=0 跳过；再补扫一次
                delay(RETRY_DELAY_MS)
                runCatching { processLatestScreenshot(uri) }
            }
        }
    }

    private suspend fun processLatestScreenshot(changedUri: Uri?) {
        val image = findCandidateImage(changedUri)
        if (image == null) {
            Log.i(TAG, "未找到候选图片 uri=$changedUri")
            return
        }
        val key = image.id.toString()
        val now = System.currentTimeMillis()
        val last = processed[key]
        // 仅“成功处理过”才去重；失败不要占坑，否则会永远跳过
        if (last != null && last > 0 && now - last < DEDUPE_WINDOW_MS) {
            Log.i(TAG, "跳过重复: id=${image.id} name=${image.displayName}")
            return
        }

        Log.i(
            TAG,
            "候选图: id=${image.id} name=${image.displayName} path=${image.relativePath} size=${image.size} age=${now - image.dateAddedMs}ms"
        )

        if (!looksLikeScreenshot(image)) {
            Log.i(TAG, "跳过非截图: ${image.displayName} / ${image.relativePath}")
            return
        }
        // 只处理最近几分钟内的新图，避免扫历史
        if (now - image.dateAddedMs > MAX_AGE_MS) {
            Log.i(TAG, "跳过旧图: ${image.displayName} age=${now - image.dateAddedMs}ms")
            return
        }
        // 仍 pending / size=0 时不要硬读，留给下一轮重试
        if (image.size == 0L) {
            Log.i(TAG, "截图尚未写完(size=0)，跳过本轮: ${image.displayName}")
            return
        }
        if (image.size in 1 until MIN_IMAGE_BYTES) {
            Log.i(TAG, "跳过过小文件: ${image.displayName} size=${image.size}")
            return
        }

        val app = context.applicationContext as? AutoBookApplication
        if (app == null) {
            Log.e(TAG, "Application 为空，无法记账")
            return
        }
        Log.i(TAG, "检测到新截图，开始识别: ${image.displayName}")
        try {
            val result = app.repository.importScreenshot(
                uri = image.uri,
                displayName = image.displayName,
                capturedAtMs = image.dateAddedMs
            )
            processed[key] = now
            if (processed.size > 100) {
                processed.entries.removeIf { now - it.value > DEDUPE_WINDOW_MS }
            }
            val screenshot = app.getDao().getScreenshot(result)
            val txId = screenshot?.parsedTransactionId
            if (txId != null && txId > 0) {
                val tx = app.getDao().getTransaction(txId)
                if (tx != null) {
                    AutoBookNotifier.notifyTransaction(context, tx, app.getDao().getCategories())
                    app.repository.addLog(
                        "截图监听",
                        "自动记账成功",
                        "${tx.merchantName} ¥${tx.amountCents / 100.0} [${tx.paymentApp.label}]"
                    )
                    Log.i(TAG, "自动记账成功 #${tx.id} ${tx.merchantName} ${tx.paymentApp.label}")
                    // 如果用户开启了「自动删除截图」，删除相册原图
                    if (app.repository.isAutoDeleteScreenshotEnabled()) {
                        deleteAlbumScreenshot(image)
                    }
                }
            } else {
                app.repository.addLog("截图监听", "已识别待确认", image.displayName)
                Log.i(TAG, "已识别待确认: ${image.displayName}")
            }
        } catch (e: Exception) {
            // 失败不写 processed，允许后续 onChange / 重试
            Log.e(TAG, "识别失败，保留重试机会: ${image.displayName}", e)
            throw e
        }
    }

    private data class ImageInfo(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val relativePath: String,
        val dateAddedMs: Long,
        val size: Long
    )

    private fun findCandidateImage(changedUri: Uri?): ImageInfo? {
        // 1) 优先解析 onChange 给的具体 uri
        if (changedUri != null) {
            queryOne(changedUri)?.let { return it }
            // 有的机型给的是目录级 uri，再查最新
        }
        // 2) 回退：查最新图片
        return queryLatestImage()
    }

    private fun queryLatestImage(): ImageInfo? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    Log.i(TAG, "MediaStore 查询为空（可能是部分相册权限）")
                    return null
                }
                readImageInfo(cursor, null)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "MediaStore 无权限", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "queryLatestImage failed", e)
            null
        }
    }

    private fun queryOne(uri: Uri): ImageInfo? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        return try {
            // 若 uri 是数字 id 的 media item
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                readImageInfo(cursor, uri)
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryOne failed: $uri ${e.message}")
            null
        }
    }

    private fun readImageInfo(cursor: android.database.Cursor, uriHint: Uri?): ImageInfo? {
        fun col(name: String): Int = cursor.getColumnIndex(name)
        val idIdx = col(MediaStore.Images.Media._ID)
        val nameIdx = col(MediaStore.Images.Media.DISPLAY_NAME)
        val pathIdx = col(MediaStore.Images.Media.RELATIVE_PATH)
        val dateAddedIdx = col(MediaStore.Images.Media.DATE_ADDED)
        val dateModIdx = col(MediaStore.Images.Media.DATE_MODIFIED)
        val sizeIdx = col(MediaStore.Images.Media.SIZE)

        val id = when {
            idIdx >= 0 -> cursor.getLong(idIdx)
            uriHint != null -> runCatching { ContentUris.parseId(uriHint) }.getOrDefault(-1L)
            else -> -1L
        }
        if (id < 0) return null
        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
        val path = if (pathIdx >= 0) cursor.getString(pathIdx).orEmpty() else ""
        val dateAddedSec = if (dateAddedIdx >= 0) cursor.getLong(dateAddedIdx) else 0L
        val dateModSec = if (dateModIdx >= 0) cursor.getLong(dateModIdx) else 0L
        val dateMs = when {
            dateAddedSec > 0 -> dateAddedSec * 1000L
            dateModSec > 0 -> dateModSec * 1000L
            else -> System.currentTimeMillis()
        }
        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return ImageInfo(id, uri, name, path, dateMs, size)
    }

    private fun looksLikeScreenshot(info: ImageInfo): Boolean {
        val name = info.displayName.lowercase()
        val path = info.relativePath.lowercase()
        // 小米拼多多截图示例：Screenshot_2026-07-19-11-58-01-281_com.xunmeng.pinduoduo.jpg
        // 路径通常：DCIM/Screenshots/
        return name.contains("screenshot") ||
            name.contains("screen_shot") ||
            name.contains("screen-shot") ||
            name.startsWith("screenshot_") ||
            name.contains("截屏") ||
            name.contains("截图") ||
            path.contains("screenshot") ||
            path.contains("screenshots") ||
            path.contains("截屏") ||
            path.contains("截图") ||
            path.contains("dcim/screenshot")
    }
    /**
     * 删除相册原截图。
     * Android 10+ 对非本 App 写入的媒体默认不能 contentResolver.delete，
     * 依次尝试：MediaStore 删除、路径文件删除、createDeleteRequest 用户确认。
     */
    private fun deleteAlbumScreenshot(image: ImageInfo) {
        // 1) MediaStore 直接删
        try {
            val deleted = context.contentResolver.delete(image.uri, null, null)
            if (deleted > 0) {
                Log.i(TAG, "MediaStore 已删除原截图: ${image.displayName}")
                return
            }
            Log.w(TAG, "MediaStore delete=0: ${image.displayName}")
        } catch (e: SecurityException) {
            Log.w(TAG, "MediaStore 无删除权限: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore 删除失败: ${e.message}")
        }

        // 2) 路径文件删除（有 MANAGE_EXTERNAL_STORAGE 时可用）
        val pathCandidates = buildList {
            if (image.displayName.isNotBlank()) {
                add("/sdcard/DCIM/Screenshots/${image.displayName}")
                add("/storage/emulated/0/DCIM/Screenshots/${image.displayName}")
                if (image.relativePath.isNotBlank()) {
                    val rel = image.relativePath.trimEnd('/')
                    add("/sdcard/$rel/${image.displayName}")
                    add("/storage/emulated/0/$rel/${image.displayName}")
                }
            }
            // MediaStore DATA 列
            try {
                context.contentResolver.query(
                    image.uri,
                    arrayOf(MediaStore.Images.Media.DATA),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            } catch (_: Exception) {}
        }.distinct()

        for (path in pathCandidates) {
            try {
                val f = File(path)
                if (f.exists() && f.canWrite() && f.delete()) {
                    // 通知图库刷新
                    try {
                        context.contentResolver.delete(image.uri, null, null)
                    } catch (_: Exception) {}
                    Log.i(TAG, "文件路径已删除原截图: $path")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "路径删除失败 $path: ${e.message}")
            }
        }

        // 3) Android 11+ 用户确认删除（系统弹窗）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(image.uri))
                // 需要 Activity 启动，这里用 FLAG_ACTIVITY_NEW_TASK 尝试
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    // createDeleteRequest 返回 PendingIntent，直接 send
                }
                pi.send()
                Log.i(TAG, "已发起系统删除确认: ${image.displayName}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "createDeleteRequest 失败: ${e.message}")
            }
        }

        Log.e(TAG, "无法删除原截图: ${image.displayName}，可能缺所有文件访问权限")
    }


}
