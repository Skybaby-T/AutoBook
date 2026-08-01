package com.tao.autobook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentApp(val label: String, val packages: Set<String>) {
    WECHAT("微信支付", setOf("com.tencent.mm")),
    ALIPAY("支付宝", setOf("com.eg.android.AlipayGphone")),
    UNION_PAY("云闪付", setOf("com.unionpay", "com.unionpay.tsmservice")),
    JD("京东支付", setOf("com.jingdong.app.mall", "com.jd.jrapp", "com.jingdong.app")),
    DOUYIN("抖音支付", setOf("com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite")),
    TAOBAO("淘宝", setOf("com.taobao.taobao", "com.taobao.litetao")),
    TMALL("天猫", setOf("com.tmall.wireless")),
    PINDUODUO("拼多多", setOf("com.xunmeng.pinduoduo")),
    MEITUAN("美团", setOf(
        "com.sankuai.meituan",
        "com.meituan.android.generalcategories",
        "com.sankuai.meituan.takeoutnew"
    )),
    UNKNOWN("未知", emptySet());

    companion object {
        fun fromPackage(packageName: String?): PaymentApp {
            if (packageName.isNullOrBlank()) return UNKNOWN
            entries.firstOrNull { packageName in it.packages }?.let { return it }
            val lower = packageName.lowercase()
            return when {
                lower.contains("tencent.mm") -> WECHAT
                lower.contains("alipay") -> ALIPAY
                lower.contains("unionpay") -> UNION_PAY
                lower.contains("jingdong") || lower.contains("com.jd.") || lower.startsWith("com.jd") -> JD
                lower.contains("aweme") || lower.contains("douyin") -> DOUYIN
                lower.contains("taobao") -> TAOBAO
                lower.contains("tmall") -> TMALL
                lower.contains("pinduoduo") || lower.contains("xunmeng") -> PINDUODUO
                lower.contains("meituan") || lower.contains("sankuai") -> MEITUAN
                else -> UNKNOWN
            }
        }

        /** 从截图文件名解析包名，如 Screenshot_..._com.jingdong.app.mall.jpg */
        fun packageFromScreenshotName(name: String?): String? {
            if (name.isNullOrBlank()) return null
            // Screenshot_2026-07-24-20-04-13-042_com.jingdong.app.mall.jpg
            val m = Regex(
                """(?:Screenshot|screenshot|截图|截屏)[^_]*_(?:\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}(?:-\d+)?)_(.+?)\.(?:jpg|jpeg|png|webp)$""",
                RegexOption.IGNORE_CASE
            ).find(name)
            if (m != null) return m.groupValues[1]
            val m2 = Regex("""(com\.[A-Za-z0-9_.]+)\.(?:jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE).find(name)
            return m2?.groupValues?.getOrNull(1)
        }

        /** 从截图文件名解析时间：yyyy-MM-dd-HH-mm-ss */
        fun timeFromScreenshotName(name: String?): Long? {
            if (name.isNullOrBlank()) return null
            val m = Regex("""(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})-(\d{2})(?:-\d+)?""").find(name) ?: return null
            return runCatching {
                val y = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val d = m.groupValues[3].toInt()
                val h = m.groupValues[4].toInt()
                val mi = m.groupValues[5].toInt()
                val s = m.groupValues[6].toInt()
                java.time.LocalDateTime.of(y, mo, d, h, mi, s)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
        }
    }
}

enum class SourceType { NOTIFICATION, ACCESSIBILITY, SCREENSHOT, MANUAL }
enum class ScreenshotSourceType { MANUAL_UPLOAD, AUTO_CAPTURE }
enum class ScreenshotStatus { PENDING_REVIEW, CONFIRMED, IGNORED }
enum class TransactionType(val label: String) { EXPENSE("支出"), INCOME("收入"), OTHER("其他") }

@Entity(tableName = "categories", indices = [Index(value = ["type", "sortOrder"]), Index(value = ["parentId"])])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
    val type: TransactionType = TransactionType.EXPENSE,
    val isDefault: Boolean = true,
    val parentId: String? = null
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupeKey"], unique = true), Index(value = ["paidAt"]), Index(value = ["categoryId"]), Index(value = ["type"]), Index(value = ["excludeFromStats"])]
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
    val type: TransactionType = TransactionType.EXPENSE,
    /** 不计入收支：退款、提现、还款、内部转账等，不进任何统计与报表 */
    val excludeFromStats: Boolean = false,
    /** 不计入预算：仍进收支统计，但不占预算额度 */
    val excludeFromBudget: Boolean = false
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
    val note: String = "",
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

    // 二级分类 ID
    const val FOOD_BREAKFAST = "food_breakfast"
    const val FOOD_LUNCH = "food_lunch"
    const val FOOD_DINNER = "food_dinner"
    const val FOOD_TAKEOUT = "food_takeout"
    const val FOOD_SNACK = "food_snack"
    const val FOOD_FRUIT = "food_fruit"
    const val TRANSPORT_BUS = "transport_bus"
    const val TRANSPORT_TAXI = "transport_taxi"
    const val TRANSPORT_METRO = "transport_metro"
    const val TRANSPORT_FUEL = "transport_fuel"
    const val TRANSPORT_PARKING = "transport_parking"
    const val BILLS_WATER = "bills_water"
    const val BILLS_ELECTRIC = "bills_electric"
    const val BILLS_GAS = "bills_gas"
    const val BILLS_RENT = "bills_rent"
    const val BILLS_INTERNET = "bills_internet"
    const val ENTERTAINMENT_GAME = "entertainment_game"
    const val ENTERTAINMENT_MOVIE = "entertainment_movie"
    const val ENTERTAINMENT_SPORT = "entertainment_sport"
    const val SHOPPING_CLOTHES = "shopping_clothes"
    const val SHOPPING_DAILY = "shopping_daily"
    const val SHOPPING_DIGITAL = "shopping_digital"

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
        CategoryEntity(INCOME_OTHER, "其他收入", "MoreHoriz", 0xFFDA8F2DL, 90, TransactionType.INCOME),
        // 餐饮子分类
        CategoryEntity(FOOD_BREAKFAST, "早餐", "FreeBreakfast", 0xFFE85D4FL, 11, TransactionType.EXPENSE, parentId = FOOD),
        CategoryEntity(FOOD_LUNCH, "午餐", "Restaurant", 0xFFE85D4FL, 12, TransactionType.EXPENSE, parentId = FOOD),
        CategoryEntity(FOOD_DINNER, "晚餐", "Dining", 0xFFE85D4FL, 13, TransactionType.EXPENSE, parentId = FOOD),
        CategoryEntity(FOOD_TAKEOUT, "外卖", "DeliveryDining", 0xFFE85D4FL, 14, TransactionType.EXPENSE, parentId = FOOD),
        CategoryEntity(FOOD_SNACK, "零食", "Cookie", 0xFFE85D4FL, 15, TransactionType.EXPENSE, parentId = FOOD),
        CategoryEntity(FOOD_FRUIT, "水果", "Spa", 0xFFE85D4FL, 16, TransactionType.EXPENSE, parentId = FOOD),
        // 交通子分类
        CategoryEntity(TRANSPORT_BUS, "公交", "DirectionsBus", 0xFF2F80EDL, 21, TransactionType.EXPENSE, parentId = TRANSPORT),
        CategoryEntity(TRANSPORT_TAXI, "打车", "LocalTaxi", 0xFF2F80EDL, 22, TransactionType.EXPENSE, parentId = TRANSPORT),
        CategoryEntity(TRANSPORT_METRO, "地铁", "Subway", 0xFF2F80EDL, 23, TransactionType.EXPENSE, parentId = TRANSPORT),
        CategoryEntity(TRANSPORT_FUEL, "加油", "LocalGasStation", 0xFF2F80EDL, 24, TransactionType.EXPENSE, parentId = TRANSPORT),
        CategoryEntity(TRANSPORT_PARKING, "停车", "LocalParking", 0xFF2F80EDL, 25, TransactionType.EXPENSE, parentId = TRANSPORT),
        // 生活缴费子分类
        CategoryEntity(BILLS_WATER, "水费", "WaterDrop", 0xFF6F52EDL, 41, TransactionType.EXPENSE, parentId = BILLS),
        CategoryEntity(BILLS_ELECTRIC, "电费", "Bolt", 0xFF6F52EDL, 42, TransactionType.EXPENSE, parentId = BILLS),
        CategoryEntity(BILLS_GAS, "燃气", "LocalFireDepartment", 0xFF6F52EDL, 43, TransactionType.EXPENSE, parentId = BILLS),
        CategoryEntity(BILLS_RENT, "房租", "Home", 0xFF6F52EDL, 44, TransactionType.EXPENSE, parentId = BILLS),
        CategoryEntity(BILLS_INTERNET, "网费", "Wifi", 0xFF6F52EDL, 45, TransactionType.EXPENSE, parentId = BILLS),
        // 娱乐子分类
        CategoryEntity(ENTERTAINMENT_GAME, "游戏", "SportsEsports", 0xFFE0528DL, 51, TransactionType.EXPENSE, parentId = ENTERTAINMENT),
        CategoryEntity(ENTERTAINMENT_MOVIE, "电影", "Movie", 0xFFE0528DL, 52, TransactionType.EXPENSE, parentId = ENTERTAINMENT),
        CategoryEntity(ENTERTAINMENT_SPORT, "运动", "FitnessCenter", 0xFFE0528DL, 53, TransactionType.EXPENSE, parentId = ENTERTAINMENT),
        // 购物子分类
        CategoryEntity(SHOPPING_CLOTHES, "服装", "Checkroom", 0xFFD98B2BL, 31, TransactionType.EXPENSE, parentId = SHOPPING),
        CategoryEntity(SHOPPING_DAILY, "日用品", "CleaningServices", 0xFFD98B2BL, 32, TransactionType.EXPENSE, parentId = SHOPPING),
        CategoryEntity(SHOPPING_DIGITAL, "数码", "Devices", 0xFFD98B2BL, 33, TransactionType.EXPENSE, parentId = SHOPPING),
    )

    fun fallbackFor(type: TransactionType): String = when (type) {
        TransactionType.EXPENSE -> OTHER
        TransactionType.INCOME -> INCOME_OTHER
        TransactionType.OTHER -> OTHER
    }
}

