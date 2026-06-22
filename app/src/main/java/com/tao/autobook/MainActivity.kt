package com.tao.autobook

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tao.autobook.notify.AutoBookNotifier
import com.tao.autobook.ui.AutoBookApp
import com.tao.autobook.ui.AutoBookViewModel
import com.tao.autobook.service.KeepAliveService
import com.tao.autobook.ui.AutoBookViewModelFactory

class MainActivity : ComponentActivity() {
    private val pendingTransactionId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 21)
        startForegroundService(Intent(this, KeepAliveService::class.java))
        pendingTransactionId.value = intent.getLongExtra(AutoBookNotifier.EXTRA_TRANSACTION_ID, -1L).takeIf { it > 0 }
        setContent { AutoBookRoot() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 最小化到后台，不退出
        moveTaskToBack(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTransactionId.value = intent.getLongExtra(AutoBookNotifier.EXTRA_TRANSACTION_ID, -1L).takeIf { it > 0 }
    }

    @Composable
    private fun AutoBookRoot() {
        val app = application as AutoBookApplication


        var notificationEnabled by remember { mutableStateOf(isNotificationListenerEnabled()) }
        var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled()) }

        // Refresh permission status when app comes to foreground
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    notificationEnabled = isNotificationListenerEnabled()
                    accessibilityEnabled = isAccessibilityEnabled()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val vm: AutoBookViewModel = viewModel(factory = AutoBookViewModelFactory(app.repository))
        val state by vm.state.collectAsState()
        val chatMessages by vm.chatMessages.collectAsState()
        val isChatSending by vm.isChatSending.collectAsState()
        val customKeywords by vm.customKeywords.collectAsState()
        var openTransactionId by remember { mutableStateOf(pendingTransactionId.value) }
        var notice by remember { mutableStateOf<com.tao.autobook.notify.AutoBookNotice?>(null) }
        val pickScreenshots = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            vm.importScreenshots(uris)
        }
        var attachingTransactionId by remember { mutableStateOf<Long?>(null) }
        var manualVoucherUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
        val pickTransactionScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val txId = attachingTransactionId
            attachingTransactionId = null
            if (uri != null && txId != null) vm.attachScreenshot(txId, uri)
        }
        val pickManualVoucher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
            manualVoucherUris = uris
        }
        val pickBillFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            vm.importBillFiles(uris)
        }

        DisposableEffect(Unit) {
            AutoBookNotifier.setForegroundListener { vm.showForegroundNotice(it) }
            onDispose { AutoBookNotifier.setForegroundListener(null) }
        }
        LaunchedEffect(Unit) {
            vm.noticeEvents.collect { notice = it }
        }
        LaunchedEffect(Unit) {
            vm.openTransactionEvents.collect { openTransactionId = it }
        }
        LaunchedEffect(pendingTransactionId.value) {
            pendingTransactionId.value?.let { openTransactionId = it }
        }

        AutoBookApp(
            state = state,
            onImportScreenshot = {
                pickScreenshots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpenNotificationSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            onOpenAppNotificationSettings = { openAppNotificationSettings() },
            onOpenAccessibilitySettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onConfirmPending = { item, merchant, amount, category -> vm.confirmPending(item, merchant, amount, category) },
            onIgnorePending = vm::ignorePending,
            onDeleteTransaction = vm::deleteTransaction,
            onDeleteTransactions = vm::deleteTransactions,
            onRemoveVoucher = { txId, idx -> vm.removeVoucher(txId, idx) },
            onAttachScreenshot = { transactionId ->
                attachingTransactionId = transactionId
                pickTransactionScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpenVoucherPreview = vm::loadVoucherPreview,
            onClearVoucherPreview = vm::clearVoucherPreview,
            manualVoucherCount = manualVoucherUris.size,
            onPickManualVoucher = { pickManualVoucher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onClearManualVoucher = { manualVoucherUris = emptyList() },
            onAddManual = { merchant, amount, category, type, paidAt, note, paymentApp ->
                vm.addManualTransactionWithVouchers(merchant, amount, category, type, paidAt, note, paymentApp, manualVoucherUris)
                manualVoucherUris = emptyList()
            },
            onUpdateTransaction = vm::updateTransaction,
            onUpdateTransactionWithApp = { id, m, a, c, t, p, n, app -> vm.updateTransactionWithApp(id, m, a, c, t, p, n, app) },
            onSaveCategory = vm::saveCategory,
            onDeleteCategory = vm::deleteCategory,
            onExportCsv = { vm.exportCsv(::shareCsv) },
            onImportBills = { pickBillFiles.launch(arrayOf("text/*", "text/csv", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
            onSaveAiSettings = vm::saveAiSettings,
            onTestAiSettings = vm::testAiSettings,
            onFetchAiModels = vm::fetchAiModels,
            onClearUnconfirmedScreenshots = vm::clearUnconfirmedScreenshotCache,
            onMessageConsumed = vm::clearMessage,
            notificationEnabled = notificationEnabled,
            accessibilityEnabled = accessibilityEnabled,
            openTransactionId = openTransactionId,
            notice = notice,
            onNoticeConsumed = { notice = null },
            onOpenTransactionConsumed = {
                openTransactionId = null
                pendingTransactionId.value = null
            },
            onExitApp = { finish() },
            onLoadLogs = { vm.loadLogs() },
            onClearLogs = { vm.clearLogs() },
            onSaveAiNotificationPrompt = { vm.saveCustomPrompt("notification", it) },
            onSaveAiAccessibilityPrompt = { vm.saveCustomPrompt("accessibility", it) },
            aiNotificationPrompt = vm.getCustomPrompt("notification"),
            aiAccessibilityPrompt = vm.getCustomPrompt("accessibility"),
            defaultNotificationPrompt = vm.getDefaultPrompt("notification"),
            defaultAccessibilityPrompt = vm.getDefaultPrompt("accessibility"),
            onAddKeyword = { vm.addCustomKeyword(it) },
            onRemoveKeyword = { vm.removeCustomKeyword(it) },
            customKeywords = customKeywords,
            onRequestAiForAccessibility = { /* 由 AutoBookApp 内部处理 AI 未配置弹窗 */ },
            onRefreshAiStats = { vm.refreshAiStats() },
            chatMessages = chatMessages,
            isChatSending = isChatSending,
            onSendChat = { vm.sendChatMessage(it) },
            onExecuteChatOp = { vm.executeChatOperation(it) },
            onClearChatHistory = { vm.clearChatHistory() },
            onAddNotificationRule = vm::addNotificationRule,
            onDeleteNotificationRule = vm::deleteNotificationRule,
        )



    }

    private fun shareCsv(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "导出账本 CSV"))
    }

    private fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!enabled) return false
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(services)
        while (splitter.hasNext()) {
            val service = splitter.next()
            if (service.contains(packageName) && service.contains("PaymentAccessibilityService")) return true
        }
        return false
    }
}
