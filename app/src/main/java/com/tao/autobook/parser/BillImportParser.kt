package com.tao.autobook.parser

import com.tao.autobook.data.ImportedBillRow
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class BillImportParseResult(
    val rows: List<ImportedBillRow>,
    val failedCount: Int
)

class BillImportParser(private val fallbackNow: () -> Long = { System.currentTimeMillis() }) {
    private val paymentParser = PaymentTextParser(fallbackNow)
    private val dateTimeFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
    )
    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy年M月d日")
    )

    fun parse(text: String, hintName: String? = null): BillImportParseResult {
        val normalized = text.replace("\uFEFF", "").trim()
        if (normalized.isBlank()) return BillImportParseResult(emptyList(), 1)
        val lines = normalized.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return BillImportParseResult(emptyList(), 1)

        val delimiter = detectDelimiter(lines)
        val headerIndex = lines.indexOfFirst { line ->
            val lower = line.lowercase(Locale.ROOT)
            val hasSpecific = listOf("交易时间", "付款时间", "创建时间", "交易对方").any { lower.contains(it) }
            val genericCount = listOf("时间", "金额", "交易金额", "商户", "商品名称").count { lower.contains(it) }
            hasSpecific || genericCount >= 3
        }
        val header = headerIndex.takeIf { it >= 0 }?.let { splitLine(lines[it], delimiter).map { cell -> cell.trim() } }
        val dataLines = if (headerIndex >= 0) lines.drop(headerIndex + 1) else lines
        val sourceApp = detectApp(lines.take(8).joinToString(" ") + " " + hintName.orEmpty())

        val rows = mutableListOf<ImportedBillRow>()
        var failed = 0
        dataLines.forEach { line ->
            val row = if (header != null) parseStructuredLine(header, splitLine(line, delimiter), sourceApp, line) else parseLooseLine(line, sourceApp)
            if (row != null) rows += row else failed++
        }
        return BillImportParseResult(rows, failed)
    }

    private fun parseStructuredLine(header: List<String>, cells: List<String>, sourceApp: PaymentApp, raw: String): ImportedBillRow? {
        if (cells.size < 2) return parseLooseLine(raw, sourceApp)
        // Skip rows where all meaningful cells are "/"
        val nonEmpty = cells.count { it.isNotBlank() && it != "/" }
        if (nonEmpty < 2) return null
        val map = header.mapIndexedNotNull { index, name -> cells.getOrNull(index)?.let { name to it.trim() } }.toMap()
        val joined = map.values.joinToString(" ").ifBlank { raw }
        val amountText = firstValue(map, "金额(元)", "金额", "交易金额", "收/支", "收入", "支出", "实付", "付款金额", "订单金额", "账单金额") ?: joined
        val amount = parseAmount(amountText) ?: paymentParser.extractAmountCents(joined) ?: return null
        val type = detectType(joined, amountText)
        val merchant = firstValue(map, "交易对方", "商户", "商家", "商品名称", "商品", "店铺", "名称", "对方", "收款方", "付款方")
            ?.cleanupMerchant()
            ?.takeIf { it.isNotBlank() && it != "/" && it.length >= 2 }
            ?: firstValue(map, "交易类型")?.takeIf { it.isNotBlank() && it != "/" }?.cleanupMerchant()
            ?: paymentParser.parse(joined)?.merchantName?.takeIf { it != "未识别商户" }
            ?: defaultMerchant(type)
        val paidAt = parseDateTime(firstValue(map, "交易时间", "付款时间", "创建时间", "支付时间", "时间", "日期") ?: joined)
        val app = detectApp(joined).takeIf { it != PaymentApp.UNKNOWN } ?: sourceApp
        val note = firstValue(map, "备注", "交易状态", "当前状态", "订单号", "说明").orEmpty()
        return ImportedBillRow(abs(amount), merchant, paidAt, app, type, note, raw)
    }

    private fun parseLooseLine(line: String, sourceApp: PaymentApp): ImportedBillRow? {
        val amount = parseAmount(line) ?: paymentParser.extractAmountCents(line) ?: return null
        val type = detectType(line, line)
        val parsed = paymentParser.parse(line)
        val app = parsed?.paymentApp?.takeIf { it != PaymentApp.UNKNOWN } ?: detectApp(line).takeIf { it != PaymentApp.UNKNOWN } ?: sourceApp
        val merchant = parsed?.merchantName?.takeIf { it != "未识别商户" } ?: extractLooseMerchant(line) ?: defaultMerchant(type)
        val paidAt = parseDateTime(line)
        return ImportedBillRow(abs(amount), merchant, paidAt, app, type, rawText = line)
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(8).joinToString("\n")
        return listOf(',', '\t', ';').maxBy { ch -> sample.count { it == ch } }
    }

    private fun splitLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> {
                    cells += cell.toString()
                    cell.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    private fun firstValue(map: Map<String, String>, vararg keys: String): String? {
        keys.forEach { key ->
            map.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun parseAmount(text: String): Long? {
        if (text.isBlank() || text == "/") return null
        val cleaned = text.replace(",", "").replace("¥", "").replace("￥", "").replace("元", "")
        val signed = Regex("""[-+]?\s*[0-9]+(?:\.[0-9]{1,2})?""").find(cleaned)?.value?.replace(" ", "") ?: return null
        return signed.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    }

    private fun parseDateTime(text: String): Long {
        val candidate = Regex("20\\d{2}[-/年]\\d{1,2}[-/月]\\d{1,2}(?:日)?(?:\\s+\\d{1,2}:\\d{1,2}(?::\\d{1,2})?)?").find(text)?.value ?: return fallbackNow()
        dateTimeFormats.forEach { formatter ->
            runCatching { return LocalDateTime.parse(candidate, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        }
        dateFormats.forEach { formatter ->
            runCatching { return LocalDate.parse(candidate, formatter).atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        }
        return fallbackNow()
    }

    private fun detectType(text: String, amountText: String): TransactionType = when {
        amountText.trim().startsWith("-") -> TransactionType.EXPENSE
        amountText.trim().startsWith("+") -> TransactionType.INCOME
        amountText.trim() == "/" -> TransactionType.OTHER // 中性交易
        listOf("收入", "收款", "退款", "转入", "已收钱", "入账").any { text.contains(it) } -> TransactionType.INCOME
        listOf("还款", "提现", "充值", "转出", "转入零钱通").any { text.contains(it) } -> TransactionType.OTHER
        else -> TransactionType.EXPENSE
    }

    private fun detectApp(text: String): PaymentApp = when {
        text.contains("支付宝") || text.contains("蚂蚁") -> PaymentApp.ALIPAY
        text.contains("微信") || text.contains("财付通") -> PaymentApp.WECHAT
        text.contains("云闪付") || text.contains("银联") -> PaymentApp.UNION_PAY
        text.contains("京东") -> PaymentApp.JD
        text.contains("抖音") -> PaymentApp.DOUYIN
        text.contains("淘宝") -> PaymentApp.TAOBAO
        text.contains("天猫") -> PaymentApp.TMALL
        text.contains("拼多多") || text.contains("多多") -> PaymentApp.PINDUODUO
        text.contains("美团") -> PaymentApp.MEITUAN
        else -> PaymentApp.UNKNOWN
    }

    private fun extractLooseMerchant(text: String): String? {
        val markers = listOf("交易对方", "商户", "商家", "店铺", "商品", "收款方", "付款给", "订单")
        markers.forEach { marker ->
            Regex("$marker[:：]?\\s*([^，。；;|,]{2,28})").find(text)?.groupValues?.getOrNull(1)?.cleanupMerchant()?.let { return it }
        }
        return null
    }

    private fun defaultMerchant(type: TransactionType): String = when (type) {
        TransactionType.EXPENSE -> "导入消费"
        TransactionType.INCOME -> "导入收入"
        TransactionType.OTHER -> "导入账单"
    }

    private fun String.cleanupMerchant(): String = trim()
        .trim('"', '\'', ' ', ':', '：', '/')
        .replace(Regex("^(给|向|在|商家|商户|店铺|商品|收款方|交易对方)"), "")
        .replace(Regex("(付款|支付|消费|订单|交易成功).*$"), "")
        .trim(' ', ':', '：', '-', '—')
}
