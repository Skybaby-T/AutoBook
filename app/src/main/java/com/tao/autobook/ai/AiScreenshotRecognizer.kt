package com.tao.autobook.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Base64
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToLong

class AiScreenshotRecognizer {
    suspend fun recognize(bitmap: Bitmap, config: AiRecognitionConfig, ocrText: String): Result<AiParsedPayment> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.configured) { "AI 截图识别未配置完整" }
            val imageBase64 = bitmap.compressForAi()
            val request = buildRequest(config.model, imageBase64, ocrText)
            val response = postJson(config, request)
            parseResponse(response)
        }
    }

    suspend fun recognizePaymentText(rawText: String, config: AiRecognitionConfig): Result<AiParsedPayment> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.configured) { "AI 识别未配置完整" }
            require(rawText.isNotBlank()) { "通知文本为空" }
            val request = buildTextRequest(config.model, rawText)
            val response = postJson(config, request)
            parseResponse(response)
        }
    }

    suspend fun recognizeAccessibilityText(rawText: String, config: AiRecognitionConfig): Result<AiParsedPayment> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.configured) { "AI 识别未配置完整" }
            require(rawText.isNotBlank()) { "无障碍页面文本为空" }
            val request = buildAccessibilityTextRequest(config.model, rawText)
            val response = postJson(config, request)
            parseResponse(response)
        }
    }

    suspend fun testConnection(config: AiRecognitionConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.configured) { "请先填写 API 地址、模型名和密钥" }
            val request = JSONObject()
                .put("model", config.model)
                .put("messages", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("content", "只回复 OK")))
                .put("temperature", 0)
                .put("max_tokens", 8)
            validateChatResponse(postJson(config, request))
        }.map { Unit }
    }

    suspend fun listModels(apiUrl: String, apiKey: String, timeoutSeconds: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiUrl.isNotBlank()) { "请先填写 API 地址" }
            require(apiKey.isNotBlank()) { "请先填写 API Key" }
            val url = normalizeModelsUrl(apiUrl)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutSeconds.coerceIn(8, 90) * 1000
                readTimeout = timeoutSeconds.coerceIn(8, 90) * 1000
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("模型列表接口返回 $code：${text.take(180)}")
            val models = parseModelIds(text).ifEmpty { error("模型列表为空或格式不兼容") }
            val candidates = visionFirst(models).take(60)
            candidates.filter { model -> supportsVision(apiUrl, apiKey, model, timeoutSeconds) }
                .ifEmpty { error("没有验证到支持图片理解的模型") }
        }
    }

    private fun buildRequest(model: String, imageBase64: String, ocrText: String): JSONObject {
        val prompt = """
            你是一个中文消费截图记账解析器。请从支付截图中提取一笔最明确的消费记录。
            只返回 JSON，不要解释，不要 Markdown。
            JSON 字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string, EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string)。
            如果截图里有多笔记录，优先选择用户点开的那一笔或页面主体最明显的一笔。
            如果能看到金额，amount 必须填写纯数字元；merchantName 无法确定时填商品名、交易对象或平台名；paidAt 无法确定时留空；note 写适合账单展示的简短备注。
            如果这是消费、付款、账单或订单截图，且能识别金额，请给 confidence 不低于 0.65，方便自动入账。
            只有在图片不是消费/付款/账单/订单截图，或看不到金额时，confidence 才低于 0.5。
            本地 OCR 文本：${ocrText.take(1600)}
        """.trimIndent()
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("temperature", 0)
            .put("max_tokens", 500)
    }

    fun buildTextRequestWithPrompt(model: String, rawText: String, customPrompt: String): JSONObject {
        val prompt = if (customPrompt.isNotBlank()) {
            customPrompt + "\n\n通知文本：${rawText.take(1600)}"
        } else {
            // Use default prompt
            buildDefaultNotificationPrompt(rawText)
        }
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0)
            .put("max_tokens", 360)
    }

    private fun buildDefaultNotificationPrompt(rawText: String): String {
        return """你是中文支付通知记账解析器。请从通知标题/正文中提取一笔账单，只返回JSON，不要Markdown。
字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string, EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
规则：只有真实消费才算有效账单；isSpam=true时amount填0；categoryHint必须是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
通知文本：${rawText.take(1600)}"""
    }

    private fun buildTextRequest(model: String, rawText: String): JSONObject {
        val prompt = """
            你是中文支付通知记账解析器。请从通知标题/正文中提取一笔账单，只返回 JSON，不要 Markdown。
            字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string, EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
            重要规则：
            1. 必须返回 isSpam=true 的通知：
               - 花呗/信用卡月度账单提醒（"花呗账单XXX元"、"点击还款"、"本期账单"）
               - 广告推送、营销活动、优惠券、红包领取、抽奖
               - 贷款推广、理财推荐、保险推销
               - App功能推送、活动通知、版本更新
               - 签到提醒、打卡提醒、会员续费提醒
            2. 必须返回 isSpam=false 的通知：
               - 京东白条消费通知（"白条交易提醒"、"消费X元"）
               - 支付宝/微信支付成功通知
               - 任何包含"支付成功""付款成功""购买成功""扣款成功"的通知
               - 退款到账通知
            3. isSpam=true 时 amount 填 0，merchantName 填空字符串
            4. isSpam=false 时 amount 和 merchantName 必须填写
            5. 区分关键："交易提醒"+"消费X元"=真实消费，"账单提醒"+"待还"=账单
            3. 退款、退款到账、原路退回必须返回 type=INCOME，categoryHint=退款
            4. categoryHint 必须是以下之一：餐饮、交通、购物、生活缴费、娱乐、医疗、教育、转账、人情、退款、工资、奖金、理财、其他
            5. merchantName 必须是真实商户名或商品名，不能是"AI识别消费""未知消费"等无意义值。无法确定时用支付App名。
            6. 通知文本中的金额可能是待还金额（如"花呗账单1713.48元"），不是实际消费金额，注意区分。

            通知文本：${rawText.take(1600)}
        """".trimIndent()
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0)
            .put("max_tokens", 360)
    }

    private fun buildAccessibilityTextRequest(model: String, rawText: String): JSONObject {
        val prompt = """
            你是中文支付页面记账解析器。以下是从支付成功页面通过无障碍服务提取的文本。
            请从中提取一笔最明确的消费或退款记录，只返回 JSON，不要 Markdown。
            字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string, EXPENSE/INCOME), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。

            重要规则：
            1. 金额提取：优先匹配 ¥ 或 ￥ 后面的数字，如 ¥12.50；也匹配"金额：¥12.50"或"12.50元"格式。取页面中最明显的那个金额。
            2. 商户提取（重要）：从"付款给""商家""商户""店铺""商品说明""收款方""商品名""交易对方"等字段提取真实商户名称。绝对不能返回"AI识别消费""未知消费"等无意义值。如果页面有商品名，用商品名作为商户。如果完全无法确定，用支付App名（如"微信支付""支付宝"）。注意："回头客""的组合"等可能是商户名或商品名，应如实记录。
            3. 忽略页面导航栏、底部菜单、广告、推荐内容、"返回""完成"等按钮文字。
            4. 如果是支付成功/交易成功页面且能看到金额，confidence 不低于 0.65。
            5. 只有非消费页面（如首页、设置页）或完全看不到金额时，confidence 低于 0.5。
            6. 退款、退回相关文本返回 type=INCOME，categoryHint=退款。
            7. isSpam 在以下情况返回 true：页面不是支付相关、是花呗/白条还款页面、是账单提醒页面、是广告页面。
            8. categoryHint 必须是以下之一：餐饮、交通、购物、生活缴费、娱乐、医疗、教育、转账、人情、退款、工资、奖金、理财、其他

            页面文本：
            ${rawText.take(3000)}
        """".trimIndent()
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0)
            .put("max_tokens", 480)
    }

    fun postJsonPublic(config: AiRecognitionConfig, body: JSONObject): String = postJson(config, body)

    private fun postJson(config: AiRecognitionConfig, body: JSONObject): String {
        stats.record(true) // will be updated with tokens after response
        val connection = (URL(normalizeChatCompletionsUrl(config.apiUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutSeconds * 1000
            readTimeout = config.timeoutSeconds * 1000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("AI 接口返回 $code：${text.take(180)}")
        return text
    }

    private fun normalizeChatCompletionsUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "API 地址必须以 http:// 或 https:// 开头" }
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            trimmed.matches(Regex("https?://[^/]+")) -> "$trimmed/v1/chat/completions"
            else -> trimmed
        }
    }

    private fun normalizeModelsUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "API 地址必须以 http:// 或 https:// 开头" }
        return when {
            trimmed.endsWith("/models") -> trimmed
            trimmed.endsWith("/chat/completions") -> trimmed.removeSuffix("/chat/completions") + "/models"
            trimmed.endsWith("/v1") -> "$trimmed/models"
            trimmed.matches(Regex("https?://[^/]+")) -> "$trimmed/v1/models"
            else -> "$trimmed/models"
        }
    }

    private fun parseModelIds(response: String): List<String> {
        val root = JSONObject(response)
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            val item = data.opt(index)
            when (item) {
                is JSONObject -> item.optString("id").ifBlank { item.optString("name") }.ifBlank { null }
                is String -> item
                else -> null
            }
        }.distinct().sorted()
    }

    private fun visionFirst(models: List<String>): List<String> {
        val visionHints = listOf("vision", "vl", "flash", "omni", "4o", "image", "visual", "qwen-vl", "gemini", "claude")
        return models.sortedWith(compareBy<String> { model ->
            val lower = model.lowercase()
            if (visionHints.any { lower.contains(it) }) 0 else 1
        }.thenBy { it.lowercase() })
    }

    private fun supportsVision(apiUrl: String, apiKey: String, model: String, timeoutSeconds: Int): Boolean {
        val config = AiRecognitionConfig(
            enabled = true,
            apiUrl = apiUrl,
            model = model,
            apiKey = apiKey,
            timeoutSeconds = timeoutSeconds.coerceIn(8, 90)
        )
        val request = buildVisionProbeRequest(model)
        return runCatching { validateChatResponse(postJson(config, request)) }.isSuccess
    }

    private fun buildVisionProbeRequest(model: String): JSONObject {
        val imageBase64 = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            bitmap.recycle()
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "请判断图片是否可见，只回复 OK。"))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")))
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("temperature", 0)
            .put("max_tokens", 8)
    }

    private fun validateChatResponse(response: String) {
        val root = runCatching { JSONObject(response) }.getOrElse { error("AI 接口没有返回 JSON，请确认地址是 /v1/chat/completions") }
        val choices = root.optJSONArray("choices") ?: error("AI 接口返回缺少 choices，请确认是 OpenAI 兼容 Chat Completions 接口")
        val content = choices.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        require(content != null) { "AI 接口返回缺少 message.content" }
    }

    private fun parseResponse(response: String): AiParsedPayment {
        val root = JSONObject(response)
        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.opt("content")
            ?.let { value ->
                when (value) {
                    is String -> value
                    is JSONArray -> (0 until value.length()).joinToString("\n") { idx -> value.optJSONObject(idx)?.optString("text").orEmpty() }
                    else -> value.toString()
                }
            }
            ?: response
        return parseAiJson(content.extractJsonObject()) ?: error("AI 未返回有效 JSON")
    }

    private fun String.extractJsonObject(): String {
        val cleaned = replace("```json", "", ignoreCase = true).replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 未返回 JSON" }
        return cleaned.substring(start, end + 1)
    }

    private fun Bitmap.compressForAi(): String {
        val maxSide = 1280f
        val scale = minOf(1f, maxSide / maxOf(width, height).toFloat())
        val target = if (scale < 1f) {
            Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postScale(scale, scale) }, true)
        } else this
        val output = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, 85, output)
        if (target !== this) target.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun recognizeBillImport(csvText: String, config: AiRecognitionConfig): Result<List<AiBillRow>> = withContext(Dispatchers.IO) {
        runCatching {
            val truncated = csvText.take(8000)
            val prompt = """你是中文账单导入解析器。分析以下支付宝/微信/京东等平台导出的CSV账单数据。
只返回JSON数组，不要Markdown，不要解释。
每个元素字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm:ss), type(string, EXPENSE/INCOME/OTHER), categoryHint(string), note(string), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN)。
规则：
1. 识别CSV表头，按列名提取字段
2. 金额必须是纯数字（元），不能是日期
3. merchantName取交易对方/商户名，不要取交易状态
4. 收入类type=INCOME，支出类type=EXPENSE，不计收支/退款=OTHER
5. categoryHint根据商品说明和交易分类推断
6. 跳过表头前的说明文字和空行
7. 跳过交易状态为"交易关闭"的记录
CSV数据：
$truncated"""
            val content = org.json.JSONArray()
                .put(org.json.JSONObject().put("type", "text").put("text", prompt))
            val body = org.json.JSONObject()
                .put("model", config.model)
                .put("messages", org.json.JSONArray().put(org.json.JSONObject().put("role", "user").put("content", content)))
                .put("temperature", 0)
                .put("max_tokens", 4000)
            val response = postJson(config, body)
            val jsonText = response.let { s -> val start = s.indexOf("["); val end = s.lastIndexOf("]"); if (start >= 0 && end > start) s.substring(start, end + 1) else s }
            val arr = org.json.JSONArray(jsonText)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                AiBillRow(
                    amountCents = (obj.optDouble("amount", 0.0) * 100).toLong(),
                    merchantName = obj.optString("merchantName", ""),
                    paidAt = obj.optString("paidAt", ""),
                    type = obj.optString("type", "EXPENSE"),
                    categoryHint = obj.optString("categoryHint", ""),
                    note = obj.optString("note", ""),
                    paymentApp = obj.optString("paymentApp", "UNKNOWN")
                )
            }.filter { it.amountCents > 0 }
        }
    }



    private fun trackUsage(response: String) {
        runCatching {
            val json = org.json.JSONObject(response)
            val usage = json.optJSONObject("usage")
            if (usage != null) {
                val prompt = usage.optInt("prompt_tokens", 0)
                val completion = usage.optInt("completion_tokens", 0)
                stats.record(true, prompt, completion)
            } else {
                stats.record(true)
            }
        }.getOrElse { stats.record(true) }
    }

    fun recordFailure() {
        stats.record(false)
    }

    companion object {
        var stats = AiStats()
            private set
        fun resetStats() { stats = AiStats() }

        fun parseAiJson(jsonText: String): AiParsedPayment? = runCatching {
            val json = JSONObject(jsonText)
            val amount = parseAmount(json.opt("amount"))
            val isSpam = json.optBoolean("isSpam", false)
            AiParsedPayment(
                amountCents = amount,
                merchantName = json.optString("merchantName").ifBlank { "AI识别消费" },
                paidAt = parsePaidAt(json.optString("paidAt")),
                paymentApp = runCatching { PaymentApp.valueOf(json.optString("paymentApp", "UNKNOWN")) }.getOrDefault(PaymentApp.UNKNOWN),
                type = parseType(json.optString("type")),
                categoryHint = json.optString("categoryHint"),
                note = json.optString("note"),
                confidence = parseConfidence(json.opt("confidence")),
                reason = json.optString("reason"),
                rawJson = json.toString(),
                isSpam = isSpam
            )
        }.getOrNull()

        private fun parseAmount(value: Any?): Long? {
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").replace("￥", "").replace("¥", "").replace("元", "").trim().toDoubleOrNull()
                else -> null
            } ?: return null
            return number.takeIf { it > 0 }?.let { (it * 100).roundToLong() }
        }

        private fun parseConfidence(value: Any?): Float {
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().removeSuffix("%").toDoubleOrNull()?.let { if (value.trim().endsWith("%")) it / 100.0 else it }
                else -> null
            } ?: return 0.65f
            val normalized = if (number > 1.0) number / 100.0 else number
            return normalized.toFloat().coerceIn(0f, 1f)
        }

        private fun parseType(value: String): TransactionType {
            val text = value.trim().uppercase()
            return runCatching { TransactionType.valueOf(text) }.getOrElse {
                when {
                    text.contains("INCOME") || text.contains("收入") || text.contains("退款") -> TransactionType.INCOME
                    text.contains("OTHER") || text.contains("其他") -> TransactionType.OTHER
                    else -> TransactionType.EXPENSE
                }
            }
        }

        private fun parsePaidAt(text: String): Long? {
            val value = text.trim().replace('/', '-').replace('年', '-').replace('月', '-').replace("日", " ")
            if (value.isBlank()) return null
            return runCatching {
                val normalized = if (value.length <= 10) "$value 00:00" else value.take(16)
                LocalDateTime.parse(normalized.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.recoverCatching {
                LocalDate.parse(value.take(10)).atTime(LocalTime.MIN).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }
}
