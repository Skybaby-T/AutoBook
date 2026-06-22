package com.tao.autobook.ui

import android.net.Uri
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tao.autobook.ai.AiRecognitionSettings
import com.tao.autobook.data.AutoBookLogEntry
import com.tao.autobook.data.AutoBookRepository
import com.tao.autobook.data.NotificationRuleEntity
import com.tao.autobook.data.NotificationMatchType
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.BuiltInCategories
import com.tao.autobook.data.CategoryEntity
import com.tao.autobook.data.PendingScreenshotReview
import com.tao.autobook.data.TransactionEntity
import com.tao.autobook.data.TransactionType
import com.tao.autobook.notify.AutoBookNotice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class AutoBookUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = BuiltInCategories.defaults,
    val pending: List<PendingScreenshotReview> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val screenshotBytes: Long = 0L,
    val aiSettings: AiRecognitionSettings = AiRecognitionSettings(),
    val aiModels: List<String> = emptyList(),
    val voucherPreviewTransactionId: Long? = null,
    val voucherPreviewBitmaps: List<Bitmap> = emptyList(),
    val logs: List<AutoBookLogEntry> = emptyList(),
    val notificationRules: List<NotificationRuleEntity> = emptyList(),
    val aiStats: String = "",
    val syncFeedback: String = "",
    val monthExpenseCents: Long = 0L,
    val monthIncomeCents: Long = 0L,
    val todayExpenseCents: Long = 0L,
    val todayIncomeCents: Long = 0L
)

