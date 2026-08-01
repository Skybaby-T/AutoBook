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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.time.Instant
import java.time.ZoneId

private const val TAG = "AutoBook"
private const val LOCAL_AUTO_CONFIRM_CONFIDENCE = 0.78f
private const val AI_AUTO_IMPORT_CONFIDENCE = 0.50f
private const val DEDUP_WINDOW_MS: Long = 300_000L
private const val LOG_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000
private const val LEDGER_DISPLAY_LIMIT = 500
private const val CHAT_CONTEXT_LIMIT = 100
/** 报表排行榜取前 N 名 */
private const val REPORT_RANK_LIMIT = 10
/** 分类下钻明细最多取 N 笔 */
private const val REPORT_DRILL_TX_LIMIT = 30
/** 一天的毫秒数 */
private const val DAY_MS: Long = 24 * 60 * 60 * 1000L
/** 单笔最大金额：1亿人民币（分）。超此视为脏数据 */
private const val MAX_AMOUNT_CENTS: Long = 10_000_000_000L // 1e8 元 * 100

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
    private val aiMutex = Mutex()
    private var lastAiCallTime = 0L

    val transactions: Flow<List<TransactionEntity>> = dao.observeRecentTransactions(LEDGER_DISPLAY_LIMIT)
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val pendingScreenshots: Flow<List<ScreenshotCaptureEntity>> = dao.observeScreenshotsByStatus(ScreenshotStatus.PENDING_REVIEW)
    val aiSettings: Flow<AiRecognitionSettings> = aiSettingsStore.settings

    suspend fun initialize() {
            dao.seedCategoriesIfEmpty()
            // 清理离谱金额（AI/导入误把元当分或超大数），避免启动统计 SUM integer overflow 闪退
            runCatching {
                val fixed = dao.sanitizeOutlierAmounts(MAX_AMOUNT_CENTS, System.currentTimeMillis())
                if (fixed > 0) {
                    addLog("数据修复", "清理异常金额", "共 $fixed 条 amountCents 超限已置 0")
                }
            }
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
        android.util.Log.d(TAG, "AI配置: enabled=${aiConfig.enabled}, configured=${aiConfig.configured}, url=${aiConfig.apiUrl.take(30)}, model=${aiConfig.model}")

        // ====== AI开启：先本地预过滤，再过AI ======
        if (aiConfig.configured) {
            // 本地预过滤：不含支付关键词的通知直接跳过，不浪费AI调用
            // 负过滤：含这些词的通知即使有支付关键词也不是真实消费
            // 白名单：从SharedPreferences读取，用户可自由增删
            val allKeywords = getWhitelist()
            if (allKeywords.none { raw.contains(it) }) {
                return@withContext CaptureResult(null, false)
            }

            // 黑名单：含这些词的通知直接跳过，不浪费AI调用
            val blacklist = listOf(
                "签到", "邀请", "活动", "抽奖", "优惠券", "红包领取",
                "社保", "社保卡", "医保卡",
                "人民币", "您有", "到账通知", "余额变动",
                "验证码", "校验码", "动态码", "登录验证",
                "还款提醒", "账单提醒", "出账", "最低还款",
                "物流", "发货", "签收", "派送", "取件",
                "评价", "晒单", "售后", "退款申请",
                "信用", "额度", "提额", "借款", "贷款"
            )
            if (blacklist.any { raw.contains(it) }) {
                addLog("通知监听", "黑名单过滤", raw.take(100))
                return@withContext CaptureResult(null, false)
            }

            // 直接把原始通知文本给AI分析，不做本地预解析
            val aiResult = recognizeNotificationWithAi(raw).getOrNull()

            if (aiResult == null) {
                // AI未返回结果，用本地解析器兜底
                val localParsed = parser.parse(raw, packageName)
                if (localParsed != null && localParsed.amountCents > 0) {
                    addLog("通知监听", "AI未返回结果，本地兜底", "${localParsed.merchantName} ¥${localParsed.amountCents / 100.0}")
                    val result = upsertParsedPayment(localParsed, SourceType.NOTIFICATION, null)
                    if (result.created) {
                        addLog("通知监听", "本地兜底记账成功", "${localParsed.merchantName} ¥${localParsed.amountCents / 100.0}")
                    }
                    dao.insertRawCapture(RawCaptureEntity(
                        sourceType = SourceType.NOTIFICATION, paymentApp = app,
                        capturedAt = System.currentTimeMillis(),
                        titleHash = stableSha256(title.orEmpty()),
                        textHash = stableSha256(text.orEmpty()),
                        parsedTransactionId = result.transaction?.id
                    ))
                    return@withContext result
                }
                addLog("通知监听", "AI未返回结果，本地也无法解析，跳过", raw.take(80))
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
                ParsedPayment(
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


    suspend fun importScreenshot(
                uri: Uri,
                displayName: String? = null,
                capturedAtMs: Long? = null
            ): Long = withContext(Dispatchers.IO) {
                // 手动导入若未传文件名，从 URI 取 DISPLAY_NAME，便于解析包名/时间
                val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: displayName(uri).takeIf { it.isNotBlank() }
                val stored = screenshotStorage.saveEncryptedFromUri(uri)
                val bitmap = screenshotStorage.loadBitmap(stored.encryptedPath)
                try {
                    val ocrText = bitmap?.let { ocrRecognizer.recognize(it) }.orEmpty()
                    createScreenshotRecord(
                        path = stored.encryptedPath,
                        source = ScreenshotSourceType.MANUAL_UPLOAD,
                        ocrText = ocrText,
                        bitmap = bitmap,
                        displayName = resolvedName,
                        capturedAtMs = capturedAtMs
                    ).first
                } finally {
                    bitmap?.recycle()
                }
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

    suspend fun clearAllPendingScreenshots() = withContext(Dispatchers.IO) {
        val screenshots = dao.getUnconfirmedScreenshots()
        screenshots.forEach { screenshotStorage.delete(it.encryptedFilePath) }
        dao.deleteUnconfirmedScreenshots()
        screenshots.size
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
                // 待确认页也从 OCR 元数据兜底支付App/时间（文件名包名已写入 OCR）
                val pkgFromMeta = Regex("""截图来源应用包名：([^\s\n]+)""").find(rawText)?.groupValues?.getOrNull(1)
                val appFromMeta = PaymentApp.fromPackage(pkgFromMeta)
                val fileName = Regex("""截图文件名：([^\n]+)""").find(rawText)?.groupValues?.getOrNull(1)
                val appFromName = PaymentApp.fromPackage(PaymentApp.packageFromScreenshotName(fileName))
                val fallbackApp = if (appFromMeta != PaymentApp.UNKNOWN) appFromMeta else appFromName
                val fallbackPaidAt = screenshot.capturedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                val ai = extractAiSuggestion(rawText)
                val parsed = (ai?.toParsedPayment(fallbackApp = fallbackApp, fallbackPaidAt = fallbackPaidAt)
                    ?: parser.parse(rawText)?.let { local ->
                        local.copy(
                            paymentApp = if (local.paymentApp == PaymentApp.UNKNOWN && fallbackApp != PaymentApp.UNKNOWN) fallbackApp else local.paymentApp,
                            paidAt = if (local.paidAt <= 0) fallbackPaidAt else local.paidAt
                        )
                    })
                val category = parsed?.let { chooseCategory(it, rules) } ?: BuiltInCategories.OTHER
                PendingScreenshotReview(
                    id = screenshot.id,
                    capturedAt = screenshot.capturedAt,
                    sourceType = screenshot.sourceType,
                    ocrPreview = buildPendingPreview(rawText, ai),
                    suggestedMerchant = parsed?.merchantName?.takeIf { it != "未识别商户" } ?: "截图消费",
                    suggestedAmountCents = parsed?.amountCents,
                    suggestedCategoryId = category,
                    suggestedPaymentApp = parsed?.paymentApp ?: fallbackApp,
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
        paymentApp: PaymentApp,
        excludeFromStats: Boolean? = null,
        excludeFromBudget: Boolean? = null
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
            excludeFromStats = excludeFromStats ?: existing.excludeFromStats,
            excludeFromBudget = excludeFromBudget ?: existing.excludeFromBudget,
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

    suspend fun deleteTransactionsWithImages(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val idList = ids.toList()
        val screenshots = dao.getScreenshotsByTransactionIds(idList)
        screenshots.forEach { screenshotStorage.delete(it.encryptedFilePath) }
        dao.deleteScreenshotsByTransactionIds(idList)
        dao.deleteTransactions(idList)
    }

    suspend fun upsertCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        dao.upsertCategory(category)
    }

    /**
     * 分类上移/下移。
     * 同级列表（同 type 且同 parentId）内按当前顺序找相邻项交换 sortOrder。
     * 交换前先规整化一遍，避免历史数据里 sortOrder 重复导致换了没反应。
     */
    suspend fun moveCategory(categoryId: String, up: Boolean): Boolean = withContext(Dispatchers.IO) {
        val all = dao.getCategories()
        val target = all.firstOrNull { it.id == categoryId } ?: return@withContext false
        val siblings = all.filter { it.type == target.type && it.parentId == target.parentId }
            .sortedBy { it.sortOrder }
        if (siblings.size < 2) return@withContext false
        // 规整化：去掉重复 sortOrder
        val needNormalize = siblings.map { it.sortOrder }.toSet().size != siblings.size
        val ordered = if (needNormalize) {
            dao.normalizeCategoryOrder(siblings.map { it.id })
            dao.getCategories().filter { it.type == target.type && it.parentId == target.parentId }
                .sortedBy { it.sortOrder }
        } else siblings
        val index = ordered.indexOfFirst { it.id == categoryId }
        if (index < 0) return@withContext false
        val swapIndex = if (up) index - 1 else index + 1
        if (swapIndex !in ordered.indices) return@withContext false
        val a = ordered[index]
        val b = ordered[swapIndex]
        dao.swapCategoryOrder(a.id, a.sortOrder, b.id, b.sortOrder)
        true
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

    private suspend fun createScreenshotRecord(
            path: String,
            source: ScreenshotSourceType,
            ocrText: String,
            bitmap: Bitmap? = null,
            displayName: String? = null,
            capturedAtMs: Long? = null
        ): Pair<Long, CaptureResult> {
            val aiConfig = aiSettingsStore.loadConfig()
            val pkgFromName = PaymentApp.packageFromScreenshotName(displayName)
            val appFromName = PaymentApp.fromPackage(pkgFromName)
            val timeFromName = PaymentApp.timeFromScreenshotName(displayName)
            val captureTime = capturedAtMs?.takeIf { it > 0 }
                ?: timeFromName
                ?: System.currentTimeMillis()

            // 把截图元数据塞进 OCR 文本，给 AI / 本地解析更多上下文
            val metaLines = buildList {
                if (!displayName.isNullOrBlank()) add("截图文件名：$displayName")
                if (!pkgFromName.isNullOrBlank()) add("截图来源应用包名：$pkgFromName")
                if (appFromName != PaymentApp.UNKNOWN) add("截图来源支付App：${appFromName.label}")
                add("截图时间：${java.time.Instant.ofEpochMilli(captureTime).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()}")
            }
            val enrichedOcr = (metaLines.joinToString("\n") + "\n" + ocrText).trim()

            // 先跑本地 OCR，再把 OCR 文本和压缩截图一起给 AI。
            // 这样即使模型视觉能力弱，也能从 OCR 文本中提取金额。
            val aiAttempt = if (bitmap != null && aiConfig.configured) {
                recognizeWithAi(bitmap, enrichedOcr)
            } else {
                null
            }
            val ai = aiAttempt?.getOrNull()
            val aiError = aiAttempt?.exceptionOrNull()?.message
            if (!aiError.isNullOrBlank()) {
                addLog("截图补记", "AI识别失败，本地OCR兜底", aiError.take(180))
            } else if (ai != null && !ai.canAutoImport()) {
                val reason = listOfNotNull(
                    ai.reason.takeIf { it.isNotBlank() },
                    ai.amountCents?.let { "¥${it / 100.0}" },
                    "confidence=${ai.confidence}"
                ).joinToString(" / ")
                addLog("截图补记", "AI识别不确定，本地OCR兜底", reason.take(180))
            }

            val storedText = combineOcrAndAi(enrichedOcr, ai, aiError)
            val screenshotId = dao.insertScreenshot(
                ScreenshotCaptureEntity(
                    encryptedFilePath = path,
                    sourceType = source,
                    capturedAt = captureTime,
                    ocrTextHash = stableSha256(storedText),
                    ocrRawTextEncrypted = cryptoStore.encryptToString(storedText),
                    status = ScreenshotStatus.PENDING_REVIEW
                )
            )

            val localParsed = parser.parse(enrichedOcr)?.let { local ->
                // 本地解析也用文件名元数据补支付App/时间
                local.copy(
                    paymentApp = if (local.paymentApp == PaymentApp.UNKNOWN && appFromName != PaymentApp.UNKNOWN) appFromName else local.paymentApp,
                    paidAt = if (local.paidAt <= 0) captureTime else local.paidAt
                )
            }

            // 本地先拦聊天/非支付截图，避免“我已经付款了”这类会话误入账
            if (looksLikeChatOrNonPaymentScreenshot(enrichedOcr, ai)) {
                addLog("截图补记", "非支付截图，跳过自动入账", enrichedOcr.take(80))
                return screenshotId to CaptureResult(null, false)
            }

            // AI成功识别且可自动入账
            val parsed = if (ai != null && ai.canAutoImport()) {
                ai.toParsedPayment(fallbackApp = appFromName, fallbackPaidAt = captureTime)
            } else {
                // AI未配置或失败时，使用本地OCR解析
                localParsed
            }?.let { p ->
                // 最终兜底：支付App / 时间 / 分类
                val app = if (p.paymentApp == PaymentApp.UNKNOWN && appFromName != PaymentApp.UNKNOWN) appFromName else p.paymentApp
                val paidAt = if (p.paidAt <= 0) captureTime else p.paidAt
                // 京东外卖/美团外卖等：OCR 含外卖关键词时，分类偏向餐饮
                val categoryHint = when {
                    p.categoryHint.isNotBlank() -> p.categoryHint
                    enrichedOcr.contains("外卖") || enrichedOcr.contains("骑手") || enrichedOcr.contains("送达") -> "餐饮"
                    app == PaymentApp.JD && (enrichedOcr.contains("外卖") || enrichedOcr.contains("合计")) -> "餐饮"
                    app == PaymentApp.MEITUAN -> "餐饮"
                    app == PaymentApp.PINDUODUO || app == PaymentApp.TAOBAO || app == PaymentApp.TMALL || app == PaymentApp.JD -> "购物"
                    else -> p.categoryHint
                }
                p.copy(paymentApp = app, paidAt = paidAt, categoryHint = categoryHint)
            }

            val canAutoConfirm = if (ai != null && ai.canAutoImport()) {
                true
            } else {
                (localParsed?.confidence ?: 0f) >= LOCAL_AUTO_CONFIRM_CONFIDENCE
            }
            if (parsed != null && canAutoConfirm) {
                val result = upsertParsedPayment(parsed, SourceType.SCREENSHOT, screenshotId)
                dao.getScreenshot(screenshotId)?.let {
                    dao.updateScreenshot(
                        it.copy(
                            parsedTransactionId = result.transaction?.id,
                            status = if (result.transaction != null) ScreenshotStatus.CONFIRMED else ScreenshotStatus.PENDING_REVIEW
                        )
                    )
                }
                if (result.created) {
                    addLog(
                        "截图补记",
                        "自动记账成功",
                        "${parsed.merchantName} ¥${parsed.amountCents / 100.0} [${parsed.paymentApp.label}]"
                    )
                } else if (result.transaction != null) {
                    addLog("截图合并", "已合并到已有账单并补充凭证", "#${result.transaction.id} ${result.transaction.merchantName}")
                }
                return screenshotId to result
            }
            return screenshotId to CaptureResult(null, false)
        }

    private suspend fun upsertParsedPayment(parsed: ParsedPayment, sourceType: SourceType, screenshotId: Long?): CaptureResult {
            val safeAmount = sanitizeAmountCents(parsed.amountCents)
            if (safeAmount <= 0L) {
                addLog("记账拦截", "金额异常跳过", "amountCents=${parsed.amountCents} ${parsed.merchantName}")
                return CaptureResult(null, false)
            }
            val safeParsed = if (safeAmount == parsed.amountCents) parsed else parsed.copy(amountCents = safeAmount)
            val dedupe = parser.dedupeKey(safeParsed)
            dao.findByDedupeKey(dedupe)?.let { existing ->
                val merged = mergeIntoExistingTransaction(existing, safeParsed, screenshotId, sourceType)
                return CaptureResult(merged, false)
            }
            // 截图与通知合并窗口放宽到 30 分钟；同金额同类型优先合并
            val windowMs = if (sourceType == SourceType.SCREENSHOT || sourceType == SourceType.NOTIFICATION) {
                30 * 60 * 1000L
            } else {
                DEDUP_WINDOW_MS
            }
            val from = safeParsed.paidAt - windowMs
            val to = safeParsed.paidAt + windowMs
            val similar = dao.findSimilar(safeParsed.paymentApp, safeParsed.amountCents, safeParsed.type, from, to)
                ?: if (sourceType in automaticSources) {
                    dao.findSimilarAutoAnyApp(safeParsed.amountCents, safeParsed.type, from, to, automaticSources)
                } else null
            if (similar != null) {
                val merged = mergeIntoExistingTransaction(similar, safeParsed, screenshotId, sourceType)
                return CaptureResult(merged, false)
            }

            val rules = dao.getMerchantRules()
            val category = chooseCategory(safeParsed, rules)
            val now = System.currentTimeMillis()
            val id = dao.insertTransaction(
                            TransactionEntity(
                                amountCents = safeParsed.amountCents,
                                merchantName = safeParsed.merchantName,
                                categoryId = category,
                                paymentApp = safeParsed.paymentApp,
                                paidAt = safeParsed.paidAt,
                                sourceType = sourceType,
                                screenshotId = screenshotId,
                                dedupeKey = dedupe,
                                confidence = safeParsed.confidence,
                                note = safeParsed.note.takeIf { it.isNotBlank() }.orEmpty(),
                                createdAt = now,
                                updatedAt = now,
                                type = safeParsed.type
                            )
                        )
                        val transaction = dao.getTransaction(id)
                        return CaptureResult(transaction, transaction != null)
                    }

    /**
     * 合并记账：通知先记一笔（可能信息不全），截图后到则补全字段并挂凭证，不新建第二笔。
     */
    private suspend fun mergeIntoExistingTransaction(
        existing: TransactionEntity,
        parsed: ParsedPayment,
        screenshotId: Long?,
        sourceType: SourceType
    ): TransactionEntity {
        var updated = existing
        var changed = false

        // 1) 挂截图凭证
        if (screenshotId != null) {
            if (existing.screenshotId == null || existing.screenshotId != screenshotId) {
                updated = updated.copy(screenshotId = screenshotId)
                changed = true
            }
            dao.getScreenshot(screenshotId)?.let { ss ->
                if (ss.parsedTransactionId != existing.id || ss.status != ScreenshotStatus.CONFIRMED) {
                    dao.updateScreenshot(
                        ss.copy(
                            parsedTransactionId = existing.id,
                            status = ScreenshotStatus.CONFIRMED
                        )
                    )
                }
            }
        }

        // 2) 商户名：现有是默认/平台名，或新商户更具体时补全
        val newMerchant = parsed.merchantName.trim()
        if (newMerchant.isNotBlank() && newMerchant != "AI识别消费" && isGenericMerchant(existing.merchantName)) {
            if (newMerchant != existing.merchantName) {
                updated = updated.copy(merchantName = newMerchant)
                changed = true
            }
        } else if (
            newMerchant.isNotBlank() &&
            newMerchant != "AI识别消费" &&
            newMerchant.length > existing.merchantName.length &&
            !isGenericMerchant(newMerchant) &&
            (existing.merchantName.contains(newMerchant) || newMerchant.contains(existing.merchantName.take(4)))
        ) {
            updated = updated.copy(merchantName = newMerchant)
            changed = true
        }

        // 3) 备注：截图通常有商品名，优先补到 note
        val newNote = parsed.note.trim()
        if (newNote.isNotBlank()) {
            val cats = setOf("购物", "餐饮", "交通", "生活缴费", "娱乐", "医疗", "教育", "转账", "人情", "退款", "工资", "奖金", "理财", "其他", "宠物")
            if (existing.note.isBlank() || existing.note in cats || existing.note == existing.categoryId) {
                if (newNote !in cats && newNote != existing.note) {
                    updated = updated.copy(note = newNote)
                    changed = true
                }
            }
        }

        // 4) 分类：现有是「其他」时，用新解析分类替换
        if (existing.categoryId == BuiltInCategories.OTHER || existing.categoryId.isBlank()) {
            val rules = dao.getMerchantRules()
            val better = chooseCategory(parsed, rules)
            if (better != BuiltInCategories.OTHER && better != existing.categoryId) {
                updated = updated.copy(categoryId = better)
                changed = true
            }
        }

        // 5) 支付 App：UNKNOWN 时用新值
        if (existing.paymentApp == PaymentApp.UNKNOWN && parsed.paymentApp != PaymentApp.UNKNOWN) {
            updated = updated.copy(paymentApp = parsed.paymentApp)
            changed = true
        }

        // 6) 时间：截图解析到更可信的支付时间时更新
        if (sourceType == SourceType.SCREENSHOT && parsed.paidAt > 0) {
            val delta = kotlin.math.abs(parsed.paidAt - existing.paidAt)
            if (delta > 2 * 60 * 1000L) {
                updated = updated.copy(paidAt = parsed.paidAt)
                changed = true
            }
        }

        // 7) 置信度取更高
        if (parsed.confidence > existing.confidence) {
            updated = updated.copy(confidence = parsed.confidence)
            changed = true
        }

        if (changed) {
            updated = updated.copy(updatedAt = System.currentTimeMillis())
            dao.updateTransaction(updated)
            addLog(
                if (sourceType == SourceType.SCREENSHOT) "截图合并" else "合并记账",
                "补充已有账单",
                "#${existing.id} ${updated.merchantName} ¥${updated.amountCents / 100.0}"
            )
        } else if (screenshotId != null) {
            addLog("截图合并", "同笔账单已存在，已关联凭证", "#${existing.id}")
        }
        return updated
    }

    private fun isGenericMerchant(name: String): Boolean {
        val n = name.trim()
        if (n.isBlank()) return true
        return n == "未知" || n == "导入消费" || n == "未识别商户" || n == "导入收入" ||
            n == "AI识别消费" || n == "未命名消费" || n == "AI添加" ||
            n.startsWith("微信支付") || n.startsWith("支付宝") || n.startsWith("京东支付") ||
            n == "拼多多" || n == "淘宝" || n == "天猫" || n == "抖音" || n == "美团"
    }

    private suspend fun supplementTransaction(existing: TransactionEntity, newMerchant: String, pageText: String): TransactionEntity? {
        var updated = existing
        var changed = false

        if (newMerchant.isNotBlank() && newMerchant.length >= 2 && isGenericMerchant(existing.merchantName)) {
            updated = updated.copy(merchantName = newMerchant)
            changed = true
        }

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

    private suspend fun recognizeWithAiRaw(bitmap: Bitmap): Result<AiParsedPayment>? {
        val config = aiSettingsStore.loadConfig()
        if (!config.configured) return null
        return aiRecognizer.recognizeRaw(bitmap, config)
    }

    suspend fun recognizeAccessibilityScreenshot(bitmap: Bitmap, config: AiRecognitionConfig): AiParsedPayment? {
        return try {
            aiRecognizer.recognize(bitmap, config, "").getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun recognizeNotificationWithAi(rawText: String): Result<AiParsedPayment?> {
        val config = aiSettingsStore.loadConfig()
        android.util.Log.d(TAG, "recognizeNotificationWithAi: configured=${config.configured}, url=${config.apiUrl.take(40)}, model=${config.model}, text=${rawText.take(50)}")
        if (!config.configured) {
            android.util.Log.w(TAG, "AI未配置，跳过")
            return Result.success(null)
        }
        return aiMutex.withLock {
            // 每次 AI 调用间隔至少 1.5 秒，防止限流
            val elapsed = System.currentTimeMillis() - lastAiCallTime
            if (elapsed < 1500) {
                kotlinx.coroutines.delay(1500 - elapsed)
            }
            var lastError = ""
            repeat(3) { attempt ->
                try {
                    lastAiCallTime = System.currentTimeMillis()
                    android.util.Log.d(TAG, "AI调用中... (attempt ${attempt + 1}/3)")
                    val result = aiRecognizer.recognizePaymentText(rawText, config)
                    val ai = result.getOrNull()
                    if (ai != null) {
                        android.util.Log.d(TAG, "AI返回: amount=${ai.amountCents}, merchant=${ai.merchantName}, isSpam=${ai.isSpam}, type=${ai.type}")
                    } else {
                        android.util.Log.w(TAG, "AI返回null, exception=${result.exceptionOrNull()?.message}")
                    }
                    return@withLock result.map { it }
                } catch (e: Exception) {
                    aiRecognizer.recordFailure()
                    lastError = e.message?.take(100) ?: "未知错误"
                    android.util.Log.e(TAG, "AI调用失败(attempt ${attempt + 1}/3): ${e.message?.take(100)}")
                    if (attempt < 2) {
                        addLog("通知监听", "AI调用失败(${attempt+1}/3)，2秒后重试", lastError)
                        kotlinx.coroutines.delay(2000L)
                    } else {
                        addLog("通知监听", "AI调用失败，3次重试均失败", lastError)
                    }
                }
            }
            Result.failure(Exception("AI调用失败: $lastError"))
        }
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


    private fun looksLikeChatOrNonPaymentScreenshot(ocrText: String, ai: AiParsedPayment?): Boolean {
        if (ai?.isSpam == true) return true
        val text = buildString {
            append(ocrText)
            append(' ')
            if (ai != null) {
                append(ai.reason)
                append(' ')
                append(ai.note)
                append(' ')
                append(ai.merchantName)
                append(' ')
                append(ai.rawJson)
            }
        }
        val chatHints = listOf(
                    "我已经付款", "已付款了", "转文字", "按住 说话", "按住说话",
                    "表情", "语音", "视频通话", "聊天", "会话", "消息", "发送", "按住"
                )
                val paymentHints = listOf(
                    "支付成功", "付款成功", "交易成功", "订单详情", "账单详情", "实付", "实付款",
                    "退款到账", "收款方", "商户单号", "交易单号", "支付方式", "当前状态",
                    "商家转账", "转账时间", "付款商家", "收款方式", "付款单号", "下单时间", "支付时间"
                )
        val hasChat = chatHints.any { text.contains(it) }
        val hasPayment = paymentHints.any { text.contains(it) }
        // 聊天特征明显且没有支付页特征 -> 非支付截图
        return hasChat && !hasPayment
    }

    private fun AiParsedPayment.canAutoImport(): Boolean {
            if (isSpam) return false
            if (amountCents == null || amountCents <= 0L) return false
            if (confidence < AI_AUTO_IMPORT_CONFIDENCE) return false
            val evidence = listOf(merchantName, note, categoryHint, reason, rawJson, type.name).joinToString(" ")
            // 明确账单/支付特征（含微信商家转账收款页）
            val paymentHints = listOf(
                "支付成功", "付款成功", "交易成功", "订单详情", "账单详情", "实付", "实付款", "退款到账",
                "商家转账", "转账时间", "付款商家", "收款方式", "付款单号", "到账", "零钱", "财付通",
                "下单时间", "支付时间", "交易单号", "商户单号", "当前状态"
            )
            val hasPaymentEvidence = paymentHints.any { evidence.contains(it) }

            // 聊天/会话类截图禁止自动入账（不要用单独的“微信”二字，否则微信支付/微信账单都会被误拦）
            val chatHints = listOf(
                "聊天", "会话", "消息", "我已经付款", "已付款了",
                "语音", "视频通话", "转文字", "按住说话", "按住 说话", "表情"
            )
            if (chatHints.any { evidence.contains(it) } && !hasPaymentEvidence) {
                return false
            }
            // 必须有商户或备注之一；收入到账可放宽到付款商家/平台名
            val merchant = merchantName.trim()
            if ((merchant.isBlank() || merchant == "AI识别消费") && note.isBlank() && !hasPaymentEvidence) {
                return false
            }
            return true
        }

    private fun AiParsedPayment.toParsedPayment(
            fallbackApp: PaymentApp = PaymentApp.UNKNOWN,
            fallbackPaidAt: Long = System.currentTimeMillis()
        ): ParsedPayment? {
            val amount = amountCents ?: return null
            if (isSpam) return null
            val merchant = merchantName.ifBlank { note.ifBlank { "AI识别消费" } }
            val app = if (paymentApp == PaymentApp.UNKNOWN && fallbackApp != PaymentApp.UNKNOWN) fallbackApp else paymentApp
            val safePaidAt = paidAt?.takeIf { it > 0 } ?: fallbackPaidAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            return ParsedPayment(
                amountCents = amount,
                merchantName = merchant,
                paymentApp = app,
                paidAt = safePaidAt,
                confidence = confidence,
                rawText = listOf(rawJson, categoryHint, note, reason).joinToString("\n"),
                type = type,
                categoryHint = categoryHint,
                note = note,
                isSpam = isSpam
            )
        }

    private fun chooseCategory(parsed: ParsedPayment, rules: List<MerchantRuleEntity>): String {
        categoryFromHint(parsed.categoryHint, parsed.type)?.let { return it }
        // hint 没给出可用分类时，用商户名 + 备注再试一次二级分类
        // 场景：AI 只回「生活缴费」，但商户是「国网安徽省合肥市电力公司」，应落到「电费」
        if (parsed.type == TransactionType.EXPENSE) {
            val extra = listOf(parsed.merchantName, parsed.note).joinToString(" ").trim()
            if (extra.isNotBlank()) subCategoryFromHint(extra)?.let { return it }
        }
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
            // 先匹配二级分类（更精确），命中不了再退回一级分类
            TransactionType.EXPENSE -> subCategoryFromHint(text) ?: when {
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

    /**
     * 二级分类命中。
     * 顺序很重要：先判更具体的词（「电费」在「缴费」之前），避免「电费充值」只落到「生活缴费」。
     */
    private fun subCategoryFromHint(text: String): String? = when {
        // 生活缴费下钻
        listOf("电费", "电力", "供电", "国网", "购电", "充电费").any { text.contains(it) } -> BuiltInCategories.BILLS_ELECTRIC
        listOf("水费", "自来水", "供水", "水务").any { text.contains(it) } -> BuiltInCategories.BILLS_WATER
        listOf("燃气", "天然气", "煤气", "燃气费", "燃气公司").any { text.contains(it) } -> BuiltInCategories.BILLS_GAS
        listOf("房租", "租金", "月租", "押一付").any { text.contains(it) } -> BuiltInCategories.BILLS_RENT
        listOf("宽带", "网费", "话费", "流量费", "手机充值", "电信", "联通", "移动通信").any { text.contains(it) } -> BuiltInCategories.BILLS_INTERNET
        // 餐饮下钻
        listOf("早餐", "早点", "包子", "豆浆").any { text.contains(it) } -> BuiltInCategories.FOOD_BREAKFAST
        listOf("午餐", "中饭", "午饭").any { text.contains(it) } -> BuiltInCategories.FOOD_LUNCH
        listOf("晚餐", "晚饭", "夜宵").any { text.contains(it) } -> BuiltInCategories.FOOD_DINNER
        listOf("外卖", "骑手", "美团外卖", "饿了么").any { text.contains(it) } -> BuiltInCategories.FOOD_TAKEOUT
        listOf("零食", "薯片", "饼干", "糖").any { text.contains(it) } -> BuiltInCategories.FOOD_SNACK
        listOf("水果", "生鲜果").any { text.contains(it) } -> BuiltInCategories.FOOD_FRUIT
        // 交通下钻
        listOf("加油", "汽油", "柴油", "中石化", "中石油", "充电桩", "换电").any { text.contains(it) } -> BuiltInCategories.TRANSPORT_FUEL
        listOf("打车", "网约车", "滴滴", "出租车", "快车", "专车").any { text.contains(it) } -> BuiltInCategories.TRANSPORT_TAXI
        listOf("地铁", "轨道交通").any { text.contains(it) } -> BuiltInCategories.TRANSPORT_METRO
        listOf("公交", "巴士", "公共交通").any { text.contains(it) } -> BuiltInCategories.TRANSPORT_BUS
        listOf("停车", "车位").any { text.contains(it) } -> BuiltInCategories.TRANSPORT_PARKING
        // 购物下钻（放在娱乐前：「运动鞋」应归服装，不是运动）
        listOf("服装", "衣服", "鞋", "裤", "外套", "服饰", "T恤", "卫衣").any { text.contains(it) } -> BuiltInCategories.SHOPPING_CLOTHES
        listOf("日用", "纸巾", "洗衣", "牙膏", "清洁").any { text.contains(it) } -> BuiltInCategories.SHOPPING_DAILY
        listOf("数码", "手机", "电脑", "耳机", "充电器", "配件").any { text.contains(it) } -> BuiltInCategories.SHOPPING_DIGITAL
        // 娱乐下钻
        listOf("游戏", "点券", "皮肤", "手游", "steam").any { text.contains(it, ignoreCase = true) } -> BuiltInCategories.ENTERTAINMENT_GAME
        listOf("电影", "影城", "影院", "票房").any { text.contains(it) } -> BuiltInCategories.ENTERTAINMENT_MOVIE
        listOf("健身", "球馆", "游泳", "运动馆", "运动中心", "羽毛球", "篮球场").any { text.contains(it) } -> BuiltInCategories.ENTERTAINMENT_SPORT
        else -> null
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

    suspend fun addLog(source: String, action: String, detail: String): Long? = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "addLog: source=$source, action=$action, detail=${detail.take(50)}")
        runCatching {
            val id = dao.insertLog(AutoBookLogEntry(createdAt = System.currentTimeMillis(), source = source, action = action, detail = detail))
            android.util.Log.d(TAG, "addLog成功: id=$id")
            id
        }.onFailure { error ->
            android.util.Log.e(TAG, "写入操作日志失败: ${error.message}", error)
        }.getOrNull()
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
        suspend fun getMonthExpense(start: Long): Long = withContext(Dispatchers.IO) {
            runCatching { dao.getExpenseBetween(start, monthEndMillisFrom(start)) }.getOrDefault(0L)
        }
        suspend fun getMonthIncome(start: Long): Long = withContext(Dispatchers.IO) {
            runCatching { dao.getIncomeBetween(start, monthEndMillisFrom(start)) }.getOrDefault(0L)
        }
        suspend fun getTodayExpense(start: Long): Long = withContext(Dispatchers.IO) {
            runCatching { dao.getExpenseBetween(start, start + DAY_MS - 1) }.getOrDefault(0L)
        }
        suspend fun getTodayIncome(start: Long): Long = withContext(Dispatchers.IO) {
            runCatching { dao.getIncomeBetween(start, start + DAY_MS - 1) }.getOrDefault(0L)
        }

        /** 由「月周期起点」推出该周期终点（起点 + 1 个月 - 1ms），兼容自定义月周期起始日 */
        private fun monthEndMillisFrom(start: Long): Long {
            val zone = java.time.ZoneId.systemDefault()
            val startDate = java.time.Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            return startDate.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        }

        // ====== 报表聚合 ======
        /**
         * 一次性拉齐某个区间的全部报表数据。
         * 全走 SQL 聚合，避免 UI 层对「最近 500 条」内存列表做统计导致年报失真。
         *
         * @param start 区间起点（含）
         * @param end 区间终点（含）
         * @param type 支出 / 收入
         * @param prevStart 上一周期起点，用于环比；为 null 时环比返回 0
         * @param prevEnd 上一周期终点
         */
        suspend fun loadReport(
            start: Long,
            end: Long,
            type: TransactionType,
            prevStart: Long?,
            prevEnd: Long?,
        ): ReportSnapshot = withContext(Dispatchers.IO) {
            val counterType = if (type == TransactionType.EXPENSE) TransactionType.INCOME else TransactionType.EXPENSE
            runCatching {
                val excluded = dao.getExcludedSummary(start, end)
                ReportSnapshot(
                    summary = dao.getRangeSummary(start, end, type),
                    prevTotal = if (prevStart != null && prevEnd != null) dao.getRangeSummary(prevStart, prevEnd, type).total else 0L,
                    counterTotal = dao.getRangeSummary(start, end, counterType).total,
                    categories = dao.getCategoryTotals(start, end, type),
                    merchants = dao.getMerchantTotals(start, end, type, REPORT_RANK_LIMIT),
                    paymentApps = dao.getPaymentAppTotals(start, end, type),
                    days = dao.getDayTotals(start, end, type),
                    months = dao.getMonthTotals(start, end, type),
                    weekdays = dao.getWeekdayTotals(start, end, type),
                    budgetSpent = dao.getBudgetSpent(start, end, type),
                    budgetCategories = dao.getBudgetCategoryTotals(start, end, type),
                    excludedTotal = excluded.total,
                    excludedCount = excluded.cnt,
                )
            }.getOrElse { ReportSnapshot() }
        }

        /** 分类下钻：该分类在区间内的商家排行 + 大额明细 */
        suspend fun loadCategoryDrillDown(
            start: Long,
            end: Long,
            type: TransactionType,
            categoryId: String,
        ): CategoryDrillDown = withContext(Dispatchers.IO) {
            runCatching {
                CategoryDrillDown(
                    merchants = dao.getMerchantTotalsInCategory(start, end, type, categoryId, REPORT_RANK_LIMIT),
                    transactions = dao.getTransactionsInCategoryRange(start, end, type, categoryId, REPORT_DRILL_TX_LIMIT),
                )
            }.getOrElse { CategoryDrillDown() }
        }

        /** 最早一笔账单时间，用于「全部」区间 */
        suspend fun getEarliestPaidAt(): Long? = withContext(Dispatchers.IO) {
            runCatching { dao.getEarliestPaidAt() }.getOrNull()
        }

        // ====== 预算 ======
        fun observeBudgets(): Flow<List<BudgetEntity>> = dao.observeBudgets()

        suspend fun saveBudget(categoryId: String, amountCents: Long) = withContext(Dispatchers.IO) {
            if (amountCents <= 0L) {
                dao.deleteBudget(categoryId)
            } else {
                dao.upsertBudget(BudgetEntity(categoryId, amountCents, System.currentTimeMillis()))
            }
        }

        suspend fun clearBudgets() = withContext(Dispatchers.IO) { dao.clearBudgets() }

        private fun sanitizeAmountCents(amountCents: Long): Long {
            return when {
                amountCents < 0L -> 0L
                amountCents > MAX_AMOUNT_CENTS -> 0L
                else -> amountCents
            }
        }

        fun observeChatMessages(): Flow<List<ChatMessage>> = chatDao.observeMessages()

    suspend fun sendChatMessage(userMessage: String, imageUri: String? = null, fileName: String? = null): String = withContext(Dispatchers.IO) {
        // 保存用户消息
        chatDao.insert(ChatMessage(role = "user", content = userMessage, imageUri = imageUri, fileName = fileName))

        // 构建账单上下文
        val recentTxs = dao.getRecentTransactions(CHAT_CONTEXT_LIMIT)
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
            // 构建用户消息内容（支持多模态）
            val userContent = if (imageUri != null) {
                // 读取并压缩图片，转base64
                val base64 = imageUriToBase64(imageUri)
                if (base64 != null) {
                    val contentArray = org.json.JSONArray()
                    contentArray.put(org.json.JSONObject().put("type", "text").put("text", prompt))
                    contentArray.put(org.json.JSONObject()
                        .put("type", "image_url")
                        .put("image_url", org.json.JSONObject()
                            .put("url", "data:image/jpeg;base64,$base64")))
                    contentArray
                } else {
                    prompt // 图片读取失败，降级为纯文本
                }
            } else {
                prompt
            }

            val body = org.json.JSONObject()
                .put("model", config.model)
                .put("messages", org.json.JSONArray()
                    .put(org.json.JSONObject().put("role", "system").put("content", "你是智能记账助手，简洁回答，中文"))
                    .put(org.json.JSONObject().put("role", "user").put("content", userContent)))
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
            val categories = dao.getCategories()
            val zone = java.time.ZoneId.systemDefault()
            val nowMs = System.currentTimeMillis()

            fun resolveCategoryId(raw: String, type: TransactionType = TransactionType.EXPENSE): String? {
                if (raw.isBlank()) return null
                return categories.firstOrNull { it.name.contains(raw) }?.id
                    ?: categories.firstOrNull { it.id == raw }?.id
            }

            suspend fun queryByTarget(t: String): List<TransactionEntity> {
                            return when {
                                t.isBlank() || t == "all" -> dao.getTransactions()
                                t.startsWith("id:") -> {
                                    val id = t.removePrefix("id:").toLongOrNull()
                                    if (id != null) listOfNotNull(dao.getTransaction(id)) else emptyList()
                                }
                                t.startsWith("date:") -> {
                                    val date = java.time.LocalDate.parse(t.removePrefix("date:"))
                                    val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                                    val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                    dao.getTransactionsBetween(startMs, endMs)
                                }
                                t.startsWith("month:") -> {
                                    val ym = java.time.YearMonth.parse(t.removePrefix("month:"))
                                    val startMs = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                    val endMs = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                    dao.getTransactionsBetween(startMs, endMs)
                                }
                                t.startsWith("category:") -> {
                                    val catId = resolveCategoryId(t.removePrefix("category:"))
                                    if (catId != null) dao.getTransactionsByCategory(catId) else emptyList()
                                }
                                t.startsWith("type:") -> {
                                    val txType = when (t.removePrefix("type:").uppercase()) {
                                        "EXPENSE" -> TransactionType.EXPENSE
                                        "INCOME" -> TransactionType.INCOME
                                        else -> TransactionType.OTHER
                                    }
                                    dao.getTransactionsByType(txType)
                                }
                                else -> dao.getTransactionsByMerchant(t)
                            }
                        }

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
                        resolveCategoryId(cat, txType) ?: BuiltInCategories.fallbackFor(txType)
                    } else BuiltInCategories.fallbackFor(txType)
                    val paidAt = if (paidAtStr.isNotBlank()) parseAiDateTime(paidAtStr) else System.currentTimeMillis()
                    addManualTransaction(merchant, (amount * 100).toLong(), catId, paidAt = paidAt, type = txType, note = note)
                    val timeStr = java.time.Instant.ofEpochMilli(paidAt).atZone(zone).toLocalTime().withSecond(0).withNano(0).toString()
                    "已添加: $merchant ¥$amount [${txType.label}] $timeStr"
                }

                // ====== 删除账单 ======
                "delete" -> {
                    val txs = queryByTarget(target)
                    if (txs.isEmpty()) {
                        "没有找到匹配的记录"
                    } else {
                        deleteTransactionsWithImages(txs.map { it.id })
                        "已删除 ${txs.size} 条记录"
                    }
                }

                // ====== 修改分类 ======
                "update_category" -> {
                    val catId = resolveCategoryId(value)
                        ?: return@withContext "找不到分类: $value"
                    val catName = categories.firstOrNull { it.id == catId }?.name ?: value
                    val updated = when {
                        target.isBlank() || target == "all" -> dao.updateAllCategories(catId, nowMs)
                        target.startsWith("id:") -> {
                            val id = target.removePrefix("id:").toLongOrNull()
                            val tx = if (id != null) dao.getTransaction(id) else null
                            if (tx != null) {
                                dao.updateTransaction(tx.copy(categoryId = catId, updatedAt = nowMs))
                                1
                            } else 0
                        }
                        else -> dao.updateCategoryByMerchant(target, catId, nowMs)
                    }
                    if (updated <= 0) "没有找到匹配的记录" else "已将 $updated 条记录分类改为$catName"
                }

                // ====== 修改商户名 ======
                "update_merchant" -> {
                    val updated = when {
                        target.startsWith("id:") -> {
                            val id = target.removePrefix("id:").toLongOrNull()
                            val tx = if (id != null) dao.getTransaction(id) else null
                            if (tx != null) {
                                dao.updateTransaction(tx.copy(merchantName = value, updatedAt = nowMs))
                                1
                            } else 0
                        }
                        target.isNotBlank() -> dao.updateMerchantNameByKeyword(target, value, nowMs)
                        else -> 0
                    }
                    if (updated <= 0) "没有找到匹配的记录" else "已将 $updated 条记录商户名改为$value"
                }

                // ====== 修改金额 ======
                "update_amount" -> {
                    val amount = value.toDoubleOrNull()
                    if (amount == null || amount <= 0) return@withContext "无效金额: $value"
                    if (!target.startsWith("id:")) return@withContext "请指定具体账单ID"
                    val id = target.removePrefix("id:").toLongOrNull()
                    val tx = if (id != null) dao.getTransaction(id) else null
                    if (tx == null) {
                        "请指定具体账单ID"
                    } else {
                        dao.updateTransaction(tx.copy(amountCents = (amount * 100).toLong(), updatedAt = nowMs))
                        "已修改 1 条记录金额为 ¥$amount"
                    }
                }

                // ====== 查询 ======
                "query" -> {
                    val txs = queryByTarget(target)
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
                    val total = dao.getTransactionCount().toInt()
                    val allIds = dao.getAllTransactionIds()
                    if (allIds.isNotEmpty()) {
                        val screenshots = dao.getScreenshotsByTransactionIds(allIds)
                        screenshots.forEach { screenshotStorage.delete(it.encryptedFilePath) }
                    }
                    dao.clearAllScreenshots()
                    dao.clearAllTransactions()
                    "已清空全部 $total 条账单记录"
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

    /**
     * 将图片URI转为base64字符串（JPEG格式，最大1280px，质量80%）
     */
    private fun imageUriToBase64(uriStr: String): String? {
        return try {
            val uri = android.net.Uri.parse(uriStr)
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return null
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            // 压缩到最大1280px
            val maxSize = 1280
            val w = originalBitmap.width
            val h = originalBitmap.height
            val scale = if (w > maxSize || h > maxSize) {
                maxSize.toFloat() / maxOf(w, h)
            } else 1f
            val bitmap = if (scale < 1f) {
                val nw = (w * scale).toInt()
                val nh = (h * scale).toInt()
                val scaled = android.graphics.Bitmap.createScaledBitmap(originalBitmap, nw, nh, true)
                if (scaled !== originalBitmap) originalBitmap.recycle()
                scaled
            } else originalBitmap

            try {
                val baos = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoBook", "imageUriToBase64 failed", e)
            null
        }
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
                if (cat.parentId != null) put("parentId", cat.parentId)
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

    // ====== 远程关于信息 ======
    data class AboutInfo(
        val title: String = "SKY自动记账",
        val description: String = "",
        val website: String = "",
        val github: String = "",
        val sectionTitle: String = "AI记账助手推荐",
        val recommendations: List<Recommendation> = emptyList()
    )
    data class Recommendation(val name: String = "", val url: String = "", val desc: String = "")

    suspend fun fetchAboutInfo(): AboutInfo = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://taxi.ssssvip.cc.cd/static/about.json")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = org.json.JSONObject(text)
            val recs = mutableListOf<Recommendation>()
            json.optJSONArray("recommendations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    recs.add(Recommendation(obj.optString("name"), obj.optString("url"), obj.optString("desc")))
                }
            }
            AboutInfo(
                title = json.optString("title", "SKY自动记账"),
                description = json.optString("description", ""),
                website = json.optString("website", ""),
                github = json.optString("github", ""),
                sectionTitle = json.optString("sectionTitle", "AI记账助手推荐"),
                recommendations = recs
            )
        } catch (e: Exception) {
            android.util.Log.w("AutoBook", "fetchAboutInfo failed: ${e.message}")
            AboutInfo()
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
                    isDefault = obj.optBoolean("isDefault", false),
                    parentId = if (obj.has("parentId") && !obj.isNull("parentId")) obj.optString("parentId") else null
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
            "notification" -> """你是中文支付通知记账解析器。从通知中提取一笔账单，只返回JSON。
字段：amount(number元), merchantName(string), paidAt(string yyyy-MM-dd HH:mm), paymentApp(string), type(string EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
规则：
1. isSpam=true：签到/邀请/活动/抽奖/优惠券/广告/营销/贷款/验证码/物流/App推送。此时amount=0, merchantName=""
2. isSpam=false：支付成功/付款成功/扣款成功/白条消费/退款到账。此时amount和merchantName必须填写
3. merchantName填真实商户名，如：美团外卖、肯德基、滴滴出行。不能填"AI识别消费"
4. categoryHint填分类，只能是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
5. note填商品名，如：咖啡、外卖、洗衣液。禁止填购物、餐饮等分类名，无法确定时留空"""
            "accessibility" -> """你是中文支付页面记账解析器。从支付成功页面文本中提取一笔消费记录，只返回JSON。
字段：amount(number元), merchantName(string), paidAt(string yyyy-MM-dd HH:mm), paymentApp(string), type(string EXPENSE/INCOME), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
规则：
1. 金额：优先匹配¥/￥后面的数字，如¥12.50。取页面最明显的金额
2. 商户名：从"付款给""商家""商品说明""收款方"等字段提取。不能填"AI识别消费"，无法确定时用支付App名
3. categoryHint填分类，只能是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
4. note填商品名，如：咖啡、洗衣液、手机壳。禁止填购物、餐饮等分类名，无法确定时留空
5. 支付成功页面+有金额→confidence≥0.65。非消费页面→confidence<0.5
6. 退款相关→type=INCOME, categoryHint=退款
7. isSpam=true：非支付页面、还款页面、账单提醒、广告页面"""
            "screenshot" -> """你是中文消费截图记账解析器。只从真实支付/账单/订单详情截图提取一笔账单，只返回JSON。
                        JSON字段：amount(number元), merchantName(string), paidAt(string yyyy-MM-dd HH:mm 或空字符串), paymentApp(string ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
                        规则：
                        1. isSpam=true：聊天记录、微信/QQ对话气泡页、仅“我已经付款了/已付款/好的收到”、无支付成功/订单/账单页特征
                        2. isSpam=false：支付成功/付款成功/交易成功/订单详情/账单详情/商家转账收款/退款到账，且有明确金额
                        3. categoryHint填分类：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
                        4. note填商品名，禁止填购物/餐饮等分类名；聊天截图 note 留空
                        5. paidAt 时间优先级：①下单时间/创建时间 ②支付时间/付款时间/转账时间 ③完成时间/成交时间。多个时间并存时必须用下单时间，禁止用完成时间顶替；看不到就空字符串，禁止编造年份
                        6. 金额带“+”/收款/到账 → type=INCOME；支出 type=EXPENSE
                        7. 聊天/非支付页：isSpam=true, amount=0, confidence≤0.2；真实账单页 confidence≥0.7"""
                        else -> ""
        }
    }

    // ====== 白名单关键词管理 ======
    private val WHITELIST_KEY = "whitelist_keywords"
    private val WHITELIST_INITIALIZED = "whitelist_initialized"
    private val KEY_NOTIFICATION_AUTO_BOOK = "notification_auto_book_enabled"
    private val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
    private val KEY_AUTO_DELETE_SCREENSHOT = "auto_delete_screenshot"
    private val KEY_MONTH_START_DAY = "month_start_day"

    private val DEFAULT_WHITELIST = listOf(
        // 支付动作
        "支付成功", "付款成功", "扣款成功", "已支付", "已付款",
        "消费成功", "交易成功", "购买成功",
        // 金额标识
        "¥", "元",
        // 支付平台
        "支付宝", "微信支付", "云闪付",
        // 餐饮
        "外卖", "餐饮", "奶茶", "咖啡", "买菜", "超市", "便利店",
        // 出行
        "打车", "滴滴", "地铁", "公交", "停车费", "加油", "高速费",
        // 购物
        "淘宝", "京东", "拼多多", "天猫", "抖音商城", "闲鱼",
        // 生活缴费
        "房租", "水费", "电费", "燃气费", "话费", "物业费",
        // 娱乐
        "电影票", "KTV", "游戏充值",
        // 医疗教育
        "药店", "医院", "挂号", "学费",
        // 人情
        "红包", "转账", "还款"
    )

    fun getWhitelist(): List<String> {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt("whitelist_version", 0)
        if (currentVersion < 2) {
            // Overwrite with new default list
            prefs.edit()
                .putString(WHITELIST_KEY, DEFAULT_WHITELIST.joinToString(","))
                .putInt("whitelist_version", 2)
                .apply()
            return DEFAULT_WHITELIST
        }
        val raw = prefs.getString(WHITELIST_KEY, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    /** 应用内开关：是否用通知监听自动记账（与系统通知使用权权限独立） */
    fun isNotificationAutoBookEnabled(): Boolean {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFICATION_AUTO_BOOK, true)
    }

    fun setNotificationAutoBookEnabled(enabled: Boolean) {
        context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_AUTO_BOOK, enabled)
            .apply()
    }

    /** 是否从系统「最近任务/多任务」列表隐藏本应用。默认 true，与旧版 manifest 行为一致 */
    fun isHideFromRecentsEnabled(): Boolean {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HIDE_FROM_RECENTS, true)
    }

    fun setHideFromRecentsEnabled(enabled: Boolean) {
        context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_FROM_RECENTS, enabled)
            .apply()
    }

    /** 截图记账成功后自动删除原截图 */
    fun isAutoDeleteScreenshotEnabled(): Boolean {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_DELETE_SCREENSHOT, false)
    }

    fun setAutoDeleteScreenshotEnabled(enabled: Boolean) {
        context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_DELETE_SCREENSHOT, enabled)
            .apply()
    }

    /**
     * 月度周期起始日（1-28）。默认 1 = 自然月。
     * 设成 10 表示「本月」= 本月 10 日 至 次月 9 日，适合按工资日理财。
     * 上限 28 是为了避免 2 月没有 29/30/31 号。
     */
    fun getMonthStartDay(): Int {
        val prefs = context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MONTH_START_DAY, 1).coerceIn(1, 28)
    }

    fun setMonthStartDay(day: Int) {
        context.getSharedPreferences("autobook_settings", android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MONTH_START_DAY, day.coerceIn(1, 28))
            .apply()
    }

    /** 标记/取消「不计入收支」与「不计入预算」 */
    suspend fun updateExcludeFlags(id: Long, excludeStats: Boolean, excludeBudget: Boolean) = withContext(Dispatchers.IO) {
        dao.updateExcludeFlags(id, excludeStats, excludeBudget, System.currentTimeMillis())
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
