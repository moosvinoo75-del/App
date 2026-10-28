package com.example.lifemanager

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// ---------- اندازه‌ی نوشته‌ها (برای دسترسی بهتر) ----------

object UiPrefs {
    var fontScale by mutableStateOf(1f)

    fun load(c: Context) {
        fontScale = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getFloat("font_scale", 1f)
    }

    fun save(c: Context, v: Float) {
        fontScale = v
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putFloat("font_scale", v).apply()
    }
}

fun dueCountOf(cheques: List<Cheque>, loans: List<Loan>): Int =
    cheques.count { it.status == 0 && daysUntil(it.dueDate) <= 1 } +
        loans.count { l ->
            val d = loanDue(l)
            d != null && daysUntil(d) <= 1
        }

// ---------- قالب اصلی بخش مالی ----------

@Composable
fun FinanceHub(vm: MainVm) {
    var page by remember { mutableStateOf("home") }
    var ym by remember { mutableStateOf(currentYM()) }
    var showQuick by remember { mutableStateOf(false) }

    val mainPages = listOf("home", "txns", "accounts", "plan")
    val isSub = page !in mainPages
    BackHandler(enabled = isSub) { page = "plan" }

    val titles = mapOf(
        "budget" to "بودجه",
        "goals" to "اهداف پس‌انداز",
        "cheques" to "چک و قسط",
        "people" to "طلب و بدهی افراد",
        "recurring" to "تکراری و اشتراک",
        "tools" to "انتقال و خروجی"
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MainTabs(if (isSub) "plan" else page) { page = it }
            if (isSub) SubHeader(titles[page] ?: "") { page = "plan" }
            Box(Modifier.weight(1f)) {
                when (page) {
                    "home" -> HomePage(vm, ym, { ym = it }, { page = it }, { showQuick = true })
                    "txns" -> TxnScreen(vm, ym) { ym = it }
                    "accounts" -> AccountsScreen(vm)
                    "plan" -> PlanPage(vm) { page = it }
                    "budget" -> BudgetScreen(vm, ym) { ym = it }
                    "goals" -> GoalsScreen(vm)
                    "cheques" -> ChequeLoanScreen(vm)
                    "people" -> PeopleScreen(vm)
                    "recurring" -> RecurringScreen(vm)
                    else -> ToolsScreen(vm)
                }
            }
        }
        if (page == "home" || page == "txns") {
            ExtendedFloatingActionButton(
                onClick = { showQuick = true },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("＋ ثبت تراکنش", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (showQuick) QuickAddDialog(vm) { showQuick = false }
}

@Composable
fun MainTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        "home" to "خانه", "txns" to "تراکنش‌ها", "accounts" to "حساب‌ها", "plan" to "برنامه‌ریزی"
    )
    Row(Modifier.fillMaxWidth().statusBarsPadding()) {
        tabs.forEach { (key, label) ->
            val on = selected == key
            Column(
                Modifier.weight(1f).heightIn(min = 52.dp).clickable { onSelect(key) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    label, maxLines = 1,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(3.dp).background(
                        if (on) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("بازگشت") }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------- صفحه‌ی خانه ----------

@Composable
fun HomePage(
    vm: MainVm, ym: YM, onYm: (YM) -> Unit,
    go: (String) -> Unit, onQuick: () -> Unit
) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val txns by vm.txns.collectAsState(initial = emptyList())
    val budgets by vm.budgets.collectAsState(initial = emptyList())
    val cheques by vm.cheques.collectAsState(initial = emptyList())
    val loans by vm.loans.collectAsState(initial = emptyList())

    val dueCount = dueCountOf(cheques, loans)
    val spent = categoryTotals(monthTxns(txns, currentYM())).toMap()
    val warnings = budgets.filter {
        it.limitAmount > 0 && (spent[it.category] ?: 0L) * 100 >= it.limitAmount * 80
    }

    Column(Modifier.fillMaxSize()) {
        if (accounts.isEmpty() && txns.isEmpty()) {
            FirstRunCard(go, onQuick)
        }
        if (dueCount > 0 || warnings.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dueCount > 0) {
                    AssistChip(
                        onClick = { go("cheques") },
                        label = { Text("⏰ $dueCount سررسید نزدیک") }
                    )
                }
                warnings.forEach { b ->
                    val pct = (spent[b.category] ?: 0L) * 100 / b.limitAmount
                    AssistChip(
                        onClick = { go("budget") },
                        label = { Text("⚠️ ${b.category}: ${pct}٪ بودجه") }
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) { OverviewScreen(vm, ym, onYm) }
    }
}

@Composable
fun FirstRunCard(go: (String) -> Unit, onQuick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("شروع سریع", style = MaterialTheme.typography.titleMedium)
            Text("۱. یک حساب بساز (مثلاً «کیف پول» با موجودی فعلی‌ات).")
            Text("۲. اولین هزینه یا درآمدت را ثبت کن.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { go("accounts") }) { Text("ساخت حساب") }
                OutlinedButton(onClick = onQuick) { Text("ثبت تراکنش") }
            }
        }
    }
}

// ---------- صفحه‌ی برنامه‌ریزی ----------

private data class Tile(val key: String, val icon: String, val title: String, val subtitle: String)

@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Column(
            Modifier.padding(16.dp).heightIn(min = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(t.icon, style = MaterialTheme.typography.headlineMedium)
            Text(t.title, fontWeight = FontWeight.Bold)
            Text(
                t.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlanPage(vm: MainVm, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val budgets by vm.budgets.collectAsState(initial = emptyList())
    val goals by vm.goals.collectAsState(initial = emptyList())
    val cheques by vm.cheques.collectAsState(initial = emptyList())
    val loans by vm.loans.collectAsState(initial = emptyList())
    val people by vm.people.collectAsState(initial = emptyList())
    val recurring by vm.recurring.collectAsState(initial = emptyList())

    val dueCount = dueCountOf(cheques, loans)
    val openPeople = people.count { it.paid < it.amount }
    val fixedMonthly = recurring.filter { it.active && !it.isIncome }.sumOf { it.amount }

    val tiles = listOf(
        Tile("budget", "🎯", "بودجه", if (budgets.isEmpty()) "سقف خرج هر دسته" else "${budgets.size} دسته"),
        Tile("goals", "🏁", "اهداف پس‌انداز", if (goals.isEmpty()) "برای خرید یا سفر" else "${goals.size} هدف"),
        Tile("cheques", "🧾", "چک و قسط", if (dueCount > 0) "$dueCount سررسید نزدیک" else "سررسید و یادآور"),
        Tile("people", "🤝", "طلب و بدهی افراد", if (openPeople > 0) "$openPeople مورد باز" else "چه کسی چقدر"),
        Tile(
            "recurring", "🔁", "تکراری و اشتراک",
            if (fixedMonthly > 0) "ماهانه ${shortAmount(fixedMonthly)}" else "اجاره، قسط، اینترنت"
        ),
        Tile("tools", "🔀", "انتقال و خروجی", "بین حساب‌ها · اکسل")
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> TileCard(t, Modifier.weight(1f)) { go(t.key) } }
            }
        }
        SectionCard("اندازه‌ی نوشته‌ها") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.9f to "کوچک", 1f to "معمولی", 1.2f to "بزرگ", 1.4f to "خیلی بزرگ")
                    .forEach { (v, label) ->
                        FilterChip(
                            selected = abs(UiPrefs.fontScale - v) < 0.01f,
                            onClick = { UiPrefs.save(ctx, v) },
                            label = { Text(label) }
                        )
                    }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- ثبت سریع تراکنش ----------

@Composable
fun QuickAddDialog(vm: MainVm, onDismiss: () -> Unit) {
    val accounts by vm.accounts.collectAsState(initial = emptyList())
    val usable = accounts.filter { it.type !in PRICED_TYPES }
    var income by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayJalali()) }
    var dateErr by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var pickedAcc by remember { mutableStateOf(0L) }
    val accId = if (usable.any { it.id == pickedAcc }) pickedAcc else (usable.firstOrNull()?.id ?: 0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت تراکنش") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !income, onClick = { income = false },
                        label = { Text("هزینه") }, modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = income, onClick = { income = true },
                        label = { Text("درآمد") }, modifier = Modifier.weight(1f)
                    )
                }
                NumField(amount, { amount = it }, "مبلغ (تومان)")
                AmountHint(amount)
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
                LineField(note, { note = it }, "توضیح (اختیاری)")
                if (usable.size > 1) {
                    Text("حساب", style = MaterialTheme.typography.bodySmall)
                    AccountChips(usable, accId) { pickedAcc = it }
                }
                if (showDate) {
                    DateField(dateText, dateErr, "تاریخ (شمسی)") { dateText = it; dateErr = false }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تاریخ: امروز")
                        TextButton(onClick = { showDate = true }) { Text("تغییر") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = amount.faToLong()
                    val d = parseJalali(dateText)
                    if (d == null) {
                        dateErr = true
                        showDate = true
                    } else if (a != null && a > 0) {
                        vm.addTxn(a, income, category.trim(), note.trim(), d, accId)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (income) IncomeColor else ExpenseColor
                )
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
