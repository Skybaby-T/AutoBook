package com.tao.autobook.parser

import com.tao.autobook.data.ParsedPayment
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionType
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToLong

class PaymentTextParser(private val clock: () -> Long = { System.currentTimeMillis() }) {
    companion object {
        const val DEDUP_WINDOW_MS: Long = 5 * 60 * 1000L
    }

    private val amountRegexes = listOf(
        Regex("""[¥￥]([0-9]+(?:\.[0-9]{1,2})?)"""),
        Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*(?:元|块)"""),
        Regex("""(?:人民币|RMB|CNY)\s*([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""消费([0-9]+(?:\.[0-9]{1,2})?)元?"""),
        Regex("""付款([0-9]+(?:\.[0-9]{1,2})?)元?"""),
        Regex("""支付([0-9]+(?:\.[0-9]{1,2})?)元?""")
    )

    private val dateRegex = Regex("""(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})""")
    private val merchantMarkers = listOf(
        "付款给", "付款至", "商户", "商家", "收款方",
        "店铺", "店家", "卖家", "商品", "商品名称", "商品说明",
        "交易对方", "消费于"
    )
    private val successWords = listOf(
        "支付成功", "付款成功", "交易成功", "扣款成功", "消费成功",
        "已支付", "已付款", "订单已支付", "付款完成", "支付完成",
        "购买成功", "充值成功", "缴费成功",
        "退款", "退款成功", "退款到账", "到账", "原路退回", "退回",
        "交易提醒", "有一笔", "收到转账"
    )
    private val refundWords = listOf(
        "退款", "退款成功", "退款到账", "已退款", "退回", "原路退回",
        "退货退款", "退款入账", "到账"
    )

    // 本地模式垃圾通知关键词
    private val spamKeywords = listOf(
        "花呗账单", "要还花呗", "白条账单", "信用卡账单",
        "点击还款", "还款提醒", "账单提醒", "月度账单",
        "红包待领取", "订阅提醒", "免费领取", "优惠券",
        "抽奖", "限时", "推广", "广告", "营销",
        "中奖", "贷款", "借款", "提额",
        "转入零钱通", "零钱通转入", "余额宝转入"
    )

    fun parse(text: String, packageName: String? = null, now: Long = clock()): ParsedPayment? {
        val normalized = text.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (normalized.isBlank()) return null
        if (successWords.none { normalized.contains(it) } && amountRegexes.none { it.containsMatchIn(normalized) }) return null

        // 垃圾通知过滤
        if (spamKeywords.any { normalized.contains(it) }) return null

        val amount = extractAmountCents(normalized) ?: return null
        val app = detectPaymentApp(normalized, packageName)
        val merchant = extractMerchant(normalized, app)
        val paidAt = extractDate(normalized, now)
        val type = detectType(normalized)
        val confidence = calculateConfidence(normalized, merchant, app)
        return ParsedPayment(
            amountCents = amount,
            merchantName = merchant,
            paymentApp = app,
            paidAt = paidAt,
            confidence = confidence,
            rawText = normalized,
            type = type,
            categoryHint = if (type == TransactionType.INCOME && refundWords.any { normalized.contains(it) }) "退款" else ""
        )
    }

    fun detectType(text: String): TransactionType {
        val normalized = text.replace('\n', ' ')
        return when {
            refundWords.any { normalized.contains(it) } -> TransactionType.INCOME
            listOf("还款", "充值", "提现", "转出到银行卡", "信用卡还款").any { normalized.contains(it) } -> TransactionType.OTHER
            else -> TransactionType.EXPENSE
        }
    }

    fun extractAmountCents(text: String): Long? {
        return amountRegexes.asSequence()
            .mapNotNull { regex -> regex.findAll(text).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }.maxOrNull() }
            .firstOrNull()
            ?.let { (it * 100).roundToLong() }
    }

    fun detectPaymentApp(text: String, packageName: String?): PaymentApp {
        PaymentApp.fromPackage(packageName).takeIf { it != PaymentApp.UNKNOWN }?.let { return it }
        return when {
            text.contains("支付宝") || text.contains("蚂蚁") -> PaymentApp.ALIPAY
            text.contains("微信") || text.contains("财付通") -> PaymentApp.WECHAT
            text.contains("云闪付") || text.contains("银联") -> PaymentApp.UNION_PAY
            text.contains("京东") || text.contains("京东支付") || text.contains("白条") -> PaymentApp.JD
            text.contains("抖音") || text.contains("抖音支付") || text.contains("抖音商城") -> PaymentApp.DOUYIN
            text.contains("淘宝") -> PaymentApp.TAOBAO
            text.contains("天猫") -> PaymentApp.TMALL
            text.contains("拼多多") || text.contains("多多支付") -> PaymentApp.PINDUODUO
            text.contains("美团") || text.contains("美团支付") -> PaymentApp.MEITUAN
            else -> PaymentApp.UNKNOWN
        }
    }

    fun dedupeKey(parsed: ParsedPayment): String {
        val bucket = parsed.paidAt / DEDUP_WINDOW_MS
        val material = listOf(parsed.paymentApp.name, parsed.type.name, parsed.amountCents, parsed.merchantName.lowercase(Locale.ROOT), bucket)
            .joinToString("|")
        return sha256(material).take(32)
    }

    private fun extractMerchant(text: String, app: PaymentApp): String {
        for (marker in merchantMarkers) {
            val pattern = Regex("$marker[:：]?\\s*([^，。；;|]{2,24})")
            val value = pattern.find(text)?.groupValues?.getOrNull(1)?.cleanupMerchant()
            if (!value.isNullOrBlank() && !value.looksLikeAmount()) return value
        }

        val withoutAmount = amountRegexes.fold(text) { acc, regex -> acc.replace(regex, " ") }
        val candidates = withoutAmount.split(' ', '，', '。', '；', ';', '|')
            .map { it.cleanupMerchant() }
            .filter { it.length in 2..24 && !it.contains("支付") && !it.contains("成功") && !it.looksLikeAmount() }
        return candidates.firstOrNull { candidate ->
            when (app) {
                PaymentApp.ALIPAY -> !candidate.contains("支付宝")
                PaymentApp.WECHAT -> !candidate.contains("微信")
                PaymentApp.UNION_PAY -> !candidate.contains("云闪付") && !candidate.contains("银联")
                PaymentApp.JD -> !candidate.contains("京东支付")
                PaymentApp.DOUYIN -> !candidate.contains("抖音支付") && !candidate.contains("抖音商城")
                PaymentApp.TAOBAO -> !candidate.contains("淘宝")
                PaymentApp.TMALL -> !candidate.contains("天猫")
                PaymentApp.PINDUODUO -> !candidate.contains("拼多多") && !candidate.contains("多多支付")
                PaymentApp.MEITUAN -> !candidate.contains("美团支付")
                PaymentApp.UNKNOWN -> true
            }
        } ?: "未识别商户"
    }

    private fun extractDate(text: String, fallback: Long): Long {
        val match = dateRegex.find(text) ?: return fallback
        val year = match.groupValues[1].toIntOrNull() ?: return fallback
        val month = match.groupValues[2].toIntOrNull() ?: return fallback
        val day = match.groupValues[3].toIntOrNull() ?: return fallback
        return runCatching {
            LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(fallback)
    }

    private fun calculateConfidence(text: String, merchant: String, app: PaymentApp): Float {
        var score = 0.45f
        if (successWords.any { text.contains(it) }) score += 0.15f
        if (app != PaymentApp.UNKNOWN) score += 0.15f
        if (merchant != "未识别商户") score += 0.15f
        if (dateRegex.containsMatchIn(text)) score += 0.05f
        return score.coerceAtMost(0.98f)
    }

    private fun String.cleanupMerchant(): String = trim()
        .replace(Regex("^(给|向|在|商家|商户|收款方|付款给|付款至)"), "")
        .replace(Regex("(付款成功|支付成功|交易成功|扣款成功).*$"), "")
        .trim(' ', ':', '：', '-', '—', '\'', '"')

    private fun String.looksLikeAmount(): Boolean = contains(Regex("""[0-9]+(?:\.[0-9]{1,2})?\s*(元|块)"""))

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
