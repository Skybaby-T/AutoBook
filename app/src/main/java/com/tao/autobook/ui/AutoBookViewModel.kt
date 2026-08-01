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
import kotlinx.coroutines.flow.Flow
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
    val aboutInfo: com.tao.autobook.data.AutoBookRepository.AboutInfo = com.tao.autobook.data.AutoBookRepository.AboutInfo(),
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
    private val aboutInfoFlow = MutableStateFlow(com.tao.autobook.data.AutoBookRepository.AboutInfo())
    val customKeywords: StateFlow<List<String>> = MutableStateFlow(repository.getWhitelist())
    private val _customKeywords = customKeywords as MutableStateFlow<List<String>>
    private val monthExpenseFlow = MutableStateFlow(0L)
    private val monthIncomeFlow = MutableStateFlow(0L)
    private val todayExpenseFlow = MutableStateFlow(0L)
    private val todayIncomeFlow = MutableStateFlow(0L)
    /** 报表页状态：周期/类型/区间/聚合结果/预算/下钻 */
    private val reportState = MutableStateFlow(ReportUiState())
    val report: StateFlow<ReportUiState> = reportState
    val chatMessages: StateFlow<List<com.tao.autobook.data.ChatMessage>> = repository.observeChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val chatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = chatSending
    private val chatOpsResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val chatOperationEvents: SharedFlow<String> = chatOpsResult

    val openTransactionEvents: SharedFlow<Long> = openTransactionRequest
    val noticeEvents: SharedFlow<AutoBookNotice> = autoBookNotices

    // 将 6 层 combine 嵌套拆分为 3 组并行 combine + 1 次合并，避免深嵌套
    private data class BaseGroup(
        val txs: List<TransactionEntity>,
        val cats: List<CategoryEntity>,
        val pending: List<PendingScreenshotReview>,
        val isBusy: Boolean,
        val msg: String?
    )
    private data class AiSyncGroup(
        val rules: List<NotificationRuleEntity>,
        val ai: AiRecognitionSettings,
        val models: List<String>,
        val preview: Pair<Long, List<Bitmap>>?,
        val logList: List<AutoBookLogEntry>
    )
    private data class StatsGroup(
        val aiStats: String,
        val about: com.tao.autobook.data.AutoBookRepository.AboutInfo,
        val mExp: Long,
        val mInc: Long,
        val tExp: Long
    )

    private val baseGroup: Flow<BaseGroup> = combine(
        repository.transactions, repository.categories, pendingReviews, busy, message
    ) { txs, cats, pending, isBusy, msg ->
        BaseGroup(txs, cats, pending, isBusy, msg)
    }
    private val aiSyncGroup: Flow<AiSyncGroup> = combine(
        notificationRules, repository.aiSettings, aiModels, voucherPreview, logs
    ) { rules, ai, models, preview, logList ->
        AiSyncGroup(rules, ai, models, preview, logList)
    }
    private val statsGroup: Flow<StatsGroup> = combine(
        aiStatsFlow, aboutInfoFlow, monthExpenseFlow, monthIncomeFlow
    ) { statsStr, about, mExp, mInc ->
        StatsGroup(statsStr, about, mExp, mInc, 0L)
    }

    val state: StateFlow<AutoBookUiState> = combine(
        baseGroup, aiSyncGroup, statsGroup, todayExpenseFlow, todayIncomeFlow
    ) { base, aiSync, stats, tExp, tInc ->
        AutoBookUiState(
            transactions = base.txs,
            categories = base.cats.ifEmpty { BuiltInCategories.defaults },
            pending = base.pending,
            isBusy = base.isBusy,
            message = base.msg,
            screenshotBytes = repository.screenshotStorageBytes(),
            notificationRules = aiSync.rules,
            aiSettings = aiSync.ai,
            aiModels = aiSync.models,
            voucherPreviewTransactionId = aiSync.preview?.first,
            voucherPreviewBitmaps = aiSync.preview?.second ?: emptyList(),
            logs = aiSync.logList,
            aiStats = stats.aiStats,
            aboutInfo = stats.about,
            monthExpenseCents = stats.mExp,
            monthIncomeCents = stats.mInc,
            todayExpenseCents = tExp,
            todayIncomeCents = tInc
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBookUiState())

    init {
        viewModelScope.launch { repository.observeNotificationRules().collect { notificationRules.value = it } }
        viewModelScope.launch { repository.observeLogs().collect { logs.value = it } }
        fetchAboutInfo()
        // 月度/日度统计：每30秒刷新一次（数据库聚合查询）
                viewModelScope.launch {
                    while (true) {
                        runCatching {
                            val now = java.time.LocalDate.now()
                            // 「本月」按用户设置的月周期起始日算，而不是死板的自然月 1 号
                            val monthStart = currentMonthCycleStartMillis(repository.getMonthStartDay())
                            val todayStart = now.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            monthExpenseFlow.value = repository.getMonthExpense(monthStart)
                            monthIncomeFlow.value = repository.getMonthIncome(monthStart)
                            todayExpenseFlow.value = repository.getTodayExpense(todayStart)
                            todayIncomeFlow.value = repository.getTodayIncome(todayStart)
                        }
                        kotlinx.coroutines.delay(30_000L)
                    }
                }
        viewModelScope.launch {
            repository.pendingScreenshots.collect { pendingReviews.value = repository.buildPendingReviews(it) }
        }
        // 预算变化实时进报表状态
        viewModelScope.launch {
            repository.observeBudgets().collect { list ->
                reportState.value = reportState.value.copy(budgets = list)
            }
        }
        // 「全部」区间起点取最早一笔账单
        viewModelScope.launch {
            reportState.value = reportState.value.copy(monthStartDay = repository.getMonthStartDay())
            repository.getEarliestPaidAt()?.let { earliest ->
                val date = Instant.ofEpochMilli(earliest).atZone(ZoneId.systemDefault()).toLocalDate()
                reportState.value = reportState.value.copy(allStart = date)
            }
            refreshReport()
        }
        // 账单变化后自动重算报表
        viewModelScope.launch {
            repository.transactions.collect { refreshReport() }
        }
    }

    // ====== 报表 ======
    /** 按当前 period/type/anchor 重新聚合报表数据 */
    fun refreshReport() {
        viewModelScope.launch {
            val s = reportState.value
            reportState.value = s.copy(loading = true)
            val range = s.range
            val prev = s.prevRange
            val snapshot = repository.loadReport(
                start = range.start.startMillis(),
                end = range.endInclusive.endMillis(),
                type = s.type,
                prevStart = prev?.start?.startMillis(),
                prevEnd = prev?.endInclusive?.endMillis(),
            )
            val current = reportState.value
            reportState.value = current.copy(snapshot = snapshot, loading = false)
            // 下钻若已展开，同步刷新
            current.drillCategoryId?.let { loadDrillDown(it) }
        }
    }

    fun setReportPeriod(period: ReportPeriod) {
        if (reportState.value.period == period) return
        reportState.value = reportState.value.copy(period = period, anchor = LocalDate.now(), drillCategoryId = null, drill = com.tao.autobook.data.CategoryDrillDown())
        refreshReport()
    }

    fun setReportType(type: TransactionType) {
        if (reportState.value.type == type) return
        reportState.value = reportState.value.copy(type = type, drillCategoryId = null, drill = com.tao.autobook.data.CategoryDrillDown())
        refreshReport()
    }

    /** 周期翻页：step = -1 上一期，+1 下一期 */
    fun shiftReportPeriod(step: Long) {
        val s = reportState.value
        if (!s.pageable) return
        if (step > 0 && !s.canGoNext) return
        val anchor = when (s.period) {
            ReportPeriod.Week -> s.anchor.plusWeeks(step)
            ReportPeriod.Month -> s.anchor.plusMonths(step)
            ReportPeriod.Year -> s.anchor.plusYears(step)
            else -> s.anchor
        }
        reportState.value = s.copy(anchor = anchor, drillCategoryId = null, drill = com.tao.autobook.data.CategoryDrillDown())
        refreshReport()
    }

    fun setReportCustomRange(start: LocalDate, end: LocalDate) {
        val (from, to) = if (start.isAfter(end)) end to start else start to end
        reportState.value = reportState.value.copy(period = ReportPeriod.Custom, customStart = from, customEnd = to, drillCategoryId = null, drill = com.tao.autobook.data.CategoryDrillDown())
        refreshReport()
    }

    /** 点分类展开/收起下钻 */
    fun toggleReportDrill(categoryId: String) {
        val s = reportState.value
        if (s.drillCategoryId == categoryId) {
            reportState.value = s.copy(drillCategoryId = null, drill = com.tao.autobook.data.CategoryDrillDown())
        } else {
            reportState.value = s.copy(drillCategoryId = categoryId)
            loadDrillDown(categoryId)
        }
    }

    private fun loadDrillDown(categoryId: String) {
        viewModelScope.launch {
            val s = reportState.value
            val range = s.range
            val drill = repository.loadCategoryDrillDown(
                start = range.start.startMillis(),
                end = range.endInclusive.endMillis(),
                type = s.type,
                categoryId = categoryId,
            )
            if (reportState.value.drillCategoryId == categoryId) {
                reportState.value = reportState.value.copy(drill = drill)
            }
        }
    }

    /** 保存预算，amountText 为空或 0 表示删除该预算 */
    fun saveBudget(categoryId: String, amountText: String) {
        val cents = parseMoneyToCents(amountText) ?: 0L
        viewModelScope.launch {
            runCatching { repository.saveBudget(categoryId, cents) }
                .onFailure { message.value = it.message ?: "预算保存失败" }
        }
    }

    fun clearBudgets() {
        viewModelScope.launch {
            runCatching { repository.clearBudgets() }
                .onSuccess { message.value = "预算已清空" }
                .onFailure { message.value = it.message ?: "预算清空失败" }
        }
    }

    // ====== 不计入收支 / 不计入预算 ======
    /** 单独切换标记（列表长按等场景用，编辑弹窗走 updateTransactionFull） */
    fun updateExcludeFlags(id: Long, excludeStats: Boolean, excludeBudget: Boolean) {
        viewModelScope.launch {
            runCatching { repository.updateExcludeFlags(id, excludeStats, excludeBudget) }
                .onSuccess {
                    message.value = if (excludeStats) "已设为不计入收支" else "已恢复计入收支"
                    refreshReport()
                }
                .onFailure { message.value = it.message ?: "设置失败" }
        }
    }

    // ====== 月度周期起始日 ======
    fun getMonthStartDay(): Int = repository.getMonthStartDay()

    fun setMonthStartDay(day: Int) {
        repository.setMonthStartDay(day)
        reportState.value = reportState.value.copy(monthStartDay = repository.getMonthStartDay(), anchor = LocalDate.now())
        refreshReport()
        // 首页「本月」也要立刻跟着变
        viewModelScope.launch {
            runCatching {
                val monthStart = currentMonthCycleStartMillis(repository.getMonthStartDay())
                monthExpenseFlow.value = repository.getMonthExpense(monthStart)
                monthIncomeFlow.value = repository.getMonthIncome(monthStart)
            }
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
        voucherPreview.value?.second?.forEach { it.recycle() }
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

    fun clearAllPending() {
        viewModelScope.launch {
            busy.value = true
            val count = repository.clearAllPendingScreenshots()
            message.value = "已清空 $count 条待确认记录"
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

    /** 保存账单，同时落「不计入收支/不计入预算」标记，一次写库避免竞争 */
    fun updateTransactionFull(
        id: Long,
        merchant: String,
        amountText: String,
        categoryId: String,
        type: TransactionType,
        paidAt: Long,
        note: String,
        paymentApp: com.tao.autobook.data.PaymentApp,
        excludeFromStats: Boolean,
        excludeFromBudget: Boolean
    ) {
        viewModelScope.launch {
            val cents = parseMoneyToCents(amountText)
            if (cents == null || cents <= 0) {
                message.value = "请输入有效金额"
                return@launch
            }
            val displayName = merchant.ifBlank { note.trim().lineSequence().firstOrNull()?.take(24).orEmpty() }.ifBlank { type.defaultMerchant() }
            repository.updateTransaction(id, displayName, cents, categoryId, paidAt, note, type, paymentApp, excludeFromStats, excludeFromBudget)
            message.value = if (excludeFromStats) "已保存，该笔不计入收支" else "账单已保存"
            refreshReport()
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
            repository.deleteTransactionsWithImages(ids)
            message.value = "已删除 ${ids.size} 笔账单"
        }
    }

    fun saveCategory(existing: CategoryEntity?, name: String, type: TransactionType, color: Long, icon: String, parentId: String? = null) {
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
                    isDefault = existing?.isDefault ?: false,
                    parentId = parentId ?: existing?.parentId
                )
            )
            message.value = "分类已保存"
        }
    }

    /** 分类上移/下移，改同级 sortOrder */
    fun moveCategory(category: CategoryEntity, up: Boolean) {
        viewModelScope.launch {
            val ok = runCatching { repository.moveCategory(category.id, up) }.getOrDefault(false)
            if (!ok) message.value = if (up) "已经是第一个了" else "已经是最后一个了"
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

    fun sendChatMessage(message: String, imageUri: String? = null, fileName: String? = null) {
        if (message.isBlank() && imageUri == null) return
        viewModelScope.launch {
            chatSending.value = true
            val reply = repository.sendChatMessage(message, imageUri, fileName)
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

    fun isNotificationAutoBookEnabled(): Boolean = repository.isNotificationAutoBookEnabled()

    fun setNotificationAutoBookEnabled(enabled: Boolean) {
        repository.setNotificationAutoBookEnabled(enabled)
        message.value = if (enabled) "已开启通知自动记账" else "已关闭通知自动记账（截图记账不受影响）"
    }

    fun isHideFromRecentsEnabled(): Boolean = repository.isHideFromRecentsEnabled()

    fun setHideFromRecentsEnabled(enabled: Boolean) {
        repository.setHideFromRecentsEnabled(enabled)
        message.value = if (enabled) "已在最近任务中隐藏本应用" else "已在最近任务中显示本应用"
    }

    fun isAutoDeleteScreenshotEnabled(): Boolean = repository.isAutoDeleteScreenshotEnabled()

    fun setAutoDeleteScreenshotEnabled(enabled: Boolean) {
        repository.setAutoDeleteScreenshotEnabled(enabled)
        message.value = if (enabled) "已开启记账后自动删除截图" else "已关闭自动删除截图"
    }

    fun fetchAboutInfo() {
        viewModelScope.launch {
            aboutInfoFlow.value = repository.fetchAboutInfo()
        }
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
