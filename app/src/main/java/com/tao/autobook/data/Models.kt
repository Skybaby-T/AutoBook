package com.tao.autobook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentApp(val label: String, val packages: Set<String>) {
    WECHAT("微信支付", setOf("com.tencent.mm")),
    ALIPAY("支付宝", setOf("com.eg.android.AlipayGphone")),
    UNION_PAY("云闪付", setOf("com.unionpay", "com.unionpay.tsmservice")),
    JD("京东支付", setOf("com.jingdong.app.mall", "com.jd.jrapp")),
    DOUYIN("抖音支付", setOf("com.ss.android.ugc.aweme")),
    TAOBAO("淘宝", setOf("com.taobao.taobao")),
    TMALL("天猫", setOf("com.tmall.wireless")),
    PINDUODUO("拼多多", setOf("com.xunmeng.pinduoduo")),
    MEITUAN("美团", setOf("com.sankuai.meituan", "com.meituan.android.generalcategories")),
    UNKNOWN("未知", emptySet());

    companion object {
        fun fromPackage(packageName: String?): PaymentApp = entries.firstOrNull { app ->
            packageName != null && packageName in app.packages
        } ?: UNKNOWN
    }
}

enum class SourceType { NOTIFICATION, ACCESSIBILITY, SCREENSHOT, MANUAL }
enum class ScreenshotSourceType { MANUAL_UPLOAD, AUTO_CAPTURE }
enum class ScreenshotStatus { PENDING_REVIEW, CONFIRMED, IGNORED }
enum class TransactionType(val label: String) { EXPENSE("支出"), INCOME("收入"), OTHER("其他") }

