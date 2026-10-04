package com.tao.autobook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.tao.autobook.MainActivity

/**
 * 前台保活服务。
 *
 * 存活策略：只依赖前台通知 + START_STICKY，由系统在内存回收后自动重建。
 * 曾用 1 分钟 RTC_WAKEUP 闹钟"保活"，实测是空转耗电（开机 9 天唤醒 5163 次，
 * 全机 Top1，而 onReceive 只重新调度自己、不做任何事），已移除。
 * 如需更激进的保活，请优先考虑给用户引导"电池优化白名单"，而不是加闹钟。
 */
class KeepAliveService : Service() {
    companion object {
        private const val CHANNEL_ID = "autobook_keepalive"
        private const val NOTIFICATION_ID = 9999

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, KeepAliveService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SKY自动记账")
                .setContentText("正在后台运行 · 截图自动记账")
                .setSmallIcon(com.tao.autobook.R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "后台运行",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持记账服务在后台运行"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
