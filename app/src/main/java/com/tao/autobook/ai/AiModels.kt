package com.tao.autobook.ai

import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionType

data class AiRecognitionSettings(
    val enabled: Boolean = false,
    val apiUrl: String = "",
    val model: String = "",
    val apiKeySet: Boolean = false,
    val timeoutSeconds: Int = 60
) {
    val configured: Boolean get() = enabled && apiUrl.isNotBlank() && model.isNotBlank() && apiKeySet
}

data class AiRecognitionConfig(
    val enabled: Boolean,
    val apiUrl: String,
    val model: String,
    val apiKey: String,
    val timeoutSeconds: Int
) {
    val configured: Boolean get() = enabled && apiUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}

data class AiParsedPayment(
    val amountCents: Long?,
    val merchantName: String,
    val paidAt: Long?,
    val paymentApp: PaymentApp,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryHint: String,
    val note: String,
    val confidence: Float,
    val reason: String,
    val rawJson: String,
    val isSpam: Boolean = false
)

data class AiStats(
    var callCount: Int = 0,
    var totalPromptTokens: Int = 0,
    var totalCompletionTokens: Int = 0,
    var totalTokens: Int = 0,
    var lastCallAt: Long = 0L,
    var errorCount: Int = 0
) {
    fun record(success: Boolean, promptTokens: Int = 0, completionTokens: Int = 0) {
        callCount++
        if (success) {
            totalPromptTokens += promptTokens
            totalCompletionTokens += completionTokens
            totalTokens += promptTokens + completionTokens
        } else {
            errorCount++
        }
        lastCallAt = System.currentTimeMillis()
    }
    fun summary(): String = "已调用 ${callCount} 次，失败 ${errorCount} 次"
}

data class AiBillRow(
    val amountCents: Long,
    val merchantName: String,
    val paidAt: String,
    val type: String,
    val categoryHint: String,
    val note: String,
    val paymentApp: String
)
