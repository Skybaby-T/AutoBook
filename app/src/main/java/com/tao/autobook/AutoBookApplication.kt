package com.tao.autobook

import android.app.Application
import android.content.Context
import com.tao.autobook.data.AutoBookDatabase
import com.tao.autobook.data.AutoBookRepository
import com.tao.autobook.notify.AutoBookNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AutoBookApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database by lazy { AutoBookDatabase.get(this) }
    val repository by lazy { AutoBookRepository(this, database.dao(), database.chatDao()) }
    private val screenshotObserver by lazy { com.tao.autobook.service.ScreenshotCaptureObserver(this, appScope) }

    fun getDao() = database.dao()
    fun getChatDao() = database.chatDao()

    override fun onCreate() {
        super.onCreate()
        AutoBookNotifier.createChannel(this)
        // 启动截图监听：用户截图后自动识别并作为凭证记账
        screenshotObserver.start()
        appScope.launch {
            repository.initialize()
            // 启动时同步远程规则库
            repository.syncRemoteRules()
            // 每6小时同步一次远程规则
            while (true) {
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
                try { repository.syncRemoteRules() } catch (_: Exception) {}
            }
        }
        loadOptionalExtension()
    }

    /**
     * 加载可选的本地扩展模块（若存在）。
     * 该模块不随开源仓库分发；类缺失时静默跳过，不影响任何主功能。
     */
    private fun loadOptionalExtension() {
        try {
            val clazz = Class.forName("com.tao.autobook.ext.AppExtension")
            val instance = clazz.getDeclaredField("INSTANCE").get(null)
            clazz.getDeclaredMethod("start", Context::class.java, CoroutineScope::class.java)
                .invoke(instance, this, appScope)
        } catch (_: Throwable) {
            // 模块不存在或加载失败：属正常情况，忽略
        }
    }
}
