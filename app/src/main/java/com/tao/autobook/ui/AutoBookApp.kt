package com.tao.autobook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val AppBackground = Color(0xFFF4F6FA)
internal val CardWhite = Color(0xFFFFFFFF)
internal val Ink = Color(0xFF121820)
internal val Muted = Color(0xFF7D8792)
internal val Line = Color(0xFFE8ECF2)
internal val Blue = Color(0xFF4C8DCE)
private val BlueSoft = Color(0xFFEAF4FB)
private val Green = Color(0xFF10B981)
private val Orange = Color(0xFFEBA35D)
internal val Red = Color(0xFFD86B64)
private val Teal = Color(0xFF56A89A)
internal val FreshBlue = Color(0xFF6BAED6)
internal val FreshMint = Color(0xFF7BCBB8)
internal val FreshCoral = Color(0xFFFF9B8A)
private val FreshLavender = Color(0xFFAFA7E8)
private val FreshYellow = Color(0xFFF2C879)
private val FreshAqua = Color(0xFF78C6D0)
internal val FreshPalette = listOf(FreshMint, FreshBlue, FreshCoral, FreshYellow, FreshLavender, FreshAqua, Color(0xFF95D5B2), Color(0xFFFFC6A5))

internal data class ChartSeries(val values: List<Long>, val labels: List<String>)

