package com.tao.autobook.ui

import com.tao.autobook.data.BudgetEntity
import com.tao.autobook.data.CategoryDrillDown
import com.tao.autobook.data.ReportSnapshot
import com.tao.autobook.data.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/** 报表周期粒度 */
enum class ReportPeriod(val label: String) {
    Week("周"),
    Month("月"),
    Year("年"),
    All("全部"),
    Custom("自定义")
}

/** 报表页状态 */
data class ReportUiState(
    val period: ReportPeriod = ReportPeriod.Month,
    val type: TransactionType = TransactionType.EXPENSE,
    /** 当前周期锚点：周报取该周、月报取该月、年报取该年 */
    val anchor: LocalDate = LocalDate.now(),
    val customStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    val customEnd: LocalDate = LocalDate.now(),
    /** 「全部」区间的真实起点，由最早一笔账单决定 */
    val allStart: LocalDate = LocalDate.now().withDayOfMonth(1),
    /** 月度周期起始日（1-28）。1 = 自然月 */
    val monthStartDay: Int = 1,
    val snapshot: ReportSnapshot = ReportSnapshot(),
    val budgets: List<BudgetEntity> = emptyList(),
    /** 展开下钻的分类，null 表示未展开 */
    val drillCategoryId: String? = null,
    val drill: CategoryDrillDown = CategoryDrillDown(),
    val loading: Boolean = false,
) {
    /** 当前区间的起止日期 */
    val range: ClosedRange<LocalDate> get() = when (period) {
        ReportPeriod.Week -> anchor.with(java.time.DayOfWeek.MONDAY)..anchor.with(java.time.DayOfWeek.SUNDAY)
        ReportPeriod.Month -> monthCycle(anchor, monthStartDay)
        ReportPeriod.Year -> anchor.withDayOfYear(1)..anchor.withDayOfYear(anchor.lengthOfYear())
        ReportPeriod.All -> allStart..LocalDate.now()
        ReportPeriod.Custom -> customStart..customEnd
    }

    /** 上一周期的起止日期，用于环比；「全部」和自定义不算环比 */
    val prevRange: ClosedRange<LocalDate>? get() = when (period) {
        ReportPeriod.Week -> anchor.minusWeeks(1).let { it.with(java.time.DayOfWeek.MONDAY)..it.with(java.time.DayOfWeek.SUNDAY) }
        ReportPeriod.Month -> monthCycle(range.start.minusDays(1), monthStartDay)
        ReportPeriod.Year -> anchor.minusYears(1).let { it.withDayOfYear(1)..it.withDayOfYear(it.lengthOfYear()) }
        ReportPeriod.All, ReportPeriod.Custom -> null
    }

    /** 区间显示文案 */
    val rangeLabel: String get() = when (period) {
        ReportPeriod.Week -> "${range.start.monthValue}/${range.start.dayOfMonth} - ${range.endInclusive.monthValue}/${range.endInclusive.dayOfMonth}"
        // 自定义起始日时把真实区间标出来，避免「8月」实际是 7/10-8/9 造成误解
        ReportPeriod.Month -> if (monthStartDay == 1) {
            "${range.start.year}年${range.start.monthValue}月"
        } else {
            "${range.start.monthValue}/${range.start.dayOfMonth} - ${range.endInclusive.monthValue}/${range.endInclusive.dayOfMonth}"
        }
        ReportPeriod.Year -> "${anchor.year}年"
        ReportPeriod.All -> "全部账单"
        ReportPeriod.Custom -> "${customStart.monthValue}/${customStart.dayOfMonth} - ${customEnd.monthValue}/${customEnd.dayOfMonth}"
    }

    /** 环比方向的对比文案；无法环比时为空 */
    val prevLabel: String get() = when (period) {
        ReportPeriod.Week -> "上周"
        ReportPeriod.Month -> "上月"
        ReportPeriod.Year -> "去年"
        else -> ""
    }

    /** 是否还能往后翻（不允许翻到未来区间） */
    val canGoNext: Boolean get() = when (period) {
        ReportPeriod.Week, ReportPeriod.Month, ReportPeriod.Year -> range.endInclusive.isBefore(LocalDate.now())
        else -> false
    }

    /** 周期切换按钮是否可翻页 */
    val pageable: Boolean get() = period == ReportPeriod.Week || period == ReportPeriod.Month || period == ReportPeriod.Year

    /** 区间天数，用于日均 */
    val dayCount: Int get() {
        val start = range.start
        val end = minOf(range.endInclusive, LocalDate.now())
        return if (end.isBefore(start)) 1 else (java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
    }
}

/** 日期 → 当天 00:00:00 的毫秒 */
fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 日期 → 当天 23:59:59.999 的毫秒 */
fun LocalDate.endMillis(): Long = atTime(23, 59, 59, 999_000_000).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * 求 date 所属的「月周期」区间。
 * startDay = 1 时就是自然月；startDay = 10 时，8/15 属于 8/10 - 9/9，8/3 属于 7/10 - 8/9。
 */
fun monthCycle(date: LocalDate, startDay: Int): ClosedRange<LocalDate> {
    val day = startDay.coerceIn(1, 28)
    val start = if (date.dayOfMonth >= day) {
        date.withDayOfMonth(day)
    } else {
        date.minusMonths(1).withDayOfMonth(day)
    }
    return start..start.plusMonths(1).minusDays(1)
}

/** 当前所在月周期的起点毫秒，供首页「本月」统计用 */
fun currentMonthCycleStartMillis(startDay: Int): Long = monthCycle(LocalDate.now(), startDay).start.startMillis()