@Entity(tableName = "categories", indices = [Index(value = ["type", "sortOrder"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
    val type: TransactionType = TransactionType.EXPENSE,
    val isDefault: Boolean = true
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupeKey"], unique = true), Index(value = ["paidAt"]), Index(value = ["categoryId"]), Index(value = ["type"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val currency: String = "CNY",
    val merchantName: String,
    val categoryId: String,
    val paymentApp: PaymentApp,
    val paidAt: Long,
    val sourceType: SourceType,
    val screenshotId: Long? = null,
    val dedupeKey: String,
    val confidence: Float,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val type: TransactionType = TransactionType.EXPENSE
) {
    val amount: Double get() = amountCents / 100.0
}

@Entity(
    tableName = "screenshots",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["parsedTransactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["parsedTransactionId"]), Index(value = ["capturedAt"])]
)
data class ScreenshotCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedFilePath: String,
    val sourceType: ScreenshotSourceType,
    val capturedAt: Long,
    val ocrTextHash: String,
    val ocrRawTextEncrypted: String,
    val parsedTransactionId: Long? = null,
    val status: ScreenshotStatus
)

@Entity(tableName = "merchant_rules", indices = [Index(value = ["keyword", "paymentApp"], unique = true)])
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val categoryId: String,
    val paymentApp: PaymentApp,
    val priority: Int,
    val createdByUser: Boolean
)

@Entity(tableName = "raw_captures", indices = [Index(value = ["capturedAt"])])
data class RawCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: SourceType,
    val paymentApp: PaymentApp,
    val capturedAt: Long,
    val titleHash: String,
    val textHash: String,
    val parsedTransactionId: Long? = null
)

data class ParsedPayment(
    val amountCents: Long,
    val merchantName: String,
    val paymentApp: PaymentApp,
    val paidAt: Long,
    val confidence: Float,
    val rawText: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryHint: String = "",
    val isSpam: Boolean = false
)

data class PendingScreenshotReview(
    val id: Long,
    val capturedAt: Long,
    val sourceType: ScreenshotSourceType,
    val ocrPreview: String,
    val suggestedMerchant: String,
    val suggestedAmountCents: Long?,
    val suggestedCategoryId: String,
    val suggestedPaymentApp: PaymentApp,
    val confidence: Float
)

data class CaptureResult(
    val transaction: TransactionEntity?,
    val created: Boolean
)

@Entity(tableName = "auto_book_logs", indices = [Index(value = ["createdAt"])])
data class AutoBookLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val action: String,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ImportedBillRow(
    val amountCents: Long,
    val merchantName: String,
    val paidAt: Long,
    val paymentApp: PaymentApp,
    val type: TransactionType,
    val note: String = "",
    val rawText: String = ""
)

data class BillImportResult(
    val successCount: Int = 0,
    val duplicateCount: Int = 0,
    val failedCount: Int = 0,
    val unsupportedCount: Int = 0
) {
    operator fun plus(other: BillImportResult): BillImportResult = BillImportResult(
        successCount = successCount + other.successCount,
        duplicateCount = duplicateCount + other.duplicateCount,
        failedCount = failedCount + other.failedCount,
        unsupportedCount = unsupportedCount + other.unsupportedCount
    )
}

object BuiltInCategories {
    const val FOOD = "food"
    const val TRANSPORT = "transport"
    const val SHOPPING = "shopping"
    const val BILLS = "bills"
    const val ENTERTAINMENT = "entertainment"
    const val MEDICAL = "medical"
    const val EDUCATION = "education"
    const val TRANSFER = "transfer"
    const val SOCIAL = "social"
    const val OTHER = "other"

    const val SALARY = "salary"
    const val REFUND = "refund"
    const val BONUS = "bonus"
    const val FINANCE = "finance"
    const val INCOME_OTHER = "income_other"

    // 保留旧分类ID以兼容数据库，但UI中不显示
    const val REPAYMENT = "repayment"
    const val TOP_UP = "top_up"
    const val WITHDRAW = "withdraw"
    const val OTHER_MISC = "other_misc"

    val defaults = listOf(
        CategoryEntity(FOOD, "餐饮", "Restaurant", 0xFFE85D4FL, 10, TransactionType.EXPENSE),
        CategoryEntity(TRANSPORT, "交通", "DirectionsCar", 0xFF2F80EDL, 20, TransactionType.EXPENSE),
        CategoryEntity(SHOPPING, "购物", "ShoppingBag", 0xFFD98B2BL, 30, TransactionType.EXPENSE),
        CategoryEntity(BILLS, "生活缴费", "ReceiptLong", 0xFF6F52EDL, 40, TransactionType.EXPENSE),
        CategoryEntity(ENTERTAINMENT, "娱乐", "Movie", 0xFFE0528DL, 50, TransactionType.EXPENSE),
        CategoryEntity(MEDICAL, "医疗", "LocalHospital", 0xFF20A67AL, 60, TransactionType.EXPENSE),
        CategoryEntity(EDUCATION, "教育", "School", 0xFF4F6F52L, 70, TransactionType.EXPENSE),
        CategoryEntity(TRANSFER, "转账", "SwapHoriz", 0xFF7A7A7AL, 80, TransactionType.EXPENSE),
        CategoryEntity(SOCIAL, "人情", "Group", 0xFFB25E3CL, 90, TransactionType.EXPENSE),
        CategoryEntity(OTHER, "其他", "MoreHoriz", 0xFF5C6470L, 100, TransactionType.EXPENSE),
        CategoryEntity(SALARY, "工资", "Payments", 0xFFFFB547L, 10, TransactionType.INCOME),
        CategoryEntity(REFUND, "退款", "AssignmentReturn", 0xFFFF9F43L, 20, TransactionType.INCOME),
        CategoryEntity(BONUS, "奖金", "CardGiftcard", 0xFFF4A340L, 30, TransactionType.INCOME),
        CategoryEntity(FINANCE, "理财", "Savings", 0xFFE5A244L, 40, TransactionType.INCOME),
        CategoryEntity(INCOME_OTHER, "其他收入", "MoreHoriz", 0xFFDA8F2DL, 90, TransactionType.INCOME)
    )

    fun fallbackFor(type: TransactionType): String = when (type) {
        TransactionType.EXPENSE -> OTHER
        TransactionType.INCOME -> INCOME_OTHER
        TransactionType.OTHER -> OTHER
    }
}

