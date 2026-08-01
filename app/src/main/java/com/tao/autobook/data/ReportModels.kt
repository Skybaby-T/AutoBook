package com.tao.autobook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 报表聚合结果模型。
 * 全部由 SQL 聚合直接产出，不再依赖内存里「最近 500 条」账单，
 * 保证年报/自定义区间统计不失真。
 */

/** 月度预算。categoryId 为 TOTAL_BUDGET_ID 时表示总预算 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: String,
    val amountCents: Long,
    val updatedAt: Long = 0L,
)

/** 总预算的固定 key */
const val TOTAL_BUDGET_ID = "__TOTAL__"

/** 区间总览：合计、笔数、最大单笔、有账单的天数 */
data class RangeSummaryRow(
    val total: Long,
    val cnt: Int,
    val maxAmount: Long,
    val activeDays: Int,
)

/** 按分类聚合 */
data class CategoryTotalRow(
    val categoryId: String,
    val total: Long,
    val cnt: Int,
)

/** 按商家聚合（排行榜） */
data class MerchantTotalRow(
    val merchantName: String,
    val total: Long,
    val cnt: Int,
)

/** 按支付方式聚合 */
data class PaymentAppTotalRow(
    val paymentApp: String,
    val total: Long,
    val cnt: Int,
)

/** 按天聚合，bucket 形如 2026-08-02 */
data class DayTotalRow(
    val bucket: String,
    val total: Long,
    val cnt: Int,
)

/** 按月聚合，bucket 形如 2026-08 */
data class MonthTotalRow(
    val bucket: String,
    val total: Long,
    val cnt: Int,
)

/** 按星期聚合，weekday 遵循 SQLite strftime('%w')：0=周日 … 6=周六 */
data class WeekdayTotalRow(
    val weekday: Int,
    val total: Long,
    val cnt: Int,
)

/** 一个区间的完整报表快照 */
data class ReportSnapshot(
    val summary: RangeSummaryRow = RangeSummaryRow(0L, 0, 0L, 0),
    /** 上一周期同类型合计，用于环比 */
    val prevTotal: Long = 0L,
    /** 同区间对向类型合计：看支出时这里是收入，用于算结余 */
    val counterTotal: Long = 0L,
    val categories: List<CategoryTotalRow> = emptyList(),
    val merchants: List<MerchantTotalRow> = emptyList(),
    val paymentApps: List<PaymentAppTotalRow> = emptyList(),
    val days: List<DayTotalRow> = emptyList(),
    val months: List<MonthTotalRow> = emptyList(),
    val weekdays: List<WeekdayTotalRow> = emptyList(),
    /** 预算口径合计：排除不计收支 + 不计预算 */
    val budgetSpent: Long = 0L,
    /** 预算口径的分类合计 */
    val budgetCategories: List<CategoryTotalRow> = emptyList(),
    /** 区间内被标记「不计入收支」的金额合计 */
    val excludedTotal: Long = 0L,
    /** 区间内被标记「不计入收支」的笔数 */
    val excludedCount: Int = 0,
)

/** 分类下钻结果 */
data class CategoryDrillDown(
    val merchants: List<MerchantTotalRow> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
)
