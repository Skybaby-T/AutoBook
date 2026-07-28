package com.tao.autobook.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tao.autobook.AutoBookApplication
import com.tao.autobook.notify.AutoBookNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaymentNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as AutoBookApplication
        // 应用内开关：关闭后即使系统权限开着也不自动记账
        if (!app.repository.isNotificationAutoBookEnabled()) {
            return
        }
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = listOfNotNull(
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.subText")?.toString()
        ).joinToString(" ")
        scope.launch {
            val result = app.repository.captureNotification(sbn.packageName, title, text)
            if (result.created && result.transaction != null) {
                AutoBookNotifier.notifyTransaction(this@PaymentNotificationListenerService, result.transaction, app.getDao().getCategories())
            }
        }
    }
}
