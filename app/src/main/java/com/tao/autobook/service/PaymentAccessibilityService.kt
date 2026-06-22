package com.tao.autobook.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.tao.autobook.AutoBookApplication
import com.tao.autobook.ai.AiRecognitionConfig
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.notify.AutoBookNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaymentAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastProcessAt = 0L
    private var lastPageText = ""
    private var lastPackageName = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 200
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val app = PaymentApp.fromPackage(packageName)
        if (app == PaymentApp.UNKNOWN) return

        val rootNode = rootInActiveWindow ?: return
        val pageText = extractAllText(rootNode)

        if (pageText == lastPageText && packageName == lastPackageName) return
        lastPageText = pageText
        lastPackageName = packageName

        val refundKeywords = listOf(
            "退款成功", "闪电退款", "已同意退款", "极速退款",
            "退款已到账", "退款到账", "已退款", "退款处理成功"
        )
        val isRefundPage = refundKeywords.any { pageText.contains(it) }
        
        if (isRefundPage || isPaymentSuccessPage(pageText, app)) {
            val now = System.currentTimeMillis()
            if (now - lastProcessAt < 30_000L) return
            lastProcessAt = now
            processPaymentSuccess(pageText, app, isRefundPage)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "A11yService"
        private const val MAX_AMOUNT = 1_000_000L
    }

    private fun extractAllText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        val visited = mutableSetOf<Int>()

        fun traverse(n: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > 50) return
            val nodeId = System.identityHashCode(n)
            if (visited.contains(nodeId)) return
            visited.add(nodeId)

            n.text?.toString()?.takeIf { it.isNotBlank() && it.length > 1 && it.trim().length > 1 && !it.trim().matches(Regex("^\\d+$")) }?.let {
                texts.add(it.trim())
            }
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it.length > 1 && it.trim().length > 1 && !it.trim().matches(Regex("^\\d+$")) }?.let {
                texts.add(it.trim())
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { traverse(it, depth + 1) }
            }
        }

        traverse(node)
        return texts.distinct().joinToString("\n")
    }

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
            // AI识别失败
        }
        // AI模式下失败，放入待确认
        val parsed = parsePaymentFromPageText(pageText, app)
        if (parsed != null) {
            addPendingFromAccessibility(pageText, app, parsed, appInstance)
        }
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
