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
        }.onFailure {
            android.util.Log.w("AiRecognizer", "Screenshot recognize failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}")
        }
    }

    suspend fun recognizeRaw(bitmap: Bitmap, config: AiRecognitionConfig): Result<AiParsedPayment> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.configured) { "AI 截图识别未配置完整" }
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            val imageBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            val request = buildRequest(config.model, imageBase64, "", "image/png")
            val response = postJson(config, request)
            parseResponse(response)
        }.onFailure {
            android.util.Log.w("AiRecognizer", "Raw screenshot recognize failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}")
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
                .put("thinking", JSONObject().put("type", "disabled"))
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

    private fun buildRequest(model: String, imageBase64: String, ocrText: String, mimeType: String = "image/jpeg"): JSONObject {
        val prompt = """
                            你是中文消费截图记账解析器。只从“真实支付/账单/订单详情”截图提取一笔账单，只返回JSON。
                            JSON字段：amount(number元), merchantName(string), paidAt(string yyyy-MM-dd HH:mm 或空字符串), paymentApp(string ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
                            规则：
                            1. isSpam=true（必须拒绝自动入账）：聊天记录、微信/QQ对话气泡页、朋友圈、验证码、广告、物流、评价、邀请、签到、仅“我已经付款了/已付款/好的收到”文字、没有支付成功页/订单页/账单页特征
                            2. isSpam=false 仅当截图是：支付成功、付款成功、交易成功、订单详情、账单详情、商家转账/收款详情、退款到账，且能看到明确金额
                            3. categoryHint填分类：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
                               - 外卖/骑手/送达/出餐 → 餐饮
                               - 拼多多/淘宝/天猫/京东商城实物购买 → 购物
                               - 京东外卖也归餐饮，不要归购物
                               - 商家转账/福利到账/收款 → 退款或转账（收入）
                            4. note填商品名/服务名，禁止填购物、餐饮等分类名；聊天截图 note 留空
                            5. merchantName填商户名/付款方/收款方；聊天截图 merchantName 留空
                            6. paidAt 时间优先级（非常重要，只填一个）：
                               ① 下单时间 / 创建时间 / 提交订单时间（最高优先，拼多多/电商订单必须用这个）
                               ② 支付时间 / 付款时间 / 转账时间
                               ③ 完成时间 / 成交时间 / 收货时间（最低，禁止优先于下单时间）
                               页面同时出现多个时间时，绝不要用完成时间顶替下单时间；都没有则用“截图时间”；禁止编造年份
                            7. paymentApp 优先根据“截图来源应用包名/截图来源支付App”判断：com.jingdong→JD，com.tencent.mm→WECHAT，alipay→ALIPAY，pinduoduo→PINDUODUO，meituan/sankuai→MEITUAN
                            8. amount 仅提取本次金额；带“+”/收款/到账 → type=INCOME；支出用正数金额、type=EXPENSE
                            9. 聊天/非支付页：isSpam=true, amount=0, confidence≤0.2；真实账单页 confidence≥0.7
                            本地OCR与元数据：
                            ${ocrText.take(1800)}
                        """.trimIndent()
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mimeType;base64,$imageBase64")))
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("temperature", 0)
            .put("max_tokens", 500)
            .put("thinking", JSONObject().put("type", "disabled"))
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
            .put("thinking", JSONObject().put("type", "disabled"))
    }

    private fun buildDefaultNotificationPrompt(rawText: String): String {
        return """你是中文支付通知记账解析器。请从通知标题/正文中提取一笔账单，只返回JSON，不要Markdown。
字段：amount(number, 元), merchantName(string), paidAt(string, yyyy-MM-dd HH:mm 或空字符串), paymentApp(string, ALIPAY/WECHAT/UNION_PAY/JD/DOUYIN/TAOBAO/TMALL/PINDUODUO/MEITUAN/UNKNOWN), type(string, EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
重要规则：
1. 必须返回 isSpam=true：签到、邀请、活动、抽奖、优惠券、广告、营销、贷款、理财、验证码、物流、签收、评价、社保卡、App推送
2. 必须返回 isSpam=false：支付成功、付款成功、扣款成功、白条消费、退款到账
3. isSpam=true时amount填0；isSpam=false时amount和merchantName必须填写
4. categoryHint：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
5. 拼多多/淘宝/京东等电商购物归入"购物"类
6. note填商品名，禁止填购物、餐饮等分类名。无法确定时留空
通知文本：${rawText.take(1600)}"""
    }

    private fun buildTextRequest(model: String, rawText: String): JSONObject {
        val prompt = """
            你是中文支付通知记账解析器。从通知中提取一笔账单，只返回JSON。
            字段：amount(number元), merchantName(string), paidAt(string yyyy-MM-dd HH:mm), paymentApp(string), type(string EXPENSE/INCOME/OTHER), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
            规则：
            1. isSpam=true：签到/邀请/活动/抽奖/优惠券/广告/营销/贷款/验证码/物流/App推送/花呗账单提醒/信用卡账单。此时amount=0, merchantName=""
            2. isSpam=false：支付成功/付款成功/扣款成功/白条消费/退款到账。此时amount和merchantName必须填写
            3. merchantName填真实商户名，如：美团外卖、肯德基、滴滴出行。不能填"AI识别消费"
            4. categoryHint填分类，只能是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
            5. note填商品名，如：咖啡、外卖、洗衣液。禁止填购物、餐饮等分类名，无法确定时留空
            6. 退款到账/原路退回→type=INCOME, categoryHint=退款
            通知文本：${rawText.take(1600)}
        """".trimIndent()
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0)
            .put("max_tokens", 360)
            .put("thinking", JSONObject().put("type", "disabled"))
    }

    private fun buildAccessibilityTextRequest(model: String, rawText: String): JSONObject {
        val prompt = """
            你是中文支付页面记账解析器。从支付成功页面文本中提取一笔消费记录，只返回JSON。
            字段：amount(number元), merchantName(string), paidAt(string yyyy-MM-dd HH:mm), paymentApp(string), type(string EXPENSE/INCOME), categoryHint(string), note(string), confidence(number 0-1), reason(string), isSpam(boolean)。
            规则：
            1. 金额：优先匹配¥/￥后面的数字，如¥12.50。取页面最明显的金额
            2. 商户名：从"付款给""商家""商品说明""收款方"等字段提取。不能填"AI识别消费"，无法确定时用支付App名
            3. categoryHint填分类，只能是：餐饮/交通/购物/生活缴费/娱乐/医疗/教育/转账/人情/退款/工资/奖金/理财/其他
            4. note填商品名，如：咖啡、洗衣液、手机壳。禁止填购物、餐饮等分类名，无法确定时留空
            5. 支付成功页面+有金额→confidence≥0.65。非消费页面→confidence<0.5
            6. 退款相关→type=INCOME, categoryHint=退款
            7. isSpam=true：非支付页面、还款页面、账单提醒、广告页面
            页面文本：${rawText.take(3000)}
        """".trimIndent()
        return JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("temperature", 0)
            .put("max_tokens", 480)
            .put("thinking", JSONObject().put("type", "disabled"))
    }

    fun postJsonPublic(config: AiRecognitionConfig, body: JSONObject): String = postJson(config, body)

    private fun postJson(config: AiRecognitionConfig, body: JSONObject): String {
        val url = normalizeChatCompletionsUrl(config.apiUrl)
        android.util.Log.d("AiRecognizer", "POST ${url}, model=${body.optString("model")}, bodyLen=${body.toString().length}")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.timeoutSeconds * 1000
            readTimeout = config.timeoutSeconds * 1000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            android.util.Log.d("AiRecognizer", "Response: code=${code}, bodyLen=${text.length}, preview=${text.take(120)}")
            if (code !in 200..299) {
                stats.record(false)
                error("AI 接口返回 $code：${text.take(180)}")
            }
            trackUsage(text)
            text
        } finally {
            connection.disconnect()
        }
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
            .put("thinking", JSONObject().put("type", "disabled"))
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
        require(content.isNotBlank()) { "AI 返回内容为空" }
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
        }.map { p ->
            // 如果 note 等于 categoryHint 或是常见分类名，用 merchantName 替代
            val cats = setOf("购物","餐饮","交通","生活缴费","娱乐","医疗","教育","转账","人情","退款","工资","奖金","理财","其他","宠物")
            val fixedNote = if (p.note == p.categoryHint || p.note in cats) {
                p.merchantName.takeIf { it != "AI识别消费" && it.length <= 20 }.orEmpty()
            } else p.note
            p.copy(note = fixedNote)
        }.getOrNull()

        private fun parseAmount(value: Any?): Long? {
                    val number = when (value) {
                        is Number -> value.toDouble()
                        is String -> value.replace(",", "").replace("￥", "").replace("¥", "").replace("元", "").trim().toDoubleOrNull()
                        else -> null
                    } ?: return null
                    // 金额单位是元；超过 1 亿视为脏数据；AI 若已返回「分」会异常大
                    if (number <= 0.0 || number > 100_000_000.0) return null
                    val cents = (number * 100).roundToLong()
                                        return cents.takeIf { it in 1L..10_000_000_000L }
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
            val millis = runCatching {
                val normalized = if (value.length <= 10) "$value 00:00" else value.take(16)
                LocalDateTime.parse(normalized.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.recoverCatching {
                LocalDate.parse(value.take(10)).atTime(LocalTime.MIN).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            // 拒绝离谱时间：早于 2023 或晚于当前+1天，一律当无效
            val now = System.currentTimeMillis()
            val min = LocalDate.of(2023, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val max = now + 24 * 60 * 60 * 1000L
            if (millis < min || millis > max) return null
            return millis
        }
    }
}
