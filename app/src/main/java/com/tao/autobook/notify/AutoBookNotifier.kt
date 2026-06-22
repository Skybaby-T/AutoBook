package com.tao.autobook.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tao.autobook.MainActivity
import com.tao.autobook.R
import com.tao.autobook.data.CategoryEntity
import com.tao.autobook.data.TransactionEntity
import java.util.concurrent.atomic.AtomicReference

data class AutoBookNotice(
    val transactionId: Long,
    val title: String,
    val body: String
)

object AutoBookNotifier {
    const val CHANNEL_ID = "auto_book_success"
    const val EXTRA_TRANSACTION_ID = "transactionId"

    private val foregroundListener = AtomicReference<((AutoBookNotice) -> Unit)?>(null)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "自动记账成功", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "自动识别支付后显示一次记账成功提醒"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attributes)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun setForegroundListener(listener: ((AutoBookNotice) -> Unit)?) {
        foregroundListener.set(listener)
    }

    fun notifyTransaction(context: Context, transaction: TransactionEntity, categories: List<CategoryEntity>) {
        createChannel(context)
        val category = categories.firstOrNull { it.id == transaction.categoryId }?.name ?: "其他"
        val notice = AutoBookNotice(
            transactionId = transaction.id,
            title = "已自动记账",
            body = "${transaction.merchantName} · ${formatAmount(transaction.amountCents)} · $category"
        )
        postSystemNotification(context, notice)
        // 只发送系统通知，不再弹应用内弹窗
    }

    private fun postSystemNotification(context: Context, notice: AutoBookNotice) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TRANSACTION_ID, notice.transactionId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notice.transactionId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify((10_000 + notice.transactionId).toInt(), notification)
        }
    }

    private fun formatAmount(cents: Long): String = "¥%,.2f".format(cents / 100.0)
}
