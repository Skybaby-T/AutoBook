package com.tao.autobook.ai

import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiScreenshotRecognizerTest {
    @Test
    fun parsesStringAmountAndPercentConfidence() {
        val parsed = AiScreenshotRecognizer.parseAiJson(
            """
            {
              "amount": "18.90元",
              "merchantName": "支付宝商家服务",
              "paidAt": "2026-06-16 10:30",
              "paymentApp": "ALIPAY",
              "categoryHint": "餐饮",
              "note": "早餐",
              "confidence": "90%",
              "reason": "截图显示支付成功和金额"
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(1890L, parsed!!.amountCents)
        assertEquals(PaymentApp.ALIPAY, parsed.paymentApp)
        assertEquals(0.9f, parsed.confidence, 0.001f)
    }

    @Test
    fun normalizesHundredScaleConfidence() {
        val parsed = AiScreenshotRecognizer.parseAiJson(
            """
            {
              "amount": 36.5,
              "merchantName": "抖音商城",
              "paymentApp": "DOUYIN",
              "confidence": 85
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(3650L, parsed!!.amountCents)
        assertEquals(0.85f, parsed.confidence, 0.001f)
    }

    @Test
    fun parsesRefundTypeFromAiJson() {
        val parsed = AiScreenshotRecognizer.parseAiJson(
            """
            {
              "amount": 19.9,
              "merchantName": "淘宝退款",
              "paymentApp": "TAOBAO",
              "type": "INCOME",
              "categoryHint": "退款",
              "confidence": 0.88
            }
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals("退款", parsed.categoryHint)
    }
}
