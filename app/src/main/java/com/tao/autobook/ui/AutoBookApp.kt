package com.tao.autobook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditScore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tao.autobook.ai.AiRecognitionSettings
import com.tao.autobook.data.BuiltInCategories
import com.tao.autobook.data.CategoryEntity
import com.tao.autobook.data.PendingScreenshotReview
import com.tao.autobook.data.SourceType
import com.tao.autobook.data.NotificationRuleEntity
import com.tao.autobook.data.NotificationMatchType
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionEntity
import com.tao.autobook.data.TransactionType
import com.tao.autobook.notify.AutoBookNotice
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val AppBackground = Color(0xFFF4F6FA)
private val CardWhite = Color(0xFFFFFFFF)
private val Ink = Color(0xFF121820)
private val Muted = Color(0xFF7D8792)
private val Line = Color(0xFFE8ECF2)
private val Blue = Color(0xFF4C8DCE)
private val BlueSoft = Color(0xFFEAF4FB)
private val Green = Color(0xFF10B981)
private val Orange = Color(0xFFEBA35D)
private val Red = Color(0xFFD86B64)
private val Teal = Color(0xFF56A89A)
private val FreshBlue = Color(0xFF6BAED6)
private val FreshMint = Color(0xFF7BCBB8)
private val FreshCoral = Color(0xFFFF9B8A)
private val FreshLavender = Color(0xFFAFA7E8)
private val FreshYellow = Color(0xFFF2C879)
private val FreshAqua = Color(0xFF78C6D0)
private val FreshPalette = listOf(FreshMint, FreshBlue, FreshCoral, FreshYellow, FreshLavender, FreshAqua, Color(0xFF95D5B2), Color(0xFFFFC6A5))

private data class ChartSeries(val values: List<Long>, val labels: List<String>)

enum class Tab(val label: String, val icon: ImageVector) {
    Ledger("记账", Icons.Default.Book),
    Entry("记一笔", Icons.Default.Add),
    Report("报表", Icons.Default.BarChart),
    Settings("设置", Icons.Default.Settings)
}

enum class ReportRange(val label: String) { Week("周报"), Month("月报"), Year("年报") }

