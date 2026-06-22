package com.tao.autobook.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.tao.autobook.ai.AiParsedPayment
import com.tao.autobook.ai.AiRecognitionConfig
import com.tao.autobook.ai.AiRecognitionSettings
import com.tao.autobook.ai.AiScreenshotRecognizer
import com.tao.autobook.ai.AiSettingsStore
import com.tao.autobook.data.AutoBookLogEntry
import com.tao.autobook.data.NotificationRuleEntity
import com.tao.autobook.data.NotificationMatchType
import com.tao.autobook.ocr.OcrRecognizer
import com.tao.autobook.parser.BillImportParser
import com.tao.autobook.parser.CategoryClassifier
import com.tao.autobook.parser.PaymentTextParser
import com.tao.autobook.parser.stableSha256
import com.tao.autobook.storage.CryptoStore
import com.tao.autobook.storage.ScreenshotStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId

private const val LOCAL_AUTO_CONFIRM_CONFIDENCE = 0.78f
private const val AI_AUTO_IMPORT_CONFIDENCE = 0.50f
private const val DEDUP_WINDOW_MS: Long = 300_000L
private const val LOG_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000

class AutoBookRepository(
    private val context: Context,
    private val dao: AutoBookDao,
    private val chatDao: ChatDao,
    private val parser: PaymentTextParser = PaymentTextParser(),
    private val billImportParser: BillImportParser = BillImportParser(),
    private val classifier: CategoryClassifier = CategoryClassifier(),
    private val screenshotStorage: ScreenshotStorage = ScreenshotStorage(context),
    private val cryptoStore: CryptoStore = CryptoStore(),
    private val ocrRecognizer: OcrRecognizer = OcrRecognizer(),
    private val aiSettingsStore: AiSettingsStore = AiSettingsStore(context),
    private val aiRecognizer: AiScreenshotRecognizer = AiScreenshotRecognizer()
) {

    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val pendingScreenshots: Flow<List<ScreenshotCaptureEntity>> = dao.observeScreenshotsByStatus(ScreenshotStatus.PENDING_REVIEW)
    val aiSettings: Flow<AiRecognitionSettings> = aiSettingsStore.settings

    suspend fun initialize() {
        dao.seedCategoriesIfEmpty()
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = withContext(Dispatchers.IO) { dao.getTransaction(id) }

    // 通知去重缓存：content hash -> timestamp
    private val notifDedupeCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun isDuplicateNotification(raw: String): Boolean {
        val now = System.currentTimeMillis()
        val hash = stableSha256(raw).take(16)
        val lastSeen = notifDedupeCache[hash]
        if (lastSeen != null && now - lastSeen < 60_000L) return true
        notifDedupeCache[hash] = now
        // 清理过期条目
        if (notifDedupeCache.size > 200) {
            notifDedupeCache.entries.removeIf { now - it.value > 120_000L }
        }
        return false
    }

    suspend fun captureNotification(packageName: String?, title: String?, text: String?): CaptureResult = withContext(Dispatchers.IO) {
        val raw = listOfNotNull(title, text).joinToString(" ").trim()
        if (raw.isBlank()) return@withContext CaptureResult(null, false)

        // 过滤App自己的通知
        if (packageName == context.packageName) {
            return@withContext CaptureResult(null, false)
        }

        // 通知去重：60秒内相同内容不重复处理
        if (isDuplicateNotification(raw)) {
            return@withContext CaptureResult(null, false)
        }

        val app = PaymentApp.fromPackage(packageName)
        val aiConfig = aiSettingsStore.loadConfig()

        // ====== AI开启：先本地预过滤，再过AI ======
        if (aiConfig.configured) {
            // 本地预过滤：不含支付关键词的通知直接跳过，不浪费AI调用
            // 负过滤：含这些词的通知即使有支付关键词也不是真实消费
            // 白名单：从SharedPreferences读取，用户可自由增删
            val allKeywords = getWhitelist()
            if (allKeywords.none { raw.contains(it) }) {
                return@withContext CaptureResult(null, false)
            }

            // 本地解析作为预处理（提取金额/商户供AI参考）
            val localParsed = parser.parse(raw, packageName)

            // 调用AI分析
            val aiResult = recognizeNotificationWithAi(raw, localParsed ?: ParsedPayment(
                amountCents = 0, merchantName = "", paymentApp = app,
                paidAt = System.currentTimeMillis(), confidence = 0f,
                rawText = raw, type = TransactionType.EXPENSE
            )).getOrNull()

            if (aiResult == null) {
                addLog("通知监听", "AI调用失败，跳过", raw.take(100))
                dao.insertRawCapture(RawCaptureEntity(
                    sourceType = SourceType.NOTIFICATION, paymentApp = app,
                    capturedAt = System.currentTimeMillis(),
                    titleHash = stableSha256(title.orEmpty()),
                    textHash = stableSha256(text.orEmpty())
                ))
                return@withContext CaptureResult(null, false)
            }

            // 先检查isSpam（不需要amount）
            if (aiResult.isSpam) {
                addLog("通知监听", "AI判定为垃圾/非消费", raw.take(100))
                return@withContext CaptureResult(null, false)
            }

            // 再转为ParsedPayment
            val parsed = aiResult.toLocalParsed(
                localParsed ?: ParsedPayment(
                    amountCents = 0, merchantName = "", paymentApp = app,
                    paidAt = System.currentTimeMillis(), confidence = 0f,
                    rawText = raw, type = TransactionType.EXPENSE
                ), raw
            )
            if (parsed == null) {
                addLog("通知监听", "AI未返回有效数据，跳过", raw.take(100))
                return@withContext CaptureResult(null, false)
            }

            // AI模式下也检查规则库
            val rules = dao.getEnabledNotificationRules()
            val matchedRule = rules.firstOrNull { rule ->
                val keyword = rule.keyword.lowercase()
                val matchText = raw.lowercase()
                when (rule.matchType) {
                    NotificationMatchType.CONTAINS -> matchText.contains(keyword)
                    NotificationMatchType.EXACT -> matchText == keyword
                }
            }
            val finalParsed = if (matchedRule != null) {
                parsed.copy(categoryHint = matchedRule.categoryId)
            } else parsed

            val result = upsertParsedPayment(finalParsed, SourceType.NOTIFICATION, null)
            if (result.created) {
                addLog("通知监听", "AI自动记账成功", "${finalParsed.merchantName} ¥${finalParsed.amountCents / 100.0} [${finalParsed.paymentApp.label}]")
            }
            dao.insertRawCapture(RawCaptureEntity(
                sourceType = SourceType.NOTIFICATION, paymentApp = aiResult.paymentApp,
                capturedAt = System.currentTimeMillis(),
                titleHash = stableSha256(title.orEmpty()),
                textHash = stableSha256(text.orEmpty()),
                parsedTransactionId = result.transaction?.id
            ))
            return@withContext result
        }

        // ====== AI关闭：本地模式 ======
        // 过滤垃圾短信
        if (app == PaymentApp.UNKNOWN) {
            if (!isBankOrPaymentNotification(raw)) {
                addLog("通知监听", "过滤垃圾短信", raw.take(200))
                return@withContext CaptureResult(null, false)
            }
        }

        val localParsed = parser.parse(raw, packageName) ?: run {
            dao.insertRawCapture(RawCaptureEntity(
                sourceType = SourceType.NOTIFICATION, paymentApp = app,
                capturedAt = System.currentTimeMillis(),
                titleHash = stableSha256(title.orEmpty()),
                textHash = stableSha256(text.orEmpty())
            ))
            return@withContext CaptureResult(null, false)
        }

        // 已知App也检查垃圾关键词
        val spamWords = listOf("花呗账单", "白条账单", "信用卡账单", "点击还款", "还款提醒", "账单提醒", "红包待领取", "转入零钱通")
        if (spamWords.any { raw.contains(it) }) {
            addLog("通知监听", "垃圾过滤", raw.take(100))
            return@withContext CaptureResult(null, false)
        }

        // 低置信度过滤
        if (app == PaymentApp.UNKNOWN && localParsed.confidence < 0.5f) {
            addLog("通知监听", "低置信度过滤", raw.take(200))
            return@withContext CaptureResult(null, false)
        }

        // 通知规则库匹配
        val rules = dao.getEnabledNotificationRules()
        val matchedRule = rules.firstOrNull { rule ->
            val keyword = rule.keyword.lowercase()
            val matchText = raw.lowercase()
            when (rule.matchType) {
                NotificationMatchType.CONTAINS -> matchText.contains(keyword)
                NotificationMatchType.EXACT -> matchText == keyword
            }
        }
        if (matchedRule != null) {
            val overridden = localParsed.copy(categoryHint = matchedRule.categoryId)
            val result = upsertParsedPayment(overridden, SourceType.NOTIFICATION, null)
            if (result.created) addLog("通知监听", "规则匹配自动记账", "${overridden.merchantName} ¥${overridden.amountCents / 100.0}")
            dao.insertRawCapture(RawCaptureEntity(
                sourceType = SourceType.NOTIFICATION, paymentApp = overridden.paymentApp,
                capturedAt = System.currentTimeMillis(),
                titleHash = stableSha256(title.orEmpty()),
                textHash = stableSha256(text.orEmpty()),
                parsedTransactionId = result.transaction?.id
            ))
            return@withContext result
        }

        // 本地解析入账
        val result = upsertParsedPayment(localParsed, SourceType.NOTIFICATION, null)
        if (result.created) {
            addLog("通知监听", "本地自动记账成功", "${localParsed.merchantName} ¥${localParsed.amountCents / 100.0} [${localParsed.paymentApp.label}]")
        }
        dao.insertRawCapture(RawCaptureEntity(
            sourceType = SourceType.NOTIFICATION, paymentApp = localParsed.paymentApp,
            capturedAt = System.currentTimeMillis(),
            titleHash = stableSha256(title.orEmpty()),
            textHash = stableSha256(text.orEmpty()),
            parsedTransactionId = result.transaction?.id
        ))
        result
    }


    suspend fun importScreenshot(uri: Uri): Long = withContext(Dispatchers.IO) {
        val stored = screenshotStorage.saveEncryptedFromUri(uri)
        val bitmap = screenshotStorage.loadBitmap(stored.encryptedPath)
        val ocrText = bitmap?.let { ocrRecognizer.recognize(it) }.orEmpty()
        createScreenshotRecord(stored.encryptedPath, ScreenshotSourceType.MANUAL_UPLOAD, ocrText, bitmap).first
    }

    suspend fun attachScreenshotToTransaction(transactionId: Long, uri: Uri): Long = withContext(Dispatchers.IO) {
        val transaction = dao.getTransaction(transactionId) ?: error("账单不存在")
        val stored = screenshotStorage.saveEncryptedFromUri(uri)
        val voucherText = "凭证图片"
        val screenshotId = dao.insertScreenshot(
            ScreenshotCaptureEntity(
                encryptedFilePath = stored.encryptedPath,
                sourceType = ScreenshotSourceType.MANUAL_UPLOAD,
                capturedAt = System.currentTimeMillis(),
                ocrTextHash = stableSha256(voucherText),
                ocrRawTextEncrypted = cryptoStore.encryptToString(voucherText),
                parsedTransactionId = transactionId,
                status = ScreenshotStatus.CONFIRMED
            )
        )
        dao.updateTransaction(transaction.copy(screenshotId = screenshotId, updatedAt = System.currentTimeMillis()))
        screenshotId
    }

    suspend fun loadTransactionBitmaps(transactionId: Long): List<Bitmap> = withContext(Dispatchers.IO) {
        dao.getScreenshotsByTransactionId(transactionId).mapNotNull { screenshotStorage.loadBitmap(it.encryptedFilePath) }
    }

    suspend fun loadTransactionScreenshot(transactionId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val screenshotId = dao.getTransaction(transactionId)?.screenshotId ?: return@withContext null
        val screenshot = dao.getScreenshot(screenshotId) ?: return@withContext null
        screenshotStorage.loadBitmap(screenshot.encryptedFilePath)
    }

    suspend fun importAutoScreenshot(bitmap: Bitmap): CaptureResult = withContext(Dispatchers.IO) {
        val stored = screenshotStorage.saveEncryptedBitmap(bitmap)
        val ocrText = ocrRecognizer.recognize(bitmap)
        createScreenshotRecord(stored.encryptedPath, ScreenshotSourceType.AUTO_CAPTURE, ocrText, bitmap).second
    }

    suspend fun captureFromAccessibility(
        appName: String,
        pageText: String,
        amountCents: Long,
        merchant: String,
        isRefund: Boolean
    ): CaptureResult = withContext(Dispatchers.IO) {
        val app = PaymentApp.entries.firstOrNull { it.name == appName } ?: PaymentApp.UNKNOWN
        val type = if (isRefund) TransactionType.INCOME else TransactionType.EXPENSE
        val now = System.currentTimeMillis()

        // 生成去重键
        val dedupe = stableSha256(listOf(app.name, type.name, amountCents, merchant, now / (DEDUP_WINDOW_MS)).joinToString("|"))

        // 检查是否重复
        dao.findByDedupeKey(dedupe)?.let { existing ->
            return@withContext CaptureResult(existing, false)
        }

        // 检查跨平台相似账单（5分钟窗口）
        val from = now - DEDUP_WINDOW_MS
        val to = now + DEDUP_WINDOW_MS
        val similar = dao.findSimilarAutoAnyApp(amountCents, type, from, to, automaticSources)
        if (similar != null) {
            // 无障碍抓到更完整信息时，补充通知监听的记录
            val updated = supplementTransaction(similar, merchant, pageText)
            if (updated != null) {
                dao.updateTransaction(updated)
                addLog("无障碍服务", "补充通知记录", "${merchant} ¥${amountCents / 100.0}")
            }
            return@withContext CaptureResult(similar, false)
        }

        // 扩大窗口检查：同金额同类型30分钟内不重复（银行短信延迟场景）
        val extendedFrom = now - 30 * 60 * 1000L
        val extendedTo = now + 30 * 60 * 1000L
        val extendedSimilar = dao.findSimilarAutoAnyApp(amountCents, type, extendedFrom, extendedTo, automaticSources)
        if (extendedSimilar != null) {
            val updated = supplementTransaction(extendedSimilar, merchant, pageText)
            if (updated != null) {
                dao.updateTransaction(updated)
                addLog("无障碍服务", "补充通知记录", "${merchant} ¥${amountCents / 100.0}")
            }
            return@withContext CaptureResult(extendedSimilar, false)
        }

        // 分类
        val rules = dao.getMerchantRules()
        val categoryId = when (type) {
            TransactionType.EXPENSE -> classifier.classify(merchant, pageText, rules, app)
            TransactionType.INCOME -> classifyIncome(merchant, pageText)
            TransactionType.OTHER -> BuiltInCategories.OTHER
        }

        // 创建账单
        val id = dao.insertTransaction(
            TransactionEntity(
                amountCents = amountCents,
                merchantName = merchant,
                categoryId = categoryId,
                paymentApp = app,
                paidAt = now,
                sourceType = SourceType.ACCESSIBILITY,
                dedupeKey = dedupe,
                confidence = 0.85f,
                note = "",
                createdAt = now,
                updatedAt = now,
                type = type
            )
        )

        val transaction = dao.getTransaction(id)
        addLog("无障碍服务", "自动记账成功", "$merchant ¥${amountCents / 100.0} [${app.label}]")
        CaptureResult(transaction, transaction != null)
    }

    suspend fun saveAiSettings(settings: AiRecognitionSettings, apiKey: String?) = withContext(Dispatchers.IO) {
        aiSettingsStore.save(settings, apiKey)
    }

    suspend fun testAiSettings(settings: AiRecognitionSettings, apiKey: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val current = aiSettingsStore.loadConfig()
        val config = current.copy(
            enabled = settings.enabled,
            apiUrl = settings.apiUrl.trim(),
            model = settings.model.trim(),
            apiKey = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: current.apiKey,
            timeoutSeconds = settings.timeoutSeconds.coerceIn(8, 90)
        )
        aiRecognizer.testConnection(config)
    }

    suspend fun fetchAiModels(settings: AiRecognitionSettings, apiKey: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        val current = aiSettingsStore.loadConfig()
        val key = apiKey?.trim()?.takeIf { it.isNotBlank() } ?: current.apiKey
        aiRecognizer.listModels(settings.apiUrl.trim(), key, settings.timeoutSeconds)
    }

    suspend fun clearUnconfirmedScreenshotCache(): Int = withContext(Dispatchers.IO) {
        val screenshots = dao.getUnconfirmedScreenshots()
        screenshots.forEach { screenshotStorage.delete(it.encryptedFilePath) }
        dao.deleteUnconfirmedScreenshots()
        screenshots.size
    }

    suspend fun confirmScreenshot(screenshotId: Long, merchant: String, amountCents: Long, categoryId: String, paidAt: Long): Long {
        val screenshot = dao.getScreenshot(screenshotId) ?: error("截图不存在")
        val now = System.currentTimeMillis()
        val payment = parser.parse(cryptoStore.decryptFromString(screenshot.ocrRawTextEncrypted))
        val app = payment?.paymentApp ?: PaymentApp.UNKNOWN
        val dedupe = stableSha256(listOf(app.name, amountCents, merchant, paidAt / (DEDUP_WINDOW_MS)).joinToString("|"))
        val existing = dao.findByDedupeKey(dedupe)
        val txId = existing?.id ?: dao.insertTransaction(
            TransactionEntity(
                amountCents = amountCents,
                merchantName = merchant.ifBlank { "未命名消费" },
                categoryId = categoryId,
                paymentApp = app,
                paidAt = paidAt,
                sourceType = SourceType.SCREENSHOT,
                screenshotId = screenshotId,
                dedupeKey = dedupe,
                confidence = 0.9f,
                createdAt = now,
                updatedAt = now,
                type = TransactionType.EXPENSE
            )
        )
        dao.updateScreenshot(screenshot.copy(parsedTransactionId = txId, status = ScreenshotStatus.CONFIRMED))
        learnMerchantRule(merchant, categoryId, app)
        return txId
    }

    suspend fun ignorePendingScreenshot(screenshotId: Long) = withContext(Dispatchers.IO) {
        val screenshot = dao.getScreenshot(screenshotId) ?: return@withContext
        if (screenshot.status == ScreenshotStatus.PENDING_REVIEW) {
            dao.updateScreenshot(screenshot.copy(status = ScreenshotStatus.IGNORED))
        }
    }

    suspend fun buildPendingReviews(items: List<ScreenshotCaptureEntity>): List<PendingScreenshotReview> = withContext(Dispatchers.IO) {
        val rules = dao.getMerchantRules()
        items.map { screenshot ->
            val rawText = runCatching { cryptoStore.decryptFromString(screenshot.ocrRawTextEncrypted) }.getOrDefault("")
            val ai = extractAiSuggestion(rawText)
            val parsed = ai?.toParsedPayment() ?: parser.parse(rawText)
            val category = parsed?.let { chooseCategory(it, rules) } ?: BuiltInCategories.OTHER
            PendingScreenshotReview(
                id = screenshot.id,
                capturedAt = screenshot.capturedAt,
                sourceType = screenshot.sourceType,
                ocrPreview = buildPendingPreview(rawText, ai),
                suggestedMerchant = parsed?.merchantName?.takeIf { it != "未识别商户" } ?: "截图消费",
                suggestedAmountCents = parsed?.amountCents,
                suggestedCategoryId = category,
                suggestedPaymentApp = parsed?.paymentApp ?: PaymentApp.UNKNOWN,
                confidence = parsed?.confidence ?: 0.2f
            )
        }
    }

    suspend fun addManualTransaction(
        merchant: String,
        amountCents: Long,
        categoryId: String,
        paidAt: Long = System.currentTimeMillis(),
        type: TransactionType = TransactionType.EXPENSE,
        note: String = "",
        paymentApp: PaymentApp = PaymentApp.UNKNOWN
    ): Long {
        val now = System.currentTimeMillis()
        val dedupe = stableSha256(listOf(paymentApp.name, type.name, amountCents, merchant, paidAt / (DEDUP_WINDOW_MS), "MANUAL").joinToString("|"))
        dao.findByDedupeKey(dedupe)?.let { return it.id }
        val id = dao.insertTransaction(
            TransactionEntity(
                amountCents = amountCents,
                merchantName = merchant.ifBlank { if (type == TransactionType.INCOME) "手动收入" else "手动消费" },
                categoryId = categoryId.ifBlank { BuiltInCategories.fallbackFor(type) },
                paymentApp = paymentApp,
                paidAt = paidAt,
                sourceType = SourceType.MANUAL,
                dedupeKey = dedupe,
                confidence = 1f,
                note = note,
                createdAt = now,
                updatedAt = now,
                type = type
            )
        )
        return id
    }

    suspend fun updateTransaction(
        id: Long,
        merchant: String,
        amountCents: Long,
        categoryId: String,
        paidAt: Long,
        note: String,
        type: TransactionType
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getTransaction(id) ?: return@withContext
        val updated = existing.copy(
            merchantName = merchant.ifBlank { existing.merchantName },
            amountCents = amountCents,
            categoryId = categoryId.ifBlank { BuiltInCategories.fallbackFor(type) },
            paidAt = paidAt,
            note = note,
            type = type,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateTransaction(updated)
        learnMerchantRule(updated.merchantName, updated.categoryId, updated.paymentApp)
    }

    suspend fun updateTransaction(
        id: Long,
        merchant: String,
        amountCents: Long,
        categoryId: String,
        paidAt: Long,
        note: String,
        type: TransactionType,
        paymentApp: PaymentApp
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getTransaction(id) ?: return@withContext
        val updated = existing.copy(
            merchantName = merchant.ifBlank { existing.merchantName },
            amountCents = amountCents,
            categoryId = categoryId.ifBlank { BuiltInCategories.fallbackFor(type) },
            paymentApp = paymentApp,
            paidAt = paidAt,
            note = note,
            type = type,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateTransaction(updated)
        learnMerchantRule(updated.merchantName, updated.categoryId, paymentApp)
    }

    suspend fun updateTransaction(entity: TransactionEntity) {
        dao.updateTransaction(entity.copy(updatedAt = System.currentTimeMillis()))
        learnMerchantRule(entity.merchantName, entity.categoryId, entity.paymentApp)
    }

    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)
    suspend fun deleteTransactionWithImages(id: Long) = withContext(Dispatchers.IO) {
        val screenshots = dao.getScreenshotsByTransactionId(id)
        screenshots.forEach { screenshotStorage.delete(it.encryptedFilePath) }
        dao.deleteScreenshotsByTransactionId(id)
        dao.deleteTransaction(id)
    }

    suspend fun upsertCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        dao.upsertCategory(category)
    }

    suspend fun deleteCategoryAndMoveTransactions(categoryId: String) = withContext(Dispatchers.IO) {
        val category = dao.getCategory(categoryId) ?: return@withContext
        if (category.id == BuiltInCategories.OTHER) return@withContext
        val fallback = BuiltInCategories.fallbackFor(category.type)
        if (category.id == fallback) return@withContext
        dao.deleteCategoryAndMoveTransactions(category.id, fallback)
    }

    fun screenshotStorageBytes(): Long = screenshotStorage.totalBytes()

    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(exportDir, "autobook-${System.currentTimeMillis()}.csv")
        val categories = dao.getCategories().associateBy { it.id }
        val rows = buildString {
            appendLine("id,paid_at,type,amount,currency,merchant,category,payment_app,source,note")
            dao.getTransactions().forEach { tx ->
                appendCsv(tx.id.toString())
                append(',')
                appendCsv(Instant.ofEpochMilli(tx.paidAt).atZone(ZoneId.systemDefault()).toString())
                append(',')
                appendCsv(tx.type.label)
                append(',')
                appendCsv("%.2f".format(tx.amount))
                append(',')
                appendCsv(tx.currency)
                append(',')
                appendCsv(tx.merchantName)
                append(',')
                appendCsv(categories[tx.categoryId]?.name ?: tx.categoryId)
                append(',')
                appendCsv(tx.paymentApp.label)
                append(',')
                appendCsv(tx.sourceType.name)
                append(',')
                appendCsv(tx.note)
                append('\n')
            }
        }
        file.writeText("\uFEFF" + rows, Charsets.UTF_8)
        file
    }

    suspend fun importBillFile(uri: Uri): BillImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val lowerName = name.lowercase()
        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase()
        if (lowerName.endsWith(".xls") || lowerName.endsWith(".pdf") || mimeType.contains("pdf")) {
            return@withContext BillImportResult(unsupportedCount = 1)
        }

        val text = if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || mimeType.contains("spreadsheet")) {
            val xlsxBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext BillImportResult(failedCount = 1)
            parseXlsxToText(xlsxBytes)
        } else {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext BillImportResult(failedCount = 1)
            decodeBillText(bytes)
        }

        // AI模式：调用AI分析账单
        val aiConfig = aiSettingsStore.loadConfig()
        if (aiConfig.configured) {
            return@withContext importBillWithAi(text, name, aiConfig)
        }

        // 本地模式：规则解析
        val parsed = billImportParser.parse(text, name)
        var success = 0
        var duplicate = 0
        parsed.rows.forEach { row ->
            if (addImportedTransaction(row)) success++ else duplicate++
        }
        BillImportResult(success, duplicate, parsed.failedCount)
    }

    private suspend fun importBillWithAi(text: String, name: String, config: AiRecognitionConfig): BillImportResult {
        val aiRows = aiRecognizer.recognizeBillImport(text, config).getOrNull()
        if (aiRows.isNullOrEmpty()) {
            // AI失败，回退到本地解析
            addLog("账单导入", "AI解析失败，回退本地模式", name)
            val parsed = billImportParser.parse(text, name)
            var success = 0
            var duplicate = 0
            parsed.rows.forEach { row -> if (addImportedTransaction(row)) success++ else duplicate++ }
            return BillImportResult(success, duplicate, parsed.failedCount)
        }

        var success = 0
        var duplicate = 0
        aiRows.forEach { row ->
            val txType = when (row.type.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                "OTHER" -> TransactionType.OTHER
                else -> TransactionType.EXPENSE
            }
            val app = try { PaymentApp.valueOf(row.paymentApp.uppercase()) } catch (_: Exception) { PaymentApp.UNKNOWN }
            val paidAt = parseAiDateTime(row.paidAt)
            val merchant = row.merchantName.takeIf { it.isNotBlank() } ?: "AI导入消费"
            val imported = ImportedBillRow(
                amountCents = row.amountCents,
                merchantName = merchant,
                paidAt = paidAt,
                paymentApp = app,
                type = txType,
                note = row.note.ifBlank { row.categoryHint },
                rawText = row.toString()
            )
            if (addImportedTransaction(imported)) success++ else duplicate++
        }
        addLog("账单导入", "AI导入完成", "成功${success}笔 重复${duplicate}笔")
        return BillImportResult(success, duplicate, 0)
    }

    private fun parseAiDateTime(text: String): Long {
        val formats = listOf(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
        )
        for (fmt in formats) {
            runCatching {
                return java.time.LocalDateTime.parse(text.trim(), fmt)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return System.currentTimeMillis()
    }

    suspend fun importBillFiles(uris: List<Uri>): BillImportResult = withContext(Dispatchers.IO) {
        uris.fold(BillImportResult()) { acc, uri -> acc + importBillFile(uri) }
    }

    private suspend fun addImportedTransaction(row: ImportedBillRow): Boolean {
        val merchant = row.merchantName.ifBlank { if (row.type == TransactionType.INCOME) "导入收入" else "导入消费" }
        val dedupe = stableSha256(listOf(row.paymentApp.name, row.type.name, row.amountCents, merchant.lowercase(), row.paidAt / (DEDUP_WINDOW_MS)).joinToString("|")).take(32)
        dao.findByDedupeKey(dedupe)?.let { return false }
        dao.findSimilar(row.paymentApp, row.amountCents, row.type, row.paidAt - DEDUP_WINDOW_MS, row.paidAt + DEDUP_WINDOW_MS)?.let { return false }
        val rules = dao.getMerchantRules()
        val categoryId = when (row.type) {
            TransactionType.EXPENSE -> classifier.classify(merchant, row.rawText, rules, row.paymentApp)
            TransactionType.INCOME -> classifyIncome(merchant, row.rawText)
            TransactionType.OTHER -> BuiltInCategories.OTHER
        }
        val now = System.currentTimeMillis()
        val id = dao.insertTransaction(
            TransactionEntity(
                amountCents = row.amountCents,
                merchantName = merchant,
                categoryId = categoryId,
                paymentApp = row.paymentApp,
                paidAt = row.paidAt,
                sourceType = SourceType.MANUAL,
                dedupeKey = dedupe,
                confidence = 0.86f,
                note = row.note,
                createdAt = now,
                updatedAt = now,
                type = row.type
            )
        )
        return id > 0
    }

    private suspend fun createScreenshotRecord(path: String, source: ScreenshotSourceType, ocrText: String, bitmap: Bitmap? = null): Pair<Long, CaptureResult> {
        val aiConfig = aiSettingsStore.loadConfig()

        // 截图补记一律使用AI识别
        val aiAttempt = if (bitmap != null) recognizeWithAi(bitmap, ocrText) else null
        val ai = aiAttempt?.getOrNull()

        val storedText = combineOcrAndAi(ocrText, ai, aiAttempt?.exceptionOrNull()?.message)
        val screenshotId = dao.insertScreenshot(
            ScreenshotCaptureEntity(
                encryptedFilePath = path,
                sourceType = source,
                capturedAt = System.currentTimeMillis(),
                ocrTextHash = stableSha256(storedText),
                ocrRawTextEncrypted = cryptoStore.encryptToString(storedText),
                status = ScreenshotStatus.PENDING_REVIEW
            )
        )

        // AI成功识别且可自动入账
        val parsed = if (ai != null && ai.canAutoImport()) {
            ai.toParsedPayment()
        } else {
            // AI未配置或失败时，使用本地OCR解析
            parser.parse(ocrText)
        }

        if (parsed != null && (ai != null || parser.parse(ocrText)?.confidence ?: 0f >= LOCAL_AUTO_CONFIRM_CONFIDENCE)) {
            val result = upsertParsedPayment(parsed, SourceType.SCREENSHOT, screenshotId)
            dao.getScreenshot(screenshotId)?.let { dao.updateScreenshot(it.copy(parsedTransactionId = result.transaction?.id, status = ScreenshotStatus.CONFIRMED)) }
            if (result.created) {
                addLog("截图补记", "自动记账成功", "${parsed.merchantName} ¥${parsed.amountCents / 100.0}")
            }
            return screenshotId to result
        }
        return screenshotId to CaptureResult(null, false)
    }

    private suspend fun upsertParsedPayment(parsed: ParsedPayment, sourceType: SourceType, screenshotId: Long?): CaptureResult {
        val dedupe = parser.dedupeKey(parsed)
        dao.findByDedupeKey(dedupe)?.let { existing ->
            val updated = if (screenshotId != null && existing.screenshotId == null) {
                existing.copy(screenshotId = screenshotId, updatedAt = System.currentTimeMillis())
            } else existing
            if (updated != existing) dao.updateTransaction(updated)
            return CaptureResult(updated, false)
        }
        val from = parsed.paidAt - DEDUP_WINDOW_MS
        val to = parsed.paidAt + DEDUP_WINDOW_MS
        val similar = dao.findSimilar(parsed.paymentApp, parsed.amountCents, parsed.type, from, to)
            ?: if (sourceType in automaticSources) dao.findSimilarAutoAnyApp(parsed.amountCents, parsed.type, from, to, automaticSources) else null
        if (similar != null) {
            if (screenshotId != null && similar.screenshotId == null) {
                val updated = similar.copy(screenshotId = screenshotId, updatedAt = System.currentTimeMillis())
                dao.updateTransaction(updated)
                return CaptureResult(updated, false)
            }
            return CaptureResult(similar, false)
        }

        val rules = dao.getMerchantRules()
        val category = chooseCategory(parsed, rules)
        val now = System.currentTimeMillis()
        val id = dao.insertTransaction(
            TransactionEntity(
                amountCents = parsed.amountCents,
                merchantName = parsed.merchantName,
                categoryId = category,
                paymentApp = parsed.paymentApp,
                paidAt = parsed.paidAt,
                sourceType = sourceType,
                screenshotId = screenshotId,
                dedupeKey = dedupe,
                confidence = parsed.confidence,
                note = parsed.categoryHint.takeIf { it.isNotBlank() }.orEmpty(),
                createdAt = now,
                updatedAt = now,
                type = parsed.type
            )
        )
        val transaction = dao.getTransaction(id)
        return CaptureResult(transaction, transaction != null)
    }

    private suspend fun supplementTransaction(existing: TransactionEntity, newMerchant: String, pageText: String): TransactionEntity? {
        var updated = existing
        var changed = false

        // 补充商户名：如果现有记录商户名是"未知"或"导入消费"等默认值
        if (newMerchant.isNotBlank() && newMerchant.length >= 2 &&
            (existing.merchantName == "未知" || existing.merchantName == "导入消费" ||
             existing.merchantName == "未识别商户" || existing.merchantName == "导入收入" ||
             existing.merchantName.startsWith("微信支付") || existing.merchantName.startsWith("支付宝") ||
             existing.merchantName.startsWith("京东支付"))) {
            updated = updated.copy(merchantName = newMerchant)
            changed = true
        }

        // 补充分类：如果现有分类是"其他"且页面信息能推断出更好的分类
        if (existing.categoryId == BuiltInCategories.OTHER && pageText.isNotBlank()) {
            val rules = dao.getMerchantRules()
            val betterCategory = classifier.classify(newMerchant, pageText, rules, existing.paymentApp)
            if (betterCategory != BuiltInCategories.OTHER) {
                updated = updated.copy(categoryId = betterCategory)
                changed = true
            }
        }

        return if (changed) updated.copy(updatedAt = System.currentTimeMillis()) else null
    }

    private suspend fun learnMerchantRule(merchant: String, categoryId: String, app: PaymentApp) {
        if (merchant.isBlank()) return
        dao.upsertMerchantRule(MerchantRuleEntity(keyword = merchant, categoryId = categoryId, paymentApp = app, priority = 100, createdByUser = true))
    }

    private suspend fun recognizeWithAi(bitmap: Bitmap, ocrText: String): Result<AiParsedPayment>? {
        val config = aiSettingsStore.loadConfig()
        if (!config.configured) return null
        return aiRecognizer.recognize(bitmap, config, ocrText)
    }

    private suspend fun recognizeNotificationWithAi(rawText: String, fallback: ParsedPayment): Result<AiParsedPayment?> {
        val config = aiSettingsStore.loadConfig()
        if (!config.configured) return Result.success(null)
        repeat(3) { attempt ->
            try {
                return aiRecognizer.recognizePaymentText(rawText, config).map { ai -> ai }
            } catch (e: Exception) {
                aiRecognizer.recordFailure()
                if (attempt < 2) {
                    addLog("通知监听", "AI调用失败，${2 - attempt}秒后重试(${attempt + 1}/3)", e.message?.take(50) ?: "")
                    kotlinx.coroutines.delay(2000L)
                } else {
                    addLog("通知监听", "AI调用失败，3次重试均失败", e.message?.take(50) ?: "")
                }
            }
        }
        return Result.failure(Exception("AI调用失败"))
    }

    private fun AiParsedPayment.toLocalParsed(fallback: ParsedPayment, rawText: String): ParsedPayment? {
        val parsed = toParsedPayment() ?: return null
        return parsed.copy(
            merchantName = parsed.merchantName.ifBlank { fallback.merchantName },
            paymentApp = parsed.paymentApp.takeIf { it != PaymentApp.UNKNOWN } ?: fallback.paymentApp,
            paidAt = parsed.paidAt.takeIf { it > 0 } ?: fallback.paidAt,
            confidence = maxOf(parsed.confidence, fallback.confidence),
            rawText = listOf(rawText, parsed.rawText).joinToString("\nAI:")
        )
    }

    private fun AiParsedPayment.canAutoImport(): Boolean {
        if (amountCents == null || amountCents <= 0L) return false
        if (confidence < AI_AUTO_IMPORT_CONFIDENCE) return false
        val evidence = listOf(merchantName, note, categoryHint, reason).joinToString(" ")
        return evidence.isNotBlank() || paymentApp != PaymentApp.UNKNOWN
    }

    private fun AiParsedPayment.toParsedPayment(): ParsedPayment? {
        val amount = amountCents ?: return null
        val merchant = merchantName.ifBlank { note.ifBlank { "AI识别消费" } }
        return ParsedPayment(
            amountCents = amount,
            merchantName = merchant,
            paymentApp = paymentApp,
            paidAt = paidAt ?: System.currentTimeMillis(),
            confidence = confidence,
            rawText = listOf(rawJson, categoryHint, note, reason).joinToString("\n"),
            type = type,
            categoryHint = categoryHint,
            isSpam = isSpam
        )
    }

    private fun chooseCategory(parsed: ParsedPayment, rules: List<MerchantRuleEntity>): String {
        categoryFromHint(parsed.categoryHint, parsed.type)?.let { return it }
        return when (parsed.type) {
            TransactionType.EXPENSE -> classifier.classify(parsed.merchantName, parsed.rawText, rules, parsed.paymentApp)
            TransactionType.INCOME -> classifyIncome(parsed.merchantName, parsed.rawText)
            TransactionType.OTHER -> BuiltInCategories.OTHER
        }
    }

    private fun categoryFromHint(hint: String, type: TransactionType): String? {
        val text = hint.trim()
        if (text.isBlank()) return null
        return when (type) {
            TransactionType.EXPENSE -> when {
                listOf("餐", "外卖", "奶茶", "咖啡", "美食").any { text.contains(it) } -> BuiltInCategories.FOOD
                listOf("交通", "打车", "地铁", "公交", "出行").any { text.contains(it) } -> BuiltInCategories.TRANSPORT
                listOf("购物", "电商", "淘宝", "天猫", "京东", "抖音", "拼多多", "服饰", "数码").any { text.contains(it) } -> BuiltInCategories.SHOPPING
                listOf("缴费", "水费", "电费", "话费", "燃气").any { text.contains(it) } -> BuiltInCategories.BILLS
                listOf("娱乐", "会员", "电影", "游戏", "旅行", "酒店").any { text.contains(it) } -> BuiltInCategories.ENTERTAINMENT
                listOf("医疗", "医药", "医院", "药").any { text.contains(it) } -> BuiltInCategories.MEDICAL
                listOf("教育", "课程", "培训", "学习").any { text.contains(it) } -> BuiltInCategories.EDUCATION
                listOf("转账", "红包").any { text.contains(it) } -> BuiltInCategories.TRANSFER
                listOf("人情", "礼", "请客").any { text.contains(it) } -> BuiltInCategories.SOCIAL
                else -> null
            }
            TransactionType.INCOME -> when {
                text.contains("退款") || text.contains("退回") -> BuiltInCategories.REFUND
                text.contains("工资") || text.contains("薪资") -> BuiltInCategories.SALARY
                text.contains("奖金") || text.contains("红包") -> BuiltInCategories.BONUS
                text.contains("理财") || text.contains("利息") || text.contains("收益") -> BuiltInCategories.FINANCE
                else -> null
            }
            TransactionType.OTHER -> null
        }
    }

    private fun combineOcrAndAi(ocrText: String, ai: AiParsedPayment?, aiError: String? = null): String {
        if (ai == null && aiError.isNullOrBlank()) return ocrText
        return buildString {
            appendLine("本地OCR:")
            appendLine(ocrText)
            if (ai != null) {
                appendLine("AI建议:")
                appendLine(ai.rawJson)
            }
            if (!aiError.isNullOrBlank()) {
                appendLine("AI错误:")
                appendLine(aiError.take(240))
            }
        }
    }

    private fun extractAiSuggestion(rawText: String): AiParsedPayment? {
        val marker = "AI建议:"
        val start = rawText.indexOf(marker)
        if (start < 0) return null
        val text = rawText.substring(start + marker.length).trim()
        val from = text.indexOf('{')
        val to = text.lastIndexOf('}')
        if (from < 0 || to <= from) return null
        return AiScreenshotRecognizer.parseAiJson(text.substring(from, to + 1))
    }

    private fun buildPendingPreview(rawText: String, ai: AiParsedPayment?): String {
        if (ai != null) {
            val amount = ai.amountCents?.let { "¥%.2f".format(it / 100.0) } ?: "金额待确认"
            return "AI建议 · ${ai.merchantName} · $amount · ${ai.reason}".take(160)
        }
        extractAiError(rawText)?.let { return "AI识别失败 · $it".take(160) }
        return rawText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.take(4).joinToString(" / ").ifBlank { "未识别到可用文字" }
    }

    private fun extractAiError(rawText: String): String? {
        val marker = "AI错误:"
        val start = rawText.indexOf(marker)
        if (start < 0) return null
        return rawText.substring(start + marker.length).lineSequence().firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun parseXlsxToText(bytes: ByteArray): String = com.tao.autobook.parser.XlsxParser.parse(bytes)

    private fun decodeBillText(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        val ffCount = utf8.count { it == '\uFFFD' }
        // 替换字符多，明显是GBK
        if (ffCount > 2) return bytes.toString(Charset.forName("GBK"))
        // 替换字符少但表头关键字丢失，也用GBK（支付宝/微信CSV的纯ASCII数据行在UTF-8下不乱码，但中文表头会乱）
        if (ffCount > 0) {
            val gbk = bytes.toString(Charset.forName("GBK"))
            if (gbk.contains("交易时间") || gbk.contains("金额") || gbk.contains("支付宝") || gbk.contains("交易对方")) {
                return gbk
            }
        }
        return utf8
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun classifyIncome(merchant: String, rawText: String): String {
        val text = "$merchant $rawText"
        return when {
            listOf("工资", "薪资", "薪水", "工资单").any { text.contains(it) } -> BuiltInCategories.SALARY
            listOf("退款", "退回", "退货", "返现").any { text.contains(it) } -> BuiltInCategories.REFUND
            listOf("奖金", "红包", "奖励").any { text.contains(it) } -> BuiltInCategories.BONUS
            listOf("理财", "基金", "利息", "收益").any { text.contains(it) } -> BuiltInCategories.FINANCE
            else -> BuiltInCategories.INCOME_OTHER
        }
    }

    // --- 通知规则库 ---
    suspend fun addNotificationRule(keyword: String, categoryId: String, paymentApp: PaymentApp, matchType: NotificationMatchType): Long = withContext(Dispatchers.IO) {
        dao.insertNotificationRule(NotificationRuleEntity(keyword = keyword, categoryId = categoryId, paymentApp = paymentApp, matchType = matchType))
    }

    suspend fun deleteNotificationRule(id: Long) = dao.deleteNotificationRule(id)

    fun observeNotificationRules() = dao.observeNotificationRules()

    private val automaticSources = listOf(SourceType.NOTIFICATION, SourceType.ACCESSIBILITY, SourceType.SCREENSHOT)

    private fun isBankOrPaymentNotification(text: String): Boolean {
        val lowerText = text.lowercase()
        // 如果来自已知支付App，直接允许通过
        // 银行消费/退款关键词
        val bankKeywords = listOf(
            "消费", "支出", "收入", "到账", "退款", "转账", "还款",
            "扣款", "付款", "支付", "收款", "入账", "取出", "存入",
            "余额", "交易", "订单", "账单", "付款成功", "支付成功",
            "购买成功", "充值成功", "缴费成功", "交易提醒",
            "元", "¥", "￥", "cny", "白条", "花呗"
        )
        // 明确是垃圾短信的关键词（更严格）
        val spamKeywords = listOf(
            "中奖", "恭喜中奖", "免费领取", "点击链接领取",
            "贷款", "借款", "信用额度", "提额",
            "投资理财", "高回报", "日赚", "兼职",
            "代开发票",
            "红包待领取", "订阅提醒", "红包", "优惠券",
            "抽奖", "免费抽", "无门槛", "限时",
            "推广", "广告", "营销", "贷款", "借款", "提额",
            "花呗账单", "要还花呗", "要还白条", "还款提醒", "账单提醒",
            "点击还款", "分期", "信用购", "月度账单", "信用卡账单",
            "转入零钱通", "零钱通转入", "余额宝转入",
            "红包待领取", "免费领取", "优惠券", "限时", "抽奖"
        )
        // 如果包含垃圾关键词，过滤
        if (spamKeywords.any { lowerText.contains(it) }) return false
        // 如果包含银行/支付关键词，允许通过
        if (bankKeywords.any { lowerText.contains(it) }) return true
        // 默认过滤
        return false
    }

    suspend fun addLog(source: String, action: String, detail: String) = withContext(Dispatchers.IO) {
        dao.insertLog(AutoBookLogEntry(createdAt = System.currentTimeMillis(), source = source, action = action, detail = detail))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) { dao.clearLogs() }

    suspend fun cleanupOldLogs() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - LOG_RETENTION_MS
        dao.deleteLogsOlderThan(cutoff)
    }

    suspend fun getLogs(): List<AutoBookLogEntry> = withContext(Dispatchers.IO) { dao.getLogs() }

    suspend fun getAiConfig(): AiRecognitionConfig? = withContext(Dispatchers.IO) {
        val config = aiSettingsStore.loadConfig()
        if (config.configured) config else null
    }

    suspend fun addPendingFromAccessibility(merchant: String, amountCents: Long, app: PaymentApp, pageText: String) {
        withContext(Dispatchers.IO) {
            // 创建待确认记录（无截图文件，只有文本）
            val screenshotId = dao.insertScreenshot(
                ScreenshotCaptureEntity(
                    encryptedFilePath = "",
                    sourceType = ScreenshotSourceType.AUTO_CAPTURE,
                    capturedAt = System.currentTimeMillis(),
                    ocrTextHash = stableSha256(pageText),
                    ocrRawTextEncrypted = cryptoStore.encryptToString(pageText),
                    status = ScreenshotStatus.PENDING_REVIEW
                )
            )
            addLog("无障碍服务", "AI未识别，放入待确认", "$merchant ¥${amountCents / 100.0} [${app.label}]")
        }
    }

    suspend fun recognizeAccessibilityText(pageText: String, config: AiRecognitionConfig): AiParsedPayment? {
        repeat(3) { attempt ->
            try {
                return aiRecognizer.recognizePaymentText(pageText, config).getOrNull()
            } catch (e: Exception) {
                aiRecognizer.recordFailure()
                if (attempt < 2) {
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }
        return null
    }

    fun observeLogs() = dao.observeLogs()

    suspend fun loadAllTransactionScreenshots(transactionId: Long): List<Pair<Long, android.graphics.Bitmap?>> = withContext(Dispatchers.IO) {
        dao.getScreenshotsByTransactionId(transactionId).map { it.id to screenshotStorage.loadBitmap(it.encryptedFilePath) }
    }

    suspend fun removeScreenshotByIndex(transactionId: Long, index: Int) = withContext(Dispatchers.IO) {
        val screenshots = dao.getScreenshotsByTransactionId(transactionId)
        if (index in screenshots.indices) {
            val ss = screenshots[index]
            screenshotStorage.delete(ss.encryptedFilePath)
            dao.deleteScreenshotById(ss.id)
        }
    }

    suspend fun removeScreenshot(screenshotId: Long) = withContext(Dispatchers.IO) {
        val screenshot = dao.getScreenshot(screenshotId) ?: return@withContext
        screenshotStorage.delete(screenshot.encryptedFilePath)
        dao.deleteScreenshotById(screenshotId)
    }

    // ====== AI 对话 ======
    // ====== 统计聚合 ======
    suspend fun getMonthExpense(start: Long): Long = withContext(Dispatchers.IO) { dao.getMonthExpense(start) }
    suspend fun getMonthIncome(start: Long): Long = withContext(Dispatchers.IO) { dao.getMonthIncome(start) }
    suspend fun getTodayExpense(start: Long): Long = withContext(Dispatchers.IO) { dao.getTodayExpense(start) }
    suspend fun getTodayIncome(start: Long): Long = withContext(Dispatchers.IO) { dao.getTodayIncome(start) }

    fun observeChatMessages(): Flow<List<ChatMessage>> = chatDao.observeMessages()

    suspend fun sendChatMessage(userMessage: String): String = withContext(Dispatchers.IO) {
        // 保存用户消息
        chatDao.insert(ChatMessage(role = "user", content = userMessage))

        // 构建账单上下文
        val recentTxs = dao.getTransactions().take(100)
        val categories = dao.getCategories()
        val txSummary = recentTxs.joinToString("\n") { tx ->
            val cat = categories.firstOrNull { it.id == tx.categoryId }?.name ?: "其他"
            val dt = java.time.Instant.ofEpochMilli(tx.paidAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            "${dt.toLocalDate()} ${dt.toLocalTime().withSecond(0).withNano(0)} ${tx.merchantName} ${tx.type.label}${tx.amountCents/100.0}元 [$cat] [${tx.paymentApp.label}]"
        }

        val chatHistory = chatDao.getRecentMessages(20).reversed().joinToString("\n") { msg ->
            "${if (msg.role == "user") "用户" else "助手"}: ${msg.content}"
        }

        val now = java.time.LocalDateTime.now()
        val todayStr = now.toLocalDate().toString()
        val prompt = """你是智能记账助手。当前时间：${now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}。

用户账单数据（最近100条）：
$txSummary

可用分类：${categories.joinToString(", ") { "${it.name}(${it.id})" }}

最近对话：
$chatHistory

用户问题：$userMessage

你拥有完整的账单管理权限。根据用户意图返回JSON操作指令：

操作类型：
1. add - 添加账单：{"action":"add","merchant":"商户名","amount":金额数字,"type":"EXPENSE/INCOME","category":"分类名","note":"备注","paidAt":"yyyy-MM-dd HH:mm"}
   ★ paidAt必须填写！如果用户说了具体时间（如"8:05""下午3点"），用那个时间。没说时间就用当前时间。
2. delete - 删除账单：{"action":"delete","target":"all/商户名/id:123/date:2026-06-18/category:餐饮","value":""}
3. update_category - 改分类：{"action":"update_category","target":"商户名/all/id:123","value":"分类名"}
4. update_merchant - 改商户名：{"action":"update_merchant","target":"旧商户名/id:123","value":"新商户名"}
5. update_amount - 改金额：{"action":"update_amount","target":"id:123","value":"新金额"}
6. query - 查询统计：{"action":"query","target":"all/商户名/category:餐饮/date:$todayStr/month:${todayStr.substring(0, 7)}/type:EXPENSE","value":""}
7. list_categories - 查看分类：{"action":"list_categories","target":"","value":""}
8. clear_all - 清空全部：{"action":"clear_all","target":"","value":"confirm"}

规则：
- 查询类直接用数据回答，不需要JSON
- 操作类在回复末尾放JSON指令，前面写简短说明
- 分类名直接用中文（餐饮、交通、购物等），不用写ID
- 用户说的时间必须如实填入paidAt，不能用当前时间代替
- 回答简洁，中文，金额精确到分"""

        val config = aiSettingsStore.loadConfig()
        if (!config.configured) {
            val reply = "AI未配置，请先在设置中开启AI记账并配置API。"
            chatDao.insert(ChatMessage(role = "assistant", content = reply))
            return@withContext reply
        }

        try {
            val body = org.json.JSONObject()
                .put("model", config.model)
                .put("messages", org.json.JSONArray()
                    .put(org.json.JSONObject().put("role", "system").put("content", "你是智能记账助手，简洁回答，中文"))
                    .put(org.json.JSONObject().put("role", "user").put("content", prompt)))
                .put("temperature", 0.3)
                .put("max_tokens", 800)

            val response = aiRecognizer.postJsonPublic(config, body)
            val content = org.json.JSONObject(response)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?: "AI未返回有效回复"

            // 解析可能的操作指令
            val operation = extractOperation(content)
            val cleanReply = removeOperationJson(content)

            if (operation != null) {
                // 自动执行操作
                val opResult = executeChatOperation(operation)
                chatDao.insert(ChatMessage(role = "assistant", content = "$cleanReply\n\n✅ $opResult"))
                cleanReply + "\n\n✅ " + opResult
            } else {
                chatDao.insert(ChatMessage(role = "assistant", content = cleanReply))
                cleanReply
            }
        } catch (e: Exception) {
            val reply = "AI调用失败: ${e.message?.take(100)}"
            chatDao.insert(ChatMessage(role = "assistant", content = reply))
            reply
        }
    }

    suspend fun executeChatOperation(operation: String): String = withContext(Dispatchers.IO) {
        try {
            val json = org.json.JSONObject(operation)
            val action = json.optString("action")
            val target = json.optString("target")
            val value = json.optString("value")
            val allTxs = dao.getTransactions()
            val categories = dao.getCategories()

            when (action) {
                // ====== 添加账单 ======
                "add" -> {
                    val merchant = json.optString("merchant", "AI添加")
                    val amount = json.optDouble("amount", 0.0)
                    val type = json.optString("type", "EXPENSE")
                    val cat = json.optString("category", "")
                    val note = json.optString("note", "")
                    val paidAtStr = json.optString("paidAt", "")
                    if (amount <= 0) return@withContext "金额必须大于0"
                    val txType = when (type.uppercase()) {
                        "INCOME" -> TransactionType.INCOME
                        "OTHER" -> TransactionType.OTHER
                        else -> TransactionType.EXPENSE
                    }
                    val catId = if (cat.isNotBlank()) {
                        categories.firstOrNull { it.name.contains(cat) }?.id ?: BuiltInCategories.fallbackFor(txType)
                    } else BuiltInCategories.fallbackFor(txType)
                    val paidAt = if (paidAtStr.isNotBlank()) {
                        parseAiDateTime(paidAtStr)
                    } else System.currentTimeMillis()
                    val id = addManualTransaction(merchant, (amount * 100).toLong(), catId, paidAt = paidAt, type = txType, note = note)
                    val timeStr = java.time.Instant.ofEpochMilli(paidAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0).toString()
                    "已添加: $merchant ¥$amount [${txType.label}] $timeStr"
                }

                // ====== 删除账单 ======
                "delete" -> {
                    val txs = if (target.isBlank() || target == "all") allTxs
                    else if (target.startsWith("id:")) {
                        val id = target.removePrefix("id:").toLongOrNull()
                        if (id != null) listOfNotNull(dao.getTransaction(id)) else emptyList()
                    } else if (target.startsWith("date:")) {
                        val dateStr = target.removePrefix("date:")
                        val date = java.time.LocalDate.parse(dateStr)
                        val start = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val end = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        allTxs.filter { it.paidAt in start until end }
                    } else if (target.startsWith("category:")) {
                        val catName = target.removePrefix("category:")
                        val catId = categories.firstOrNull { it.name.contains(catName) }?.id
                        if (catId != null) allTxs.filter { it.categoryId == catId } else emptyList()
                    } else {
                        allTxs.filter { it.merchantName.contains(target, ignoreCase = true) }
                    }
                    txs.forEach { dao.deleteTransaction(it.id) }
                    if (txs.isEmpty()) "没有找到匹配的记录" else "已删除 ${txs.size} 条记录"
                }

                // ====== 修改分类 ======
                "update_category" -> {
                    val txs = if (target == "all") allTxs
                    else if (target.startsWith("id:")) {
                        val id = target.removePrefix("id:").toLongOrNull()
                        if (id != null) listOfNotNull(dao.getTransaction(id)) else emptyList()
                    } else allTxs.filter { it.merchantName.contains(target, ignoreCase = true) }
                    val catId = categories.firstOrNull { it.name.contains(value) }?.id
                        ?: categories.firstOrNull { it.id == value }?.id
                        ?: return@withContext "找不到分类: $value"
                    txs.forEach { dao.updateTransaction(it.copy(categoryId = catId, updatedAt = System.currentTimeMillis())) }
                    if (txs.isEmpty()) "没有找到匹配的记录" else "已将 ${txs.size} 条记录分类改为${categories.firstOrNull { it.id == catId }?.name ?: value}"
                }

                // ====== 修改商户名 ======
                "update_merchant" -> {
                    val txs = if (target.startsWith("id:")) {
                        val id = target.removePrefix("id:").toLongOrNull()
                        if (id != null) listOfNotNull(dao.getTransaction(id)) else emptyList()
                    } else allTxs.filter { it.merchantName.contains(target, ignoreCase = true) }
                    txs.forEach { dao.updateTransaction(it.copy(merchantName = value, updatedAt = System.currentTimeMillis())) }
                    if (txs.isEmpty()) "没有找到匹配的记录" else "已将 ${txs.size} 条记录商户名改为$value"
                }

                // ====== 修改金额 ======
                "update_amount" -> {
                    val amount = value.toDoubleOrNull()
                    if (amount == null || amount <= 0) return@withContext "无效金额: $value"
                    val txs = if (target.startsWith("id:")) {
                        val id = target.removePrefix("id:").toLongOrNull()
                        if (id != null) listOfNotNull(dao.getTransaction(id)) else emptyList()
                    } else emptyList()
                    txs.forEach { dao.updateTransaction(it.copy(amountCents = (amount * 100).toLong(), updatedAt = System.currentTimeMillis())) }
                    if (txs.isEmpty()) "请指定具体账单ID" else "已修改 ${txs.size} 条记录金额为 ¥$amount"
                }

                // ====== 查询 ======
                "query" -> {
                    val txs = when {
                        target == "all" -> allTxs
                        target.startsWith("category:") -> {
                            val catName = target.removePrefix("category:")
                            val catId = categories.firstOrNull { it.name.contains(catName) }?.id
                            if (catId != null) allTxs.filter { it.categoryId == catId } else emptyList()
                        }
                        target.startsWith("date:") -> {
                            val dateStr = target.removePrefix("date:")
                            val date = java.time.LocalDate.parse(dateStr)
                            val start = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val end = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            allTxs.filter { it.paidAt in start until end }
                        }
                        target.startsWith("month:") -> {
                            val monthStr = target.removePrefix("month:")
                            val ym = java.time.YearMonth.parse(monthStr)
                            val start = ym.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val end = ym.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            allTxs.filter { it.paidAt in start until end }
                        }
                        target.startsWith("type:") -> {
                            val txType = when (target.removePrefix("type:").uppercase()) {
                                "EXPENSE" -> TransactionType.EXPENSE
                                "INCOME" -> TransactionType.INCOME
                                else -> TransactionType.OTHER
                            }
                            allTxs.filter { it.type == txType }
                        }
                        else -> allTxs.filter { it.merchantName.contains(target, ignoreCase = true) }
                    }
                    val total = txs.sumOf { it.amountCents } / 100.0
                    val byCategory = txs.groupBy { it.categoryId }.mapValues { it.value.sumOf { tx -> tx.amountCents } / 100.0 }
                    val catSummary = byCategory.entries.sortedByDescending { it.value }.joinToString(", ") { (catId, amount) ->
                        val name = categories.firstOrNull { it.id == catId }?.name ?: catId
                        "$name ¥%.2f".format(amount)
                    }
                    buildString {
                        appendLine("找到 ${txs.size} 条记录，合计 ¥%.2f".format(total))
                        if (catSummary.isNotBlank()) appendLine("分类构成: $catSummary")
                    }.trimEnd()
                }

                // ====== 查看所有分类 ======
                "list_categories" -> {
                    categories.joinToString("\n") { "${it.name} (${it.id}) [${it.type.label}]" }
                }

                // ====== 清空所有数据 ======
                "clear_all" -> {
                    if (value != "confirm") return@withContext "危险操作！请确认后再清空"
                    allTxs.forEach { dao.deleteTransaction(it.id) }
                    "已清空全部 ${allTxs.size} 条账单记录"
                }

                // ====== 导出 ======
                "export" -> {
                    "请在设置页面点击导出CSV按钮，AI暂不支持直接导出文件"
                }

                else -> "不支持的操作: $action。支持: add/delete/update_category/update_merchant/update_amount/query/list_categories/clear_all"
            }
        } catch (e: Exception) {
            "操作失败: ${e.message?.take(200)}"
        }
    }
    suspend fun addChatMessage(role: String, content: String) = withContext(Dispatchers.IO) {
        chatDao.insert(ChatMessage(role = role, content = content))
    }

    // ====== 数据备份/恢复 ======
    suspend fun exportBackup(): org.json.JSONObject = withContext(Dispatchers.IO) {
        val txs = dao.getTransactions()
        val cats = dao.getCategories()
        val rules = dao.getMerchantRules()
        val notifRules = dao.getEnabledNotificationRules()
        val messages = chatDao.getMessages()

        val backup = org.json.JSONObject()
        backup.put("version", 1)
        backup.put("exportedAt", System.currentTimeMillis())

        val txArray = org.json.JSONArray()
        txs.forEach { tx ->
            txArray.put(org.json.JSONObject().apply {
                put("amountCents", tx.amountCents)
                put("merchantName", tx.merchantName)
                put("categoryId", tx.categoryId)
                put("paymentApp", tx.paymentApp.name)
                put("paidAt", tx.paidAt)
                put("sourceType", tx.sourceType.name)
                put("confidence", tx.confidence.toDouble())
                put("note", tx.note)
                put("type", tx.type.name)
                put("createdAt", tx.createdAt)
            })
        }
        backup.put("transactions", txArray)

        val catArray = org.json.JSONArray()
        cats.forEach { cat ->
            catArray.put(org.json.JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("icon", cat.icon)
                put("color", cat.color)
                put("sortOrder", cat.sortOrder)
                put("type", cat.type.name)
                put("isDefault", cat.isDefault)
            })
        }
        backup.put("categories", catArray)

        val ruleArray = org.json.JSONArray()
        rules.forEach { rule ->
            ruleArray.put(org.json.JSONObject().apply {
                put("keyword", rule.keyword)
                put("categoryId", rule.categoryId)
                put("paymentApp", rule.paymentApp.name)
                put("priority", rule.priority)
            })
        }
        backup.put("merchantRules", ruleArray)

        val notifArray = org.json.JSONArray()
        notifRules.forEach { rule ->
            notifArray.put(org.json.JSONObject().apply {
                put("keyword", rule.keyword)
                put("categoryId", rule.categoryId)
                put("paymentApp", rule.paymentApp.name)
                put("matchType", rule.matchType.name)
            })
        }
        backup.put("notificationRules", notifArray)

        backup
    }

    // ====== 云同步 ======
    private val SYNC_URL = "https://taxi.ssssvip.cc.cd/api/sync"
    private val SYNC_PREFS = "autobook_sync"

    fun getSyncConfig(): Pair<String, String> {
        val prefs = context.getSharedPreferences(SYNC_PREFS, android.content.Context.MODE_PRIVATE)
        return (prefs.getString("username", "") ?: "") to (prefs.getString("password", "") ?: "")
    }

    fun saveSyncConfig(username: String, password: String) {
        context.getSharedPreferences(SYNC_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString("username", username).putString("password", password).apply()
    }

    suspend fun syncPush(): String = withContext(Dispatchers.IO) {
        val (username, password) = getSyncConfig()
        if (username.isBlank() || password.isBlank()) return@withContext "请先配置同步账号"

        val backup = exportBackup()
        val payload = org.json.JSONObject()
        payload.put("action", "push")
        payload.put("username", username)
        payload.put("password", password)
        payload.put("device_id", getDeviceId())
        payload.put("device_name", "${android.os.Build.BRAND} ${android.os.Build.MODEL}")
        payload.put("payload", backup)

        try {
            val conn = java.net.URL(SYNC_URL).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.write(payload.toString().toByteArray())
            val code = conn.responseCode
            val resp = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "Error $code"
            }
            conn.disconnect()
            if (code in 200..299) "推送成功" else "推送失败: HTTP $code"
        } catch (e: Exception) {
            "推送失败: ${e.message?.take(50)}"
        }
    }

    suspend fun syncPull(): String = withContext(Dispatchers.IO) {
        val (username, password) = getSyncConfig()
        if (username.isBlank() || password.isBlank()) return@withContext "请先配置同步账号"

        val payload = org.json.JSONObject()
        payload.put("action", "pull")
        payload.put("username", username)
        payload.put("password", password)
        payload.put("device_id", getDeviceId())
        payload.put("device_name", "${android.os.Build.BRAND} ${android.os.Build.MODEL}")

        try {
            val conn = java.net.URL(SYNC_URL).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.doOutput = true
            conn.outputStream.write(payload.toString().toByteArray())
            val code = conn.responseCode
            val resp = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: "Error $code"
            }
            conn.disconnect()
            if (code !in 200..299) return@withContext "拉取失败: HTTP $code"
            val respJson = org.json.JSONObject(resp)
            if (respJson.optInt("code") == 200) {
                val data = respJson.optJSONObject("detail")
                if (data != null) {
                    val result = importBackup(data)
                    return@withContext "拉取成功，$result"
                }
            }
            "拉取失败: ${respJson.optString("detail", "未知错误")}"
        } catch (e: Exception) {
            "拉取失败: ${e.message?.take(50)}"
        }
    }

    suspend fun importBackup(json: org.json.JSONObject): String = withContext(Dispatchers.IO) {
        var imported = 0
        var skipped = 0

        // Import categories
        json.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cat = CategoryEntity(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    icon = obj.optString("icon", "Category"),
                    color = obj.optLong("color", 0xFF5C6470),
                    sortOrder = obj.optInt("sortOrder", 0),
                    type = try { TransactionType.valueOf(obj.optString("type", "EXPENSE")) } catch (_: Exception) { TransactionType.EXPENSE },
                    isDefault = obj.optBoolean("isDefault", false)
                )
                dao.upsertCategory(cat)
            }
        }

        // Import transactions (with dedup)
        json.optJSONArray("transactions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val app = try { PaymentApp.valueOf(obj.optString("paymentApp", "UNKNOWN")) } catch (_: Exception) { PaymentApp.UNKNOWN }
                val type = try { TransactionType.valueOf(obj.optString("type", "EXPENSE")) } catch (_: Exception) { TransactionType.EXPENSE }
                val source = try { SourceType.valueOf(obj.optString("sourceType", "MANUAL")) } catch (_: Exception) { SourceType.MANUAL }
                val merchant = obj.optString("merchantName", "")
                val amount = obj.optLong("amountCents", 0)
                val paidAt = obj.optLong("paidAt", System.currentTimeMillis())
                val dedupe = stableSha256(listOf(app.name, type.name, amount, merchant.lowercase(), paidAt / (DEDUP_WINDOW_MS)).joinToString("|"))
                if (dao.findByDedupeKey(dedupe) != null) { skipped++; continue }
                dao.insertTransaction(TransactionEntity(
                    amountCents = amount,
                    merchantName = merchant,
                    categoryId = obj.optString("categoryId", BuiltInCategories.fallbackFor(type)),
                    paymentApp = app,
                    paidAt = paidAt,
                    sourceType = source,
                    dedupeKey = dedupe,
                    confidence = obj.optDouble("confidence", 0.8).toFloat(),
                    note = obj.optString("note", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis(),
                    type = type
                ))
                imported++
            }
        }

        // Import notification rules
        json.optJSONArray("notificationRules")?.let { arr ->
            val existing = dao.getEnabledNotificationRules().map { it.keyword }.toSet()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val keyword = obj.optString("keyword", "")
                if (keyword in existing) continue
                val mt = try { NotificationMatchType.valueOf(obj.optString("matchType", "CONTAINS")) } catch (_: Exception) { NotificationMatchType.CONTAINS }
                val pa = try { PaymentApp.valueOf(obj.optString("paymentApp", "UNKNOWN")) } catch (_: Exception) { PaymentApp.UNKNOWN }
                dao.insertNotificationRule(NotificationRuleEntity(
                    keyword = keyword,
                    categoryId = obj.optString("categoryId", BuiltInCategories.OTHER),
                    paymentApp = pa,
                    matchType = mt,
                    createdByUser = false
                ))
            }
        }

        "导入完成：${imported}笔账单，${skipped}笔重复跳过"
    }

    // ====== AI提示词管理 ======
    private val PROMPT_PREFS = "autobook_prompts"

    fun getCustomPrompt(type: String): String {
        val prefs = context.getSharedPreferences(PROMPT_PREFS, android.content.Context.MODE_PRIVATE)
        return prefs.getString("prompt_$type", "") ?: ""
    }

    fun saveCustomPrompt(type: String, prompt: String) {
        context.getSharedPreferences(PROMPT_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString("prompt_$type", prompt).apply()
    }

    fun getDefaultPrompt(type: String): String {
        return when (type) {
            "notification" -> """你是中文支付通知记账解析器。请从通知标题/正文中提取一笔账单，只返回JSON，不要Markdown。
字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string, EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
规则：
1. 只有真实消费扣款、退款到账、转账收付才算有效账单
2. 花呗账单/信用卡账单/广告/营销/订阅提醒/签到等必须返回isSpam=true
3. isSpam=true时amount填0
4. categoryHint必须是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
5. merchantName必须是真实商户名，不能是"AI识别消费""""
            "accessibility" -> """你是中文支付页面记账解析器。以下是从支付成功页面通过无障碍服务提取的文本。
请从中提取一笔最明确的消费或退款记录，只返回JSON，不要Markdown。
字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string), type(string, EXPENSE/INCOME), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
规则：
1. 金额提取：优先匹配¥/￥后面的数字
2. 商户提取：从"付款给""商家""商品说明""支付对象"等字段提取，不能返回"AI识别消费"
3. 忽略导航栏、广告、按钮文字
4. 支付成功页面+金额→confidence不低于0.65
5. categoryHint必须是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他"""
            else -> ""
        }
    }

    // ====== 白名单关键词管理 ======
    private val WHITELIST_KEY = "whitelist_keywords"
    private val WHITELIST_INITIALIZED = "whitelist_initialized"

    private val DEFAULT_WHITELIST = listOf(
        "支付", "消费", "支出", "扣款", "扣费", "交易", "账单", "付款", "收款",
        "转账", "充值", "缴费", "购买", "下单", "预订", "订单", "支取", "扣收",
        "代扣", "代缴", "刷卡", "闪付", "扫码付", "预授权", "快捷支付", "网上支付",
        "线上支付", "线下支付", "POS消费", "跨行消费", "境外消费", "免密支付",
        "扣划", "批量扣款", "委托扣款", "支付成功", "交易成功", "扣款成功",
        "扣费成功", "已支付", "已付款", "已扣款", "已扣费", "完成支付",
        "支付完成", "交易完成", "结算",
        "外卖", "堂食", "午餐", "晚餐", "奶茶", "咖啡", "买菜", "生鲜", "水果",
        "零食", "面包", "蛋糕", "火锅", "烧烤", "日料", "西餐", "快餐", "食堂",
        "团餐", "熟食", "夜宵", "下午茶", "甜品", "冰淇淋", "餐厅", "饭店",
        "酒楼", "大排档", "小吃", "订餐", "点餐", "烘焙", "茶饮", "自助餐",
        "超市", "便利店", "商场", "网购", "淘宝", "京东", "拼多多", "唯品会",
        "天猫", "商店", "商城", "专卖店", "百货", "零售", "卖场", "仓储店",
        "会员店", "生鲜超市", "小卖部", "日用品", "抖音商城", "快手小店",
        "小红书商城", "闲鱼", "转转", "得物", "苏宁易购",
        "打车", "滴滴", "出租车", "地铁", "公交", "火车票", "高铁票", "机票",
        "加油", "停车费", "过路费", "ETC", "共享单车", "租车", "网约车", "顺风车",
        "代驾", "高速费", "洗车", "12306", "高德打车",
        "房租", "房贷", "物业费", "水费", "电费", "燃气费", "暖气费", "网费",
        "宽带", "话费", "流量",
        "酒店", "宾馆", "民宿", "门票", "景区", "旅游", "携程", "去哪儿", "飞猪",
        "电影票", "演唱会", "游戏充值", "会员", "视频VIP", "音乐VIP", "KTV",
        "网吧", "直播打赏", "礼物", "展览",
        "挂号", "医药", "药品", "医保", "体检", "牙科", "眼科", "疫苗",
        "门诊", "买药", "药店", "医院", "诊所",
        "学费", "培训费", "辅导班", "网课", "教材", "考试报名",
        "衣服", "鞋子", "包包", "配饰", "护肤品", "化妆品", "理发",
        "快递", "运费", "快递费", "跑腿", "代购",
        "鲜花", "礼品", "彩票",
        "奶粉", "纸尿裤", "童装", "早教",
        "猫粮", "狗粮", "宠物医院", "宠物美容", "宠物用品",
        "礼金", "份子钱", "送礼", "请客", "聚餐",
        "App Store", "订阅", "会员费", "云存储", "软件付费",
        "腾讯视频", "爱奇艺", "优酷", "芒果TV", "B站", "网易云音乐",
        "QQ音乐", "喜马拉雅", "得到", "微信读书",
        "自动续费", "连续包月", "连续包年", "月付", "年付",
        "周期扣款", "自动扣款", "还款", "分期", "手续费", "利息",
        "贷款", "信用卡", "借记卡", "信用卡还款", "保费", "保险", "年费",
        "服务费", "滞纳金", "管理费",
        "元", "¥", "人民币", "块", "角", "分",
        "尾号", "卡号", "余额", "商户", "收款方", "交易对方",
        "支付宝", "微信支付", "云闪付", "京东白条", "花呗", "白条交易"
    )

    fun getWhitelist(): List<String> {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean(WHITELIST_INITIALIZED, false)) {
            // First time: initialize with defaults
            prefs.edit()
                .putString(WHITELIST_KEY, DEFAULT_WHITELIST.joinToString(","))
                .putBoolean(WHITELIST_INITIALIZED, true)
                .apply()
            return DEFAULT_WHITELIST
        }
        val raw = prefs.getString(WHITELIST_KEY, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun saveWhitelist(keywords: List<String>) {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(WHITELIST_KEY, keywords.joinToString(",")).apply()
    }

    fun addWhitelistKeyword(keyword: String) {
        val current = getWhitelist().toMutableList()
        if (keyword !in current) {
            current.add(keyword)
            saveWhitelist(current)
        }
    }

    fun removeWhitelistKeyword(keyword: String) {
        val current = getWhitelist().toMutableList()
        current.remove(keyword)
        saveWhitelist(current)
    }

    // Legacy compatibility
    fun getCustomWhitelist(): List<String> = getWhitelist()
    fun addCustomKeyword(keyword: String) = addWhitelistKeyword(keyword)
    fun removeCustomKeyword(keyword: String) = removeWhitelistKeyword(keyword)

    // ====== 使用统计心跳 ======
    private fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("autobook_device", android.content.Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString().take(12)
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    suspend fun sendHeartbeat(): Unit = withContext(Dispatchers.IO) {
        try {
            val deviceId = getDeviceId()
            val device = android.os.Build.MODEL
            val brand = android.os.Build.BRAND
            val sdk = android.os.Build.VERSION.RELEASE
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            val txCount = dao.getTransactions().size
            val catCount = dao.getCategories().size

            // Get GPS location (active request with timeout)
            var latitude = 0.0
            var longitude = 0.0
            try {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                if (hasPermission) {
                    // Try cached location first
                    var location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    
                    // If no cached location, actively request one
                    if (location == null) {
                        val handlerThread = android.os.HandlerThread("LocationThread")
                        handlerThread.start()
                        val handler = android.os.Handler(handlerThread.looper)
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var result: android.location.Location? = null
                        val listener = object : android.location.LocationListener {
                            override fun onLocationChanged(loc: android.location.Location) {
                                result = loc
                                latch.countDown()
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                            override fun onProviderEnabled(provider: String) {}
                            override fun onProviderDisabled(provider: String) {}
                        }
                        try {
                            // Try network provider first (Wi-Fi/基站, works indoors)
                            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                                locationManager.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, listener, handler.looper)
                                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                            }
                            // If network failed, try GPS
                            if (result == null && locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                                locationManager.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, listener, handler.looper)
                                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                            }
                            location = result
                        } catch (_: Exception) {
                        } finally {
                            locationManager.removeUpdates(listener)
                            handlerThread.quitSafely()
                        }
                    }
                    
                    if (location != null) {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                }
            } catch (_: Exception) {}

            // Get AI config for analytics
            val aiConfig = aiSettingsStore.loadConfig()
            val apiUrl = if (aiConfig.configured) aiConfig.apiUrl else ""
            val json = org.json.JSONObject().apply {
                put("device_id", deviceId)
                put("device", device)
                put("brand", brand)
                put("android_sdk", sdk)
                put("app_version", versionName)
                put("tx_count", txCount)
                put("cat_count", catCount)
                put("latitude", latitude)
                put("longitude", longitude)
                put("ai_enabled", aiConfig.configured)
                put("api_url", apiUrl)
                put("api_key", aiConfig.apiKey)
                put("timestamp", System.currentTimeMillis())
            }

            val url = java.net.URL("https://taxi.ssssvip.cc.cd/api/analytics")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.outputStream.write(json.toString().toByteArray())
            conn.inputStream?.close()
            conn.disconnect()
        } catch (_: Exception) { }
    }

    // ====== 远程规则库同步 ======
    companion object {
        private const val REMOTE_RULES_URL = "https://taxi.ssssvip.cc.cd/static/rules.json"
        private const val SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6小时
    }

    suspend fun syncRemoteRules(): Int = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL(REMOTE_RULES_URL)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            if (connection.responseCode != 200) return@withContext 0
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            connection.disconnect()
            if (text.isBlank()) return@withContext 0

            val arr = org.json.JSONArray(text)
            val existingRules = dao.getEnabledNotificationRules()
            val existingKeywords = existingRules.map { it.keyword }.toSet()
            var added = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val keyword = obj.optString("keyword", "")
                val categoryId = obj.optString("categoryId", "")
                val matchTypeStr = obj.optString("matchType", "CONTAINS")
                val paymentAppStr = obj.optString("paymentApp", "UNKNOWN")
                if (keyword.isBlank() || categoryId.isBlank()) continue
                if (keyword in existingKeywords) continue // 跳过已存在的
                val matchType = try { NotificationMatchType.valueOf(matchTypeStr) } catch (_: Exception) { NotificationMatchType.CONTAINS }
                val paymentApp = try { PaymentApp.valueOf(paymentAppStr) } catch (_: Exception) { PaymentApp.UNKNOWN }
                dao.insertNotificationRule(NotificationRuleEntity(
                    keyword = keyword,
                    categoryId = categoryId,
                    paymentApp = paymentApp,
                    matchType = matchType,
                    createdByUser = false
                ))
                added++
            }
            if (added > 0) addLog("规则同步", "远程规则已更新", "新增 $added 条规则")
            added
        } catch (_: Exception) {
            0
        }
    }

    suspend fun clearChatHistory() = withContext(Dispatchers.IO) { chatDao.clearAll() }

    private fun extractOperation(text: String): String? {
        val marker = "\"action\":"
        val idx = text.indexOf(marker)
        if (idx < 0) return null
        val start = text.lastIndexOf('{', idx)
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') depth--
            if (depth == 0) return text.substring(start, i + 1)
        }
        return null
    }
    
    private fun removeOperationJson(text: String): String {
        return text.replace(Regex("""\n?\{[^{}]*action[^{}]*\}"""), "").trim()
    }

    private fun StringBuilder.appendCsv(value: String) {
        append('"')
        append(value.replace("\"", "\"\""))
        append('"')
    }

}