package com.tao.autobook.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tao.autobook.AutoBookApplication
import com.tao.autobook.ai.AiParsedPayment
import com.tao.autobook.ai.AiRecognitionConfig
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.notify.AutoBookNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.coroutines.resume

class PaymentAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastProcessAt = 0L
    private var lastPageText = ""
    private var lastPackageName = ""
    private var lastFullScanAt = 0L

    // 支付动作触发状态
    private var paymentInitiated = false
    private var paymentInitiatedAt = 0L
    private var paymentTriggerApp = PaymentApp.UNKNOWN

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 800
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
            // 用户要求：无障碍不再主动自动记账，避免浏览商品误触发。
            // 仅保留服务存活；真正记账改为用户截图后由 ScreenshotCaptureObserver 处理。
            return
        }

    private fun markPaymentTriggered(app: PaymentApp) {
        paymentInitiated = true
        paymentInitiatedAt = SystemClock.uptimeMillis()
        paymentTriggerApp = app
                // 截图前最后检查：如果是视频App，扫描整页确认不是视频内容
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val fullText = extractAllText(rootNode)
                    if (isVideoContent(fullText, app)) {
                        android.util.Log.d(TAG, "跳过: 视频内容，取消触发")
                        paymentInitiated = false
                        paymentTriggerApp = PaymentApp.UNKNOWN
                        return
                    }
                }
                android.util.Log.d(TAG, "支付动作触发: ${app.label}")
                // 延迟3秒后截图分析
                scope.launch {
                    kotlinx.coroutines.delay(3000L)
                    captureAndAnalyze(app)
                }
    }

    private fun shouldScanFullPage(
        event: AccessibilityEvent,
        app: PaymentApp,
        eventText: String,
        now: Long
    ): Boolean {
        val hasStrongSignal = containsStrongPaymentSignal(eventText)
        val minInterval = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> CLICK_SCAN_INTERVAL_MS
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> WINDOW_STATE_SCAN_INTERVAL_MS
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (hasStrongSignal || containsPaymentContext(eventText)) {
                    PAYMENT_CONTEXT_SCAN_INTERVAL_MS
                } else if (app == PaymentApp.DOUYIN) {
                    DOUYIN_CONTENT_SCAN_INTERVAL_MS
                } else {
                    CONTENT_CHANGED_SCAN_INTERVAL_MS
                }
            }
            else -> return false
        }

        if (hasStrongSignal) {
            lastFullScanAt = now
            return true
        }

        if (now - lastFullScanAt < minInterval) return false
        lastFullScanAt = now
        return true
    }

    private fun extractEventText(event: AccessibilityEvent): String {
        val parts = mutableListOf<String>()
        event.text?.forEach { item ->
            item?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)

        val source = try {
            event.source
        } catch (_: Exception) {
            null
        }
        if (source != null) {
            try {
                source.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
                source.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
            } catch (_: Exception) {
                // 部分三方页面节点可能已失效，忽略即可。
            } finally {
                try {
                    source.recycle()
                } catch (_: Exception) {
                }
            }
        }

        return parts.distinct().joinToString("\n").take(500)
    }

    // ====== 支付动作触发检测 ======
    private fun isPaymentTrigger(text: String, event: AccessibilityEvent, fromFullPage: Boolean): Boolean {
            if (text.isBlank()) return false
            val compact = text.compactText()
            if (compact.isBlank()) return false

            // 视频/社交App中观看视频时不触发（页面文字来自视频内容而非真实UI）
            val pkg = event.packageName?.toString() ?: ""
            val app = PaymentApp.fromPackage(pkg)
            if (isVideoContent(compact, app)) return false

        // 明确支付短语才允许从整页文本触发；避免“视频/浮窗/付款码/确认”等泛词在非支付场景触发。
        if (containsStrongPaymentSignal(compact)) return true

        // 单独的“付款/支付/确认”只允许来自点击事件的按钮文本，不能来自整页扫描。
        if (fromFullPage || event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return false

        val labels = text.lines()
            .map { it.trim().compactText() }
            .filter { it.isNotBlank() }

        if (labels.any { it in NON_PAYMENT_BUTTON_LABELS }) return false
        if (labels.any { it in EXACT_PAYMENT_BUTTON_LABELS }) return true
        if (labels.any { it == "确认" } && containsPaymentContext(compact)) return true

        return false
    }

    // 检测是否在观看视频内容（抖音等视频App中，页面文字来自视频而非真实UI）
    private fun isVideoContent(text: String, app: PaymentApp): Boolean {
        if (app != PaymentApp.DOUYIN) return false
        val compact = text.compactText()
        val videoScore = VIDEO_INDICATORS.count { compact.contains(it) }
        return videoScore >= 3
    }

    private fun containsStrongPaymentSignal(text: String): Boolean {
            val compact = text.compactText()
            if (STRONG_PAYMENT_PHRASES.any { compact.contains(it) }) return true

            // 如果页面包含浏览商品的标志，不触发（用户在浏览而非支付）
            if (BROWSING_INDICATORS.any { compact.contains(it) }) return false

            // 如果页面包含视频内容的标志，不触发（用户在看视频而非支付）
            if (VIDEO_INDICATORS.count { compact.contains(it) } >= 3) return false

            val hasAmount = PAYMENT_AMOUNT_REGEX.containsMatchIn(compact)
            if (!hasAmount) return false

            return AMOUNT_PAYMENT_CONTEXTS.any { compact.contains(it) }
        }

    private fun containsPaymentContext(text: String): Boolean {
        val compact = text.compactText()
        return PAYMENT_CONTEXTS.any { compact.contains(it) }
    }

    // ====== 截图+分析 ======
    private suspend fun captureAndAnalyze(app: PaymentApp) {
        val now = System.currentTimeMillis()
        if (now - lastProcessAt < 15_000L) {
            android.util.Log.d(TAG, "跳过: 15秒内已处理")
            paymentInitiated = false
            return
        }
        lastProcessAt = now

        android.util.Log.d(TAG, "开始截图分析: ${app.label}")
        val screenshot = captureScreenshot()
        if (screenshot == null) {
            android.util.Log.w(TAG, "截图失败")
            paymentInitiated = false
            return
        }
        android.util.Log.d(TAG, "截图成功: ${screenshot.width}x${screenshot.height}")

        // 直接在当前协程执行，不再 scope.launch，避免截图被提前回收
        try {
            val appInstance = application as AutoBookApplication
            val aiConfig = appInstance.repository.getAiConfig()
            android.util.Log.d(TAG, "AI配置: ${if (aiConfig != null && aiConfig.configured) "已配置(${aiConfig.model})" else "未配置"}")

            if (aiConfig != null && aiConfig.configured) {
                // AI模式：截图直接给AI分析
                android.util.Log.d(TAG, "AI截图识别中...")
                val aiResult = appInstance.repository.recognizeAccessibilityScreenshot(screenshot, aiConfig)
                if (aiResult != null) {
                    android.util.Log.d(TAG, "AI返回: amount=${aiResult.amountCents}, merchant=${aiResult.merchantName}, isSpam=${aiResult.isSpam}")
                } else {
                    android.util.Log.w(TAG, "AI返回null")
                }
                if (aiResult != null && aiResult.amountCents != null && aiResult.amountCents > 0 && !aiResult.isSpam) {
                    val isRefund = aiResult.categoryHint.contains("退款")
                    val result = appInstance.repository.captureFromAccessibility(
                        appName = app.name,
                        pageText = "",
                        amountCents = aiResult.amountCents,
                        merchant = aiResult.merchantName.ifBlank { app.label },
                        isRefund = isRefund
                    )
                    if (result.created && result.transaction != null) {
                        AutoBookNotifier.notifyTransaction(
                            this@PaymentAccessibilityService,
                            result.transaction,
                            appInstance.getDao().getCategories()
                        )
                    }
                } else {
                    recordInvalidAiScreenshotResult(app, appInstance, aiResult)
                }
            } else {
                // 本地模式：提取文本分析
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val pageText = extractAllText(rootNode)
                    val parsed = parsePaymentFromPageText(pageText, app)
                    if (parsed != null) {
                        val result = appInstance.repository.captureFromAccessibility(
                            appName = app.name,
                            pageText = pageText,
                            amountCents = parsed.amountCents,
                            merchant = parsed.merchant,
                            isRefund = parsed.isRefund
                        )
                        if (result.created && result.transaction != null) {
                            AutoBookNotifier.notifyTransaction(
                                this@PaymentAccessibilityService,
                                result.transaction,
                                appInstance.getDao().getCategories()
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "captureAndAnalyze failed", e)
        } finally {
            screenshot.recycle()
            paymentInitiated = false
        }
    }

    private suspend fun recordInvalidAiScreenshotResult(
        app: PaymentApp,
        appInstance: AutoBookApplication,
        aiResult: AiParsedPayment?
    ) {
        val pageText = rootInActiveWindow?.let { extractAllText(it) }.orEmpty()
        val parsed = parsePaymentFromPageText(pageText, app)
        if (parsed != null) {
            addPendingFromAccessibility(pageText, app, parsed, appInstance)
            return
        }

        val action = when {
            aiResult == null -> "AI未返回结果，跳过"
            aiResult.isSpam -> "AI判定非消费，跳过"
            aiResult.amountCents == null || aiResult.amountCents <= 0L -> "AI未识别金额，跳过"
            else -> "AI未识别，跳过"
        }
        val detail = listOfNotNull(
            app.label,
            aiResult?.merchantName?.takeIf { it.isNotBlank() },
            pageText.take(80).takeIf { it.isNotBlank() }
        ).joinToString(" / ").take(200)
        appInstance.repository.addLog("无障碍服务", action, detail)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "A11yService"
        private const val MAX_AMOUNT = 1_000_000L
        private const val PAYMENT_INIT_TIMEOUT_MS = 12_000L
        private const val CLICK_SCAN_INTERVAL_MS = 400L
        private const val WINDOW_STATE_SCAN_INTERVAL_MS = 800L
        private const val PAYMENT_CONTEXT_SCAN_INTERVAL_MS = 800L
        private const val CONTENT_CHANGED_SCAN_INTERVAL_MS = 2_500L
        private const val DOUYIN_CONTENT_SCAN_INTERVAL_MS = 5_000L
        private const val MAX_EXTRACT_NODES = 140
        private const val MAX_EXTRACT_DEPTH = 18
        private const val MAX_EXTRACT_CHARS = 3_000
        private const val MAX_EXTRACT_MILLIS = 120L

        private val STRONG_PAYMENT_PHRASES = listOf(
            "确认支付", "确认付款", "立即支付", "立即付款",
            "提交订单", "确认订单", "确认交易", "订单支付",
            "请输入支付密码", "输入支付密码", "支付密码",
            "交易密码", "付款密码",
            "指纹支付", "刷脸支付", "面容支付", "验证指纹", "验证面容",
            "收银台"
        )

        private val AMOUNT_PAYMENT_CONTEXTS = listOf(
            "收银台", "实付款", "需付款", "应付款", "待付款",
            "订单支付", "确认支付", "确认付款", "立即支付", "立即付款"
        )

        private val PAYMENT_CONTEXTS = listOf(
            "收银台", "支付密码", "交易密码", "付款密码",
            "实付款", "需付款", "应付款", "订单支付",
            "确认支付", "确认付款", "立即支付", "立即付款"
        )

        private val EXACT_PAYMENT_BUTTON_LABELS = setOf("付款", "支付")
        private val NON_PAYMENT_BUTTON_LABELS = setOf(
            "付款码", "收款码", "扫码", "扫一扫", "视频", "浮窗", "直播",
            "搜索", "更多", "更多功能", "确认收货", "待付款",
            "加入购物车", "立即购买", "收藏店铺", "进店逛逛"
        )

        private val PAYMENT_AMOUNT_REGEX = Regex("([¥￥]\\d{1,6}(\\.\\d{1,2})?|\\d{1,6}(\\.\\d{1,2})?元)")

        // 浏览商品页面的标志性关键词，出现这些说明用户在浏览而非支付
        // 视频/社交App中观看视频时的标志词，出现这些说明用户在看视频而非操作真实页面
        private val VIDEO_INDICATORS = setOf(
            "分享", "评论", "点赞", "收藏", "关注",
            "转发", "弹幕", "全屏", "倍速", "循环播放",
            "投屏", "举报", "不感兴趣", "相似推荐",
            "首页", "朋友", "消息", "我", "拍摄",
            "展开", "收起", "展开全文", "收起全文",
            "直播中", "正在直播", "进入直播间",
            "已赞", "已评论", "已收藏", "已关注"
        )

        private val BROWSING_INDICATORS = setOf(
            "加入购物车", "立即购买", "商品详情", "商品评价", "店铺",
            "已选", "月销", "累计评价", "服务保障",
            "领券", "优惠券", "满减", "凑单", "收藏",
            "分享", "客服", "店铺评分", "正品保障",
            "7天退换", "运费险", "急速退款", "先用后付",
            "去结算", "立即下单", "立即抢购", "马上抢",
            "已售", "好评率", "问大家", "看直播",
            "相似商品", "为你推荐", "看了又看", "买了又买",
            "店铺首页", "所有商品", "店铺动态"
        )
    }

    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        val visited = mutableSetOf<Int>()
        var nodeCount = 0
        val startedAt = SystemClock.uptimeMillis()
        val digitOnlyRegex = Regex("^\\d+$")

        fun traverse(n: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > MAX_EXTRACT_DEPTH ||
                nodeCount >= MAX_EXTRACT_NODES ||
                texts.sumOf { it.length } >= MAX_EXTRACT_CHARS ||
                SystemClock.uptimeMillis() - startedAt > MAX_EXTRACT_MILLIS
            ) return
            val nodeId = System.identityHashCode(n)
            if (visited.contains(nodeId)) return
            visited.add(nodeId)
            nodeCount++

            try {
                n.text?.toString()?.takeIf { it.isNotBlank() && it.length > 1 && it.trim().length > 1 && !it.trim().matches(digitOnlyRegex) }?.let {
                    texts.add(it.trim())
                }
                n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it.length > 1 && it.trim().length > 1 && !it.trim().matches(digitOnlyRegex) }?.let {
                    texts.add(it.trim())
                }
            } catch (_: Exception) {
                return
            }

            val childCount = try {
                n.childCount
            } catch (_: Exception) {
                0
            }
            for (i in 0 until childCount) {
                if (nodeCount >= MAX_EXTRACT_NODES || SystemClock.uptimeMillis() - startedAt > MAX_EXTRACT_MILLIS) return
                val child = try {
                    n.getChild(i)
                } catch (_: Exception) {
                    null
                }
                child?.let { traverse(it, depth + 1) }
            }
        }

        traverse(node)
        return texts.distinct().joinToString("\n")
    }

    private fun String.compactText(): String = replace(Regex("\\s+"), "")

    private fun isPaymentSuccessPage(text: String, app: PaymentApp): Boolean {
        // ====== 排除：订单详情/历史页面 ======
        val orderPageKeywords = listOf(
            "待发货", "待收货", "待付款", "已签收", "交易关闭",
            "正在出库", "仓库处理中", "整装待发", "包裹",
            "查看订单", "订单详情", "订单编号", "物流详情",
            "再次购买", "申请退款", "退款详情", "退款进度",
            "确认收货", "评价", "售后", "运单号",
            "订单状态", "已取消", "已关闭", "退款中", "退款申请"
        )
        if (orderPageKeywords.any { text.contains(it) }) return false

        // 退款页面单独处理（不排除，但标记为退款）
        val refundPageKeywords = listOf(
            "退款成功", "闪电退款", "已同意退款", "极速退款",
            "退款已到账", "退款到账", "已退款", "退款处理成功"
        )

        // ====== 排除：列表/账单页 ======
        val listPageKeywords = listOf(
            "查看更多账单", "账单管理", "账单明细", "交易明细", "交易记录",
            "历史账单", "全部账单", "账单列表", "筛选", "排序",
            "近三个月", "近一个月", "近七天", "交易时间"
        )
        if (listPageKeywords.any { text.contains(it) }) return false

        // ====== 排除：基金/理财转入转出 ======
        val fundKeywords = listOf("转入零钱通", "零钱通转入", "余额宝转入", "零钱通转出")
        if (fundKeywords.any { text.contains(it) }) return false

        // 行数限制已移除，避免误杀弹窗型支付成功页

        // ====== 必须：支付成功或退款关键词 ======
        val successKeywords = listOf(
            "支付成功", "付款成功", "购买成功", "交易成功",
            "订单已支付", "扣款成功", "下单成功",
            "已支付", "已付款", "已消费", "消费成功",
            "支付完成", "付款完成", "交易完成",
            "转账成功", "充值成功", "缴费成功"
        )
        val isRefundPage = refundPageKeywords.any { text.contains(it) }
        if (!isRefundPage && successKeywords.none { text.contains(it) }) return false

        // ====== 必须：金额 ======
        val hasAmount = text.contains("¥") || text.contains("￥") ||
                Regex("\\d+\\.\\d{2}").containsMatchIn(text) ||
                Regex("\\d+元").containsMatchIn(text)
        if (!hasAmount) return false

        // 商户信息：有则更好，没有也行（弹窗型支付成功页可能没有商户）
        val hasMerchant = text.contains("付款给") || text.contains("商家") ||
                text.contains("商户") || text.contains("收款方") ||
                text.contains("店铺") || text.contains("商品") ||
                text.contains("向") && text.contains("付款") ||
                text.contains("商品说明") || text.contains("商品名称") ||
                text.contains("支付对象") || text.contains("交易对方")
        // 有商户信息直接通过，没有也通过（用App名兜底）
        return true
    }

    private fun processPaymentSuccess(pageText: String, app: PaymentApp, isRefund: Boolean = false) {
        scope.launch {
            try {
                val appInstance = application as AutoBookApplication
                val aiConfig = appInstance.repository.getAiConfig()

                // AI开启时优先使用AI分析
                if (aiConfig != null && aiConfig.configured) {
                    processWithAi(pageText, app, aiConfig, appInstance)
                } else {
                    processWithLocalRules(pageText, app, appInstance, isRefund)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "processPaymentSuccess failed", e)
            }
        }
    }

    private suspend fun processWithAi(
        pageText: String,
        app: PaymentApp,
        aiConfig: AiRecognitionConfig,
        appInstance: AutoBookApplication,
        isRefund: Boolean = false
    ) {
        // 优先截图给AI分析（不压缩）
        val screenshot = captureScreenshot()
        if (screenshot != null) {
            try {
                val aiResult = appInstance.repository.recognizeAccessibilityScreenshot(screenshot, aiConfig)
                if (aiResult != null && aiResult.amountCents != null && aiResult.amountCents > 0 && !aiResult.isSpam) {
                    val isRefundPage = isRefund || pageText.contains("退款") || pageText.contains("退回") ||
                            aiResult.categoryHint.contains("退款")
                    val result = appInstance.repository.captureFromAccessibility(
                        appName = app.name,
                        pageText = pageText,
                        amountCents = aiResult.amountCents,
                        merchant = aiResult.merchantName.ifBlank { app.label },
                        isRefund = isRefundPage
                    )
                    if (result.created && result.transaction != null) {
                        AutoBookNotifier.notifyTransaction(
                            this@PaymentAccessibilityService,
                            result.transaction,
                            appInstance.getDao().getCategories()
                        )
                    }
                    screenshot.recycle()
                    return
                }
            } catch (_: Exception) {
                // AI截图识别失败
            }
            screenshot.recycle()
        }
        // 截图失败或AI识别失败，用文本兜底
        try {
            val aiResult = appInstance.repository.recognizeAccessibilityText(pageText, aiConfig)
            if (aiResult != null && aiResult.amountCents != null && aiResult.amountCents > 0 && !aiResult.isSpam) {
                val isRefundPage = isRefund || pageText.contains("退款") || pageText.contains("退回") ||
                        aiResult.categoryHint.contains("退款")
                val result = appInstance.repository.captureFromAccessibility(
                    appName = app.name,
                    pageText = pageText,
                    amountCents = aiResult.amountCents,
                    merchant = aiResult.merchantName.ifBlank { app.label },
                    isRefund = isRefundPage
                )
                if (result.created && result.transaction != null) {
                    AutoBookNotifier.notifyTransaction(
                        this@PaymentAccessibilityService,
                        result.transaction,
                        appInstance.getDao().getCategories()
                    )
                }
                return
            }
        } catch (_: Exception) {
            // AI文本识别也失败
        }
        // 全部失败，放入待确认
        val parsed = parsePaymentFromPageText(pageText, app)
        if (parsed != null) {
            addPendingFromAccessibility(pageText, app, parsed, appInstance)
        }
    }

    private suspend fun captureScreenshot(): android.graphics.Bitmap? = suspendCancellableCoroutine { cont ->
        val displayManager = getSystemService(DisplayManager::class.java)
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { it.run() },
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                        result.hardwareBuffer, result.colorSpace
                    )
                    result.hardwareBuffer?.close()
                    cont.resume(bitmap)
                }
                override fun onFailure(errorCode: Int) {
                    cont.resume(null)
                }
            }
        )
    }

    private suspend fun processWithLocalRules(
        pageText: String,
        app: PaymentApp,
        appInstance: AutoBookApplication,
        isRefund: Boolean = false
    ) {
        val parsed = parsePaymentFromPageText(pageText, app) ?: return
        val effectiveParsed = if (isRefund) parsed.copy(isRefund = true) else parsed
        val aiConfig = appInstance.repository.getAiConfig()

        if (aiConfig != null && aiConfig.configured) {
            // AI模式：AI分析失败时放入待确认
            addPendingFromAccessibility(pageText, app, parsed, appInstance)
        } else {
            // 本地模式：直接录入
            val result = appInstance.repository.captureFromAccessibility(
                appName = app.name,
                pageText = pageText,
                amountCents = parsed.amountCents,
                merchant = parsed.merchant,
                isRefund = parsed.isRefund
            )
            if (result.created && result.transaction != null) {
                AutoBookNotifier.notifyTransaction(
                    this@PaymentAccessibilityService,
                    result.transaction,
                    appInstance.getDao().getCategories()
                )
            }
        }
    }

    private suspend fun addPendingFromAccessibility(
        pageText: String,
        app: PaymentApp,
        parsed: PaymentInfo,
        appInstance: AutoBookApplication
    ) {
        // 将本地解析结果写入待确认表
        appInstance.repository.addPendingFromAccessibility(
            merchant = parsed.merchant,
            amountCents = parsed.amountCents,
            app = app,
            pageText = pageText
        )
    }

    private fun parsePaymentFromPageText(text: String, app: PaymentApp): PaymentInfo? {
        val amount = extractAmount(text) ?: return null
        val merchant = extractMerchant(text, app) ?: "未知消费"
        val isRefund = text.contains("退款") || text.contains("退回") || text.contains("原路退回")

        return PaymentInfo(
            amountCents = amount,
            merchant = merchant,
            isRefund = isRefund
        )
    }

    private fun extractAmount(text: String): Long? {
        val patterns = listOf(
            Regex("[¥￥](\\d{1,6}\\.\\d{1,2})"),
            Regex("(\\d{1,6}\\.\\d{1,2})元"),
            Regex("金额[：:]?\\s*[¥￥]?(\\d{1,6}\\.?\\d{0,2})"),
            Regex("(?:付款|支付|消费|实付|实收)[：:]?\\s*[¥￥]?(\\d{1,6}\\.?\\d{0,2})"),
            Regex("(\\d{1,6})元"),
            Regex("(?:^|\\s)(\\d{1,6}\\.\\d{1,2})(?:\\s|$)")
        )

        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val amountStr = match.groupValues[1]
                val amount = amountStr.toDoubleOrNull() ?: continue
                if (amount > 0 && amount < MAX_AMOUNT) {
                    return (amount * 100).toLong()
                }
            }
        }
        return null
    }

    private fun extractMerchant(text: String, app: PaymentApp): String? {
        val merchantPatterns = listOf(
            Regex("(?:商户|商家|店铺|门店)[：:]?\\s*([^\\n¥￥\\d]{2,20})"),
            Regex("(?:商品|商品名称|商品说明)[：:]?\\s*([^\\n]{2,30})"),
            Regex("付款给\\s*([^\\n]{2,20})"),
            Regex("向\\s*([^\\n]{2,20})\\s*(?:付款|支付)"),
            Regex("在\\s*([^\\n]{2,20})\\s*(?:消费|支付|购买)"),
            Regex("(?:收款方|对方|支付对象)[：:]?\\s*([^\\n¥￥\\d]{2,20})"),
            Regex("(?:订单|订单号|交易单号)[：:]?\\s*([^\\n]{2,30})")
        )

        for (pattern in merchantPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length in 2..30 &&
                    !merchant.contains("¥") && !merchant.contains("￥") &&
                    !merchant.matches(Regex("^\\d+\\.?\\d*$"))) {
                    return merchant
                }
            }
        }

        return when (app) {
            PaymentApp.WECHAT -> "微信支付"
            PaymentApp.ALIPAY -> "支付宝"
            PaymentApp.DOUYIN -> "抖音支付"
            PaymentApp.JD -> "京东支付"
            PaymentApp.TAOBAO -> "淘宝"
            PaymentApp.TMALL -> "天猫"
            PaymentApp.PINDUODUO -> "拼多多"
            PaymentApp.MEITUAN -> "美团"
            PaymentApp.UNION_PAY -> "云闪付"
            PaymentApp.UNKNOWN -> "未知支付"
        }
    }

    private data class PaymentInfo(
        val amountCents: Long,
        val merchant: String,
        val isRefund: Boolean
    )
}