@Composable
fun AutoBookApp(
    state: AutoBookUiState,
    onImportScreenshot: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onConfirmPending: (PendingScreenshotReview, String, String, String) -> Unit,
    onIgnorePending: (PendingScreenshotReview) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onDeleteTransactions: (Set<Long>) -> Unit,
    onRemoveVoucher: (Long, Int) -> Unit = { _, _ -> },
    onAttachScreenshot: (Long) -> Unit,
    onOpenVoucherPreview: (Long) -> Unit,
    onClearVoucherPreview: () -> Unit,
    manualVoucherCount: Int = 0,
    onPickManualVoucher: () -> Unit,
    onClearManualVoucher: () -> Unit,
    onAddManual: (String, String, String, TransactionType, Long, String, PaymentApp) -> Unit,
    onUpdateTransaction: (Long, String, String, String, TransactionType, Long, String) -> Unit,
    onUpdateTransactionWithApp: (Long, String, String, String, TransactionType, Long, String, com.tao.autobook.data.PaymentApp) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSaveCategory: (CategoryEntity?, String, TransactionType, Long, String) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onExportCsv: () -> Unit,
    onImportBills: () -> Unit,
    onSaveAiSettings: (AiRecognitionSettings, String?) -> Unit,
    onTestAiSettings: (AiRecognitionSettings, String?) -> Unit,
    onFetchAiModels: (AiRecognitionSettings, String?) -> Unit,
    onClearUnconfirmedScreenshots: () -> Unit,
    onMessageConsumed: () -> Unit,
    notificationEnabled: Boolean,
    accessibilityEnabled: Boolean,
    openTransactionId: Long?,
    notice: AutoBookNotice?,
    onNoticeConsumed: () -> Unit,
    onOpenTransactionConsumed: () -> Unit,
    onExitApp: () -> Unit,
    onLoadLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onAddKeyword: (String) -> Unit = {},
    onRemoveKeyword: (String) -> Unit = {},
    customKeywords: List<String> = emptyList(),
    onRequestAiForAccessibility: () -> Unit,
    onRefreshAiStats: () -> Unit = {},
    chatMessages: List<com.tao.autobook.data.ChatMessage> = emptyList(),
    isChatSending: Boolean = false,
    onSendChat: (String) -> Unit = {},
    onExecuteChatOp: (String) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    onSaveAiNotificationPrompt: (String) -> Unit = {},
    onSaveAiAccessibilityPrompt: (String) -> Unit = {},
    aiNotificationPrompt: String = "",
    aiAccessibilityPrompt: String = "",
    defaultNotificationPrompt: String = "",
    defaultAccessibilityPrompt: String = "",
    onExportBackup: () -> Unit = {},
    onImportBackup: (String) -> Unit = {},
    onSyncPush: (String, String) -> Unit = { _, _ -> },
    onSyncPull: (String, String) -> Unit = { _, _ -> },
    onSyncConfig: (String, String) -> Unit = { _, _ -> },
    syncConfig: Pair<String, String> = Pair("", ""),
    onAddNotificationRule: (String, String, PaymentApp, NotificationMatchType) -> Unit,
    onDeleteNotificationRule: (Long) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.Ledger) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showPendingReview by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showNotificationRuleManager by remember { mutableStateOf(false) }
    var showUsageGuide by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showWhitelistManager by remember { mutableStateOf(false) }
    var showAiPromptEditor by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    fun loadLogs() { onLoadLogs() }
    var showAiRequiredDialog by remember { mutableStateOf(false) }
    var currentNotice by remember { mutableStateOf<AutoBookNotice?>(null) }

    fun returnToLedgerHome() {
        editingTransaction = null
        showCategoryManager = false
        showPendingReview = false
        showAiSettings = false
        showLogs = false
        showChat = false
        currentNotice = null
        showExitConfirm = false
        tab = Tab.Ledger
    }

    BackHandler {
        when {
            showAiPromptEditor -> showAiPromptEditor = false
            showWhitelistManager -> showWhitelistManager = false
            showChat -> showChat = false
            showLogs -> showLogs = false
            showAiSettings -> showAiSettings = false
            showAiRequiredDialog -> showAiRequiredDialog = false
            editingTransaction != null -> editingTransaction = null
            currentNotice != null -> currentNotice = null
            showNotificationRuleManager -> showNotificationRuleManager = false
            showUsageGuide -> showUsageGuide = false
            showCategoryManager || showPendingReview || tab != Tab.Ledger -> returnToLedgerHome()
            else -> showExitConfirm = true
        }
    }

    LaunchedEffect(openTransactionId, state.transactions) {
        val id = openTransactionId ?: return@LaunchedEffect
        state.transactions.firstOrNull { it.id == id }?.let {
            editingTransaction = it
            tab = Tab.Ledger
            onOpenTransactionConsumed()
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            currentNotice = notice
            onNoticeConsumed()
        }
    }
    LaunchedEffect(state.transactions, editingTransaction?.id) {
        val id = editingTransaction?.id ?: return@LaunchedEffect
        state.transactions.firstOrNull { it.id == id }?.let { editingTransaction = it }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = when {
                        showNotificationRuleManager -> "通知规则库"
                    showUsageGuide -> "使用说明"
                    showLogs -> "操作日志"
                    showAiPromptEditor -> "AI提示词"
                    showWhitelistManager -> "白名单管理"
                        showCategoryManager -> "分类管理"
                        showPendingReview -> "待确认截图"
                        else -> tab.label
                    },
                    showBack = showCategoryManager || showPendingReview || showNotificationRuleManager || showUsageGuide || showLogs || showWhitelistManager || showAiPromptEditor,
                    onBack = {
                        showCategoryManager = false
                        showPendingReview = false
                        showNotificationRuleManager = false
                        showUsageGuide = false
                        showLogs = false
                        showAiPromptEditor = false
                    },
                    showChatIcon = tab == Tab.Ledger && !showCategoryManager && !showPendingReview && !showLogs,
                    onChat = { showChat = true }
                )
            },
            bottomBar = {
                if (!showCategoryManager && !showPendingReview && !showNotificationRuleManager && !showUsageGuide) {
                    NavigationBar(containerColor = CardWhite) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = item == tab,
                                onClick = { tab = item; showLogs = false; showChat = false },
                                icon = { Icon(item.icon, item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (tab == Tab.Ledger && !showCategoryManager && !showPendingReview && !showChat) {
                    FloatingActionButton(onClick = { tab = Tab.Entry }, containerColor = Blue, contentColor = Color.White, shape = CircleShape) {
                        Icon(Icons.Default.Add, contentDescription = "记一笔")
                    }
                }
            }
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding), color = AppBackground) {
                if (showNotificationRuleManager) {
                    NotificationRuleManagerScreen(state, onAddNotificationRule, onDeleteNotificationRule)
                } else if (showChat) {
                    ChatScreen(
                        messages = chatMessages,
                        isSending = isChatSending,
                        onSend = onSendChat,
                        onExecuteOp = onExecuteChatOp,
                        onClearHistory = onClearChatHistory,
                        onBack = { showChat = false }
                    )
                } else if (showAiPromptEditor) {
                    AiPromptEditorScreen(
                        notificationPrompt = aiNotificationPrompt,
                        accessibilityPrompt = aiAccessibilityPrompt,
                        defaultNotificationPrompt = defaultNotificationPrompt,
                        defaultAccessibilityPrompt = defaultAccessibilityPrompt,
                        onSaveNotification = onSaveAiNotificationPrompt,
                        onSaveAccessibility = onSaveAiAccessibilityPrompt
                    )
                } else if (showWhitelistManager) {
                    WhitelistManagerScreen(customKeywords, onAddKeyword, onRemoveKeyword)
                } else if (showLogs) {
                    LogScreen(state, onClearLogs = { onClearLogs() })
                } else if (showUsageGuide) {
                    UsageGuideDialog(state.aiSettings.configured, { showUsageGuide = false })
                } else if (showAiRequiredDialog) {
                    AlertDialog(
                        onDismissRequest = { showAiRequiredDialog = false },
                        title = { Text("需要配置 AI") },
                        text = { Text("请先开启 AI 记账并配置接口。无障碍识别依赖 AI 分析支付成功页面内容，未配置 AI 时无法使用。") },
                        confirmButton = {
                            TextButton(onClick = {
                                showAiRequiredDialog = false
                                showAiSettings = true
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAiRequiredDialog = false }) { Text("取消") }
                        }
                    )
                } else if (showCategoryManager) {
                    CategoryManagerScreen(state, onSaveCategory, onDeleteCategory)
                } else if (showPendingReview) {
                    PendingReviewScreen(state, onConfirmPending, onIgnorePending)
                } else {
                    when (tab) {
                        Tab.Ledger -> LedgerScreen(state, onImportScreenshot, onPendingReview = { showPendingReview = true }, onEdit = { editingTransaction = it }, onDelete = onDeleteTransaction, onDeleteBatch = onDeleteTransactions)
                        Tab.Entry -> EntryScreen(state, onAddManual, manualVoucherCount = manualVoucherCount, onPickManualVoucher = onPickManualVoucher, onClearManualVoucher = onClearManualVoucher)
                        Tab.Report -> ReportScreen(state)
                        Tab.Settings -> SettingsScreen(
                            state = state,
                            onNotification = onOpenNotificationSettings,
                            onAppNotification = onOpenAppNotificationSettings,
                            onAccessibility = onOpenAccessibilitySettings,
                            onExportCsv = onExportCsv,
                            onImportBills = onImportBills,
                            onOpenAiSettings = { showAiSettings = true },
                            onClearUnconfirmedScreenshots = onClearUnconfirmedScreenshots,
                            onOpenCategoryManager = { showCategoryManager = true },
                            onOpenNotificationRuleManager = { showNotificationRuleManager = true },
                            onShowUsageGuide = { showUsageGuide = true },
                            onShowWhitelistManager = { showWhitelistManager = true },
                            onShowAiPromptEditor = { showAiPromptEditor = true },
                            onExportBackup = onExportBackup,
                            onImportBackupClick = {},
                            onShowLogs = { showLogs = true; loadLogs() },
                            onSyncPush = onSyncPush,
                            onSyncPull = onSyncPull,
                            onSyncConfig = onSyncConfig,
                            syncConfig = syncConfig,
                            onRequestAiForAccessibility = onRequestAiForAccessibility,
                            onShowAiRequiredDialog = { showAiRequiredDialog = true },
                            onAddNotificationRule = onAddNotificationRule,
                            onDeleteNotificationRule = onDeleteNotificationRule,
                            notificationEnabled = notificationEnabled,
                            accessibilityEnabled = accessibilityEnabled
                        )
                    }
                }
            }
        }
    }

    editingTransaction?.let { tx ->
        TransactionEditDialog(
            tx = tx,
            categories = state.categories,
            onDismiss = { editingTransaction = null },
            onSave = { merchant, amount, category, type, paidAt, note, paymentApp ->
                onUpdateTransactionWithApp(tx.id, merchant, amount, category, type, paidAt, note, paymentApp)
                editingTransaction = null
            },
            onAttachScreenshot = { onAttachScreenshot(tx.id) },
            voucherBitmaps = if (state.voucherPreviewTransactionId == tx.id) state.voucherPreviewBitmaps else emptyList(),
            onLoadVoucherPreview = { onOpenVoucherPreview(tx.id) },
            onClearVoucherPreview = onClearVoucherPreview,
            onDelete = {
                onDeleteTransaction(tx.id)
                editingTransaction = null
            },
            onRemoveVoucher = { idx -> onRemoveVoucher(tx.id, idx) }
        )
    }

    currentNotice?.let { n ->
        AlertDialog(
            onDismissRequest = { currentNotice = null },
            title = { Text(n.title) },
            text = { Text(n.body) },
            confirmButton = {
                Button(onClick = {
                    state.transactions.firstOrNull { it.id == n.transactionId }?.let { editingTransaction = it }
                    currentNotice = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("查看/编辑") }
            },
            dismissButton = { TextButton(onClick = { currentNotice = null }) { Text("知道了") } }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出自动记账？") },
            text = { Text("再次确认后退出应用，避免全面屏返回手势误触。") },
            confirmButton = { Button(onClick = onExitApp, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("退出") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("继续使用") } }
        )
    }

    if (showAiSettings) {
        LaunchedEffect(Unit) { onRefreshAiStats() }
        AiSettingsDialog(
            settings = state.aiSettings,
            models = state.aiModels,
            aiStats = state.aiStats,
            onDismiss = { showAiSettings = false },
            onSave = { settings, key ->
                onSaveAiSettings(settings, key)
                showAiSettings = false
            },
            onTest = onTestAiSettings,
            onFetchModels = onFetchAiModels
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onMessageConsumed,
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = { Button(onClick = onMessageConsumed, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("知道了") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, showBack: Boolean, onBack: () -> Unit, showChatIcon: Boolean = false, onChat: () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold, color = Ink) },
        navigationIcon = {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
        },
        actions = {
            if (showChatIcon && !showBack) {
                Surface(
                    onClick = onChat,
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF5B9BD5),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("✨", style = MaterialTheme.typography.labelSmall)
                        Text("AI助手", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

@Composable
private fun LedgerScreen(state: AutoBookUiState, onImportScreenshot: () -> Unit, onPendingReview: () -> Unit, onEdit: (TransactionEntity) -> Unit, onDelete: (Long) -> Unit, onDeleteBatch: (Set<Long>) -> Unit) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) } // null=全部, EXPENSE=支出, INCOME=收入

    fun enterSelection(id: Long) { selectionMode = true; selectedIds = setOf(id) }
    fun toggleSelect(id: Long) { selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id }
    fun selectAll() { selectedIds = state.transactions.map { it.id }.toSet() }
    fun selectThisWeek() {
        val weekStart = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        selectedIds = state.transactions.filter { it.paidAt >= weekStart }.map { it.id }.toSet()
    }
    fun selectThisMonth() {
        val monthStart = java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        selectedIds = state.transactions.filter { it.paidAt >= monthStart }.map { it.id }.toSet()
    }
    fun exitSelection() { selectionMode = false; selectedIds = emptySet() }

    BackHandler(enabled = selectionMode) { exitSelection() }

    Box(Modifier.fillMaxSize()) {
    val filteredTxs = if (typeFilter != null) state.transactions.filter { it.type == typeFilter } else state.transactions
    val grouped = filteredTxs.groupBy { formatDate(it.paidAt) }
    LazyColumn(Modifier.fillMaxSize().padding(
        start = 16.dp, end = 16.dp, top = 16.dp,
        bottom = if (selectionMode) 72.dp else 16.dp
    ), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LedgerSummaryCard(state, typeFilter) { typeFilter = it } }
        if (!selectionMode) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton("上传截图补记", Icons.Default.PhotoLibrary, onImportScreenshot, Modifier.weight(1f))
                    ActionButton("待确认 ${state.pending.size}", Icons.Default.TaskAlt, onPendingReview, Modifier.weight(1f), secondary = true)
                }
            }
        }
        grouped.forEach { (date, txs) ->
            item { DateHeader(date, txs) }
            items(txs, key = { it.id }) { tx ->
                TransactionRow(
                    tx, state.categories,
                    onEdit = { if (selectionMode) toggleSelect(tx.id) else onEdit(tx) },
                    onDelete = onDelete,
                    selectionMode = selectionMode,
                    selected = tx.id in selectedIds,
                    onLongPress = { enterSelection(tx.id) },
                    onToggle = { toggleSelect(tx.id) }
                )
            }
        }
        if (state.transactions.isEmpty()) item { EmptyHint("开启通知监听或导入支付截图后，账单会自动出现在这里。") }
    }

    // 底部批量操作栏
    if (selectionMode) {
        Surface(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            color = CardWhite,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // 快速选择按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("本周" to ::selectThisWeek, "本月" to ::selectThisMonth, "全选" to ::selectAll).forEach { (label, action) ->
                        OutlinedButton(onClick = action, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(36.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(onClick = { selectedIds = emptySet() }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f).height(36.dp)) {
                        Text("取消全选", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(6.dp))
                // 操作按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::exitSelection) { Text("退出选择") }
                    Spacer(Modifier.weight(1f))
                    Text("已选 ${selectedIds.size} 项", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onDeleteBatch(selectedIds); exitSelection() },
                        enabled = selectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = Color.White)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun LedgerSummaryCard(
    state: AutoBookUiState,
    typeFilter: TransactionType?,
    onFilterChange: (TransactionType?) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = BlueSoft), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("今日支出（元）", color = Muted)
            Text(formatMoney(state.todayExpenseCents).removePrefix("¥"), color = Color(0xFF0D3D9A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterMetricBox("本月支出", formatMoney(state.monthExpenseCents), Modifier.weight(1f), selected = typeFilter == TransactionType.EXPENSE) {
                    onFilterChange(if (typeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE)
                }
                FilterMetricBox("本月收入", formatMoney(state.monthIncomeCents), Modifier.weight(1f), selected = typeFilter == TransactionType.INCOME) {
                    onFilterChange(if (typeFilter == TransactionType.INCOME) null else TransactionType.INCOME)
                }
            }
        }
    }
}

@Composable
private fun FilterMetricBox(label: String, value: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (selected) Blue else CardWhite.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = if (selected) Color.White else Muted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = if (selected) Color.White else Color(0xFF0D3D9A), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite.copy(alpha = 0.82f)), shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = Color(0xFF0D3D9A), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DateHeader(date: String, txs: List<TransactionEntity>) {
    val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("$date ${if (date == formatDate(System.currentTimeMillis())) "今天" else ""}", color = Ink, fontWeight = FontWeight.Bold)
        Text("支${formatMoney(expense)} 收${formatMoney(income)}", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TransactionRow(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (Long) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggle: () -> Unit = {}
) {
    val category = categories.firstOrNull { it.id == tx.categoryId }
    val tint = Color((category?.color ?: 0xFF5C6470L).toInt())
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Red, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggle() else onEdit(tx) },
                    onLongClick = onLongPress
                )
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // 选择模式复选框
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = Blue),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                // 左竖列：分类图标 + 名称（居中）
                Column(
                    modifier = Modifier.width(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(40.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconFor(category?.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        category?.name ?: "其他",
                        color = tint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(12.dp))

                // 中间内容区
                Column(modifier = Modifier.weight(1f)) {
                    // 第一行：时间 · 支付方式 · 来源（绿色小标签）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatTime(tx.paidAt)} · ${tx.paymentApp.label} ",
                            color = Muted,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            sourceLabel(tx.sourceType),
                            color = Green,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.background(Green.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    // 第二行：商户名称
                    Text(
                        tx.merchantName,
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    // 第三行：备注（非空时显示）
                    if (tx.note.isNotBlank()) {
                        Text(
                            tx.note,
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // 右竖列：金额（居中）
                Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        formatSignedMoney(tx),
                        color = if (tx.type == TransactionType.INCOME) Green else Ink,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这笔账单记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(tx.id)
                }) { Text("删除", color = Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun sourceLabel(source: SourceType): String = when (source) {
    SourceType.NOTIFICATION -> "通知"
    SourceType.ACCESSIBILITY -> "无障碍"
    SourceType.SCREENSHOT -> "截图"
    SourceType.MANUAL -> "手动"
}

@Composable
private fun SourceTag(sourceType: SourceType) {
    val text = when (sourceType) {
        SourceType.NOTIFICATION -> "自动记账"
        SourceType.ACCESSIBILITY -> "自动截图"
        SourceType.SCREENSHOT -> "截图识别"
        SourceType.MANUAL -> "手动"
    }
    Text(text, color = Green, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color(0xFFE7F8F0), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
}

@Composable
private fun EntryScreen(
    state: AutoBookUiState,
    onAddManual: (String, String, String, TransactionType, Long, String, PaymentApp) -> Unit,
    manualVoucherCount: Int = 0,
    onPickManualVoucher: () -> Unit,
    onClearManualVoucher: () -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var merchantInput by remember { mutableStateOf("") }
    var paidAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var editingTime by remember { mutableStateOf(false) }
    var paymentApp by remember { mutableStateOf(PaymentApp.WECHAT) }
    val filteredCategories = state.categories.filter { it.type == type }
    var categoryId by remember(type, filteredCategories) { mutableStateOf(filteredCategories.firstOrNull()?.id ?: BuiltInCategories.fallbackFor(type)) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TypeTabs(type) { type = it }
        Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 金额 + 时间
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("¥ $amount", color = if (amount == "0") Color(0xFFBBC3CC) else Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                    InfoPill(formatDateTimeShort(paidAt), Icons.Default.CalendarToday, Modifier.clickable { editingTime = true })
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line, RoundedCornerShape(1.dp)))
                // 支付方式 + 上传凭证（同一行）
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PaymentAppSelector(selected = paymentApp, onSelect = { paymentApp = it }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPickManualVoucher() }.padding(horizontal = 4.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Image, contentDescription = "上传凭证", tint = if (manualVoucherCount > 0) Green else Blue, modifier = Modifier.size(20.dp))
                            if (manualVoucherCount > 0) {
                                Text("$manualVoucherCount", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd).background(Red, CircleShape).padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        Text("凭证", color = if (manualVoucherCount > 0) Green else Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                // 消费类别
                CategoryGrid(filteredCategories, selected = categoryId, onSelect = { categoryId = it }, modifier = Modifier.fillMaxWidth().weight(1f))
                // 商户名称 + 备注（同一行各占一半）
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = merchantInput, onValueChange = { merchantInput = it },
                        label = { Text("商户名称") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("备注") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                }
            }
        }
        NumberPad(
            onKey = { key -> amount = applyAmountKey(amount, key) },
            onDone = {
                onAddManual(merchantInput, amount, categoryId, type, paidAt, note, paymentApp)
                amount = "0"
                note = ""
                merchantInput = ""
                paidAt = System.currentTimeMillis()
                onClearManualVoucher()
            }
        )
    }

    if (editingTime) {
        DateTimeEditDialog(
            initialMillis = paidAt,
            onDismiss = { editingTime = false },
            onSave = {
                paidAt = it
                editingTime = false
            }
        )
    }
}

@Composable
private fun PaymentAppSelector(selected: PaymentApp, onSelect: (PaymentApp) -> Unit, modifier: Modifier = Modifier) {
    val apps = listOf(
        PaymentApp.WECHAT to "微信",
        PaymentApp.ALIPAY to "支付宝",
        PaymentApp.DOUYIN to "抖音",
        PaymentApp.JD to "京东",
        PaymentApp.UNION_PAY to "云闪付",
        PaymentApp.UNKNOWN to "其他"
    )
    Row(
        modifier.background(CardWhite, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        apps.forEach { (app, label) ->
            val isSelected = selected == app
            Box(
                Modifier
                    .weight(1f)
                    .background(if (isSelected) BlueSoft else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(app) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Blue else Muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TypeTabs(type: TransactionType, onChange: (TransactionType) -> Unit) {
    val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
    Row(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.Center) {
        types.forEach { item ->
            Text(
                item.label,
                color = if (item == type) Ink else Muted,
                fontWeight = if (item == type) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).background(if (item == type) Color.White else Color.Transparent, RoundedCornerShape(14.dp)).clickable { onChange(item) }.padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<CategoryEntity>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier.height(246.dp)) {
    LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories, key = { it.id }) { category ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(category.id) }) {
                val tint = Color(category.color.toInt())
                Box(
                    Modifier.size(48.dp).background(if (selected == category.id) tint.copy(alpha = 0.24f) else Color.Transparent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(38.dp).background(tint.copy(alpha = 0.16f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Icon(iconFor(category.icon), contentDescription = category.name, tint = tint, modifier = Modifier.size(22.dp))
                    }
                }
                Text(category.name, color = Ink, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NumberPad(onKey: (String) -> Unit, onDone: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3", "⌫"), listOf("4", "5", "6", "+"), listOf("7", "8", "9", "-"), listOf(".", "0", "再记一笔", "完成"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isDone = key == "完成"
                    Button(
                        onClick = { if (isDone) onDone() else onKey(key) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDone) Blue else if (key == "再记一笔") Color(0xFFE0E6EF) else CardWhite, contentColor = if (isDone) Color.White else Ink),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(key, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
    }
}

private fun applyAmountKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1).ifBlank { "0" }
    "+", "-", "再记一笔" -> current
    "." -> if (current.contains('.')) current else "$current."
    else -> {
        val next = if (current == "0") key else current + key
        val dot = next.indexOf('.')
        if (dot >= 0 && next.length - dot > 3) current else next.take(10)
    }
}

@Composable
private fun ReportScreen(state: AutoBookUiState) {
    var range by remember { mutableStateOf(ReportRange.Month) }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val data = state.transactions.filter { it.type == type }
    val series = valuesForRange(data, range)
    val topCategory = data.groupBy { it.categoryId }.maxByOrNull { it.value.sumOf { tx -> tx.amountCents } }?.let { entry ->
        state.categories.firstOrNull { it.id == entry.key }?.name ?: "其他"
    } ?: "暂无"
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ReportTabs(range, onRange = { range = it }, type, onType = { type = it }) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFEFF)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("${range.label}概览", color = Ink, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricBlock("本期${type.label}", formatMoney(series.values.sum()), Modifier.weight(1f))
                        MetricBlock("最大分类", topCategory, Modifier.weight(1f))
                    }
                }
            }
        }
        item { ChartCard("${range.label}趋势") { LineChart(series, if (type == TransactionType.INCOME) FreshMint else FreshBlue) } }
        item { ChartCard("${type.label}柱状") { BarChart(series, if (type == TransactionType.INCOME) FreshMint else FreshCoral) } }
        item { CategoryPieCard(state, type) }
        if (data.isEmpty()) item { EmptyHint("有${type.label}记录后，这里会显示趋势和分类构成。") }
    }
}

@Composable
private fun ReportTabs(range: ReportRange, onRange: (ReportRange) -> Unit, type: TransactionType, onType: (TransactionType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReportRange.entries.forEach { item ->
                SegmentButton(item.label, selected = item == range, modifier = Modifier.weight(1f)) { onRange(item) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(Modifier.background(CardWhite, RoundedCornerShape(18.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SegmentButton("支出", selected = type == TransactionType.EXPENSE) { onType(TransactionType.EXPENSE) }
                SegmentButton("收入", selected = type == TransactionType.INCOME) { onType(TransactionType.INCOME) }
            }
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Ink else Muted,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier.background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ChartCard(title: String, chart: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold)
            chart()
        }
    }
}

@Composable
private fun LineChart(series: ChartSeries, color: Color) {
    val values = series.values
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val maxValue = max(1L, values.maxOrNull() ?: 0L).toFloat()
        if (values.all { it == 0L }) {
            drawLine(Line, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.62f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            return@Canvas
        }
        val stepX = size.width / max(1, values.size - 1)
        repeat(4) { i ->
            val y = size.height * i / 4f
            drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        values.zipWithNext().forEachIndexed { index, (a, b) ->
            val p1 = Offset(index * stepX, size.height - (a / maxValue) * size.height)
            val p2 = Offset((index + 1) * stepX, size.height - (b / maxValue) * size.height)
            drawLine(color, p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        values.forEachIndexed { index, value ->
            drawCircle(color, 4.dp.toPx(), Offset(index * stepX, size.height - (value / maxValue) * size.height))
        }
    }
        ChartLabels(series.labels)
    }
}

@Composable
private fun BarChart(series: ChartSeries, color: Color) {
    val values = series.values
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val maxValue = max(1L, values.maxOrNull() ?: 0L).toFloat()
        val gap = 8.dp.toPx()
        val barWidth = (size.width - gap * (values.size + 1)) / max(1, values.size)
        values.forEachIndexed { index, value ->
            val height = max(4f, (value / maxValue) * size.height)
            val left = gap + index * (barWidth + gap)
            drawRoundRect(color.copy(alpha = 0.72f), Offset(left, size.height - height), Size(barWidth, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()))
        }
    }
        ChartLabels(series.labels)
    }
}

@Composable
private fun ChartLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { label -> Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
    }
}

@Composable
private fun CategoryPieCard(state: AutoBookUiState, type: TransactionType) {
    val grouped = state.transactions.filter { it.type == type }.groupBy { it.categoryId }.mapValues { it.value.sumOf { tx -> tx.amountCents } }.toList().sortedByDescending { it.second }
    val total = grouped.sumOf { it.second }.coerceAtLeast(1L)
    ChartCard("${type.label}分类构成") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DonutChart(grouped.map { it.second }, grouped.indices.map { FreshPalette[it % FreshPalette.size] }, Modifier.size(150.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.take(5).forEachIndexed { index, (categoryId, categoryTotal) ->
                    val category = state.categories.firstOrNull { it.id == categoryId }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(FreshPalette[index % FreshPalette.size], CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("${category?.name ?: "其他"} ${(categoryTotal * 100 / total).coerceAtMost(100)}%", color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatMoney(categoryTotal), color = Muted)
                    }
                }
                if (grouped.isEmpty()) Text("暂无${type.label}数据", color = Muted)
            }
        }
    }
}

@Composable
private fun DonutChart(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val total = values.sum().takeIf { it > 0 } ?: 1L
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value.toFloat() / total * 360f
            drawArc(colors.getOrElse(index) { Blue }, start, sweep, false, style = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Butt))
            start += sweep
        }
        if (values.isEmpty()) drawCircle(Line, radius = size.minDimension / 2.6f, style = Stroke(width = 26.dp.toPx()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsScreen(
    state: AutoBookUiState,
    onNotification: () -> Unit,
    onAppNotification: () -> Unit,
    onAccessibility: () -> Unit,
    onExportCsv: () -> Unit,
    onImportBills: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onClearUnconfirmedScreenshots: () -> Unit,
    onOpenCategoryManager: () -> Unit,
    onOpenNotificationRuleManager: () -> Unit,
    onShowUsageGuide: () -> Unit,
    onExportBackup: () -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onShowLogs: () -> Unit = {},
    onSyncPush: (String, String) -> Unit = { _, _ -> },
    onSyncPull: (String, String) -> Unit = { _, _ -> },
    onSyncConfig: (String, String) -> Unit = { _, _ -> },
    syncConfig: Pair<String, String> = Pair("", ""),
    onShowWhitelistManager: () -> Unit = {},
    onShowAiPromptEditor: () -> Unit = {},
    onRequestAiForAccessibility: () -> Unit,
    onShowAiRequiredDialog: () -> Unit,
    onAddNotificationRule: (String, String, PaymentApp, NotificationMatchType) -> Unit,
    onDeleteNotificationRule: (Long) -> Unit,
    notificationEnabled: Boolean,
    accessibilityEnabled: Boolean
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("权限") }
        item { SettingCard("通知监听", "${if (notificationEnabled) "已开启" else "未开启"} · 用于读取支付成功通知并自动记账", Icons.Default.Notifications, onNotification) }
        item { SettingCard("系统横幅通知", "用于显示自动记账成功后的顶部弹窗；HyperOS 中请允许悬浮/横幅通知", Icons.Default.Notifications, onAppNotification) }
        item { SettingCard("无障碍辅助", "${if (accessibilityEnabled) "已开启" else "未开启"} · ${if (state.aiSettings.configured) "用于识别支付成功页并尝试自动截图" else "需要先开启 AI 记账才能使用"}", Icons.Default.Accessibility, if (state.aiSettings.configured) onAccessibility else onShowAiRequiredDialog) }
        item { SectionTitle("账本") }
        item { SettingCard("分类管理", "新增、重命名、换色或删除消费分类", Icons.Default.Category, onOpenCategoryManager) }
        item { SettingCard("导入账单", "选择微信、支付宝、京东、淘宝、抖音等 CSV/TXT/XLSX 账单", Icons.Default.FileUpload, onImportBills) }
        item { SettingCard("导出 CSV", "生成本地账本文件，并通过系统分享面板保存或发送", Icons.Default.FileDownload, onExportCsv) }
        item { SettingCard("通知规则库", "本地模式下自定义关键词规则匹配自动记账", Icons.Default.Category, onOpenNotificationRuleManager) }
        item { SettingCard("白名单管理", "自定义消费相关关键词白名单", Icons.Default.Notifications, onShowWhitelistManager) }
        item { SectionTitle("AI 记账") }
        item { SettingCard("AI 记账设置", "${if (state.aiSettings.enabled) "已开启" else "已关闭"} · 配置API地址和模型", Icons.Default.Image, onOpenAiSettings) }
        item { SettingCard("AI 提示词自定义", "自定义通知识别和页面识别的AI提示词", Icons.Default.Edit, onShowAiPromptEditor) }
        item { SettingCard("模型调用统计", state.aiStats.ifBlank { "暂无调用记录" }, Icons.Default.BarChart, {}) }
        item { SectionTitle("数据") }
        item { SettingCard("导出数据", "将所有账单、分类、规则导出为JSON文件", Icons.Default.FileDownload, onExportBackup) }
        item { SettingCard("导入数据", "从JSON备份文件恢复账单数据", Icons.Default.FileUpload, onImportBackupClick) }
        item { SettingCard("操作日志", "查看自动记账和系统操作记录", Icons.Default.ReceiptLong, onShowLogs) }
        item { SettingCard("使用说明", "了解 AI 模式和本地模式的使用方式", Icons.Default.MoreHoriz, onShowUsageGuide) }
        item { SectionTitle("云同步") }
        item { SyncSettingsCard(syncConfig, onSyncConfig, onSyncPush, onSyncPull) }
        item { SectionTitle("关于") }
        item {
            val about = state.aboutInfo
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${about.title} v1.0.4", color = Ink, fontWeight = FontWeight.Bold)
                    if (about.description.isNotBlank()) {
                        Text(about.description, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (about.website.isNotBlank()) {
                        val ctx = LocalContext.current
                        Text(
                            "官网：${about.website}",
                            color = Blue,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.combinedClickable(
                                onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(about.website))) },
                                onLongClick = {
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", about.website))
                                    android.widget.Toast.makeText(ctx, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        )
                    }
                    if (about.recommendations.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("AI记账助手推荐", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        about.recommendations.forEach { rec ->
                            val ctx = LocalContext.current
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rec.url)))
                                }.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("•", color = Blue, fontWeight = FontWeight.Bold)
                                Column {
                                    Text(rec.name, color = Blue, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    Text(rec.desc, color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("隐私模式", color = Ink, fontWeight = FontWeight.Bold)
                    Text("账单、截图和 OCR 原文只保存在本机。截图使用 Android Keystore 加密。", color = Muted)
                    Text("截图占用：${state.screenshotBytes / 1024} KB", color = Muted)
                    OutlinedButton(onClick = onClearUnconfirmedScreenshots, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Red)
                        Spacer(Modifier.width(8.dp))
                        Text("清除待确认截图缓存", color = Red)
                    }
                    Text("红米/HyperOS 建议允许自启动、后台运行，并关闭过度省电。", color = Orange)
                }
            }
        }
    }
}

@Composable
private fun AiSettingsDialog(
    settings: AiRecognitionSettings,
    models: List<String>,
    aiStats: String = "",
    onDismiss: () -> Unit,
    onSave: (AiRecognitionSettings, String?) -> Unit,
    onTest: (AiRecognitionSettings, String?) -> Unit,
    onFetchModels: (AiRecognitionSettings, String?) -> Unit
) {
    var enabled by remember(settings) { mutableStateOf(settings.enabled) }
    var apiUrl by remember(settings) { mutableStateOf(settings.apiUrl) }
    var model by remember(settings) { mutableStateOf(settings.model) }
    var apiKey by remember(settings) { mutableStateOf("") }
    var timeout by remember(settings) { mutableStateOf(settings.timeoutSeconds.toString()) }

    fun editedSettings(): AiRecognitionSettings = AiRecognitionSettings(
        enabled = enabled,
        apiUrl = apiUrl.trim(),
        model = model.trim(),
        apiKeySet = settings.apiKeySet || apiKey.isNotBlank(),
        timeoutSeconds = timeout.toIntOrNull()?.coerceIn(8, 90) ?: 30
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 记账设置") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启用 AI 兜底识别", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("关闭时截图仍只在本机 OCR。开启后，低置信度截图会发送到你配置的接口。", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                item { OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it }, label = { Text("API 地址") }, placeholder = { Text("可填根域名，自动补 /v1/chat/completions") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text(if (settings.apiKeySet) "API Key（留空不修改）" else "API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("模型名") }, placeholder = { Text("先验证模型，或手动输入") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedButton(onClick = { onFetchModels(editedSettings().copy(enabled = true), apiKey.ifBlank { null }) }, shape = RoundedCornerShape(10.dp)) { Text("验证模型") }
                    }
                }
                if (models.isNotEmpty()) {
                    item { Text("支持图片理解的模型", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    items(models.take(24)) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (item == model) BlueSoft else CardWhite),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().clickable { model = item }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (item == model) Text("已选", color = Blue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (models.size > 24) item { Text("已显示前 24 个通过图片测试的模型，可手动输入未显示的模型名。", color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                item { OutlinedTextField(value = timeout, onValueChange = { timeout = it.filter { ch -> ch.isDigit() }.take(2) }, label = { Text("超时秒数") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = BlueSoft), shape = RoundedCornerShape(12.dp)) {
                        Text("隐私提示：AI 功能默认关闭。验证模型会向接口发送一张极小测试图；正式识别只在截图补记本地识别不确定时上传压缩截图。密钥使用本机 Keystore 加密保存。", color = Muted, modifier = Modifier.padding(12.dp))
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("模型调用统计", color = Ink, fontWeight = FontWeight.Bold)
                            Text(aiStats.ifBlank { "暂无调用记录" }, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(editedSettings(), apiKey.ifBlank { null }) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onTest(editedSettings().copy(enabled = true), apiKey.ifBlank { null }) }) { Text("测试连接") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun CategoryManagerScreen(state: AutoBookUiState, onSave: (CategoryEntity?, String, TransactionType, Long, String) -> Unit, onDelete: (CategoryEntity) -> Unit) {
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var addingType by remember { mutableStateOf<TransactionType?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TransactionType.entries.forEach { type ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(type.label)
                    TextButton(onClick = { addingType = type }) { Text("添加") }
                }
            }
            items(state.categories.filter { it.type == type }, key = { it.id }) { category ->
                CategoryManageRow(category, onEdit = { editing = category }, onDelete = { onDelete(category) })
            }
        }
    }
    editing?.let { category ->
        CategoryEditDialog(category = category, fixedType = category.type, onDismiss = { editing = null }, onSave = { name, type, color, icon ->
            onSave(category, name, type, color, icon)
            editing = null
        })
    }
    addingType?.let { type ->
        CategoryEditDialog(category = null, fixedType = type, onDismiss = { addingType = null }, onSave = { name, t, color, icon ->
            onSave(null, name, t, color, icon)
            addingType = null
        })
    }
}

@Composable
private fun CategoryManageRow(category: CategoryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(category.color.toInt()).copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(iconFor(category.icon), contentDescription = category.name, tint = Color(category.color.toInt()), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(category.name, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(if (category.isDefault) "默认分类" else "自定义分类", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = Red) }
        }
    }
}

@Composable
private fun PendingReviewScreen(
    state: AutoBookUiState,
    onConfirm: (PendingScreenshotReview, String, String, String) -> Unit,
    onIgnore: (PendingScreenshotReview) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EmptyHint("这里用于处理截图 OCR 或自动识别不够确定的消费。确认后才会写入账本，避免误记。") }
        if (state.pending.isEmpty()) {
            item { EmptyHint("暂无待确认截图。上传支付截图后，如果识别结果不够确定，会出现在这里。") }
        } else {
            items(state.pending, key = { it.id }) { item ->
                PendingReviewCard(item, state.categories, onConfirm, onIgnore)
            }
        }
    }
}

@Composable
private fun PendingReviewCard(
    item: PendingScreenshotReview,
    categories: List<CategoryEntity>,
    onConfirm: (PendingScreenshotReview, String, String, String) -> Unit,
    onIgnore: (PendingScreenshotReview) -> Unit
) {
    var merchant by remember(item.id) { mutableStateOf(item.suggestedMerchant) }
    var amount by remember(item.id) { mutableStateOf(item.suggestedAmountCents?.let { formatMoneyPlain(it) } ?: "") }
    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }
    var category by remember(item.id, expenseCategories) { mutableStateOf(item.suggestedCategoryId.ifBlank { BuiltInCategories.OTHER }) }
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("截图 ${formatDate(item.capturedAt)} · ${item.sourceType.name}", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(item.ocrPreview, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("识别商户/备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            CategoryGrid(expenseCategories.take(10), selected = category, onSelect = { category = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("置信度 ${(item.confidence * 100).toInt()}%", color = Orange, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onIgnore(item) }) { Text("删除记录", color = Red) }
                    Button(onClick = { onConfirm(item, merchant, amount, category) }, enabled = amount.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("确认入账") }
                }
            }
        }
    }
}

@Composable
private fun TransactionEditDialog(
    tx: TransactionEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, TransactionType, Long, String, com.tao.autobook.data.PaymentApp) -> Unit,
    onUpdateTransactionWithApp: (Long, String, String, String, TransactionType, Long, String, PaymentApp) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onAttachScreenshot: () -> Unit,
    voucherBitmaps: List<android.graphics.Bitmap> = emptyList(),
    onLoadVoucherPreview: () -> Unit,
    onClearVoucherPreview: () -> Unit,
    onDelete: () -> Unit,
    onRemoveVoucher: (Int) -> Unit = {}
) {
    var type by remember(tx.id) { mutableStateOf(tx.type) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchantName) }
    var amount by remember(tx.id) { mutableStateOf(formatMoneyPlain(tx.amountCents)) }
    var note by remember(tx.id) { mutableStateOf(tx.note) }
    var merchantInput by remember(tx.id) { mutableStateOf(tx.merchantName) }
    var editPaymentApp by remember(tx.id) { mutableStateOf(tx.paymentApp) }
    var paidAt by remember(tx.id) { mutableStateOf(tx.paidAt) }
    var editingTime by remember { mutableStateOf(false) }
    var showVoucherFull by remember { mutableStateOf(false) }
    var category by remember(tx.id, type) { mutableStateOf(if (categories.any { it.id == tx.categoryId && it.type == type }) tx.categoryId else BuiltInCategories.fallbackFor(type)) }
    val filtered = categories.filter { it.type == type }
    LaunchedEffect(tx.screenshotId) {
        if (tx.screenshotId != null) onLoadVoucherPreview() else onClearVoucherPreview()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账单") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { TypeTabs(type) { type = it } }
                item {
                    Column {
                        Text("支付方式", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                        PaymentAppSelector(selected = editPaymentApp, onSelect = { editPaymentApp = it })
                    }
                }
                item { OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { CategoryGrid(filtered, selected = category, onSelect = { category = it }) }
                item { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = BlueSoft), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = merchantInput,
                                onValueChange = { merchantInput = it; merchant = it },
                                label = { Text("商户名称") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            // 凭证图片：可滑动查看 + 删除
                            if (voucherBitmaps.isNotEmpty()) {
                                var voucherPage by remember { mutableStateOf(0) }
                                Column {
                                    Text("凭证 ${voucherPage + 1}/${voucherBitmaps.size}", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Box(Modifier.fillMaxWidth().height(160.dp).background(CardWhite, RoundedCornerShape(10.dp))) {
                                        val safePage = voucherPage.coerceIn(0, (voucherBitmaps.size - 1).coerceAtLeast(0))
                                        if (voucherBitmaps.isNotEmpty() && safePage < voucherBitmaps.size) {
                                            Image(
                                                bitmap = voucherBitmaps[safePage].asImageBitmap(),
                                                contentDescription = "凭证${safePage + 1}",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize().clickable { showVoucherFull = true }
                                            )
                                        }
                                        // 左右箭头
                                        if (voucherPage > 0) {
                                            IconButton(onClick = { voucherPage-- }, modifier = Modifier.align(Alignment.CenterStart).size(32.dp)) {
                                                Icon(Icons.Default.ArrowBack, "上一张", tint = Blue, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        if (voucherPage < voucherBitmaps.size - 1) {
                                            IconButton(onClick = { voucherPage++ }, modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)) {
                                                Icon(Icons.Default.ArrowForward ?: Icons.Default.ArrowBack, "下一张", tint = Blue, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        // 删除按钮
                                        IconButton(
                                            onClick = {
                                                onRemoveVoucher(voucherPage)
                                                // 删除后修正页码，防止越界
                                                if (voucherPage > 0 && voucherPage >= voucherBitmaps.size - 1) {
                                                    voucherPage = voucherBitmaps.size - 2
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Red.copy(alpha = 0.8f), CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, "删除", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            // 添加凭证按钮
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onAttachScreenshot, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Blue)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (voucherBitmaps.isEmpty()) "上传凭证" else "添加凭证", color = Blue)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(merchant, amount, category, type, paidAt, note, editPaymentApp) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = Red) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    if (editingTime) {
        DateTimeEditDialog(
            initialMillis = paidAt,
            onDismiss = { editingTime = false },
            onSave = {
                paidAt = it
                editingTime = false
            }
        )
    }
    if (showVoucherFull && voucherBitmaps.isNotEmpty() && voucherBitmaps.size > 0) {
        AlertDialog(
            onDismissRequest = { showVoucherFull = false },
            title = { Text("凭证图片") },
            text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(voucherBitmaps.size) { idx ->
                Image(
                    bitmap = voucherBitmaps[idx].asImageBitmap(),
                    contentDescription = "凭证${idx + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )
            }
            }
            },
            confirmButton = { Button(onClick = { showVoucherFull = false }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("关闭") } }
        )
    }
}

@Composable
private fun DateTimeEditDialog(initialMillis: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val initial = remember(initialMillis) { Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    val currentYear = LocalDate.now().year
    var year by remember(initialMillis) { mutableStateOf(initial.year) }
    var month by remember(initialMillis) { mutableStateOf(initial.monthValue) }
    var day by remember(initialMillis) { mutableStateOf(initial.dayOfMonth) }
    var hour by remember(initialMillis) { mutableStateOf(initial.hour) }
    var minute by remember(initialMillis) { mutableStateOf(initial.minute) }
    val maxDay = YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(year, month) { if (day > maxDay) day = maxDay }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记账时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WheelPicker((currentYear - 5..currentYear + 2).toList(), year, { year = it }, { "${it}年" }, Modifier.weight(1.25f))
                    WheelPicker((1..12).toList(), month, { month = it }, { "%02d月".format(it) }, Modifier.weight(1f))
                    WheelPicker((1..maxDay).toList(), day, { day = it }, { "%02d日".format(it) }, Modifier.weight(1f))
                    WheelPicker((0..23).toList(), hour, { hour = it }, { "%02d时".format(it) }, Modifier.weight(1f))
                    WheelPicker((0..59).toList(), minute, { minute = it }, { "%02d分".format(it) }, Modifier.weight(1f))
                }
                Text("上下滑动并点击数值选择时间", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val millis = LocalDateTime.of(year, month, day.coerceAtMost(maxDay), hour, minute)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                onSave(millis)
            }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun WheelPicker(values: List<Int>, selected: Int, onSelected: (Int) -> Unit, label: (Int) -> String, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.height(154.dp).background(CardWhite, RoundedCornerShape(12.dp)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(values) { value ->
            val active = value == selected
            Text(
                label(value),
                color = if (active) Blue else Muted,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().height(36.dp).background(if (active) BlueSoft else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onSelected(value) }.padding(horizontal = 6.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CategoryEditDialog(category: CategoryEntity?, fixedType: TransactionType, onDismiss: () -> Unit, onSave: (String, TransactionType, Long, String) -> Unit) {
    var name by remember(category?.id) { mutableStateOf(category?.name ?: "") }
    var color by remember(category?.id) { mutableStateOf(category?.color ?: paletteFor(fixedType).first()) }
    var icon by remember(category?.id) { mutableStateOf(category?.icon ?: "Category") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "添加分类" else "编辑分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("颜色", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    paletteFor(fixedType).forEach { c ->
                        Box(Modifier.size(34.dp).background(Color(c.toInt()), CircleShape).clickable { color = c }, contentAlignment = Alignment.Center) {
                            if (color == c) Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Text("图标", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Restaurant", "Wallet", "ReceiptLong", "Category", "MoreHoriz").forEach { key ->
                        OutlinedButton(onClick = { icon = key }, shape = RoundedCornerShape(10.dp)) { Icon(iconFor(key), contentDescription = key, tint = if (icon == key) Blue else Muted) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, fixedType, color, icon) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, secondary: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) CardWhite else Blue, contentColor = if (secondary) Ink else Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(icon, contentDescription = text)
        Spacer(Modifier.size(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoPill(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.height(48.dp).background(CardWhite, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = text, tint = Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Ink, maxLines = 1)
    }
}

@Composable
private fun SettingCard(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(BlueSoft, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = title, tint = Blue, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, color = Ink, fontWeight = FontWeight.Bold)
                Text(body, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyHint(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Muted, modifier = Modifier.padding(16.dp))
    }
}

private fun iconFor(name: String?): ImageVector = when (name) {
    "Restaurant" -> Icons.Default.Restaurant
    "DirectionsCar" -> Icons.Default.DirectionsCar
    "ShoppingBag" -> Icons.Default.ShoppingBag
    "ReceiptLong" -> Icons.Default.ReceiptLong
    "Movie" -> Icons.Default.Movie
    "LocalHospital" -> Icons.Default.LocalHospital
    "School" -> Icons.Default.School
    "SwapHoriz" -> Icons.Default.SwapHoriz
    "Group" -> Icons.Default.Group
    "Payments" -> Icons.Default.Payments
    "Savings" -> Icons.Default.Savings
    "CardGiftcard" -> Icons.Default.CardGiftcard
    "CreditScore" -> Icons.Default.CreditScore
    "Wallet", "AddCard", "AccountBalanceWallet" -> Icons.Default.Wallet
    "MoreHoriz" -> Icons.Default.MoreHoriz
    else -> Icons.Default.Category
}

private fun paletteFor(type: TransactionType): List<Long> = when (type) {
    TransactionType.EXPENSE -> listOf(0xFFE85D4FL, 0xFF2F80EDL, 0xFFD98B2BL, 0xFF20A67AL, 0xFF6F52EDL)
    TransactionType.INCOME -> listOf(0xFFFFB547L, 0xFFFF9F43L, 0xFF10B981L, 0xFF2F80EDL, 0xFFDA8F2DL)
    TransactionType.OTHER -> listOf(0xFFB879F2L, 0xFFA56DE2L, 0xFF9163D9L, 0xFF7F5DC7L, 0xFF5C6470L)
}

private fun valuesForRange(transactions: List<TransactionEntity>, range: ReportRange): ChartSeries {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    return when (range) {
        ReportRange.Week -> (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to transactions.filter { Instant.ofEpochMilli(it.paidAt).atZone(zone).toLocalDate() == day }.sumOf { it.amountCents }
        }.let { rows -> ChartSeries(rows.map { it.second }, rows.map { it.first.format(DateTimeFormatter.ofPattern("MM-dd")) }) }
        ReportRange.Month -> (29 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to transactions.filter { Instant.ofEpochMilli(it.paidAt).atZone(zone).toLocalDate() == day }.sumOf { it.amountCents }
        }.let { rows ->
            val values = rows.map { it.second }
            val labels = rows.mapIndexedNotNull { index, row -> if (index == 0 || index == 9 || index == 19 || index == rows.lastIndex) row.first.format(DateTimeFormatter.ofPattern("MM-dd")) else null }
            ChartSeries(values, labels)
        }
        ReportRange.Year -> (11 downTo 0).map { offset ->
            val month = today.minusMonths(offset.toLong()).withDayOfMonth(1)
            month to transactions.filter { Instant.ofEpochMilli(it.paidAt).atZone(zone).toLocalDate().withDayOfMonth(1) == month }.sumOf { it.amountCents }
        }.let { rows -> ChartSeries(rows.map { it.second }, rows.map { "${it.first.monthValue}月" }) }
    }
}

private fun formatDateTimeShort(millis: Long): String = "${formatDate(millis)} ${formatTime(millis)}"

private fun parseDateTimeInput(date: String, time: String): Long? = runCatching {
    val parsedDate = LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
    val parsedTime = LocalTime.parse(time.trim().let { if (it.length == 5) it else it.take(5) })
    LocalDateTime.of(parsedDate, parsedTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()



@Composable
private fun NotificationRuleManagerScreen(
    state: AutoBookUiState,
    onAdd: (String, String, PaymentApp, NotificationMatchType) -> Unit,
    onDelete: (Long) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newKeyword by remember { mutableStateOf("") }
    var newCategoryId by remember { mutableStateOf(BuiltInCategories.OTHER) }
    var newMatchType by remember { mutableStateOf(NotificationMatchType.CONTAINS) }
    var newPaymentApp by remember { mutableStateOf(PaymentApp.UNKNOWN) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("在本地模式下，通知监听会根据规则库中的关键词匹配通知内容，命中后使用对应分类自动记账。", color = Muted, style = MaterialTheme.typography.bodySmall) }
        items(state.notificationRules, key = { it.id }) { rule ->
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.keyword, color = Ink, fontWeight = FontWeight.Bold)
                        Text("${rule.matchType.label} | ${rule.paymentApp.label} -> ${rule.categoryId}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDelete(rule.id) }) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = Red) }
                }
            }
        }
        item {
            Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("添加规则")
            }
        }
    }
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加通知规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = newKeyword, onValueChange = { newKeyword = it }, label = { Text("关键词") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("匹配方式", color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotificationMatchType.entries.forEach { mt ->
                            SegmentButton(mt.label, selected = mt == newMatchType, modifier = Modifier.weight(1f)) { newMatchType = mt }
                        }
                    }
                    Text("目标分类", color = Muted)
                    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.categories.filter { it.type == TransactionType.EXPENSE }) { cat ->
                            Box(modifier = Modifier.background(if (newCategoryId == cat.id) BlueSoft else Color.Transparent, RoundedCornerShape(8.dp)).clickable { newCategoryId = cat.id }.padding(6.dp), contentAlignment = Alignment.Center) {
                                Text(cat.name, color = if (newCategoryId == cat.id) Blue else Ink, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { if (newKeyword.isNotBlank()) { onAdd(newKeyword, newCategoryId, newPaymentApp, newMatchType); showAddDialog = false; newKeyword = "" } }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun AiPromptEditorScreen(
    notificationPrompt: String,
    accessibilityPrompt: String,
    defaultNotificationPrompt: String,
    defaultAccessibilityPrompt: String,
    onSaveNotification: (String) -> Unit,
    onSaveAccessibility: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var notifPrompt by remember { mutableStateOf(notificationPrompt.ifBlank { defaultNotificationPrompt }) }
    var a11yPrompt by remember { mutableStateOf(accessibilityPrompt.ifBlank { defaultAccessibilityPrompt }) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tab selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("通知识别", "页面识别").forEachIndexed { idx, label ->
                Text(
                    label,
                    color = if (selectedTab == idx) Blue else Color(0xFF7D8792),
                    fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(if (selectedTab == idx) BlueSoft else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedTab = idx }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Text(
            if (selectedTab == 0) "通知识别提示词（AI开启时，每条通知都会发送给AI分析）"
            else "页面识别提示词（无障碍服务检测到支付成功页面时调用AI）",
            color = Color(0xFF7D8792),
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = if (selectedTab == 0) notifPrompt else a11yPrompt,
            onValueChange = { if (selectedTab == 0) notifPrompt = it else a11yPrompt = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (selectedTab == 0) notifPrompt = defaultNotificationPrompt else a11yPrompt = defaultAccessibilityPrompt
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("恢复默认") }
            Button(
                onClick = {
                    if (selectedTab == 0) onSaveNotification(notifPrompt) else onSaveAccessibility(a11yPrompt)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
        }
    }
}

@Composable
private fun SyncSettingsCard(
    syncConfig: Pair<String, String>,
    onSyncConfig: (String, String) -> Unit,
    onSyncPush: (String, String) -> Unit,
    onSyncPull: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(syncConfig.first) }
    var password by remember { mutableStateOf(syncConfig.second) }
    var passwordVisible by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    // Display value: masked when hidden, real when visible
    val displayPassword = if (passwordVisible) password else "\u2022".repeat(password.length)

    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("云同步", color = Ink, fontWeight = FontWeight.Bold)
            Text("账号密码同步数据到服务器，多设备共享账单", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            if (feedback.isNotBlank()) {
                Text(feedback, color = if (feedback.contains("失败") || feedback.contains("错误")) Red else Blue, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("账号") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = displayPassword,
                onValueChange = { newVal ->
                    if (passwordVisible) {
                        password = newVal
                    } else {
                        // When masked, figure out what changed
                        if (newVal.length > displayPassword.length) {
                            // User typed a character
                            password += newVal.last()
                        } else if (newVal.length < displayPassword.length) {
                            // User deleted a character
                            password = password.dropLast(1)
                        }
                    }
                },
                label = { Text("密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = {
                    Text(
                        if (passwordVisible) "隐藏" else "显示",
                        color = Blue,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { passwordVisible = !passwordVisible }.padding(8.dp)
                    )
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSyncConfig(username, password); feedback = "账号已保存" },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("保存账号") }
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            feedback = "请先填写账号和密码"
                        } else {
                            feedback = "推送中..."
                            onSyncPush(username, password)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DCE)),
                    modifier = Modifier.weight(1f)
                ) { Text("推送") }
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            feedback = "请先填写账号和密码"
                        } else {
                            feedback = "拉取中..."
                            onSyncPull(username, password)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.weight(1f)
                ) { Text("拉取") }
            }
        }
    }
}

@Composable
private fun WhitelistManagerScreen(
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Add keyword input
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                label = { Text("添加关键词") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    if (newKeyword.isNotBlank()) {
                        onAdd(newKeyword.trim())
                        newKeyword = ""
                    }
                },
                enabled = newKeyword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("添加") }
        }

        Spacer(Modifier.height(12.dp))

        // Keywords list
        Text("共 ${keywords.size} 个关键词（添加/删除即时生效）", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        if (keywords.isEmpty()) {
            EmptyHint("白名单为空，请添加消费相关关键词。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(keywords) { keyword ->
                    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(keyword, color = Ink, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onRemove(keyword) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "删除", tint = Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogScreen(state: AutoBookUiState, onClearLogs: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("最近操作日志", color = Ink, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearLogs) { Text("清空", color = Red) }
            }
        }
        if (state.logs.isEmpty()) {
            item { EmptyHint("暂无日志记录。") }
        }
        items(state.logs.take(200)) { log ->
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(log.source, color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(formatDate(log.createdAt) + " " + formatTime(log.createdAt), color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(log.action, color = Ink, style = MaterialTheme.typography.bodySmall)
                    if (log.detail.isNotBlank()) {
                        Text(log.detail, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageGuideDialog(aiEnabled: Boolean, onDismiss: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = BlueSoft), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI 模式", color = Blue, fontWeight = FontWeight.Bold)
                    Text("开启 AI 记账开关并配置 API 后，通知监听、无障碍辅助和截图补记都会自动使用 AI 智能识别消费内容，支持自动分类，识别准确率更高。", color = Ink, style = MaterialTheme.typography.bodySmall)
                    Text("当前状态：${if (aiEnabled) "已开启" else "未开启"}", color = if (aiEnabled) Green else Muted, fontWeight = FontWeight.Medium)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本地模式", color = Ink, fontWeight = FontWeight.Bold)
                    Text("不联网，纯本地识别。通知监听通过本地解析器和规则库匹配自动记账；无障碍辅助不可用。可通过通知规则库自定义关键词规则提升本地识别效果。", color = Ink, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("功能说明", color = Ink, fontWeight = FontWeight.Bold)
                    Text("通知监听：开启后自动读取支付 App 的通知，解析金额和商户并自动记账。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("无障碍辅助：AI 模式下自动识别支付成功页面文本。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("截图补记：手动导入支付截图，通过本地 OCR 和 AI 识别后自动记账。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("账单导入：导入微信、支付宝、京东等 CSV/TXT 账单文件。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("知道了") } }
    }
}
