package com.tao.autobook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.tao.autobook.data.CategoryEntity
import com.tao.autobook.data.PaymentApp
import com.tao.autobook.data.TOTAL_BUDGET_ID
import com.tao.autobook.data.TransactionType
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/**
 * 统计报表页。
 * 数据全部来自 ViewModel 的 SQL 聚合（ReportUiState），不再在 UI 里对内存列表做统计。
 *
 * 结构：周期选择 → 概览（含环比/日均/结余）→ 预算 → 趋势 → 分类构成（可下钻）
 *       → 商家排行 → 支付方式 → 星期消费习惯
 */
@Composable
internal fun ReportScreen(
    report: ReportUiState,
    categories: List<CategoryEntity>,
    onPeriod: (ReportPeriod) -> Unit,
    onType: (TransactionType) -> Unit,
    onShift: (Long) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onDrill: (String) -> Unit,
    onSaveBudget: (String, String) -> Unit,
) {
    var showCustomPicker by remember { mutableStateOf(false) }
    var budgetTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showBudgetManager by remember { mutableStateOf(false) }

    val snap = report.snapshot
    val typeLabel = report.type.label
    val accent = if (report.type == TransactionType.INCOME) FreshMint else FreshBlue

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ReportHeader(
                report = report,
                onPeriod = onPeriod,
                onType = onType,
                onShift = onShift,
                onOpenCustom = { showCustomPicker = true },
            )
        }
        item { ReportOverviewCard(report) }
        // 预算仅在看支出时有意义
        if (report.type == TransactionType.EXPENSE) {
            item {
                BudgetCard(
                    report = report,
                    categories = categories,
                    onEdit = { id, current -> budgetTarget = id to current },
                    onOpenManager = { showBudgetManager = true },
                )
            }
        }
        item { ReportTrendCard(report, accent) }
        item {
            CategoryCompositionCard(
                report = report,
                categories = categories,
                onDrill = onDrill,
            )
        }
        item { MerchantRankCard(report) }
        item { PaymentAppCard(report) }
        item { WeekdayCard(report, accent) }
        if (snap.summary.cnt == 0) {
            item { ReportEmptyHint("当前区间没有${typeLabel}记录。换个周期或先记几笔试试。") }
        }
    }

    if (showCustomPicker) {
        CustomRangeDialog(
            initialStart = report.customStart,
            initialEnd = report.customEnd,
            onDismiss = { showCustomPicker = false },
            onConfirm = { s, e ->
                onCustomRange(s, e)
                showCustomPicker = false
            },
        )
    }

    budgetTarget?.let { (categoryId, current) ->
        BudgetEditDialog(
            title = if (categoryId == TOTAL_BUDGET_ID) "设置月度总预算" else "设置分类预算：" + (categories.firstOrNull { it.id == categoryId }?.name ?: "分类"),
            initial = current,
            onDismiss = { budgetTarget = null },
            onConfirm = { text ->
                onSaveBudget(categoryId, text)
                budgetTarget = null
            },
        )
    }

    if (showBudgetManager) {
        BudgetManagerDialog(
            report = report,
            categories = categories,
            onDismiss = { showBudgetManager = false },
            onSave = onSaveBudget,
        )
    }
}

// ====== 顶部：周期 + 类型 + 翻页 ======

@Composable
private fun ReportHeader(
    report: ReportUiState,
    onPeriod: (ReportPeriod) -> Unit,
    onType: (TransactionType) -> Unit,
    onShift: (Long) -> Unit,
    onOpenCustom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ReportPeriod.entries.forEach { p ->
                SegmentButton(
                    p.label,
                    selected = p == report.period,
                    modifier = Modifier.weight(1f)
                ) {
                    if (p == ReportPeriod.Custom) onOpenCustom() else onPeriod(p)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 区间标题 + 左右翻页
            Row(
                Modifier.weight(1f).background(CardWhite, RoundedCornerShape(14.dp)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onShift(-1L) }, enabled = report.pageable) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "上一期", tint = if (report.pageable) Ink else Line)
                }
                Text(
                    report.rangeLabel,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onShift(1L) }, enabled = report.canGoNext) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "下一期", tint = if (report.canGoNext) Ink else Line)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.background(CardWhite, RoundedCornerShape(18.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SegmentButton("支出", selected = report.type == TransactionType.EXPENSE) { onType(TransactionType.EXPENSE) }
                SegmentButton("收入", selected = report.type == TransactionType.INCOME) { onType(TransactionType.INCOME) }
            }
        }
    }
}