enum class Tab(val label: String, val icon: ImageVector) {
    Ledger("记账", Icons.Default.Book),
    Entry("记一笔", Icons.Default.Add),
    Report("报表", Icons.Default.BarChart),
    Settings("设置", Icons.Default.Settings)
}

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
    onSaveCategory: (CategoryEntity?, String, TransactionType, Long, String, String?) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onMoveCategory: (CategoryEntity, Boolean) -> Unit = { _, _ -> },
    onExportCsv: () -> Unit,
    onImportBills: () -> Unit,
    onSaveAiSettings: (AiRecognitionSettings, String?) -> Unit,
    onTestAiSettings: (AiRecognitionSettings, String?) -> Unit,
    onFetchAiModels: (AiRecognitionSettings, String?) -> Unit,
    onClearUnconfirmedScreenshots: () -> Unit,
    onMessageConsumed: () -> Unit,
    notificationEnabled: Boolean,
    notificationAutoBookEnabled: Boolean = true,
    onToggleNotificationAutoBook: (Boolean) -> Unit = {},
    hideFromRecents: Boolean = true,
    onToggleHideFromRecents: (Boolean) -> Unit = {},
    autoDeleteScreenshot: Boolean = false,
    onToggleAutoDeleteScreenshot: (Boolean) -> Unit = {},
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
    onSendChat: (String, String?, String?) -> Unit = { _, _, _ -> },
    onExecuteChatOp: (String) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    onPickChatImage: () -> Unit = {},
    onPickChatFile: () -> Unit = {},
    pendingChatImageUri: String? = null,
    pendingChatFileName: String? = null,
    onClearPendingChatImage: () -> Unit = {},
    onClearPendingChatFile: () -> Unit = {},
    onClearAllPending: () -> Unit = {},
    onSaveAiNotificationPrompt: (String) -> Unit = {},
    onSaveAiAccessibilityPrompt: (String) -> Unit = {},
    onSaveAiScreenshotPrompt: (String) -> Unit = {},
    aiNotificationPrompt: String = "",
    aiAccessibilityPrompt: String = "",
    aiScreenshotPrompt: String = "",
    defaultNotificationPrompt: String = "",
    defaultAccessibilityPrompt: String = "",
    defaultScreenshotPrompt: String = "",
    onExportBackup: () -> Unit = {},
    onImportBackup: (String) -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onAddNotificationRule: (String, String, PaymentApp, NotificationMatchType) -> Unit,
    onDeleteNotificationRule: (Long) -> Unit,
    // 报表页：状态与回调
    report: ReportUiState = ReportUiState(),
    onReportPeriod: (ReportPeriod) -> Unit = {},
    onReportType: (TransactionType) -> Unit = {},
    onReportShift: (Long) -> Unit = {},
    onReportCustomRange: (java.time.LocalDate, java.time.LocalDate) -> Unit = { _, _ -> },
    onReportDrill: (String) -> Unit = {},
    onSaveBudget: (String, String) -> Unit = { _, _ -> },
    /** 保存账单（含不计入收支/不计入预算标记） */
    onUpdateTransactionFull: (Long, String, String, String, TransactionType, Long, String, com.tao.autobook.data.PaymentApp, Boolean, Boolean) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    monthStartDay: Int = 1,
    onMonthStartDayChange: (Int) -> Unit = {}
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
                        onBack = { showChat = false },
                        onPickImage = onPickChatImage,
                        onPickFile = onPickChatFile,
                        pendingImageUri = pendingChatImageUri,
                        pendingFileName = pendingChatFileName,
                        onClearPendingImage = onClearPendingChatImage,
                        onClearPendingFile = onClearPendingChatFile
                    )
                } else if (showAiPromptEditor) {
                    AiPromptEditorScreen(
                        notificationPrompt = aiNotificationPrompt,
                        accessibilityPrompt = aiAccessibilityPrompt,
                        screenshotPrompt = aiScreenshotPrompt,
                        defaultNotificationPrompt = defaultNotificationPrompt,
                        defaultAccessibilityPrompt = defaultAccessibilityPrompt,
                        defaultScreenshotPrompt = defaultScreenshotPrompt,
                        onSaveNotification = onSaveAiNotificationPrompt,
                        onSaveAccessibility = onSaveAiAccessibilityPrompt,
                        onSaveScreenshot = onSaveAiScreenshotPrompt
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
                    CategoryManagerScreen(state, onSaveCategory, onDeleteCategory, onMoveCategory)
                } else if (showPendingReview) {
                    PendingReviewScreen(state, onConfirmPending, onIgnorePending, onClearAllPending = { onClearAllPending() })
                } else {
                    when (tab) {
                        Tab.Ledger -> LedgerScreen(state, onImportScreenshot, onPendingReview = { showPendingReview = true }, onEdit = { editingTransaction = it }, onDelete = onDeleteTransaction, onDeleteBatch = onDeleteTransactions)
                        Tab.Entry -> EntryScreen(state, onAddManual, manualVoucherCount = manualVoucherCount, onPickManualVoucher = onPickManualVoucher, onClearManualVoucher = onClearManualVoucher)
                        Tab.Report -> ReportScreen(
                            report = report,
                            categories = state.categories,
                            onPeriod = onReportPeriod,
                            onType = onReportType,
                            onShift = onReportShift,
                            onCustomRange = onReportCustomRange,
                            onDrill = onReportDrill,
                            onSaveBudget = onSaveBudget,
                        )
                        Tab.Settings -> SettingsScreen(
                            state = state,
                            notificationAutoBookEnabled = notificationAutoBookEnabled,
                            onToggleNotificationAutoBook = onToggleNotificationAutoBook,
                            hideFromRecents = hideFromRecents,
                            onToggleHideFromRecents = onToggleHideFromRecents,
                            autoDeleteScreenshot = autoDeleteScreenshot,
                            onToggleAutoDeleteScreenshot = onToggleAutoDeleteScreenshot,
                            monthStartDay = monthStartDay,
                            onMonthStartDayChange = onMonthStartDayChange,
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
                            onImportBackupClick = onImportBackupClick,
                            onShowLogs = { showLogs = true; loadLogs() },
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
            onRemoveVoucher = { idx -> onRemoveVoucher(tx.id, idx) },
            onSaveWithFlags = { merchant, amount, category, type, paidAt, note, paymentApp, exStats, exBudget ->
                onUpdateTransactionFull(tx.id, merchant, amount, category, type, paidAt, note, paymentApp, exStats, exBudget)
                editingTransaction = null
            }
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
    var searchQuery by remember { mutableStateOf("") }

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

    fun matchesSearch(tx: TransactionEntity, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        val categoryName = state.categories.firstOrNull { it.id == tx.categoryId }?.name.orEmpty()
        val haystack = listOf(
            tx.merchantName,
            tx.note,
            categoryName,
            tx.paymentApp.label,
            tx.type.label,
            tx.sourceType.name,
            "%.2f".format(tx.amount),
            formatDate(tx.paidAt),
            formatTime(tx.paidAt)
        ).joinToString(" ").lowercase()
        // 支持空格分词：全部词都命中才算匹配
        return q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.all { token ->
            haystack.contains(token)
        }
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    Box(Modifier.fillMaxSize()) {
    val filteredTxs = state.transactions
        .asSequence()
        .filter { typeFilter == null || it.type == typeFilter }
        .filter { matchesSearch(it, searchQuery) }
        .toList()
    val grouped = filteredTxs.groupBy { formatDate(it.paidAt) }
    LazyColumn(Modifier.fillMaxSize().padding(
        start = 16.dp, end = 16.dp, top = 16.dp,
        bottom = if (selectionMode) 72.dp else 16.dp
    ), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索商户、备注、分类、金额…", color = Muted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Muted)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空", tint = Muted)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue,
                    unfocusedBorderColor = Line,
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* 本地即时过滤，无需额外动作 */ })
            )
        }
        item { LedgerSummaryCard(state, typeFilter) { typeFilter = it } }
        if (!selectionMode) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionButton("上传截图补记", Icons.Default.PhotoLibrary, onImportScreenshot, Modifier.weight(1f))
                    ActionButton("待确认 ${state.pending.size}", Icons.Default.TaskAlt, onPendingReview, Modifier.weight(1f), secondary = true)
                }
            }
        }
        if (searchQuery.isNotBlank()) {
            item {
                Text(
                    "找到 ${filteredTxs.size} 条结果",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
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
        if (state.transactions.isEmpty()) {
            item { EmptyHint("截图后会自动记账；也可开启通知自动记账或导入账单。") }
        } else if (filteredTxs.isEmpty()) {
            item { EmptyHint(if (searchQuery.isNotBlank()) "没有匹配「$searchQuery」的账单" else "当前筛选下没有账单") }
        }
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
    // 日汇总也排除「不计入收支」的账单，跟报表口径一致
    val counted = txs.filterNot { it.excludeFromStats }
    val expense = counted.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = counted.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
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
                        // 不计入收支的账单打橙色标记，避免用户困惑为何统计里没有它
                        if (tx.excludeFromStats) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "不计收支",
                                color = Orange,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.background(Orange.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
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
                onAddManual(merchantInput, evaluateAmountExpression(amount), categoryId, type, paidAt, note, paymentApp)
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
        PaymentApp.PINDUODUO to "拼多多",
        PaymentApp.DOUYIN to "抖音",
        PaymentApp.JD to "京东",
        PaymentApp.UNION_PAY to "云闪付",
        PaymentApp.UNKNOWN to "其他"
    )
    LazyRow(
        modifier.background(CardWhite, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(apps) { (app, label) ->
            val isSelected = selected == app
            Box(
                Modifier
                    .background(if (isSelected) BlueSoft else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(app) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
    // Only show top-level categories in the grid
    val topCategories = categories.filter { it.parentId == null }
    // Find which top-level category is the parent of the selected subcategory
    val selectedSub = categories.firstOrNull { it.id == selected && it.parentId != null }
    val expandedTopId = remember { mutableStateOf<String?>(selectedSub?.parentId) }
    // When selected changes, update expandedTopId
    LaunchedEffect(selected) {
        val sub = categories.firstOrNull { it.id == selected && it.parentId != null }
        if (sub != null) expandedTopId.value = sub.parentId
    }
    val expandedCat = expandedTopId.value
    val subcategories = if (expandedCat != null) categories.filter { it.parentId == expandedCat } else emptyList()

    Column(modifier) {
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(topCategories, key = { it.id }) { category ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    val subs = categories.filter { it.parentId == category.id }
                    if (subs.isNotEmpty()) {
                        expandedTopId.value = if (expandedTopId.value == category.id) null else category.id
                        // Auto-select first subcategory when expanding
                        if (expandedTopId.value == category.id) {
                            onSelect(subs.first().id)
                        }
                    } else {
                        expandedTopId.value = null
                        onSelect(category.id)
                    }
                }) {
                    val tint = Color(category.color.toInt())
                    val isExpanded = expandedTopId.value == category.id
                    val isSelected = selected == category.id || isExpanded
                    Box(
                        Modifier.size(48.dp).background(if (isSelected) tint.copy(alpha = 0.24f) else Color.Transparent, RoundedCornerShape(12.dp)),
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
        // Subcategory row
        if (subcategories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                subcategories.forEach { sub ->
                    val subTint = Color(sub.color.toInt())
                    val isSubSelected = selected == sub.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(if (isSubSelected) subTint.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { onSelect(sub.id) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(Modifier.size(8.dp).background(subTint, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(sub.name, color = if (isSubSelected) subTint else Ink, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberPad(onKey: (String) -> Unit, onDone: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3", "⌫"), listOf("4", "5", "6", "+"), listOf("7", "8", "9", "-"), listOf(".", "0", "=", "完成"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isDone = key == "完成"
                    Button(
                        onClick = { if (isDone) onDone() else onKey(key) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDone) Blue else if (key == "=") Color(0xFFE0E6EF) else CardWhite, contentColor = if (isDone) Color.White else Ink),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(key, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
    }
}

private fun applyAmountKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1).ifBlank { "0" }
    "+", "-" -> appendAmountOperator(current, key)
    "=" -> evaluateAmountExpression(current)
    "." -> appendAmountDecimal(current)
    else -> appendAmountDigit(current, key)
}

private fun appendAmountOperator(current: String, operator: String): String {
    val trimmed = current.ifBlank { "0" }.trimTrailingDecimal()
    if (trimmed.lastOrNull()?.isAmountOperator() == true) {
        return trimmed.dropLast(1) + operator
    }
    val evaluated = evaluateAmountExpression(trimmed)
    if (evaluated == "0" && operator == "-") return evaluated
    return "$evaluated$operator"
}

private fun appendAmountDecimal(current: String): String {
    val normalized = current.ifBlank { "0" }
    if (normalized.lastOrNull()?.isAmountOperator() == true) return "${normalized}0."
    val token = currentAmountToken(normalized)
    if (token.contains('.')) return normalized
    return "$normalized."
}

private fun appendAmountDigit(current: String, digit: String): String {
    val normalized = current.ifBlank { "0" }
    val token = currentAmountToken(normalized)
    val prefix = normalized.dropLast(token.length)
    val next = when {
        normalized == "0" && digit == "0" -> "0"
        token == "0" && !token.contains('.') -> prefix + digit
        else -> normalized + digit
    }
    val nextToken = currentAmountToken(next)
    val dot = nextToken.indexOf('.')
    if (dot >= 0 && nextToken.length - dot > 3) return current
    val integerDigits = nextToken.substringBefore('.').trimStart('0').length
    if (integerDigits > 7) return current
    return next.take(18)
}

private fun evaluateAmountExpression(expression: String): String {
    var text = expression.ifBlank { "0" }
        .trimTrailingOperator()
        .trimTrailingDecimal()
    if (text.isBlank()) return "0"

    var total = BigDecimal.ZERO
    var operator = '+'
    var start = 0
    for (i in 0..text.length) {
        val atEnd = i == text.length
        val ch = if (atEnd) null else text[i]
        if (atEnd || ch == '+' || ch == '-') {
            val numberText = text.substring(start, i).trimTrailingDecimal()
            val value = numberText.toBigDecimalOrNull() ?: return "0"
            total = if (operator == '-') total.subtract(value) else total.add(value)
            if (!atEnd && ch != null) operator = ch
            start = i + 1
        }
    }

    if (total <= BigDecimal.ZERO) return "0"
    if (total > BigDecimal("9999999.99")) total = BigDecimal("9999999.99")
    return total.setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

private fun currentAmountToken(text: String): String {
    val lastOperator = maxOf(text.lastIndexOf('+'), text.lastIndexOf('-'))
    return if (lastOperator >= 0) text.substring(lastOperator + 1) else text
}

private fun String.trimTrailingOperator(): String = dropLastWhile { it.isAmountOperator() }

private fun String.trimTrailingDecimal(): String = if (endsWith('.')) dropLast(1) else this

private fun Char.isAmountOperator(): Boolean = this == '+' || this == '-'

private fun String.toBigDecimalOrNull(): BigDecimal? = try {
    BigDecimal(this)
} catch (_: NumberFormatException) {
    null
}

@Composable
internal fun SegmentButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Ink else Muted,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier.background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

/** 月度周期起始日选择：1-28，1 表示自然月 */
@Composable
private fun MonthStartDayDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selected by remember { mutableStateOf(current.coerceIn(1, 28)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("月度周期起始日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (selected == 1) "当前：自然月（1 日 - 月末）"
                    else "当前：每月 $selected 日 至 次月 ${selected - 1} 日",
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
                Text("按工资日设置，可让「本月支出」和月预算跟真实现金流对齐。仅支持 1-28 日，避免 2 月缺日。", color = Muted, style = MaterialTheme.typography.bodySmall)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(190.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items((1..28).toList()) { day ->
                        val isSel = day == selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .background(if (isSel) Blue else Color(0xFFF2F5F9), RoundedCornerShape(8.dp))
                                .clickable { selected = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$day", color = if (isSel) Color.White else Ink, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsScreen(
    state: AutoBookUiState,
    notificationAutoBookEnabled: Boolean = true,
    onToggleNotificationAutoBook: (Boolean) -> Unit = {},
    hideFromRecents: Boolean = true,
    onToggleHideFromRecents: (Boolean) -> Unit = {},
    autoDeleteScreenshot: Boolean = false,
    onToggleAutoDeleteScreenshot: (Boolean) -> Unit = {},
    monthStartDay: Int = 1,
    onMonthStartDayChange: (Int) -> Unit = {},
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
        item { SettingCard("通知监听权限", "${if (notificationEnabled) "系统已授权" else "系统未授权"} · 点此去系统设置开关通知使用权", Icons.Default.Notifications, onNotification) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Blue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("通知自动记账", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (notificationAutoBookEnabled) "开启：支付通知会自动记账"
                            else "关闭：只靠截图记账，通知不入账",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (notificationAutoBookEnabled && !notificationEnabled) {
                            Text("系统通知使用权未开，开关开了也不会生效", color = Red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Switch(
                        checked = notificationAutoBookEnabled,
                        onCheckedChange = onToggleNotificationAutoBook
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Blue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("最近任务中隐藏", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hideFromRecents) "开启：从多任务/最近任务列表隐藏本应用"
                            else "关闭：在最近任务列表中显示本应用",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = hideFromRecents,
                        onCheckedChange = onToggleHideFromRecents
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Blue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("记账成功自动删除截图", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (autoDeleteScreenshot) "开启：自动记账成功后自动删除相册原截图"
                            else "关闭：保留相册截图",
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = autoDeleteScreenshot,
                        onCheckedChange = onToggleAutoDeleteScreenshot
                    )
                }
            }
        }
        item { SettingCard("系统横幅通知", "用于显示自动记账成功后的顶部弹窗；HyperOS 中请允许悬浮/横幅通知", Icons.Default.Notifications, onAppNotification) }
        item { SettingCard("无障碍辅助", "${if (accessibilityEnabled) "已开启" else "未开启"} · 当前版本不主动自动记账（仅保留服务）", Icons.Default.Accessibility, if (state.aiSettings.configured) onAccessibility else onShowAiRequiredDialog) }
        item { SectionTitle("账本") }
        // 月度周期起始日：按工资日算「本月」，影响统计、预算和报表
        item {
            var showPicker by remember { mutableStateOf(false) }
            SettingCard(
                "月度周期起始日",
                if (monthStartDay == 1) "每月 1 日（自然月）· 影响统计、预算和报表" else "每月 $monthStartDay 日 至 次月 ${monthStartDay - 1} 日 · 影响统计、预算和报表",
                Icons.Default.CalendarToday
            ) { showPicker = true }
            if (showPicker) {
                MonthStartDayDialog(
                    current = monthStartDay,
                    onDismiss = { showPicker = false },
                    onConfirm = {
                        onMonthStartDayChange(it)
                        showPicker = false
                    }
                )
            }
        }
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
        item { SectionTitle("关于") }
        item {
            val about = state.aboutInfo
            val ctx = LocalContext.current
            fun openUrl(url: String) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            fun copyUrl(url: String) {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", url))
                android.widget.Toast.makeText(ctx, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
            }

            Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val appVer = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
                    Text("${about.title} v$appVer", color = Ink, fontWeight = FontWeight.Bold)
                    if (about.description.isNotBlank()) {
                        Text(about.description, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    // 官网
                    if (about.website.isNotBlank()) {
                        Row(Modifier.fillMaxWidth().clickable { openUrl(about.website) }.padding(vertical = 2.dp)) {
                            Text("官网  ", color = Ink, style = MaterialTheme.typography.bodySmall)
                            Text(about.website, color = Blue, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.combinedClickable(
                                    onClick = { openUrl(about.website) },
                                    onLongClick = { copyUrl(about.website) }
                                ))
                        }
                    }
                    // GitHub
                    val github = about.github
                    if (github.isNotBlank()) {
                        Row(Modifier.fillMaxWidth().clickable { openUrl(github) }.padding(vertical = 2.dp)) {
                            Text("GitHub ", color = Ink, style = MaterialTheme.typography.bodySmall)
                            Text(github, color = Blue, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.combinedClickable(
                                    onClick = { openUrl(github) },
                                    onLongClick = { copyUrl(github) }
                                ))
                        }
                    }
                    // AI推荐
                    if (about.recommendations.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(about.sectionTitle, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        about.recommendations.forEach { rec ->
                            Column(Modifier.fillMaxWidth().clickable { openUrl(rec.url) }.padding(vertical = 3.dp)) {
                                Text(rec.name, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(rec.url, color = Blue, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.combinedClickable(
                                        onClick = { openUrl(rec.url) },
                                        onLongClick = { copyUrl(rec.url) }
                                    ))
                                if (rec.desc.isNotBlank()) {
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
private fun CategoryManagerScreen(
    state: AutoBookUiState,
    onSave: (CategoryEntity?, String, TransactionType, Long, String, String?) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onMove: (CategoryEntity, Boolean) -> Unit = { _, _ -> }
) {
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var addingType by remember { mutableStateOf<TransactionType?>(null) }
    var addingSubTo by remember { mutableStateOf<CategoryEntity?>(null) }
    var expandedTopId by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EmptyHint("用 ↑ ↓ 调整分类顺序，顺序会影响记一笔和编辑账单里的分类排列。") }
        TransactionType.entries.forEach { type ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(type.label)
                    TextButton(onClick = { addingType = type }) { Text("添加") }
                }
            }
            val topCategories = state.categories.filter { it.type == type && it.parentId == null }
            itemsIndexed(topCategories, key = { _, item -> item.id }) { index, category ->
                val subcategories = state.categories.filter { it.parentId == category.id }
                val isExpanded = expandedTopId == category.id
                Column {
                    CategoryManageRow(
                        category,
                        onEdit = { editing = category },
                        onDelete = { onDelete(category) },
                        onExpand = if (subcategories.isNotEmpty()) {
                            { expandedTopId = if (isExpanded) null else category.id }
                        } else null,
                        isExpanded = isExpanded,
                        subCount = subcategories.size,
                        onAddSub = { addingSubTo = category },
                        canMoveUp = index > 0,
                        canMoveDown = index < topCategories.lastIndex,
                        onMoveUp = { onMove(category, true) },
                        onMoveDown = { onMove(category, false) }
                    )
                    if (isExpanded && subcategories.isNotEmpty()) {
                        Column(Modifier.padding(start = 24.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            subcategories.forEachIndexed { subIndex, sub ->
                                CategoryManageRow(
                                    sub,
                                    onEdit = { editing = sub },
                                    onDelete = { onDelete(sub) },
                                    onExpand = null,
                                    isExpanded = false,
                                    subCount = 0,
                                    onAddSub = null,
                                    canMoveUp = subIndex > 0,
                                    canMoveDown = subIndex < subcategories.lastIndex,
                                    onMoveUp = { onMove(sub, true) },
                                    onMoveDown = { onMove(sub, false) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { category ->
        CategoryEditDialog(category = category, fixedType = category.type, allCategories = state.categories, onDismiss = { editing = null }, onSave = { name, type, color, icon, parentId ->
            onSave(category, name, type, color, icon, parentId)
            editing = null
        })
    }
    addingType?.let { type ->
        CategoryEditDialog(category = null, fixedType = type, allCategories = state.categories, onDismiss = { addingType = null }, onSave = { name, t, color, icon, parentId ->
            onSave(null, name, t, color, icon, parentId)
            addingType = null
        })
    }
    addingSubTo?.let { parent ->
        CategoryEditDialog(category = null, fixedType = parent.type, allCategories = state.categories, defaultParentId = parent.id, onDismiss = { addingSubTo = null }, onSave = { name, t, color, icon, parentId ->
            onSave(null, name, t, color, icon, parentId ?: parent.id)
            addingSubTo = null
        })
    }
}

@Composable
private fun CategoryManageRow(
    category: CategoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExpand: (() -> Unit)? = null,
    isExpanded: Boolean = false,
    subCount: Int = 0,
    onAddSub: (() -> Unit)? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 排序：上移 / 下移
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = if (canMoveUp) Blue else Line,
                    modifier = Modifier.size(22.dp).clickable(enabled = canMoveUp, onClick = onMoveUp)
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "下移",
                    tint = if (canMoveDown) Blue else Line,
                    modifier = Modifier.size(22.dp).clickable(enabled = canMoveDown, onClick = onMoveDown)
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(38.dp).background(Color(category.color.toInt()).copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(iconFor(category.icon), contentDescription = category.name, tint = Color(category.color.toInt()), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(category.name, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = when {
                    category.parentId != null -> "子分类"
                    category.isDefault -> "默认分类"
                    else -> "自定义分类"
                }
                Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (onAddSub != null) {
                IconButton(onClick = onAddSub, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "添加子分类", tint = Blue, modifier = Modifier.size(18.dp)) }
            }
            // 子分类数量与展开箭头并排，不要塞进同一个 IconButton 否则会重叠
            if (onExpand != null) {
                Row(
                    Modifier.clickable(onClick = onExpand).padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$subCount", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = Muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = Red, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun PendingReviewScreen(
    state: AutoBookUiState,
    onConfirm: (PendingScreenshotReview, String, String, String) -> Unit,
    onIgnore: (PendingScreenshotReview) -> Unit,
    onClearAllPending: () -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EmptyHint("这里用于处理截图 OCR 或自动识别不够确定的消费。确认后才会写入账本，避免误记。") }
        if (state.pending.isEmpty()) {
            item { EmptyHint("暂无待确认截图。上传支付截图后，如果识别结果不够确定，会出现在这里。") }
        } else {
            item {
                Button(
                    onClick = onClearAllPending,
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("全部清空（${state.pending.size}条）", color = Color.White)
                }
            }
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
    onRemoveVoucher: (Int) -> Unit = {},
    /** 带「不计入收支/不计入预算」标记的保存回调 */
    onSaveWithFlags: (String, String, String, TransactionType, Long, String, com.tao.autobook.data.PaymentApp, Boolean, Boolean) -> Unit
) {
    var type by remember(tx.id) { mutableStateOf(tx.type) }
    var merchant by remember(tx.id) { mutableStateOf(tx.merchantName) }
    var amount by remember(tx.id) { mutableStateOf(formatMoneyPlain(tx.amountCents)) }
    var note by remember(tx.id) { mutableStateOf(tx.note) }
    var merchantInput by remember(tx.id) { mutableStateOf(tx.merchantName) }
    var editingTime by remember { mutableStateOf(false) }
    var excludeStats by remember(tx.id) { mutableStateOf(tx.excludeFromStats) }
    var excludeBudget by remember(tx.id) { mutableStateOf(tx.excludeFromBudget) }
    var editPaymentApp by remember(tx.id) { mutableStateOf(tx.paymentApp) }
    var paidAt by remember(tx.id) { mutableStateOf(tx.paidAt) }
    var showVoucherFull by remember { mutableStateOf(false) }
    var category by remember(tx.id, type) { mutableStateOf(if (categories.any { it.id == tx.categoryId && it.type == type }) tx.categoryId else BuiltInCategories.fallbackFor(type)) }
    val filtered = categories.filter { it.type == type }
    // categories 异步加载后，修正 category 为 tx 原始值
    LaunchedEffect(categories, tx.categoryId, type) {
        if (categories.isNotEmpty()) {
            val match = categories.firstOrNull { it.id == tx.categoryId && it.type == type }
            if (match != null && category != tx.categoryId) {
                category = tx.categoryId
            }
        }
    }
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
                item { CategoryGrid(categories.filter { it.type == type || it.parentId != null }, selected = category, onSelect = { category = it }) }
                item { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    InfoPill(formatDateTimeShort(paidAt), Icons.Default.CalendarToday, Modifier.fillMaxWidth().clickable { editingTime = true })
                }
                // 不计入收支 / 不计入预算：退款、提现、还款、内部转账用这个，避免污染统计
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8F0)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("不计入收支", color = Ink, fontWeight = FontWeight.Bold)
                                    Text("退款、提现、还款、内部转账勾这个，不进统计和报表", color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(checked = excludeStats, onCheckedChange = { excludeStats = it })
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("不计入预算", color = Ink)
                                    Text("仍算收支，但不占预算额度（如大额一次性支出）", color = Muted, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(checked = excludeBudget, onCheckedChange = { excludeBudget = it }, enabled = !excludeStats)
                            }
                        }
                    }
                }
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
        confirmButton = {
            Button(
                onClick = {
                    // exclude 标记随保存一起提交，避免两个异步写入竞争导致标记被 copy 覆盖
                    onSaveWithFlags(
                        merchant, amount, category, type, paidAt, note, editPaymentApp,
                        excludeStats, if (excludeStats) false else excludeBudget
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) { Text("保存") }
        },
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
private fun CategoryEditDialog(
    category: CategoryEntity?,
    fixedType: TransactionType,
    allCategories: List<CategoryEntity> = emptyList(),
    defaultParentId: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, TransactionType, Long, String, String?) -> Unit
) {
    var name by remember(category?.id) { mutableStateOf(category?.name ?: "") }
    var color by remember(category?.id) { mutableStateOf(category?.color ?: paletteFor(fixedType).first()) }
    var icon by remember(category?.id) { mutableStateOf(category?.icon ?: "Category") }
    var parentId by remember(category?.id) { mutableStateOf(category?.parentId ?: defaultParentId) }
    val topCategories = allCategories.filter { it.type == fixedType && it.parentId == null && it.id != category?.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "添加分类" else "编辑分类") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分类名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                // Parent category selector
                if (topCategories.isNotEmpty()) {
                    Text("父级分类", color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        val noneSelected = parentId == null
                        Box(
                            Modifier.background(if (noneSelected) Blue.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { parentId = null }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("无（顶级）", color = if (noneSelected) Blue else Muted, style = MaterialTheme.typography.bodySmall, fontWeight = if (noneSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        topCategories.forEach { top ->
                            val isSelected = parentId == top.id
                            Box(
                                Modifier.background(if (isSelected) Color(top.color.toInt()).copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { parentId = top.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(top.name, color = if (isSelected) Color(top.color.toInt()) else Muted, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
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
        confirmButton = { Button(onClick = { onSave(name, fixedType, color, icon, parentId) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") } },
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
    screenshotPrompt: String,
    defaultNotificationPrompt: String,
    defaultAccessibilityPrompt: String,
    defaultScreenshotPrompt: String,
    onSaveNotification: (String) -> Unit,
    onSaveAccessibility: (String) -> Unit,
    onSaveScreenshot: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var notifPrompt by remember { mutableStateOf(notificationPrompt.ifBlank { defaultNotificationPrompt }) }
    var a11yPrompt by remember { mutableStateOf(accessibilityPrompt.ifBlank { defaultAccessibilityPrompt }) }
    var ssPrompt by remember { mutableStateOf(screenshotPrompt.ifBlank { defaultScreenshotPrompt }) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Tab selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("通知识别", "页面识别", "截图补记").forEachIndexed { idx, label ->
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
            when (selectedTab) {
                0 -> "通知识别提示词（AI开启时，每条通知都会发送给AI分析）"
                1 -> "页面识别提示词（无障碍服务检测到支付成功页面时调用AI）"
                else -> "截图补记提示词（上传截图时AI自动识别消费记录）"
            },
            color = Color(0xFF7D8792),
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = when (selectedTab) {
                0 -> notifPrompt
                1 -> a11yPrompt
                else -> ssPrompt
            },
            onValueChange = {
                when (selectedTab) {
                    0 -> notifPrompt = it
                    1 -> a11yPrompt = it
                    else -> ssPrompt = it
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    when (selectedTab) {
                        0 -> notifPrompt = defaultNotificationPrompt
                        1 -> a11yPrompt = defaultAccessibilityPrompt
                        else -> ssPrompt = defaultScreenshotPrompt
                    }
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("恢复默认") }
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> onSaveNotification(notifPrompt)
                        1 -> onSaveAccessibility(a11yPrompt)
                        else -> onSaveScreenshot(screenshotPrompt)
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
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
                    Text("无障碍辅助：当前不主动自动记账，避免浏览误触发。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("截图记账：支付后你自己截图，系统会自动识别并保存凭证。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("截图补记：手动导入支付截图，通过本地 OCR 和 AI 识别后自动记账。", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("账单导入：导入微信、支付宝、京东等 CSV/TXT 账单文件。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("知道了") } }
    }
}
