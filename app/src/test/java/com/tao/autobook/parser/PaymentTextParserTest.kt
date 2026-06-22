package com.tao.autobook.parser

import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PaymentTextParserTest {
    private val parser = PaymentTextParser { 1_700_000_000_000L }

    @Test
    fun parsesAlipayNotification() {
        val parsed = parser.parse("支付宝 支付成功 向瑞幸咖啡付款 ￥18.00", "com.eg.android.AlipayGphone")
        assertNotNull(parsed)
        assertEquals(1800L, parsed!!.amountCents)
        assertEquals(PaymentApp.ALIPAY, parsed.paymentApp)
        assertEquals("瑞幸咖啡", parsed.merchantName)
    }

    @Test
    fun parsesWechatNotification() {
        val parsed = parser.parse("微信支付 付款成功 商户：便利蜂 金额12.50元", "com.tencent.mm")
        assertNotNull(parsed)
        assertEquals(1250L, parsed!!.amountCents)
        assertEquals(PaymentApp.WECHAT, parsed.paymentApp)
        assertEquals("便利蜂", parsed.merchantName)
    }

    @Test
    fun detectsJdFromText() {
        val parsed = parser.parse("京东支付交易成功 商品 京东超市 人民币88.30元", null)
        assertNotNull(parsed)
        assertEquals(8830L, parsed!!.amountCents)
        assertEquals(PaymentApp.JD, parsed.paymentApp)
    }

    @Test
    fun detectsMainstreamShoppingApps() {
        val douyin = parser.parse("抖音支付 订单已支付 店铺：抖音商城旗舰店 ￥36.90", "com.ss.android.ugc.aweme")
        val taobao = parser.parse("淘宝 付款成功 店铺：三只松鼠旗舰店 29.80元", "com.taobao.taobao")
        val meituan = parser.parse("美团支付 支付成功 商家：美团外卖 18元", "com.sankuai.meituan")
        assertEquals(PaymentApp.DOUYIN, douyin!!.paymentApp)
        assertEquals(PaymentApp.TAOBAO, taobao!!.paymentApp)
        assertEquals(PaymentApp.MEITUAN, meituan!!.paymentApp)
    }

    @Test
    fun parsesBillImportCsv() {
        val result = BillImportParser { 1_700_000_000_000L }.parse(
            "交易时间,交易对方,商品名称,收/支,金额,备注\n" +
                "2026-06-15 12:30:00,瑞幸咖啡,拿铁,支出,-18.00,支付宝"
        )
        assertEquals(1, result.rows.size)
        assertEquals(1800L, result.rows.first().amountCents)
        assertEquals("瑞幸咖啡", result.rows.first().merchantName)
    }

    @Test
    fun createsStableDedupeKey() {
        val one = parser.parse("云闪付 支付成功 商户：地铁 6元", "com.unionpay")!!
        val two = parser.parse("云闪付 支付成功 商户：地铁 6元", "com.unionpay")!!
        assertEquals(parser.dedupeKey(one), parser.dedupeKey(two))
    }

    @Test
    fun parsesRefundAsIncome() {
        val parsed = parser.parse("支付宝 退款到账 商户：淘宝订单 ￥29.90", "com.eg.android.AlipayGphone")
        assertNotNull(parsed)
        assertEquals(2990L, parsed!!.amountCents)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("退款", parsed.categoryHint)
    }
}