// ====== 概览：合计 / 笔数 / 日均 / 笔均 / 最大单笔 / 环比 / 结余 ======

@Composable
private fun ReportOverviewCard(report: ReportUiState) {
    val snap = report.snapshot
    val total = snap.summary.total
    val cnt = snap.summary.cnt
    val dayAvg = total / report.dayCount
    val txAvg = if (cnt > 0) total / cnt else 0L
    val balance = if (report.type == TransactionType.EXPENSE) snap.counterTotal - total else total - snap.counterTotal
    val deltaRatio = if (snap.prevTotal > 0L) (total - snap.prevTotal) * 100.0 / snap.prevTotal else null

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFEFF)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("${report.rangeLabel} · ${report.type.label}合计", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(total), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                }
                // 环比
                if (deltaRatio != null && report.prevLabel.isNotEmpty()) {
                    val up = deltaRatio >= 0
                    val badgeColor = if (up) Red else FreshMint
                    Column(horizontalAlignment = Alignment.End) {
                        Text("较${report.prevLabel}", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            (if (up) "+" else "-") + "%.1f%%".format(abs(deltaRatio)),
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportMetric("笔数", "$cnt 笔", Modifier.weight(1f))
                ReportMetric("日均", formatMoney(dayAvg), Modifier.weight(1f))
                ReportMetric("笔均", formatMoney(txAvg), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportMetric("最大单笔", formatMoney(snap.summary.maxAmount), Modifier.weight(1f))
                ReportMetric("记账天数", "${snap.summary.activeDays} 天", Modifier.weight(1f))
                ReportMetric(
                    if (report.type == TransactionType.EXPENSE) "本期结余" else "本期净收",
                    formatMoney(balance),
                    Modifier.weight(1f),
                    valueColor = if (balance < 0) Red else FreshMint
                )
            }
            if (report.prevLabel.isNotEmpty()) {
                Text(
                    "${report.prevLabel}同期：${formatMoney(snap.prevTotal)}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // 不计入收支的账单单列出来，让用户知道有多少钱被排除了
            if (snap.excludedCount > 0) {
                Text(
                    "另有 ${snap.excludedCount} 笔 ${formatMoney(snap.excludedTotal)} 标记为不计入收支（退款/提现/还款等），未计入以上数据",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Ink) {
    Column(modifier) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ====== 预算 ======

@Composable
private fun BudgetCard(
    report: ReportUiState,
    categories: List<CategoryEntity>,
    onEdit: (String, String) -> Unit,
    onOpenManager: () -> Unit,
) {
    val totalBudget = report.budgets.firstOrNull { it.categoryId == TOTAL_BUDGET_ID }?.amountCents ?: 0L
    // 预算口径：排除「不计入收支」和「不计入预算」的账单
    val spent = report.snapshot.budgetSpent
    val catBudgets = report.budgets.filter { it.categoryId != TOTAL_BUDGET_ID && it.amountCents > 0L }
    val catSpent = report.snapshot.budgetCategories.associate { it.categoryId to it.total }

    ReportCard("预算") {
        if (totalBudget <= 0L) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("还没设置总预算", color = Muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEdit(TOTAL_BUDGET_ID, "") }) { Text("设置", color = Blue) }
            }
        } else {
            val ratio = (spent.toDouble() / totalBudget).coerceIn(0.0, 1.0)
            val left = totalBudget - spent
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("总预算 ${formatMoney(totalBudget)}", color = Ink, fontWeight = FontWeight.Bold)
                        Text(
                            if (left >= 0) "剩余 ${formatMoney(left)}" else "已超支 ${formatMoney(-left)}",
                            color = if (left >= 0) Muted else Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { onEdit(TOTAL_BUDGET_ID, formatMoneyPlain(totalBudget)) }) { Text("修改", color = Blue) }
                }
                ProgressBar(ratio.toFloat(), if (left < 0) Red else FreshMint)
                Text("已用 ${"%.0f".format(ratio * 100)}%", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (catBudgets.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("分类预算", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            catBudgets.forEach { budget ->
                val name = categories.firstOrNull { it.id == budget.categoryId }?.name ?: "分类"
                val used = catSpent[budget.categoryId] ?: 0L
                val r = (used.toDouble() / budget.amountCents).coerceIn(0.0, 1.0)
                val over = used > budget.amountCents
                Column(
                    Modifier.fillMaxWidth().clickable { onEdit(budget.categoryId, formatMoneyPlain(budget.amountCents)) }.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = Ink, maxLines = 1)
                        Text("${formatMoney(used)} / ${formatMoney(budget.amountCents)}", color = if (over) Red else Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    ProgressBar(r.toFloat(), if (over) Red else FreshBlue)
                }
            }
        } else {
            Text("还没设置分类预算", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        // 统一入口：可为任意分类设置预算，而不是只推荐一个
        TextButton(onClick = onOpenManager) { Text("管理分类预算（全部分类）", color = Blue) }
    }
}

/**
 * 分类预算管理：列出全部支出分类（含子分类），逐个可设。
 * 之前只在报表里推荐「消费最多的一个未设分类」，导致看起来永远只有两条预算。
 */
@Composable
private fun BudgetManagerDialog(
    report: ReportUiState,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val expenseCats = categories.filter { it.type == TransactionType.EXPENSE }
    val tops = expenseCats.filter { it.parentId == null }
    val budgetMap = report.budgets.associate { it.categoryId to it.amountCents }
    val spentMap = report.snapshot.budgetCategories.associate { it.categoryId to it.total }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类预算") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(420.dp)) {
                item {
                    Text("点任意分类设置月度预算，填 0 或留空取消。", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                tops.forEach { top ->
                    item {
                        BudgetManagerRow(
                            name = top.name,
                            amount = budgetMap[top.id] ?: 0L,
                            used = spentMap[top.id] ?: 0L,
                            isSub = false,
                            onClick = { editing = top.id to (budgetMap[top.id]?.let { formatMoneyPlain(it) } ?: "") }
                        )
                    }
                    val subs = expenseCats.filter { it.parentId == top.id }
                    items(subs, key = { it.id }) { sub ->
                        BudgetManagerRow(
                            name = sub.name,
                            amount = budgetMap[sub.id] ?: 0L,
                            used = spentMap[sub.id] ?: 0L,
                            isSub = true,
                            onClick = { editing = sub.id to (budgetMap[sub.id]?.let { formatMoneyPlain(it) } ?: "") }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("完成") } }
    )

    editing?.let { (categoryId, current) ->
        val name = categories.firstOrNull { it.id == categoryId }?.name ?: "分类"
        BudgetEditDialog(
            title = "设置分类预算：$name",
            initial = current,
            onDismiss = { editing = null },
            onConfirm = {
                onSave(categoryId, it)
                editing = null
            }
        )
    }
}

@Composable
private fun BudgetManagerRow(name: String, amount: Long, used: Long, isSub: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (isSub) 18.dp else 0.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = if (isSub) Muted else Ink,
            fontWeight = if (isSub) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = if (isSub) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
        )
        if (amount > 0L) {
            Text(
                "${formatMoney(used)} / ${formatMoney(amount)}",
                color = if (used > amount) Red else Blue,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Text("未设置", color = Line, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProgressBar(ratio: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRoundRect(Line, cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        if (ratio > 0f) {
            drawRoundRect(
                color,
                size = Size(size.width * ratio.coerceIn(0f, 1f), size.height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

// ====== 趋势 ======

@Composable
private fun ReportTrendCard(report: ReportUiState, accent: Color) {
    // 年报/全部按月，其余按天
    val byMonth = report.period == ReportPeriod.Year ||
        report.period == ReportPeriod.All ||
        java.time.temporal.ChronoUnit.DAYS.between(report.range.start, report.range.endInclusive) > 62
    val series = if (byMonth) monthSeries(report) else daySeries(report)

    ReportCard(if (byMonth) "月度趋势" else "每日趋势") {
        ReportLineChart(series, accent)
        Spacer(Modifier.height(4.dp))
        ReportBarChart(series, accent)
    }
}

/** 补齐区间内每一天，缺失日补 0，保证折线不跳变 */
private fun daySeries(report: ReportUiState): ChartSeries {
    val map = report.snapshot.days.associate { it.bucket to it.total }
    val start = report.range.start
    val end = minOf(report.range.endInclusive, LocalDate.now())
    if (end.isBefore(start)) return ChartSeries(emptyList(), emptyList())
    val days = mutableListOf<LocalDate>()
    var cur = start
    while (!cur.isAfter(end)) {
        days += cur
        cur = cur.plusDays(1)
    }
    val values = days.map { map[it.toString()] ?: 0L }
    val step = max(1, days.size / 4)
    val labels = days.filterIndexed { i, _ -> i % step == 0 || i == days.lastIndex }
        .map { "${it.monthValue}/${it.dayOfMonth}" }
    return ChartSeries(values, labels)
}

/** 补齐区间内每一个月 */
private fun monthSeries(report: ReportUiState): ChartSeries {
    val map = report.snapshot.months.associate { it.bucket to it.total }
    var cur = report.range.start.withDayOfMonth(1)
    val end = minOf(report.range.endInclusive, LocalDate.now()).withDayOfMonth(1)
    if (end.isBefore(cur)) return ChartSeries(emptyList(), emptyList())
    val months = mutableListOf<LocalDate>()
    while (!cur.isAfter(end)) {
        months += cur
        cur = cur.plusMonths(1)
    }
    val values = months.map { map["%04d-%02d".format(it.year, it.monthValue)] ?: 0L }
    val step = max(1, months.size / 6)
    val labels = months.filterIndexed { i, _ -> i % step == 0 || i == months.lastIndex }
        .map { "${it.monthValue}月" }
    return ChartSeries(values, labels)
}

// ====== 分类构成 + 下钻 ======

@Composable
private fun CategoryCompositionCard(
    report: ReportUiState,
    categories: List<CategoryEntity>,
    onDrill: (String) -> Unit,
) {
    val rows = report.snapshot.categories
    val total = rows.sumOf { it.total }.coerceAtLeast(1L)
    ReportCard("${report.type.label}分类构成") {
        if (rows.isEmpty()) {
            Text("暂无${report.type.label}数据", color = Muted)
            return@ReportCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReportDonutChart(rows.map { it.total }, rows.indices.map { FreshPalette[it % FreshPalette.size] }, Modifier.size(130.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("共 ${rows.size} 个分类", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(formatMoney(total), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("点分类可查看明细", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, row ->
            val name = categories.firstOrNull { it.id == row.categoryId }?.name ?: "其他"
            val pct = row.total * 100 / total
            val expanded = report.drillCategoryId == row.categoryId
            Column(
                Modifier.fillMaxWidth().clickable { onDrill(row.categoryId) }.padding(vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(FreshPalette[index % FreshPalette.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(name, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$pct%  ", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(row.total), color = Ink)
                }
                ProgressBar((row.total.toFloat() / total).coerceIn(0f, 1f), FreshPalette[index % FreshPalette.size])
                Text("${row.cnt} 笔 · 笔均 ${formatMoney(if (row.cnt > 0) row.total / row.cnt else 0L)}", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (expanded) {
                DrillDownBlock(report)
            }
        }
    }
}

@Composable
private fun DrillDownBlock(report: ReportUiState) {
    val drill = report.drill
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF7F9FC), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (drill.merchants.isEmpty() && drill.transactions.isEmpty()) {
            Text("该分类下暂无可下钻明细", color = Muted, style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (drill.merchants.isNotEmpty()) {
            Text("商家 TOP", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            drill.merchants.take(5).forEach { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(m.merchantName, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${m.cnt}笔 ${formatMoney(m.total)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (drill.transactions.isNotEmpty()) {
            Text("大额账单", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            drill.transactions.take(5).forEach { tx ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${formatDate(tx.paidAt)} ${tx.merchantName.ifBlank { tx.note.ifBlank { "未命名" } }}",
                        color = Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(formatMoney(tx.amountCents), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ====== 商家排行 ======

@Composable
private fun MerchantRankCard(report: ReportUiState) {
    val rows = report.snapshot.merchants
    ReportCard("商家排行") {
        if (rows.isEmpty()) {
            Text("暂无商家数据", color = Muted)
            return@ReportCard
        }
        val maxTotal = rows.maxOf { it.total }.coerceAtLeast(1L)
        rows.forEachIndexed { index, row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
                    Text(row.merchantName, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${row.cnt}笔 ", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(formatMoney(row.total), color = Ink)
                }
                ProgressBar((row.total.toFloat() / maxTotal).coerceIn(0f, 1f), FreshCoral)
            }
        }
    }
}

// ====== 支付方式 ======

@Composable
private fun PaymentAppCard(report: ReportUiState) {
    val rows = report.snapshot.paymentApps
    ReportCard("支付方式分布") {
        if (rows.isEmpty()) {
            Text("暂无支付方式数据", color = Muted)
            return@ReportCard
        }
        val total = rows.sumOf { it.total }.coerceAtLeast(1L)
        rows.forEachIndexed { index, row ->
            val label = runCatching { PaymentApp.valueOf(row.paymentApp).label }.getOrDefault(row.paymentApp)
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(FreshPalette[index % FreshPalette.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Ink, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("${row.total * 100 / total}%  ", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(formatMoney(row.total), color = Ink)
                }
                ProgressBar((row.total.toFloat() / total).coerceIn(0f, 1f), FreshPalette[index % FreshPalette.size])
            }
        }
    }
}

// ====== 星期消费习惯 ======

private val WEEKDAY_LABELS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

@Composable
private fun WeekdayCard(report: ReportUiState, accent: Color) {
    val map = report.snapshot.weekdays.associate { it.weekday to it.total }
    // 展示顺序改成周一到周日，符合国内习惯
    val order = listOf(1, 2, 3, 4, 5, 6, 0)
    val values = order.map { map[it] ?: 0L }
    val labels = order.map { WEEKDAY_LABELS[it].removePrefix("周") }
    val peakIndex = values.indices.maxByOrNull { values[it] } ?: 0

    ReportCard("消费习惯（按星期）") {
        ReportBarChart(ChartSeries(values, labels), accent)
        if (values.any { it > 0L }) {
            Text(
                "最爱花钱：${WEEKDAY_LABELS[order[peakIndex]]}，合计 ${formatMoney(values[peakIndex])}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ====== 通用图表组件 ======

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ReportLineChart(series: ChartSeries, color: Color) {
    val values = series.values
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val maxValue = max(1L, values.maxOrNull() ?: 0L).toFloat()
            repeat(4) { i ->
                val y = size.height * i / 4f
                drawLine(Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (values.isEmpty() || values.all { it == 0L }) {
                drawLine(Line, Offset(0f, size.height * 0.62f), Offset(size.width, size.height * 0.62f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                return@Canvas
            }
            val stepX = size.width / max(1, values.size - 1)
            values.zipWithNext().forEachIndexed { index, (a, b) ->
                val p1 = Offset(index * stepX, size.height - (a / maxValue) * size.height)
                val p2 = Offset((index + 1) * stepX, size.height - (b / maxValue) * size.height)
                drawLine(color, p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            // 点数太多就不画圆点，避免糊成一片
            if (values.size <= 40) {
                values.forEachIndexed { index, value ->
                    drawCircle(color, 3.5f.dp.toPx(), Offset(index * stepX, size.height - (value / maxValue) * size.height))
                }
            }
        }
        ReportChartLabels(series.labels)
    }
}

@Composable
private fun ReportBarChart(series: ChartSeries, color: Color) {
    val values = series.values
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (values.isEmpty()) return@Canvas
            val maxValue = max(1L, values.maxOrNull() ?: 0L).toFloat()
            val gap = if (values.size > 20) 2.dp.toPx() else 8.dp.toPx()
            val barWidth = (size.width - gap * (values.size + 1)) / max(1, values.size)
            values.forEachIndexed { index, value ->
                val h = max(3f, (value / maxValue) * size.height)
                val left = gap + index * (barWidth + gap)
                drawRoundRect(
                    color.copy(alpha = 0.75f),
                    Offset(left, size.height - h),
                    Size(barWidth, h),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
        ReportChartLabels(series.labels)
    }
}

@Composable
private fun ReportChartLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { label -> Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
    }
}

@Composable
private fun ReportDonutChart(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val total = values.sum().takeIf { it > 0 } ?: 1L
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value.toFloat() / total * 360f
            drawArc(colors.getOrElse(index) { Blue }, start, sweep, false, style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt))
            start += sweep
        }
        if (values.isEmpty()) drawCircle(Line, radius = size.minDimension / 2.6f, style = Stroke(width = 24.dp.toPx()))
    }
}

@Composable
private fun ReportEmptyHint(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Muted, modifier = Modifier.padding(16.dp))
    }
}

// ====== 弹窗：自定义区间 / 预算编辑 ======

@Composable
private fun CustomRangeDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    var startText by remember { mutableStateOf(initialStart.toString()) }
    var endText by remember { mutableStateOf(initialEnd.toString()) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义区间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it; error = "" },
                    label = { Text("开始日期 (yyyy-MM-dd)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it; error = "" },
                    label = { Text("结束日期 (yyyy-MM-dd)") },
                    singleLine = true
                )
                if (error.isNotEmpty()) Text(error, color = Red, style = MaterialTheme.typography.bodySmall)
                Text("最长支持 2 年区间", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                    val e = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()
                    when {
                        s == null || e == null -> error = "日期格式不对，例如 2026-08-01"
                        java.time.temporal.ChronoUnit.DAYS.between(minOf(s, e), maxOf(s, e)) > 730 -> error = "区间不能超过 2 年"
                        else -> onConfirm(s, e)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun BudgetEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("填 0 或留空表示取消该预算", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
