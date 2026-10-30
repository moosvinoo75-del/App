package com.example.lifemanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// ---------- ماه شمسی ----------

data class YM(val y: Int, val m: Int)

fun currentYM(): YM {
    val j = jalaliOf(System.currentTimeMillis())
    return YM(j[0], j[1])
}

fun YM.shift(delta: Int): YM {
    val idx = y * 12 + (m - 1) + delta
    return YM(idx / 12, idx % 12 + 1)
}

fun YM.label(): String = "${JALALI_MONTHS[m - 1]} $y"

private fun ymStart(ym: YM): Long {
    val g = jalaliToGregorian(ym.y, ym.m, 1)
    val c = Calendar.getInstance()
    c.set(g[0], g[1] - 1, g[2], 0, 0, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun monthTxns(all: List<Txn>, ym: YM): List<Txn> {
    val s = ymStart(ym)
    val e = ymStart(ym.shift(1))
    return all.filter { it.date >= s && it.date < e }
}

fun categoryTotals(list: List<Txn>): List<Pair<String, Long>> =
    list.filter { !it.isIncome }
        .groupBy { it.category.ifBlank { "بدون دسته" } }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }

// مبلغ کوتاه و خوانا: ۱.۲ میلیارد، ۴۵۰ هزار
fun shortAmount(v: Long): String {
    val a = abs(v)
    val s = when {
        a >= 1_000_000_000L -> String.format(Locale.US, "%.1f میلیارد", a / 1e9)
        a >= 1_000_000L -> String.format(Locale.US, "%.1f میلیون", a / 1e6)
        a >= 1_000L -> String.format(Locale.US, "%.0f هزار", a / 1e3)
        else -> a.toString()
    }
    return (if (v < 0) "-" else "") + s.replace(".0 ", " ")
}

val CHART_COLORS = listOf(
    Color(0xFF0F766E), Color(0xFF2563EB), Color(0xFFF59E0B), Color(0xFFDB2777),
    Color(0xFF7C3AED), Color(0xFF16A34A), Color(0xFFEA580C), Color(0xFF64748B)
)

@Composable
fun MonthNav(ym: YM, onYm: (YM) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { onYm(ym.shift(-1)) }) { Text("ماه قبل") }
        Text(ym.label(), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onYm(ym.shift(1)) }) { Text("ماه بعد") }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value, color = color, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1
            )
        }
    }
}

// ---------- نمودارها ----------

@Composable
fun DonutChart(values: List<Float>, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = values.sum()
        if (total > 0f) {
            val stroke = size.minDimension * 0.22f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = v / total * 360f
                drawArc(
                    color = colors[i % colors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
                start += sweep
            }
        }
    }
}

@Composable
fun LineChart(points: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (points.size >= 2) {
            val minV = points.min()
            val maxV = points.max()
            val range = (maxV - minV).coerceAtLeast(1L).toFloat()
            val pad = 10f
            val path = Path()
            var lastX = 0f
            var lastY = 0f
            points.forEachIndexed { i, v ->
                // قدیمی‌ترین نقطه سمت راست (مثل جهت فارسی)
                val x = size.width * (1f - i / (points.size - 1).toFloat())
                val y = size.height - pad - (v - minV).toFloat() / range * (size.height - 2 * pad)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                lastX = x
                lastY = y
            }
            drawPath(path, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawCircle(color = color, radius = 8f, center = Offset(lastX, lastY))
        }
    }
}

private fun barHeight(v: Long, max: Long): Float = if (max > 0) maxOf(2f, 110f * v / max) else 2f