class AutoBookViewModel(private val repository: AutoBookRepository) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pendingReviews = MutableStateFlow<List<PendingScreenshotReview>>(emptyList())
    private val openTransactionRequest = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val autoBookNotices = MutableSharedFlow<AutoBookNotice>(extraBufferCapacity = 1)
    private val aiModels = MutableStateFlow<List<String>>(emptyList())
    private val voucherPreview = MutableStateFlow<Pair<Long, List<Bitmap>>?>(null)
    private val logs = MutableStateFlow<List<AutoBookLogEntry>>(emptyList())
    private val notificationRules = MutableStateFlow<List<NotificationRuleEntity>>(emptyList())
    private val aiStatsFlow = MutableStateFlow("")
    val syncFeedbackFlow = MutableStateFlow("")
    val customKeywords: StateFlow<List<String>> = MutableStateFlow(repository.getWhitelist())
    private val _customKeywords = customKeywords as MutableStateFlow<List<String>>
    private val monthExpenseFlow = MutableStateFlow(0L)
    private val monthIncomeFlow = MutableStateFlow(0L)
    private val todayExpenseFlow = MutableStateFlow(0L)
    private val todayIncomeFlow = MutableStateFlow(0L)
    val chatMessages: StateFlow<List<com.tao.autobook.data.ChatMessage>> = repository.observeChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val chatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = chatSending
    private val chatOpsResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val chatOperationEvents: SharedFlow<String> = chatOpsResult

    val openTransactionEvents: SharedFlow<Long> = openTransactionRequest
    val noticeEvents: SharedFlow<AutoBookNotice> = autoBookNotices

    private val baseState = combine(
        repository.transactions,
        repository.categories,
        pendingReviews,
        busy,
        message
    ) { txs, cats, pending, isBusy, msg ->
        AutoBookUiState(txs, cats.ifEmpty { BuiltInCategories.defaults }, pending, isBusy, msg, repository.screenshotStorageBytes())
    }

    private val baseWithRules = combine(baseState, notificationRules) { base, rules ->
        base.copy(notificationRules = rules)
    }

    private val stateWithAi = combine(baseWithRules, repository.aiSettings, aiModels, voucherPreview, logs) { base, ai, models, preview, logList ->
        base.copy(aiSettings = ai, aiModels = models, voucherPreviewTransactionId = preview?.first, voucherPreviewBitmaps = preview?.second ?: emptyList(), logs = logList)
    }
    private val stateWithSync = combine(stateWithAi, aiStatsFlow, syncFeedbackFlow) { base, statsStr, syncFb ->
        base.copy(aiStats = statsStr, syncFeedback = syncFb)
    }
    private val stateWithStats = combine(stateWithSync, monthExpenseFlow, monthIncomeFlow, todayExpenseFlow) { base, mExp, mInc, tExp ->
        base.copy(monthExpenseCents = mExp, monthIncomeCents = mInc, todayExpenseCents = tExp)
    }
    val state: StateFlow<AutoBookUiState> = combine(stateWithStats, todayIncomeFlow) { base, tInc ->
        base.copy(todayIncomeCents = tInc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBookUiState())

    init {
        viewModelScope.launch { repository.observeNotificationRules().collect { notificationRules.value = it } }
        // 月度/日度统计：每30秒刷新一次（数据库聚合查询）
        viewModelScope.launch {
            while (true) {
                val now = java.time.LocalDate.now()
                val monthStart = now.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayStart = now.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                monthExpenseFlow.value = repository.getMonthExpense(monthStart)
                monthIncomeFlow.value = repository.getMonthIncome(monthStart)
                todayExpenseFlow.value = repository.getTodayExpense(todayStart)
                todayIncomeFlow.value = repository.getTodayIncome(todayStart)
                kotlinx.coroutines.delay(30_000L)
            }
        }
        viewModelScope.launch {
            repository.pendingScreenshots.collect { pendingReviews.value = repository.buildPendingReviews(it) }
        }
    }

    fun importScreenshots(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            runCatching { uris.forEach { repository.importScreenshot(it) } }
                .onSuccess { message.value = "已上传 ${uris.size} 张截图，识别不确定的会进入待确认" }
                .onFailure { message.value = it.message ?: "截图导入失败" }
            busy.value = false
        }
    }

    fun attachScreenshot(transactionId: Long, uri: Uri) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.attachScreenshotToTransaction(transactionId, uri) }
                .onSuccess { message.value = "凭证图片已关联到账单" }
                .onFailure { message.value = it.message ?: "凭证图片上传失败" }
            busy.value = false
        }
    }

    fun loadVoucherPreview(transactionId: Long) {
        viewModelScope.launch {
            val bitmaps = runCatching { repository.loadTransactionBitmaps(transactionId) }.getOrNull() ?: emptyList()
            voucherPreview.value = transactionId to bitmaps
        }
    }

    fun clearVoucherPreview() {
        voucherPreview.value = null
    }

    fun confirmPending(item: PendingScreenshotReview, merchant: String = item.suggestedMerchant, amountText: String = item.suggestedAmountCents?.let { formatMoneyPlain(it) } ?: "", categoryId: String = item.suggestedCategoryId) {
        val amount = parseMoneyToCents(amountText) ?: item.suggestedAmountCents ?: return
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.confirmScreenshot(item.id, merchant.ifBlank { item.suggestedMerchant }, amount, categoryId.ifBlank { item.suggestedCategoryId }, item.capturedAt)
            }.onSuccess { message.value = "已确认截图账单" }
                .onFailure { message.value = it.message ?: "确认失败" }
            busy.value = false
        }
    }

    fun ignorePending(item: PendingScreenshotReview) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.ignorePendingScreenshot(item.id) }
                .onSuccess { message.value = "待确认记录已删除" }
                .onFailure { message.value = it.message ?: "删除失败" }
            busy.value = false
        }
    }

    fun importBillFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.importBillFiles(uris) }
                .onSuccess { result ->
                    message.value = buildString {
                        append("导入完成：成功 ${result.successCount} 笔")
                        if (result.duplicateCount > 0) append("，重复 ${result.duplicateCount} 笔")
                        if (result.failedCount > 0) append("，未识别 ${result.failedCount} 行")
                        if (result.unsupportedCount > 0) append("，${result.unsupportedCount} 个文件需先导出 CSV/TXT")
                    }
                }
                .onFailure { message.value = it.message ?: "账单导入失败" }
            busy.value = false
        }
    }

    fun saveAiSettings(settings: AiRecognitionSettings, apiKey: String?) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.saveAiSettings(settings, apiKey) }
                .onSuccess { message.value = "AI 记账设置已保存" }
                .onFailure { message.value = it.message ?: "AI 设置保存失败" }
            busy.value = false
        }
    }

    fun testAiSettings(settings: AiRecognitionSettings, apiKey: String?) {
        viewModelScope.launch {
            busy.value = true
            val result = repository.testAiSettings(settings, apiKey)
            message.value = result.fold(
                onSuccess = { "AI 接口连接测试成功" },
                onFailure = { it.message ?: "AI 接口连接测试失败" }
            )
            busy.value = false
        }
    }

    fun fetchAiModels(settings: AiRecognitionSettings, apiKey: String?) {
        viewModelScope.launch {
            busy.value = true
            val result = repository.fetchAiModels(settings, apiKey)
            result.onSuccess { models ->
                aiModels.value = models
                message.value = "已验证 ${models.size} 个支持图片理解的模型"
            }.onFailure {
                message.value = it.message ?: "验证模型列表失败"
            }
            busy.value = false
        }
    }

    fun clearUnconfirmedScreenshotCache() {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.clearUnconfirmedScreenshotCache() }
                .onSuccess { count -> message.value = "已清除 $count 条待确认截图缓存" }
                .onFailure { message.value = it.message ?: "清除截图缓存失败" }
            busy.value = false
        }
    }

    fun addManualTransaction(merchant: String, amountText: String, categoryId: String, type: TransactionType, paidAt: Long, note: String = "", paymentApp: PaymentApp = PaymentApp.UNKNOWN, voucherUri: Uri? = null) {
        viewModelScope.launch {
            val cents = parseMoneyToCents(amountText)
            if (cents == null || cents <= 0) {
                message.value = "请输入有效金额"
                return@launch
            }
            val displayName = merchant.ifBlank { note.trim().lineSequence().firstOrNull()?.take(24).orEmpty() }.ifBlank { type.defaultMerchant() }
            val id = repository.addManualTransaction(displayName, cents, categoryId.ifBlank { BuiltInCategories.fallbackFor(type) }, paidAt = paidAt, type = type, note = note, paymentApp = paymentApp)
            if (voucherUri != null) repository.attachScreenshotToTransaction(id, voucherUri)
            message.value = if (voucherUri != null) "已新增${type.label}记录并关联凭证" else "已新增${type.label}记录"
        }
    }

    fun updateTransaction(id: Long, merchant: String, amountText: String, categoryId: String, type: TransactionType, paidAt: Long, note: String) {
        viewModelScope.launch {
            val cents = parseMoneyToCents(amountText)
            if (cents == null || cents <= 0) {
                message.value = "请输入有效金额"
                return@launch
            }
            val displayName = merchant.ifBlank { note.trim().lineSequence().firstOrNull()?.take(24).orEmpty() }.ifBlank { type.defaultMerchant() }
            repository.updateTransaction(id, displayName, cents, categoryId, paidAt, note, type)
            message.value = "账单已保存"
        }
    }

    fun updateTransactionWithApp(id: Long, merchant: String, amountText: String, categoryId: String, type: TransactionType, paidAt: Long, note: String, paymentApp: com.tao.autobook.data.PaymentApp) {
        viewModelScope.launch {
            val cents = parseMoneyToCents(amountText)
            if (cents == null || cents <= 0) {
                message.value = "请输入有效金额"
                return@launch
            }
            val displayName = merchant.ifBlank { note.trim().lineSequence().firstOrNull()?.take(24).orEmpty() }.ifBlank { type.defaultMerchant() }
            repository.updateTransaction(id, displayName, cents, categoryId, paidAt, note, type, paymentApp)
            message.value = "账单已保存"
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransactionWithImages(id)
            message.value = "账单已删除"
        }
    }

    fun removeVoucher(transactionId: Long, index: Int) {
        viewModelScope.launch {
            repository.removeScreenshotByIndex(transactionId, index)
            loadVoucherPreview(transactionId)
            message.value = "凭证已删除"
        }
    }

    fun addManualTransactionWithVouchers(merchant: String, amountText: String, categoryId: String, type: TransactionType, paidAt: Long, note: String, paymentApp: PaymentApp, voucherUris: List<Uri>) {
        viewModelScope.launch {
            val cents = parseMoneyToCents(amountText)
            if (cents == null || cents <= 0) {
                message.value = "请输入有效金额"
                return@launch
            }
            val displayName = merchant.ifBlank { note.trim().lineSequence().firstOrNull()?.take(24).orEmpty() }.ifBlank { type.defaultMerchant() }
            val id = repository.addManualTransaction(displayName, cents, categoryId.ifBlank { BuiltInCategories.fallbackFor(type) }, paidAt = paidAt, type = type, note = note, paymentApp = paymentApp)
            voucherUris.forEach { uri -> repository.attachScreenshotToTransaction(id, uri) }
            message.value = if (voucherUris.isNotEmpty()) "已新增${type.label}记录并关联${voucherUris.size}张凭证" else "已新增${type.label}记录"
        }
    }

    fun deleteTransactions(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.deleteTransactionWithImages(it) }
            message.value = "已删除 ${ids.size} 笔账单"
        }
    }

    fun saveCategory(existing: CategoryEntity?, name: String, type: TransactionType, color: Long, icon: String) {
        viewModelScope.launch {
            val id = existing?.id ?: "cat_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val sortOrder = existing?.sortOrder ?: ((state.value.categories.filter { it.type == type }.maxOfOrNull { it.sortOrder } ?: 0) + 10)
            repository.upsertCategory(
                CategoryEntity(
                    id = id,
                    name = name.ifBlank { "未命名" },
                    icon = icon.ifBlank { "Category" },
                    color = color,
                    sortOrder = sortOrder,
                    type = type,
                    isDefault = existing?.isDefault ?: false
                )
            )
            message.value = "分类已保存"
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            if (category.id == BuiltInCategories.OTHER || category.id == BuiltInCategories.INCOME_OTHER) {
                message.value = "兜底分类不能删除"
                return@launch
            }
            repository.deleteCategoryAndMoveTransactions(category.id)
            message.value = "分类已删除，历史账单已转到其他"
        }
    }

    fun requestOpenTransaction(id: Long) {
        openTransactionRequest.tryEmit(id)
    }

    fun showForegroundNotice(notice: AutoBookNotice) {
        autoBookNotices.tryEmit(notice)
    }

    fun exportCsv(onReady: (File) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            runCatching { repository.exportCsv() }
                .onSuccess {
                    message.value = "CSV 已生成"
                    onReady(it)
                }
                .onFailure { message.value = it.message ?: "导出失败" }
            busy.value = false
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            logs.value = repository.getLogs()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            logs.value = emptyList()
            message.value = "日志已清空"
        }
    }

    fun addNotificationRule(keyword: String, categoryId: String, paymentApp: PaymentApp, matchType: NotificationMatchType) {
        viewModelScope.launch {
            repository.addNotificationRule(keyword, categoryId, paymentApp, matchType)
            message.value = "规则已添加"
        }
    }

    fun deleteNotificationRule(id: Long) {
        viewModelScope.launch {
            repository.deleteNotificationRule(id)
            message.value = "规则已删除"
        }
    }

    fun sendChatMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            chatSending.value = true
            val reply = repository.sendChatMessage(message)
            chatSending.value = false
        }
    }

    fun executeChatOperation(operation: String) {
        viewModelScope.launch {
            chatSending.value = true
            val result = repository.executeChatOperation(operation)
            // 直接插入结果消息，不再调AI
            repository.addChatMessage("assistant", "✅ $result")
            chatSending.value = false
        }
    }

    // ====== 云同步 ======
    fun getSyncConfig(): Pair<String, String> = repository.getSyncConfig()

    fun saveSyncConfig(username: String, password: String) {
        repository.saveSyncConfig(username, password)
        syncFeedbackFlow.value = "同步账号已保存: $username"
    }

    fun syncPush(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            syncFeedbackFlow.value = "请先填写账号和密码"
            return
        }
        repository.saveSyncConfig(username, password)
        syncFeedbackFlow.value = "推送中..."
        viewModelScope.launch {
            val result = repository.syncPush()
            syncFeedbackFlow.value = result
        }
    }

    fun syncPull(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            syncFeedbackFlow.value = "请先填写账号和密码"
            return
        }
        repository.saveSyncConfig(username, password)
        syncFeedbackFlow.value = "拉取中..."
        viewModelScope.launch {
            val result = repository.syncPull()
            syncFeedbackFlow.value = result
        }
    }

    fun exportBackup(onReady: (String) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            val json = repository.exportBackup()
            onReady(json.toString(2))
            busy.value = false
        }
    }

    fun importBackup(jsonStr: String) {
        viewModelScope.launch {
            busy.value = true
            val result = runCatching {
                val json = org.json.JSONObject(jsonStr)
                repository.importBackup(json)
            }
            message.value = result.getOrDefault("导入失败")
            busy.value = false
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            message.value = "对话记录已清空"
        }
    }

    fun addCustomKeyword(keyword: String) {
        repository.addWhitelistKeyword(keyword)
        _customKeywords.value = repository.getWhitelist()
    }

    fun removeCustomKeyword(keyword: String) {
        repository.removeWhitelistKeyword(keyword)
        _customKeywords.value = repository.getWhitelist()
    }

    fun getCustomPrompt(type: String): String = repository.getCustomPrompt(type)
    fun getDefaultPrompt(type: String): String = repository.getDefaultPrompt(type)
    fun saveCustomPrompt(type: String, prompt: String) {
        repository.saveCustomPrompt(type, prompt)
        message.value = "AI提示词已保存"
    }

    fun refreshAiStats() {
        aiStatsFlow.value = com.tao.autobook.ai.AiScreenshotRecognizer.stats.summary()
    }

    fun clearMessage() {
        message.value = null
    }
}

class AutoBookViewModelFactory(private val repository: AutoBookRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AutoBookViewModel(repository) as T
}

private fun TransactionType.defaultMerchant(): String = when (this) {
    TransactionType.EXPENSE -> "手动消费"
    TransactionType.INCOME -> "手动收入"
    TransactionType.OTHER -> "手动事项"
}

private fun monthStartMillis(): Long = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun todayStartMillis(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun formatMoney(cents: Long): String = "¥%,.2f".format(cents / 100.0)
fun formatSignedMoney(tx: TransactionEntity): String = when (tx.type) {
    TransactionType.INCOME -> "+${formatMoney(tx.amountCents)}"
    TransactionType.EXPENSE -> "-${formatMoney(tx.amountCents)}"
    TransactionType.OTHER -> formatMoney(tx.amountCents)
}
fun formatMoneyPlain(cents: Long): String = "%.2f".format(cents / 100.0)
fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0).toString()
fun parseMoneyToCents(text: String): Long? = text.trim().replace("¥", "").replace(",", "").toDoubleOrNull()?.let { kotlin.math.round(it * 100).toLong() }
