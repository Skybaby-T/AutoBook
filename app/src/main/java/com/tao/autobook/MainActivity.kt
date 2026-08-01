package com.tao.autobook

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
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
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_MEDIA_IMAGES
                ),
                20
            )
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 20)
        }
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 21)
        try { startForegroundService(Intent(this, KeepAliveService::class.java)) } catch (_: Exception) {}
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
        var notificationAutoBookEnabled by remember { mutableStateOf(vm.isNotificationAutoBookEnabled()) }
        var hideFromRecents by remember { mutableStateOf(vm.isHideFromRecentsEnabled()) }
        var autoDeleteScreenshot by remember { mutableStateOf(vm.isAutoDeleteScreenshotEnabled()) }
        var monthStartDay by remember { mutableStateOf(vm.getMonthStartDay()) }
        // 启动时按设置同步「最近任务隐藏」
        LaunchedEffect(Unit) { setExcludeFromRecents(hideFromRecents) }
        val state by vm.state.collectAsState()
        val chatMessages by vm.chatMessages.collectAsState()
        val isChatSending by vm.isChatSending.collectAsState()
        val customKeywords by vm.customKeywords.collectAsState()
        val report by vm.report.collectAsState()
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
        val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                vm.exportBackup { json ->
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
            }
        }
        val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val jsonStr = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (jsonStr != null) vm.importBackup(jsonStr)
            }
        }
        val pickBillFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            vm.importBillFiles(uris)
        }
        // 聊天图片/文件选择器
        var pendingChatImageUri by remember { mutableStateOf<String?>(null) }
        var pendingChatFileName by remember { mutableStateOf<String?>(null) }
        val pickChatImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pendingChatImageUri = uri.toString()
            }
        }
        val pickChatFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pendingChatFileName = getDisplayName(uri) ?: "附件"
            }
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
            onMoveCategory = vm::moveCategory,
            onExportCsv = { vm.exportCsv(::shareCsv) },
            onImportBills = { pickBillFiles.launch(arrayOf("text/*", "text/csv", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
            onSaveAiSettings = vm::saveAiSettings,
            onTestAiSettings = vm::testAiSettings,
            onFetchAiModels = vm::fetchAiModels,
            onClearUnconfirmedScreenshots = vm::clearUnconfirmedScreenshotCache,
            onMessageConsumed = vm::clearMessage,
            notificationEnabled = notificationEnabled,
            notificationAutoBookEnabled = notificationAutoBookEnabled,
            onToggleNotificationAutoBook = {
                vm.setNotificationAutoBookEnabled(it)
                notificationAutoBookEnabled = it
            },
            hideFromRecents = hideFromRecents,
            onToggleHideFromRecents = {
                vm.setHideFromRecentsEnabled(it)
                hideFromRecents = it
                setExcludeFromRecents(it)
            },
            autoDeleteScreenshot = autoDeleteScreenshot,
            onToggleAutoDeleteScreenshot = {
                vm.setAutoDeleteScreenshotEnabled(it)
                autoDeleteScreenshot = it
                if (it) requestAllFilesAccessIfNeeded()
            },
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
            onClearAllPending = { vm.clearAllPending() },
            onSaveAiNotificationPrompt = { vm.saveCustomPrompt("notification", it) },
            onSaveAiAccessibilityPrompt = { vm.saveCustomPrompt("accessibility", it) },
            onSaveAiScreenshotPrompt = { vm.saveCustomPrompt("screenshot", it) },
            aiNotificationPrompt = vm.getCustomPrompt("notification"),
            aiAccessibilityPrompt = vm.getCustomPrompt("accessibility"),
            aiScreenshotPrompt = vm.getCustomPrompt("screenshot"),
            defaultNotificationPrompt = vm.getDefaultPrompt("notification"),
            defaultAccessibilityPrompt = vm.getDefaultPrompt("accessibility"),
            defaultScreenshotPrompt = vm.getDefaultPrompt("screenshot"),
            onAddKeyword = { vm.addCustomKeyword(it) },
            onRemoveKeyword = { vm.removeCustomKeyword(it) },
            customKeywords = customKeywords,
            onRequestAiForAccessibility = { /* 由 AutoBookApp 内部处理 AI 未配置弹窗 */ },
            onRefreshAiStats = { vm.refreshAiStats() },
            chatMessages = chatMessages,
            isChatSending = isChatSending,
            onSendChat = { msg, imgUri, fName ->
                vm.sendChatMessage(msg, imgUri, fName)
                pendingChatImageUri = null
                pendingChatFileName = null
            },
            onExecuteChatOp = { vm.executeChatOperation(it) },
            onClearChatHistory = { vm.clearChatHistory() },
            onPickChatImage = { pickChatImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPickChatFile = { pickChatFile.launch(arrayOf("*/*")) },
            pendingChatImageUri = pendingChatImageUri,
            pendingChatFileName = pendingChatFileName,
            onClearPendingChatImage = { pendingChatImageUri = null },
            onClearPendingChatFile = { pendingChatFileName = null },
            onExportBackup = {
                vm.exportBackup { json ->
                    val exportDir = java.io.File(cacheDir, "exports").also { it.mkdirs() }
                    val file = java.io.File(exportDir, "autobook-backup-${System.currentTimeMillis()}.json")
                    file.writeText(json)
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(android.content.Intent.createChooser(intent, "导出数据"))
                }
            },
            onImportBackupClick = {
                importBackupLauncher.launch(arrayOf("application/json"))
            },
            onAddNotificationRule = vm::addNotificationRule,
            onDeleteNotificationRule = vm::deleteNotificationRule,
            report = report,
            onReportPeriod = vm::setReportPeriod,
            onReportType = vm::setReportType,
            onReportShift = vm::shiftReportPeriod,
            onReportCustomRange = vm::setReportCustomRange,
            onReportDrill = vm::toggleReportDrill,
            onSaveBudget = vm::saveBudget,
            onUpdateTransactionFull = vm::updateTransactionFull,
            monthStartDay = monthStartDay,
            onMonthStartDayChange = {
                vm.setMonthStartDay(it)
                monthStartDay = it
            },
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



    /** Android 11+ 删除系统截图需要「所有文件访问」权限 */
    private fun requestAllFilesAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun setExcludeFromRecents(hide: Boolean) {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { task ->
                runCatching { task.setExcludeFromRecents(hide) }
            }
        } catch (_: Exception) {
        }
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

    private fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) { null }
    }
}