@Composable
fun TrendChart(data: List<Triple<String, Long, Long>>) {
    val maxV = data.maxOfOrNull { maxOf(it.second, it.third) } ?: 0L
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { (label, inc, exp) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        Modifier.width(12.dp).height(barHeight(inc, maxV).dp)
                            .background(IncomeColor, RoundedCornerShape(3.dp))
                    )
                    Box(
                        Modifier.width(12.dp).height(barHeight(exp, maxV).dp)
                            .background(ExpenseColor, RoundedCornerShape(3.dp))
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

// ---------- نمای کلی ----------

@Composable
fun OverviewScreen(vm: MainVm, ym: YM, onYm: (YM) -> Unit) {
    val txns by vm.txns.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val snaps by vm.snapshots.collectAsState(initial = emptyList())

    val w = remember(accounts) { computeWealth(accounts) }
    val month = monthTxns(txns, ym)
    val income = month.filter { it.isIncome }.sumOf { it.amount }
    val expense = month.filter { !it.isIncome }.sumOf { it.amount }
    val saving = income - expense
    val rate = if (income > 0) (saving * 100 / income).toInt() else 0

    // آمارهای هوشمند
    val isCurrentMonth = ym == currentYM()
    val daysPassed = if (isCurrentMonth) {
        val j = jalaliOf(System.currentTimeMillis())
        maxOf(1, j[2])
    } else {
        30
    }
    val dailyAvgExpense = expense / daysPassed
    val projectedExpense = dailyAvgExpense * 30

    val maxDayExpense = month.filter { !it.isIncome }
        .groupBy { jalaliText(it.date) }
        .map { it.value.sumOf { tx -> tx.amount } }
        .maxOrNull() ?: 0L

    val cats = categoryTotals(month)
    val top = cats.take(6)
    val rest = cats.drop(6).sumOf { it.second }
    val slices = if (rest > 0) top + ("سایر دسته‌ها" to rest) else top
    val sliceTotal = slices.sumOf { it.second }

    val trend = (5 downTo 0).map { back ->
        val m = ym.shift(-back)
        val list = monthTxns(txns, m)
        Triple(
            JALALI_MONTHS[m.m - 1],
            list.filter { it.isIncome }.sumOf { it.amount },
            list.filter { !it.isIncome }.sumOf { it.amount }
        )
    }
    val netPoints = snaps.takeLast(30).map { it.net }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeroCard(
            "ثروت خالص", w.net,
            "دارایی‌ها", fmt(w.assets),
            "بدهی‌ها", fmt(w.debts)
        )
        AmountWords(w.net)

        MonthNav(ym, onYm)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("درآمد", shortAmount(income), IncomeColor, Modifier.weight(1f))
            StatCard("هزینه", shortAmount(expense), ExpenseColor, Modifier.weight(1f))
            StatCard(
                "پس‌انداز", shortAmount(saving),
                if (saving >= 0) IncomeColor else ExpenseColor, Modifier.weight(1f)
            )
        }
        if (income > 0) {
            Text(
                "نرخ پس‌انداز این ماه: ${rate}٪ از درآمد",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // کارت تحلیل هوشمند
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تحلیل و میانگین‌های ماه", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("میانگین خرج روزانه:", style = MaterialTheme.typography.bodySmall)
                    Text("${fmt(dailyAvgExpense)} تومان", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("پرهزینه‌ترین روز ماه:", style = MaterialTheme.typography.bodySmall)
                    Text("${fmt(maxDayExpense)} تومان", fontWeight = FontWeight.SemiBold)
                }
                if (isCurrentMonth) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("پیش‌بینی کل خرج این ماه:", style = MaterialTheme.typography.bodySmall)
                        Text("${shortAmount(projectedExpense)} تومان", fontWeight = FontWeight.SemiBold, color = ExpenseColor)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("هزینه‌ها بر اساس دسته", style = MaterialTheme.typography.titleMedium)
                if (slices.isEmpty()) {
                    Text(
                        "در این ماه هزینه‌ای ثبت نشده.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DonutChart(
                            slices.map { it.second.toFloat() }, CHART_COLORS,
                            Modifier.size(120.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            slices.forEachIndexed { i, s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(10.dp).clip(CircleShape)
                                            .background(CHART_COLORS[i % CHART_COLORS.size])
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        s.first, Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1
                                    )
                                    Text(
                                        "${s.second * 100 / sliceTotal}٪ · ${shortAmount(s.second)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("درآمد و هزینه‌ی ۶ ماه اخیر", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(IncomeColor))
                        Spacer(Modifier.width(6.dp))
                        Text("درآمد", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(ExpenseColor))
                        Spacer(Modifier.width(6.dp))
                        Text("هزینه", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TrendChart(trend)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("روند ثروت خالص", style = MaterialTheme.typography.titleMedium)
                if (netPoints.size >= 2) {
                    LineChart(
                        netPoints, MaterialTheme.colorScheme.primary,
                        Modifier.fillMaxWidth().height(120.dp)
                    )
                    Text(
                        "از ${shortAmount(netPoints.first())} تا ${shortAmount(netPoints.last())} تومان",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "هر روز یک نقطه ثبت می‌شود. بعد از چند روز نمودار روند اینجا نمایش داده می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- بودجه ----------

@Composable
fun BudgetRow(b: Budget, spent: Long, onDelete: () -> Unit) {
    val frac = if (b.limitAmount > 0) spent.toFloat() / b.limitAmount else 0f
    val color = when {
        frac >= 1f -> ExpenseColor
        frac >= 0.8f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }
    val left = b.limitAmount - spent
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.category, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${(frac * 100).toInt()}٪", color = color, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDelete) { Text("✕") }
            }
            LinearProgressIndicator(
                progress = { frac.coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Text(
                "${fmt(spent)} از ${fmt(b.limitAmount)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (left >= 0) "مانده: ${shortAmount(left)} تومان"
                else "بیش از بودجه: ${shortAmount(-left)} تومان",
                style = MaterialTheme.typography.bodySmall,
                color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseColor
            )
        }
    }
}

@Composable
fun BudgetScreen(vm: MainVm, ym: YM, onYm: (YM) -> Unit) {
    val budgets by vm.budgets.collectAsState(initial = emptyList())
    val txns by vm.txns.collectAsState(initial = emptyList())
    var category by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }

    val month = monthTxns(txns, ym)
    val spent = categoryTotals(month).toMap()
    val totalLimit = budgets.sumOf { it.limitAmount }
    val totalSpent = budgets.sumOf { spent[it.category] ?: 0L }
    val totalFrac = if (totalLimit > 0) totalSpent.toFloat() / totalLimit else 0f

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MonthNav(ym, onYm)

        if (budgets.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("کل بودجه‌ی این ماه", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { totalFrac.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    )
                    Text(
                        "${shortAmount(totalSpent)} از ${shortAmount(totalLimit)} تومان خرج شده",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Text(
                "هنوز بودجه‌ای تعریف نکرده‌ای. پایین، برای هر دسته سقف ماهانه بگذار.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        budgets.forEach { b ->
            BudgetRow(b, spent[b.category] ?: 0L) { vm.delBudget(b) }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("بودجه‌ی جدید", style = MaterialTheme.typography.titleMedium)
                Text(
                    "برای تغییر بودجه‌ی یک دسته، همان دسته را دوباره با مبلغ جدید ثبت کن.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORIES.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) }
                        )
                    }
                }
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text("دسته") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limit, onValueChange = { limit = it },
                    label = { Text("سقف ماهانه (تومان)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                AmountHint(limit)
                Button(onClick = {
                    val l = limit.faToLong()
                    if (category.isNotBlank() && l != null && l > 0) {
                        vm.setBudget(category.trim(), l)
                        category = ""
                        limit = ""
                    }
                }) { Text("ثبت بودجه") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- اهداف پس‌انداز ----------

@Composable
fun GoalsScreen(vm: MainVm) {
    val goals by vm.goals.collectAsState(initial = emptyList())
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }

    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }

    var contrib by remember { mutableStateOf<Goal?>(null) }
    var contribText by remember { mutableStateOf("") }
    var pickedAcc by remember { mutableStateOf(0L) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (goals.isEmpty()) {
            Text(
                "هنوز هدفی نداری. مثلاً «خرید ماشین» یا «سفر».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        goals.forEach { g ->
            val frac = if (g.target > 0) (g.saved.toFloat() / g.target).coerceIn(0f, 1f) else 0f
            val remaining = (g.target - g.saved).coerceAtLeast(0L)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(
                            "${(frac * 100).toInt()}٪",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { vm.delGoal(g) }) { Text("✕") }
                    }
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    )
                    Text(
                        "${fmt(g.saved)} از ${fmt(g.target)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (remaining > 0) "مانده تا هدف: ${shortAmount(remaining)} تومان"
                        else "به هدف رسیدی 🎉",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { contrib = g; contribText = ""; pickedAcc = 0L }) {
                        Text("افزودن یا برداشت پس‌انداز")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("هدف جدید", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("نام هدف") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target, onValueChange = { target = it },
                    label = { Text("مبلغ هدف (تومان)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                AmountHint(target)
                OutlinedTextField(
                    value = saved, onValueChange = { saved = it },
                    label = { Text("تا الان چقدر جمع کرده‌ای؟ (اختیاری)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                AmountHint(saved)
                Button(onClick = {
                    val t = target.faToLong()
                    if (name.isNotBlank() && t != null && t > 0) {
                        vm.addGoal(name.trim(), t, (saved.faToLong() ?: 0L).coerceAtLeast(0L))
                        name = ""
                        target = ""
                        saved = ""
                    }
                }) { Text("افزودن هدف") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    contrib?.let { g ->
        val chosenAcc = if (usable.any { it.id == pickedAcc }) pickedAcc else (usable.firstOrNull()?.id ?: 0L)
        AlertDialog(
            onDismissRequest = { contrib = null },
            title = { Text(g.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = contribText, onValueChange = { contribText = it },
                        label = { Text("مبلغ (تومان)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AmountHint(contribText)
                    if (usable.isNotEmpty()) {
                        Text("انتخاب حساب (اختیاری برای کسر/افزایش موجودی)", style = MaterialTheme.typography.bodySmall)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            usable.forEach { a ->
                                FilterChip(
                                    selected = chosenAcc == a.id,
                                    onClick = { pickedAcc = a.id },
                                    label = { Text(a.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = contribText.faToLong()
                    if (v != null && v > 0) {
                        vm.addToGoal(g, v, chosenAcc)
                    }
                    contrib = null
                }) { Text("افزودن (واریز)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val v = contribText.faToLong()
                    if (v != null && v > 0) {
                        vm.addToGoal(g, -v, chosenAcc)
                    }
                    contrib = null
                }) { Text("برداشت") }
            }
        )
    }
}
