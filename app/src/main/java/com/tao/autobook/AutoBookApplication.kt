package com.tao.autobook

import android.app.Application
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

    fun getDao() = database.dao()
    fun getChatDao() = database.chatDao()

    override fun onCreate() {
        super.onCreate()
        AutoBookNotifier.createChannel(this)
        appScope.launch {
            repository.initialize()
            // 启动时上报使用统计
            repository.sendHeartbeat()
            // 启动时同步远程规则库
            repository.syncRemoteRules()
            // 每6小时自动同步
            while (true) {
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
                repository.syncRemoteRules()
            }
        }
    }
}
